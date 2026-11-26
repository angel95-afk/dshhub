# §7.3　关键 schema

> 所属：后端　｜　覆盖章节：§7.3
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

### 7.3 关键 schema

```yaml
components:
  schemas:
    NpmSubmissionRequest:
      type: object
      required: [namespace, visibility, sourceType, category, packageName]
      description: >-
        npm 来源**只有一个输入：包名（必填，可 scoped，形如 name / @scope/name）**（`PRD §6.2/§6.3` v2.13）。
        **没有 version 字段**——表单不得出现版本输入框，用户不指定、不覆盖版本；分支 / tag / commit / 凭据同样不填。
        版本由服务端调 `resolveLatest(packageName)` 取 registry latest 对应的**最新非预发布版本**（跳 prerelease），
        **在提交那一刻冻结**进提交快照，扫描 / 审核 / 代发 / 上架全程复用，后续阶段绝不重新解析 latest。
        请求若以任何途径夹带 `version`（或 `ref` / `tag` / 凭据）字段：显式 400 + `plugin.error.versionFieldNotSupported`，
        固定文案「npm 来源只填包名，版本由服务端自动取最新非预发布版本，不支持指定版本」（前端绕过时后端必须挡住）。
        重复提交且 latest 未变、该 `name@version` 已存在：409 `versionExists`，npm 专属文案
        「该版本已存在，请先在上游 npm 发布新版本后重新提交」。
      properties:
        namespace: { type: string }
        visibility:
          $ref: '#/components/schemas/PluginVisibility'
        sourceType: { type: string, const: npm }
        category: { $ref: '#/components/schemas/PluginCategory' }
        packageName: { type: string, maxLength: 214, pattern: '^(@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*$', description: 'npm 包名，可 scoped；不含版本' }
    GitSubmissionRequest:
      type: object
      required: [namespace, visibility, sourceType, category, url]
      description: >-
        Git 来源只有一个输入：仓库地址（必填）；**一插件一仓库，插件包必须在仓库根目录**。
        **没有 ref / tag / commit / path / 凭据字段**（`PRD §6.3`，表单里出现 token/私钥是红线）。
        commit / branch / resolved_at 由服务端解析后落库并展示。
        版本唯一性判定是 **`package_name@version`**（来自包内 `package.json`），不是 commit；
        `commit + branch + resolved_at` 只是溯源指纹，改了版本号但 commit 没变应放行，commit 变了但版本号没改仍命中 409。
        请求若夹带 `path` 等本期不支持字段：显式 400 + 固定文案「本期不支持子目录，请将插件包置于仓库根目录」。
      properties:
        namespace: { type: string }
        visibility:
          $ref: '#/components/schemas/PluginVisibility'
        sourceType: { type: string, const: git }
        category: { $ref: '#/components/schemas/PluginCategory' }
        url: { type: string, maxLength: 1000, description: 'https://host/group/repo(.git) 或 git@host:group/repo.git' }
    PluginVisibility:
      type: string
      enum: [PUBLIC, NAMESPACE_ONLY, PRIVATE]
      description: >-
        可见范围（`PRD §6.3` v2.16，取值与中文标签沿用技能侧、不新造词）：`PUBLIC` 公开 / `NAMESPACE_ONLY` 仅命名空间 / `PRIVATE` 私有。
        **必选、无留空、无默认值**（不传即 400，不由服务端兜底成 `PUBLIC`）；zip / folder 的 multipart 提交同样必带。
        与"空间是否公开"是两个正交维度，两者共同决定可见性与状态文案（`PRD §8.4` 四行判定表）——所以**只下发可见范围是不够的**，DTO 必须同时给出 `namespaceIsPublic`（见下）。
        非法取值 → 400 `PLUGIN_VALIDATION_VISIBILITY_INVALID`（§7.4.1 A 族），`details` 带 `{field, allowedValues[]}`。
    PluginCategory:
      type: string
      enum: [agent, ui, memory, communication, devtools, search-knowledge, data, security, other]
    PluginScanVerdict:
      type: string
      nullable: true
      enum: [clear, review, red]
      description: 四态中 error 不落库为 verdict——扫描失败表现为 report.status=FAILED
    PluginSubmissionStatus:
      type: string
      enum: [RECEIVED, MATERIALIZING, SCANNING, SCAN_RED, REVIEW_PENDING, PUBLISHING,
             LISTED, SCAN_FAILED, REVIEW_REJECTED, PUBLISH_FAILED, VALIDATION_FAILED, CANCELED]
    PluginStatus:
      type: string
      enum: [DRAFT, PENDING, LISTED, HIDDEN, BLOCKED, REMOVED]
    PluginVersionStatus:
      type: string
      enum: [SCAN_PASSED, REVIEW_PENDING, APPROVED, PUBLISHING, LISTED, REJECTED, YANKED]
      description: >-
        SCAN_PASSED=扫描通过（待审或免审待上架）；REVIEW_PENDING=等待审核（制品只在暂存区，私服无此版本）；
        APPROVED=审核通过待上架（免审空间不出现）；PUBLISHING=上架中（**仅 submission_source ∈ {zip, folder} 可达**，
        npm/git 从 SCAN_PASSED/REVIEW_PENDING 直接进 LISTED，永不出现此态）；LISTED=已上架；REJECTED=审核拒绝终态；YANKED=人工撤下。
        对外可见性只由 LISTED 决定，其余状态一律按未上架处理（详情页与安装文档 404）。
    StagedFileEntry:
      type: object
      required: [path, size, binary, sensitive]
      properties:
        path: { type: string, description: '包内相对路径（POSIX，无前导 /、无盘符）；与 tar 条目名逐字节一致，后续读取用它做白名单比对' }
        size: { type: integer, format: int64, description: 解压后字节数（不是压缩后） }
        binary: { type: boolean, description: '按 §7.6 判定（扩展名/MIME + 前 8 KiB 采样 + UTF-8 可解码比例）；true 时不提供文本预览' }
        sensitive: { type: boolean, description: '命中敏感文件名规则（.env/.npmrc/*.pem/*.key/id_* 等，可能含凭据）。只做标记、不隐藏——审核的目的就是看它' }
    StagedFileListing:
      type: object
      required: [submissionId, sha512, indexedAt, entryCount, totalBytes, limits, entries]
      description: >-
        **扁平 manifest**：`entries` 是包内全部文件的平铺数组，每条只有 POSIX 相对路径 + 三个判定位，
        **不含目录节点、不含 children、不含深度字段**。理由有三条：① tar 本来就是一条扁平条目流，
        在后端造出目录树等于把"一次顺序遍历"变成"一次遍历 + 一棵要缓存的树"，且空目录/隐式目录的判定要另定规则；
        ② 目录树是纯展示结构，前端已有 `features/skill/file-tree-builder.ts`（技能审核页在用），复用它一行适配即可；
        ③ 一旦后端开始按目录返回，就会出现"展开一个目录 = 一次请求"的 N+1，以及"清单与目录视图不一致"的一致性窗口。
        **因此不新增 `.../files?dir=` 这类逐目录接口**（§16 S21 有断言）。
      properties:
        submissionId: { type: integer, format: int64 }
        sha512: { type: string, description: '暂存包指纹，与 plugin_submission.tarball_sha512 同源（LISTED 后回落 plugin_version.sha512）；页面必须原样展示，供与扫描报告 tarball_sha512 对照' }
        indexedAt: { type: string, format: date-time, description: 本次清单的生成时间（缓存命中时为首次生成时间） }
        entryCount: { type: integer }
        totalBytes: { type: integer, format: int64, description: 全部条目解压后总字节 }
        limits:
          type: object
          description: 服务端**实际生效**的上限（来自 §11.1 配置）。前端据此决定"内联预览 / 引导下载 / 提示超限"，不得硬编码
          properties:
            maxEntries: { type: integer }
            maxUncompressedBytes: { type: integer, format: int64 }
            maxEntryBytes: { type: integer, format: int64 }
            maxTextBytes: { type: integer, format: int64 }
        entries:
          type: array
          description: 顺序与 tar 条目顺序一致（不排序、不分组）；目录树由前端构建
          items: { $ref: '#/components/schemas/StagedFileEntry' }
    StagedFileText:
      type: string
      description: >-
        `text/plain; charset=utf-8` 的纯文本内容。只做 UTF-8 解码，不做任何转义/包装（转义由前端文本节点承担）。
        超过 `maxTextBytes` 不截断、直接 413 引导下载——截断会让审核人以为"文件就到这里"。
    StagedFileBinaryMeta:
      type: object
      required: [binary, path, size, detectedType]
      description: 二进制文件的元信息（**不含字节**）。要字节请走 `.../files/download`
      properties:
        binary: { type: boolean, const: true }
        path: { type: string }
        size: { type: integer, format: int64 }
        detectedType: { type: string, example: image/png, description: 判定依据命中的类型（扩展名或魔数），仅用于展示 }
        magicHex: { type: string, example: '89504e470d0a1a0a', description: 前 8 字节十六进制，给人判断用，不是内容 }
    PluginReportReason:
      type: string
      enum: [misleading, malicious, license, spam, other]
    DshIndexDocument:
      type: object
      required: [format, title, plugins]
      properties:
        format: { type: integer, const: 1 }
        title: { $ref: '#/components/schemas/LocalizedText' }
        description: { $ref: '#/components/schemas/LocalizedText' }
        metadata:
          type: object
          properties:
            categories:
              type: array
              items:
                type: object
                required: [id, title]
                properties:
                  id: { $ref: '#/components/schemas/PluginCategory' }
                  title: { $ref: '#/components/schemas/LocalizedText' }
            labels: { type: array, items: { type: string }, description: 本期留空 }
        plugins:
          type: array
          items: { $ref: '#/components/schemas/DshIndexPlugin' }
    LocalizedText:
      type: object
      required: [en]
      properties:
        zh: { type: string }
        en: { type: string }
        ko: { type: string }
        ru: { type: string }
    DshIndexPlugin:
      type: object
      required: [name, title, versions]
      properties:
        name: { type: string, description: npm 包名 }
        title: { $ref: '#/components/schemas/LocalizedText' }
        description: { $ref: '#/components/schemas/LocalizedText' }
        icon: { type: string, example: icons/3f2a9c1b7d4e5f60.svg }
        author: { type: string }
        homepage: { type: string, format: uri }
        repository:
          type: object
          required: [url]
          properties:
            url: { type: string, format: uri }
        metadata:
          type: object
          required: [category]
          properties:
            category: { $ref: '#/components/schemas/PluginCategory' }
        versions:
          type: array
          minItems: 1
          items: { $ref: '#/components/schemas/DshIndexVersion' }
    DshIndexVersion:
      type: object
      required: [version, publishTimestamp, source]
      properties:
        version: { type: string, description: 精确 SemVer }
        publishTimestamp: { type: integer, description: Unix 秒（上架时间） }
        engines:
          type: object
          properties:
            dsh: { type: string }
        source:
          description: >-
            **唯一由 `resolveDistributionSource(pluginVersion)` 判定并填充**（§8.1.1），与用户当初选了哪种上传方式**无关**。
            镜像里已有该包的该版本 → 写 `npm`；镜像是明确 404 → 退 `git`；超时/5xx/网络错误 → **构建失败**（不降级）。
            `plugin_version.submission_source`（用户上传方式）**永不进索引**，也不出现在详情页。
          oneOf:
            - type: object
              required: [type, registry]
              properties:
                type: { const: npm }
                registry: { type: string, format: uri }
            - type: object
              required: [type, url, commit]
              properties:
                type: { const: git }
                url: { type: string, format: uri }
                commit: { type: string, pattern: '^[0-9a-f]{40}$' }
  responses:
    PluginError:
      description: >-
        本地化领域异常统一体（不含堆栈与凭据）。三段各司其职，**前端只许按 `errorCode` 分支**
        （§7.2.2）：`error` = i18n key（文案可改、四语言各一份）、`errorCode` = 稳定契约（发布后改名即破坏兼容）、
        `details` = 机器可读上下文（前端据此决定渲染哪个可自修动作）。
      content:
        application/json:
          schema:
            type: object
            required: [error, errorCode]
            properties:
              error: { type: string, example: plugin.error.scanRedLine, description: i18n key，按 locale 渲染 }
              errorCode: { type: string, example: PLUGIN_CONFLICT_VERSION_EXISTS, pattern: '^PLUGIN_[A-Z0-9_]+$', description: '稳定错误码，见 §7.4.1 全集；**不得**由 error 反推' }
              message: { type: string, nullable: true, description: '服务端渲染的兜底文案，仅供展示与日志；**禁止**作为分支依据（§16 S22）' }
              details:
                type: object
                additionalProperties: true
                description: 按族定义的结构化上下文（§7.4.1）；缺省为空对象而不是 null
    RateLimited:
      description: 限流
      headers:
        Retry-After: { schema: { type: integer } }
    MarkdownDoc:
      description: 纯 Markdown
      content:
        text/markdown:
          schema: { type: string }
```

**可见性字段：`PluginDetail` 与 `PluginPage.items[]` 必须下发，且必须成对下发**（`PRD §8.4` v2.16）

```yaml
# PluginDetail 与 PluginPage.items[] 共同新增的两个字段（其余字段不变，见 §7.2.1 与 §5.1）
visibility:
  $ref: '#/components/schemas/PluginVisibility'
namespaceIsPublic:
  type: boolean
  description: >-
    所属命名空间是否为公开空间。**与 `visibility` 成对使用**：状态文案与可见性判定由这两个字段的组合决定
    （4 行判定表见 `PRD §8.4`），前端不得按空间名、路由或其他信号推断。非公开空间恒 `false`。
```

为什么必须成对：`PUBLIC` 落在私有空间里同样是"上架但不进索引"，只给 `visibility` 会让前端把 `PUBLIC` 一律渲染成「已上架」；只给 `namespaceIsPublic` 则会把 `NAMESPACE_ONLY` 渲染成「已上架」。**两个维度少一个就一定渲染错**，所以是同一组字段、同一条校验（§16 S24）。

三档状态文案与两条稳定说明句是**服务端下发的文案位**，不由前端拼：`NAMESPACE_ONLY` →「仅命名空间」、`PRIVATE` →「私有」、私有空间 →「已通过（仅空间内可见）」；说明句键见 §10.5（`plugin.visibility.notice.*`）。**未收录原因用 `PRD §3.4` 的既有字面量**「未收录：可见范围非公开」，**不新增第二个索引状态枚举**（§5.4）。

卡片（`PluginPage.items[]`）同样需要这两个字段：市场页只列 `PUBLIC` + 公开空间的插件，但「我的插件」与命名空间插件 tab 复用同一个 `PluginCard` 的数据形状，缺字段就得在前端特判；**统一带上，列表文案由后端字段决定**。

