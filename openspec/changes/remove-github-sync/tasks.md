# Tasks

> 交付单元：**阶段 2 与阶段 3 必须同一个 PR**（拆开则中间态不可编译）。其余阶段可独立提交、独立验收。
> 阶段顺序不可调换，理由见 `design.md` 决策 4。

## 阶段 1 · 数据面

- [x] 1.1 新建 `server/skillhub-app/src/main/resources/db/migration/V10000__remove_github_sync.sql`
  - 前置断言：`skill.github_sync_managed=TRUE` 计数 0、`@github` namespace 下 Skill 计数 0、`review_task.origin_type='GITHUB_SYNC'` 计数 0，任一不为 0 则 `RAISE EXCEPTION`
  - 删除顺序：2 个视图 → 打破循环外键 → 核心表约束与索引 → L1~L11 表 → 5 个核心列 → `@github` namespace 与 publisher 账号 → 函数与触发器
  - 完整 DDL 见 `design.md` 决策 3
- [x] 1.2 改造 `server/skillhub-app/src/test/java/com/iflytek/skillhub/db/FlywayMigrationGuardrailTest.java`
  - `versionedMigrations_mustRemainContiguous` 从"1..N 连续"改为"低位段连续且高位段（≥10000）内部无空洞"
  - 保留 `versionedMigrations_mustUseUniqueVersions`、`migrationFiles_mustMatchFlywayVersionedNaming` 原样
- [x] 1.3 新增 `server/skillhub-app/src/test/java/com/iflytek/skillhub/db/GithubSyncRemovalMigrationTest.java`
  - 断言 37 张 `github_*` 表全部不存在
  - 断言 2 个视图、1 个函数不存在
  - 断言 5 个核心列不存在（`review_task.origin_type`、`review_task.github_sync_ingestion_id`、`skill.github_sync_managed`、`skill_version.github_sync_ingestion_id`、`skill_version.github_sync_revision`）
  - 断言 `@github` namespace 与 `github-sync-publisher` 账号不存在
  - 断言 `review_task`、`skill`、`skill_version` 其余列完好（正例对照）
  - 断言空库 V1→V10000 全量迁移成功
- [x] 1.4 删除 6 个 github 迁移专项测试
  - `GithubCatalogNamespaceMigrationTest`、`GithubReviewOriginMigrationTest`、`GithubContentRevisionUpgradeTest`、`GithubDiscoveryOutcomeUpgradeTest`、`GithubCandidateEligibilityUpgradeTest`
  - `GithubMainUpgradeCompatibilityTest` **不删**，见 3.14
- [x] 1.5 验证：`make test-backend-app`

## 阶段 2 · 后端骨架（与阶段 3 同 PR）

- [x] 2.1 删除 `github-sync-go/` 整个目录（62 个 `.go` + `Dockerfile`、`.dockerignore`、`README.md`、`go.mod`）
- [x] 2.2 删除 `server/skillhub-github-sync/` 模块（53 个类 + `pom.xml`）
- [x] 2.3 改 `server/pom.xml`：移除 39 行 `<module>`、76–78 行 `dependencyManagement` 条目
- [x] 2.4 改 `server/skillhub-app/pom.xml:103`、`server/skillhub-infra/pom.xml:32`：移除依赖
- [x] 2.5 删除 `server/skillhub-infra/src/main/java/.../infra/jpa/` 下 29 个 `*Github*` 实现（含 `AesGcmGithubCredentialCipher`）
- [x] 2.6 改 `server/Dockerfile:14`：移除 `COPY skillhub-github-sync/pom.xml`

## 阶段 3 · 后端应用（与阶段 2 同 PR）

- [x] 3.1 删除 `skillhub-app` 102 个纯 github 文件（19 controller + 1 config + 41 dto + 2 listener/metrics + 10 repository + 20 service + 9 task）
- [x] 3.2 改 `controller/portal/SkillController.java`：移除 `githubSkillSourceQueryRepository` 注入（71、82、85、97、108、168）
- [x] 3.3 改 `controller/portal/SkillSearchController.java`：移除 52 行 `allowableValues` 中的 `github`、移除 100 行 `case "github"`
- [x] 3.4 改 `dto/SkillDetailResponse.java`：移除 36、70、72 的 `githubSource` 字段与构造参数
- [x] 3.5 改 `service/SkillSearchAppService.java:310`：移除 `withSource(...)` 来源装配
- [x] 3.6 改 `service/ReviewPortalAppService.java:196、252`：移除 `GITHUB_SYNC` 来源过滤
- [x] 3.7 改 `repository/JpaReviewQueueQueryRepository.java:153-155`：移除 `GITHUB_SYNC` 关键词子查询
- [x] 3.8 改 `exception/GlobalExceptionHandler.java:277-278`：移除 `/internal/github-sync/` 与 credentials 路径脱敏分支
- [x] 3.9 改 `filter/RequestLoggingFilter.java:29-30`：移除跳过日志的 github 路径前缀
- [x] 3.10 改 `src/main/resources/application.yml:163-180`：删除 `skillhub.github-sync:` 全部配置
- [x] 3.11 改 `skillhub-domain` 10 个文件
  - `Skill.java`（34-35、145-155、237-238）、`SkillVersion.java`（43-47、127-136）
  - `ReviewTask.java`（32-33、191-199、216）、`ReviewOriginType.java`（删 `GITHUB_SYNC` 枚举值，或整体删除该枚举并移除引用）
  - `ReviewService.java`（112-119、168-175）、`TeamAutoPublicationPolicy.java:50`、`TeamAutoPublicationService.java`（151、172、186）
  - `SkillReviewSubmitService.java`（92-98、161-167）
  - `SkillPublishService.java`（375-388、472-480、575-617、705-726、820-826、852、868、1020-1024、1058、1254、1312-1326）
  - `SkillLifecycleProjectionService.java:196`
  - `NamespaceService.java:56`：**保留** `"github"` 保留 slug 逻辑判断，仅确认无编译依赖后按需清理
- [x] 3.12 改 `skillhub-search` 3 个文件：`SkillSearchSource.java`（删 `GITHUB`）、`PostgresFullTextQueryService.java`（128-130）、`PostgresFullTextQueryServiceTest.java`（48-51）
- [x] 3.13 改 `skillhub-auth/.../RouteSecurityPolicyRegistry.java`：移除 138-142、315-316、343-345 的 github-repositories 路由策略。**`GitHubClaimsExtractor` 保留不动**
- [x] 3.14 改写 `GithubMainUpgradeCompatibilityTest.java`
  - 删除 45 行 `github_sync_managed` 断言、51 行 `namespace.slug='github'` 断言
  - 删除"V76..V119 成功数 == 44"断言
  - **保留** V1–V75 升级路径覆盖，改为断言升级到 V10000 后核心表结构完好
- [x] 3.15 删除 `skillhub-domain` 测试 `SkillGithubSyncOwnershipTest.java`
- [x] 3.16 改写含 github 断言的 9 个核心测试（不整删）
  - `JpaReviewQueueQueryRepositoryTest`（删 145 行、382 行起两个 github 用例，保留其余）
  - `SkillSearchSourcePostgresTest`、`SkillSearchAppServiceTest`、`SkillControllerTest`、`SkillSearchControllerTest`
  - `RequestLoggingFilterTest`（127-133）、`GlobalExceptionHandlerTest`（197-210）、`ReviewSkillDetailAppServiceTest`（77、157）
- [x] 3.17 删除 49 个 Java 测试文件（16 controller + 6 db 中的 5 个 + 1 metrics + 14 repository + 11 service + 1 domain）+ `support/GithubSyncWorkerCapabilities.java`
- [x] 3.18 验证：`make build-backend` + `make test-backend`

## 阶段 4 · 前端

- [x] 4.1 删除 52 个纯 github 文件
  - `features/github-sync/` 38 个、`features/review/` 5 个、`features/skill/` 2 个
  - `pages/` 5 个：`github-skills.tsx`、`dashboard/github-sync.tsx`、`dashboard/github-deep-discovery.tsx`、`dashboard/namespace-github-sources.tsx` + 1 个测试
  - `i18n/` 2 个 locale 测试
- [x] 4.2 改 `app/router.tsx`：移除 5、105、166-168、175-177、323-326、561-566、575-606、608-615 及 825/847/850/851 路由树注册
- [x] 4.3 改 `pages/dashboard.tsx:63`：移除 `githubSync` 侧边栏项（27、71、78 的 `superAdmin` 机制**保留**，其他项仍在用）
- [x] 4.4 改 `app/layout.tsx:101`：移除 `nav.githubSkills` 导航项（59 行 `superAdmin` 过滤逻辑**保留**）
- [x] 4.5 改 `pages/dashboard/my-namespaces.tsx:137-141`：移除"GitHub 来源"入口
- [x] 4.6 改 `features/namespace/namespace-management-nav.tsx`：移除团队来源入口
- [x] 4.7 改 `api/client.ts`（187、193、257、257-314、310-520、1629-1630）与 `api/types.ts`（256、360、388、397、653、664、700、922、923）
- [x] 4.8 执行 `make generate-api` 重新生成 `api/generated/schema.d.ts`（**禁止手改**）
- [x] 4.9 改审核相关 8 个文件：`review-center-filters.ts`（4、34）、`review-task-summary.tsx:10`、`use-review-detail.ts:50`、`dashboard/reviews.tsx`（71、89、160）、`dashboard/review-detail.tsx`（19、57、172、247、318-319、338-339、377、448）、`dashboard/repository-review.tsx:55`、`dashboard/namespace-reviews.tsx:12`
- [x] 4.10 改搜索与详情 4 个文件：`pages/search.tsx`（157、160）、`pages/skill-detail.tsx`（22、1132、1269、1524-1525）、`features/skill/skill-card.tsx`（84、86、179、181）、`features/search/market-search-controls.tsx`（5、18-19、98-107、174）
- [x] 4.11 改 4 个 locale JSON（`zh`、`en`、`ru`、`ko`）：删除 `githubCatalog`、`deepDiscovery`、`githubSync` 三个整块 + 47 个散落 key
- [x] 4.12 重新生成 `docs/frontend-analytics-manifest.json`，并改写 5 个含 github 断言的 analytics 测试
- [x] 4.13 改写 19 个共享前端测试中的 github 断言（不整删）
- [x] 4.14 验证：`make typecheck-web` + `make test-web` + `make build-web`

## 阶段 5 · 部署、文档与归档

- [x] 5.1 删除 3 个文件：`.github/workflows/pr-github-sync.yml`、`charts/skillhub/templates/github-sync-deployment.yaml`、`deploy/k8s/base/github-sync-deployment.yaml`
- [x] 5.2 改 `Makefile`：1 行 `.PHONY`、149-158 行 `dev-github-sync` 目标
- [x] 5.3 改 3 个 workflow：`backend-tests.yml`（32、33、36）、`pr-tests.yml`（85、86、89）、`publish-images.yml`（48-53 matrix 条目）
- [x] 5.4 改 `compose.release.yml`（107-110、240-259）、`docker-compose.staging.yml`（37-40、92-108）
- [x] 5.5 改 `.env.release.example:136-148`：删除 10 个 `SKILLHUB_GITHUB_SYNC_*` 变量
- [x] 5.6 改 6 个 chart 文件：`_helpers.tpl`（73-79）、`server-deployment.yaml`（99-118）、`secret.yaml`（60、63、64）、`validate.yaml`（129）、`values.yaml`（130、132、133、414-447）、`values.schema.json`（218-220、320-353 含 353 行 `required`）
- [x] 5.7 改 `charts/skillhub/README.md`（65-91、174-175、192、597）与 `charts/skillhub/tests/configuration-contracts.sh`（78-94、567、590-604）
- [x] 5.8 改 4 个 k8s 文件：`backend-deployment.yaml`（30-52）、`configmap.yaml`（33-34）、`secret.yaml.example`（26-30）、`deploy/k8s/README.md`（65-67、172-181）
- [x] 5.9 改 `deploy/storage/silo.env.example:21-24`、`rustfs.env.example:21-24`
- [x] 5.10 改 `scripts/tests/storage-compose-test.sh`（21、25-30、44-55）、`storage-compose-render-test.sh`（15、24、44）
- [x] 5.11 删除 6 篇文档：`docs/github-sync-operations.md`、`docs/api/github-sync-internal.md`、`docs/api/github-sync-internal.openapi.yaml`、`docs/api/github-sync-admin.md`、`docs/api/github-deep-discovery.md`、`docs/api/github-deep-discovery.openapi.yaml`
- [x] 5.12 改 17 篇文档（重点：`09-deployment.md` 166-200、313、318、321、846、849 与 `04-search-architecture.md` 172-184、`02-domain-model.md` 28、29、34）
- [x] 5.13 归档现有 OpenSpec change：把 `openspec/changes/` 下除本变更外的 32 个目录移入 `openspec/changes/archive/`
- [x] 5.14 验证：`make validate-release-config` + chart 渲染测试 + `scripts/tests/*.sh`

## 阶段 6 · 端到端验收

- [x] 6.1 `make build` 全绿
- [x] 6.2 `make test-backend` + `make test-frontend` 全绿
- [x] 6.3 空库从 V1 直跑 V10000 成功，`GithubSyncRemovalMigrationTest` 通过
- [x] 6.4 已有 V126 开发库增量执行 V10000 成功（模拟旧库升级）
- [x] 6.5 前端手点：`/github`、`/dashboard/github-sync`、`/dashboard/github-deep-discovery`、`/dashboard/namespaces/$slug/github-sources` 均 404 或不可达
- [x] 6.6 前端手点：主导航无"开源技能"入口，后台侧边栏无"GitHub 同步"项，市场无来源筛选器，技能详情无 GitHub 来源卡
- [x] 6.7 全局检索确认：`grep -ri "github" --include=*.java --include=*.ts --include=*.tsx` 仅剩 GitHub OAuth 与文档链接
- [x] 6.8 部署冒烟：`docker compose --env-file .env.release -f compose.release.yml up -d` 后 `/actuator/health` 与登录可用
