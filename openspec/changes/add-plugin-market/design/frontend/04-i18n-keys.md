# §10.5　i18n 键清单（四语言必须齐平）

> 所属：前端　｜　覆盖章节：§10.5
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

### 10.5 i18n 键清单（四语言必须齐平）

命名空间 `plugin.*`：

```
nav.plugins
sidebar.myPlugins
overview.myPlugins
namespaceManagement.plugins

plugin.market.title / .subtitle
plugin.market.indexSource.title / .subtitle / .copy / .copied
plugin.market.indexSource.step1 / .step2 / .step3
                                          （**已删除 `httpWarning` 键**：PRD §4.1 v2.19 ⑥ 删去 `http:` 警告与"索引源属于当前 profile"整句；市场页只铺三步，不展开 `format: 1` 与安全提示；相关术语定义仍在 PRD §2，不得据此在页面加提示）
plugin.market.indexSource.status.loading / .error / .empty / .ready
plugin.market.categories.all
plugin.market.category.{agent|ui|memory|communication|devtools|search-knowledge|data|security|other}
plugin.market.sort.newest / .name
plugin.market.resultCount
plugin.market.loadMore
plugin.market.empty.clearFilters
plugin.market.error.retry

plugin.card.copyCount                   → 复制提示词次数（卡片与详情页**同源同文案**，标注"按来源去重，仅供参考"；PRD §10/§11 验收 22 v2.19 ①。**不得新增浏览数字段/键**——浏览只落库、不进任何界面）
plugin.market.onlyFavorites             → 仅看收藏（v2.20 ④ 按原型 S03 补入的筛选开关；默认关闭）
plugin.market.sort.copyCount            → 复制最多（v2.20 ④ 按原型 S03 补入的排序项；默认排序仍为发布时间倒序）
（**v2.20 ② 起用户侧市场卡片不渲染任何裁决徽标**：`plugin.card.verdict.*` 三个键**不删**，但仅供提交者 / 治理侧的「我的插件」列表与审核页使用，公开市场卡片与详情页不再挂载）

plugin.detail.packageName.copy / .copied
plugin.detail.install.title
plugin.detail.install.versionLabel
plugin.detail.install.promptLabel
plugin.detail.install.copyPrompt
plugin.detail.install.copied
（v2.20 ④：`equivalentCommand` / `ignoreScriptsHint` 两键**详情页不再渲染**——详情页不展示等价命令；等价命令仍在 §5.3 安装文档内给 Agent，键保留不删）
plugin.detail.install.equivalentCommand
plugin.detail.install.ignoreScriptsHint
plugin.detail.source.npm / .git
plugin.detail.source.git.repo / .commit
plugin.detail.source.resolvedHint → 以上为服务端解析结果，不是提交时填写的原话
plugin.detail.engines.undeclared        → 未声明，装前请自行确认
plugin.detail.versions.title
plugin.detail.versions.current
plugin.detail.versions.latest           → 最新（版本列表「最新」标记；PRD §5.1 v2.19 ③）
plugin.detail.readme.snapshotNotice
（v2.20 ②：`plugin.detail.disclaimer.line` **详情页不再渲染**——用户侧本期不展示免责说明，键保留不删、供治理侧备用）
plugin.detail.disclaimer.line

                                          （**v2.20 ② 起下列 `plugin.scan.*` 判定/能力/红线/证据/指纹键全部只在治理侧与提交者视图使用**；详情页 / 市场卡片 / 安装文档均不再渲染披露，但键本身**不删**——完整报告在治理侧审核页展开，§7.3。判定规则与四裁决文案不变）
plugin.scan.verdict.clear               → 未检出风险信号
plugin.scan.verdict.review              → 需确认
plugin.scan.verdict.red                 → 有红线
plugin.scan.verdict.waived              → 管理员已确认风险
plugin.scan.capability.shell / .fs-read / .fs-write / .network / .credentials
plugin.scan.capability.env / .subagent / .host-runtime / .llm / .dynamic-code
plugin.scan.redLine.install-script / .core-tamper / .creds-network
plugin.scan.redLine.plaintext-http / .literal-ip
plugin.scan.evidence.title
plugin.scan.disclaimer.title / .body / .limitation1..5
plugin.scan.toolVersion / .scannedAt / .fingerprint
plugin.scan.falsePositive.link

plugin.submit.title / .subtitle
plugin.submit.namespace.label / .help
plugin.submit.source.npm / .git / .zip / .folder
plugin.submit.source.npm.name / .nameHint    （只有包名一个输入框，可 scoped；无 .version / .versionPlaceholder：表单不得出现版本输入框，版本由服务端取 latest 并冻结）
plugin.submit.source.git.url / .urlHint / .preflight
                                          （只有仓库地址一个输入框；无 .path / .ref / .tag / .commit / 凭据输入；.commitHint 仅用于进度/详情页展示提交号）
plugin.submit.source.git.preflight.ok / .hostNotAllowed / .unreachable / .authFailed / .repoNotFound / .defaultBranchMissing
plugin.submit.source.git.postClone.notFound / .fieldNotSupported
plugin.error.versionExists.hint              → 该版本已存在，请先在仓库中升级版本号再提交（Git 来源专用）
plugin.error.versionExists.npmHint           → 该版本已存在，请先在上游 npm 发布新版本后重新提交
plugin.submit.source.npm.fieldNotSupported   → npm 来源只填包名，版本由服务端自动取最新非预发布版本，不支持指定版本
plugin.submit.source.zip.drop / .hint
plugin.submit.source.folder.drop / .hint / .unsupported
plugin.submit.visibility.label / .help    （右栏「发布设置」区第二项，紧跟命名空间；**必选、无留空、无默认**，UI 可预选「公开」但必须显式提交；三档标签复用技能侧 `publish.visibilityOptions.*`，不新增同义键）
plugin.submit.category.label / .help
plugin.submit.autoParsed.title / .readonlyHint
plugin.submit.checklist.title / .item.{...}
plugin.submit.action.next                 → 下一步：确认（第一步唯一按钮，只打开确认弹窗、不提交）
plugin.submit.action.nextHint             → 提交前将再次核对版本与展示信息
plugin.submit.confirm.title               → 确认提交信息（第二步弹窗，PRD §6.3 v2.19 ⑨）
plugin.submit.confirm.displayName / .packageVersion / .visibility / .summary / .reviewNote
                                          （弹窗五行，与技能发布页 `publish.tsx` 同款：显示名称 / `@namespace/name@version` / 可见性 / 简介 / 末行审核说明文案——复用 `publish.reviewNotice.description`"提交后的处理取决于可见性和安全扫描策略。请以本次提交回执及版本状态为准。"；可见性行与表单 `VisibilitySelect` 联动即时同步，PRD §6.3）
plugin.submit.confirm.backToEdit          → 返回编辑（outline）
plugin.submit.confirm.publish             → 确认发布（primary，**唯一发起 POST 的入口**）
plugin.submit.back                        → 返回我的插件（ghost + ArrowLeft；**固定返回「我的插件」`/dashboard/plugins`**，不靠浏览器后退、不按来源改目标；产品意图 PRD §6.1 v2.20）
plugin.submit.capability.disabled.reviewNotReady       → 审核将在治理阶段开放
plugin.submit.capability.disabled.publishDisabled        → 插件提交暂时关闭，请稍后再试
plugin.submit.capability.disabled.spaceNotEnabled        → 该空间尚未开放插件提交
plugin.submit.progress.title / .stage.RECEIVED / .MATERIALIZING / .SCANNING
plugin.submit.progress.stage.REVIEW_PENDING / .PUBLISHING / .LISTED
plugin.submit.progress.stage.SCAN_RED / .SCAN_FAILED / .REVIEW_REJECTED / .PUBLISH_FAILED / .VALIDATION_FAILED / .CANCELED
plugin.submit.progress.redLinesTitle / .retry / .editPackage
plugin.submit.progress.stillRunning

plugin.mine.title / .subtitle
plugin.mine.inFlight.title
plugin.mine.inFlight.empty                → 当前没有进行中的提交
plugin.mine.noInstallableVersion          → 已无可安装版本
plugin.mine.inFlight.waiting              → 已等待 {duration}
plugin.mine.inFlight.editPackage          → 改包后重新提交
plugin.mine.filter.q / .status / .namespace / .category / .verdict / .inFlight
plugin.table.column.plugin / .version / .status / .category / .verdict / .copyCount / .actions
                                          （`.copyCount` 为「我的插件」列表的复制提示词次数列，与 `plugin.card.copyCount` 同源同字段同文案、取 `promptCopyCount`，PRD §10 v2.20 S06；**不得新增浏览数列/键**）
plugin.table.action.view / .hide / .restore / .reason / .resubmit / .rescan / .review
plugin.table.action.yank / .unyank / .delete / .releaseName
plugin.table.action.yankConfirm / .deleteConfirm / .releaseNameConfirm
plugin.status.DRAFT / .PENDING / .LISTED / .HIDDEN / .BLOCKED / .REMOVED
                                          → 草稿 / 待上架 / 已上架 / 已下架 / 已限制 / 已删除
plugin.status.BLOCKED.hint                → 已被管理员加入黑名单，同包名无法再次提交
plugin.versionStatus.SCAN_PASSED / .REVIEW_PENDING / .APPROVED / .PUBLISHING / .LISTED / .REJECTED / .YANKED
                                          → 待审核 / 等待审核 / 待发布 / 发布中 / 已上架 / 未通过 / 已撤下
                                          （私有空间把「已上架」换成「已通过（仅空间内可见）」；状态名不得自造）
plugin.namespace.title / .tabs.plugins / .empty.noPlugins / .empty.noMatch / .empty.cta

plugin.visibility.PUBLIC / .NAMESPACE_ONLY / .PRIVATE
                                          → 公开 / 仅命名空间 / 私有
                                          （**标签复用技能侧 `publish.visibilityOptions.public / .namespaceOnly / .private`，不新增同义词**；
                                            本组只用于状态文案与说明句，`PRD §6.3` v2.16 明确"取值与中文标签沿用技能侧"）
plugin.visibility.status.listed           → 已上架
plugin.visibility.status.namespaceOnly    → 仅命名空间
plugin.visibility.status.private          → 私有
plugin.visibility.status.spacePrivate     → 已通过（仅空间内可见）
                                          （四档由 `visibility` + `namespaceIsPublic` 判定，禁止按空间猜；§8.4、§16 S24）
plugin.visibility.notice.namespaceOnly    → 该插件可见范围为仅命名空间，不会出现在公共索引中，空间成员可在站内查看
plugin.visibility.notice.private          → 该插件可见范围为私有，仅作者本人可见，不会出现在公共索引中
plugin.visibility.notice.spacePrivate     → 该插件所属空间为私有，不会出现在公共索引中
                                          （三条稳定说明句，`PRD §8.4` 原文；四语言齐平，不得意译成"同事能搜到"这类承诺）
plugin.visibility.formHint.public         → 空间公开时进入索引源，DSH「发现」页可搜到
plugin.visibility.formHint.namespaceOnly  → 不会进入索引源：DSH 搜不到，仅该命名空间成员在站内可见
plugin.visibility.formHint.private        → 不会进入索引源：DSH 搜不到，仅作者本人可见
                                          （**提交页 `VisibilitySelect` 下方的预提交提示，与上面三条 `notice.*` 是两个表面、两套措辞，不得合并**：表单说的是"还没提交、将来会怎样"，
                                            插件此刻还不存在，写"该插件…"不成立；详情页与列表说的是已存在插件的陈述句。`formHint` 在非 `PUBLIC` 时必须同时加醒目样式
                                            （`PRD` 口径：选到「公开」以外立刻提示"不会进入索引源"，不等到提交才知道；原型 S05 第 09 条））
plugin.visibility.indexStatus.notPublic   → 未收录：可见范围非公开
                                          （**照抄 `PRD §3.4` 字面量**，不新造第二个索引状态枚举，§5.4）
plugin.visibility.change.title / .label / .help
plugin.visibility.change.widenDisabled    → 放宽可见范围需重新提交（v1 只受理缩窄，变宽方向置灰，§7.2、§14.2 Q11）
plugin.detail.report.entry                → 举报
plugin.review.staged                     → 暂存包（尚未上架）
plugin.review.fingerprint                 → 内容指纹
plugin.review.reportAge                   → 报告生成于 {days} 天前 · 工具 {version}
plugin.review.publishState.PENDING / .PUBLISHING / .FAILED / .LISTED
                                          → 待发布 / 发布中 / 发布失败 / 已上架
                                          （**仅「我的插件」与治理页渲染；审核页一律不得出现任何发布/代发状态文案**，§7.2 硬规则 6、§16 S20）
plugin.review.publishRetry                → 重试发布（同上，仅「我的插件」与治理页）
plugin.review.files.title                 → 包内文件（只读）
plugin.review.files.notice                → 内容来自本次提交的暂存包，与 sha512 为同一份字节；只读不执行
plugin.review.files.empty                 → 该包内没有可浏览的文件
plugin.review.files.binary                → 二进制文件（{type}，{size}），不提供文本预览
plugin.review.files.sensitive             → 可能含凭据
plugin.review.files.tooLarge              → 文件过大（{size}），超过 512 KiB 不自动预览，请下载后查看
plugin.review.files.download              → 下载
plugin.review.files.purged                → 包内容已按保留策略清理（保留 {days} 天）
plugin.review.files.limitExceeded         → 该包超出可浏览上限（{limit}），请联系作者重新打包
plugin.review.files.mismatch              → 暂存包内容与记录的 sha512 不一致，已阻止浏览并告警
plugin.review.files.loading / .error      → 加载中 / 无法读取包内文件
plugin.review.files.pending               → 包还在准备中，稍后刷新
plugin.review.authorSelfNotice            → 你是本次提交的作者（中性提示，不阻断操作）
plugin.review.canReview.reason.notSpaceAdmin → 仅该空间的空间管理员可审核
plugin.review.canReview.reason.notPending   → 该插件版本已不在待审状态
plugin.review.reject.dialogTitle / .reasonLabel / .reasonRequired
plugin.report.title
plugin.report.reason.label / .reason.misleading / .reason.malicious / .reason.license / .reason.spam / .reason.other
plugin.report.details.label / .detailsRequired
plugin.report.submit / .cancel / .accepted

plugin.error.packageNameTaken / .versionExists / .submissionInFlight
plugin.error.invalidPackage / .gitUnresolved / .archiveTooLarge / .archiveUnsafe
plugin.error.noPermission / .uploadSourceDisabled
plugin.error.scanRedLine / .scanFailed / .scanInvalidReport / .scanRateLimited
plugin.error.registryPublishFailed / .packageNameMismatch / .submitRateLimited
plugin.error.iconInvalid / .packageNameReserved / .packageNameBlocked / .versionNotYankable
plugin.error.publishArtifactMissing / .notRemoved
plugin.error.reportRateLimited / .reportDetailsRequired
plugin.error.versionNotReviewable / .publishNotRetryable / .gitHostNotAllowed
plugin.error.gitUnreachable / .gitAuthFailed / .gitRepoNotFound / .gitDefaultBranchMissing / .gitPackageNotFound / .gitFieldNotSupported / .versionFieldNotSupported
plugin.error.stagedSubmissionNotFound / .stagedFilePathInvalid / .stagedFileNotFound
plugin.error.stagedFileTooLarge / .stagedArchiveLimitExceeded / .stagedPackagePurged
plugin.error.stagedPackageMismatch / .fileBrowseDisabled
plugin.error.visibilityInvalid            → 请选择可见范围（公开 / 仅命名空间 / 私有）
plugin.error.visibilityWidenPending       → v1 不支持放宽可见范围，如需放宽请重新提交（固定文案，不得改写为鼓励用户反复重试的说法）

plugin.disclaimer.neverSaySafe           （文案契约自检用；不渲染）
```

**用户可见文案清单（照抄 PRD 原文，页面上不露 code）**——这几句是产品定稿的措辞，实现时不得自造、不得替换成错误码：

| 场景 | 页面原文 |
|---|---|
| 撤掉最后一个可上架版本后（作者在「我的插件」看到） | 已无可安装版本 |
| 未上架 / 待审核 / 已通过待发布（非成员访问） | **详情页 403、安装文档 404，两套语义不合并**（PRD §8.4/§9；均不返回任何内容、不写"审核中"、不写"未上架"） |
| 恢复被撤版本时私服已取不到制品（409 `publishArtifactMissing`） | 该版本制品已不存在，请重新提交该版本 |
| 包名在黑名单（409 `packageNameBlocked`） | 该包名已被限制，请联系管理员 |
| 包名已软删未释放（409 `packageNameReserved`） | 该包名已被下架，名额未释放 |
| 包名被他空间收录（409 `packageNameTaken`） | 已被 {namespace} 空间收录 |
| 同名同版本 `package_name@version` 已存在，**Git 来源**（409 `versionExists`；最常见是默认分支未推进且包内版本号未改，但判定只看 `name@version`，与 commit 无关） | 该版本已存在，请先在仓库中升级版本号再提交 |
| 同名同版本 `package_name@version` 已存在，**npm 来源**（409 `versionExists`；latest 未变，服务端解析出的冻结版本撞库） | 该版本已存在，请先在上游 npm 发布新版本后重新提交 |
| Git 仓库根目录缺含 DSH 插件声明的 `package.json`（`gitPackageNotFound`，克隆后校验） | 仓库根目录必须包含含 DSH 插件声明的 package.json（一个插件一个仓库），请按规范调整仓库结构后重新提交 |
| Git 请求夹带 `path` 等本期不支持字段（400 `gitFieldNotSupported`） | 本期不支持子目录，请将插件包置于仓库根目录 |
| npm 请求夹带 `version` 等本期不支持字段（400 `versionFieldNotSupported`） | npm 来源只填包名，版本由服务端自动取最新非预发布版本，不支持指定版本 |
| Git 详情页来源行（§5.1） | 仓库地址 + 提交号；均为**服务端解析出的最终值**，不是用户原话 |
| 私有空间、非成员打开**详情页**（403） | 无权限访问该空间 |
| 私有空间、任何人取**安装文档 / `/dsh/*`** | 404，无内容（不是无权；两个语义不合并，见 §13 #18） |

**内部标注文案（仅管理员/审核人可见；`PRD` 未覆盖，本设计补充）**——这几句不进对外页面，但必须把后果说全：

| 场景 | 后台原文 |
|---|---|
| 版本 `APPROVED` 且 `publish_attempt > 0` | 已通过审核，等待发布 · 上次失败：{code} · 已重试 {n} 次 |
| 代发连续失败达阈值 | 代发连续失败，需人工介入 · {name}@{version} · 最后错误：{code} |
| 版本 `REJECTED` | 已拒绝 · 制品未推送（无需清理） |
| 审核页「包内文件」已过保留期 | 包内容已按保留策略清理（保留 {days} 天）· 指纹与报告仍可查 |
| 审核页「包内文件」超出浏览上限 | 该包超出可浏览上限（{limit}），请联系作者重新打包 |
| 审核页「包内文件」指纹对不上 | 暂存包内容与记录的 sha512 不一致，已阻止浏览并告警 |

> 第二行是运维视角的告警文案，第一行是作者/管理员在后台看到的行级标注。**链路反转后不再有"制品残留在内部源"这一类文案**——审核拒绝时制品从未推送过，`dsh plugin add name@version` 无从装到。

硬约束：`plugin.scan.*` 的 10 个能力与 5 个红线 code 必须四语言齐全；`plugin.scan.disclaimer.limitation1..5` 必须照抄 `PRD §7.1` 的工具已知局限语义，不得改写、不得弱化。`plugin.submit.capability.disabled.reviewNotReady` 必须照抄 `PRD §12`（v2.11）P0 行原文「审核将在治理阶段开放」，不得改写。

