# §3.3–§3.5　Controller 清单、适配器接口签名（先冻结）、复用与独立的边界

> 所属：后端　｜　覆盖章节：§3.3–§3.5
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

### 3.3 Controller 清单

| Controller | 路径前缀 | 说明 |
|---|---|---|
| `PluginController` | `/api/v1/plugins/**` | 目录列表、详情、版本列表、分类修改、上下架、撤版/恢复、软删、释放包名、**代发重试**、统计上报 |
| `PluginSubmissionController` | `/api/v1/plugins/submissions/**`、`/api/v1/plugins/submit-capabilities` | 提交、提交状态轮询、**提交入口可用性**（`PRD §6.1` 能力开关，前端唯一判定来源，§7.2） |
| `PluginSubmissionFileController` | `/api/v1/plugins/submissions/{id}/files`、`.../files/content`、`.../files/download` | **审核用包内文件只读浏览**（清单/单文件/下载）。独立成类而不是塞进 `PluginSubmissionController`：这一组端点的响应头、审计与限流都与提交轮询完全不同，混在一个类里最容易在改一处时把 `text/html` 漏出去（§7.2、§7.6） |
| `PluginIndexController` | `/dsh/**` | `index.json`、`icons/**`、`install/**.md` |
| `PluginSecurityScanController` | `/api/v1/plugins/scan-reports/**`、`.../versions/{v}/security-scan`、`.../rescan`、`.../waiver` | 披露、完整报告、重扫、放行 |
| `PluginDashboardController` | `/api/v1/dashboard/plugins`、`/api/v1/dashboard/namespaces/{slug}/plugins` | 我的插件、空间维度列表 |
| `PluginReportController` | `/api/v1/plugins/{ns}/{slug}/report`、`/api/v1/plugins/reports` | 举报提交（登录用户）+ 举报**只读**列表（`SKILL_ADMIN`/`SUPER_ADMIN`）；**本期无处置端点、无状态流转**（§7.2、§13 #15） |

### 3.4 适配器接口签名（先冻结）

```java
// domain/plugin/port/NpmRegistryPort.java
public interface NpmRegistryPort {
    // 带版本的底层能力保留给内部用（如按已冻结快照重取同一版本）；用户提交路径绝不传版本。
    NpmPackageMetadata resolveMetadata(String packageName, String versionOrDistTag); // versionOrDistTag=null → 取 latest dist-tag 且跳过 prerelease
    // 用户路径唯一入口：等价于 resolveMetadata(packageName, null)。
    default NpmPackageMetadata resolveLatest(String packageName) { return resolveMetadata(packageName, null); }
    TarballRef downloadTarball(String packageName, String version, Path target, long maxBytes);
    void publish(Path tarball, String packageName, String version);                   // 只写元数据 + tarball，不跑包内脚本
    RegistryProbe probeAnonymousRead();                                              // 启动自检：匿名能否 GET tarball
}

// domain/plugin/port/GitSourceResolver.java
public interface GitSourceResolver {
    GitTransport transport();                                          // 配置默认 ssh；v1 只装配 SSH_KEY，HTTPS_TOKEN 只留空实现
    GitResolvedRef resolve(GitCloneUrl url);                           // ls-remote --symref → 默认分支 + 其 HEAD commit
    Path exportTree(GitCloneUrl url, GitResolvedRef ref, Path target, Duration budget);
    GitPreflightResult preflight(GitCloneUrl url);                     // ls-remote 级别，不克隆
}

// domain/plugin/GitCloneUrl.java —— 规范化后的克隆地址与原始输入分离，鉴权方式由它自身携带
public record GitCloneUrl(String scheme, String host, int port, String path, String canonicalUrl) {
    static GitCloneUrl parse(String userInput, GitUrlMapping mapping);      // 接受 https://host/p.git 与 git@host:p.git，按配置归一
}

// domain/plugin/port/PluginTrustScanPort.java
public interface PluginTrustScanPort {
    TrustCheckReport scan(Path tarball, String packageName, String version, String spec);
    // 适配器内收敛：超时、重试、503 排队、报告解析；异常只抛 PluginScanAdapterException(code)
}

// domain/plugin/port/PluginIndexPublisherPort.java
public interface PluginIndexPublisherPort {
    PublishedIndexRef publishVersioned(byte[] body, long gen);  // key = dsh/index/v{gen:010d}.json
    void switchPointer(PublishedIndexRef ref);                   // 覆盖 PUT dsh/index/current.json
    Optional<IndexPointer> readPointer();
    byte[] readVersioned(String key);
}

// domain/plugin/port/PluginIconStorePort.java
public interface PluginIconStorePort {
    String put(String pluginKey, byte[] bytes, String contentType); // 返回 dsh/icons/{pluginKey}.{ext}
    IconAsset get(String key);
}

// domain/plugin/port/PluginArchiveReaderPort.java —— 只读，只认一个入参：暂存 tarball 的字节来源
public interface PluginArchiveReaderPort {
    // 遍历 tar 流一次产出清单；超任一上限直接抛，不截断（截断会假装"看全了"）
    StagedFileListing readIndex(TarballSource source, ArchiveLimits limits);
    // 精确路径读取：内部按"清单条目名 === path"定位，禁止先拼接路径再打开文件
    StagedEntryBytes readEntry(TarballSource source, StagedFileEntry entry, ArchiveLimits limits);
    // 二进制判定：扩展名/MIME + 前 8 KiB 采样（含 NUL）+ UTF-8 可解码比例
    BinaryVerdict detectBinary(byte[] head, String path);
}

// domain/plugin/ArchiveLimits.java —— 上限一律由配置构造，不写在业务代码里
public record ArchiveLimits(int maxEntries, long maxUncompressedBytes, long maxEntryBytes,
                            long maxTextBytes, int maxCompressionRatio, Duration budget) {}
```

实现约定：
- 所有端口由 `skillhub-infra` 实现，`skillhub-domain` 只声明。**禁止**在 `domain/plugin` 中 import `org.springframework.web.*`、`software.amazon.*`、`org.eclipse.jgit.*`。
- `NpmRegistryPort.publish` 的 HTTP 细节（`PUT /{name}`、`PUT /{name}/-/{file}`、`_attachments`、`dist-tags`、`registry` 归一化、preflight 探测）全部封在 `NpmRegistryPublishAdapter`；Verdaccio / Nexus / 私有实现只换这一个类。
- `spec` 参数（`npm:{name}@{version}` 或 `git+{url}#{commit}`）只作来源标签，**不参与闸门**（调用契约见本设计 §9.1）。
- **npm 来源的版本解析与冻结（用户最新裁决）**：用户**只填包名（可 scoped）**，没有版本输入框；版本一律由服务端调 `resolveLatest(packageName)`（即 `resolveMetadata(name, null)`）取 registry `latest` dist-tag 指向的**最新非预发布版本**。`resolveMetadata` 保留可传版本/标签的签名供内部按已冻结快照重取同一制品，但**用户提交路径永不传版本**。解析出的版本在**提交那一刻冻结**进该次提交的快照（`plugin_submission.source_payload = npm{packageName, resolvedVersion, resolvedAt}`），扫描 / 审核 / 代发 / 上架**全程复用该冻结版本，后续阶段绝不重新解析 latest**——否则 `latest` 移动会造成"审的是 A、发的是 B"，破坏"扫的就是发的" sha512 与扫描指纹断言（§4.2、§6.2、§8.4）。上游后续发新版**不影响**已上架版本，要上新版必须重新提交（对应 `PRD §13` R11）。npm 来源的提交前校验（若有预检步骤）只经 `resolveLatest` 取 latest 元数据、**不读取任何版本参数**；其四类错误分类（包名不存在 / 无权限 / registry 不可达 / 只有预发布版本）保持不变，不因本裁决增删。

#### 3.4.1 Git 来源：v1 只做 SSH 一个后端、只取默认分支最新提交

用户提出用 SSH 拉内网 Git，最终定为**只做 SSH**（内网不支持 HTTPS + 个人凭证）：`skillhub.plugin.git.transport` **默认 `ssh`**，`SshKeyGitResolver` 是 v1 唯一交付实现；`HttpsTokenGitResolver` **只留端口与空实现、不进 P0/P1 交付范围**（将来要加就是新写一个实现类，不动调用方）。

| 项 | 规则 |
|---|---|
| 端口与实现 | `GitSourceResolver`（domain 端口）；v1 装配 `SshKeyGitResolver`。`GitTransportSelector` 启动时装配唯一 bean，避免"两条路都能走"。HTTPS 实现在本变更里**不写**（不交付、不测），只保留端口形状 |
| 身份 | **平台服务账号单一身份、只读**，不是每个用户。用户名/私钥都由配置提供，**用户不填任何 token / 私钥**（`PRD §6.4` 第 6 条把"表单里出现私钥/token"写成红线） |
| 用户输入 | **只有一个仓库地址**。接受 `https://host/group/repo(.git)` 与 `git@host:group/repo.git` 两种写法，**不接受分支 / tag / commit** |
| 取什么代码 | 一律取该仓库**默认分支的最新提交**：`ls-remote --symref` 读 `refs/remotes/origin/HEAD` 得默认分支 → `depth=1` 单分支浅克隆 → 读 `HEAD` 的 commit。**禁止**接受用户指定 ref/SHA（这同时消掉了 `allowReachableSHA1InWant` 那个坑） |
| 版本指纹 | `commit` + `branch` + `resolved_at` 三者一起落库（`plugin_version.git_commit/git_branch/git_resolved_at`）：同一版本号若重提时解析出不同 commit，说明源码动了，按"同版本不同内容"处理（409 `versionExists`，要求改版本号） |
| 地址归一化 | 只能用配置：`SKILLHUB_PLUGIN_GIT_URL_TEMPLATES`（对应产品口径 `skillhub.plugin.git.url-mapping`；host → SSH 模板 + 端口），**不在代码里猜协议**，也不做隐式转换。样例见 §11.1 |
| 授权边界 | 只读；host 白名单 + 可选 path 前缀白名单（`host:port` + `/group/` 前缀）；**DNS 解析后按解析结果再校验一次**（防 DNS rebinding 把内网地址当 SSRF 跳板），拒绝环回/链路本地/云元数据地址段 |
| host key 校验（**必选，不可省**） | 二选一，实现时定一种并写进部署文档：① `known_hosts` 文件（`SKILLHUB_PLUGIN_GIT_KNOWN_HOSTS`），由 JGit sshd 的 `KnownHostsServerKeyVerifier` 校验；② 直接配该 host 的公钥指纹（`SKILLHUB_PLUGIN_GIT_HOST_KEY_PINS=git.corp=ssh-ed25519 AAAA...`），用自定义 `ServerKeyVerifier` 做常量时间比对。**禁止任何等价于 `StrictHostKeyChecking=no` 的配置**——只有私钥没有 host key 校验，等于关掉 MITM 防护，劫持 DNS 就能冒充内网 Git。两者都未配 → **启动失败**（fail closed），不提供"跳过校验"的开关 |
| 私钥 | 文件路径，secret 挂载（`0600`，非 root 可读）；**不放环境变量**（会进 `/proc/self/environ` 与崩溃转储）、不进日志；支持**双钥轮换窗口**（`..._SSH_KEY_PATH` 与 `..._SSH_KEY_PATH_NEXT`，主钥失败回退旧钥） |
| **仓库结构规范（一插件一仓库）** | 表单**只有仓库地址一个输入框**；**一个插件一个仓库，插件包必须在仓库根目录**。不接受子目录 / 分支 / tag / commit / 凭据字段；若请求体夹带 `path`（或 `ref`/`tag`/`commit`/凭据）等字段，后端**显式 400 拒绝**并给固定文案「本期不支持子目录，请将插件包置于仓库根目录」（前端可能绕过，后端必须挡住，§7.3、§7.4） |
| **根目录校验（克隆后）** | 浅克隆后检查**仓库根目录**是否存在含 DSH 插件声明（`dsh.bundle`）的 `package.json`；缺失即 `gitPackageNotFound`，固定文案「仓库根目录必须包含含 DSH 插件声明的 package.json（一个插件一个仓库），请按规范调整仓库结构后重新提交」。**无路径候选、无自动探测**：不填子目录就没有"解析最终目录"这回事，也不写入任何 path 字段（§6.2、§7.4） |
| 克隆约束 | `depth=1`、单分支、60s 预算、大小上限、禁子模块与符号链接；容器**非 root**。**禁符号链接是克隆层通用约束**（防仓库内 symlink 指到根目录外），与用户可控 path 无关——本期不存在用户可控 path，故不再需要"解析结果仍在仓库根内"的 path 断言（§14.1 A8） |
| 预检 | `POST /api/v1/plugins/git/preflight`（§7.2）：`ls-remote` 级别，验地址可解析 / host 在白名单 / SSH 握手成功（**含 host key 校验**）/ 凭据有读权限 / 默认分支存在。**预检不克隆、不读任何文件**——"根目录有没有 `package.json`"预检发现不了，只能在克隆后校验；错误码与文案必须区分「预检失败」与「克隆后校验失败」；分类型返回，前端给不同文案（不许笼统报"拉取失败"） |
| 依赖 | `org.eclipse.jgit.ssh.apache`（MINA sshd），**版本锁死**，仅进 `skillhub-infra`，写进依赖清单与提案影响面 |

**这条设计丢掉了什么（写清代价，别当没有）**：用户不能指定分支/tag/commit，也不能回滚到某个历史版本——要发旧代码只能在新分支上打一个版本号，或走 npm 来源。这是"消掉 `allowReachableSHA1InWant` 不确定性"的交换条件；若将来要放开"用户指定 ref"，需要扩 `GitSourceResolver` 端口并同步改表单与调用方，且必须先在 Git 服务端确认可拉任意 SHA——本期不做。

### 3.5 复用与独立的边界（硬规则）

| 能力 | 复用 | 独立 | 理由 |
|---|---|---|---|
| 表结构 | — | `plugin*` 9 张表全新 + `review_task` 加索引 | 与 skill 表零共享，避免 `skill_version` 状态机被插件语义污染 |
| 审核队列 | `review_task` 表、`ReviewTaskStatus`、`ReviewTaskRepository.updateStatusWithVersion/recordDecision/syncReviewTaskState`、审核页与批量审核 UI | `PluginReviewDecisionService`（新） | `ReviewService.approveCheckedTask` 直接 `skillVersionRepository.findById(task.getSkillVersionId())` 并发布 Skill（`ReviewService.java:236`，入口 `approveReview` 在 `:205`），PLUGIN_VERSION 进入会抛 `skill_version.not_found` 或错误发布。**必须**在 `ReviewService.approveReview/rejectReview` 入口对 `PLUGIN_VERSION` 显式抛 `DomainBadRequestException("review.unsupported_subject")`，把插件决策引到独立服务 |
| 审计 | `AuditLogService.record(actor, action, targetType, targetId, requestId, clientIp, ua, detailJson)` | 新 action 常量 | 复用设施，新增 `PLUGIN_*` 事件类型 |
| 对象存储 | `ObjectStorageService`（put/get/delete/exists/metadata） | 键命名空间 `dsh/**`、`plugin/**` | 不新增存储抽象；`domain` 不直接依赖存储（走端口） |
| 搜索 | PG `tsvector` 生成列 + GIN | `plugin.search_vector` 独立列 | 不改 `skillhub-search` SPI |
| 鉴权 | 现有会话、`String` 身份、平台角色 `SKILL_ADMIN`/`SUPER_ADMIN`、空间角色 | 插件权限断言（`PluginPermissionChecker`） | 角色来源复用，断言对象不同 |
| 限流 | `@RateLimit(category=..., authenticated=..., anonymous=...)` | 新 category | — |
| 扫描 | scanner 容器的并发闸、硬超时、临时根、脱敏 | `/scan-plugin-upload`、`dsh-trust-check`、报告语义、裁决函数 | 与 `skill-scanner` 不共用表、不共用语义（PRD §7.5） |
| 异步 | `claim(now, lease)` + `FOR UPDATE SKIP LOCKED` 模式 | 两张新表、两套状态机 | 不复用 `scan_task_outbox`（语义不同、失败处理不同） |
| 举报 | 前端 `features/report/report-text.ts` 的理由码文案映射、举报弹窗形态；后台"举报管理"页加一个**只读**专题 tab | 新表 `plugin_report` + `PluginReportService`（新，只有 `submit` + `list` 两个方法） | 既有 `skill_report.skill_id` 是 `NOT NULL`，`SkillReportService.submit` 还会断言 skill 存在并发布 skill 领域事件——泛型化要同时改表、改服务契约与既有后台查询三处。举报本身只有"谁报了哪个对象、理由是什么"三个语义，新建窄表风险更低。**复用文案，不复用表**：理由码取同一套（`misleading/malicious/license/spam/other`），避免同一个问题在两处有两种叫法。本期**没有处置流程**（PRD §1.2 只承诺入口），所以表里也没有状态列 |

**禁止新增的跨模块依赖**：

1. `domain/plugin` → `skillhub-storage`：domain 已有该依赖（`skillhub-domain/pom.xml:36`，属 `AGENTS.md` 记录的已知偏差），plugin 域**不得**扩大它，图标与索引一律走端口。
2. `domain/plugin` → `skillhub-search`、`skillhub-notification`：不新增。
3. `skillhub-infra` → `skillhub-app`：不允许（现有方向是 app→infra）。
4. `scanner` 侧不得引入任何 Java/Spring 概念，也不得 import `skill_scanner` 的上传 handler 来做插件扫描（两套解包规则会产生"扫的包 ≠ 发的包"）。

第 4 条特别说明：`/scan-plugin-upload` 自己解包自己 spawn，不复用上游 `/scan-upload` 的处理链，因为上游对技能包有 `SKILL.md` 入口假设，而插件 tarball 没有。

---

