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
      parameters:
        - { name: q, in: query, schema: { type: string, maxLength: 200 } }
        - { name: category, in: query, schema: { $ref: '#/components/schemas/PluginCategory' } }
        - { name: sort, in: query, schema: { type: string, enum: [newest, name], default: newest } }
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
      description: 市场页模块一所需的运行时信息；索引源地址由后端生成，前端不拼接
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

