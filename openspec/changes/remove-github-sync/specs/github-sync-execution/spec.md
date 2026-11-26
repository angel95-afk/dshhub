## Purpose

本 capability 定义 Java 与 Go 的任务协作、固定快照、增量更新、限流、断点续跑和可观察状态。本次变将其**整体移除**：`github-sync-go/` 独立服务与 `skillhub-github-sync` Maven 模块在删除后，本 capability 无任何实现载体。

## REMOVED Requirements

### Requirement: 同步 SHALL 使用持久任务和可恢复领取

**Reason**: 任务持久化模型（`github_sync_run`、`github_sync_task`、`github_sync_path_result`）随 Go 服务一并退场。内网无出网链路，任务永远不会被领取。

**Migration**: 三张表在 V10000 中按 L11 → L3 依赖层删除；`refresh_github_sync_run_status()` 函数与 `github_sync_run_status_after_task` 触发器在删表后显式删除（`DROP TABLE` 不自动清除函数）。

### Requirement: 采集服务离线 SHALL 不影响现有 SkillHub 业务

**Reason**: 该保障性要求的前提（存在一个可能离线的采集服务）不再成立。

**Migration**: 部署清单中的 `github-sync` Compose profile、Helm `githubSync.enabled`、K8s 可选 Deployment 全部移除，不再存在该服务。

### Requirement: 内部任务接口 SHALL 只接受采集服务身份

**Reason**: `/internal/github-sync/v1/**` 全部内部接口随 `GithubSyncInternalController` 删除；`ROLE_GITHUB_SYNC_WORKER` 机器身份随 `GithubSyncInternalSecurityConfig` 删除。

**Migration**: `GlobalExceptionHandler` 与 `RequestLoggingFilter` 中针对该路径前缀的特判一并移除，避免留下永不命中的分支。

### Requirement: 每次仓库扫描 SHALL 固定可追溯的 revision

**Reason**: 扫描由 Go 服务执行，服务删除后无扫描行为。

**Migration**: `github_source_binding`、`github_source_skill_binding` 等来源账本表在 V10000 中删除。

### Requirement: 仓库 SHALL 递归发现独立 Skill 包

**Reason**: 递归发现依赖 GitHub Tree API 与受限临时工作区，均随 Go 服务删除。

**Migration**: 无。`GithubSkillPathPolicy` 与 archive 校验相关类随模块删除。

### Requirement: 扫描不完整 SHALL 与上游删除区分

**Reason**: 该区分服务于同步续跑语义，无同步即无意义。

**Migration**: 无。

### Requirement: 单个技能包 SHALL 在资源边界内完整采集

**Reason**: 资源预算（工作区字节数、空闲保留）是 Go 服务运行时参数，随服务删除。

**Migration**: `SKILLHUB_GITHUB_SYNC_MAX_WORKSPACE_BYTES`、`_TOTAL_WORKSPACE_BYTES`、`_MIN_FREE_BYTES`、`_TMPFS_BYTES` 等部署变量与 Helm values 移除。

### Requirement: 采集对象 SHALL 临时隔离并由 SkillHub 复核

**Reason**: 临时暂存上传（`github_sync_staged_upload`）与 Java 侧二次校验（`GithubSyncArchiveValidator`）是同步链路专有环节。

**Migration**: 相关表与类删除；`SKILLHUB_GITHUB_SYNC_STAGING_CAPACITY_LIMIT_BYTES` 与 `GithubSyncStagingCapacityMetrics` 移除。

### Requirement: 同步 SHALL 尊重额度、积压和公平性

**Reason**: 额度控制面向 GitHub API 限额与审核积压，二者均不再存在。

**Migration**: `github_sync_admission_policy`、`github_sync_namespace_admission_state`、`github_official_quota_*`、`github_official_discovery_budget` 等表删除。

### Requirement: 来源不可达 SHALL 有持续恢复路径

**Reason**: 来源可达性追踪的前提是存在外部来源。

**Migration**: `github_source_ignore_patterns` 相关表与 `GithubSourceIgnoreAppService` 删除。

### Requirement: 平台采集 SHALL 支持持久安全暂停与恢复

**Reason**: 暂停控制（`github_platform_collection_control`）用于在运行中的采集服务上安全停机，服务删除后无操作对象。

**Migration**: 表与 `AdminGithubPlatformControlController`、`GithubPlatformControlController` 删除。
