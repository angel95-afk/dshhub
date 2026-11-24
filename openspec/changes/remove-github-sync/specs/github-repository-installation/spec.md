## Purpose

本 capability 定义仓库当前可用成员的安全解析、批量安装及结果呈现。本次变将其**整体移除**：该能力的输入是仓库目录接口（`github-repository-catalog`），上游能力移除后本能力无数据可解析。

## REMOVED Requirements

### Requirement: REQ-1 Current available repository plan

**Reason**: 安装清单由 `/api/web/github-repositories/{id}/install-plan` 提供，该端点依赖仓库目录与成员快照，二者随 `github-repository-catalog` 一并移除。

**Migration**: 端点删除；`GithubRepositoryInstallController`、`GithubRepositoryInstallPlanResponse`、`GithubRepositoryInstallPlanTest` 删除；`GithubRepositoryInstall` 前端组件删除。

### Requirement: REQ-2 Exact identities and continuing authorization

**Reason**: 精确身份（版本 + 指纹）来自同步摄入记录 `github_sync_package_ingestion`，该表在 V10000 中删除。

**Migration**: 无。授权判定回归既有逐 Skill 安装路径，`/api/cli/v1/skills/{ns}/{slug}/versions/{v}/download` 不受影响。

### Requirement: REQ-3 Reuse per-skill safe installation

**Reason**: 该要求本身是把批量安装降解为逐 Skill 安装。批量入口移除后，逐 Skill 安装路径（`install-service.ts`）**保留且行为不变**。

**Migration**: CLI `repository install` 命令与 `services/repository-install-service.ts` 删除；`skillhub install` 命令不受影响。

### Requirement: REQ-4 Honest batch results

**Reason**: 无批量操作即无批量结果语义。

**Migration**: `repository-compatibility.test.ts`、`repository-install-command.test.ts`、`repository-install-service.test.ts` 删除。

### Requirement: REQ-5 Web installation entry and snapshot separation

**Reason**: 网页安装入口挂在仓库详情页，仓库详情随本变更移除。

**Migration**: `github-repository-install.tsx` 与其测试删除；`github-repository-results.tsx` 中的安装入口移除。
