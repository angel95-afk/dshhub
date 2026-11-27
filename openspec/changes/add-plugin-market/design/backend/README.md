# 后端模块索引 — DSH 插件市场 v1.0

本目录是 `../` 总体设计的后端分册，共 16 个文件，覆盖 `§3`–`§9`。**只移动文字，不改内容与章节编号**；章节号全局唯一，跨文件引用写 `§N.M`。

后端按功能域分为：分层与边界（01–02）、关键链路（03–05）、领域状态与数据（06–08）、接口契约（09–14）、索引与扫描（15–16）。

| 文件 | § | 行数 | 一句话 |
|---|---|---|---|
| [`01-layering-packages.md`](01-layering-packages.md) | §3.1–§3.2 | 362 | 新增包清单、类图 |
| [`02-controllers-adapters-reuse.md`](02-controllers-adapters-reuse.md) | §3.3–§3.5 | 134 | Controller 清单、适配器接口签名（先冻结）、复用与独立的边界 |
| [`03-flow-submit.md`](03-flow-submit.md) | §4.1–§4.2 | 227 | 提交同步校验路径、submission worker 全流程、**两步确认时序（v2.19 ⑨）** |
| [`04-flow-index-and-install-doc.md`](04-flow-index-and-install-doc.md) | §4.3–§4.5 | 161 | 索引重建与指针切换、索引读取与 304、安装文档渲染 |
| [`05-flow-review-and-file-browse.md`](05-flow-review-and-file-browse.md) | §4.6–§4.7 | 204 | 审核链路（通过才代发）、审核人浏览暂存包内文件 |
| [`06-domain-state-machines.md`](06-domain-state-machines.md) | §5 | 223 | 版本状态机、提交状态机、不可达边与不变量 |
| [`07-data-model-ddl.md`](07-data-model-ddl.md) | §6.1–§6.2 | 421 | Flyway 文件命名与顺序、可直接执行的完整 DDL |
| [`08-data-retention-and-staging-window.md`](08-data-retention-and-staging-window.md) | §6.3–§6.4 | 40 | 留存与压缩策略、暂存包的可浏览窗口与保留期 |
| [`09-api-public-anonymous.md`](09-api-public-anonymous.md) | §7.1 | 158 | 匿名公开接口（索引、安装文档、市场页） |
| [`10-api-portal-openapi.md`](10-api-portal-openapi.md) | §7.2 | 733 | 需登录接口的 OpenAPI paths（3 块 YAML，不要继续拆） |
| [`11-api-schemas.md`](11-api-schemas.md) | §7.3 | 263 | 关键 schema |
| [`12-api-error-codes.md`](12-api-error-codes.md) | §7.4 + §7.4.1 | 163 | 错误码表、结构化错误码全集（同步校验 51 条 + 异步扫描 3 条） |
| [`13-api-rate-limits.md`](13-api-rate-limits.md) | §7.5 | 26 | 限流点 |
| [`14-api-staged-file-security.md`](14-api-staged-file-security.md) | §7.6 | 91 | 暂存包文件浏览的安全约束（逐条写死，实现不得「灵活处理」） |
| [`15-index-generation.md`](15-index-generation.md) | §8 | 180 | 字段映射、分发来源判定、pluginKey、构建期强校验 |
| [`16-scan-integration.md`](16-scan-integration.md) | §9 | 150 | scanner 契约、镜像、裁决规则、脱敏 |

返回总索引：[`../README.md`](../README.md)；断言（S1–S26）见 [`../04-overall-tasks-and-assertions.md`](../04-overall-tasks-and-assertions.md)。
