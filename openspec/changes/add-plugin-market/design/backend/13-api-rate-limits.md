# §7.5　限流点

> 所属：后端　｜　覆盖章节：§7.5
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

### 7.5 限流点

| 接口 | category | 认证用户 | 匿名 | 窗口 |
|---|---|---|---|---|
| `POST /plugins/submissions` | `plugin-submit` | 10 | — | 3600s / 用户 |
| `POST /plugins/{ns}/{slug}/stats` | `plugin-stats` | 120 | 40 | 60s / IP |
| `POST .../rescan` | `plugin-rescan` | 5 | — | 3600s / 版本（业务层再断言一次） |
| `POST /plugins/{ns}/{slug}/report` | `plugin-report` | 5 | — | 3600s / 用户 |
| `POST .../versions/{v}/yank`、`.../delete`、`.../release-name`、`.../publish` | `plugin-lifecycle` | 20 | — | 3600s / 用户（`publish` 重试同一版本 60s 内只允许 3 次，避免把私服打穿） |
| `PATCH /plugins/{ns}/{slug}/visibility` | `plugin-lifecycle` | 20 | — | 3600s / 用户（**并入 `plugin-lifecycle`，不新增 category**：缩窄可见范围是低频治理动作，与挂 / 摘标签、下架、撤版同族；多开一类只会多一处要调的阈值，排障时也会把同类治理动作打散到两个计数器里） |
| `POST /plugins/git/preflight` | `plugin-git-preflight` | 10 | — | 60s / 用户（预检会打到内网 Git，必须限流） |
| `GET /plugins/submissions/{id}/files` | `plugin-submission-files` | 60 | — | 60s / 用户（一次审核通常只拉 1–2 次；上限主要防脚本枚举包结构与探测提交 id） |
| `GET /plugins/submissions/{id}/files/content` | `plugin-submission-file-content` | 120 | — | 60s / 用户（翻一个目录树会连点十几下，量级必须比清单高，否则正常审核会被 429 打断） |
| `GET /plugins/submissions/{id}/files/download` | `plugin-submission-file-download` | 10 | — | 60s / 用户（与 `plugin-git-preflight` 同量级：一次请求 = 一次完整对象读 + 一条审计） |
| `GET /api/v1/plugins*` | `plugin-catalog` | 120 | 60 | 60s |
| `/dsh/*` | 不限流 | — | — | 由缓存与 304 兜住；nginx `client_max_body_size` 已限 |

`/dsh/*` 刻意不限流：DSH 拉索引的频率不受本站控制，限流只会把失败模式从"稍慢"变成"索引不可用"。

三个预览端点的 category **独立于 `plugin-catalog`**：更具体的路径规则优先于 `GET /api/v1/plugins*` 的桶，否则"审核被限流"和"目录被限流"会混在同一个计数器里，排障时分不清。

