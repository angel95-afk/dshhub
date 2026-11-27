# Proposal

## Why

本站目前只托管一类资产：SkillHub 技能（`SKILL.md` 包）。DSH 自身还有另一套扩展机制——**DSH 插件**（npm 包 + `dsh.bundle` 声明 + `dsh plugin add` 安装），二者形态、安装入口、可获得能力完全不同。DSH 官方已定义**插件市场索引协议**（`format: 1` 的 JSON 索引），用户可在「插件 → 实验性插件 → 自定义插件索引」中添加索引源 URL 后浏览安装。本站要做的正是**成为这样一个索引源**，并承接组织内部的插件提交、扫描闸门与命名空间治理。

需求正文见 `../../../docs/prds/plugin-market/plugin-market-v1.0-prd.md`（现行 **v2.20**（2026-11-27 冻结，461 行，状态 Frozen（已与 design 对齐）），13 节 / 27 条验收；章节号自 v2.1 起未变；v2.13 收窄 npm 包名来源口径、v2.14 新增「审核页可查看包内文件内容」、v2.19 按定稿原型同步 11 条裁决、v2.20 按用户裁决删除产品目标 4 + 用户侧披露全撤 + 返回固定回我的插件 + 修复原型旧措辞；判定规则与四裁决不变）与交互原型 `../../../docs/prds/plugin-market/plugin-market-v1.0-prototype.html`（9 屏 S01–S09）。本变更把该需求落成可实施的工程契约；**本文件不重复 PRD 的字段清单与页面细节，只说明本次变更做了什么、破坏了什么、怎么迁移**。技术方案、DDL、接口、状态机全部在 `design/README.md`。PRD 与设计不一致之处一律记入 `design/03-overall-deviations-risks-phasing.md` §13 偏离清单（共 22 条），PRD 正文不再改动。

不做本次变更的代价：内部 DSH 插件没有合规分发与审核闸门，用户只能靠口头或聊天记录里的包名自行猜测安装来源，且提交内容没有静态扫描把关（本期扫描只作上架闸门 + 治理侧审核，用户侧不展示披露）。

## What Changes

### 新增能力

- **索引源与索引文件**：新增 `/dsh/index.json`（`format: 1`）、`/dsh/icons/{key}`、`/dsh/install/{namespace}/{slug}/{version}.md`（按版本）、`/dsh/install/{namespace}/{slug}.md`（旧路径别名，等价于最新 `LISTED` 版本，直接 200 不 302）、`/dsh/install/index.md`，全部匿名可读、无重定向、正确拼接 `SKILLHUB_WEB_BASE_PATH`。索引由后端物化为"版本化键 + 指针键"，读路径带 `ETag`/`Cache-Control: public, max-age=60` 与 304。
- **插件目录**：`/plugins` 上下平铺两段（索引源 → 插件列表，无模块卡片框，`PRD §4` v2.19 ⑦；v2.20 ④ 补「仅看收藏」开关与「复制最多」排序）、`/plugins/{namespace}/{slug}` 详情（只读版本历史、安装提示词、举报入口；**v2.20 ② 起用户侧不展示任何披露，完整证据只在治理侧审核页与包内文件浏览**）。
- **四来源提交**：npm 包名 / 内网 Git / zip / 文件夹，收敛为官方协议允许的 `npm` 与 `git` 两种分发来源；zip 与文件夹由本站规范化 tarball 并代发到配置的 npm 私服。npm 来源**用户只填包名（可 scoped），表单无版本输入框、禁止手填版本**；版本由服务端取 registry `latest` 对应的**最新非预发布版本**（跳 prerelease），**提交那一刻冻结**进提交快照，扫描 / 审核 / 代发 / 上架全程复用、后续不重解析 latest（上游发新版不影响已上架版本，要上新版必须重新提交）；夹带 `version` 一律 400，重复提交撞 `name@version` 且 latest 未变则 409（npm 专属文案）。Git 来源 **v1 只做 SSH**（`skillhub.plugin.git.transport` 默认 `ssh`，HTTPS 后端只留端口不交付）：**用户只填仓库地址一个字段**（一插件一仓库、插件包在仓库根目录），**不接受子目录/分支/tag/commit，也不填任何凭据**（API 夹带 `path` 等字段一律 400 拒绝）；一律取默认分支最新提交（`ls-remote --symref` → `depth=1` 浅克隆 → 读 `HEAD`），版本指纹 = commit + branch + resolved_at。host key 必须 pin、私钥 secret 挂载 0600、非 root、双钥轮换；`POST /api/v1/plugins/git/preflight` 只做 `ls-remote`（含 host key 校验），**预检不读 package.json**，克隆后校验仓库根目录是否含 DSH 插件声明的 `package.json`（缺包即 `gitPackageNotFound`）。
- **提交生命周期状态机**：新增 `plugin_submission` 表与状态机，HTTP 请求内只做毫秒级同步校验，扫描、代发、落库、索引重建全部异步。
- **扫描闸门**：scanner 服务新增 `POST /scan-plugin-upload`，镜像内锁定 `dsh-trust-check` 精确版本；裁决 `red` 拒绝、`review`/`clear` 放行、`error` fail closed；红线放行、重扫、漂移自动下架全部写审计。
- **闸门顺序（用户第二次裁决，覆盖 v2.2）**：扫描 → **审核（仅团队空间）** → **代发** → 上架。审核人批的是**尚未推送的暂存包**，通过后才代发、代发成功才 `LISTED`；**审核拒绝即终态，没有任何撤包动作**（制品从未推送，私服上本来就没有该版本）。因此 v2.2 那一整套撤包链路（`revoke_state` 五列 + 约束 + 伪 worker + `NpmRegistryPort.unpublish` + 人工清理出口 + 残留清单）**全部删除**；`PUBLISHED` 状态一并删除。新链路的唯一代价换成「审核通过后代发可能长期失败」，用 `APPROVED` 可重试 + 「重试发布」端点 + 告警兜底，见 `design/README.md` §4.6 附表与 §14.1 A11。审核页改展示暂存包 `sha512` / 扫描指纹 / 报告时间，并写明「通过后才会推送到内部源」。
- **审核页包内文件只读浏览**（v2.14 新增）：新增三个只读端点——`GET /api/v1/plugins/submissions/{id}/files`（清单：`path/size/binary/sensitive` + 包级 sha512，不含内容）、`.../files/content?path=`（单文件）、`.../files/download?path=`（字节下载），以及前端 `PluginFileBrowser`（**复用** `features/skill` 的 `file-tree` / `CodeRenderer` / `file-type-utils`，插件侧只做数据接入与布局，绝不重写可预览判定/二进制魔数/高亮）。数据源**只有** `plugin_submission.staged_tarball_key` 指向的暂存 tarball（审核发生在代发之前，私服里没有这个包；已按保留策略清理时返回 410，**绝不回退**去私服或上游 npm 拉同名同版本）。整组端点按「未审核内容永不进入浏览器执行上下文」设计：文本只以 `text/plain; charset=utf-8` + `X-Content-Type-Options: nosniff` + `Content-Security-Policy: default-src 'none'; sandbox` + `Cache-Control: no-store` 返回；二进制只回元信息（类型/大小/魔数），下载强制 `application/octet-stream` + `Content-Disposition: attachment`；前端只用文本节点渲染（禁 `innerHTML` / `dangerouslySetInnerHTML` / `iframe` / `object` / `embed`）。路径穿越三层防护（物化阶段拒绝 `..`/绝对路径/盘符/NUL/反斜杠/符号链接 → 请求层白名单**逐字节比对** → 读取层复核，`%2e%2e%2f` 双重编码与大小写绕过均有口径）；六项资源上限（文件数 2000 / 整包解压 200 MiB / 单文件 8 MiB / 文本预览 512 KiB / 压缩比 10:1 / 请求预算 5s+10s），**超限拒绝整次浏览而不是截断**；清单/单文件/下载三个动作各写审计（不记录内容），**审计写失败即不返回内容**。见 `design/README.md` §4.7、§6.4、§7.2、§7.6、§10.2、§14.1 A17/A18、§16 的 S1–S13。
- **治理**：提交进现有审核队列（新增 `PLUGIN_VERSION` 主题类型）、下架/恢复、撤下单版本/恢复版本、软删插件、释放包名、黑名单、挂 / 摘标签、我的插件（含在途提交）、命名空间维度插件 tab、插件举报（只做入口 + 后台只读列表，不做处置流程）。**审核决定的权限按 `subjectType` 分流**：`PLUGIN_VERSION` 只允许该空间空间管理员（平台管理员 403，与包内文件浏览同一行矩阵依据）；`SKILL_METADATA` 等既有类型行为不变。另加**允许自审但留痕**（用户裁决）：`submitted_by == actor` 时 approve / reject 正常执行、不拦截，审计 `PLUGIN_REVIEW_APPROVED` / `PLUGIN_VERSION_REJECTED` 各带必填字段 `selfReview: true|false`；审核页在裁决按钮旁显示中性提示「你是本次提交的作者」（渲染条件来自服务端下发的 `isAuthor`）。队列项另下发 `canReview` / `canReviewReasonCode` / `canViewStagedFiles` 供前端置灰与提示（前端不推断角色）。
- **统计**：`plugin_stat_event` / `plugin_stat_daily` 与卡片上的「复制提示词次数」。
- **提交入口能力开关**：新增 `GET /api/v1/plugins/submit-capabilities`（能力由服务端下发，前端不硬编码分期）。**P0 仅开放个人空间提交**，团队空间三处入口（市场页头、我的插件页头、空间插件 tab）**置灰并说明「审核将在治理阶段开放」，不隐藏**（`PRD §6.1` 通用规则；文案逐字对齐 `PRD §12` P0 行，v2.8 链路反转后已无「撤包」概念）；P1 审核就绪后翻 `SKILLHUB_PLUGIN_TEAM_SPACE_SUBMIT_ENABLED=true` 即开放，前端零改动。

### BREAKING

- **顶部导航**：移除「发布」项，改为「插件市场」。发布入口下沉到技能市场页头（登录可见）。`/dashboard/publish` 路由与其 6 个查询参数（`namespace`/`visibility`/`updateName`/`updateSkill`/`resubmitSkill`/`resubmitVersion`）**保持不变**，页脚发布链接保留——深链不断。
- **部署契约**：`web/nginx.conf.template` 必须新增 `location ^~ /dsh/`。未更新的旧 nginx 配置下 `/dsh/*` 会被 SPA 的 `index.html` fallback 吞掉，索引源对外表现为"返回 HTML 的 200"，DSH 侧静默拒绝。这是**升级时必须同步变更的部署文件**。
- **scanner 镜像**：新增 Node 22 运行时与 `dsh-trust-check` 精确锁定版本，镜像体积与启动时延变化，必须在发布说明中给出前后数字。新增接口 `POST /scan-plugin-upload`；既有 `/scan`、`/scan-upload` 语义不变。
- **提交接口的时序契约**：`POST /api/v1/plugins/submissions` 只做同步校验并返回 `202 + submissionId`，**不在请求内完成扫描与代发**（替代 PRD §7.2 的同步闸门与 §10.3 的同步 202/422 语义）。该接口本期首次出现，不破坏既有消费者，但需要前端按轮询模型实现。
- **统计口径**：不提供站内 tarball 下载接口，`plugin_stat_event` 只保留 `view` / `copy_prompt`；PRD §12 的 `download` 事件与「仅收录」概念一并删除。

### 保留

- 技能侧全部既有能力：`/search`、`/dashboard/publish` 及其深链、技能审核队列、`SecurityAudit`、scanner 的 `/scan` 与 `/scan-upload`、技能版本状态机（`SCANNING`/`SCAN_FAILED`）。**不共用表、不共用语义**。
- `review_task` 表结构不变：`subject_type` 无 CHECK 约束（已核对 `V1`/`V50`），新增枚举值 `PLUGIN_VERSION` 不需要改表；仅追加一个 `status='PENDING'` 的部分唯一索引。
- `FlywayMigrationGuardrailTest` 不改：该守卫已按"低段 V1..N 连续 + 高位段（≥10000）无重复"实施，本次迁移落在高位段即可。
- 对象存储、审计、鉴权、搜索（PG 全文检索）设施沿用，不新增 Maven 模块、不改 SPI。

## Impact

- **数据库**：1 个迁移文件（`V10001__plugin_market.sql`），9 张新表（`plugin`、`plugin_version`、`plugin_submission`、`plugin_scan_report`、`plugin_scan_waiver`、`plugin_report`、`plugin_stat_event`、`plugin_stat_daily`、`plugin_index_state`）+ 28 个索引（含 3 个部分唯一索引：包名、命名空间+slug、在途提交）+ `review_task` 1 个部分唯一索引。无存量数据迁移，无破坏性 DDL，无既有表列变更。
- **后端**：`skillhub-domain/domain/plugin`、`skillhub-app/{controller,service,repository,task}`、`skillhub-infra/plugin` 三处新增包；`ReviewSubjectType` 加一个枚举值；`ReviewService` 入口对 `PLUGIN_VERSION` 显式拒绝（防止落进技能发布分支）；`JpaGovernanceQueryRepository`、`ReviewPortalAppService` 各加一处类型映射分支。新增两个三方依赖：`commons-compress`（tar.gz 读写）、`org.eclipse.jgit`（Git 解析，镜像内无 `git` 二进制）。
- **前端**：4 条新路由 + 5 个新页面 + `features/plugin/` 与 `features/plugin/scan/` 两个新目录（审核页另加 `features/plugin/review-fields.tsx` 与 `review-files.tsx`——后者是**薄层**，复用 `features/skill/` 的文件树、代码渲染与类型判定，**不改审核页路由**）；导航、市场页头、控制台侧栏与概览卡、命名空间 tab 局部改写；`zh/en/ko/ru` 四语言新增键；`api/generated` 经 `make generate-api` 重生成（不手改）。
- **scanner**：`skillhub_scanner_app.py` 新增一个路由（独立解包 + spawn 子进程，不复用上游 router）；`scanner/Dockerfile` 加 Node 22 + 锁定版本 CLI；新增并发、超时、临时目录、脱敏约束。
- **部署**：nginx 新增 `/dsh/` location；compose 与 Helm 新增 registry/worker/scan 三组环境变量；新增启动自检（registry 匿名读 + 上传来源可用性）。
- **测试**：新增迁移与守卫测试、索引生成强校验测试、扫描解析与裁决纯函数测试、worker 认领/租约/重试测试、前端 query hook 与 i18n 键一致性测试；**审核页包内文件浏览另加 §16 S1–S13**（响应头三件套、禁 `innerHTML` 的 lint+单测、`..`/编码/大小写绕过 400、超限拒绝而非截断、越权 403/404、指纹不一致 409、审计失败 503、缓存 single-flight）。

## 迁移策略

1. **前端/后端/迁移三者同批**：`/dsh/*` 与索引生成依赖新表；`plugin` 表为空时索引产出 `plugins: []` 的合法文件。本项目不设灰度，前后端与迁移一次性发布；zip/folder 代发是否可用由启动自检自动裁决，不靠人工开关分两拍。
2. **迁移编号**：`V10001__plugin_market.sql`（单文件）。不复用低段 `V104`（该号已被 `V104__github_sync_worker_presence.sql` 占用）、也不占用 `V10000`（属 `remove-github-sync` 变更）。不预留号段；不拆多文件，以免部分失败留下半套 schema。理由与守卫细节见 `design/README.md` §6.1。
3. **数据迁移**：无。全部为新表；`review_task` 只加索引，历史数据不需要回填。
4. **回退**：回滚代码后表与对象存储里的索引/图标文件成为无主数据，不影响任何既有功能；残留清理为一条运维脚本（见 `design/README.md` §12 手册）。**不写 down 迁移**：Flyway 社区版无 undo，且新建表回滚会丢失追溯用的扫描报告。
5. **故障回退**：`SKILLHUB_PLUGIN_MARKET_ENABLED=0` 时 `/dsh/*` 返回 503 且市场页隐藏，导航项保留但指向说明页——用于索引协议被 DSH 新版本拒绝时的一键止血（PRD R1）。回退到上一版索引只需把指针键指回旧 `gen`，不需要重新生成。这是故障处置手段，不是灰度放量。

## Risks

- **索引协议为实验特性（PRD R1/R2）**：单条不合规会让 DSH 拒绝整份索引且本站无法感知。缓解：生成期强校验 + 失败保留上一版 + CI 用官方校验库 + P1 出口把"真机 DSH 装成功"设为硬门禁。
- **异步化引入新的可观测缺口**：提交从"一次请求"变成"一段状态机"，失败必须能在站内被作者看懂。缓解：`plugin_submission` 的每个终态都带稳定 `errorCode` + i18n 键，前端轮询展示阶段而非只展示错误。
- **代发任意代码进私服（PRD R3）**：闸门必须在代发之前，且扫描与代发不能分批上线（见 `design/README.md` §15）。
- **私服实现未定（PRD R7）**：接口先冻结，联调用 mock registry；若私服不支持匿名只读，v1 直接禁用 zip/文件夹来源，而不是降低要求。
- **新增三方依赖**（`commons-compress`、`org.eclipse.jgit`、**`org.eclipse.jgit.ssh.apache`（v1 必需，版本锁死）**）：仅进 `skillhub-infra`，不放 domain/app；需在依赖评审中确认许可证与体积（见 `design/README.md` §3.4.1、§14）。
- **审核页预览接口是新攻击面**：它把**未审核的第三方内容**搬到本站域名下，四类风险必须同时防住——存储型 XSS（响应类型只能是 `text/plain`/`application/json`，三件套响应头 + 前端禁 `innerHTML`/`iframe`）、路径穿越（三层防护 + `%2e%2e%2f` 与大小写绕过口径）、解压炸弹（六项上限 + 超限拒绝整次浏览而非截断）、敏感信息外泄（**只对该空间空间管理员 + 提交者本人开放**；平台管理员按 `PRD §9` 矩阵不在审核人之列，一律 403，提交不存在 404）。缓解已逐条写死并配上 §16 S1–S13 断言；另有 `SKILLHUB_PLUGIN_FILE_BROWSE_ENABLED=false` 一键止血（见 `design/README.md` §7.6、§14.1 A17）。
- **有了「能看」的入口反而可能弱化审核质量**：审核人可能只看几个文件就通过，而审计会留下"看过 3 个文件"的记录。缓解：把必看项（`scripts`、`dsh.bundle`、扫描证据）前置到树上方的字段块，敏感文件计数显式提示，`FILE_VIEWED` 审计可回溯；**不做**"必须打开 N 个文件才能通过"这类形式化拦截（见 `design/README.md` §14.1 A18）。

## Capabilities

### New Capabilities

- `plugin-market-catalog`：插件市场列表、详情、版本列表、统计上报与搜索筛选。
- `plugin-index-protocol`：`format: 1` 索引的生成、强校验、版本化物化、指针切换、`/dsh/*` 读路径与缓存语义。
- `plugin-install-prompt`：安装提示词与安装文档（每插件唯一生成点）。
- `plugin-submission`：四来源提交、同步校验、异步状态机与代发私服。
- `plugin-trust-disclosure`：插件扫描、裁决闸门、能力/红线披露、重扫与放行、升级漂移自动下架。
- `plugin-namespace-management`：我的插件、命名空间维度插件列表与管理动作、审核接入。

### Modified Capabilities

- `skill-plugin-separation`（新增边界声明）：DSH 插件与 SkillHub 技能是两套资产，禁止共用表、共用版本状态机、共用扫描结论口径。技能侧能力本身不变。
- `navigation-publishing-entry`：顶部导航移除发布项，发布入口下沉；`/dashboard/publish` 契约不变。

### Removed Capabilities

无。PRD 中提到的「仅收录」概念与 `download` 事件从未实现，属需求内清理，不构成对既有能力的移除。
