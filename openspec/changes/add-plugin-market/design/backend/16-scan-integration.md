# §9　scanner 契约、镜像、裁决规则、脱敏

> 所属：后端　｜　覆盖章节：§9
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

## 9. 扫描集成

### 9.1 契约：`POST /scan-plugin-upload`

```text
POST /scan-plugin-upload          (Content-Type: multipart/form-data)
  package  : 二进制，规范化 npm tarball（.tgz），必填
  name     : npm 包名，必填（与 package.json 一致，服务端复核）
  version  : 精确 SemVer，必填
  spec     : 来源标签，必填。npm/zip/folder → "npm:{name}@{version}"；git → "git+{url}#{commit}"

200 { "schemaVersion": 2, "profile": "", "dir": "<scanner 内临时目录名，非宿主机路径>",
      "generatedAt": "2026-11-25T10:00:00Z",
      "plugins": [ { "name": "...", "version": "...", "spec": "...",
                     "capabilities": [ ... ], "redLines": [ ... ],
                     "evidence": [ ... ], "destinations": [ ... ],
                     "pathEscapes": [ ... ], "secretTouches": [ ... ],
                     "score": 0, "band": "...", "summary": "..." } ],
      "errors": [ ... ] }

4xx/5xx { "error": "<稳定 code>", "detail": "<脱敏说明>" }
```

错误码：

| HTTP | code | 含义 | 后端处置 |
|---|---|---|---|
| 400 | `unpack_failed` | tarball 无法解包 / 结构不符（缺 `package/` 根） | 不可重试 → `VALIDATION_FAILED` |
| 422 | `invalid_package` | 无 `package.json`、`name`/`version` 与请求不符 | 不可重试 → `VALIDATION_FAILED` |
| 413 | `too_large` | 解包后超限（> 50 MiB 或 > 2000 文件） | 不可重试 → `VALIDATION_FAILED` |
| 503 | `busy` | 并发满或排队 > 30s | **可重试** → `retryLater` |
| 504 | `scan_timeout` | 120s 预算耗尽，子进程被杀 | **可重试**（最多 `maxAttempts`） |
| 500 | `scan_crashed` | 子进程异常退出且 stdout 无 JSON | **可重试**，连续两次即终态 |

### 9.2 scanner 镜像：Node 22 + 锁定 `dsh-trust-check`

```dockerfile
FROM python:3.11-slim-bookworm

ARG NODE_VERSION=22.11.0
ARG DSH_TRUST_CHECK_VERSION=0.2.1        # 精确锁定，禁 range，禁 latest
ARG NODE_SHA256=<构建期固定的官方 tarball 校验和>

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends curl xz-utils ca-certificates; \
    curl -fsSLO "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz"; \
    echo "${NODE_SHA256}  node-v${NODE_VERSION}-linux-x64.tar.xz" | sha256sum -c -; \
    tar -xJf "node-v${NODE_VERSION}-linux-x64.tar.xz" -C /usr/local --strip-components=1 --no-same-owner; \
    rm "node-v${NODE_VERSION}-linux-x64.tar.xz"; \
    npm install -g --ignore-scripts "dsh-trust-check@${DSH_TRUST_CHECK_VERSION}"; \
    npm cache clean --force; \
    apt-get purge -y curl xz-utils; apt-get autoremove -y; rm -rf /var/lib/apt/lists/*
```

约束：
1. **构建期固定版本与校验和**；运行期绝不 `npx`（供应链 + 可用性；版本策略见本设计 §9.2）。
2. `npm install -g --ignore-scripts`：安装工具本身不跑 postinstall。
3. 不在镜像里装 `git`、不带构建工具链；`node` 仅供 CLI 使用。
4. 保留 `USER app`（非 root），`/tmp/skillhub-scans` 与新增的 `/tmp/skillhub-plugin-scans` 由 `app` 拥有、0700。
5. 依赖来源走组织内镜像源 + 校验和；升级 = 改 `ARG` + 发布说明 + 全量重扫对比。

镜像体积/启动影响（写入发布说明）：Node 22 运行时约 +45–55 MB（解压后约 120 MB），镜像总体积预计从 ~220 MB 增至 ~280 MB（以构建实测为准）；启动时延不变（Node 只在请求内 spawn，`uvicorn` 启动路径没有变化）。

### 9.3 运行约束

| 约束 | 实现 |
|---|---|
| 非 root | 沿用 `USER app` |
| 无出网 | 部署侧 NetworkPolicy / compose 网络只连 server；扫描进程不发起任何网络请求。**不做"运行时探测出网再禁用"这种不可靠手段**，靠部署约束 |
| per-request 0700 临时目录 | `tempfile.mkdtemp(prefix="plugin-scan-", dir=/tmp/skillhub-plugin-scans, mode=0o700)`，`finally` 中 `shutil.rmtree(ignore_errors=True)` |
| 不执行包内脚本 | 只 `tarfile` 解包 + 读文件；不 `npm install`、不 `npm pack`、不 `node` 执行包内代码 |
| 单次预算 120s | `subprocess.run(..., timeout=120)`；超时 `kill()` 后返回 504 |
| 服务端 130s 强杀 | 外层 `asyncio.wait_for(130)`；超时记 `scan_crashed` 并不重启容器（与技能侧 `os._exit(124)` 的区别：插件扫描是子进程，可精确 kill） |
| 并发 1 | 复用 `SKILLHUB_SCANNER_MAX_CONCURRENT_SCANS`，但插件扫描有**独立信号量**，避免与技能扫描互相饿死 |
| 排队 30s 返 503 | 独立信号量上做 `asyncio.wait_for(acquire, 30)` 语义；拿不到即 503 + `Retry-After: 30` |
| 输出脱敏 | 复用 `_redact_supported_tokens` 对 `redLines[].evidence`、`destinations[]`、`errors[]` 递归脱敏；`dir` 字段改写为容器内相对目录名，不暴露宿主机路径 |

**与技能扫描的硬边界**（`PRD §7.5`）：

| 维度 | 技能（`skill-scanner`） | 插件（`dsh-trust-check`） |
|---|---|---|
| 结论语义 | `severity` / `findings` / `isSafe` | `capabilities` / `redLines` / `verdict` |
| 数据表 | `security_audit`、`skill_version` 冗余列 | `plugin_scan_report`、`plugin_version` 冗余列 |
| 状态机 | `SCANNING` → `SCAN_FAILED`（可重试后置） | 提交状态机，扫描失败即回滚提交 |
| 闸门位置 | 发布流程内的旁路步骤 | 代发私服之前的硬闸门 |
| 复用 | 容器形态、并发闸、超时框架、脱敏工具 | 同左 |

禁止：把 `isSafe` 口径用于插件；把 `capabilities` 写进 `security_audit`；让插件版本进入技能的 `SCANNING`/`SCAN_FAILED` 状态机。

### 9.4 裁决规则（fail closed）

```text
verdict(report):
  if report.errors is not empty            -> ERROR
  if report.schemaVersion not in {2}       -> ERROR   (可读集合固定为 {2})
  if report.plugins is empty               -> ERROR
  p = report.plugins[0]
  if p.capabilities missing or p.redLines missing -> ERROR   (硬依赖五字段)
  if p.redLines is not empty               -> RED
  if p.capabilities is not empty
     or p.patch has any override/disable   -> REVIEW
  else                                     -> CLEAR
```

| 裁决 | 含义 | 本站处置 | 页面表述 |
|---|---|---|---|
| `RED` | `redLines` 非空 | 拒绝（不写私服、不落版本）；管理员可"确认风险"放行 | 有红线 |
| `REVIEW` | 无红线但有特权能力/注入 | 允许继续；详情页必须展示能力清单 | 需确认 |
| `CLEAR` | 无红线无特权能力 | 允许继续；详情页**仍**展示披露区块 | 未检出风险信号 |
| `ERROR` | `errors` 非空 / schema 未知 / 超时 / 进程失败 / 字段缺失 | fail closed，可重试；绝不按 `clear` 处理 | 扫描未完成 |

- 只看 `redLines` + `capabilities`（等价工具自身的 `verdict()`），**不看** `score`/`band`。
- 未知 capability 值、`classifyRedLine()` 归到 `raw` 的红线：原样展示英文，不报错（工具承诺只增不减）。
- 红线句子按 `classifyRedLine()` 的 **code** 做 i18n，不用英文整句匹配。
- `plaintext-http` 与 `literal-ip` 每份报告最多各一条，展示时不得暗示"共 N 个外连地址"。

### 9.5 报告解析顺序与可读 `schemaVersion`

固定解析顺序（任一步失败即 `ERROR`，不走后续步骤）：

1. `stdout` 能被严格 JSON 解析；否则 `scan_crashed`。
2. `schemaVersion` 是整数且属于可读集合 `{2}`；否则 `scanInvalidReport` + 告警（不 fail open）。
3. `errors` 是数组（缺失视为 `ERROR`）。
4. `plugins` 是非空数组。
5. `plugins[0]` 含硬依赖五字段：`name` / `version` / `spec` / `capabilities` / `redLines`。
6. 读展示字段（`evidence` / `destinations` / `pathEscapes` / `secretTouches` / `score` / `summary`），缺失不报错。

`schemaVersion` 的变更必须与解析器在同一个 PR 内改；解析器维护常量 `SUPPORTED_SCHEMA_VERSIONS = {2}`。

### 9.6 脱敏规则

| 位置 | 规则 |
|---|---|
| scanner `stderr` | 只进容器日志；按 `_STATUS_*` 系列正则脱敏（Token、URL userinfo、query secret、Bearer、JWT、私钥） |
| 报告 `evidence[].snippet` | 截断至 200 字符；同样过脱敏正则 |
| `destinations[]` | 只保留 host + port，去掉 path/query/userinfo |
| 后端 `error_detail` | 只存稳定 code + 首行说明，≤ 2000 字符；不含 URL userinfo、不含 token |
| 索引与安装文档 | **零**扫描字段、零凭据（生成期断言 + 单测） |
| 审计 `detailJson` | 含版本、`redLine` code、`fingerprint`、操作人、理由；不含证据片段原文 |

---

