# Design 索引 — DSH 插件市场 v1.0

原单体 `design.md`（4352 行）已按 **总体设计（根级）+ `backend/` + `frontend/`** 重构为 29 个文件（本索引 + 26 个分册 + 2 个子索引）。重构**只移动文字，不改内容与章节编号**：拆分前后 `##` 17 个、`###` 67 个、`####` 9 个、代码围栏 76 个逐项相等，正文逐行搬运。唯一的内容改动是 §7.2 一行 YAML 引号修复（描述文字未变，见 §5 第 5 条）。因此全文所有 `§N.M` 交叉引用仍然有效。

## 1. 模块地图

推荐阅读顺序：`00` → `backend/01`–`16` → `frontend/01`–`05` → `01`–`04`（根级 01–04 是跨前后端的总体章节 §11–§16，物理排序靠前但逻辑上在最后）。

### 总体设计（根级，与 backend/、frontend/ 同级）

| 文件 | 覆盖章节 | 行数 | 什么时候读它 |
|---|---|---|---|
| [`00-overall-goals-and-landscape.md`](00-overall-goals-and-landscape.md) | §1–§2 | 167 | 第一次接触本项目；要改架构决策或部署拓扑 |
| [`01-overall-deployment-and-config.md`](01-overall-deployment-and-config.md) | §11 | 169 | 加环境变量，改 nginx / compose / Helm |
| [`02-overall-observability.md`](02-overall-observability.md) | §12 | 135 | 加指标、排查线上故障 |
| [`03-overall-deviations-risks-phasing.md`](03-overall-deviations-risks-phasing.md) | §13–§15 | 100 | 评审「为什么和 PRD 不一致」、排期 |
| [`04-overall-tasks-and-assertions.md`](04-overall-tasks-and-assertions.md) | §16 + 附 | 114 | 拆任务、跑回归、**查安全断言 S1–S24** |

### 后端（`backend/`）

后端按功能域分为：分层与边界（01–02）、关键链路（03–05）、领域状态与数据（06–08）、接口契约（09–14）、索引与扫描（15–16）。

| 文件 | 覆盖章节 | 行数 | 什么时候读它 |
|---|---|---|---|
| [`backend/01-layering-packages.md`](backend/01-layering-packages.md) | §3.1–§3.2 | 362 | 新建包 / 类；确认「某能力该复用还是新建」的前置阅读 |
| [`backend/02-controllers-adapters-reuse.md`](backend/02-controllers-adapters-reuse.md) | §3.3–§3.5 | 134 | 新增 Controller 或适配器；判定复用边界 |
| [`backend/03-flow-submit.md`](backend/03-flow-submit.md) | §4.1–§4.2 | 223 | 改提交校验、submission worker、异步状态推进 |
| [`backend/04-flow-index-and-install-doc.md`](backend/04-flow-index-and-install-doc.md) | §4.3–§4.5 | 161 | 改索引重建、指针切换、304、安装文档模板 |
| [`backend/05-flow-review-and-file-browse.md`](backend/05-flow-review-and-file-browse.md) | §4.6–§4.7 | 204 | 改审核链路、审核页包内文件浏览 |
| [`backend/06-domain-state-machines.md`](backend/06-domain-state-machines.md) | §5 | 221 | 动任何状态、状态流转或「某态可不可达」 |
| [`backend/07-data-model-ddl.md`](backend/07-data-model-ddl.md) | §6.1–§6.2 | 421 | 改表结构、写迁移 |
| [`backend/08-data-retention-and-staging-window.md`](backend/08-data-retention-and-staging-window.md) | §6.3–§6.4 | 40 | 查留存策略、暂存包可浏览窗口 |
| [`backend/09-api-public-anonymous.md`](backend/09-api-public-anonymous.md) | §7.1 | 152 | 改匿名段（索引、安装文档、市场页）契约 |
| [`backend/10-api-portal-openapi.md`](backend/10-api-portal-openapi.md) | §7.2 | 715 | 改需登录端点。**单块 YAML，不要拆** |
| [`backend/11-api-schemas.md`](backend/11-api-schemas.md) | §7.3 | 261 | 改请求 / 响应 schema |
| [`backend/12-api-error-codes.md`](backend/12-api-error-codes.md) | §7.4 + §7.4.1 | 163 | 查错误码、新增错误分支（同步校验 51 条 + 异步扫描 3 条） |
| [`backend/13-api-rate-limits.md`](backend/13-api-rate-limits.md) | §7.5 | 26 | 动限流 category 或阈值 |
| [`backend/14-api-staged-file-security.md`](backend/14-api-staged-file-security.md) | §7.6 | 91 | **改文件浏览前必读**：穿越、解压炸弹、二进制、一致性、审计 |
| [`backend/15-index-generation.md`](backend/15-index-generation.md) | §8 | 180 | 改索引字段映射、分发来源判定、构建期强校验 |
| [`backend/16-scan-integration.md`](backend/16-scan-integration.md) | §9 | 148 | 改扫描契约、裁决规则、脱敏 |

### 前端（`frontend/`）

前端按功能域分为：路由守卫（01）、页面与组件（02）、数据与状态（03）、i18n（04）、埋点（05）。

| 文件 | 覆盖章节 | 行数 | 什么时候读它 |
|---|---|---|---|
| [`frontend/01-routing-and-guards.md`](frontend/01-routing-and-guards.md) | §10.1 | 35 | 加路由、改守卫或权限分流 |
| [`frontend/02-pages-and-component-tree.md`](frontend/02-pages-and-component-tree.md) | §10.2 | 160 | 改页面结构或组件树 |
| [`frontend/03-query-keys-and-state.md`](frontend/03-query-keys-and-state.md) | §10.3–§10.4 | 59 | 加 query key、改轮询或乐观更新 |
| [`frontend/04-i18n-keys.md`](frontend/04-i18n-keys.md) | §10.5 | 212 | 新增文案键（`zh/en/ko/ru` 必须齐平） |
| [`frontend/05-analytics.md`](frontend/05-analytics.md) | §10.6 | 28 | 加埋点事件 |

**总量对照**：26 个分册合计 **4681 行**（含每个分册统一的 5 行文件头，26 × 5 = 130 行）。其中**正文 4551 行** = 数据源 **4352 行** + 拆分后新增 **199 行**（S17–S22 断言 13 行、S23–S24 断言 9 行、`PRD` v2.16 可见范围同步 171 行、`PRD` v2.17 口径对齐 6 行；明细见 §5 第 7 条）。数据源本身仍是 4352 行、未改。

## 2. 按角色的最小阅读路径

- **后端实现**：[`backend/07-data-model-ddl.md`](backend/07-data-model-ddl.md) → [`backend/06-domain-state-machines.md`](backend/06-domain-state-machines.md) → [`backend/03-flow-submit.md`](backend/03-flow-submit.md) / [`backend/04-flow-index-and-install-doc.md`](backend/04-flow-index-and-install-doc.md) / [`backend/05-flow-review-and-file-browse.md`](backend/05-flow-review-and-file-browse.md) → [`backend/10-api-portal-openapi.md`](backend/10-api-portal-openapi.md) → [`backend/12-api-error-codes.md`](backend/12-api-error-codes.md)
- **前端实现**：[`frontend/01-routing-and-guards.md`](frontend/01-routing-and-guards.md) → [`frontend/02-pages-and-component-tree.md`](frontend/02-pages-and-component-tree.md) → [`frontend/03-query-keys-and-state.md`](frontend/03-query-keys-and-state.md) → [`backend/10-api-portal-openapi.md`](backend/10-api-portal-openapi.md)（只看自己那几个端点）→ [`backend/12-api-error-codes.md`](backend/12-api-error-codes.md)（错误码分支）→ [`frontend/04-i18n-keys.md`](frontend/04-i18n-keys.md) / [`frontend/05-analytics.md`](frontend/05-analytics.md)
- **安全评审**：[`backend/14-api-staged-file-security.md`](backend/14-api-staged-file-security.md) → [`04-overall-tasks-and-assertions.md`](04-overall-tasks-and-assertions.md)（S1–S24）→ [`backend/16-scan-integration.md`](backend/16-scan-integration.md)
- **索引 / 分发**：[`backend/15-index-generation.md`](backend/15-index-generation.md) → [`backend/04-flow-index-and-install-doc.md`](backend/04-flow-index-and-install-doc.md)
- **部署与运维**：[`01-overall-deployment-and-config.md`](01-overall-deployment-and-config.md) → [`02-overall-observability.md`](02-overall-observability.md) → [`00-overall-goals-and-landscape.md`](00-overall-goals-and-landscape.md)（§2 拓扑）

## 3. 引用规则（重构后必须遵守）

1. **章节号是全局唯一标识，不随文件走**。`§7.4` 永远指错误码表，`§16 S20` 永远指 [`04-overall-tasks-and-assertions.md`](04-overall-tasks-and-assertions.md) 里的断言，不受文件边界影响。
2. **新增内容沿用 `§N.M` 编号**（如 `§7.7`、`§7.4.2`），不要按文件重新起相对编号，否则跨文件引用立刻失效。
3. **跨文件引用写编号，不写文件名**。文件名会随重构变，编号不会。
4. **新主题优先挂到已有章节下**；确实需要新章时编号接着 `§16` 往后排（`§17`、`§18`），并同步更新本表。
5. **版本标注的读法**：正文里 `（PRD §6.3 v2.16）`、`（PRD §8.4 v2.16）` 这类括注标的是**该口径的引入版本**，不是"当前产品版本"。现行口径一律以 **`PRD v2.18`（冻结稿）** 为准：v2.17 只改了**放宽可见范围不再走审核**（改为只能随新版本提交）；v2.18 只把 `PRD §11` **验收第 8 项**的进度链终态展开成四档（走查项写全，不改判定）。三档定义、标签、收录条件、四行判定表与稳定说明句全部未动，因此这些 v2.16 标注仍然成立，不必逐处改写。

## 4. 断言索引（回归门禁）

- **S1–S16** → [`04-overall-tasks-and-assertions.md`](04-overall-tasks-and-assertions.md)「审核页『包内文件』的安全回归断言」：响应头三件套、禁危险渲染、路径穿越、解压炸弹、越权、指纹一致、审计 fail-closed、权限分流。
- **S17–S22** → 同一文件「其余硬规则的回归断言」：安装提示词唯一性、索引与详情页共用来源判定、详情页无版本选择器、审核面不透出内部动作、清单不提供逐目录接口、禁止按 message 分支。
- **S23–S24** → 同一文件「可见范围的回归断言」（`PRD §6.3/§8.2/§8.4`，v2.16 引入、v2.17 定稿）：索引只收 `PUBLIC`（非公开可见范围的插件绝不进 `/dsh/index.json`，且这是过滤不是构建失败）、范围外用户一律看不见且状态文案按四行判定表（详情页 403 / 安装文档 404 / 四档文案 + 三条稳定说明句）。

## 5. 完整性声明

1. 结构：**拆分当时** `##` 17 / `###` 67 / `####` 9 / 代码围栏 76 逐项相等（脚本断言，非人工统计）。当前实测 `##` 17 / `###` 69 / `####` 9 / 代码围栏 78——多出的 2 个 `###` 是两个新断言小节（S17–S22、S23–S24，均在 04 分册），多出的 2 个围栏是 §7.3 新增的可见性字段片段，均见第 7 条。
2. 逐字节：26 个分册去掉统一文件头后按文档顺序拼接，与 `.dev/design.full.md` **逐字节相等（除 §5.7 记录的拆分后编辑）**（sha256 见第 4 条）；脚本每次生成都做该比对。
3. 边界：每个分册都是数据源里**连续的行区间**，首尾相接、无重叠、无遗漏；章节号与正文的改动只有三类例外：§7.2 那一行 YAML 引号（见第 5 条）、拆分后补入的 S17–S24 断言、`PRD` v2.16 可见范围同步与 v2.17 口径对齐（见第 7 条）。
4. 可复现数据源 `.dev/design.full.md`（gitignored，未入库）：`sha256 8abe40b57379278f6cb2972b3886b37a24a980a4bd4ba065b9a27bb67c5abab0 / 4352 行 / 260063 chars`。日后若需再拆一次，按 §1 的表（每个分册的数据源行区间）机械切片即可；当初的 `scripts/split-design.py` 记录的是重排前的旧文件名，已随本次重构删除，不要再引用。
5. 相对最初的单体 design：**正文层面**无任何改动，例外只有三处：§7.2 一行 YAML 引号修复——`'200': { description: "…" }` 只给 `description` 值加了双引号，描述文字一字未变，修掉 PyYAML 的 `ParserError`（该行原会让整块 462 行 YAML 不可解析）；拆分后才补入的 S17–S24 八行断言与 `PRD` v2.16 可见范围同步；以及 `PRD` v2.17 回改的 4 处旧口径（见第 7 条，均不在数据源里）。
6. 行数：旧索引记原单体为 4353 行，数据源实测 4352 行；差的 1 行全部落在旧脚本按行号切片时被 `rstrip()` 掉的块尾空行上（17 处边界中 10 处按「标题 / 块级分隔前留 1 行空行」复原），不涉及任何正文行。
7. 拆分后的编辑（导致与数据源不再逐字节相等）：2026-11-26 补 S17–S22 六行断言（04 分册，+13 行）、补 S23–S24 两条可见范围断言（04 分册，+9 行）、同日 §7.2 一行 YAML 引号修复；2026-11-26 按 `PRD` v2.16 冻结稿同步「可见范围」（03 / backend 01·03·06·07·10·11·12·13·15 / frontend 02·04 共 12 个分册、+171 行，含新增 2 条错误码 `visibilityInvalid` / `visibilityWidenPending`、1 个窄端点 `PATCH .../visibility`、`plugin.visibility` 列、`PluginVisibilityPolicy` 唯一判定入口）；2026-11-26 按 `PRD` v2.17 回改四处旧口径（03 §13 #22 与 §14.2 Q11 与 §15、backend 06 `LISTED` 行、backend 10 与 12、frontend 04 新增 `plugin.visibility.formHint.*` 三键 = +6 行），并把 06 的「前端展示」列由"按空间是否公开猜"改为「按 §8.4 四行判定表取」；2026-11-26 按 `PRD` **v2.18**（只展开验收第 8 项的终态四档）同步 `backend/06` 进度链硬约束第 5 条（1 行等长替换，总行数不变）。数据源 `.dev/design.full.md` 未改，其 sha256 见第 4 条。
