# §10.2　页面与组件树

> 所属：前端　｜　覆盖章节：§10.2
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

### 10.2 页面与组件树

```
pages/plugins.tsx
├── PluginMarketHeader          (标题 + 「我的插件」outline + `PluginSubmitButton`，登录才渲染；按钮可用态见 §10.1 能力开关)
├── PluginIndexSourceCard       (`PRD §4.1`)
│   ├── IndexUrlCopyRow         (等宽 + 复制 2s 成功态)
│   ├── IndexSourceSteps        (3 步)
│   └── IndexStatusHint         (加载中/失败/空/正常 四态)
└── PluginGridSection
    ├── PluginToolbar           (搜索 debounce 300ms + 排序 + 结果数)
    ├── PluginCategoryChips     (9 类 + 全部)
    ├── PluginGrid → PluginCard (图标/标题/包名/简介/分类/版本/复制次数/需确认徽标)
    └── LoadMoreButton

pages/plugin-detail.tsx
├── PluginDetailHeader          (图标/标题/包名复制/分类/作者/版本/更新时间/复制次数)
│   ├── VisibilityStatusBadge   (状态文案三档：`NAMESPACE_ONLY`→「仅命名空间」、`PRIVATE`→「私有」、私有空间→「已通过（仅空间内可见）」、否则「已上架」；**由 DTO 的 `visibility` + `namespaceIsPublic` 判定**（§8.4、§16 S24），前端不按空间猜)
│   └── PluginReportEntry       (举报入口：文字按钮「举报」+ 图标；登录才渲染，匿名点击走登录并回跳本页)
├── VisibilityNoticeLine        (稳定说明句：仅命名空间 / 私有 / 所属空间为私有（§10.5 的 `plugin.visibility.notice.*`），非 `PUBLIC` 或私有空间才渲染)
├── PluginReadmePanel           (快照 Markdown，图片仅 https:)
├── PluginVersionList           (只读历史：版本/时间/来源/engines/裁决徽标/是否已撤版；**不含**逐版本复制、安装或切换入口——多给一个可复制版本就等于让用户装到"详情页没在卖"的版本，`PRD §5.2` v2.15)
├── InstallPanel (sticky)
│   ├── LatestVersionReadout    (只读版本号，无下拉/单选/输入框；页面上没有任何控件能改变它，§16 S19)
│   ├── InstallPromptBox        (全站唯一提示词生成点)
│   ├── EquivalentCommand       (折叠，含 --ignore-scripts)
│   ├── SourceInfo              npm：registry 地址；git：**仓库地址 + 提交号**
│   │                           （两者都是服务端解析结果，不是用户原话；包固定在仓库根目录；PRD §5.1）
│   └── EnginesInfo
│   └── ScanReportLink          (指向完整扫描报告；原名 ReportLink 与"举报"易混，改名)
└── PluginSecurityDisclosure
    ├── VerdictBadge            (未检出风险信号 / 需确认 / 有红线 / 管理员已确认风险)
    ├── CapabilityChips         (10 个能力 code → 中文)
    ├── RedLineList             (5 个 code → 中文模板 + {占位})
    ├── EvidenceCollapse        (文件:行号 + 片段)
    ├── WaiverNotice            (放行者/时间/理由摘要)
    └── DisclaimerBlock         (已知局限 5 条 + 工具版本/时间/指纹)

PluginReportDialog                (举报弹窗，挂在详情页：理由码下拉 + details(仅 other 必填) + 提交后「已收到，管理员会跟进」态)

pages/dashboard/plugin-submit.tsx
├── PluginSubmitForm            (两栏 grid：主列 + 右侧 sticky 设置区；与技能发布页 `web/src/pages/dashboard/publish.tsx:442` 同构)
│   ├── 主列
│   │   ├── SourceTabs (npm/git/zip/folder)
│   │   │   ├── NpmSourcePanel（只有包名一个输入框，可 scoped；无版本输入框，版本服务端取 latest 并冻结）
│   │   │   └── GitSourcePanel（只有仓库地址一个输入框）/ ZipSourcePanel / FolderSourcePanel
│   │   ├── CategoryPicker (9 选 1)
│   │   ├── AutoParsedPanel     (只读：name/version/engines/repository/title/readme/icon)
│   │   ├── SubmissionChecklist (提交前校验项 + 异步阶段说明；**从右栏移到这里**，非 sticky，位于自动解析块之后、提交按钮之前，条目不变)
│   │   └── SubmitActions       (提交按钮 + 未就绪原因)
│   └── PluginPublishSettings   (右栏 `<aside>`，`lg:sticky lg:top-24 lg:col-start-2 lg:row-start-1 lg:row-span-3`，标题「发布设置」)
│       ├── NamespaceSelect     (必填；带 `?namespace=` 预填；**更新版本时 `disabled`**)
│       └── VisibilitySelect    (必填、三档「公开 / 仅命名空间 / 私有」；下方一行说明文案用 `plugin.visibility.formHint.*`（**不是**详情页的 `notice.*`，两套措辞见 §10.5），`aria-describedby` 关联；选到非 `PUBLIC` 时该行立刻换醒目样式并明说"不会进入索引源")
└── SubmissionProgressView      (提交后轮询视图：阶段时间线 + 红线证据 + 重试/改包重提)

`PluginPublishSettings`（`features/plugin/publish-settings.tsx`）的三条硬约束：

1. **两块都常驻右栏、滚动不消失**（`lg:sticky lg:top-24`），与技能发布页同构——命名空间与可见范围是"提交到哪、给谁看"的决策，不该滚出视野。数据源：命名空间可选项与禁用态来自 `usePluginSubmitCapability`（§10.1 能力开关），可见范围是提交 DTO 的 `visibility`（§7.3）。
2. **可见范围必选、无留空**：UI 可预选「公开」，但**必须作为显式入参提交**——服务端不兜底（§7.3、§7.4.1 `PLUGIN_VALIDATION_VISIBILITY_INVALID`）。三档标签**复用技能侧既有键**（`publish.visibilityOptions.public` / `.namespaceOnly` / `.private`）与设置区标题键（`publish.settings`），插件侧只新增说明句 `plugin.visibility.notice.*`（§10.5），不新造同义词。
3. **不复用技能发布页的路由契约**：插件提交页有自己的路由，`dashboardPublishRoute.validateSearch` 的 6 个查询参数一个都不动（§10.1 回归红线第 1 条、§16 回归表第 1 行）。

pages/dashboard/my-plugins.tsx
├── MyPluginsHeader             (标题 + `PluginSubmitButton`；与市场页头、空间 tab 页头**同一组件同一态**)
├── SubmissionsInFlight         (在途提交区：**以提交为单位**，不是以插件为单位)
│   ├── SubmissionRow           (包名@版本 / 来源 / 当前阶段 / 已等待 / 进度条 / 放行进度)
│   ├── SubmissionStageHint     (阶段解释 + 失败态给出可自修动作：改包重提 / 重试 / 看报告)
│   └── SubmissionEmptyHint     (无在途：「当前没有进行中的提交」)
├── MyPluginsFilterBar          (关键词 q + 状态 + 空间 + 分类 + 裁决 + 「仅看在途」开关)
└── PluginTable (共用组件)
    ├── 列：图标+标题 / 包名 / 空间 / 状态徽标 / 最新版本 / 裁决徽标 / 更新时间
    └── 操作列(查看/下架/重新上架/撤版/软删/看原因/重新提交/重新扫描)

pages/dashboard/namespace-plugins.tsx
├── NamespacePluginsHeader      (空间名 + `PluginSubmitButton`（本空间维度的可用性）+ 计数)
├── PluginTable (同一组件，数据范围与操作列不同)
└── NamespacePluginsEmptyState  (三种空态**必须区分**，见下)

features/plugin/
├── plugin-queries.ts           (query key 工厂 + hooks)
├── submit-capability.tsx       (`usePluginSubmitCapability` + `PluginSubmitButton` + `PluginSubmitDisabledHint`，三处入口唯一实现)
├── install-prompt.tsx          (提示词生成与复制 + 埋点)
├── plugin-table.tsx            (我的插件 / 空间插件共用)
├── submissions-in-flight.tsx   (在途提交区)
├── review-fields.tsx           (审核页的插件专属字段块：暂存包 sha512 / 扫描指纹 / 报告时间与工具版本 / 固定上架口径文案；**不含任何发布或代发状态字段**，§16 S20)
├── review-files.tsx            (PluginFileBrowser：**复用** features/skill 的 file-tree + CodeRenderer + file-type-utils，
│                                只做插件侧数据接入与布局；文本走文本节点、二进制只给元信息+下载)
├── report-dialog.tsx           (举报弹窗；理由码文案复用既有 features/report/report-text.ts)
├── source-forms/               (四来源表单)
└── scan/
    ├── verdict-meta.ts         (裁决 → 徽标/文案映射)
    ├── capability-labels.ts    (10 能力)
    ├── red-line-labels.ts      (5 红线 code + 占位渲染)
    ├── security-disclosure.tsx
    └── use-security-scan.ts
```

「我的插件」的两个设计点（handover 缺口 #4）：

1. **在途提交以提交为单位呈现，不以插件为单位**。原因：`RED`/`SCAN_FAILED`/`VALIDATION_FAILED` 的提交**没有版本行**（§4.2 第 3 条），按插件聚合会让"我明明提交了却看不到"——这正是用户最容易卡住的地方。数据源是 `GET /api/v1/plugins/submissions?terminal=false`（本设计新增的查询参数），与 `GET /dashboard/plugins` 分开取，两块各自分页。
2. **筛选项一一对应后端参数，不做前端过滤**：`q`（包名/标题）、`status`（`DRAFT/PENDING/LISTED/HIDDEN/BLOCKED/REMOVED`）、`namespace`、`category`、`verdict`（`clear/review/red`）、`inFlight`（只看在途）。前端过滤在分页下会给出错误的"结果数"，且 `BLOCKED`/`REMOVED` 需要后端参与权限判断。

命名空间插件 tab 的空态三分支（handover 缺口 #5）：
| 分支 | 触发条件 | 展示 |
|---|---|---|
| 真空（干净空态） | `total == 0` 且无筛选条件，且用户是该空间 ADMIN/成员 | 「这个空间还没有插件」+ 主按钮「提交第一个插件」+ 一句说明安装源是同一个 `/dsh/index.json` |
| 筛选无结果 | `total == 0` 但有筛选条件 | 「没有符合条件的插件」+ 「清除筛选」（**不出现**"提交第一个插件"，否则用户会以为数据丢了） |
| 无权 | 403 | **不渲染空态**，直接错误页（与 PRD"一律拒绝访问，不显示空态"一致；空态会被误读成"空间里没有插件"） |

审核页（**不新增路由**，复用既有治理审核页，只加一个插件字段块）：

```
既有 governance/review 页（不改路由）
└── PluginReviewFields         (subject_type=PLUGIN_VERSION 时渲染，见 §4.6 字段表)
    ├── StagedPackageBadge     「暂存包 · 尚未上架」+ sha512 前 8 位
    ├── FingerprintRow         sha512（前 16 位 + 展开）+ report.fingerprint
    ├── ReportFreshness        「报告生成于 N 天前 · 工具 vX」+ 完整报告链接
    ├── NotListedNotice        固定文案常驻：「该版本尚未上架，通过后才会进入插件市场与索引」（`PRD §6.5` v2.15 原文，四语言齐平）。**审核页不渲染任何发布/代发状态字段，也不给「重试发布」入口**——那些只出现在「我的插件」与治理页（§7.2 硬规则 6、§16 S20）
    ├── PluginFileBrowser      「包内文件（只读）」——**不自己造一套**，组合见下
    │   ├── StagedFileNoticeBar   顶部固定一行：「内容来自本次提交的暂存包，与 sha512 为同一份字节；只读不执行」
    │   ├── FileTree（**复用**）   features/skill/file-tree.tsx + file-tree-builder.ts（含虚拟滚动）；
    │   │                         插件侧只加一层 `StagedFileEntry[] → FileTreeNode` 轻量适配（不复制树实现）；
    │   │                         节点徽标：二进制 / 可能含凭据（取自 `entry.binary` / `entry.sensitive`）
    │   ├── CodeRenderer（**复用**） features/skill/code-renderer.tsx（已接 rehype-highlight）；
    │   │                         language 取 `getLanguageForHighlight(path)`；只做高亮 span，插件侧不得传 HTML
    │   ├── StagedFileBinaryPanel 二进制：类型 / 大小 / 魔数 + 「下载」按钮（不渲染、不预览、不内嵌）
    │   ├── StagedFilePurgedHint  已按保留策略清理：不渲染树，只显示保留期与仍可用的 sha512/指纹
    │   └── StagedFileLimitHint   超出可浏览上限：整块提示 + 联系作者重新打包（不展示半棵树）
    └── RejectDialog           拒绝理由（必填）+ 提示「拒绝后该版本不会上架，无需清理任何制品」
```

「暂存包」这个徽标不是装饰：审核人最容易犯的错是以为"批准的是线上版本"，而这条链路上**审核对象还没上架、平台上并没有这个版本**，所以「暂存包 · 尚未上架」与那行固定文案必须常驻可见、不能折叠——审核人要回答的问题只有一个："这个版本能不能进插件市场"（`PRD §6.5` v2.15、§7.2 硬规则 6）。

**复用关系（一句话，评审按这句 grep）**：`PluginFileBrowser` = `file-tree`（复用）+ `CodeRenderer`（复用）+ `isPreviewable` / `canPreviewFile` / `getLanguageForHighlight` / `getFileTypeLabel` / `getFileIcon`（复用）+ 插件侧数据接入（`plugin-queries.ts` 的 `stagedFiles` / `stagedFileContent`）。

平台已经有整套"看代码"的实现（技能审核页在用），插件侧**只许接数据和摆布局，不许重写判定与渲染**：

| 能力 | 复用模块（`web/src/features/skill/`） | 插件侧要做的 |
|---|---|---|
| 是否可预览、是否超上限 | `file-type-utils.ts` 的 `isPreviewable(fileName, fileSize)` / `canPreviewFile(...)` | 传入 `entry.path` 与 `entry.size`；上限取服务端下发的 `limits.maxTextBytes`，**不要在插件侧写死 512 KiB** |
| 语言识别（高亮用） | `file-type-utils.ts` 的 `getLanguageForHighlight` | 直接传 `entry.path` |
| 类型标签 / 图标 | `getFileTypeLabel` / `getFileIcon` | 树节点与二进制面板共用 |
| 代码渲染 | `code-renderer.tsx` 的 `CodeRenderer({code, language, className})`（已接 rehype-highlight） | 传 `content` 与 `language`，**不传 HTML** |
| 文件树（含虚拟滚动） | `file-tree.tsx` / `file-tree-builder.ts` / `file-tree-node.tsx` | 若节点类型被技能侧结构绑死，写一层 `StagedFileEntry[] → FileTreeNode` 的**轻量适配**（≤50 行），**不复制树实现** |

两处判定并存，但职责不同、不许互相替代：**服务端的 `binary` / 413 是权威**（安全约束在服务端，不能靠前端"不显示"来防护）；前端的 `isPreviewable` 只决定"点开时要不要发预览请求"（体验优化）。前端判定与服务端不一致时，以服务端响应为准（413 → 显示"文件过大，请下载"，不再重试）。

`use-review-file.ts`（走 `GET /api/v1/reviews/{id}/file?path=...&preview=true`）**不复用**：它绑的是 `reviewId` + 技能版本链路，且没有插件的解压上限与响应头约束；插件侧用自己的 `stagedFileContent` hook。为什么不复用后端那个端点，见 §13 #20。

`PluginFileBrowser` 的三条前端约束（与 §7.2 的响应头硬约束配对，缺一条 XSS 防线就断）：

1. **`StagedFileNoticeBar` 常驻在预览区顶部、不随滚动消失、不可关闭**。它承担的是"审核人必须知道自己在看什么"：内容是**本次提交的暂存包字节**（不是线上版本、不是上一次同名包），且**只读不执行**。这句话与 `StagedPackageBadge` 是一对——一个说对象没推上去，一个说内容没被执行。
2. **只渲染文本节点**：`<pre>{content}</pre>`（React 自动转义），高亮走 `CodeRenderer`（它输出的是高亮 span，不是 HTML 字符串）；**禁止** `dangerouslySetInnerHTML`、`innerHTML`、`<iframe src>` 指向包内文件、`<object>`/`<embed>`、把 `.svg` 当 `<img>`。这条写进 §16 回归断言（S4），用 lint + 单测双保险。
3. **不预取**：树节点**不自动加载内容**，用户点击才请求（`enabled: false` 的懒查询）。否则一个 2000 文件的包会让审核页在一个请求周期里打出 2000 次内容请求，把限流打满、把对象存储打穿。二进制节点点击只请求元信息（`files/content`），下载必须再点一次（`files/download`），两次都要过审计。
4. **审批按钮、文件树入口与"你是作者"提示都由服务端下发的字段决定，前端不做角色推断**：审核页从审核队列项/详情项读 `canReview` / `canReviewReasonCode` / `canViewStagedFiles` / `isAuthor`（§7.2），据此**置灰 + 显示原因**，既不隐藏入口、也不自己判断"我是不是空间管理员"或"这是不是我提交的"。四者相互独立：作者本人 `canViewStagedFiles=true`、`canReview=true`（自审允许）、`isAuthor=true`（按钮旁渲染中性提示「你是本次提交的作者」，灰色说明文字、不用警示色、不阻断）；平台管理员 `canViewStagedFiles=false`、`canReview=false`。**前端隐藏 + 后端放开 = 直连可绕**，所以后端断言是唯一权威，字段只负责把结果提前说清。

