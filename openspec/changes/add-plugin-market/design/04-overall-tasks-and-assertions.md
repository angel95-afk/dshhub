# §16 + 附　任务拆分建议、改造前回归保护、S1–S26 安全断言、接触点清单

> 所属：总体设计　｜　覆盖章节：§16 + 附
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`README.md`](README.md)。

## 16. 后续任务拆分建议（只给切分维度与顺序）

不写正式 `tasks.md`，只给拆分依据：

1. **按"数据 → 骨架 → 读写路径 → 异步 → 治理 → 前端 → 部署"的顺序切**，因为迁移是所有后续工作的地基，而 `/dsh/index.json` 在 `plugin` 表为空时也能产出合法文件（可以先验证读路径再接写入路径）。
2. **每个任务必须自带可执行验收**：
   - 迁移任务：空库 `V1→V10001` 全量执行 + 守卫测试通过。
   - 索引任务：用夹具生成一份最小索引 + `make plugin-index-verify` 通过。
   - 扫描任务：四裁决分支的单测 + scanner 契约测试（503/504/400 各一条）。
   - 状态机任务：每个终态至少一条测试，含"重试耗尽"与"唯一索引冲突收敛为已上架"。
3. **必须与其他任务同批的原子单元**（拆开会导致中间态不可用或不可验证）：
   - `plugin_submission` 表 + `PluginSubmissionWorker` + scanner 新接口：拆开会出现"受理了但没人处理"的黑洞。
   - 索引生成 + `PluginIndexRebuildWorker` + `/dsh/index.json` 读路径 + nginx：拆开会出现"生成了但拿不到"或"拿到了 HTML"。
   - `ReviewSubjectType.PLUGIN_VERSION` + `PluginReviewDecisionService` + `ReviewService` 的显式拒绝：拆开会让插件版本进入技能发布分支。
   - 审核通过 → `APPROVED` → worker 代发 → `LISTED` 这一整段：拆开会出现"审核通过了但没人发"的黑洞，作者永远停在「已通过审核，等待发布」而系统里没有一处会推进它。同批必须包含 `next_attempt_at` 的出队逻辑与「重试发布」端点。
   - **审核页包内文件浏览：三个端点 + `PluginFileBrowser` + §16 的 S1–S13 断言**：拆开会出现"能看内容但没有 `text/plain`/`nosniff`/CSP 与前端禁用 `innerHTML` 的约束"——那是一个已经上线、但把未审核 HTML/SVG 直接渲染在自己域名下的预览面。**响应头与前端渲染约束必须和端点同一个 PR**，不允许"先上功能再补安全"。
   - **两步提交（`ConfirmSubmitDialog` + §4.1 时序 + §5.3 入口 + §7.2 POST 时机）+ S25 断言**：拆开会出现"弹窗打开了但提交已发出"或"返回编辑后留下一条受理记录"——未确认即受理是产品契约破坏，必须同批交付；且**不得**以"先做单步提交、后补确认弹窗"的增量上线，那会让中间态出现无确认的提交入口。
   - **用户侧披露全撤（删 `PluginSecurityDisclosure` + 治理侧完整报告 + 包内文件浏览 + 提交者视图；安装文档无能力摘要、用户侧无免责常驻）+ S26 断言**（v2.20 ② 收紧，v2.19 的"详情页下沉 + 安装文档一行摘要 + 免责常驻"口径作废）：这是"证据展示在哪一侧"的纯展示侧调整，**必须与判定规则/四裁决/扫描契约保持同一版本**，不得出现"用户侧已删但治理侧报告还没接好"的断档——那会让需确认/有红线的插件在两侧都看不到证据。
4. **可独立并行的两条线**：前端页面（依赖生成的 OpenAPI 类型，可与后端并行，先对 mock）与 scanner 侧（独立容器，契约冻结后即可并行）。
5. **跨任务共享的产物要先冻结**：`pluginKey` 规则、索引键名、指针对象结构、`errorCode` 取值集合、i18n key 命名。这五项一旦变动会波及所有任务，应在第一个任务里定稿。

### 改造前行为回归保护（必须随第一个任务落地）

本变更会动到既有页面与网关，下列行为**改造前是什么样，改造后必须一样**；每条都要有可执行的回归手段，不允许"人工看一遍"：

| # | 保护对象 | 回归手段 |
|---|---|---|
| 1 | `/dashboard/publish` 的 6 个查询参数（`namespace/visibility/updateName/updateSkill/resubmitSkill/resubmitVersion`）与发布技能链路 | 6 条深链的路由级测试：断言 `validateSearch` 解析结果、页面预填值与"更新/重新提交"分支不变；并断言 `/dashboard/plugins/submit` 与它互不影响 |
| 2 | nginx 的 SPA fallback 与既有 `/api/` 代理 | 请求 `/plugins`、`/dashboard/plugins` 仍返回 `index.html`；`/api/v1/**` 行为不变；`/dsh/**` 命中新 `location ^~`（三条断言同一条 nginx 配置测试） |
| 3 | 既有 `/api/v1/skills/**`、`/api/v1/reviews/**` 契约 | `ReviewService` 入口新增的 `PLUGIN_VERSION` 拒绝不得影响 `SKILL_VERSION`/`SKILL_METADATA`/`SUITE_VERSION` 三类既有路径（每个类型各一条 approve/reject 回归用例）。**另加一条**：平台管理员对 `SKILL_METADATA` 的 approve 必须**仍然成功**（§16 S16）——本轮按 `subjectType` 收紧的只是插件侧，技能侧允许平台管理员审核的既有行为不变 |
| 4 | `review_task` 既有查询与索引 | 新增的部分唯一索引只作用于 `subject_type='PLUGIN_VERSION'`，既有类型的审核列表分页结果不变 |
| 5 | 迁移守卫 | 空库 `V1 → V10001` 全量执行成功；`FlywayMigrationGuardrailTest` 现有断言全绿（低段仍连续、高位段无重复） |
| 6 | 首页/导航 | 新增"插件"入口不改变既有"技能"入口的高亮与排序；未登录用户看不到插件提交入口 |
| 7 | Git 来源默认传输与安全断言 | `SKILLHUB_PLUGIN_GIT_TRANSPORT` **缺省即 `ssh`**，装配 `SshKeyGitResolver`；配成 `https` 时**启动失败并提示"本期未实现"**（不是静默回退）；缺 `known_hosts`/`HOST_KEY_PINS` 之一、缺私钥文件、或两者 host pin 都配了但冲突时**启动失败**——不提供等价于 `StrictHostKeyChecking=no` 的跳过开关 |
| 8 | Git 提交表单无子目录入口 + API 传 `path` 必须 400 | 前端断言：Git 提交表单**只有仓库地址一个输入框**，不存在子目录/ref/tag/commit/凭据输入；后端断言：直连 `POST /api/v1/plugins/submissions` 在 git 来源的 `sourcePayload` 夹带 `path`（或 ref/tag/commit/凭据）字段 → **400 `gitFieldNotSupported`** + 固定文案「本期不支持子目录，请将插件包置于仓库根目录」（防止前端绕过） |
| 9 | npm 提交表单无版本输入框 + API 传 `version` 必须 400 + 冻结版本全流程不重解析 + 上游发新版不改动已上架版本 | ①前端断言：npm 提交表单**只有包名一个输入框**（可 scoped），DOM 中不存在任何版本输入元素、不存在 i18n 键 `plugin.submit.source.npm.version`/`.versionPlaceholder`；②后端断言：直连 `POST /api/v1/plugins/submissions` 在 npm 来源夹带 `version`（或 ref/tag/凭据）→ **400 `versionFieldNotSupported`**，且 `resolveMetadata` 的用户路径调用参数恒为 null（用 spy/mock 断言传给 `resolveMetadata` 的第二参为 null，或只调用 `resolveLatest`）；③冻结复用断言：mock registry 在提交后把 latest 从 1.0.0 推到 1.0.1，跑完整条 materialize→scan→审核→publish→索引链路，断言全程只用 1.0.0、`source_payload.resolvedVersion=1.0.0`、`resolvedAt` 为提交时刻，scanner 与 publish 收到的 version 都是 1.0.0，§8.4 第 15 条 sha512 断言通过；④不可变断言：上游发版后已上架版本（LISTED 的 `name@1.0.0`）记录、索引条目与 tarball 均不变；同包重复提交因 latest 已前移解析出 1.0.1 才是新版本，若 latest 未变则撞 409 `versionExists` 且文案为 npm 专属 |

### 审核页「包内文件」的安全回归断言（随 P1 审核任务同批落地，一条都不能省）

这一组不是"新功能验收"，是**防事故的最后一道闸**：每条都以"故意做错就红"的形式写，能自动化的一律进 CI。

| # | 断言 | 手段 |
|---|---|---|
| S1 | **文本响应的响应头三件套**：`Content-Type: text/plain; charset=utf-8`、`X-Content-Type-Options: nosniff`、`Content-Security-Policy: default-src 'none'; sandbox`（外加 `Cache-Control: no-store`） | 三个端点各一条 MockMvc 断言（含清单端点也要 nosniff/no-store）；缺任一头即失败 |
| S2 | **绝不返回可渲染类型**：请求包内 `evil.html` / `evil.svg` / `evil.js` 时，响应 `Content-Type` 必须是 `text/plain; charset=utf-8`，body 是原始文本（不是被渲染/被转义的 HTML 文档） | 用含 `<script>alert(1)</script>` 的夹具文件，断言 `Content-Type` 与 body 逐字节相等 |
| S3 | **下载端点不猜类型**：`Content-Type: application/octet-stream` + `Content-Disposition: attachment` + `nosniff` | `.svg` 与 `.html` 夹具各一条 |
| S4 | **前端禁用危险渲染 + 必须复用既有实现**：`features/plugin/review-files.tsx` 及其子组件中不出现 `dangerouslySetInnerHTML`、`innerHTML`、`<iframe`、`<object`、`<embed`；`<img>` 不得指向包内文件；**且必须从 `features/skill/` 引用 `file-tree*` / `code-renderer` / `file-type-utils`，不得在插件侧重写"可预览判定 / 二进制魔数 / 高亮"** | ESLint（`react/no-danger` + 自定义 `no-iframe-to-staged-file`）+ 单测 DOM 断言（`container.querySelector('script') === null`）+ **依赖断言**：插件目录下不存在 `isPreviewable` / `getLanguageForHighlight` / 高亮库的直接实现（只允许 re-export 或 import） |
| S5 | **路径穿越 400**：`path=../../etc/passwd`、`path=/etc/passwd`、`path=C:\Windows\win.ini`、`path=a\b`、`path=a%00b`、`path=%2e%2e%2fpackage.json`、`path=%252e%252e%252fpackage.json`、`path=Package.json`（大小写不同）→ 全部 **400 `stagedFilePathInvalid`** 或 **404 `stagedFileNotFound`**，**绝不 200** | 参数化测试 8 条（§7.6.1 的三种绕过变体必须都有用例） |
| S6 | **白名单精确命中**：`path` 必须是清单条目之一；`path=package.json/../package.json`（规范化后等于合法条目但字符串不等）也拒绝 | 单测 + 一条"用符号链接构造的恶意包"物化测试（应整包 422 `archiveUnsafe`，不入清单） |
| S7 | **超限拒绝而非截断**：文件数 2001 / 整包解压 201 MiB / 单文件 9 MiB / 文本 513 KiB / 压缩比 11:1 的夹具 → 分别 **422 `stagedArchiveLimitExceeded`** 与 **413 `stagedFileTooLarge`**；响应体里**不得**出现部分清单或部分内容 | 五条夹具各一条测试；断言 body 不是 `entries` 数组也不是文本片段 |
| S8 | **非审核人 403 / 提交不存在 404 / 已清理 410**：普通空间成员 403、非成员 403、未登录 401、`submissionId` 不存在 404、`staged_tarball_key` 置空或对象已删 410 | 权限矩阵参数化测试（5 种身份 × 3 个端点） |
| S9 | **一致性 409**：把暂存对象替换成另一份字节（sha512 不同）后请求内容 → **409 `stagedPackageMismatch`**，且**不返回字节**、触发告警计数 | 集成测试：mock 对象存储返回被篡改的字节，断言状态码 + 响应体为空 + 指标 `result=mismatch` |
| S10 | **审计写入**：三个动作各产生一条 `PLUGIN_SUBMISSION_FILES_VIEWED` / `PLUGIN_SUBMISSION_FILE_VIEWED` / `PLUGIN_SUBMISSION_FILE_DOWNLOADED`，含 `submissionId`、（单文件/下载时）`path`、`result`；且**审计 fail 时返回 503 而不是内容** | 审计服务 mock 抛异常 → 断言 503 且响应体不含文件内容 |
| S11 | **不回退数据源**：暂存包缺失时，断言**没有**任何对 `NpmRegistryPort` / 私服的调用（spy 调用次数为 0），返回 410 | 单测：spy `NpmRegistryPort`，触发 410 路径 |
| S12 | **缓存键正确**：同一提交连续两次清单请求只遍历 tar 一次（`PluginArchiveReaderPort.readIndex` 调用次数 == 1）；sha512 变化后必须重新遍历（缓存不复用旧清单） | 单测 + 计数器断言（含 single-flight 并发合并：10 并发 → 1 次遍历） |
| S13 | **i18n 齐平且无废弃键**：`plugin.review.files.*`、`plugin.review.authorSelfNotice`、`plugin.review.canReview.reason.*`（仅 `notSpaceAdmin` / `notPending`）、`plugin.error.staged*`、`plugin.error.fileBrowseDisabled` 四语言齐全；**不得出现"自审被拒"类错误码或 `canReview.reason.selfReview` 键**（该规则已按用户裁决作废，残留即为缺陷） | 既有 i18n 键一致性测试覆盖（§10.5 清单自动比对），并对四语言做"无多余键"双向断言 |
| S14 | **平台管理员审批 `PLUGIN_VERSION` → 403 `noPermission`**：approve 与 reject 各一条；同一 `taskId` 换空间管理员调用 → 200（证明不是"谁都过不去"） | 权限矩阵参数化测试；断言 403 body 的 `error` 为 `plugin.error.noPermission`，且**版本状态未变**（`REVIEW_PENDING` 仍是 `REVIEW_PENDING`） |
| S15 | **自审允许且留痕**：构造"提交者本人 == 该空间空间管理员"，approve 自己的提交 → **200**（不是 403），断言审计 `PLUGIN_REVIEW_APPROVED` 的 detailJson 里 `selfReview=true`；再换另一个空间管理员 approve 同一类提交 → 200 且 `selfReview=false`；reject 方向同样各一条（200 + `selfReview=true`） | 集成测试；同时断言响应体不含任何"禁止"类错误码，且 `canReview=true`、`isAuthor=true` 由服务端下发 |
| S16 | **技能侧回归：平台管理员 approve `SKILL_METADATA` → 仍按既有行为成功**（与 S14 成对，证明权限分流没误伤技能审核） | 一条既有技能审批用例保持全绿；断言 `ReviewService` 对 `SKILL_METADATA` 的断言路径未进入插件分支（用 spy 或分支覆盖），且 200 响应结构与改造前一致 |

### 其余硬规则的回归断言（S17–S22，与对应功能同批落地）

这一组与 S1–S16 性质相同：都是"故意做错就红"的门禁，不是新功能验收。S17–S22 分别锚定 §7.2.1 提示词唯一性、§8.1.1 来源唯一判定点、§7.2.1 无版本选择器、§7.2 六条硬规则之第 6 条、§7.3 文件清单口径、§7.2.2 + §7.4 错误码纪律；能自动化的一律进 CI。

| # | 断言 | 手段 |
|---|---|---|
| S17 | **安装提示词全站唯一、不按版本分叉**：`GET /api/v1/plugins/{namespace}/{slug}` 的 DTO 只下发**一个** `installPrompt`，句中两处版本（安装文档 URL 的 `{version}` 段与包名后的 `@version`）必须由服务端**同一个变量拼两次**，逐字符相等且等于构造该响应那一刻的最新 `LISTED` 版本；`versions[]` 只作只读历史，**不得**含逐版本提示词、逐版本复制/安装入口、安装文档路径字段。正例：`latestVersion=1.2.0` 时句中两处都是 `1.2.0`；撤版 1.3.0 后立即复制得到 1.2.0（`installPrompt` 不进缓存，`dataUpdatedAt` 超 60s 先 `refetch`）。反例：句中两处版本不等、`versions[]` 出现 `installPrompt`/安装路径字段、前端二次模板化或自行拼 URL、或缓存了已撤版版本号——任一出现即失败（§7.2.1） | ① MockMvc + 正则从 `installPrompt` 抽出两处版本，断言相等且等于 DB 最新 `LISTED` 版本（npm / git 两来源 × 单版本 / 三版本，共 4 条参数化）；② schema 严格断言：`versions[]` 每项的键集合恰为响应 schema 声明的只读字段，多一个即失败；③ 断言该响应 `Cache-Control: no-store`（页面其他字段仍按 §10.3 缓存）；④ 前端单测：spy `navigator.clipboard.writeText`，断言入参 === 响应 `installPrompt` 原文（不是前端拼出的串）；⑤ 撤版后 `refetch` 用例：断言复制到的是撤版之后的最新值 |
| S18 | **索引与详情页共用同一个来源判定**：索引 `versions[].source` 与详情页「分发来源」必须都来自 `PluginDistributionSourceResolver.resolve(version, ctx)` 这**唯一判定点**，改一处必须两处同时变；**不得**存在第二处判定（不得有第二个 `instanceof NpmRegistryPort`、第二次 registry 探测、或从 `submission_source` 回填）。正例：zip 提交的已上架版本、镜像里有制品 → 索引写 `npm` 且详情页显示 npm registry；把该版本从镜像移除 → 索引改写 `git`、详情页显示仓库地址 + 提交号。反例：镜像返回 502 或超时 → **索引必须不变（保留上一版）、告警触发**，详情页沿用上一次成功判定的结果并标注「来源判定暂不可用」；若索引降级成 `git`、或详情页自行探测出第二个答案即失败（§8.1.1） | ① 单测 spy `PluginDistributionSourceResolver.resolve`：同一构建内同一 `name@version` 只调 1 次（记忆化生效），索引生成 1 次、详情页请求 1 次，且两次返回值同源；② 架构门禁：除 resolver 实现与其两个调用方外，`grep` 不到对 `NpmRegistryPort` 的探测式调用（禁止第二处判定）；③ 参数化三场景（镜像 200 / 明确 404 / 502）双向断言索引 JSON 与详情页 DTO 的 `source` 完全一致；④ 502 场景断言指针未切换（`current.json` 前后 sha256 相等）+ `last_error` 记首条 + 告警计数 1；⑤ 详情页 60s 只读缓存只允许落在该调用点：两次请求断言探测仍为 1 次 |
| S19 | **详情页不做版本选择器**：详情页 DOM 中**不存在**任何版本选择控件（`select`、`[role=listbox]`、`input[type=radio]`、`input[name=version]`、版本输入框一律为 `null`），页面上只有**一个**只读版本号展示 + **一个**「复制提示词」按钮；历史版本区每行只读，**不得**含复制/安装/切换控件；路由与请求**不得**携带 `?version=`，`installPrompt` **不接受** `version` 参数。正例：打开详情页只发一次详情请求，URL 无 `version` query。反例：带 `?version=1.0.0` 请求详情，响应必须与不带参数时**逐字节相等**（正文未把该参数定义为 400，故按"不接受 = 忽略"口径，且不因此改变 `installPrompt`）；响应随 `?version=` 变化、或页面出现第二个复制/安装入口即失败（§7.2.1） | ① 前端 DOM 断言：`container.querySelector('select, [role=listbox], input[type=radio], input[name=version]') === null`；`getAllByRole('button', { name: /复制\|copy/i }).length === 1`；历史版本区 `querySelectorAll('button, a[download], [role=button]').length === 0`；② 路由断言：详情页挂载后 `location.search` 不含 `version`；③ MockMvc 参数化：带与不带 `?version=1.0.0` 两次响应 body 的 sha256 相等，且两处版本仍等于最新 `LISTED`；④ OpenAPI 快照断言：该端点 `parameters` 中无 `version` |
| S20 | **审核面只谈"上架 / 未上架"，不透出平台内部动作**：审核队列项、审核详情、approve 响应、reject 响应这四类 DTO **不得**含 `publishState` / `published` / `publishAttempt` / `publishError` / `nextAttemptAt` 中任一字段（approve 与 reject 响应体只允许 `status`）；所有用户可见表面（页面、进度、失败原因、风险提示、安装文档、通知、运营物料）**不得**出现「代发」「内部源」「私服」「推送」这类内部动作或内部分发通道说法；固定文案「该版本尚未上架，通过后才会进入插件市场与索引」必须常驻审核页且四语言齐平。白名单：内部代码注释、worker 名、审计 `detailJson`、运维手册——这几处本来就要能区分"代发"与其他动作，不算违规。正例：审核详情只给 `sha512` + 扫描指纹 + 版本包名 + 固定文案 + 四个只读能力字段。反例：DTO 多一个 `publishAttempt`、文案写「已推送到内部源」、固定文案缺某一语言——任一出现即失败（§7.2 硬规则 6、§5 用户可见表面硬约束 3） | ① 四条 DTO 序列化黑名单断言（队列项 / 详情 / approve / reject 各一条），对 5 个字段名参数化，命中即失败；② 静态门禁 `grep`：范围 `web/src/features/plugin/**`、`web/src/pages/**`、`web/src/i18n/locales/{zh,en,ko,ru}.json`、安装文档模板，正则 `代发\|内部源\|私服\|推送\|internal registry`，排除白名单（`server/**/worker/**`、审计 `detailJson` 构造处、`.java` 注释、`docs/ops/**` 运维手册）；③ i18n 一致性测试：固定文案四语言逐语言比对且与 `PRD §6.5` 原文一致，缺一语言即失败；④ 反例回归：故意往审核页文案塞「代发」→ 门禁必须红（验证门禁本身有效） |
| S21 | **文件清单一次返回完整树，不提供逐目录接口**：`GET /api/v1/plugins/submissions/{id}/files` **不接受** `dir` / `path` 目录参数（OpenAPI 不声明该参数；传了必须被忽略，响应与不带参数**逐字节相等**），响应 `entries[]` 是扁平 manifest，**不含**目录节点、`children`、`depth`、`type:"dir"` 等结构字段；**不得**新增 `.../files?dir=`、`.../files/tree`、`.../files/{dir}` 这类逐目录拉取端点——一旦后端按目录返回，就会出现"展开一个目录 = 一次请求"的 N+1 与"清单与目录视图不一致"的一致性窗口。正例：含 3 层目录的夹具包，一次请求返回全部文件条目，目录树由前端 `features/skill/file-tree-builder.ts` 构建。反例：传 `?dir=src` 返回该目录子集、清单里出现目录条目、或存在第二个清单端点即失败（§7.3） | ① MockMvc 参数化：不带参数 / `?dir=src` / `?path=src/` 三次响应 body 的 sha256 全相等；② schema 严格断言：`entries[]` 每项只含 `path/size/binary/sensitive` 四键，出现 `children`/`depth`/`type` 即失败；③ 路由快照断言：OpenAPI 中该端点 `parameters` 为空数组，且不存在 `.../files/tree`、`.../files/{dir}` 路径；④ 夹具断言：`entries.length` == tar 条目数、`entries[].path` 全部为文件路径（含空目录与隐式目录的包不产生任何目录条目）；⑤ 前端断言：文件树只调一次清单接口（请求 spy 计数 == 1），展开/收起目录不发请求 |
| S22 | **禁止按 `message` 分支**：`web/src/features/plugin/**` 与审核、错误处理相关前端代码**不得**出现 `message.includes(`、`serverMessage`（任何引用）、作用在错误文案上的 `.startsWith(` / `.endsWith(` / `.indexOf(`，以及按错误字符串长度或子串推断分支的写法；一切分支只许用 `errorCode`，未知码降级为通用文案 + 记一条前端日志（不抛错、不白屏）；错误响应 schema 的 `message` 必须标注"仅供展示与日志"，且**不得**带 `enum` / `pattern` 或任何契约语义；反面案例必须清掉——`web/src/features/publish/publish-error-utils.ts` 的 `VERSION_EXISTS_MARKERS`（含俄语字面量）不得被复制到插件侧，其自身在本变更内一并移除。正例：四语言文案任意修订，分支结果不变。反例：把 `message` 换成另一种语言的字符串后分支结果变化、或 `message` 被用于决定渲染哪个按钮/动作——即失败（§7.2.2、§7.4 落地硬规则 3） | ① ESLint 自定义规则 `plugin/no-error-message-branching`（`error` 级）：作用域 `web/src/features/plugin/**` 与 `web/src/features/publish/publish-error-utils.ts`，禁 `CallExpression[callee.property.name=/^(includes\|startsWith\|endsWith\|indexOf\|match\|search)$/]` 作用于 `message` 系标识符，`Identifier[name='serverMessage']` 任何引用即报错；② CI `grep` 门禁（`make web-lint-gate`）复跑同一规则做双保险，命中即阻塞合并；③ 单测：遍历 §7.4.1 全部 `errorCode`，把对应 i18n 文案替换为任意串后断言 `plugin-error-codes.ts` 映射出的 `guidance` 与动作不变；未知 `errorCode` 断言降级且不抛错；④ OpenAPI 断言 `PluginError.message` 的 description 含"仅供展示与日志"且无 `enum`/`pattern`；⑤ 反面案例断言：`web/src/features/plugin/**` 下 `grep` 不到 `VERSION_EXISTS_MARKERS` |

### 可见范围的回归断言（S23–S24，随 `PRD §6.3`/`§8.2`/`§8.4` v2.16 同批落地）

这两条守的是同一件事：**可见范围是"能不能被看见"的契约，不是展示偏好**。索引过滤漏一次 = 私有插件进了公共索引；判定散落一处 = 某个入口忘了判。两条都必须进 CI。

| # | 断言 | 手段 |
|---|---|---|
| S23 | **索引只收 `PUBLIC`**：`NAMESPACE_ONLY` / `PRIVATE` 可见范围的插件、以及私有空间下的任何插件，**绝不出现在** `/dsh/index.json`（即使其下版本全部 `LISTED`）；可见范围非 `PUBLIC` 是**收录过滤**而不是构建失败——同一份索引里其余 `PUBLIC` 插件必须正常产出、指针必须照常切换。正例：三个夹具插件（`PUBLIC` / `NAMESPACE_ONLY` / `PRIVATE`）同批入库，索引 `plugins[].name` 集合恰为 `PUBLIC` 那一个，另两个的包名与 `pluginKey` 在索引字节里 grep 不到。反例：把该 `PUBLIC` 插件改成 `NAMESPACE_ONLY` → 下一轮重建后条目必须消失（`markDirty` 已触发、`gen` 前进、指针切换、`dirty=false`）；若索引里仍留着它，或因它整份构建失败，即失败（§8.1 收录前置条件；注意 §8.4 的 17 条强校验**不含**可见范围——非 `PUBLIC` 是**过滤**，不是构建失败） | ① 生成器单测：`{PUBLIC, NAMESPACE_ONLY, PRIVATE} × {公开空间, 私有空间}` 四个夹具各一个插件，断言索引里 `plugins[].name` 的集合恰为「公开空间 + `PUBLIC`」那一个；② 切可见范围后重建用例：断言新 `gen` 对象里条目消失、`current.json` 已切换、`plugin_index_state.dirty=false`；③ 断言"过滤 ≠ 失败"：非 `PUBLIC` 存在时 `last_error` 仍为空、§8.4 的 17 条强校验全过；④ 索引字节 grep：非 `PUBLIC` 插件的 `package_name`、`pluginKey`、`icons/{pluginKey}.*` 均不出现；⑤ 真机交叉：DSH 添加索引源后只能搜到 `PUBLIC` 那个 |
| S24 | **范围外用户一律"看不见"，文案按 §8.4 判定表**：`NAMESPACE_ONLY` 插件对非该空间成员、`PRIVATE` 插件对非作者、私有空间插件对非成员——**详情页 403**（不泄漏存在性，不返回空列表、不渲染空态），**安装文档与 `/dsh/*` 下该插件任何路径 404**（两套语义不合并，§13 #18）；状态文案按判定表三档：`NAMESPACE_ONLY` →「仅命名空间」、`PRIVATE` →「私有」、私有空间下的 `PUBLIC`/`NAMESPACE_ONLY` →「已通过（仅空间内可见）」，且**必须**由 DTO 的 `visibility` + `namespace.isPublic` 两个字段判定，前端不得按"空间是否公开"单维推断。正例：空间成员打开 `NAMESPACE_ONLY` 插件 → 200 +「仅命名空间」+ 对应稳定说明句。反例：非成员拿到 200、403 与 404 被合并成同一个码、或 `NAMESPACE_ONLY` 在公开空间里显示「已上架」（按空间猜的典型表现）——任一出现即失败（§8.1、§8.4） | ① 权限矩阵参数化：4 行判定表 × `{匿名, 非成员, 空间成员, 作者, 空间 ADMIN, 平台管理员}` × `{详情页, 安装文档}`，逐格断言 200 / 403 / 404 与表一致；② 文案快照：四语言 × 三档文案 + 三条稳定说明句（`NAMESPACE_ONLY` / `PRIVATE` / 私有空间）逐字符比对，缺任一语言即失败；③ 反例单测：把 DTO 的 `namespace.isPublic` 置 `true` 而 `visibility=NAMESPACE_ONLY`，断言渲染出的仍是「仅命名空间」而非「已上架」（证明判定入口读的是两个字段）；④ 复跑 S20 的 grep 门禁：新增文案不得含「代发 / 内部源 / 私服 / 推送」；⑤ 与 S23 共用夹具做交叉断言：同一条目既不在索引里、也对范围外用户不可见 |

### 两步提交与用户侧披露全撤的回归断言（S25–S26，随 `PRD §6.3`/`§5.1`/`§7` v2.19 引入、v2.20 ② 收紧）

这两条守两步提交与"用户侧不展示任何披露"两个核心改动（v2.20 把 S26 从"详情页下沉"收紧为"用户侧全撤"），验收条目仍 27 条（不新增 PRD 验收），只是把设计契约写成"故意做错就红"的门禁。

| # | 断言 | 手段 |
|---|---|---|
| S25 | **未确认不改状态**：提交页第一步「下一步：确认」**只打开** `ConfirmSubmitDialog`，不得 `POST /submissions`、不得产生 `submissionId` / 受理记录 / 审计 / 状态；弹窗「返回编辑」或右上关闭放弃本次确认，系统里仍无该提交；只有点「确认发布」才发**一次** `POST` 并进入 `RECEIVED`。正例：确认前查 `GET /submissions?terminal=false` 无该 `name@version` 行；点确认后恰有一条 `PLUGIN_SUBMISSION_CREATED`。反例：点「下一步」即收到 202、弹窗打开时进度视图已出现、「返回编辑」后仍留下 submission——任一出现即失败（§4.1、§5.3、§7.2、§10.2） | ① 前端单测：spy `POST /submissions`，点「下一步」断言调用次数 0、「确认发布」断言恰好 1 次；②「返回编辑」用例断言无 mutation、无 `invalidate(inFlight)`；③ 集成测试：确认前直查 submissions 表无行、确认后恰一行 + 一条审计；④ 可见性联动：表单改 `visibility` 后弹窗「可见性」行即时同步（同一 DTO 字段，不另存副本） |
| S26 | **用户侧不展示任何披露、治理侧仍完整**（v2.20 ② 收紧）：详情页 / 市场卡片 / 安装文档 / 索引文件 DOM 中**不存在**能力清单 / 红线列表 / `文件:行号` 证据折叠 / 扫描器版本时间 / 裁决徽标 / 风险摘要 / 免责说明（`security-disclosure`、`verdict-meta`、`capability-labels`、`red-line-labels` 组件均不被用户侧页面引用）；详情页只留只读版本历史 / 安装区 / README。市场卡片无浏览数、无「复制提示词」按钮、无裁决徽标，复制次数与详情页同源。版本列表每行无「该版本裁决徽标」、来源只写 `npm`/`git`（无内部主机名/registry 域名）。**判定规则 / 四裁决 / 扫描契约 / 证据生成本身一概不动**——治理侧审核页完整报告、包内文件浏览与提交者视图仍可读到全部证据。正例：`/scan-reports/{id}` 仍返回完整证据；反例：用户侧任何界面渲染出证据片段 / 徽标 / 摘要 / 免责、浏览数进入任何界面、版本列表出现第二处来源判定——任一出现即失败（§1.1/§1.2、§5.1、§7.2.1、§7.3、§9、§10.2、§11 验收 23–24） | ① 前端 DOM 断言：详情页与卡片 `querySelector` 不到能力/红线/证据/徽标/免责节点；② 静态门禁 grep：`pages/plugin-detail.tsx` 与 `pages/plugins.tsx` 不 import `security-disclosure` / `verdict-meta` / `capability-labels` / `red-line-labels`；③ 接口断言：`security-scan` 匿名端点不返回裁决/能力/红线/证据/摘要字段，`/scan-reports/{id}` 仍含完整证据；④ 列表投影断言：`PluginPage.items[]`（市场卡片）与 `GET /api/v1/dashboard/plugins` 的 `items[]`（「我的插件」）**都含** `promptCopyCount`、都不含 `viewCount` 与任何 verdict 字段，且两处与详情页读同一个 `plugin.prompt_copy_count`（PRD §10 v2.20 S06 三处同源）；⑤ 复跑 S18/S19：来源唯一判定点与"无版本选择器"仍成立（v2.20 只改展示侧，不动这两条） |

---

## 附：与既有代码的接触点清单（供评审逐条核对）

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `server/skillhub-domain/.../domain/review/ReviewSubjectType.java` | 加枚举值 | `PLUGIN_VERSION` |
| `server/skillhub-domain/.../domain/review/ReviewService.java` | 加显式分流 | 入口对 `PLUGIN_VERSION` 抛 `DomainBadRequestException("review.unsupported_subject")`，把决策引到 `PluginReviewDecisionService` |
| `server/skillhub-domain/.../domain/review/ReviewTask.java` | 加工厂方法 | `forPluginVersion(pluginVersionId, pluginId, namespaceId, version, submittedBy)` |
| `server/skillhub-app/.../repository/JpaGovernanceQueryRepository.java` | 加分支 | `subject_type` 映射需认识 `PLUGIN_VERSION`；筛选参数透传 |
| `server/skillhub-app/.../service/ReviewPortalAppService.java` | 加分支 | `ReviewSubjectType.valueOf(...)` 已按字符串转枚举，新增值天然可用；仅需确认审核页展示字段对插件主题有值 |
| `server/skillhub-app/src/main/resources/application.yml` | 加配置块 | `skillhub.plugin.*`（见 §11.1） |
| `web/nginx.conf.template` | 加 location | `^~ /dsh/`（见 §11.2） |
| `web/src/app/router.tsx` | 加 5 条路由 | 见 §10.1；**不动** `dashboardPublishRoute.validateSearch` 的 6 个参数（§10.1 回归红线） |
| `web/src/features/plugin/confirm-submit-dialog.tsx` | 新增 | `ConfirmSubmitDialog`：两步提交第二步确认弹窗，与技能发布页 `publish.tsx` 同款五行（显示名称 / `@namespace/name@version` / 可见性与表单联动 / 简介 / 末行审核说明文案复用 `publish.reviewNotice.description`）；「返回编辑」outline +「确认发布」primary；**只有确认发布发 POST**（PRD §6.3 v2.19 ⑨、§16 S25） |
| `web/src/features/plugin/submit-back-button.tsx` | 新增 | 页头 ghost + ArrowLeft 返回按钮；**固定返回「我的插件」`/dashboard/plugins`，不读 `returnTo`、不依赖浏览器后退、不按来源改目标**（PRD §6.1 v2.20 ③；v2.19 的 normalizeSkillDetailReturnTo 口径作废；真实发布页是否覆盖 header 决定见 §14.2 Q13，产品意图不写成已实现） |
| `web/src/features/report/report-text.ts` | 复用不改 | 举报理由码文案与插件侧共用（`misleading/malicious/license/spam/other`） |
| `web/src/features/plugin/review-files.tsx` | 新增（**薄层**） | `PluginFileBrowser`：数据接入 + 布局 + 安全约束。树/高亮/类型判定**复用** `features/skill/{file-tree*,code-renderer.tsx,file-type-utils.ts}`，不重写；**禁 `innerHTML` / `dangerouslySetInnerHTML` / `iframe` / `object` / `embed`**（§10.2、§16 S4） |
| `server/skillhub-app/.../controller/PluginSubmissionFileController.java` | 新增 | 三个只读端点。响应头三件套（`text/plain; charset=utf-8` / `nosniff` / `CSP default-src 'none'; sandbox` / `no-store`）在控制器上写死，不依赖全局默认——全局一处改动就能把它们抹掉（§7.2、§16 S1–S3） |
| `server/skillhub-infra/.../plugin/PluginTarballArchiveReader.java` | 新增 | `PluginArchiveReaderPort` 实现：tgz **顺序遍历**（无随机访问）+ 六项上限 + 二进制判定 + 流式读取（不整包进内存）（§7.6.2/§7.6.3） |
| `web/src/app/layout.tsx` | 改 navItems | 移除 `publish`，新增 `plugins`；页脚保留发布链接 |
| `web/src/pages/search.tsx` | 改页头 | 补「我的技能」+「发布技能」按钮组，与插件市场同构 |
| `web/src/pages/dashboard.tsx` | 改侧栏与概览卡 | 「技能与数据」组插入「我的插件」；概览卡新增一张 |
| `web/src/features/namespace/namespace-management-nav.tsx` | 加 tab | `plugins`（`active` 类型扩展） |
| `web/src/i18n/locales/{zh,en,ko,ru}.json` | 加键 | 见 §10.5 |
| `web/src/api/generated/**` | 重生成 | `make generate-api`，不手改 |
| `scanner/skillhub_scanner_app.py` | 加路由 | `/scan-plugin-upload`（独立解包 + spawn + 独立信号量） |
| `scanner/Dockerfile` | 加运行时 | Node 22 + 锁定 `dsh-trust-check` |
| `docker-compose.yml` / `compose.release.yml` / `deploy/k8s/**` | 加配置 | 见 §11.3 |
| `server/skillhub-infra/pom.xml` | 加依赖 | `commons-compress`、`org.eclipse.jgit`、**`org.eclipse.jgit.ssh.apache`（v1 必需，MINA sshd，版本锁死）**；均仅进 infra |
| `db/migration/` | 新增 1 个文件 | `V10001__plugin_market.sql`（见 §6.1） |
