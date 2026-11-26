# §7.4 + §7.4.1　错误码表、结构化错误码全集（同步校验 51 条 + 异步扫描 3 条）

> 所属：后端　｜　覆盖章节：§7.4 + §7.4.1
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

### 7.4 错误码表（PRD 已不定义错误码，本表为本设计新增）

| 场景 | HTTP | i18n key | 出现阶段 |
|---|---|---|---|
| 包名已被其他空间收录 | 409 | `plugin.error.packageNameTaken` | 同步 |
| 该插件下版本已存在 | 409 | `plugin.error.versionExists` | 同步 |
| 包名已被软删但未释放（仍占位） | 409 | `plugin.error.packageNameReserved` | 同步 |
| 包名在黑名单中 | 409 | `plugin.error.packageNameBlocked` | 同步 |
| 图标不合法（非 SVG/PNG/JPEG/WebP、超 32 KiB、SVG 含脚本或外部引用、无法解析尺寸） | 422 | `plugin.error.iconInvalid` | 同步（独立上传）/ 异步（包内 `icon` 指向的文件，`VALIDATION_FAILED`） |
| 可见范围缺失或取值非法（`visibility` ∉ `{PUBLIC, NAMESPACE_ONLY, PRIVATE}`，或四种来源漏传） | 400 | `plugin.error.visibilityInvalid` | 同步（提交前校验）；**不得兜底成 `PUBLIC`**——"没选就公开"是安全事故（`PRD §6.3` v2.16：必选、无留空） |
| 撤版/恢复的前置状态不满足 | 409 | `plugin.error.versionNotYankable` | 同步 |
| 恢复到 LISTED 时私服已取不到该制品 | 409 | `plugin.error.publishArtifactMissing` | 同步 |
| 释放包名时插件不是 `REMOVED` | 409 | `plugin.error.notRemoved` | 同步 |
| 改可见范围时请求的是**变宽**方向（`PRIVATE → *`、`NAMESPACE_ONLY → PUBLIC`） | 409 | `plugin.error.visibilityWidenPending` | 同步（`PATCH .../visibility`）；v1 只受理缩窄，固定文案「v1 不支持放宽可见范围，如需放宽请重新提交」（`PRD §6.3` v2.17 已取消"放宽需审核"口径，v1 只受理缩窄；见 §13 #22、§14.2 Q11 已裁） |
| 同 `name@version` 已有在途提交 | 409 | `plugin.error.submissionInFlight` | 同步 |
| `package.json` 缺失/非法 | 422 | `plugin.error.invalidPackage` | 同步 / 异步复核 |
| Git URL 不在白名单（host 未列 / path 前缀不符 / DNS 解析到内网敏感段） | 422 | `plugin.error.gitHostNotAllowed` | **预检失败** |
| Git 地址不可解析 / SSH 握手失败（含 host key 校验失败） / 无读权限 / 仓库不存在 / 默认分支不存在 | 422 | `plugin.error.gitUnreachable` / `.gitAuthFailed` / `.gitRepoNotFound` / `.gitDefaultBranchMissing` | **预检失败**（逐类不同文案，不许笼统报"拉取失败"） |
| Git 请求夹带 `path` / `ref` / `tag` / `commit` / 凭据等本期不支持字段（前端绕过直连） | 400 | `plugin.error.gitFieldNotSupported` | 同步（提交前校验）；固定文案「本期不支持子目录，请将插件包置于仓库根目录」 |
| npm 请求夹带 `version` / `ref` / `tag` / 凭据等本期不支持字段（前端绕过直连，任何途径传版本都不受理） | 400 | `plugin.error.versionFieldNotSupported` | 同步（提交前校验）；固定文案「npm 来源只填包名，版本由服务端自动取最新非预发布版本，不支持指定版本」。用户路径不读 version，只调 `resolveLatest(packageName)` |
| npm 来源提交前校验失败：包名不存在 / 无权限 / registry 不可达 / 只有预发布版本 | 422（或对应网络态） | 四类分别提示（错误分类保持不变，不为本次裁决增删） | 同步/预检；按 `PRD §6.4` v2.13 四类分别提示，不笼统报"拉取失败"；只取 latest 元数据，不读版本参数 |
| 克隆后：仓库根目录未找到含 `dsh.bundle` 的 `package.json`（未做到一插件一仓库、包在根目录） | 422→终态 | `plugin.error.gitPackageNotFound` | **克隆后校验失败**；固定文案「仓库根目录必须包含含 DSH 插件声明的 package.json（一个插件一个仓库），请按规范调整仓库结构后重新提交」。调试细节（缺文件还是缺声明）只写服务端日志，不进用户可见文案；刻意不拆成两种提示，避免诱导用户改路径绕过一插件一仓库规范。|
| 内部私服/本站已有同名同版本（`package_name@version`），**Git 来源**——常见场景是同一仓库默认分支未推进、包内 `package.json` 版本号未改；**commit 变化但版本号未改也命中，commit 未变但版本号改了则放行** | 409 | `plugin.error.versionExists` | 同步；页面固定文案「该版本已存在，请先在仓库中升级版本号再提交」。`commit + branch + resolved_at` 只是版本指纹（溯源用），不是唯一性条件 |
| 内部私服/本站已有同名同版本（`package_name@version`），**npm 来源**——latest 未变，服务端解析出的冻结版本撞库 | 409 | `plugin.error.versionExists` | 同步；固定文案「该版本已存在，请先在上游 npm 发布新版本后重新提交」，**不得沿用 Git 的"请先在仓库中升级版本号再提交"**——两句按来源分流，不合并；唯一性同样只看 `name@version`，与解析时刻无关 |
| zip 超限 / 含非法路径 | 413 / 422 | `plugin.error.archiveTooLarge` / `plugin.error.archiveUnsafe` | 同步 |
| 非空间成员 | 403 | `plugin.error.noPermission` | 同步 |
| 上传来源不可用（私服不允许匿名读） | 503 | `plugin.error.uploadSourceDisabled` | 同步（能力开关） |
| 该空间未开放提交（团队空间在 P1 就绪前 / 全局止血关闭） | 403 | `plugin.error.uploadSourceDisabled`（`reasonCode` 见 §7.2） | 同步（能力开关；前端正常路径是**置灰**，403 只拦绕过 UI 的直连） |
| 命中红线 | 202→终态 | `plugin.error.scanRedLine` | 异步（`SCAN_RED`） |
| 扫描失败且重试耗尽 | 202→终态 | `plugin.error.scanFailed` | 异步（`SCAN_FAILED`） |
| 报告不可解析 / `schemaVersion` 未知 | 202→终态 | `plugin.error.scanInvalidReport` | 异步 |
| 扫描服务不可用 / 排队超时（未耗尽重试） | 202（内部重试） | — | 异步（`RECEIVED` + backoff） |
| 代发私服失败（不可重试） | 202→终态 | `plugin.error.registryPublishFailed` | 异步（`PUBLISH_FAILED`） |
| 异步复核包名/版本与声明不符 | 202→终态 | `plugin.error.packageNameMismatch` | 异步（`VALIDATION_FAILED`） |
| 重扫超限 | 429 | `plugin.error.scanRateLimited` | 同步 |
| 提交频率超限 | 429 | `plugin.error.submitRateLimited` | 同步 |
| 举报频率超限 | 429 | `plugin.error.reportRateLimited` | 同步 |
| 举报理由为 `other` 但未填 `details` | 422 | `plugin.error.reportDetailsRequired` | 同步 |
| 审核通过后代发失败（不可重试） | 202→版本停 `APPROVED` | `plugin.error.registryPublishFailed` | 异步（`PUBLISH_FAILED`，提交侧） |
| 代发重试的前置状态不符 | 409 | `plugin.error.publishNotRetryable` | 同步（`.../publish {RETRY}`） |
| 审核决定时版本已不在待审态 | 409 | `plugin.error.versionNotReviewable` | 同步 |
| 审核决定：非该空间空间管理员（含平台管理员 `SKILL_ADMIN` / `SUPER_ADMIN`，`PLUGIN_VERSION`） | 403 | `plugin.error.noPermission` | 同步（与包内文件浏览同一行依据，§7.6.6）。**自审不在此列**：作者是空间管理员时正常通过，审计记 `selfReview: true`（§4.6 硬规则 4） |
| 预览接口：提交不存在 | 404 | `plugin.error.stagedSubmissionNotFound` | 同步（包内文件浏览） |
| 预览接口：`path` 不合法（`..` 段 / 绝对路径 / 盘符 / NUL / 反斜杠 / 编码绕过） | 400 | `plugin.error.stagedFilePathInvalid` | 同步（包内文件浏览） |
| 预览接口：路径不在本次提交的包内（清单白名单未命中） | 404 | `plugin.error.stagedFileNotFound` | 同步（包内文件浏览） |
| 预览接口：单文件超出内联预览上限（`maxTextBytes` / `maxEntryBytes`） | 413 | `plugin.error.stagedFileTooLarge` | 同步（包内文件浏览；文案引导下载） |
| 预览接口：整包超出浏览上限（文件数 / 总解压量 / 压缩比 / 请求预算） | 422 | `plugin.error.stagedArchiveLimitExceeded` | 同步（**拒绝整次浏览，不截断**，§7.6） |
| 预览接口：暂存包已按保留策略清理（对象已删或 `staged_tarball_key` 已置空） | 410 | `plugin.error.stagedPackagePurged` | 同步（§6.4；**不回退**私服/上游） |
| 预览接口：取到的字节与记录 `sha512` 不一致 | 409 | `plugin.error.stagedPackageMismatch` | 同步（一致性红线，同时 P1 告警，§14.1 A13） |
| 预览接口：浏览能力被运维关闭 / 审计写入失败（fail closed） | 503 | `plugin.error.fileBrowseDisabled` | 同步（§14.1 A17） |

**语义变化说明**：PRD 的早期版本把「命中红线/扫描超时/代发失败」定为请求内 422/504/502；现版本 PRD 不定义错误码，全部由本表定义。异步化后这些错误不再出现在 POST 响应里，改为 `GET /submissions/{id}` 的 `status + errorCode`。前端必须按状态机渲染，而不是按 HTTP 码分支。这是本设计对 PRD 的明确偏离，见 §13。

**子目录口径已删除（勿恢复）**：`gitAmbiguousPackage`（自动探测多候选）与 `gitPathInvalid`（子目录非法）随本期"只填仓库地址、一插件一仓库"裁决一并删除，不再是本表可实现错误码。Git 来源的校验错误只剩两类：请求夹带 path/ref/tag/commit/凭据 → 400 `gitFieldNotSupported`；克隆后根目录缺 DSH 插件包 → 终态 `gitPackageNotFound`。

### 7.4.1 结构化错误码全集（同步校验 51 条 + 异步扫描 3 条）

**命名规则**：`PLUGIN_{族}_{原因}`，族 ∈ {`VALIDATION`, `CONFLICT`, `GIT`, `NPM`, `ARCHIVE`, `FILE`, `AUTH`, `RATE`}。`errorCode` 与 i18n key **一一对应但解耦**：文案可随时改写、四语言可各自不同，`errorCode` 发布后**不得改名**（要改就新增一个并在 §13 记录）。每条都必带 `details`，其中 `guidance` 是**可自修动作枚举**，前端据它决定渲染哪个按钮，而不是读文案：

`BUMP_VERSION`（改版本号）/ `RENAME_PACKAGE`（换包名）/ `CONTACT_OWNER`（联系原空间）/ `FIX_PACKAGE_JSON` / `FIX_ARCHIVE`（按提示重打包）/ `RETRY_LATER` / `CONTACT_ADMIN` / `NONE`（只展示）。

**A. 包与元数据（`PLUGIN_VALIDATION_*`）**

| errorCode | HTTP | i18n key | `details` 关键字段 | 触发 |
|---|---|---|---|---|
| `PLUGIN_VALIDATION_PACKAGE_JSON_MISSING` | 422 | `plugin.error.invalidPackage` | `{sourceType}` · `FIX_PACKAGE_JSON` | 包内无 `package.json` |
| `PLUGIN_VALIDATION_PACKAGE_JSON_INVALID` | 422 | `plugin.error.invalidPackage` | `{field, reason}` · `FIX_PACKAGE_JSON` | JSON 不可解析 / 缺 `name` 或 `version` |
| `PLUGIN_VALIDATION_BUNDLE_DECL_MISSING` | 422 | `plugin.error.invalidPackage` | `{packageName, version}` · `FIX_PACKAGE_JSON` | 缺 `dsh.bundle` 声明 |
| `PLUGIN_VALIDATION_ICON_INVALID` | 422 | `plugin.error.iconInvalid` | `{reason, actualType, limitBytes}` · `FIX_PACKAGE_JSON` | 图标类型/大小/SVG 含脚本或外链 |
| `PLUGIN_VALIDATION_NAME_MISMATCH` | 202→终态 | `plugin.error.packageNameMismatch` | `{declared, expected}` · `NONE` | 异步复核：包内 name ≠ 提交声明 |
| `PLUGIN_VALIDATION_VERSION_MISMATCH` | 202→终态 | `plugin.error.packageNameMismatch` | `{declared, frozen}` · `NONE` | 异步复核：包内 version ≠ 提交时冻结版本 |
| `PLUGIN_VALIDATION_REPORT_DETAILS_REQUIRED` | 422 | `plugin.error.reportDetailsRequired` | `{reasonCode}` · `NONE` | 举报理由 `other` 未填 `details` |
| `PLUGIN_VALIDATION_VISIBILITY_INVALID` | 400 | `plugin.error.visibilityInvalid` | `{field, allowedValues[]}` · `NONE` | 可见范围缺失或不在 `{PUBLIC, NAMESPACE_ONLY, PRIVATE}` 内（四种来源同一条）。**`guidance` 只能是 `NONE`**：这是表单必选字段，服务端不提供"猜一个"的自修动作 |

**B. 唯一性与状态冲突（`PLUGIN_CONFLICT_*`，全部 409）**

| errorCode | HTTP | i18n key | `details` 关键字段 | 触发 |
|---|---|---|---|---|
| `PLUGIN_CONFLICT_VERSION_EXISTS` | 409 | `plugin.error.versionExists` | `{packageName, version, sourceType, existingStatus, guidance}` | 同 `name@version` 已存在。**文案按 `sourceType` 分派**：`git` → 「请先在仓库中升级版本号再提交」·`BUMP_VERSION`；`npm` → 「请先在上游 npm 发布新版本后重新提交」·`BUMP_VERSION`。**两句话由 `details.sourceType` 决定，绝不由文案反推来源** |
| `PLUGIN_CONFLICT_PACKAGE_NAME_TAKEN` | 409 | `plugin.error.packageNameTaken` | `{packageName, ownerNamespace}` · `CONTACT_OWNER` | 已被其他空间收录 |
| `PLUGIN_CONFLICT_PACKAGE_NAME_RESERVED` | 409 | `plugin.error.packageNameReserved` | `{packageName}` · `CONTACT_ADMIN` | 已软删但名额未释放 |
| `PLUGIN_CONFLICT_PACKAGE_NAME_BLOCKED` | 409 | `plugin.error.packageNameBlocked` | `{packageName}` · `CONTACT_ADMIN` | 已在黑名单 |
| `PLUGIN_CONFLICT_SUBMISSION_IN_FLIGHT` | 409 | `plugin.error.submissionInFlight` | `{packageName, version, submissionId, status}` · `NONE` | 同 `name@version` 有在途提交 |
| `PLUGIN_CONFLICT_VERSION_NOT_YANKABLE` | 409 | `plugin.error.versionNotYankable` | `{version, status, allowedFrom[]}` · `NONE` | 撤版/恢复前置状态不满足 |
| `PLUGIN_CONFLICT_VERSION_NOT_REVIEWABLE` | 409 | `plugin.error.versionNotReviewable` | `{version, status}` · `NONE` | 审核决定时版本已不在待审态 |
| `PLUGIN_CONFLICT_PUBLISH_NOT_RETRYABLE` | 409 | `plugin.error.publishNotRetryable` | `{version, status, sourceType}` · `NONE` | 重试发布前置不符。**`sourceType ∈ {npm, git}` 恒不可重试**（没有这一步），前端据此直接不渲染重试按钮 |
| `PLUGIN_CONFLICT_PUBLISH_ARTIFACT_MISSING` | 409 | `plugin.error.publishArtifactMissing` | `{version}` · `CONTACT_ADMIN` | 恢复 LISTED 时私服已取不到制品 |
| `PLUGIN_CONFLICT_NOT_REMOVED` | 409 | `plugin.error.notRemoved` | `{status}` · `NONE` | 释放包名时插件不是 `REMOVED` |
| `PLUGIN_CONFLICT_STAGED_PACKAGE_MISMATCH` | 409 | `plugin.error.stagedPackageMismatch` | `{submissionId, expectedSha512Prefix, actualSha512Prefix}` · `CONTACT_ADMIN` | 暂存包字节与记录 `sha512` 不一致 |
| `PLUGIN_CONFLICT_VISIBILITY_WIDEN_PENDING` | 409 | `plugin.error.visibilityWidenPending` | `{current, requested, allowedDirections[]}` · `NONE` | 改可见范围请求了变宽方向。`details.allowedDirections` 明确列出 v1 允许的三条缩窄路径，前端据此把选择器里不可选的方向直接置灰，**不靠读文案** |

**C. Git 预检与克隆后校验（`PLUGIN_GIT_*`）**

| errorCode | HTTP | i18n key | `details` 关键字段 | 触发 |
|---|---|---|---|---|
| `PLUGIN_GIT_FIELD_NOT_SUPPORTED` | 400 | `plugin.error.gitFieldNotSupported` | `{forbiddenFields[]}` · `NONE` | 夹带 `path`/`ref`/`tag`/`commit`/凭据 |
| `PLUGIN_GIT_HOST_NOT_ALLOWED` | 422 | `plugin.error.gitHostNotAllowed` | `{host, allowedHosts[]}` · `CONTACT_ADMIN` | host 未列 / path 前缀不符 / 解析到内网敏感段 |
| `PLUGIN_GIT_UNREACHABLE` | 422 | `plugin.error.gitUnreachable` | `{host, phase}` · `RETRY_LATER` | 地址不可解析 / SSH 握手失败 |
| `PLUGIN_GIT_AUTH_FAILED` | 422 | `plugin.error.gitAuthFailed` | `{host}` · `CONTACT_ADMIN` | 无读权限 / host key 校验失败 |
| `PLUGIN_GIT_REPO_NOT_FOUND` | 422 | `plugin.error.gitRepoNotFound` | `{repoPath}` · `NONE` | 仓库不存在 |
| `PLUGIN_GIT_DEFAULT_BRANCH_MISSING` | 422 | `plugin.error.gitDefaultBranchMissing` | `{repoPath}` · `NONE` | 默认分支不存在 |
| `PLUGIN_GIT_COMMIT_UNRESOLVED` | 422 | `plugin.error.gitUnresolved` | `{expectedCommit, resolvedCommit}` · `RETRY_LATER` | 浅克隆解析出的 commit ≠ 目标 commit（§14.1 A7） |
| `PLUGIN_GIT_PACKAGE_NOT_FOUND` | 202→终态 | `plugin.error.gitPackageNotFound` | `{commit, expectedPath}` · `NONE` | 克隆后根目录无含 `dsh.bundle` 的 `package.json` |

**D. npm 预检与发布（`PLUGIN_NPM_*`）**

| errorCode | HTTP | i18n key | `details` 关键字段 | 触发 |
|---|---|---|---|---|
| `PLUGIN_NPM_VERSION_FIELD_NOT_SUPPORTED` | 400 | `plugin.error.versionFieldNotSupported` | `{forbiddenFields[]}` · `NONE` | 夹带 `version`/`ref`/`tag`/凭据 |
| `PLUGIN_NPM_PACKAGE_NOT_FOUND` | 422 | `plugin.error.npmPackageNotFound` | `{packageName}` · `NONE` | 包名在上游不存在 |
| `PLUGIN_NPM_UNAUTHORIZED` | 422 | `plugin.error.npmUnauthorized` | `{packageName, status}` · `CONTACT_ADMIN` | 上游 401/403 |
| `PLUGIN_NPM_REGISTRY_UNREACHABLE` | 503 | `plugin.error.npmRegistryUnreachable` | `{registryHost, retryable: true}` · `RETRY_LATER` | registry 不可达 / 超时 |
| `PLUGIN_NPM_PRERELEASE_ONLY` | 422 | `plugin.error.npmPrereleaseOnly` | `{packageName, latestPrerelease}` · `NONE` | 只有预发布版本 |
| `PLUGIN_NPM_REGISTRY_PUBLISH_FAILED` | 202→终态 | `plugin.error.registryPublishFailed` | `{status, attempt, maxAttempts, retryable}` · `RETRY_LATER` | 代发私服失败（**仅 zip/folder**） |

**E. 包体与预览边界（`PLUGIN_ARCHIVE_*` / `PLUGIN_FILE_*`）**

| errorCode | HTTP | i18n key | `details` 关键字段 | 触发 |
|---|---|---|---|---|
| `PLUGIN_ARCHIVE_TOO_LARGE` | 413 | `plugin.error.archiveTooLarge` | `{actualBytes, limitBytes}` · `FIX_ARCHIVE` | 上传包超限 |
| `PLUGIN_ARCHIVE_UNSAFE` | 422 | `plugin.error.archiveUnsafe` | `{reason, entryPath}` · `FIX_ARCHIVE` | `reason ∈ {PATH_TRAVERSAL, SYMLINK, ABSOLUTE_PATH, DOS_NAME}` |
| `PLUGIN_ARCHIVE_LIMIT_EXCEEDED` | 422 | `plugin.error.stagedArchiveLimitExceeded` | `{limit, limitValue, actualValue}` · `NONE` | 浏览侧整包超限（**拒绝整次浏览，不截断**） |
| `PLUGIN_FILE_SUBMISSION_NOT_FOUND` | 404 | `plugin.error.stagedSubmissionNotFound` | `{submissionId}` · `NONE` | 提交不存在 |
| `PLUGIN_FILE_PATH_INVALID` | 400 | `plugin.error.stagedFilePathInvalid` | `{pathHash, rule}` · `NONE` | `..`/绝对路径/盘符/NUL/反斜杠/编码绕过。**`details` 只给 `pathHash` 不给原串**（避免把攻击载荷回显到日志与页面） |
| `PLUGIN_FILE_NOT_FOUND` | 404 | `plugin.error.stagedFileNotFound` | `{pathHash}` · `NONE` | 路径不在清单白名单内 |
| `PLUGIN_FILE_TOO_LARGE` | 413 | `plugin.error.stagedFileTooLarge` | `{size, limitBytes, downloadAvailable: true}` · `NONE` | 单文件超内联预览上限，前端据此渲染「下载查看」 |
| `PLUGIN_FILE_PACKAGE_PURGED` | 410 | `plugin.error.stagedPackagePurged` | `{purgedAt, retentionDays}` · `NONE` | 暂存包已按保留策略清理（**不回退**私服/上游） |
| `PLUGIN_FILE_BROWSE_DISABLED` | 503 | `plugin.error.fileBrowseDisabled` | `{reason}` · `CONTACT_ADMIN` | `reason ∈ {DISABLED_BY_OPS, AUDIT_UNAVAILABLE}` |

**F. 权限、能力与限流（`PLUGIN_AUTH_*` / `PLUGIN_RATE_*`）**

| errorCode | HTTP | i18n key | `details` 关键字段 | 触发 |
|---|---|---|---|---|
| `PLUGIN_AUTH_UNAUTHENTICATED` | 401 | `plugin.error.unauthenticated` | `{}` · `NONE` | 未登录 |
| `PLUGIN_AUTH_NO_PERMISSION` | 403 | `plugin.error.noPermission` | `{requiredRole, namespaceId, subjectType}` · `NONE` | 非空间管理员 / 非成员 / 平台管理员审批 `PLUGIN_VERSION`（§7.6.6） |
| `PLUGIN_AUTH_UPLOAD_SOURCE_DISABLED` | 503 | `plugin.error.uploadSourceDisabled` | `{reasonCode}` · `CONTACT_ADMIN` | 私服匿名读自检未通过，zip/folder 不可用 |
| `PLUGIN_AUTH_PUBLISH_DISABLED` | 403 | `plugin.error.uploadSourceDisabled` | `{reasonCode: publishDisabled}` · `CONTACT_ADMIN` | 全局止血开关关闭时绕过 UI 直连 |
| `PLUGIN_AUTH_REVIEW_NOT_READY` | 403 | `plugin.error.uploadSourceDisabled` | `{reasonCode: reviewNotReady}` · `NONE` | 团队空间在 P1 就绪前绕过 UI 直连 |
| `PLUGIN_RATE_SUBMIT_LIMITED` | 429 | `plugin.error.submitRateLimited` | `{limit, windowSeconds, retryAfterSeconds}` · `RETRY_LATER` | 提交频率超限 |
| `PLUGIN_RATE_REPORT_LIMITED` | 429 | `plugin.error.reportRateLimited` | `{limit, windowSeconds, retryAfterSeconds}` · `RETRY_LATER` | 举报频率超限 |
| `PLUGIN_RATE_SCAN_LIMITED` | 429 | `plugin.error.scanRateLimited` | `{limit, windowSeconds, retryAfterSeconds}` · `RETRY_LATER` | 重扫频率超限 |

**异步扫描终态（`GET /submissions/{id}` 的 `errorCode`，与上表共用同一命名空间）**

| errorCode | i18n key | `details` 关键字段 | 对应 `status` |
|---|---|---|---|
| `PLUGIN_SCAN_RED_LINE` | `plugin.error.scanRedLine` | `{codes[], reportId, fingerprint}` | `SCAN_RED` |
| `PLUGIN_SCAN_FAILED` | `plugin.error.scanFailed` | `{attempt, maxAttempts, retryable}` | `SCAN_FAILED` |
| `PLUGIN_SCAN_INVALID_REPORT` | `plugin.error.scanInvalidReport` | `{schemaVersion, toolVersion}` | `SCAN_FAILED` |

> 异步侧的 `PUBLISH_FAILED` / `VALIDATION_FAILED` 分别复用 `PLUGIN_NPM_REGISTRY_PUBLISH_FAILED` 与 A 族的 `PLUGIN_VALIDATION_*_MISMATCH`，**不为异步再造一套码**——同一个失败原因在同步与异步两个表面必须叫同一个名字，否则前端要维护两张表。

**三条落地硬规则**：

1. **控制器不许手写错误体**：统一抛 `PluginApiException(errorCode, i18nKey, details)`，由 `@ExceptionHandler` 渲染 §7.3 的 `PluginError`。禁止 `ResponseEntity.status(409).body(Map.of("error", "..."))`——手写点一多，`errorCode` 必然有遗漏项，而遗漏的那条正是前端要分支的那条。
2. **前端只有一张映射表**：`web/src/features/plugin/plugin-error-codes.ts` 里 `errorCode → { i18nKey, guidance → 动作组件 }`。**未知 `errorCode` 降级为通用文案 + 记一条前端日志，不抛错、不白屏**——新码上线时旧前端不能崩。
3. **静态门禁**：`web/src/features/plugin/**` 下不得出现 `message.includes(`、`serverMessage`、`.startsWith(` 作用在错误文案上（§16 S22 有 grep 断言，并把 `publish-error-utils.ts` 的 `VERSION_EXISTS_MARKERS` 列为反面案例写进注释）。

