## Purpose

本 capability 定义公开 GitHub 来源的登记、自动发现、归属、规则、权限及管理页面。本次变更将其**整体移除**：内网部署不存在可用出网链路，该能力无法运行，且其派生页面与数据结构随同步引擎一并无维护价值。

## REMOVED Requirements

### Requirement: 平台 SHALL 提供官方探索与可配置的公开 GitHub 来源入口

**Reason**: 该能力依赖对 github.com 的持续出网访问。内网私有化部署环境下该链路不可用，能力无法进入可运行状态，继续保留只增加维护面与攻击面。

**Migration**: 无数据迁移。`V10000__remove_github_sync.sql` 在断言 `@github` namespace 下无 Skill 后，删除该 namespace 及其管理页面路由。

### Requirement: 来源 SHALL 归属明确的 namespace

**Reason**: 随来源管理能力一并移除；`@github` CATALOG namespace 由 V76 创建、V10000 删除。

**Migration**: `@github` namespace 与 `github-sync-publisher` 系统账号在 V10000 中按 `namespace_member` → `namespace` → `user_account` 顺序显式删除。二者被核心表以 `NO ACTION` 外键引用，无级联，故必须依赖前置断言保证无残留引用。

### Requirement: TEAM 来源 SHALL 使用来源级可见性

**Reason**: TEAM 来源是平台来源在团队维度的登记视图，来源能力不存在后该视图无数据可取。

**Migration**: `/dashboard/namespaces/$slug/github-sources` 路由移除，团队管理导航中的来源入口移除。

### Requirement: 来源与发现规则 SHALL 分别管理生命周期

**Reason**: 发现规则依赖 Go 采集服务执行，服务随本变更删除。

**Migration**: 无。`github_discovery_*` 系列表在 V10000 中删除。

### Requirement: 后台 SHALL 提供统一配置和反馈入口

**Reason**: 后台 `/dashboard/github-sync` 与深度探索页是唯一入口，随能力移除。

**Migration**: 两条前端路由移除，后台侧边栏 `githubSync` 项移除。

### Requirement: 市场 SHALL 展示可公开的 GitHub 来源

**Reason**: 同步内容不存在后，市场中不再有 GitHub 来源内容需要标识。技能卡片的来源徽标与技能详情的 GitHub 来源卡一并移除。

**Migration**: `SkillDetailResponse.githubSource` 字段、`features/skill/github-skill-source.tsx` 及 `skill-card.tsx` 的来源判定移除。

### Requirement: 凭证配置 SHALL 只服务于公开读取

**Reason**: 凭证池仅用于提升 GitHub API 读取额度，服务删除后无消费者。

**Migration**: `github_system_credential`、`github_system_credential_quota` 表删除；`SKILLHUB_GITHUB_SYNC_CREDENTIAL_KEYS` 与 `SKILLHUB_GITHUB_SYNC_ACTIVE_CREDENTIAL_KEY_VERSION` 环境变量移除；`AesGcmGithubCredentialCipher` 删除。

### Requirement: 平台 SHALL 提供单一技能同步入口和统一仓库列表

**Reason**: 该入口是同步任务的唯一人工触发起始点，任务模型随 `skillhub-github-sync` 模块删除。

**Migration**: 无。
