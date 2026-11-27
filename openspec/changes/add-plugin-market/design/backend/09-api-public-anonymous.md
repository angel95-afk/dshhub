# §7.1　匿名公开接口（索引、安装文档、市场页）

> 所属：后端　｜　覆盖章节：§7.1
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

## 7. 接口契约

### 7.1 公开（匿名）

```yaml
openapi: 3.1.0
info: { title: SkillHub Plugin Market - Public, version: 1.0.0 }
paths:
  /api/v1/plugins:
    get:
      security: []
      description: '"仅看收藏"为前端本地过滤，不新增后端参数（PRD §4.2 v2.20 ④）；sort 取 copyCount 时按 plugin.prompt_copy_count 倒序（三处同源计数，PRD §10）'
      parameters:
        - { name: q, in: query, schema: { type: string, maxLength: 200 } }
        - { name: category, in: query, schema: { $ref: '#/components/schemas/PluginCategory' } }
        - { name: sort, in: query, schema: { type: string, enum: [newest, name, copyCount], default: newest } }
        - { name: page, in: query, schema: { type: integer, minimum: 0, default: 0 } }
        - { name: size, in: query, schema: { type: integer, minimum: 1, maximum: 48, default: 24 } }
      responses:
        '200': { $ref: '#/components/responses/PluginPage' }
        '429': { $ref: '#/components/responses/RateLimited' }

  /api/v1/plugins/{namespace}/{slug}:
    get:
      security: []
      parameters: [{ $ref: '#/components/parameters/Namespace' }, { $ref: '#/components/parameters/Slug' }]
      responses:
        '200': { $ref: '#/components/responses/PluginDetail' }
        '403': { description: 插件属于私有空间且请求者非成员——**不泄漏存在性，一律拒绝访问**（`PRD §8.4`；不返回空列表、不渲染空态） }
        '404': { description: 不存在 / 未上架 / 该版本尚不可安装（`SCAN_PASSED` 待审、`APPROVED` 已通过待代发、`PUBLISHING` 代发中）。与 403 是**两套语义，不合并**：私有空间是"你看不了"，未上架是"没有这个东西" }

  /api/v1/plugins/{namespace}/{slug}/versions:
    get:
      security: []
      parameters: [{ $ref: '#/components/parameters/Namespace' }, { $ref: '#/components/parameters/Slug' }]
      responses:
        '200': { $ref: '#/components/responses/PluginVersionList' }

  /api/v1/plugins/{namespace}/{slug}/versions/{version}/security-scan:
    get:
      security: []
      description: >-
        **v2.20 ② 起本端点不再返回任何用户侧披露字段**（产品目标 4 删除、用户侧本期不展示能力/红线/裁决/摘要/免责）：
        详情页、市场卡片、安装文档均不渲染扫描结论，此端点不下发裁决、能力/红线 code、`文件:行号` 证据片段、扫描器版本/时间与任何摘要文案。
        扫描结论只作上架闸门 + 治理侧审核依据；完整报告与证据只在治理侧（需登录 `GET /api/v1/plugins/scan-reports/{id}` + 包内文件浏览，§7.2/§7.6）。
        判定规则与四裁决本身不变，只改"证据展示在哪一侧"、用户侧本期不展示。
      parameters:
        - { $ref: '#/components/parameters/Namespace' }
        - { $ref: '#/components/parameters/Slug' }
        - { name: version, in: path, required: true, schema: { type: string } }
      responses:
        '200': { $ref: '#/components/responses/PluginScanSummary' }
        '404': { description: 无成功报告（含扫描失败尚未重试的场景） }

  /api/v1/plugins/market:
    get:
      security: []
      description: 市场页「索引源」段所需的运行时信息（PRD §4 v2.19 ⑦：上下平铺两段、无模块概念）；索引源地址由后端生成，前端不拼接
      responses:
        '200':
          content:
            application/json:
              schema:
                type: object
                required: [indexUrl, installIndexDocUrl, enabled, uploadSourcesEnabled, indexState]
                properties:
                  indexUrl: { type: string, format: uri }
                  installIndexDocUrl: { type: string, format: uri }
                  enabled: { type: boolean }
                  uploadSourcesEnabled: { type: boolean, description: 私服匿名只读自检是否通过；false 时禁用 zip/文件夹来源 }
                  indexState:
                    type: object
                    properties:
                      dirty: { type: boolean }
                      gen: { type: integer }
                      lastBuiltAt: { type: string, format: date-time }
                      pluginCount: { type: integer }

  /api/v1/plugins/{namespace}/{slug}/stats:
    post:
      security: []
      description: 上报 view / copy_prompt；按 pluginId + event + IP HMAC + UTC 日期去重
      parameters: [{ $ref: '#/components/parameters/Namespace' }, { $ref: '#/components/parameters/Slug' }]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [event]
              properties:
                event: { type: string, enum: [view, copy_prompt] }
                version: { type: string, description: 可选，copy_prompt 时记录版本 }
      responses:
        '202': { description: 已受理（去重后是否计入不回传，避免探测） }
        '429': { $ref: '#/components/responses/RateLimited' }

  /dsh/index.json:
    get:
      security: []
      description: format 1 索引；匿名、无重定向；ETag/304
      responses:
        '200':
          headers:
            Cache-Control: { schema: { type: string }, example: 'public, max-age=60' }
            ETag: { schema: { type: string }, example: '"dsh-gen-42"' }
          content:
            application/json:
              schema: { $ref: '#/components/schemas/DshIndexDocument' }
        '304': { description: If-None-Match 命中 }
        '503': { description: 市场开关关闭（SKILLHUB_PLUGIN_MARKET_ENABLED=false） }

  /dsh/icons/{key}:
    get:
      security: []
      parameters: [{ name: key, in: path, required: true, schema: { type: string, pattern: '^[a-f0-9]{16}(\.(svg|png|jpg|webp))?$' } }]
      responses:
        '200':
          headers:
            Cache-Control: { schema: { type: string }, example: 'public, max-age=86400' }
          content:
            image/svg+xml: { schema: { type: string, format: binary } }
            image/png: { schema: { type: string, format: binary } }
        '404': { description: 无图标（前端用首字母占位） }

  /dsh/install/index.md:
    get:
      security: []
      responses:
        '200': { $ref: '#/components/responses/MarkdownDoc' }

  /dsh/install/{namespace}/{slug}/{version}.md:
    get:
      security: []
      description: 精确版本的安装文档；版本必须属于该插件且 status=LISTED
      parameters:
        - { $ref: '#/components/parameters/Namespace' }
        - { $ref: '#/components/parameters/Slug' }
        - { name: version, in: path, required: true, schema: { type: string } }
      responses:
        '200': { $ref: '#/components/responses/MarkdownDoc' }
        '404': { description: 不存在 / 该版本未上架 / 私有空间（统一 404，不用 403） }

  /dsh/install/{namespace}/{slug}.md:
    get:
      security: []
      description: 旧路径别名，等价于"最新 LISTED 版本"；直接 200 返回最新内容，不做 302
      parameters: [{ $ref: '#/components/parameters/Namespace' }, { $ref: '#/components/parameters/Slug' }]
      responses:
        '200': { $ref: '#/components/responses/MarkdownDoc' }
        '404': { description: 不存在 / 未上架 / 私有空间（统一 404，不用 403） }
```

