## Purpose

本 capability 定义同步内容的独立审核队列、版本更新、冲突处理及发布保护。本次变将其**整体移除**：审核队列回归单一来源（普通用户提交），`review_task` 的来源维度整体删除。

## REMOVED Requirements

### Requirement: 同步内容 SHALL 使用独立审核队列

**Reason**: 独立队列存在的唯一目的是把同步内容与普通用户提交分开呈现。同步内容不存在后，队列只剩一条来源路径，独立或合并的区分退化为恒等。

**Migration**: `ReviewPortalAppService` 的来源过滤（196、252）移除；`JpaReviewQueueQueryRepository` 中 `GITHUB_SYNC` 关键词子查询（153-155）移除；前端 `REVIEW_ORIGINS` 常量与来源筛选器移除。历史 `GITHUB_SYNC` 审核行由 V10000 的前置断言保证为 0 后整列删除。

### Requirement: 同一 Skill SHALL 只有一个当前可批准的同步目标

**Reason**: 该排他性规则服务于上游 revision 竞争，无上游即无竞争。

**Migration**: `github_sync_package_ingestion` 的发布租约列（`publication_token`、`publication_expires_at`、`publication_retry_at`、`publication_attempts`、`published_skill_id`、`published_version_id`）随表删除。

### Requirement: 同步审核 SHALL 提供可核对的内容差异

**Reason**: 差异对比以固定 commit 与上游文件树为基线，二者随同步引擎消失。

**Migration**: `features/review/github-sync-review-source.tsx` 与 `github-sync-review-queue.tsx` 删除；`review-detail.tsx` 中 `GithubSyncReviewDiff`、`GithubSyncReviewEvidence` 装配移除。

### Requirement: GitHub 同步内容 SHALL 使用独立的内容修订号

**Reason**: 内容修订号（`github_sync_revision`）只服务于同步语义的"第 N 次内容变化"，与技术版本号并列存在会产生两套版本语义。同步退场后保留它会让 `skill_version` 携带一个永不递增的列。

**Migration**: `skill_version.github_sync_revision` 与 `skill_version.github_sync_ingestion_id` 两列在 V10000 中删除。注意 `ck_skill_version_github_sync_revision`（V107）**同时引用这两列**，必须先 DROP 该约束再删列，否则迁移失败。

### Requirement: 同步 SHALL 保留来源权利与许可证信息

**Reason**: 许可证与 NOTICE 证据来源于上游包，随同步链路消失。

**Migration**: `skillDetail.githubLicenseScope*` 与审核页许可证证据相关 i18n key 移除。

### Requirement: 同步冲突 SHALL 阻断自动绑定

**Reason**: 冲突绑定发生在"上游路径与既有 Skill 坐标相撞"时，无同步即无该场景。

**Migration**: `github_source_skill_binding` 表与 `GithubSyncConflictBinder` 组件删除；人工确认绑定的 `GithubSyncManualBindingAppService` 删除。

### Requirement: 路径变化与消失 SHALL 不误删 Skill

**Reason**: 上游路径迁移检测依赖持续扫描，扫描能力已移除。

**Migration**: `GithubSyncPathIdentity` 组件与路径存在性追踪表删除。
