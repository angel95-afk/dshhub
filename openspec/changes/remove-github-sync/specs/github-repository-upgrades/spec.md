## Purpose

本 capability 定义已安装仓库技能的只读检查、精确版本升级及保护反馈。本次变将其**整体移除**：该能力依赖 `github-repository-installation` 的可用清单，上游能力移除后检查与升级均无输入。

## REMOVED Requirements

### Requirement: REQ-1 Installed members only

**Reason**: "已安装仓库成员"的概念以仓库身份为前提，仓库不再是本产品的领域概念。

**Migration**: CLI `repository check` 命令与 `services/repository-upgrade-service.ts` 删除；`skill-version-order.ts:24` 中针对仓库升级的来源证据注释与相关分支移除。逐 Skill 的 `skillhub check-updates` / `upgrade` 路径**保留且行为不变**。

### Requirement: REQ-2 Version and content comparison

**Reason**: 内容指纹对比的远端一侧来自仓库清单，清单消失后只剩本地信息，无法构成"升级检查"。

**Migration**: `repository-upgrade-command.test.ts` 删除。

### Requirement: REQ-3 Protect local state and other owners

**Reason**: 该保护规则服务于批量升级的边界判定，批量升级移除后无适用对象。

**Migration**: 无。逐 Skill 升级的本地修改保护逻辑不属于本变更范围，保持原样。

### Requirement: REQ-4 Historical metadata compatibility

**Reason**: 历史元数据指此前版本记录在包内的仓库来源提示（`install-service.ts:39` 的 source hint）。该字段为可选提示，不参与本能力判定，随批量能力移除后成为无用字段。

**Migration**: `install-service.ts:39` 的来源提示字段移除；已安装用户的既有 `inventory` 数据可包含该字段，读取端按可选字段处理，不报错。

### Requirement: REQ-5 Honest check and execution results

**Reason**: 无批量检查与批量执行即无对应结果语义。

**Migration**: 无。

### Requirement: REQ-6 Web describes local operations accurately

**Reason**: 网页侧的检查/升级命令入口挂在仓库详情页，随仓库视图一并移除。

**Migration**: `github-repository-results.tsx` 中命令入口与对应 i18n key 移除；`docs/skillhub/guide/cli.md` 与 `en/` 对应章节（中文 12、21、282、749-759；英文 12、22、295、804-814）删除。
