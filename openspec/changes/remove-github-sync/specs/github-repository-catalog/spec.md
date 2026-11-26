## Purpose

本 capability 定义按已验证仓库分组浏览已发布 GitHub Skill。本次变将其**整体移除**：仓库视图以 `github_source_binding` 与 `skill.github_sync_managed` 为唯一数据来源，同步引擎退场后既无数据也无维护价值。

## REMOVED Requirements

### Requirement: Browse open-source Skills by repository

**Reason**: 按仓库分组的浏览页 `/github` 需要"已验证的 GitHub 仓库身份"作为分组键。该身份由同步引擎的 `github_source_binding` 产生并存续，同步退场后分组键无来源，页面必然长期为空。保留一个永远为空的公开页面是纯负债。

**Migration**: `/github` 路由、`pages/github-skills.tsx`、`features/github-sync/github-repository-results.tsx`、`github-catalog.css` 删除；主导航 `nav.githubSkills` 项移除；`githubCatalog` 整个 i18n 块（四语言各约 90 行）删除。

### Requirement: Preserve the repository as a distinct resource

**Reason**: "仓库"作为独立资源依赖 `github_source_binding.repository_id`、`github_repository_snapshot` 与 `github_repository_snapshot_member` 三张表。本次变更将三张表全部删除，仓库不再是本产品的领域概念。

**Migration**: `github_repository_snapshots`（V126）相关表删除；`GithubRepositoryCatalogQueryRepository`、`GithubRepositorySnapshotQueryRepository`、`GithubRepositorySnapshotArchiveService` 删除；`/api/web/github-repositories*` 三个读接口移除。

### Requirement: Apply visibility before repository aggregation

**Reason**: 可见性聚合规则服务于"隐藏仓库内无权访问的成员"。仓库聚合本身消失后该规则无适用对象。

**Migration**: 无。相关 SQL 随查询仓库类删除。

### Requirement: Preserve exact repository snapshots

**Reason**: 快照 ZIP 是"某一固定 commit 下整仓可发布成员"的归档，依赖 `github_sync_package_ingestion` 的版本指针。同步链路消失后无法产生新快照，历史快照亦无归属。

**Migration**: `github_repository_snapshot`、`github_repository_snapshot_member` 表删除；`/api/web/github-repositories/{id}/snapshots/{snapshotId}/archive` 下载端点移除；`GithubSnapshotDownload` 组件删除。
