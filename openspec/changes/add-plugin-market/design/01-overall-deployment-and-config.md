# §11　环境变量、nginx、compose/Helm、启动自检、运行时开关

> 所属：总体设计　｜　覆盖章节：§11
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`README.md`](README.md)。

## 11. 配置与部署变更

### 11.1 新增环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `SKILLHUB_NPM_REGISTRY_URL` | 空 | 私服地址（读写共用）。zip/folder 代发与 npm 来源解析都用它。**不得包含 userinfo** |
| `SKILLHUB_NPM_REGISTRY_TOKEN` | 空 | 写凭据（仅 server 持有）；不落日志、不进索引/文档 |
| `SKILLHUB_NPM_REGISTRY_ANONYMOUS_READ` | `true` | 部署前置断言：为 `true` 时启动自检匿名读；为 `false` 则禁用 zip/folder 来源 |
| `SKILLHUB_PLUGIN_GIT_ALLOWED_HOSTS` | 空 | 逗号分隔的 Git host 白名单（只写 host[:port]，不写 scheme）。**为空即禁用 Git 来源**（fail closed）：避免"任意内网地址"成为 SSRF 面 |
| `SKILLHUB_PLUGIN_GIT_ALLOWED_PATH_PREFIXES` | 空 | 可选，形如 `git.corp:2222=/team/,/platform/`；为空表示该 host 任意 path 都允许（仍受 host 白名单约束） |
| `SKILLHUB_PLUGIN_GIT_TRANSPORT` | `ssh` | **默认 `ssh`**。v1 只交付 SSH 实现；配成 `https` 时**启动失败并明确提示"本期未实现"**，不静默回退（产品口径 `skillhub.plugin.git.transport`） |
| `SKILLHUB_PLUGIN_GIT_URL_TEMPLATES` | 空 | **地址归一化唯一来源**（产品口径 `skillhub.plugin.git.url-mapping`）。分号分隔多条 `host[:port]=ssh 模板`，模板支持 `{host}`/`{path}` 占位。**不在代码里猜协议**，没配匹配项就原样拒绝（提示联系管理员），不隐式转换。样例（非 22 端口 + 多级 group）：`git.corp=ssh://git@git.corp:2222/{path}`，则 `https://git.corp/group/sub/repo` 与 `git@git.corp:group/sub/repo.git` 都归一为 `ssh://git@git.corp:2222/group/sub/repo.git` |
| `SKILLHUB_PLUGIN_GIT_SSH_USER` | `git` | SSH 用户名（服务账号），与私钥配套 |
| `SKILLHUB_PLUGIN_GIT_SSH_KEY_PATH` | 空 | 部署私钥文件路径（`0600`，secret 挂载）。**不得用环境变量传私钥内容**；为空即禁用 Git 来源（fail closed） |
| `SKILLHUB_PLUGIN_GIT_SSH_KEY_PATH_NEXT` | 空 | 轮换用的第二把私钥；与主钥并存一个窗口，主钥失败回退它，换钥不需要停机 |
| `SKILLHUB_PLUGIN_GIT_KNOWN_HOSTS` | 空 | `known_hosts` 文件路径（host key pin，方案 A） |
| `SKILLHUB_PLUGIN_GIT_HOST_KEY_PINS` | 空 | 直接给指纹（host key pin，方案 B）：`git.corp:2222=ssh-ed25519 AAAA...`。与 `KNOWN_HOSTS` **二选一**；两者都空 → 启动失败（不提供跳过校验的开关） |
| `SKILLHUB_PLUGIN_MARKET_ENABLED` | `true` | `/dsh/*` 与市场页总开关；默认开启，仅用于故障时一键止血 |
| `SKILLHUB_PLUGIN_PUBLISH_ENABLED` | `true` | 提交入口总开关；默认开启。zip/folder 是否可用由启动自检自动裁决，不靠人工拨开关；**扫描闸门与代发必须同批上线**。关闭时入口**置灰 + 说明**（`reasonCode=publishDisabled`），不隐藏 |
| `SKILLHUB_PLUGIN_TEAM_SPACE_SUBMIT_ENABLED` | `false` | **团队空间**提交能力。默认 `false`：P0 只开放个人空间，团队空间入口置灰并说明"审核将在治理阶段开放"（`PRD §12` P0 行原文）。P1 审核 + 撤包同批就绪后翻 `true` 即开放，**前端零改动**（能力值经 `GET /api/v1/plugins/submit-capabilities` 下发，§7.2）。个人空间不受此开关影响 |
| `SKILLHUB_PLUGIN_SUBMISSION_WORKER_ENABLED` | `true` | worker 开关（多副本可只在一处关，但通常全开） |
| `SKILLHUB_PLUGIN_SUBMISSION_INTERVAL_MS` | `5000` | 与 `ScanTaskOutboxDispatcher` 的 `dispatch-interval-ms` 一致 |
| `SKILLHUB_PLUGIN_SUBMISSION_BATCH_SIZE` | `20` | 单轮条数 |
| `SKILLHUB_PLUGIN_SUBMISSION_LEASE` | `PT5M` | 覆盖"下载 + 扫描 120s + 代发"的最长路径 |
| `SKILLHUB_PLUGIN_SUBMISSION_MAX_ATTEMPTS` | `5` | 重试上限 |
| `SKILLHUB_PLUGIN_PUBLISH_MAX_ATTEMPTS` | `5` | 代发重试上限（含审核通过后的代发）；耗尽即停在 `APPROVED` 并告警。这个值只决定『什么时候停』，不决定『能不能手动重试』——手动重试永远可用 |
| `SKILLHUB_PLUGIN_PUBLISH_ALERT_THRESHOLD` | `3` | 同一 `name` 在 24 小时内累计代发失败达此次数即 P1（哪怕单版本还没到 `maxAttempts`） |
| `SKILLHUB_PLUGIN_PUBLISH_RETENTION_DAYS` | `90` | 审核拒绝 / 代发失败后暂存包的保留天数（追溯用），到期由清理任务删对象 |
| `SKILLHUB_PLUGIN_FILE_BROWSE_ENABLED` | `true` | 审核页「包内文件」总开关（止血用）。`false` 时三个端点一律 503 `fileBrowseDisabled`，审核页显示「暂不可浏览」但**不隐藏入口**——隐藏会让人以为功能不存在，然后去打人工要包 |
| `SKILLHUB_PLUGIN_FILE_MAX_ENTRIES` | `2000` | 单包条目数上限（超限 422，**不截断**，§7.6.2） |
| `SKILLHUB_PLUGIN_FILE_MAX_UNCOMPRESSED_BYTES` | `209715200` | 整包解压总量上限（200 MiB = 上传 50 MiB × 典型压缩比 4） |
| `SKILLHUB_PLUGIN_FILE_MAX_ENTRY_BYTES` | `8388608` | 单文件可下载上限（8 MiB），覆盖 wasm / 字体 / 未压缩 bundle |
| `SKILLHUB_PLUGIN_FILE_MAX_TEXT_BYTES` | `524288` | 文本内联预览上限（512 KiB ≈ 8000 行），超限 413 引导下载 |
| `SKILLHUB_PLUGIN_FILE_MAX_RATIO` | `10` | 整包压缩比阈值；单条目 > 100:1 且解压 > 4 MiB 另判炸弹条目 |
| `SKILLHUB_PLUGIN_FILE_INDEX_TIMEOUT` | `PT5S` | 清单请求预算（首次含完整遍历，缓存命中应 < 100ms） |
| `SKILLHUB_PLUGIN_FILE_READ_TIMEOUT` | `PT10S` | 单文件请求预算（顺序解压到目标条目） |
| `SKILLHUB_PLUGIN_FILE_INDEX_CACHE_TTL` | `PT10M` | 清单缓存 TTL，键 = `submissionId + tarball sha512`（进程内，与 `PluginIndexReadCache` 同构） |
| `SKILLHUB_PLUGIN_FILE_INDEX_CACHE_MAX` | `200` | 最多缓存的提交数，按 LRU 淘汰；200 个清单 ≈ 几 MB |
| `SKILLHUB_PLUGIN_INDEX_WORKER_ENABLED` | `true` | — |
| `SKILLHUB_PLUGIN_INDEX_INTERVAL_MS` | `10000` | 索引重建轮询间隔 |
| `SKILLHUB_PLUGIN_INDEX_LEASE` | `PT2M` | — |
| `SKILLHUB_PLUGIN_INDEX_CACHE_TTL` | `PT60S` | 与 `Cache-Control: max-age=60` 对齐 |
| `SKILLHUB_PLUGIN_INDEX_MAX_BYTES` | `33554432` | 32 MiB 生成期上限 |
| `SKILLHUB_PLUGIN_SOURCE_PROBE_TIMEOUT` | `PT2S` | 单次分发来源探测超时（连接 `PT1S`）。**超时按构建失败处理，绝不降级到 git**（§8.1.1 红线） |
| `SKILLHUB_PLUGIN_SOURCE_PROBE_BUDGET` | `PT20S` | 单次构建内全部探测的总预算；超预算同样构建失败并保留上一版索引 |
| `SKILLHUB_PLUGIN_SOURCE_PROBE_CONCURRENCY` | `8` | 探测有界并发；单构建内每个 `name@version` 只探一次（记忆化，不跨构建缓存） |
| `SKILLHUB_PLUGIN_SCAN_TIMEOUT` | `PT120S` | 单次扫描预算（scanner 侧同值） |
| `SKILLHUB_PLUGIN_SCAN_QUEUE_TIMEOUT` | `PT30S` | scanner 排队上限，超时 503 |
| `SKILLHUB_PLUGIN_STATS_DEDUP_SECRET` | 随机生成 | `dedup_key` 的 HMAC 密钥；缺失时统计去重降级为"仅日期 + 插件"，并在健康检查里报 WARN |
| `SKILLHUB_SECURITY_SCANNER_URL` | 既有 | 复用同一 scanner 地址 |

`SKILLHUB_PUBLIC_BASE_URL` 与 `SKILLHUB_WEB_BASE_PATH` 沿用既有含义：前者是绝对 URL 前缀（含 scheme/host/可选前缀），后者是 SPA 的 base path。`PluginPublicUrlBuilder` 规则：

```
publicBaseUrl = SKILLHUB_PUBLIC_BASE_URL  去掉尾部 '/'
indexUrl      = publicBaseUrl + "/dsh/index.json"
installDocUrl = publicBaseUrl + "/dsh/install/{ns}/{slug}/{version}.md"    // 索引里每行按版本生成
installDocAliasUrl = publicBaseUrl + "/dsh/install/{ns}/{slug}.md"          // 老路径，语义为"最新 LISTED 版本"
homepage      = publicBaseUrl + "/plugins/{ns}/{slug}"
iconPath      = "icons/{pluginKey}.{ext}"                                   // 索引内相对路径，不含前导 '/'
```

`installDocUrl` 一律带版本段：索引 `plugins[]` 里给的是"这个版本"的安装文档，而"复制哪个版本就装哪个版本"是产品侧已经定死的验收口径，无版本段的路径表达不了它。`installDocAliasUrl` 只作为兼容入口保留（v1 之前放出过的老链接、以及用户手写），**直接 200 返回最新 `LISTED` 版本的内容，不做 302**——`/dsh/*` 的硬约束是"禁止重定向"（D§4.5、D§7.1）。

`SKILLHUB_PUBLIC_BASE_URL` 为空时：`/dsh/index.json` 仍可匿名读取（DSH 拿到的是相对 `icons/` 图标），但**索引生成失败**（无法产出绝对 `homepage`）。这是刻意的：宁可不出索引，也不产出一份让 DSH 点不进去的半残索引。

### 11.2 nginx 变更

```nginx
# web/nginx.conf.template —— 新增。必须用 ^~ 前缀匹配，优先级高于正则与 SPA fallback。
location ^~ /dsh/ {
    proxy_pass $skillhub_api_upstream;
    proxy_set_header Host $skillhub_forwarded_host;
    proxy_set_header Forwarded "";
    proxy_set_header X-Forwarded-Host $skillhub_forwarded_host;
    proxy_set_header X-Forwarded-Port "";
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $proxy_x_forwarded_proto;
    proxy_set_header X-Forwarded-Prefix $skillhub_forwarded_prefix;
    proxy_set_header Cookie "";          # 索引与文档不依赖会话；不转发 Cookie 避免凭据外溢到日志
    proxy_read_timeout 30s;
    # 不做任何 rewrite / return 30x —— 官方协议禁止重定向
}
```

注意：
- 现有 `location /` 的 `try_files $uri $uri/ /index.html` 会吞掉 `/dsh/index.json`（返回 HTML 的 200），这是最容易漏的回归点，必须加 nginx 配置测试。
- 子路径部署时 `20-base-path.sh` 会生成 `skillhub-base-path*.conf` 设置 `$skillhub_forwarded_prefix`；`/dsh/` 的 location 必须转发该头，否则后端无法生成带前缀的绝对 URL。
- `/dsh/` 与 `/api/` 一样属于"直连后端"的前缀，不进 `location /` 的缓存头策略（`no-cache, must-revalidate` 会与我们的 `max-age=60` 冲突）。

### 11.3 compose 与 Helm values

compose（`docker-compose.yml` / `compose.release.yml`）在 server 服务下新增：

```yaml
environment:
  SKILLHUB_PUBLIC_BASE_URL: ${SKILLHUB_PUBLIC_BASE_URL:-}
  SKILLHUB_NPM_REGISTRY_URL: ${SKILLHUB_NPM_REGISTRY_URL:-}
  SKILLHUB_NPM_REGISTRY_TOKEN: ${SKILLHUB_NPM_REGISTRY_TOKEN:-}
  SKILLHUB_NPM_REGISTRY_ANONYMOUS_READ: "true"
  SKILLHUB_PLUGIN_MARKET_ENABLED: "true"
  SKILLHUB_PLUGIN_PUBLISH_ENABLED: "true"
  SKILLHUB_PLUGIN_TEAM_SPACE_SUBMIT_ENABLED: "false"   # P1 审核+代发重试同批就绪后改 true
  SKILLHUB_PLUGIN_GIT_READ_TOKEN: ${SKILLHUB_PLUGIN_GIT_READ_TOKEN:-}
  SKILLHUB_PLUGIN_GIT_ALLOWED_HOSTS: ${SKILLHUB_PLUGIN_GIT_ALLOWED_HOSTS:-}
  SKILLHUB_PLUGIN_GIT_TRANSPORT: ${SKILLHUB_PLUGIN_GIT_TRANSPORT:-ssh}
  SKILLHUB_PLUGIN_GIT_URL_TEMPLATES: ${SKILLHUB_PLUGIN_GIT_URL_TEMPLATES:-}
  SKILLHUB_PLUGIN_GIT_SSH_USER: ${SKILLHUB_PLUGIN_GIT_SSH_USER:-git}
  SKILLHUB_PLUGIN_GIT_SSH_KEY_PATH: /run/secrets/git_deploy_key
  SKILLHUB_PLUGIN_GIT_KNOWN_HOSTS: /etc/git/known_hosts
  # 私钥与 known_hosts 都走 secret/ConfigMap 文件挂载，不写进环境变量（见 §3.4.1）
```

scanner 服务新增：

```yaml
environment:
  SKILLHUB_SCANNER_PLUGIN_MAX_CONCURRENT_SCANS: "1"
  SKILLHUB_SCANNER_PLUGIN_SCAN_TIMEOUT_SECONDS: "120"
  SKILLHUB_SCANNER_PLUGIN_QUEUE_TIMEOUT_SECONDS: "30"
```

Helm（`deploy/k8s/base` + overlays）：
- `pluginMarket.*` values 块映射上述变量；`registryToken` / `gitReadToken` 走 Secret（不进 ConfigMap）。
- `scanner` Deployment 的资源请求上调（Node 22 常驻内存虽小，但 tar 解包 + CLI 启动峰值需 headroom）：建议 requests `cpu 200m / mem 512Mi`，limits `cpu 1000m / mem 1Gi`。
- 不为 `/dsh/` 新增 Ingress path。

### 11.4 启动自检

`PluginRegistrySelfCheck`（`ApplicationReadyEvent`，异步、不阻塞启动）：

1. `GET {registry}/`（或 `/-/ping`）可达性；不可达 → `uploadSourcesEnabled=false`，WARN。
2. 无 token 情况下 `GET {registry}/{knownPackage}` 与 tarball 下载；401/403 → `uploadSourcesEnabled=false`，WARN「私服未开放匿名只读，zip/文件夹来源已禁用」。
3. token 存在时 `GET {registry}/-/whoami`（若私服支持）；403 → `registryWritable=false`，`uploadSourcesEnabled=false`。
4. Git 白名单为空 → `gitSourceEnabled=false`。
5. 结果通过 `GET /api/v1/plugins/market` 暴露（`uploadSourcesEnabled`）并注册为 actuator health indicator（`pluginRegistry`），不参与 readiness（避免因外部依赖不可达而摘除全部 pod）。

**绝不**在自检里做真实 publish（会污染私服并占用 `name@version`）。

### 11.5 运行时开关与故障回退（非灰度）

本项目是全新域：没有存量用户要放量、没有旧索引要双读、没有历史数据要回填，**因此不设灰度发布**，也不存在"部分用户可见新功能"的中间态。下列开关只用于故障止血与发布顺序保护，默认全开。

| 开关 | 关闭后的行为 |
|---|---|
| `SKILLHUB_PLUGIN_MARKET_ENABLED=false` | `/dsh/*` 返回 503；市场页显示"索引暂不可用"；导航项保留（避免 404 让用户以为站点坏了） |
| `SKILLHUB_PLUGIN_PUBLISH_ENABLED=false` | 提交入口**置灰并说明**（`plugin.submit.capability.disabled.publishDisabled`，不隐藏——隐藏会让人以为功能没了）；`POST /submissions` 返回 503 `plugin.error.uploadSourceDisabled`；既有已上架插件不受影响 |
| `SKILLHUB_PLUGIN_TEAM_SPACE_SUBMIT_ENABLED=false` | 仅团队空间入口置灰并说明"审核将在治理阶段开放"；个人空间照常可提交；直接 `POST /submissions` 指定团队空间返回 403 `plugin.error.uploadSourceDisabled` |
| `SKILLHUB_PLUGIN_INDEX_WORKER_ENABLED=false` | 停止重建；`/dsh/*` 继续吐上一版（可用性优先） |
| `SKILLHUB_PLUGIN_SUBMISSION_WORKER_ENABLED=false` | 停止处理；在途 submission 停在原状态，恢复后按 lease 过期重新认领 |
| 指针回退 | 把 `dsh/index/current.json` 覆盖为 `{... , "key": "<上一版 key>", "gen": <上一 gen>}`；后端缓存 TTL 后自动生效（≤60s），不用重启 |

**禁止**的开关：任何"扫描失败即放行"的旁路（闸门规则见 `PRD §8.1`）。可用性靠队列与重试解决，不靠降低闸门。

### 11.6 索引协议变更应对（`PRD §13` R1）

- 生成器版本化：`PluginIndexAssembler` 内 `GENERATOR_VERSION` 常量随 schema 变更递增，写入日志与指标标签（不写进索引，协议无此字段）。
- 协议大改（如 `format: 2`）：新增 `PluginIndexAssemblerV2` + 新路径（如 `/dsh/v2/index.json`），**不原地改语义**；旧路径继续服务旧格式。
- CI 中固定一份"协议金样"（golden file），校验库升级时对比差异并在发布说明中列出。

---

