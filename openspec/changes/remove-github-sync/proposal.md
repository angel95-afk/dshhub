# Proposal

## Why

SkillHub 本仓库是面向内网私有化部署的 fork，内网环境不存在对 github.com 的可用出网链路，GitHub 同步采集服务（Java `skillhub-github-sync` 模块 + `github-sync-go` 独立服务）在该场景下无法运行，也无法被配置为可用状态。

该功能在代码与数据结构上占据了显著体量：约 375 个待删除文件、37 张 `github_*` 表、2 个视图、1 个函数、1 个触发器、5 个核心表新增列，并派生出一个独立的公开来源目录页（`/github`）、一套按仓库浏览与 CLI 批量安装/升级命令、以及一套市场搜索来源筛选维度。

这些周边能力全部以 github-sync 的数据结构为物理载体。移除同步引擎后，它们既无法产生数据也无法被维护，继续保留即成为纯负债。因此本变更将 github-sync 及其派生能力作为一个整体从产品中退场（retire），并把仓库恢复到只包含"平台内容 + 团队内容"的单一来源模型。

本 fork 尚未部署过任何环境，`flyway_schema_history` 不存在既有记录，因此本次不需要为历史库编写兼容迁移分支；但仍需要一条版本化迁移来保证已执行过 V76–V126 的开发库能够回到干净状态。

## What Changes

### BREAKING

- **移除 7 个产品能力**：`github-source-management`、`github-sync-execution`、`github-sync-review`、`github-repository-catalog`、`github-repository-installation`、`github-repository-upgrades`、`skill-search-source` 全部整体移除，不保留降级形态。
- **公开 API 破坏性变更**：
  - 搜索接口的 `source` 参数（`all|platform|github`）整体移除；`/api/v1/skills/search` 与 CLI/compat 搜索不再接受该参数。
  - `/api/web/github-repositories*` 三个读接口（仓库目录、成员清单、安装清单）移除。
  - `/api/web/github-repositories/{id}/snapshots/{snapshotId}/archive` 快照下载移除。
  - CLI 的 `repository install`、`repository check`、`repository upgrade` 三个命令移除；`skillhub repository` 子命令不再存在。
  - `/internal/github-sync/v1/**` 全部内部接口移除，`ROLE_GITHUB_SYNC_WORKER` 机器身份移除。
- **数据结构破坏性变更**：37 张 `github_*` 表、2 个视图、1 个函数、1 个触发器删除；`review_task`、`skill`、`skill_version` 上的 5 个 github 专用列及其 6 个约束、3 个索引删除。
- **部署契约变更**：`github-sync` Compose profile、Helm `githubSync.*` values、`deploy/k8s` 可选 Deployment、`SKILLHUB_GITHUB_SYNC_*` 环境变量组全部移除。已使用这些配置的部署文件在升级后不再生效。
- **路由移除**：`/github`、`/dashboard/github-sync`、`/dashboard/github-deep-discovery`、`/dashboard/namespaces/$slug/github-sources` 四条前端路由移除。

### 保留

- **GitHub OAuth 登录完整保留**（`GitHubClaimsExtractor`、`/oauth2/authorization/github`、登录按钮）。它是通用身份提供方接入，与 github-sync 无代码耦合；删除会违反 `AGENTS.md` 中"Authentication must remain extensible beyond a single identity provider"的项目原则。内网不启用该 provider 即可，无需删除代码。
- `BasicPrePublishValidator` 的 `ghp_` 密钥扫描规则保留（通用安全能力）。
- `S3StorageService.listPrefixUsage` 保留（通用能力）。
- 迁移历史 V76–V126 **原样保留、不改写、不改 checksum**，作为该功能曾经存在的可追溯记录。

### 迁移策略

- 不删除、不改写任何既有迁移文件（V76–V126 保持原样）。
- 新增 **V10000** 作为全量清理迁移：先断言无存量数据（不符则 `RAISE EXCEPTION` 中止），再按依赖顺序删除视图、打破循环外键、删表、删核心列、删 `@github` namespace 与 `github-sync-publisher` 账号、最后删函数。
- 采用 V10000 高位段而非 V127，避免与上游 `iflytek/skillhub` 后续发布的 V127+ 撞号；代价是 `FlywayMigrationGuardrailTest` 的"1..N 连续"断言必须同步改造为"允许 fork 高位段"。

## Capabilities

### New Capabilities

无。本变更不引入新能力。

### Removed Capabilities

- `github-source-management`：公开来源登记、自动发现、归属、规则、权限及管理页面。
- `github-sync-execution`：Java 与 Go 的任务协作、固定快照、增量更新、限流、断点续跑和可观察状态。
- `github-sync-review`：同步内容的独立审核队列、版本更新、冲突处理及发布保护。
- `github-repository-catalog`：按已验证仓库分组浏览已发布 GitHub Skill。
- `github-repository-installation`：仓库当前可用成员的安全解析、批量安装及结果呈现。
- `github-repository-upgrades`：已安装仓库技能的只读检查、精确版本升级及保护反馈。
- `skill-search-source`：兼容的来源筛选与来源感知的市场发现。

### Modified Capabilities

无已归档 capability 需要修改。上述 7 个能力均来自尚未归档的 OpenSpec 变更，本变更对其整体 REMOVED。

## Impact

- **后端模块**：删除 Maven 模块 `server/skillhub-github-sync/`（53 个类），`skillhub-app` 与 `skillhub-infra` 解除依赖；删除 `github-sync-go/` 整个 Go 服务（62 个 `.go`）。
- **后端代码**：`skillhub-app` 102 个纯 github 文件删除、9 个文件局部改写；`skillhub-infra` 29 个 JDBC 实现删除；`skillhub-domain` 10 个文件局部改写（含 `Skill`、`SkillVersion`、`ReviewTask`、`SkillPublishService`）；`skillhub-search` 3 个文件；`skillhub-auth` 路由策略 1 个文件。
- **前端**：52 个纯 github 文件删除、21 个共享文件局部改写（含 4 个 locale JSON 与生成文件 `schema.d.ts`，后者必须经 `make generate-api` 重新生成而非手改）。
- **数据库**：37 表 + 2 视图 + 1 函数 + 1 触发器 + 5 核心列 + 6 约束 + 3 索引；`@github` namespace 1 行、`github-sync-publisher` 账号 1 行、OWNER membership 1 行。
- **测试**：Java 49 个测试整删、10 个核心测试改写断言（含 `GithubMainUpgradeCompatibilityTest`、`JpaReviewQueueQueryRepositoryTest`、`PostgresFullTextQueryServiceTest`）；Web 14 个测试整删、19 个共享测试改写断言；新增 1 个 `GithubSyncRemovalMigrationTest`。
- **构建与部署**：3 个文件整删（`pr-github-sync.yml`、Helm `github-sync-deployment.yaml`、K8s `github-sync-deployment.yaml`），18 个文件逐行摘除。
- **文档**：6 篇整篇删除（运行手册、内部协议、管理接口、深度探索协议及其 OpenAPI），17 篇局部修改。
- **OpenSpec 归档**：现有 32 个 change 目录整体移入 `openspec/changes/archive/`；其中 `add-github-skill-sync`、`add-github-repository-catalog`、`install-github-repository-skills`、`upgrade-github-repository-skills`、`separate-market-skill-sources` 5 个由本变更 REMOVED 覆盖。

## Risks

- **BREAKING 面无向后兼容窗口**：`source` 参数与三条 CLI 命令直接消失。已分发旧版 CLI 的用户会看到命令不存在；旧版前端调用 `source=github` 会收到参数校验失败。该 fork 尚未对外分发 CLI，风险可接受，但需在发布说明中明确。
- **`FlywayMigrationGuardrailTest` 改造**：该测试当前从 `expected=1` 逐位比对，任何版本跳跃都会失败。改造需保持"无重复、无空洞、命名合规"的实质约束，只放宽高位段。
- **`@github` namespace 删除不可逆**：断言只能证明"清零时刻无数据"，无法证明历史数据曾经不重要。断言失败时应人工确认，不得用跳过断言的方式推进。
- **重新生成 `schema.d.ts`**：约 200+ 处 github 路径与 schema 由 `make generate-api` 产出；手改会在下次生成时被覆盖。
- **上游合并冲突**：V76–V126 保留但业务已退场，未来合上游 github-sync 相关改动时需明确拒绝，不能误合。
- **`GithubMainUpgradeCompatibilityTest` 的部分覆盖会丢失**：该测试虽以 Github 命名，实际验证 V1–V75 → 最新版的升级路径。改写断言而非整删，以保留主升级路径覆盖。
