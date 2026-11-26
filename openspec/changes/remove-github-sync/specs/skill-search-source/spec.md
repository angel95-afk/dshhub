## Purpose

本 capability 定义兼容的来源筛选与来源感知的市场发现。本次变将其**整体移除**：`PLATFORM` 与 `GITHUB` 的区分唯一用途是把同步内容隔出默认市场，同步内容不存在后所有内容都是平台内容，该维度退化为恒等。

## REMOVED Requirements

### Requirement: REQ-SOURCE-01 Compatible source filtering

**Reason**: `source` 参数（`all|platform|github`）的三个取值中，`github` 将永远返回空集合，`platform` 与 `all` 将永远等价。保留一个只有恒等语义的三值参数，比移除它更容易误用。

**Migration**: 搜索接口的 `source` 参数移除，`SkillSearchController:52` 的 `allowableValues` 与 `:100` 的 `case "github"` 删除，`SkillSearchSource` 枚举删除，`PostgresFullTextQueryService:128-130` 的 `s.github_sync_managed` 拼接删除。旧客户端传 `source=github` 将收到参数校验失败；该 fork 尚未分发 CLI，接受此破坏性变更。

### Requirement: REQ-SOURCE-02 Source-aware market navigation

**Reason**: 市场来源切换器用于在"全部 / 平台 / GitHub"之间跳转，三态退化为单态后切换器无意义。

**Migration**: `market-search-controls.tsx` 的来源选择（5、18-19、98-107、174）移除；`search.source` i18n 块（`github`、`platformHint`、`githubHint`、`allHint`）移除；`/github` 作为"显式 GitHub 来源"的目录入口一并移除。

### Requirement: REQ-SOURCE-03 Homepage and catalog boundaries

**Reason**: 首页自动榜单对 `PLATFORM` 的应用前提是"GITHUB 内容需被排除"，该前提消失后榜单无需来源判定。

**Migration**: 首页排行榜的自动来源过滤移除；显式策展配置**保留**（属 `add-curated-homepage-recommendations`，与本变更无关）。

### Requirement: REQ-SOURCE-04 Market card information hierarchy

**Reason**: 市场卡片的"来源"信息位用于标注内容来自平台还是 GitHub，单来源下该信息位无内容可填。

**Migration**: `skill-card.tsx`（84、86、179、181）的来源判定与徽标移除；`skillCard.github` i18n key 移除。

### Requirement: REQ-SOURCE-05 Unified market filter panel

**Reason**: 统一筛选面板中的"来源"分组是 GitHub 维度的唯一界面承载，维度移除后该分组为空。

**Migration**: 筛选面板的来源分组移除；`search.market` 中 `githubEmpty`、`viewPlatform` 两个 key 移除；面板其余筛选维度（排序、可见性等）**保留不变**。

### Requirement: REQ-SOURCE-06 Incremental market list and return context

**Reason**: 增量列表与返回上下文依赖"筛选状态可往返恢复"，来源维度是筛选状态的一部分。移除来源维度后，该要求描述的增量加载与返回恢复行为**本身仍需保留**，但它已不属于本 capability 的范围。

**Migration**: 增量加载、返回上下文恢复、范围切换、移动端标签等行为迁入既有市场发现能力继续生效；`SkillSearchSourcePostgresTest`、`SkillSearchAppServiceTest`、`SkillSearchControllerTest`、`shared/hooks/skill-search-source.test.tsx` 等测试中**仅**与来源相关的用例删除，其余分页、排序、可见性用例改写为不带 `source` 参数的形式并保留。
