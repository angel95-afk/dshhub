# §6.3–§6.4　留存与压缩策略、暂存包的可浏览窗口与保留期

> 所属：后端　｜　覆盖章节：§6.3–§6.4
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

### 6.3 留存与压缩策略

| 数据 | 策略 |
|---|---|
| `plugin_scan_report.report` | 原始 JSON 序列化后 > 1 MiB 时只存稳定字段（`name/version/spec/capabilities/redLineCodes/errors`）+ `summary`，并置 `report_truncated = true`；展示与闸门只用稳定字段 |
| `plugin_scan_report` | 永久保留（含红线与失败行）：放行、审计与"当时为什么拦/放"都要靠它；`plugin_version.scan_report_id` 指向最新一条 |
| `plugin_scan_waiver` | 永久保留（含已撤销行），撤销不删行 |
| `plugin_submission` | **不删行**，只清大字段：终态 180 天后把 `source_payload` 置 `'{}'`、`error_detail` 置 NULL、`staged_tarball_key` 置 NULL，保留 `package_name/version/status/created_at/submitted_by` 作为追溯索引。原因：`plugin_scan_report` 以 `ON DELETE CASCADE` 挂在提交上，而版本又通过 `scan_report_id` 指向报告，删提交会连带删报告并被版本外键挡住（RESTRICT），清理任务必然失败 |
| `plugin_stat_event` | 保留 90 天（只用于去重与短期回算），长期看 `plugin_stat_daily` |
| `plugin_report` | 永久保留（含已处置行）。举报是合规留痕：需要证明"某次下架/驳回是因为有人报了什么、谁在什么时候怎么处理的"。不因插件软删而清空，`ON DELETE CASCADE` 只在物理删插件时生效（本设计不物理删） |
| `tarball` | 永久保留（追溯用）。key 内含 `sha512` 前缀，内容寻址，天然幂等。**审核拒绝后也不删本站留存的暂存包**（按 90 天清理策略处理）：本站留的是"当时审的是什么"的证据，删除会让事后追溯断链 |
| `dsh/index/v{gen}.json` | 保留最近 10 个 `gen`，更早的由清理任务删除；永不删除 `current_key` 指向的对象 |

### 6.4 暂存包的可浏览窗口与保留期（与预览接口严格对齐）

§7.2 的三个预览端点能返回什么，完全由"那一刻暂存包还在不在、指向哪里"决定。这里把它写成一张表，避免出现"接口能开、对象已没"或"对象还在、key 已被清空"的中间态：

| 阶段 / 状态 | 预览数据源 | 可浏览 | 清理时机 | 审核页怎么显示 |
|---|---|---|---|---|
| `RECEIVED` / `MATERIALIZING` | 无（还没物化） | **否** | — | 树不渲染，只显示「包还在准备中」 |
| `SCANNING` | `staged_tarball_key`（物化已完成） | **是** | — | 正常浏览；顶部提示仍常驻 |
| `REVIEW_PENDING` / `SCAN_RED`（待放行） | `staged_tarball_key` | **是（必须）** | **不得清理**（清理任务只处理终态行，§5.3） | 正常浏览 |
| 已通过、代发中 / 代发失败 | `staged_tarball_key` | **是** | 代发成功前不清理 | 正常浏览 + 代发状态行 |
| `LISTED` | 优先 `plugin_version.tarball_key`（永久留存、内容寻址），`sha512` 必须与库值相等；缺失时才回落 `staged_tarball_key` | **是（追溯用）** | 代发成功后 `staged_tarball_key` 可清；`plugin_version.tarball_key` 永久保留 | 正常浏览；此时内容已可由私服直接取，接口不因此扩大可见范围 |
| `REVIEW_REJECTED` / `PUBLISH_FAILED` / `VALIDATION_FAILED` 且未满保留期 | `staged_tarball_key` | **是** | 满 `SKILLHUB_PLUGIN_PUBLISH_RETENTION_DAYS`（默认 90 天）后删对象 | 正常浏览（"当时审的是什么"的证据） |
| 终态且已过保留期 | 对象已删 | **否** | 90 天删对象；180 天由 §6.3 的清字段任务把 `staged_tarball_key` 置 NULL | 树不渲染，显示「包内容已按保留策略清理（保留 {days} 天）」+ 仍展示 sha512/指纹/报告 |

三条口径（与既有规则不冲突，是把它们对齐后的结果）：

1. **90 天删对象、180 天清 key**：两个既有值不矛盾——对象先删（省存储），key 后清（留追溯索引）。预览接口在"key 非空但对象已不存在"时返回 `410 stagedPackagePurged`，与"key 已置空"同一响应、同一文案，前端不必区分两种原因。
2. **不给"续期"能力**：审核人不能因为想看而延长保留期。要重看已清理的包，唯一路径是作者重新提交（会重新物化、重新扫描）。这条是刻意的：允许续期等于允许把任意第三方包无限期寄存在本站。
3. **`LISTED` 之后不扩大可见范围**：此时包已在私服可匿名读，但预览接口仍只对"该空间空间管理员 + 作者本人"开放。理由不是保密，而是**不为一个已无必要的入口维护第三套权限**；真要公开看内容，应该走"源码仓库/私服"而不是本站的审核页。

---

