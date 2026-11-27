# §10.6　埋点

> 所属：前端　｜　覆盖章节：§10.6
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

### 10.6 埋点

| 事件 | 时机 |
|---|---|
| `plugin.market.open` | `/plugins` 挂载 |
| `plugin.index.copy` | 索引源地址复制成功 |
| `plugin.mine.open` | 「我的插件」点击 |
| `plugin.submit.open` | 「提交插件」点击 |
| `plugin.submit.next` | 第一步点「下一步：确认」打开确认弹窗（v2.19 ⑨；**此时未提交、不发请求**） |
| `plugin.submit.confirmed` | 确认弹窗点「确认发布」，真正发起 POST（v2.19 ⑨） |
| `plugin.submit.back` | 提交页页头返回按钮点击（v2.19 ⑩） |
| `plugin.submit.submitted` | 收到 202 |
| `plugin.prompt.copy` | 复制安装提示词（同时上报 `copy_prompt`） |
| `plugin.scan.report.open` | 治理侧审核页展开完整报告/证据（v2.19 ② 起详情页不再有证据区块，`plugin.scan.*` 只在治理侧触发） |
| `plugin.review.files.open` | 审核页展开「包内文件（只读）」 |
| `plugin.review.file.open` | 审核页点开单个文件（含 `binary` 标记，不含 `path`——路径可能含内网信息，不进埋点） |
| `plugin.namespace.tab.open` | 空间插件 tab 挂载 |
| `plugin.report.open` / `plugin.report.submitted` | 打开举报弹窗 / 提交成功 |
| `navigation.owned.plugins` | 侧栏入口点击（沿用既有命名） |

`plugin.prompt.copy` 同时触发 `POST .../stats`；两者独立，埋点失败不影响统计，统计失败不阻断复制。

**统计的后台可见面：v1 不做**（handover 缺口 #8，偏离记录见 §13 #14）。`view` / `copy_prompt` 两类事件照常落库并可聚合，但**没有面向插件作者的统计页**，也没有对外展示的浏览数；v1 对外只在详情页、市场卡片与「我的插件」列表三处展示同一个「复制提示词次数」（PRD §10 v2.20 S06）。取舍理由：作者后台需要时间趋势、按版本/来源拆分、导出与权限控制，是一整块产品面；先把数据采准，比先放一个口径可疑的数字更安全。做界面时不需要回补迁移，只需读 `plugin_stat_daily`。

---

