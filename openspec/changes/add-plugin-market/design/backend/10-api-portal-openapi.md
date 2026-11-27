# §7.2　需登录接口的 OpenAPI paths（3 块 YAML，不要继续拆）

> 所属：后端　｜　覆盖章节：§7.2
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

### 7.2 需登录

```yaml
paths:
  /api/v1/plugins/submit-capabilities:
    get:
      security: [{ sessionCookie: [] }]
      description: >-
        提交入口可用性的**唯一判定来源**（`PRD §6.1` 通用规则：入口是否可用由"该空间当前是否开放提交能力"决定，
        不可用时置灰并说明原因，不隐藏入口）。三处入口（`/plugins` 页头、`/dashboard/plugins` 页头、
        命名空间插件 tab 页头）与提交页的空间选择器全部读这一个接口，**前端不得按分期常量硬编码**
        （不写 `if (space.kind === 'TEAM' && phase === 'P0')`），否则 P1 开放团队空间要改一遍前端。
      responses:
        '200':
          content:
            application/json:
              schema:
                type: object
                required: [available, namespaces]
                properties:
                  available:
                    type: boolean
                    description: 当前用户**至少有一个**空间可提交时为 true；决定页头「提交插件」按钮可用态
                  reasonCode:
                    type: string
                    nullable: true
                    description: available=false 时的原因码；available=true 时为 null。前端按 `plugin.submit.capability.disabled.{reasonCode}` 取文案（键清单见 §10.5），不认识的值降级为通用文案、不报错
                  namespaces:
                    type: array
                    description: 当前用户可见空间（个人空间 + 已加入空间），每个空间独立给可用性
                    items:
                      type: object
                      required: [namespace, kind, available]
                      properties:
                        namespace: { type: string }
                        kind: { type: string, enum: [PERSONAL, TEAM] }
                        available: { type: boolean }
                        reasonCode: { type: string, nullable: true, example: reviewRevokeNotReady }
        '401': { $ref: '#/components/responses/PluginError' }

  /api/v1/plugins/git/preflight:
    post:
      security: [{ sessionCookie: [] }]
      description: >-
        Git 来源预检（`ls-remote` 级别，**不克隆**）。分类型返回，前端逐项给不同文案，
        绝不允许笼统报"拉取失败"：地址不可解析 / host 不在白名单 / DNS 解析后落在敏感段 /
        SSH 握手失败（含 **host key 校验失败**）/ 凭据无读权限 / 仓库不存在 / 默认分支不存在。
        预检通过只代表"能连上并能看到默认分支"，不代表后面一定克隆成功（大小上限、超时另算）。
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [url]
              properties:
                url: { type: string, maxLength: 1000, description: 'https://host/group/repo(.git) 或 git@host:group/repo.git；原样提交，由 url-mapping 归一' }
      responses:
        '200':
          description: 预检结果（注意：ok=false 也是 200——这是"检查报告"，不是"操作失败"）
          content:
            application/json:
              schema:
                type: object
                required: [ok, checks]
                properties:
                  ok: { type: boolean }
                  checks:
                    type: object
                    properties:
                      urlParsed: { type: boolean }
                      hostAllowed: { type: boolean }
                      dnsSafe: { type: boolean, description: 解析结果不在环回/链路本地/云元数据段 }
                      handshakeOk: { type: boolean, description: SSH 握手成功 }
                      hostKeyVerified: { type: boolean, description: host key 命中 pin（known_hosts 或指纹）；false 即视为不可用，不给"仍然继续"的选项 }
                      authOk: { type: boolean, description: 部署私钥被接受、有读权限 }
                      repoExists: { type: boolean }
                      defaultBranchFound: { type: boolean }
                  resolved:
                    type: object
                    nullable: true
                    description: 预检只解析默认分支与其 HEAD，不解析用户指定 ref（v1 不支持）
                    properties:
                      defaultBranch: { type: string, example: main }
                      headCommit: { type: string, example: 3f2a…（40 位） }
                  transport: { type: string, enum: [SSH_KEY], example: SSH_KEY }
                  errorCode: { type: string, nullable: true, example: plugin.error.gitHostNotAllowed }
        '403': { $ref: '#/components/responses/PluginError' }
        '429': { $ref: '#/components/responses/RateLimited', description: '10 次/分钟/用户（预检会打到内网 Git，必须限流）' }

  /api/v1/plugins/submissions:
    post:
      security: [{ sessionCookie: [] }]
      description: >-
        只做毫秒级同步校验；扫描/代发/落库/索引重建异步。
        **四种来源（npm / git / zip / folder）都必须携带 `visibility`**（`PUBLIC` / `NAMESPACE_ONLY` / `PRIVATE`，必选、无留空、无服务端默认值，
        `PRD §6.3` v2.16）：缺失或非法 → 400 `PLUGIN_VALIDATION_VISIBILITY_INVALID`，**不得**兜底成 `PUBLIC`
        （"没选就公开"是安全事故，不是便利）。可见范围与"空间是否公开"是两个正交维度，落库后共同决定可见性与状态文案（`PRD §8.4`）。
        **调用时机（PRD §6.3 v2.19 ⑨）**：本端点是**唯一受理入口**，只能由「确认提交信息」弹窗的「确认发布」触发；第一步「下一步：确认」只打开弹窗，不得预调本端点、不得预建 submission 或提前返回 `submissionId`。未确认前无受理记录、无状态推进；服务端无需持久化"待确认"态（表单是纯前端状态）。两步时序见 §4.1、状态机见 §5.3、组件见 §10.2。
      requestBody:
        content:
          application/json:
            schema:
              oneOf:
                - $ref: '#/components/schemas/NpmSubmissionRequest'
                - $ref: '#/components/schemas/GitSubmissionRequest'
            examples:
              npm:
                summary: npm 来源（visibility 必填、无默认）
                value: { namespace: team-a, visibility: PUBLIC, sourceType: npm, category: devtools, packageName: '@team/bar' }
              git:
                summary: Git 来源（可见范围同样必填，这里选仅命名空间）
                value: { namespace: team-a, visibility: NAMESPACE_ONLY, sourceType: git, category: devtools, url: 'git@git.internal:group/dsh-plugin-bar.git' }
          multipart/form-data:
            schema:
              type: object
              required: [namespace, visibility, sourceType, category, package]
              properties:
                namespace: { type: string }
                visibility:
                  $ref: '#/components/schemas/PluginVisibility'
                sourceType: { type: string, enum: [zip, folder] }
                category: { $ref: '#/components/schemas/PluginCategory' }
                package: { type: string, format: binary, description: 'zip ≤ 50 MiB；folder 由前端打包为 zip 后走同一字段' }
      responses:
        '202':
          description: 同步校验通过，进入异步状态机
          content:
            application/json:
              schema:
                type: object
                required: [submissionId, status, pollAfterMs]
                properties:
                  submissionId: { type: integer, format: int64 }
                  status: { $ref: '#/components/schemas/PluginSubmissionStatus' }
                  pollAfterMs: { type: integer, example: 2000 }
        '403': { $ref: '#/components/responses/PluginError' }
        '409': { $ref: '#/components/responses/PluginError' }
        '413': { $ref: '#/components/responses/PluginError' }
        '422': { $ref: '#/components/responses/PluginError' }
        '429': { $ref: '#/components/responses/RateLimited' }
        '503': { $ref: '#/components/responses/PluginError' }

  /api/v1/plugins/submissions:
    get:
      security: [{ sessionCookie: [] }]
      description: >-
        我的在途提交（「我的插件」页的在途区用）。只返回当前用户提交、且可按 terminal 过滤的记录；
        默认 terminal=false（只要非终态），这样 SCAN_RED / SCAN_FAILED / VALIDATION_FAILED 这些"没有版本行"的提交
        也能被用户看到——按插件聚合会漏掉它们。
      parameters:
        - { name: terminal, in: query, schema: { type: boolean, default: false } }
        - { name: namespace, in: query, schema: { type: string } }
        - { name: page, in: query, schema: { type: integer, default: 0 } }
        - { name: size, in: query, schema: { type: integer, default: 20 } }
      responses:
        '200': { description: "分页；每项字段与 GET /submissions/{id} 同构（去掉 report 正文）" }
        '403': { $ref: '#/components/responses/PluginError' }

  /api/v1/plugins/submissions/{id}:
    get:
      security: [{ sessionCookie: [] }]
      description: 提交进度轮询；仅提交者本人、空间成员、审核人、平台管理员可见
      responses:
        '200':
          content:
            application/json:
              schema:
                type: object
                required: [id, status, stage, terminal, attemptCount, pollAfterMs]
                properties:
                  id: { type: integer, format: int64 }
                  status: { $ref: '#/components/schemas/PluginSubmissionStatus' }
                  stage: { type: string, description: 与 status 同义的展示阶段标识 }
                  terminal: { type: boolean }
                  attemptCount: { type: integer }
                  pollAfterMs: { type: integer, description: 终态时为 0，前端停止轮询 }
                  packageName: { type: string }
                  version: { type: string }
                  pluginId: { type: integer, format: int64, nullable: true }
                  pluginVersionId: { type: integer, format: int64, nullable: true }
                  scanReportId: { type: integer, format: int64, nullable: true }
                  verdict: { $ref: '#/components/schemas/PluginScanVerdict' }
                  errorCode: { type: string, nullable: true, example: plugin.error.scanRedLine }
                  errorDetail: { type: string, nullable: true, description: 脱敏摘要，不含 Token、URL userinfo }
        '403': { $ref: '#/components/responses/PluginError' }
        '404': { $ref: '#/components/responses/PluginError' }

  /api/v1/plugins/submissions/{id}/files:
    get:
      security: [{ sessionCookie: [] }]
      description: >-
        **审核用包内文件清单**（只返回清单，不返回任何文件内容）。数据源只有一处：
        `plugin_submission.staged_tarball_key` 指向的暂存 tarball——审核发生在代发之前，私服里没有这个包，
        **任何情况下不得回退**去私服或上游 npm 拉同名同版本（否则就成了"审 A 看 B"）。
        可见范围：**该空间的空间管理员（空间 `ADMIN`）+ 提交者本人**（看自己的提交），
        **平台管理员（`SKILL_ADMIN` / `SUPER_ADMIN`）不在其列**——`PRD §9` 矩阵「审核插件版本（团队空间）」
        一行是访客—/空间成员—/空间管理员✓/**平台管理员—**，他们查看扫描结论走矩阵「查看完整扫描报告与证据」
        那一行，与包内文件浏览是两件事（v2.14 `PRD §6.5` 第 6 条按矩阵收紧）。
        **其他空间成员（非 ADMIN）、他空间成员、平台管理员与非成员一律 403** `plugin.error.noPermission`
        （与私有空间对非成员的口径一致，不泄漏存在性）。注意与 `GET /submissions/{id}` 的区别：那个接口空间成员可看
        "状态"，本组接口空间成员**不能**看"内容"——包内容是审核材料，不是空间公共资产（§7.6.6）。
        提交不存在返回 **404**。
        返回 `entries[]` + 包级 `sha512` + 服务端**实际生效的上限**（`limits`），
        前端据此决定"可预览 / 只能下载 / 已清理"，不得自己硬编码上限。
      responses:
        '200':
          description: >-
            清单读取成功。`entries[].path` 为包内相对路径（POSIX，无前导 `/`），与 tar 条目名逐字节一致。
            超任一上限时**不截断、不返回半份清单**，直接 422（见 §7.6 上限表）
          content:
            application/json:
              schema: { $ref: '#/components/schemas/StagedFileListing' }
        '403': { $ref: '#/components/responses/PluginError', description: '既非该空间空间管理员也非提交者本人（平台管理员 SKILL_ADMIN / SUPER_ADMIN 亦在此列，§7.6.6）' }
        '404': { $ref: '#/components/responses/PluginError', description: 'plugin.error.stagedSubmissionNotFound' }
        '410': { $ref: '#/components/responses/PluginError', description: 'plugin.error.stagedPackagePurged——包内容已按保留策略清理（§6.4）' }
        '422': { $ref: '#/components/responses/PluginError', description: 'plugin.error.stagedArchiveLimitExceeded——文件数/总解压量/压缩比/预算超限' }
        '429': { $ref: '#/components/responses/RateLimited', description: '60 次/分钟/用户（§7.5）' }
        '503': { $ref: '#/components/responses/PluginError', description: 'plugin.error.fileBrowseDisabled——运维关闭浏览能力，或审计写入失败（fail closed）' }

  /api/v1/plugins/submissions/{id}/files/content:
    get:
      security: [{ sessionCookie: [] }]
      description: >-
        读取**单个文件**的内容。`path` 必须精确命中清单中的某一项（白名单逐字节比对），
        **禁止**"取 query 参数 → 拼路径 → 打开文件"这种写法：路径穿越的第一道防线在物化阶段，
        第二道就是这里的白名单，第三道才是读取时的规范化复核（§7.6 三层）。
        文本一律以 `text/plain; charset=utf-8` 返回；**绝不**返回 `text/html`、`image/svg+xml`、
        `application/javascript`、`text/javascript`、`application/xhtml+xml` 等可执行/可渲染类型。
        二进制**不返回字节**，返回 200 + 元信息（判定类型 / 大小 / 前 8 字节魔数），由前端提示改用下载端点。
        权限、404/403/410 口径与清单端点完全一致（同一个 `assertVisible`）。
      parameters:
        - name: path
          in: query
          required: true
          schema: { type: string, maxLength: 1024 }
          description: >-
            包内相对路径，必须与清单条目**逐字节相等**（大小写敏感，不做 case-folding）。
            含 `..` 段、以 `/` 开头、含盘符（`^[A-Za-z]:`）、含 NUL、含反斜杠 `\`、
            或解码后仍含 `%` 的值一律 **400 `plugin.error.stagedFilePathInvalid`**（口径见 §7.6）。
      responses:
        '200':
          description: 文本内容（text/plain）或二进制元信息（application/json）；两者互斥
          headers:
            X-Content-Type-Options: { schema: { type: string, enum: [nosniff] }, description: '写死，防 MIME 嗅探' }
            Content-Security-Policy: { schema: { type: string }, description: "default-src 'none'; sandbox（写死，禁止脚本与任何子资源）" }
            Cache-Control: { schema: { type: string, enum: ['no-store'] }, description: 审核材料不进任何共享缓存 }
            Cross-Origin-Resource-Policy: { schema: { type: string, enum: [same-origin] } }
            Content-Disposition: { schema: { type: string }, description: "inline; filename*=UTF-8''<encoded>（文本）；二进制元信息响应不带该头" }
          content:
            text/plain:
              schema: { $ref: '#/components/schemas/StagedFileText' }
            application/json:
              schema: { $ref: '#/components/schemas/StagedFileBinaryMeta' }
        '400': { $ref: '#/components/responses/PluginError', description: 'plugin.error.stagedFilePathInvalid' }
        '403': { $ref: '#/components/responses/PluginError' }
        '404': { $ref: '#/components/responses/PluginError', description: 'plugin.error.stagedFileNotFound——白名单未命中（不区分"不存在"与"不许看"，避免探测包结构）' }
        '409': { $ref: '#/components/responses/PluginError', description: 'plugin.error.stagedPackageMismatch——取到的字节与记录 sha512 不一致，已拒绝返回并告警（§14.1 A13）' }
        '410': { $ref: '#/components/responses/PluginError', description: 'plugin.error.stagedPackagePurged' }
        '413': { $ref: '#/components/responses/PluginError', description: 'plugin.error.stagedFileTooLarge——单文件超上限，改走下载端点' }
        '422': { $ref: '#/components/responses/PluginError', description: 'plugin.error.stagedArchiveLimitExceeded' }
        '429': { $ref: '#/components/responses/RateLimited', description: '120 次/分钟/用户（浏览一个树会连点，量级比清单端点高，§7.5）' }
        '503': { $ref: '#/components/responses/PluginError', description: 'plugin.error.fileBrowseDisabled' }

  /api/v1/plugins/submissions/{id}/files/download:
    get:
      security: [{ sessionCookie: [] }]
      description: >-
        单个文件的字节下载（二进制或超出内联预览上限的文本）。存在的唯一理由是"审核人要看清一个 8 MiB 的
        `dist/bundle.js` 或一张 SVG"——不是为了给用户下载插件包（站内不提供 tarball 下载，§13 #4）。
        响应强制 `Content-Disposition: attachment` + `X-Content-Type-Options: nosniff` +
        `Content-Security-Policy: default-src 'none'; sandbox` + `Cache-Control: no-store`，
        `Content-Type` 一律 `application/octet-stream`（**不猜包内声明的类型**，不返回 `image/svg+xml`）。
        每次下载写审计（含 path），权限与错误口径同内容端点。
      parameters:
        - name: path
          in: query
          required: true
          schema: { type: string, maxLength: 1024 }
      responses:
        '200':
          description: 文件字节流（application/octet-stream + attachment）
          headers:
            Content-Disposition: { schema: { type: string }, description: "attachment; filename*=UTF-8''<encoded>" }
            X-Content-Type-Options: { schema: { type: string, enum: [nosniff] } }
            Content-Security-Policy: { schema: { type: string }, description: "default-src 'none'; sandbox" }
            Cache-Control: { schema: { type: string, enum: ['no-store'] } }
        '400': { $ref: '#/components/responses/PluginError' }
        '403': { $ref: '#/components/responses/PluginError' }
        '404': { $ref: '#/components/responses/PluginError' }
        '409': { $ref: '#/components/responses/PluginError' }
        '410': { $ref: '#/components/responses/PluginError' }
        '413': { $ref: '#/components/responses/PluginError', description: 'plugin.error.stagedFileTooLarge——单文件超过可下载上限（8 MiB）' }
        '429': { $ref: '#/components/responses/RateLimited', description: '10 次/分钟/用户（与 `plugin-git-preflight` 同量级，§7.5）' }
        '503': { $ref: '#/components/responses/PluginError' }

  /api/v1/dashboard/plugins:
    get:
      security: [{ sessionCookie: [] }]
      parameters:
        - { name: q, in: query, schema: { type: string } }
        - { name: status, in: query, schema: { type: string, enum: [DRAFT, PENDING, LISTED, HIDDEN, BLOCKED, REMOVED] } }
        - { name: namespace, in: query, schema: { type: string } }
        - { name: category, in: query, schema: { $ref: '#/components/schemas/PluginCategory' } }
        - { name: verdict, in: query, schema: { type: string, enum: [clear, review, red] } }
        - { name: page, in: query, schema: { type: integer, default: 0 } }
        - { name: size, in: query, schema: { type: integer, default: 20 } }
      responses:
        '200':
          description: 我提交的插件（含 PENDING/REJECTED/HIDDEN/BLOCKED 与裁决列）；全部筛选在后端完成，前端不做二次过滤
          content:
            application/json:
              schema:
                type: object
                required: [items, total]
                properties:
                  items:
                    type: array
                    items:
                      type: object
                      description: 既有「我的插件」列表投影（包名 / 状态 / 可见范围 / 裁决 / 最新版本）全部保留，此处只显式声明本次补入的计数字段
                      required: [promptCopyCount]
                      properties:
                        promptCopyCount: { type: integer, format: int64, description: '复制提示词次数；与详情页、市场卡片读同一个 plugin.prompt_copy_count 字段、同一句文案（PRD §10 三处同源，v2.20 S06），不下发 viewCount' }
                  total: { type: integer }

  /api/v1/dashboard/namespaces/{slug}/plugins:
    get:
      security: [{ sessionCookie: [] }]
      description: 空间维度插件列表；非成员返回 403（不返回空列表，避免误导）
      responses:
        '200': { description: 该空间全部插件（含待审/拒绝/下架） }
        '403': { $ref: '#/components/responses/PluginError' }

  /api/v1/plugins/{namespace}/{slug}:
    patch:
      security: [{ sessionCookie: [] }]
      description: 改分类（空间 ADMIN 或作者）；上架后可改，改后触发索引重建
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [category]
              properties:
                category: { $ref: '#/components/schemas/PluginCategory' }
      responses:
        '200': { description: 已更新 }
        '403': { $ref: '#/components/responses/PluginError' }

  /api/v1/plugins/{namespace}/{slug}/visibility:
    patch:
      security: [{ sessionCookie: [] }]
      description: >-
        改可见范围（本设计新增的**窄端点**；`PRD §6.3`（v2.17）只给"可改"这个能力与"缩窄可单改、立即生效 / 放宽无单改入口"的规则，未给路径）。
        **v1 只受理缩窄方向**：`PUBLIC → NAMESPACE_ONLY` / `PUBLIC → PRIVATE` / `NAMESPACE_ONLY → PRIVATE`，立即生效、
        同事务写审计（`PLUGIN_VISIBILITY_CHANGED`，含旧值 → 新值）并 `markDirty` 触发索引重建。
        任何**变宽**方向（`PRIVATE → *`、`NAMESPACE_ONLY → PUBLIC`、以及同值重复提交视为无变更 → 200 幂等）一律
        409 `PLUGIN_CONFLICT_VISIBILITY_WIDEN_PENDING` + 固定文案「v1 不支持放宽可见范围，如需放宽请重新提交」——
        产品侧 `PRD v2.17` 已明确"放宽没有单改入口、只能随新版本提交"，因此这里只拒绝不受理（不为此新增插件级审核主题，见 §13 #22、§14.2 Q11 已裁）。
        权限与"改分类"同级：插件作者本人 + 该空间空间管理员；其他空间成员与非成员 403 `plugin.error.noPermission`。
        与既有 `PATCH /plugins/{ns}/{slug}`（改分类）**分开**：两个动作的权限相同但后果不同（可见范围会改变索引收录与访问可见性），
        合并成一个端点会让"只改分类"的调用方意外触发可见性变更。
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [visibility]
              properties:
                visibility:
                  $ref: '#/components/schemas/PluginVisibility'
                reasonCode: { type: string, maxLength: 64, description: 可选，写入审计；缩窄不需要理由，留作运营追溯 }
      responses:
        '200': { description: 已变更并已标脏索引（缩窄立即生效） }
        '400': { $ref: '#/components/responses/PluginError', description: 'plugin.error.visibilityInvalid（非法取值）' }
        '403': { $ref: '#/components/responses/PluginError' }
        '404': { $ref: '#/components/responses/PluginError' }
        '409': { $ref: '#/components/responses/PluginError', description: 'plugin.error.visibilityWidenPending（变宽方向 v1 不受理）' }

  /api/v1/plugins/{namespace}/{slug}/lifecycle:
    post:
      security: [{ sessionCookie: [] }]
      description: 下架 / 恢复上架（`PRD §9` 授权矩阵；本接口为本设计新增，PRD 只给了能力未给路径）
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [action]
              properties:
                action: { type: string, enum: [HIDE, RESTORE] }
                reasonCode: { type: string, maxLength: 64 }
      responses:
        '200': { description: 已变更并已标脏索引 }
        '403': { $ref: '#/components/responses/PluginError' }
        '409': { $ref: '#/components/responses/PluginError' }

  /api/v1/plugins/{namespace}/{slug}/versions/{version}/yank:
    post:
      security: [{ sessionCookie: [] }]
      description: >-
        撤下 / 恢复单个版本。权限：空间 ADMIN、插件作者、SKILL_ADMIN、SUPER_ADMIN。
        YANK 前置 status=LISTED；UNYANK 前置 status=YANKED 且该版本最近一次成功报告裁决非 red（或被按指纹放行），
        且私服仍能取到该制品。两个方向都幂等（重复调用 200，不改首次审计的 reasonCode）。
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [action, reasonCode]
              properties:
                action: { type: string, enum: [YANK, UNYANK] }
                reasonCode: { type: string, maxLength: 64, description: 预置原因码，前端下拉选择，不允许自由文本（避免把内部信息写进审计） }
      responses:
        '200':
          description: >-
            已变更并已标脏索引。YANK 若撤掉的是最后一个 LISTED 版本，同一事务内把 plugin.status 置 HIDDEN
            并把 latest_version_id 回填为剩余最高 LISTED 版本（无则 NULL）；不删除私服制品，
            已安装用户与既有 lockfile 不受影响，仅"市场不可发现"。
        '403': { $ref: '#/components/responses/PluginError' }
        '404': { $ref: '#/components/responses/PluginError' }
        '409': { $ref: '#/components/responses/PluginError' }
        '429': { $ref: '#/components/responses/RateLimited' }

  /api/v1/plugins/{namespace}/{slug}/versions/{version}/publish:
    post:
      security: [{ sessionCookie: [] }]
      description: >-
        代发重试（审核通过后代发失败的人工出口）。作者本人、空间 ADMIN、SKILL_ADMIN / SUPER_ADMIN 均可用；
        前置 version.status ∈ {APPROVED, SCAN_PASSED} 且父插件未上架。重试**不重走扫描与审核**：
        只把 publish_attempt 清零、next_attempt_at 置为 now，由 worker 领走。幂等：连续调用只改首次审计。
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [action]
              properties:
                action: { type: string, enum: [RETRY] }
      responses:
        '200': { description: 已重新入队；返回最新 status 与 nextAttemptAt }
        '403': { $ref: '#/components/responses/PluginError' }
        '404': { $ref: '#/components/responses/PluginError' }
        '409': { $ref: '#/components/responses/PluginError', description: '版本不在 APPROVED/SCAN_PASSED，或父插件已上架' }
        '429': { $ref: '#/components/responses/RateLimited' }

  /api/v1/plugins/{namespace}/{slug}/delete:
    post:
      security: [{ sessionCookie: [] }]
      description: >-
        软删整个插件（status → REMOVED）。权限：空间 ADMIN + 平台管理员（SKILL_ADMIN / SUPER_ADMIN），按 PRD §9 矩阵。
        必填理由；幂等（已 REMOVED 再调用返回 200 且不改首次理由）。不删 tarball 与报告；
        软删后详情页与安装文档立即 404、索引下一轮剔除；**package_name 不自动释放**。
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [reason]
              properties:
                reason: { type: string, minLength: 4, maxLength: 1000 }
      responses:
        '200': { description: 已软删（或此前已软删），已标脏索引 }
        '403': { $ref: '#/components/responses/PluginError' }
        '404': { $ref: '#/components/responses/PluginError' }

  /api/v1/plugins/{namespace}/{slug}/release-name:
    post:
      security: [{ sessionCookie: [] }]
      description: >-
        释放包名（墓碑化）。仅 SUPER_ADMIN。前置 status=REMOVED（否则 409 plugin.error.notRemoved），
        避免"还挂着却已释放"导致同名包并存。释放后 package_name 与 (namespace_id, slug) 都不再参与唯一性，
        新提交可占用；旧行保留为墓碑，对任何用户可见入口不可见。幂等（重复调用只保留首次 name_released_at）。
        与 delete 分成两个端点是刻意的：软删是"运营动作"，释放是"不可逆的名额回收"，权限与责任人都不同。
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [reason]
              properties:
                reason: { type: string, minLength: 4, maxLength: 1000 }
      responses:
        '200': { description: 已释放；返回 releasedAt 与该包名的占位状态 }
        '403': { $ref: '#/components/responses/PluginError' }
        '404': { $ref: '#/components/responses/PluginError' }
        '409': { $ref: '#/components/responses/PluginError' }

  /api/v1/plugins/scan-reports/{id}:
    get:
      security: [{ sessionCookie: [] }]
      description: 完整报告与证据；仅提交者本人、空间成员、审核人、SKILL_ADMIN、SUPER_ADMIN
      responses:
        '200': { description: 完整（必要时已截断标记）报告 }
        '403': { $ref: '#/components/responses/PluginError' }

  /api/v1/plugins/{namespace}/{slug}/versions/{version}/rescan:
    post:
      security: [{ sessionCookie: [] }]
      description: 重新扫描，限流 5 次/小时/版本；新增 report 行，不改版本状态（除 red 自动下架）
      responses:
        '202': { description: 已排队 }
        '429': { $ref: '#/components/responses/PluginError' }

  /api/v1/plugins/scan-reports/{id}/waiver:
    post:
      security: [{ sessionCookie: [] }]
      description: >-
        红线放行 / 撤销放行（SKILL_ADMIN / SUPER_ADMIN）；必填理由；写审计（含放行者、时间、指纹、红线码）。
        撤销放行若导致某版本从"已放行"变为"未放行且 red"，该版本随插件自动下架，
        并在**同一次审计里**记录撤销原因、新结论与受影响版本，同时**站内通知作者与该次放行的判定人**
        （说明撤销原因、新结论指向、重新提交/重新判定路径）。沿用现有站内通知渠道，不新增通道；
        通知发送失败不回滚业务，但要在审计 detail 里写 `notifyFailed=true` 以便补发。
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [waived, reason]
              properties:
                waived: { type: boolean }
                reason: { type: string, minLength: 4, maxLength: 1000 }
      responses:
        '200': { description: 已记录；若触发自动下架，返回 affectedVersions 与 notificationsSent }
        '403': { $ref: '#/components/responses/PluginError' }
```

**包内文件浏览的响应头与渲染硬约束（P0 级红线，回归断言见 §16）**——这一组端点把**未审核的第三方内容**搬到本站域名下，任何一处放松都等于给自己开一个存储型 XSS：

| # | 硬约束 | 违反的后果 |
|---|---|---|
| 1 | 文本内容响应的 `Content-Type` **只能是** `text/plain; charset=utf-8` | 包内 `.html` / `.svg` / `.js` 会被浏览器当文档或脚本执行（同源 XSS，可直接读 Cookie） |
| 2 | 三个端点（清单/内容/下载）响应头都必须带 `X-Content-Type-Options: nosniff` | 老浏览器会按内容嗅探类型，"返回 text/plain 但内容像 HTML"照样被渲染 |
| 3 | 文本与下载响应必须带 `Content-Security-Policy: default-src 'none'; sandbox` | 即便类型被绕过，脚本与子资源加载也被封死；`sandbox` 顺带切断 `top` 导航与表单提交 |
| 4 | 一律 `Cache-Control: no-store` | 审核材料（可能含凭据/内网地址）不得进 CDN、代理或浏览器磁盘缓存 |
| 5 | 下载端点 `Content-Type` 固定 `application/octet-stream` + `Content-Disposition: attachment` | 猜包内声明的类型会把 `image/svg+xml` 直接交给浏览器渲染 |
| 6 | 前端渲染只能用文本节点（`{text}` / `textContent` / `<pre>`）；**禁止** `innerHTML`、`dangerouslySetInnerHTML`、`<iframe src>` 指向包内文件、`<object>`/`<embed>` | React 的自动转义是唯一成本为零的 XSS 防线；一旦有人图省事用 `dangerouslySetInnerHTML` 做"高亮"，前面 5 条全白做 |
| 7 | 包内文件的 `Content-Disposition` 只有两种合法组合：内容端点 `inline` **且** `Content-Type` 必须是 `text/plain; charset=utf-8`；下载端点 `attachment` **且** `application/octet-stream`。**禁止** `inline` 与任何可渲染类型同时出现 | `inline` + `text/html` 是最经典的一击：浏览器直接把它当页面打开，且 URL 还是我们域名下的 |

第 6 条不是"建议"：`PluginFileBrowser` 里的高亮如果用第三方库，必须是"先转义再做标记、输出到 `textContent`"的方案；任何引入 `innerHTML` 的实现视为不通过评审。

举报（**只做入口 + 只读可见，本期不做处置流程**）：

> 口径来源：PRD 只要求"详情页有举报入口"（`PRD §1.2`/`PRD §5.1` 明确处置流程与责任方不在本期）。**只读列表是设计补齐的运营底线**——举报写进去连运营都看不见，这个入口就是骗用户。但这不等于承诺处置：没有状态、没有"已处理/已忽略"、没有处置动作端点。

```yaml
paths:
  /api/v1/plugins/{namespace}/{slug}/report:
    post:
      security: [{ sessionCookie: [] }]
      description: >-
        详情页举报入口。权限：任意登录用户（作者举报自己无意义但不必拦）。
        只落一行 plugin_report 并写审计；**不阻断、不自动下架、不改任何插件状态**。
        限流 5 次/小时/用户；刻意不做"同一人同一插件只留一条"的唯一约束（没有处置态可以判定"是否已处理"，
        全唯一会把"同一人后来再次举报"永久挡掉）。
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [reasonCode]
              properties:
                reasonCode: { type: string, enum: [misleading, malicious, license, spam, other] }
                details: { type: string, maxLength: 2000, description: reasonCode=other 时必填 }
                version: { type: string, description: 可选；不传表示针对插件整体 }
      responses:
        '200': { description: 已受理（同一人重复点只多出一行，不影响插件） }
        '403': { $ref: '#/components/responses/PluginError' }
        '404': { $ref: '#/components/responses/PluginError' }
        '429': { $ref: '#/components/responses/RateLimited' }

  /api/v1/plugins/reports:
    get:
      security: [{ sessionCookie: [] }]
      description: >-
        举报**只读**列表（SKILL_ADMIN / SUPER_ADMIN）。复用既有"举报管理"页的列表外壳加一个 tab，
        只读、按时间倒序，**不提供任何状态筛选以外的写操作**（无 PATCH、无处置端点）。
      parameters:
        - { name: pluginId, in: query, schema: { type: integer, format: int64 } }
        - { name: since, in: query, schema: { type: string, format: date } }
        - { name: page, in: query, schema: { type: integer, default: 0 } }
        - { name: size, in: query, schema: { type: integer, default: 20 } }
      responses:
        '200': { description: 分页列表（插件摘要 + 举报人脱敏标识 + 理由码 + 时间）；无处置字段 } 
        '403': { $ref: '#/components/responses/PluginError' }
```

审核决定（**复用既有端点**，只补插件语义；路径与请求体不变，仍由既有 `ReviewController` 提供）：

```yaml
paths:
  /api/v1/reviews/{taskId}/approve:
    post:
      security: [{ sessionCookie: [] }]
      description: >-
        通过。对 subjectType=PLUGIN_VERSION：**按提交来源分两种走向**——
        ① 来源 `zip`/`folder`：版本 REVIEW_PENDING → APPROVED，review_task 结案，代发由 worker 在下一轮领走
        （不在此接口内调用私服）；② 来源 `npm`/`git`：版本 **REVIEW_PENDING → LISTED 直接上架**（同一事务，`APPROVED` 不落库），
        `publish_timestamp` 与 `markDirty` 一并写入——npm 的包本来就在源上、git 按仓库地址 + 提交号分发，**都不需要代发**
        （`PRD §6.2`/`§11` 第 17 项）。响应体**只有** `status`，**不得**再返回 `publishScheduled` 之类发布/代发状态字段
        （审核页不需要知道平台内部何时推送，§7.2 五条硬规则 1）。
        **权限按 subjectType 分流，两套语义不许合并**：
        ① `PLUGIN_VERSION` → **只允许该空间的空间管理员**（`PluginPermissionChecker.assertSpaceAdmin(namespaceId)`）；
        平台管理员（`SKILL_ADMIN` / `SUPER_ADMIN`）**403 `plugin.error.noPermission`**——`PRD §9` 矩阵
        「审核插件版本（团队空间）」该行对平台管理员是 `—`，且平台管理员按同一矩阵**没有包内文件读取权**（§7.6.6），
        若允许其审批就是**无依据盲批**；平台管理员的正当入口是红线放行 / 黑名单 / 释放包名，不是审核通过。
        ② `SKILL_METADATA` 等**既有 subjectType → 保持既有行为完全不变**（既有技能审核允许平台管理员，
        本轮不得收紧）：实现必须**先判 `subjectType` 再断言**，禁止把插件分支的权限收紧扩散到技能侧。
        ③ **允许自审（用户裁决），但必须留痕**：`submitted_by == actor` **不拦截**，正常通过；审计
        `PLUGIN_REVIEW_APPROVED` 增加必填字段 `selfReview: true|false`（`actor == submitted_by` 时为 `true`）。
        这是**审计字段、不是拦截条件**：允许自审是因为单人空间/唯一管理员场景下没有别的审核人，拦掉等于审核永远做不了；
        但"谁批的、是不是自己批的"必须可事后区分，否则复盘时无法识别"这条结论只有提交人自己看过"。
      responses:
        '200':
          description: 已通过。zip/folder 的发布已入队（上架通常在分钟级完成，端到端 ≤2 分钟见 §8.3）；npm/git 此刻已是 `LISTED`
          content:
            application/json:
              schema:
                type: object
                required: [status]
                properties:
                  status: { type: string, example: APPROVED, description: 'review_task 的结论，两种来源都一样；版本的 status 见 GET 审核项/提交详情' }
        '403': { $ref: '#/components/responses/PluginError', description: 'plugin.error.noPermission（非该空间空间管理员，含平台管理员）——自审不在此列，不拦截（§4.6 硬规则 3）' }
        '409': { $ref: '#/components/responses/PluginError', description: 乐观锁冲突 / 版本已不在 REVIEW_PENDING }

  /api/v1/reviews/{taskId}/reject:
    post:
      security: [{ sessionCookie: [] }]
      description: >-
        拒绝（必填理由）。对 subjectType=PLUGIN_VERSION：版本 REVIEW_PENDING → REJECTED（终态），review_task 结案。
        **没有任何"撤销已发布制品"的动作**——制品从未离开暂存区，私服上本来就没有 name@version。
        **权限与 approve 完全同源**（同一条 `subjectType` 分流）：`PLUGIN_VERSION` 只允许该空间空间管理员
        （平台管理员 403 `noPermission`），`SKILL_METADATA` 等既有 subjectType 行为不变。
        自审同样**不拦截**，但审计 `PLUGIN_VERSION_REJECTED` 同样带 `selfReview: true|false`——
        通过权与拒绝权必须对称（只给通过留痕、不给拒绝留痕，等于让"自审拒绝"消失在审计里）。
      responses:
        '200':
          description: 已拒绝（终态）
          content:
            application/json:
              schema:
                type: object
                properties:
                  status: { type: string, example: REJECTED }
        '403': { $ref: '#/components/responses/PluginError', description: '同上（plugin.error.noPermission）' }
        '409': { $ref: '#/components/responses/PluginError' }
```

关于审核通过/拒绝的六条硬规则（链路反转后两条不变，本次补四条：权限同源、允许自审并留痕、两轴正交、审核面口径只谈上架与否）：

1. **通过 = 落库即生效；是否需要代发由来源决定**。`approve` 接口内不调用私服（`publish` 是外部网络调用，可能几十秒）。**zip/folder**：版本 `REVIEW_PENDING → APPROVED`、review_task 结案、写审计，代发交给 worker，作者侧显示"已通过审核，等待发布"；**npm/git**：版本 `REVIEW_PENDING → LISTED` **直接上架**（同一事务写 `publish_timestamp` + `markDirty`），作者侧看到的就是「已上架」，**没有"等待发布"这一态**。响应体**只有** `status`。
2. **拒绝 = 终态，无任何撤销动作**。制品从未推送，`REJECTED` 就是结束；审核人不需要为"撤销已发布"再等一个异步结果（v2.2 的 `revokeState` 字段与整条撤包链路已删除）。
3. **审核决定权与包内文件读取权必须同源（本次新增）**。`PLUGIN_VERSION` 的 approve/reject 与 §7.2 的三个文件浏览端点用**同一条判定**：该空间空间管理员；平台管理员两处都 403。原因不是权限美学，而是**后果**：若允许"看不到包却能批"，审批就退化成盲签，审计里也查不出他到底看没看（§14.1 A18）。注意这条只约束"**谁能进审核**"，不约束"作者能不能批自己的包"——后者见第 4 条。
4. **允许自审，但必须留痕（本次新增，用户裁决）**：`submitted_by == actor` 时 approve **与** reject 都**不拦截**，正常执行；审计 `PLUGIN_REVIEW_APPROVED` / `PLUGIN_VERSION_REJECTED` 各带一个必填字段 `selfReview: true|false`（`actor == submitted_by` 时为 `true`）。为什么允许：单人空间、或空间只有一个管理员时，拦掉自审等于审核永远做不了，功能直接失效；为什么必须留痕：不加字段就无法区分"这条结论是别人审的"还是"提交人自己批的"，出事后复盘没有依据。审核页在裁决按钮旁显示一行**中性**提示「你是本次提交的作者」（灰色说明文字，不用警示色、不阻断操作），让审核人知道自己正在批自己的包——告知不等于禁止。
5. **自审与权限是两个正交的轴**：能进审核（第 3 条）≠ 不能审自己（第 4 条允许）≠ 能看包（§7.6.6）。三者组合出的四种身份（他空间管理员、本空间管理员、作者兼管理员、平台管理员）在 §7.6.6 表里逐行写清了结果。
6. **审核面只谈"上架 / 未上架"，不谈平台内部动作（本次新增，`PRD §6.5` v2.15）**：审核页（含审核队列项、审核详情、approve/reject 响应、拒绝弹窗文案）**不得出现**"代发""内部源""私服""推送"等平台内部动作或内部分发通道的说法，也**不得下发任何发布状态字段**（`publishState` / `published` / `publishAttempt` / `publishError` / `nextAttemptAt`）。理由不是保密，而是**口径必须唯一**：审核人要回答的问题只有一个——"这个版本能不能进插件市场"；一旦页面告诉他"已推送到某处"，他就会开始按"制品是否到位"做判断，而不是按"包内容是否可信"做判断，审核依据被悄悄换掉了。固定文案「该版本尚未上架，通过后才会进入插件市场与索引」常驻，四语言齐平（§16 S20 有静态门禁）。

**审核队列项新增三个只读字段**（前端按钮与提示的唯一依据，服务端计算，实现不得让前端推断角色）：`GET /api/v1/reviews?subjectType=PLUGIN_VERSION` 的每一项与详情项返回

| 字段 | 类型 | 含义 |
|---|---|---|
| `canReview` | `boolean` | 当前用户能否对该项 approve/reject。**只由"是否该空间空间管理员"与"是否仍在待审态"决定，不因"是作者"而为 false**。`false` 时前端置灰按钮并显示 `canReviewReasonCode` 对应文案，**不得隐藏入口**（与 §10.1 能力开关同一原则：隐藏会让人以为功能不存在） |
| `canReviewReasonCode` | `string \| null` | `notSpaceAdmin`（非该空间空间管理员，含平台管理员）/ `notPending`（已不在待审态）；`canReview=true` 时为 `null`。前端不认识的值降级为通用文案、不报错 |
| `canViewStagedFiles` | `boolean` | 当前用户能否浏览该项的暂存包文件。与 `canReview` 独立：作者本人两者皆 `true`（能看、也能批自己的包，批时页面有中性提示）；平台管理员两者皆 `false` |
| `isAuthor` | `boolean` | 当前用户是否就是本次提交的作者（`actor == submitted_by`）。**唯一用途是渲染那行中性提示「你是本次提交的作者」**，不参与任何拦截；与审计字段 `selfReview` 同一取值来源，避免前端和后端各算一次 |

服务端**必须**用同一个 `assertSpaceAdmin` + 同一份 `actor == submitted_by` 判定来计算这些字段与接口的实际校验，不允许"接口一套、字段一套"：字段只是把接口的判定结果提前告诉前端，**不是第二套规则**。

**通过后发布失败的恢复路径**（链路反转后唯一的新故障面，**只在 zip/folder 上存在**）：版本停在 `APPROVED`、审核结论不回滚、`POST .../publish {action: RETRY}` 由作者或管理员重试；连续失败超阈值（`publish_attempt >= 5` 或同一 `name` 24h 内失败 ≥3 次）触发 P1 告警。完整语义见 §4.6 附表。

审核页字段（前端必须取到，见 §10.2）：暂存包 `sha512` + 扫描指纹 + 报告时间/工具版本 + 版本与包名 + **固定文案「该版本尚未上架，通过后才会进入插件市场与索引」** + 四个只读能力字段（`canReview` / `canReviewReasonCode` / `canViewStagedFiles` / `isAuthor`）。**不得出现任何发布/代发状态字段**（硬规则 6）。

**提交入口可用性（能力开关，不吃分期常量）**——`PRD §6.1` 把这条写成了通用规则，因此服务端必须能表达"某空间此刻是否开放提交"，而不是让前端记住"P0 团队空间不能提交"：

| 判定层 | 规则 |
|---|---|
| 全局 | `SKILLHUB_PLUGIN_PUBLISH_ENABLED=false` → 所有空间 `available=false`、`reasonCode=publishDisabled`（运维止血） |
| 空间 | 空间侧 `pluginSubmitEnabled`（`GET /namespaces/{slug}` 既有响应加只读字段，本设计新增）；个人空间恒 `true` |
| 团队空间 | `pluginSubmitEnabled` 由 `SKILLHUB_PLUGIN_TEAM_SPACE_SUBMIT_ENABLED`（默认 `false`）决定，**P1 审核 + 代发重试同批就绪后打开这一个开关即开放，前端零改动**；为 `false` 时 `reasonCode=reviewNotReady` |
| 第三方来源 | zip/folder 在私服匿名只读自检未通过时 `available` 仍为 `true`（提交入口可用），差异在提交页的**来源级**灰态（§9.1），不混进这个开关 |

失败语义分开：`available=false` 是"能力未开放"（置灰 + 原因，**不是 404、不是 403**）；用户绕过前端直接 `POST /submissions` 时后端仍须 403 `plugin.error.uploadSourceDisabled`，并对团队空间返回同一 `reasonCode`。

除"能力开关"外还有一处"不要硬编码"：**三处入口行为必须一致**（`/plugins` 页头、`/dashboard/plugins` 页头、命名空间插件 tab 页头），不得一处隐藏一处置灰（§10.1）。

实现落点不新增类：`PluginSubmissionService.resolveCapability(currentUser)` 只读三处配置（全局开关 / 空间 `pluginSubmitEnabled` / 团队空间开关）并返回上面的结构，控制器直接转发；`POST /submissions` 在同步校验第一段复用同一方法，保证"入口置灰"与"接口 403"用的是同一判定（不会出现置灰了但接口能过，或反之）。

#### 7.2.1 详情页安装提示词：全站只有一个（`PRD §5.2` v2.15）

详情页 DTO 只下发**一个** `installPrompt`，句中两处版本（安装文档 URL 的 `{version}` 段 + 包名后的 `@version`）**必须相等，且等于构造该响应那一刻的最新已上架版本**：

| 字段 | 规则 |
|---|---|
| `latestVersion` | 只读，当前最新 `LISTED` 版本（按 `publish_timestamp` 取最大值）。页面上没有任何控件能改变它 |
| `installPrompt` | 服务端渲染好的整句：`请按照 {publicBaseUrl}/dsh/install/{ns}/{slug}/{version}.md 安装 DSH 插件 {packageName}@{version}。` 两处 `{version}` **由同一个变量拼两次**；前端不得拼、不得替换、不得二次模板化 |
| `versions[]` | **降为只读历史**：版本号 / 上架时间 / `engines.dsh` / 来源（只写 `npm` / `git`，**不展示内部主机名、registry 域名、仓库主机地址**，PRD §5.1 v2.19 ⑧）/ 是否已撤版 / 「最新」标记。**不显示「该版本裁决徽标」**（PRD §5.1 v2.19 ③）。**不含**逐版本提示词、**不含**逐版本复制或安装入口、**不含**安装文档路径字段。多给一个可复制版本，就等于让用户能装到一个"当前详情页没在卖"的版本，`PRD §5.2` 的"永远指向确定版本"当场失效 |
| 不做 | 不做 `VersionSelect`（下拉 / 单选 / 输入框都不做）、不接受 `?version=` 查询参数、不提供按版本的 `installPrompt`（§16 S19 有 DOM 断言 + 接口断言） |
| 不展示披露（PRD §1.1/§1.2/§5.1/§7 v2.20 ②） | 详情 DTO **不下发**裁决 / 能力清单 / 红线列表 / `文件:行号` 证据 / 字面量去向 / 扫描器版本与时间 / 风险摘要 / 免责说明等任何披露字段；卡片 / 列表 / 详情均无裁决徽标，安装文档不附摘要。**判定规则、四裁决、扫描契约、证据生成本身一概不变**——完整报告与证据仍在治理侧审核页（§4.6、§7.2 `GET /scan-reports/{id}`）与包内文件浏览（§4.7、§7.6）、以及提交者视图。扫描只作上架闸门 + 治理侧审核依据，用户侧本期不展示披露 |

**为什么渲染必须放服务端**：提示词里有两处版本必须一致。前端拼就会出现"URL 段刷新了、`@version` 还是旧值"这类中间态；服务端一个变量拼两次，天然一致，前端只做 `navigator.clipboard.writeText(resp.installPrompt)`。

**"复制那一刻的最新版本"怎么落地**：`installPrompt` **不进 60s 缓存**——详情页其他字段按 §10.3 的 `staleTime` 缓存，这一句必须 `no-store`；复制动作前若当前 query 的 `dataUpdatedAt` 已超过 60s，先 `refetch` 一次再复制。理由不是性能洁癖：把一个可能已撤版的版本号缓存一分钟，用户复制出来的提示词就会指向一个已经 404 的安装文档（§4.5 的撤版 404 规则），而他会以为是站点坏了。

**详情 DTO 还必须成对下发 `visibility` + `namespaceIsPublic`**（`PRD §8.4` v2.16，字段定义见 §7.3）：状态文案与"能不能看"都由这两个字段的组合决定，**前端不得按空间名或路由推断**；`NAMESPACE_ONLY` / `PRIVATE` 的插件即使版本全部 `LISTED` 也不进索引（收录前置条件见 §8.1）。只给其中一个必然渲染错，见 §16 S24。

#### 7.2.2 结构化错误码：每种同步校验失败都必须有稳定 `errorCode`（本次新增）

**总则（硬约束，不是建议）**：`POST /plugins/submissions`、`POST /plugins/{ns}/{slug}/versions/{v}/{yank,unyank,publish,rescan}`、五个审核接口、三个预览接口——**每一类"同步校验失败"都必须返回**：

1. **稳定 `errorCode`**：`SCREAMING_SNAKE` 常量，随 `error`（i18n key）一起返回。**`errorCode` 是契约，`message` 不是**——文案可以随时改、可以四语言不同，`errorCode` 一旦发布不得改名（要改就新增并在 §13 记录）。
2. **结构化 `details`**：机器可读的上下文字段（哪个字段、哪个值、哪个冲突对象、剩余额度、重试提示），前端据此决定**渲染什么控件**（如"跳到原空间"按钮、"改版本号"指引），而不是靠读文案。
3. **禁止前端按 `message` 分支**：不得出现 `message.includes('...')`、`serverMessage.startsWith(...)`、按错误字符串长度/子串推断分支的代码。**已有反面案例必须在本变更里清掉**：`web/src/features/publish/publish-error-utils.ts` 用 `VERSION_EXISTS_MARKERS` 做子串匹配（且含俄语字面量）来判断"版本已存在"——四语言任何一次文案修订都会让它静默失效，而失效表现是"提示语没变但按钮不出现"，最难排查的那类故障。插件侧**从第一行代码起就不许复制这个模式**（§16 S22 有静态门禁）。

响应体形状（统一，见 §7.3 `PluginError`）：

```json
{
  "error": "plugin.error.versionExists",
  "errorCode": "PLUGIN_CONFLICT_VERSION_EXISTS",
  "message": "该版本已存在，请先在上游 npm 发布新版本后重新提交",
  "details": { "packageName": "@team/bar", "version": "1.0.3", "sourceType": "npm", "existingStatus": "LISTED", "guidance": "BUMP_VERSION" }
}
```

**为什么值得单独设计（而不是"顺手加个字段"）**：① 本变更有 **6 族、30+ 种**同步失败，其中 Git 预检 4 类、npm 预检 4 类、唯一性 3 分支、包体 2 类，**产品要求逐类不同文案**（`PRD §6.4` v2.15："不许笼统报'拉取失败'"）——没有稳定码，前端只能靠文案分流，等于把产品文案变成接口契约；② 这些错误里有 5 类需要**不同的可自修动作**（改版本号 / 换包名 / 联系原空间 / 补 `package.json` / 换仓库结构），动作分派必须有机器可读依据；③ 异步终态的 `errorCode` 已经写在 `GET /submissions/{id}` 上（§7.4），同步侧若没有同级字段，前端就有两套错误处理路径。

**可见范围（`PRD §6.3` v2.16）的三处未定项——都不阻塞开发启动，但实现时按本节口径走**：① 三档默认值是否固定为 `PUBLIC`：当前**必选、无默认、服务端不兜底**（§14.2 Q10）；② 放宽方向：**已裁**（§14.2 Q11 关闭）——v1 不受理变宽，走 409 `visibilityWidenPending`，要更宽只能随新版本提交；③ 改可见范围的入口是否需要独立交互稿：当前只有上面那个窄端点，交互沿用「我的插件」/ 空间插件 tab 的行内操作（§14.2 Q12）。

