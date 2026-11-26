# Design

## Context

本变更把一个跨 4 层（Maven 模块 / Java 应用 / React 前端 / Go 独立服务）的功能整体下线。已在 `proposal.md` 说明动机；本文只记录技术方案、执行顺序和取舍。

关键既有事实（来自本仓实际源码核对）：

- `server/pom.xml:39` 注册 `skillhub-github-sync` 模块；仅 `skillhub-app`（`pom.xml:103`）与 `skillhub-infra`（`pom.xml:32`）依赖它。
- 45 个 github 专用迁移：V76–V119 连续 44 个，加 V126，当前最大版本 V126。
- `skillhub-search` 的 `PostgresFullTextQueryService:128-130` 直接拼接 `s.github_sync_managed`，是本变更中唯一"删列会直接导致 SQL 报错"的跨模块点。
- `FlywayMigrationGuardrailTest:54` 从 `expected=1` 逐位比对版本，任何跳跃必失败。
- `GithubDeepDiscoveryReadModelIntegrationTest:61-63` 用 `ClassPathResource` 读取 `V111__github_official_candidate_controls.sql` 原文重放；迁移文件一旦删除该测试即崩（本变更保留迁移文件，故不受影响，但改写测试时需注意）。

## Goals / Non-Goals

**Goals**

- 让 `grep -ri github` 在业务代码中只剩 GitHub OAuth 与文档链接。
- 让数据库在 V10000 之后不残留任何 github 语义对象（表、视图、函数、触发器、核心表专列）。
- 保证空库从 V1 全量迁移到 V10000 成功，且 `review_task`/`skill`/`skill_version` 的其余结构与数据完好。
- 保证每阶段可独立编译、独立验收、独立回滚。

**Non-Goals**

- 不删除 GitHub OAuth 登录（`GitHubClaimsExtractor` 等）。
- 不删除 `BasicPrePublishValidator` 的 `ghp_` 规则。
- 不改写任何既有迁移文件的内容或 checksum。
- 不做 `@github` 存量 Skill 向普通 namespace 的数据迁移（断言保证无存量数据）。

## Decisions

### 决策 1：迁移用 V10000 高位段，并改造连续性守卫

**选择**：新增 `V10000__remove_github_sync.sql`；把 `FlywayMigrationGuardrailTest.versionedMigrations_mustRemainContiguous` 从"1..N 连续"改为"1..N 连续 **且** 高位段（≥10000）内部无重复、无空洞"。

**理由**：fork 长期跟进上游，V127 必然与上游撞号。`docs/09-deployment.md` 已记录过该仓库因版本号冲突被迫放弃分支数据的历史（"早期未发布开发分支使用过 V66–V109 的不同编号，它们与 main 历史冲突，不能直接套用"）。高位段一次性解决该类冲突。

**代价**：守卫测试本身被放宽，需在测试中显式保留"无重复、命名合规、低位段连续"三条实质约束，避免把守卫改造成空壳。

**备选与否决**：V127 需每次合上游重新编号，是把负债推迟而非消除；删文件重编号（原 Q29 的 B 选项）会让开发库的 `flyway_schema_history` 保留无法解析的版本记录，故不采用。

### 决策 2：迁移内先断言后删除

**选择**：V10000 开头用 `DO $$ ... $$` 断言三个前置条件，任一不满足即 `RAISE EXCEPTION` 并整体回滚：

1. `skill WHERE github_sync_managed = TRUE` 计数为 0；
2. `namespace WHERE slug = 'github'` 下的 `skill` 计数为 0；
3. `review_task WHERE origin_type = 'GITHUB_SYNC'` 计数为 0。

**理由**：与 V76 既有的冲突校验风格一致（`V76__github_catalog_namespace.sql:6-10` 用 `RAISE EXCEPTION` 阻止接管既有数据）。断言把"本 fork 无存量数据"这一口头前提变成可执行的迁移前置条件——若某天有人真的跑过同步，迁移会明确失败而非静默删除。

**取舍**：断言失败时无法自动修复，必须人工确认后清理数据再重跑。这是刻意选择：一次性清理迁移不应承担业务数据迁移职责。

### 决策 3：数据库删除顺序（严格分层）

顺序错误会触发 PostgreSQL 的 `dependent objects` 或外键约束错误。完整顺序如下。

**第 1 步：删视图（视图依赖表，必须先于表删除）**

```sql
DROP VIEW IF EXISTS github_candidate_discovery_eligibility;  -- V110/V111/V118
DROP VIEW IF EXISTS github_official_owner_eligibility;       -- V113
```

**第 2 步：打破循环外键（V109 造成 `github_sync_task` ↔ `github_deep_discovery_query` 双向引用）**

```sql
ALTER TABLE github_sync_task
    DROP CONSTRAINT IF EXISTS fk_github_sync_task_official_query;
ALTER TABLE github_deep_discovery_query
    DROP CONSTRAINT IF EXISTS github_deep_discovery_query_active_task_id_fkey;
```

**第 3 步：删核心表上的 github 外键与约束（必须先于删列、先于删表）**

```sql
ALTER TABLE review_task
    DROP CONSTRAINT IF EXISTS ck_review_task_github_sync_origin,
    DROP CONSTRAINT IF EXISTS ck_review_task_origin_type,
    DROP CONSTRAINT IF EXISTS review_task_github_sync_ingestion_id_fkey;
DROP INDEX IF EXISTS uq_review_task_github_sync_ingestion;
DROP INDEX IF EXISTS idx_review_task_origin_status;

ALTER TABLE skill_version
    DROP CONSTRAINT IF EXISTS ck_skill_version_github_sync_revision,   -- V107，同时引用两列
    DROP CONSTRAINT IF EXISTS skill_version_github_sync_ingestion_id_fkey,
    DROP CONSTRAINT IF EXISTS skill_version_github_sync_ingestion_id_key;
```

注意：`ck_skill_version_github_sync_revision` 同时引用 `github_sync_revision` 与 `github_sync_ingestion_id`，因此必须与两列的删除在同一迁移内、且先于任一 `DROP COLUMN`。

**第 4 步：按依赖层删表（L1 → L11，被引用者后删）**

| 层 | 表 |
|---|---|
| L1（无被引用） | `github_sync_worker_presence`、`github_sync_worker_instance`、`github_sync_schedule_policy`、`github_official_resource_cooldown`、`github_official_secondary_cooldown`、`github_official_discovery_budget`、`github_discovery_metrics_epoch`、`github_discovery_outcome_event`、`github_discovery_legacy_unknown_validation`、`github_official_validated_seed`、`github_platform_collection_control`、`github_sync_claim_cursor`、`github_sync_namespace_admission_state`、`github_sync_admission_policy`、`github_discovery_entry`、`github_source_origin` |
| L2 | `github_official_quota_observation`、`github_system_credential_quota`、`github_repository_snapshot_member` |
| L3 | `github_official_discovery_reservation`、`github_discovery_task_hit`、`github_discovery_batch`、`github_sync_path_result`、`github_discovery_candidate_origin` |
| L4 | `github_official_discovery_shard`（自引用，先删叶子） |
| L5 | `github_repository_snapshot`、`github_system_credential` |
| L6 | `github_discovery_candidate` |
| L7 | `github_source_skill_binding` |
| L8 | `github_sync_package_ingestion`（最阻塞：被核心表 + 自身 + snapshot_member 引用） |
| L9 | `github_sync_staged_upload` → `github_sync_package_admission` |
| L10 | `github_sync_task` |
| L11 | `github_sync_run` → `github_source_binding` |

实现建议：全部使用 `DROP TABLE IF EXISTS <name> CASCADE;` 并按上表顺序，`CASCADE` 用于吸收遗漏的内联约束；不可改为无序一次性 `DROP ... CASCADE`，否则会掩盖真实依赖。

**第 5 步：删核心表列（在约束与索引已删之后）**

```sql
ALTER TABLE review_task
    DROP COLUMN IF EXISTS origin_type,
    DROP COLUMN IF EXISTS github_sync_ingestion_id;

ALTER TABLE skill
    DROP COLUMN IF EXISTS github_sync_managed;

ALTER TABLE skill_version
    DROP COLUMN IF EXISTS github_sync_ingestion_id,
    DROP COLUMN IF EXISTS github_sync_revision;
```

**第 6 步：删 `@github` namespace 与系统账号（NO ACTION 外键，必须显式按序删除）**

```sql
DELETE FROM namespace_member WHERE namespace_id IN (SELECT id FROM namespace WHERE slug = 'github');
DELETE FROM namespace      WHERE slug = 'github';
DELETE FROM user_account   WHERE id = 'github-sync-publisher';
```

`namespace.created_by`、`namespace_member.user_id`、`skill.owner_id`、`audit_log.actor_user_id`、`review_task.submitted_by` 等列均无 `ON DELETE` 行为，因此只能靠第 2 步的断言（该 namespace 下无 Skill、无 GITHUB_SYNC 审核）保证无残留引用。

**第 7 步：删函数（`DROP TABLE` 不会清除函数与触发器）**

```sql
DROP TRIGGER IF EXISTS github_sync_run_status_after_task ON github_sync_task;  -- 随表删除，留作防御
DROP FUNCTION IF EXISTS refresh_github_sync_run_status();                       -- V86
```

### 决策 4：代码删除顺序（5 阶段，每阶段可独立编译）

| 阶段 | 内容 | 编译态 |
|---|---|---|
| 1 数据面 | V10000 迁移 + `GithubSyncRemovalMigrationTest` + `FlywayMigrationGuardrailTest` 改造 | 后端编译通过，旧代码仍在 |
| 2 后端骨架 | 删 Go 服务、删 Maven 模块、改 3 个 `pom.xml`、删 `infra/jpa` 29 个实现 | **不可编译**（app/domain 仍引用），需与阶段 3 同 PR |
| 3 后端应用 | 删 app 102 个文件、改 9 个混合文件、改 domain 10 个、改 search 3 个、改 auth 1 个 | 编译通过 |
| 4 前端 | 删 52 个文件、改 21 个共享文件、`make generate-api` 重生成 `schema.d.ts` | 前端构建通过 |
| 5 部署与文档 | 3 个整删、18 个逐行摘除、6 篇整删、17 篇局部改、OpenSpec 归档 | 配置校验通过 |

**阶段 2 与 3 若拆 PR 则中间态不可编译**，因此必须合并为一个 PR 提交；`tasks.md` 仍按阶段组织，但标注二者为一个交付单元。

**阶段顺序不可调换**：先删 `skillhub-search` 的 SQL 拼接再删列，会得到"列还在但没人读"的安全中间态；反之先删列再改 SQL，则任何一次搜索都会 500。

### 决策 5：`skill-search-source` 整体移除，`source` 参数直接消失

**选择**：删除 `SkillSearchSource` 枚举、`SkillSearchController:52` 的 `allowableValues`、`100` 的 `case "github"`、`PostgresFullTextQueryService:128-130` 的 `APPEND`、以及前端 `market-search-controls.tsx` 的来源筛选器。

**理由**：`PLATFORM` 与 `GITHUB` 的区分唯一用途是把同步内容隔出默认市场。同步不存在后，全库都是平台内容，`ALL`/`PLATFORM` 的区分退化为恒等，保留即为无意义抽象。

**代价**：这是公开 API 的破坏性变更，`web/src/shared/hooks/skill-search-source.test.tsx`、`SkillSearchSourcePostgresTest`、`SkillSearchAppServiceTest`、`SkillSearchControllerTest` 等需同步改写，不能整删——它们同时覆盖分页、排序、可见性等非来源行为。

### 决策 6：`review_task` 的来源维度整体移除

**选择**：删 `origin_type` 列、`ReviewOriginType` 枚举、`ReviewTask.forGithubSyncSkillVersion(...)`、`ReviewPortalAppService:196` 的 `Set.of("USER","GITHUB_SYNC")`、`JpaReviewQueueQueryRepository:153-155` 的 `GITHUB_SYNC` 关键词子查询、前端 `REVIEW_ORIGINS`。

**理由**：Q12 已确认审核队列不再需要区分来源。移除后审核队列只剩一条来源路径，筛选器由"来源"维度降为无维度。

**注意**：`JpaReviewQueueQueryRepositoryTest` 中 145 行与 382 行起的两个 github 相关用例需删除，其余用例（关键词不跨 namespace 泄漏等）必须保留。

## Risks / Trade-offs

| 风险 | 缓解 |
|---|---|
| 阶段 2/3 拆分导致不可编译中间态 | 合并为一个 PR；`tasks.md` 显式标注交付单元 |
| `CASCADE` 掩盖遗漏依赖 | 按 L1→L11 顺序执行，`CASCADE` 仅作兜底；`GithubSyncRemovalMigrationTest` 断言最终对象集合为空 |
| 守卫测试被放宽成空壳 | 改造后仍需断言"无重复版本""命名合规""低位段连续"三条 |
| `schema.d.ts` 手改 | 强制走 `make generate-api`，`tasks.md` 中列为独立步骤并附校验 |
| 删列后 `PostgresFullTextQueryService` 未同步 | 阶段 2/3 同 PR；`PostgresFullTextQueryServiceTest` 改写后断言 SQL 不含 `github_sync_managed` |
| OpenSpec 归档后失去 5 个参考规格 | 归档保留全部原文于 `openspec/changes/archive/`，可回溯 |

## Migration Plan

1. 执行 V10000 前先人工跑一次只读核查（可选，用于确认断言前提）：
   `SELECT count(*) FROM skill WHERE github_sync_managed;`
   `SELECT count(*) FROM review_task WHERE origin_type = 'GITHUB_SYNC';`
2. 空库场景：`V1 → V10000` 全量迁移，断言与删除均在同一次执行内完成。
3. 已有开发库场景（曾执行 V76–V126）：V10000 作为增量执行；因 V76–V126 已建表，V10000 的 `DROP ... IF EXISTS` 全部匹配成功。
4. 回退：本迁移不可逆。回退需从备份恢复数据库，或重新执行 V76–V126 建表（不推荐，语义上已放弃该功能）。

## Open Questions

无。全部 31 项决策已在本轮质询中确认。
