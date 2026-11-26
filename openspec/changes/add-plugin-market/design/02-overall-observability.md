# §12　指标、日志字段、告警阈值、故障处置手册

> 所属：总体设计　｜　覆盖章节：§12
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`README.md`](README.md)。

## 12. 可观测与运维

### 12.1 指标

| 指标 | 类型 | 标签 | 用途 |
|---|---|---|---|
| `plugin.submission.created` | counter | `source` | 提交量 |
| `plugin.submission.stage.duration` | timer | `stage` | 各阶段耗时（RECEIVED→…→LISTED 逐段拆分） |
| `plugin.submission.terminal` | counter | `status`, `error_code` | 终态分布 |
| `plugin.submission.retry` | counter | `stage` | 重试次数 |
| `plugin.scan.verdict` | counter | `verdict` | 裁决分布（clear/review/red/error） |
| `plugin.scan.duration` | timer | `source` | 扫描耗时 |
| `plugin.scan.waiver` | counter | — | 放行次数 |
| `plugin.publish.result` | counter | `result` | 代发成功/失败 |
| `plugin.review.pending.age` | gauge | — | 待审的最老时长（审核 SLA 的直接指标） |
| `plugin.review.approved.age` | gauge | — | 已通过但未上架的最老时长（= 代发卡住的最长等待，链路反转后新增的关键指标） |
| `plugin.review.denied` | counter | `reason` (`notSpaceAdmin`/`notPending`) | 审批被拒分布：`notSpaceAdmin` 突增说明前端按钮态与服务端判定不一致（`canReview` 下发有 bug） |
| `plugin.review.self_review` | counter | `action` (`approve`/`reject`) | 自审次数（`actor == submitted_by`）。**不设告警**——自审是允许的；只在复盘与治理报表里看趋势：某空间自审占比长期接近 100%，说明它缺第二个管理员（该补人不该补规则） |
| `plugin.publish.failure` | counter | `stage` (initial/retry/manual) | 代发失败分布：首次 / 自动重试 / 人工重试 |
| `plugin.stagedfile.list` | counter | `result` (ok/denied/purged/limit/mismatch) | 审核页清单读取结果分布（`denied` 突增=有人在探他人提交） |
| `plugin.stagedfile.entry` | counter | `kind` (text/binary) | 单文件读取分布；`binary` 占比高说明审核人在逐个看产物 |
| `plugin.stagedfile.download` | counter | `result` | 下载次数（应为个位数/天，突增即异常） |
| `plugin.stagedfile.limit.hit` | counter | `limit` (entries/uncompressed/entry/text/ratio/timeout) | 上限命中分布：既是调参依据，也是"有人在试解压炸弹"的信号 |
| `plugin.stagedfile.indexcache` | counter | `result` (hit/miss) | tgz 无随机访问，遍历是真开销；命中率低就要看 TTL/容量配置 |
| `plugin.stagedfile.duration` | timer | `op` (index/entry) | 清单遍历耗时 / 单文件读取耗时 |
| `plugin.index.build` | counter | `result` | 重建成功/失败 |
| `plugin.index.build.duration` | timer | — | 重建耗时 |
| `plugin.index.plugin.count` | gauge | — | 索引内插件数 |
| `plugin.index.dirty.age` | gauge | — | 脏标记已持续多久（延迟窗口） |
| `dsh.index.request` | counter | `status` (200/304/503) | 命中与 304 率 |
| `dsh.install.request` | counter | `template`(index/plugin) | 文档渲染量 |
| `plugin.registry.probe` | gauge | `anonymousRead`, `writable` | 自检结果 |

**SLO 目标值**（handover 缺口 #6；这些是告警与验收的依据，落地时按此建看板与阈值）：

| 对象 | 指标 | 目标 | 测量口径 | 超了怎么办 |
|---|---|---|---|---|
| 市场列表 / 搜索 | `GET /api/v1/plugins` P95 | **< 300ms** @ 5,000 插件、20 万次/天 | 服务端耗时（不含网络），含 `page` 翻页与分类/关键词筛选 | 先查是否命中 `search_vector` GIN 索引与 `idx_plugin_listed`；未开 `pg_trgm`/计划退化则回退到只按 `category + updated_at` 游标分页 |
| 插件详情 | `GET /api/v1/plugins/{ns}/{slug}` P95 | < 150ms | 单行 + 版本列表 | 同上 |
| 索引生成 | `plugin.index.build.duration` P95 | **< 1s** @ 500 插件（P99 < 3s） | 一次"读库 + 序列化 + 17 条校验"，不含 PUT 网络耗时、也不含分发来源探测耗时（后者单列 `plugin.index.source_probe.duration`） | 查单表扫描是否走顺序扫描、`metadata_snapshot` 是否过大；超过 500 插件后按 §15 二期评估分片 |
| 索引端到端生效 | `plugin.index.dirty.age` P95 | **≤ 2 分钟**（预算拆解见 §8.3） | 从 `markDirty` 事务提交到 `current.json` 指向新 `gen` | 查 worker 是否存活、私服/对象存储是否慢、生成是否连续失败 |
| 提交同步响应 | `POST /api/v1/plugins/submissions` P95 | < 400ms | 同步校验链路（不含异步） | 查包名/版本查重 SQL 是否走唯一索引、zip 场景是否误读了流 |
| `/dsh/index.json` | `dsh.index.request` P95 延迟 + 304 率 | P95 < 100ms；304 率 > 80% | 含 304 与 200 | 304 率低说明 `ETag`/`Cache-Control` 失效或代理未透传；延迟高查是否在读放大（每请求重算 sha256） |
| 扫描 | `plugin.scan.duration` P95 | < 60s（预算 120s） | scanner 侧 | 查 Node 冷启动与 tarball 大小分布 |
| 审核页包内文件清单 | `plugin.stagedfile.duration{op=index}` P95 | 缓存命中 **< 100ms**；首次 **< 2s** @ 50 MiB 包 | 服务端耗时（不含网络） | 首次慢先看解压是否在请求线程里做了多余拷贝；命中率低查 `..._INDEX_CACHE_TTL/MAX` 与多副本分布 |
| 审核页单文件读取 | `plugin.stagedfile.duration{op=entry}` P95 | **< 300ms**（目标条目靠后的文件最坏要走过整条流） | 服务端耗时 | 查是否每次重开对象、是否把整包读进内存（应流式） |

列表 P95 之所以能压在 300ms：列表只读 `plugin` 单表 + `latest_version_id`（不 join 版本表、不聚合统计），统计数字由 `plugin_stat_daily` 的冗余计数字段直接读，不做实时 `count(*)`。若后续要按统计排序，另建物化视图，不在线上列表里 join。

### 12.2 日志字段（禁凭据）

固定字段：`traceId`、`requestId`、`submissionId`、`pluginId`、`pluginVersionId`、`scanReportId`、`gen`、`stage`、`verdict`、`attempt`、`durationMs`、`errorCode`。

禁止：`SKILLHUB_NPM_REGISTRY_TOKEN`、Git token、URL userinfo、tarball 内容、报告中未脱敏片段、Cookie。日志脱敏复用既有 `logback` 配置 + scanner 的 `_redact_supported_tokens` 语义；后端对 `error_detail` 再做一次 `userinfo` 剥离。

### 12.3 告警阈值

| 告警 | 条件 | 级别 |
|---|---|---|
| 索引重建连续失败 | `plugin.index.build{result=failure}` ≥ 3 次/30min | P1 |
| 索引脏龄超出 SLO | `plugin.index.dirty.age` > 5min | P2 |
| 索引脏龄严重超期 | `plugin.index.dirty.age` > 15min | P1 |
| 索引生成过慢 | `plugin.index.build.duration` P95 > 1s（连续 3 个窗口） | P2 |
| 列表接口超 SLO | `GET /api/v1/plugins` P95 > 300ms 持续 5min | P2 |
| 列表接口严重超时 | 同上 P95 > 1s 持续 5min | P1 |
| 索引 304 率异常低 | `dsh.index.request{status="200"}` 占比 > 50% 持续 10min | P2（多为 ETag 失效或代理剥离缓存头） |
| 扫描 red 突增 | `plugin.scan.verdict{verdict=red}` 1h 环比 > 3× | P2 |
| `clear→red` 漂移 | 单次重扫任务内 ≥ 1 例 | P1（附版本清单） |
| 队列积压 | 最老 `RECEIVED` 年龄 > 15min（`max(created_at)` 差值） | P2 |
| 代发失败率 | `plugin.publish.result{result=failure}` > 20%（5min 窗口，≥5 次） | P1 |
| 审核通过后代发失败 | `plugin.publish.failure{stage="retry"}` ≥ 1 且该版本 `publish_attempt >= maxAttempts` | P1（附 `name@version`，需人工介入：凭据/保留策略/网络） |
| 同一包连续代发失败 | 同一 `name` 24h 内失败 ≥ `SKILLHUB_PLUGIN_PUBLISH_ALERT_THRESHOLD` | P1（多为仓库级问题，不是单版本问题） |
| 已通过但长期未上架 | `plugin.review.approved.age` > 30min | P2（正常应在分钟级完成，超过说明队列或私服有问题） |
| 审核期过长 | `plugin.review.pending.age` > 72h | P2（审核 SLA；此窗口内制品**不可安装**，只影响作者等待时间） |
| 私服匿名读失效 | `plugin.registry.probe{anonymousRead="false"}` | P1（会禁用上传来源） |
| 暂存包指纹不一致（预览面） | `plugin.stagedfile.list{result="mismatch"}` + `stagedfile.entry{mismatch}` ≥ 1 | P1（附 submissionId 与期望/实际 sha512 前 16 位；语义同 §14.1 A13） |
| 预览上限命中突增 | `plugin.stagedfile.limit.hit` > 20 / 10min | P2（多为审核人撞上正常大包；连续命中 `ratio`/`uncompressed` 则按解压炸弹处置） |
| 预览越权探测 | `plugin.stagedfile.list{result="denied"}` > 10 / 10min（同用户） | P2（有人在枚举他人 submission id） |
| `/dsh/index.json` 5xx | > 0.1% / 5min | P1 |
| 扫描 `error` 率 | `plugin.scan.verdict{verdict=error}` > 10% / 30min | P2 |

### 12.4 常见故障处置手册

**A. 索引生成失败（指针未切换）**

1. 看 `plugin_index_state.last_error` 与日志 `plugin.index.build{result=failure}` 附近的 `validate` 失败项。
2. 常见根因：某版本 `publish_timestamp` 为 NULL、`homepage` 因 `SKILLHUB_PUBLIC_BASE_URL` 为空而无法生成、同一 `pluginKey` 冲突、`name` 重复（历史脏数据）。
3. 修复数据后 `UPDATE plugin_index_state SET dirty = TRUE, lease_until = NULL, last_error = NULL WHERE id = 1;` 触发重建。
4. 期间 DSH 仍拿到上一版索引（可用），无需紧急操作。

**B. 私服不可用**

1. `/api/v1/plugins/market` 的 `uploadSourcesEnabled` 变 false，提交页提示"暂不可用"。
2. 已在途 submission 会按 backoff 重试，`maxAttempts` 耗尽后 `PUBLISH_FAILED`（可让作者重提）。
3. 恢复后**不要**手工改状态，让重试自然消化；若已终态，作者重提即可（`name@version` 未被占用）。

**C. 扫描器升级导致结论漂移**

1. 按扫描工具的版本策略（本设计 §9.2）只在 CHANGELOG「Affects catalog results」不为 None 时升级。
2. 升级后跑 `PluginScanBackfillTask`（手动触发：`POST /api/v1/admin/plugins/scan-backfill`，仅 `SUPER_ADMIN`）。
3. 比对 `report.fingerprint` 与 `verdict` 差异清单，`clear→red` 的版本自动 `HIDDEN` 并告警。
4. 差异清单进发布说明；不是"自动接受新结论"，仍可人工放行。

**D. 队列积压**

1. 看 `plugin.submission.terminal{status=RECEIVED}` 年龄与 scanner 的 503 率。
2. 若是扫描慢：检查 scanner 并发是否被技能扫描占满 → 调 `SKILLHUB_SCANNER_MAX_CONCURRENT_SCANS` 或临时只处理插件。
3. 若是代发慢：查私服响应时间；必要时临时提高 `SKILLHUB_PLUGIN_SUBMISSION_BATCH_SIZE`（但 lease 要同步上调）。
4. 不建议直接 `UPDATE` 状态推进——会绕过闸门。

**E. 审核通过后代发卡住（版本停在 `APPROVED`）**

1. 查 `plugin.review.approved.age` 与后台「已通过待发布」清单（`status='APPROVED' AND publish_attempt > 0`，按 `updated_at` 正序）。
2. 看 `publish_error` 分类：401/403 → 凭据或权限（换 `SKILLHUB_NPM_REGISTRY_TOKEN`）；409 → 私服已有同名同版本（多半是上次实际成功但回滚了状态，先 `npm view` 确认，再决定重试或改版本号）；5xx/超时 → 私服侧，查其健康与保留策略。
3. 修完调用 `POST /api/v1/plugins/{ns}/{slug}/versions/{version}/publish {"action":"RETRY"}`（作者在同一页面点「重试发布」等价），**不要**直接 `UPDATE` 状态（会绕过审计与 `next_attempt_at` 计算）。
4. 重试**不重走扫描与审核**：审核结论已固化在 `APPROVED` 上，重试只把代发重新排队。若包本身有问题，应当作新版本重新提交（走完整链路）。
5. 私服彻底不可用时，把它当"上不了架"而不是"审核失败"对外表达：作者看到「已通过审核，等待发布」，不是「未通过」。

**F. 残留清理（回滚后）**

```sql
-- 仅代码回滚后清理用；执行前确认无新的 plugin 写入
TRUNCATE plugin_report, plugin_scan_waiver, plugin_stat_event, plugin_stat_daily,
         plugin_scan_report, plugin_submission, plugin_version, plugin,
         plugin_index_state RESTART IDENTITY CASCADE;
```
对象存储侧删除前缀 `dsh/` 与 `plugin/`。注意：私服上已 `LISTED` 的包**不会**被这条清理删掉，需按 `plugin_version` 的清单单独处理（代码回滚后这些包就只是"多出来的包"，不影响既有功能）。

---

