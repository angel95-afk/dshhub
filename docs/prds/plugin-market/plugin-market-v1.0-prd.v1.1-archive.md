# PRD: DSH 插件市场 v1.0

**版本**: v1.1（评审修订）
**日期**: 2026-11-25
**状态**: Draft（待评审）
**修订**: v1.1 — 市场页去掉提示词模块（三模块改两模块）；补齐「我的插件」入口（§3.3）；新增命名空间插件 tab（§3.4）；验收新增 H 组。
**涉及范围**: `../../../web`（导航、技能市场、新页面）、`server/`（新增 plugin 领域、索引生成、npm 发布适配器、扫描闸门）、`scanner/`（新增插件扫描入口与 Node 运行时）、部署配置（新增路由前缀、scanner 镜像变更）

---

## 1. 背景与目标

本站目前只托管一类资产：SkillHub 技能（`SKILL.md` 包），通过 CLI / `skillhub-dsh` 连接插件安装到 Agent 的技能根目录。DSH 自身还有另一套扩展机制——**DSH 插件**（npm 包，带 `dsh.bundle` 声明，通过 `dsh plugin --profile <p> add` 安装）。二者形态、安装入口、可获得的能力完全不同。

DSH 官方已定义**插件市场索引协议**（`format: 1` 的 JSON 索引），用户在 DSH「插件 → 实验性插件 → 自定义插件索引」中打开开关后，可在「发现」页添加索引源 URL，即可浏览并安装索引中列出的插件。本站要做的正是**成为这样一个索引源**，并承接组织内部的插件提交与治理。

对比参考站：`dsh-hub.cc`（社区插件商店，9 个分类，提示词形如 `请按照 https://dsh-hub.cc/install/<owner>/<slug>.md 安装 DSH 插件 <owner>/<slug>。`）、`dsh.market`（目录站，强调"每日扫描 + 评分"）。本站的差异点在于：**内部私服分发 + 命名空间治理 + 审核与审计**，而不是公网爬取。

### 1.1 目标

1. 让 DSH 用户能在本站一次拿到两样东西：可添加的索引源地址（市场页）、可复制给 Agent 的单插件安装提示词（插件详情页），从而闭环完成"发现 → 安装"。
2. 让内部插件能通过四种来源（内网 Git / npm 包名 / zip / 文件夹）提交并进入目录，且**索引文件始终符合官方协议**。
3. 把"发布"从全局导航收进市场页，让"浏览市场"和"发布内容"在同一上下文内完成。
4. 在提交链路上用 `dsh-trust-check` 做**静态能力披露 + 红线闸门**：用户在装之前就能看到插件会碰什么（shell / 文件 / 网络 / 凭据 / 环境变量 / 注入），红线段默认挡住（§7）。

### 1.2 非目标（本期不做）

- DSH 内的商店插件（在 DSH 界面里直接浏览本站目录）——需要新发布一个 dsh 插件，独立立项。
- CLI 插件子命令（`skillhub plugin install/list`）。
- 外部热度指标采集（`github_stars`、`npm_weekly_downloads`）。
- 私服分发量接入（私服实现未定）。
- 插件评分、评论、场景包（Scene Packs）、插件与技能互推。
- **运行时行为观测**（沙箱动态执行、真实网络流量、调用链追踪）——本期只有静态披露。`dsh-trust-check` 不展开依赖树、不跟 `node_modules`、看不到运行时拼接的 `import`/`eval`，这些漏判在 §7.1 明示给用户。
- 安全背书：本站不出具"安全 / 已认证 / 无风险"结论，也不做漏洞定级（§7.5 文案契约）。

---

## 2. 名词与边界

| 术语 | 定义 | 易混点 |
|---|---|---|
| **DSH 插件** | 以 npm 包形态分发、声明 `dsh.bundle`、由 `dsh plugin add` 安装的扩展 | 不是技能，不含 `SKILL.md` 入口约定 |
| **SkillHub 技能** | `SKILL.md` 技能包，通过 CLI 装入 `.dsh/skills` 等技能根 | 与插件是两套资产，互不复用表 |
| **插件市场** | 本站 `/plugins` 门户页 | 与 `/search`（技能市场）并列 |
| **索引源** | 索引文件的 URL，由用户加入 DSH「发现 → 添加索引源」，属于当前 profile | 不是 npm registry 地址 |
| **索引文件** | 符合官方 `format: 1` 的 JSON，本文固定路径 `/dsh/index.json` | 全站只有一份，不按分类拆分 |
| **安装提示词** | 插件详情页里复制给 Agent 的一段自然语言，指向该插件自己的安装文档 | 只出现在详情页；市场页不提供提示词模块 |
| **安装文档** | `/dsh/install/**.md`，Markdown，供 Agent 阅读后执行安装 | 不是给人看的详情页 |
| **分发来源（source）** | 官方协议里版本的安装来源，只能是 `npm` 或 `git` | 与"提交来源"不是同一维度，见 §6 |
| **能力披露** | `dsh-trust-check` 对插件源码的静态判定：`capabilities` + `redLines` + `文件:行号` 证据 | 不是安全评级，也不是漏洞列表 |
| **裁决（verdict）** | 安装前三态：`red`（有红线）/ `review`（有特权能力）/ `clear`（未检出） | 不看 `score`/`band`，分数低也可能是 `review` |
| **红线（redLine）** | 五类硬信号：安装脚本、核心 bundle 篡改、读凭据且有网络、明文 `http://`、字面量 IP 外连 | 段落文案由工具冻结，本站不自行改写 |
| **扫描指纹** | 报告里的 `ackFingerprint`，内容一变就变 | 本站只用于漂移对比，不生成 `trust-ack.json` |
| **商店插件** | 在 DSH 内复现插件市场界面的插件（非本期） | 市场页只做索引源与列表，不依赖它 |

> 术语沿用 `../../../CONTEXT.md`：不得把"SkillHub 技能"统称为"DSH 插件"，不得用"接入 DSH"同时指代插件托管与技能消费。

---

## 3. 信息架构与路由

### 3.1 顶部导航改造

现状：`首页 / 发布 / 技能市场 / 技能套件 / 控制台`。改造后：

| 位置 | 改造前 | 改造后 |
|---|---|---|
| 第 2 项 | 发布 → `/dashboard/publish` | **插件市场** → `/plugins` |
| 第 3 项 | 技能市场 → `/search` | 技能市场 → `/search` |
| 第 4 项 | 技能套件 → `/suites` | 技能套件 → `/suites` |
| 第 5 项 | 控制台 → `/dashboard` | 控制台 → `/dashboard` |

- 移除导航中的「发布」，改为在技能市场页头右侧新增主按钮「发布技能」（登录后可见），跳转 `/dashboard/publish`。
- **`/dashboard/publish` 路由与其 5 个查询参数（`namespace`/`visibility`/`updateName`/`updateSkill`/`resubmitSkill`/`resubmitVersion`）保持不变**——「我的技能」的更新与重新提交流程直接深链该路由。
- 页脚「产品」栏的发布链接保留，仅作为次级入口。
- 移动端菜单跟随同一数据源，无需单独改动。

### 3.2 新增路由

| 页面 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| 插件市场 | `/plugins` | 匿名可访问 | 两模块页面（§4） |
| 插件详情 | `/plugins/{namespace}/{slug}` | 匿名可访问 | 版本、安装、README |
| 提交插件 | `/dashboard/plugins/submit` | 登录 + 空间成员 | 四来源表单 |
| 我的插件 | `/dashboard/plugins` | 登录 | 我提交的插件与状态 |
| 命名空间插件管理 | `/dashboard/namespaces/{slug}/plugins` | 登录 + 空间成员 | 空间维度插件列表与管理（§3.4） |

导航高亮沿用现有严格匹配规则：`/plugins` 不与 `/plugins/{ns}/{slug}` 互相点亮之外的路径。

### 3.3 「我的 XX」入口对齐

技能侧的既有入口是「技能市场页头 → 我的技能」与「控制台侧栏 → 我的技能」；插件侧必须同构，不能只在控制台侧栏里才找得到：

| 位置 | 技能侧 | 插件侧（本期） |
|---|---|---|
| 市场页头 | 我的技能（次级按钮）+ 发布技能（主按钮） | **我的插件**（次级按钮，`/dashboard/plugins`）+ 提交插件（主按钮） |
| 控制台侧栏「技能与数据」组 | 我的技能、我的套件、命名空间… | 在「我的技能」之后插入 **我的插件** |
| 命名空间详情 | 技能 tab | 新增 **插件 tab**（§3.4） |

- 市场页头的「我的插件」与「提交插件」并列，**未登录时两个都不渲染**；「提交插件」保持主按钮，「我的插件」用次级按钮（`variant="outline"`）。
- 技能市场页头若当前只剩「发布技能」，一并补上「我的技能」次级按钮，保证两个市场的页头按钮组完全对称（同一组件、同一顺序）。
- 侧栏图标用 lucide `Plug`（与「我的技能」的 `Package` 区分），文案键 `sidebar.myPlugins`。
- 埋点：`plugin.mine.open`、`plugin.submit.open`；控制台侧栏沿用 `navigation.owned.plugins`。
- 控制台首页的概览卡片区同步新增一张「我的插件」卡（`overview.myPlugins`），与「我的技能」卡同级，避免入口只存在于侧栏。

### 3.4 命名空间维度的插件管理

命名空间详情（`/dashboard/namespaces/{slug}`）现有 tab 为 `技能 / 成员 / 审核 / 设置`，本期在「技能」后插入 **插件** tab，落到 `/dashboard/namespaces/{slug}/plugins`：

- **内容**：该命名空间下全部插件（含 `PENDING`/`REJECTED`/`HIDDEN`），列：插件（图标 + 标题 + npm 包名）、最新版本、状态、分类、裁决、提交人、更新时间、操作
- **筛选**：状态（全部 / 已上架 / 待审核 / 已拒绝 / 已下架）、关键词（标题或包名）、分类
- **操作**（按权限显示）：
  - 空间 ADMIN：下架 / 恢复上架 / 改分类 / 查看完整扫描报告 / 重新扫描；进入该空间审核队列（复用现有审核页，筛选类型 `PLUGIN_VERSION`）
  - 插件作者本人：查看详情、看拒绝原因、重新提交
  - 普通 MEMBER：只读列表
- **页头右侧**：「提交插件」主按钮，携带 `?namespace={slug}` 预填命名空间
- **可见性**：tab 对所有空间成员可见（含 PERSONAL 空间）；`members` tab 仍只对 TEAM 可见，两者互不影响
- **空态**：区分"空间还没有插件"（给出提交入口）与"筛选无结果"（给出清空筛选）
- **不做**：空间级别的批量操作、跨空间迁移插件（如需迁移，走下架 + 重新提交，保留审计）

> 实现落点：`NamespaceManagementNav` 的 `tabs` 增加 `{ key: 'plugins', visible: true }`，`active` 类型扩为 `'skills' | 'plugins' | 'members' | 'reviews' | 'settings'`；新增 `pages/dashboard/namespace-plugins.tsx`，复用 `features/plugin/` 的列表与操作组件，不复制一份表格逻辑。

---

## 4. 插件市场页（`/plugins`）

自上而下**两个模块**，顺序固定：索引源 → 插件列表。

> 市场页**不提供** DSH 安装插件提示词。提示词按插件生成（含包名与精确版本），统一收在详情页（§5.2）——放在市场页只能写通用句式，Agent 拿到后仍要自己猜装哪个包；两处并存还会让用户误以为要各复制一次。

### 4.1 模块一：索引源

**目的**：让用户把本站加进 DSH 的索引源列表。

内容：

- 主展示：`{publicBaseUrl}/dsh/index.json`，等宽字体 + 复制按钮（复制成功态 2s）
- 三步说明（与 DSH 实际入口一致）：
  1. 打开 DSH「插件」，在「实验性插件」下启用「自定义插件索引」
  2. 切到「发现」标签，点击「添加索引源」，粘贴上面的地址
  3. 保存后即可在「发现」页浏览、搜索、安装
- 提示：索引源属于当前 profile；`http:` 索引可被网络路径上的他人篡改，生产必须用 `https:`
- 状态：加载中骨架 / 加载失败显示"索引暂不可用"并保留地址可复制 / 索引为空时显示"暂无已上架插件"

> 地址一律由后端按 `SKILLHUB_PUBLIC_BASE_URL` + `SKILLHUB_WEB_BASE_PATH` 生成，前端不硬编码；子路径部署时不得丢前缀。

### 4.2 模块二：插件市场展示内容

- **页头按钮**（右对齐）：「我的插件」（次级，→ `/dashboard/plugins`）+「提交插件」（主按钮，→ `/dashboard/plugins/submit`）；**均登录后渲染，匿名不渲染**（与 §3.3 一致）
- **工具栏**：关键词搜索框（debounce 300ms）、分类按钮组（9 类，单选，可"全部"）、结果数与排序（最新 / 名称）
- **网格**：卡片 2/3/4 列响应式
- **卡片字段**：图标（无图标用首字母占位）、标题（缺省显示 npm 包名）、一句话简介、分类徽标、作者、最新版本号、`复制提示词次数`
- **卡片交互**：整卡可点进详情；卡片上**不放**"复制提示词"按钮——提示词要带版本与包名，只能在详情页生成（§5.2）
- **空/异常态**：无结果给出"清空筛选"；请求失败可重试
- **分页**：每页 24，底部"加载更多"（不做页码跳转，与搜索页一致）

**列表默认排序 = 发布时间倒序**（对应索引内置的"发布时间"排序，见 §8.2）。不声明 `sortKeys` 指标，避免依赖尚未采集的外部数据。

---

## 5. 插件详情页

### 5.1 结构

| 区域 | 内容 |
|---|---|
| 头部 | 图标、标题、npm 包名（等宽 + 复制）、分类、作者、最新版本、更新时间、`复制提示词次数` |
| 安装区 | 版本选择器（默认推荐版本）→ 安装提示词代码块 + 复制；折叠区给出等价命令 `dsh plugin --profile <profile> add {name}@{version} --ignore-scripts` |
| 来源 | `npm`（含 registry 地址）或 `git`（仓库地址 + 40 位 commit + 子目录）；仓库/主页外链 |
| 兼容性 | `engines.dsh`（缺省显示"未声明，装前请自行确认"） |
| 安全披露 | 裁决徽标、能力 chips、红线列表、证据折叠（`文件:行号` + 片段）、工作区外路径 / 密钥触摸 / 字面量去向、扫描器版本与时间、免责说明；红线被管理员放行时显示放行理由（§7.5） |
| 正文 | README（提交时快照，Markdown 渲染，图片走外链白名单策略：仅 `https:`） |
| 版本列表 | 版本号、发布时间、来源、`engines.dsh`、该版本裁决徽标、该版本单独的复制提示词 |
| 底部 | 举报入口（复用现有 report 能力）、"查看我的插件"（作者本人） |

### 5.2 每插件的提示词

**这是全站唯一生成安装提示词的位置**（市场页不再有提示词模块，见 §4）。

```
请按照 {publicBaseUrl}/dsh/install/{namespace}/{slug}.md 安装 DSH 插件 {npmName}。
```

- `{npmName}` 用 npm 包名，而不是 `namespace/slug`——官方安装 spec 是 `name@version`，用市场标识会让 Agent 猜错包名。
- 选择具体版本时，提示词保持同一句式（文档里带精确版本），不在提示词中塞版本号，减少歧义。
- 详情页的安装区固定包含「复制提示词」按钮；版本列表每一行也提供单版本复制（同一句式，文档指向该版本）。
- 提示词内的站点地址由后端下发，前端不拼接，子路径部署时必须带 base path。

### 5.3 安装文档 `/dsh/install/{namespace}/{slug}.md`

后端按模板生成，纯 Markdown（`Content-Type: text/markdown; charset=utf-8`），内容顺序固定：

1. 一行标题 + 该插件一句话说明
2. **需要安装的内容**：包名、精确版本、`engines.dsh` 要求、来源（npm registry 或 git 仓库 + commit）
3. **安装步骤**：确认 Profile 名 → `dsh plugin --profile <profile> add {name}@{version} --ignore-scripts` → 提示"不要省略 `--ignore-scripts`"
4. **校验**：`dsh plugin --profile <profile> list`（或用户在 DSH 界面确认）中应出现该包与版本；说明"仅包安装成功，不代表插件功能可用"
5. **卸载**：`dsh plugin --profile <profile> remove {name}`
6. **安全提示**：包来源、是否执行安装脚本、请核对来源与版本；附一行能力披露摘要（能力、红线 code、"未检出≠安全"声明，见 §7.5）；不得在文档中写入任何 Token

`index.md` 同模板，改为"如何添加索引源 + 当前目录概览"，并列出前 N 个插件（不超过 50 条，其余指向站点）。

> `index.md` 仍保留生成：用户拿到索引地址后，可直接让 Agent 读这份文档完成配置。**但市场页不再展示指向它的提示词模块**——页面上的索引源以地址 + 三步说明呈现（§4.1），避免与详情页提示词形成两套话术。

---

## 6. 提交插件（四种来源）

### 6.1 入口与权限

- 入口：`/plugins` 页头「提交插件」、`/dashboard/plugins` 页头「提交插件」、命名空间插件 tab 页头「提交插件」（带 `?namespace={slug}` 预填）→ 均落到 `/dashboard/plugins/submit`
- 需登录；需为所选命名空间的成员（角色不低于 MEMBER），并遵循该空间的发布策略
- 表单第一步选命名空间（下拉，只列出有权限的空间）；带 `?namespace=` 时预选该空间，仍可在表单内切换

### 6.2 来源收敛规则

官方索引的 `versions[].source` 只允许 `npm` 与 `git`，因此四种提交来源必须收敛：

| 提交来源 | 处理方式 | 生成的分发来源 |
|---|---|---|
| **npm 包名** | 用包名（可 scoped）向配置的 registry 查询 metadata，取指定或最新非预发布版本 | `{type:"npm", registry}` |
| **内网 Git 仓库** | 填 https URL（可选 `path` 子目录、可选 ref），后端解析出 **40 位 commit SHA**，读取该 commit 下的 `package.json` | `{type:"git", url, commit, path?}` |
| **zip 压缩包** | 解压 → 校验 `package.json` → 规范化为 npm tarball → **代发到自建 npm 私服** | `{type:"npm", registry}` |
| **文件夹** | 前端 `webkitdirectory` 选目录 → 前端打包为 zip → 走 zip 同一链路 | 同 zip |

**关键约束**：zip / 文件夹必须能解析出合法 `package.json`（`name` + `version` + 非 `private`），否则直接拒绝——无法代发就无法进入索引。

### 6.3 表单字段

**全自动为原则，人工只选来源与分类。**

| 字段 | 来源 | 可编辑 |
|---|---|---|
| 命名空间 | 用户选择 | 是（提交后不可改） |
| 提交来源 | 用户选择（4 选 1） | 是 |
| 来源参数 | Git URL / ref / path；npm 包名 / 版本；zip 文件；目录 | 是 |
| 分类 | 用户选择（9 类必选 1） | 是（上架后可改） |
| 包名 `name` | `package.json` | 否 |
| 版本 `version` | `package.json` | 否 |
| `engines.dsh` | `package.json` | 否 |
| 标题 / 简介 | `package.json` 的 `dsh` 字段或 `description` | 否 |
| 图标 | 包内 `icon`/`README` 首图，切图后转 data URL 或存图标文件 | 否 |
| README | 包内 `../../../README.md` | 否 |
| 仓库地址 | `package.json.repository` 或 Git 来源 | 否 |

> 提交页需明确提示："标题、简介、图标取自包内声明；如需修改请更新包后重新提交。" 这与"索引与包内容必须一致"的原则绑定。

### 6.4 校验清单（提交时同步返回）

必须全部通过才允许建版本：

1. `package.json` 可解析，`name` 合法（含 scoped），`version` 是精确 SemVer
2. `private` 不为 `true`；存在 `dsh.bundle` 或 `dsh` 相关声明（缺失给出警告而非拒绝，兼容早期插件）
3. `engines.dsh` 存在时必须是合法 SemVer 范围（可选）
4. 版本在**该插件内不重复**；私服中 `name@version` 已存在则拒绝，提示"请先升级 package.json 的 version"
5. zip 解压后总大小 ≤ 50 MiB、文件数 ≤ 2000、无路径穿越（`..`）、无符号链接、无 `.git` 目录
6. Git 来源：URL 必须 `https:`，commit 必须 40 位十六进制，`path` 下必须存在 `package.json`
7. 图标 ≤ 32 KiB（内联时）且为 SVG/PNG/JPEG/WebP
8. 安全扫描完成且未命中红线（§7.2）；扫描失败、报告不可解析一律按不通过处理，不放行

### 6.5 提交后流程

```
提交 → 解析/校验 → 规范化发布产物（zip/文件夹 → npm tarball；npm/git → 物化目录）
   → 安全扫描（§7）→ 命中红线或扫描失败即终止：不写私服、不落版本
   → 落库 plugin + plugin_version(状态 PENDING/APPROVED)
   → [需审核空间] 进现有审核队列 → 审核通过
   → 代发 npm（zip/文件夹）成功后才可 LISTED
   → 重建索引文件（原子替换）
```

- 扫描发生在**代发之前**：命中红线的包不得写入私服，也不得占用 `name@version`
- 代发 npm 失败：整个提交回滚（不落版本），返回私服原始错误摘要（脱敏）
- 审核拒绝：版本置 REJECTED，不入索引，作者可在「我的插件」看到原因并重新提交
- 下架：空间 ADMIN 或平台 `SKILL_ADMIN`/`SUPER_ADMIN`，写审计日志；下架后立即重建索引

---

## 7. 安全扫描（`dsh-trust-check`）

### 7.1 定位与边界

扫描器：**`dsh-trust-check`**（<https://github.com/liuwenji007/dsh-trust-check>，MIT），锁定 `schemaVersion: 2` 的发布版本（首个带 `facts[]` 的 0.2.0 及以上，精确版本在镜像构建时固定）。

它做的是**静态能力披露**：把插件会碰什么（`shell` / `fs-read` / `fs-write` / `network` / `credentials` / `env` / `subagent` / `host-runtime` / `llm` / `dynamic-code`）、注入了什么（`cordis.patch.yml` 的 override/disable、`systemPrompt`、技能正文）、来源是否可核对摊开，每条附 `文件:行号` 与原文片段。

它不是杀毒、不做安全背书：**`未检出` 只表示"本次静态扫描没看到"**，本站任何位置都不得表述为"安全 / 已认证 / 无风险"。

必须写进页面免责说明的能力边界（来自工具的已知局限，直接照抄语义，不改写）：

- 只扫给定目录，**不下载 tarball**——拉包是本站的职责（§7.4）
- 不展开 `node_modules`，裸包 `import 'lodash'` 不跟进
- 运行时拼接的 `import('node:' + x)`、字符串拼 URL、混淆的 `eval`/`Function` 可能绕过规则表
- 扫描发生在安装前，无法阻止插件被别的客户端安装时执行安装脚本
- `score`/`band` 是排序参考，不是裁决依据

### 7.2 扫描时机与闸门

两个节点，缺一不可：

1. **提交时（同步闸门）**：解包/物化 → 扫描 → 按裁决决定是否继续（§6.5）
2. **上架前（一致性复核）**：最新扫描为 `red` 且未放行、或最近一次扫描失败（`FAILED`/`error`）的版本，不得置为 `LISTED`

裁决规则（安装前没有 `trust-ack.json`，不引入 ack 语义）：

| 裁决 | 条件 | 本站处置 |
|---|---|---|
| `red` | `redLines` 非空 | **拒绝提交**，返回红线与证据；`SKILL_ADMIN`/`SUPER_ADMIN` 可"确认风险"放行（§7.6），放行后页面标注、审计留痕 |
| `review` | 无红线，但 `capabilities` 非空或 patch 有 override/disable | 允许继续；上架后详情页**必须**展示能力清单，卡片显示"需确认"徽标 |
| `clear` | 无红线、无特权能力 | 允许继续；详情页仍然展示披露区块（"未检出风险信号"），不得隐藏 |
| `error` | `errors` 非空、`schemaVersion` 不可识别、超时、进程失败 | **fail closed**：提交失败可重试，绝不静默按 `clear` 处理 |

- 闸门只看 `redLines` + `capabilities`（等价于 `verdict()`），不看 `score`/`band`
- `install-script` 红线的特别说明：本站安装文档强制 `--ignore-scripts`，但用户可能用其他客户端或手工安装，因此仍按红线默认拦截；确需收录由管理员按 §7.6 放行
- 不做"扫描服务不可用时放行"的旁路开关。可用性靠队列与重试解决，不靠降低闸门

### 7.3 调用方式（spawn CLI，不 import 包）

后端是 JVM，扫描器是 Node 包，选择**进程隔离 + CLI JSON**而不是把 Node 塞进 JVM：

```sh
dsh-trust-check --dir <productDir> --spec npm:<name>@<version> --json
```

- **不加 `--exit-code`**：本站解析 JSON。加了之后 `review`/`red` 会以非 0 退出，任何 `execFile` 式调用都会把正常结果打进错误分支
- `stdout` 只取 JSON；`stderr` 进日志并脱敏（不得含 Token、URL userinfo）
- 解析顺序固定：`schemaVersion === 2` → `errors` 为空 → `plugins[0]` 存在 → 读字段
- 硬依赖只有五个字段：`name` / `version` / `spec` / `capabilities` / `redLines`；`evidence` / `destinations` / `pathEscapes` / `secretTouches` / `score` / `summary` 仅作展示，**不得**参与闸门判断
- `--spec` 只作来源标签：npm / zip / 文件夹用 `npm:{name}@{version}`；git 用 `git+{url}#{commit}`。spec 解析结果不决定闸门（未知形态只影响"来源是否锁定"的展示）
- 未知 capability 值、`classifyRedLine()` 归到 `raw` 的红线：原样显示英文，不报错（工具承诺只增不减）

**运行位置**：复用现有 `../../../scanner` 服务，新增上传式接口；镜像内装 Node 22 + 精确锁定版本的 `dsh-trust-check`。

```text
POST /scan-plugin-upload      multipart: package=<规范化 tarball>, name, version, spec
→ 200  { schemaVersion, profile:"", dir, generatedAt, plugins:[...], errors:[...] }   # 原样透传
→ 4xx/5xx  { error }          # 解包失败、超限、超时、扫描器异常
```

理由：`../../../scanner` 已有并发闸（`SKILLHUB_SCANNER_MAX_CONCURRENT_SCANS`）、硬超时、upload 模式与内网 only 部署，全部可复用，不必为插件再起一个服务。若后续需要独立扩缩容，仅拆部署，不改后端适配器接口。

**不在生产站点容器内运行扫描**（JVM 镜像不带 Node，也不该带）。扫描器容器约束：

- 非 root 运行；每个请求一个 0700 临时目录，扫描结束立即删除
- 扫描进程**无出网**（静态扫描不需要网络）
- 不执行包内脚本、不 `npm install`、不 `npm pack`；tarball 由本站规范化生成
- 单次扫描预算 120s，服务端 130s 强杀子进程并记 `error`；并发 1；排队等待超过 30s 返回 503 让客户端重试，避免连接堆积

### 7.4 各来源如何拿到待扫目录

**统一原则：扫"将要分发的那份产物"，而不是用户上传的原始目录。** zip / 文件夹先规范化为 npm tarball，再从 tarball 解包扫描，保证私服里的字节与被扫描的字节一致（tarball 的 `sha512` 与扫描指纹一起落库）。

| 提交来源 | 待扫目录 | 备注 |
|---|---|---|
| npm 包名 | 本站从配置 registry 下载 `name@version` tarball（限 50 MiB）后解包 | 下载失败/超限 → `error`，不落库 |
| 内网 Git | `git archive <commit>` 解包后取 `path` 子目录 | 不 clone 全历史、不跑构建；只读 token |
| zip | 规范化后的 tarball 解包 | 与 §6.4 校验共用同一次解包 |
| 文件夹 | 前端打包成 zip → 同 zip | 同 zip |

同一份解压产物同时用于 `package.json` 校验与扫描——两边解压规则不一致会造成"扫的包 ≠ 发的包"，属于供应链绕过，必须禁止。

### 7.5 结果留存与展示

数据结构见 §9.2 / §9.3。展示要点：

- **详情页**：裁决徽标 → 能力 chips → 红线（中文固定文案 + 证据折叠）→ 工作区外路径 / 密钥触摸 / 字面量去向（折叠）→ 扫描器版本、扫描时间、指纹 → 免责与已知局限
- **卡片 / 列表**：只在 `review` / `red` 上显示徽标；`clear` 不带徽标（详情页仍展示区块）
- **审核页**：审核人看完整报告与证据，作为参考而非替代判断
- **安装文档**：§5.3 第 6 条附一行摘要，供 Agent 转述
- **索引文件不含任何扫描字段**——官方协议没有这些字段，多写要冒"整份索引被拒"的风险（§8.5）；披露只走站内页面与安装文档

文案契约（照抄工具的固定值，不自造措辞，也不加工具没观测到的范围）：

| capability | 中文展示 |
|---|---|
| `shell` | 会执行系统命令 |
| `fs-read` | 会读取本机文件 |
| `fs-write` | 会写入本机文件 |
| `network` | 会访问网络 |
| `credentials` | 会接触凭据或密钥 |
| `env` | 会读取环境变量 |
| `subagent` | 会启动子代理 |
| `host-runtime` | 依赖 DSH 宿主运行时 |
| `llm` | 会调用模型 |
| `dynamic-code` | 会在运行时执行代码 |

| redLine code | 中文展示 |
|---|---|
| `install-script` | 安装时会执行脚本（{scripts}） |
| `core-tamper` | 覆盖或禁用了 DSH 核心 bundle（{finding}） |
| `creds-network` | 代码中检出：读取凭据的值，且有网络访问 |
| `plaintext-http` | 代码中检出：明文 http:// 连接（{host}） |
| `literal-ip` | 代码中检出：直接连接 IP 地址（{ip}） |

红线句子用 `classifyRedLine()` 归类后按 code 做 i18n，**不得**用英文整句做匹配（模板会随版本增补）。`plaintext-http` 与 `literal-ip` 每份报告最多各一条，红线条数不等于主机数，展示时不要暗示"共 N 个外连地址"。

**禁用词**：安全、已认证、无风险、通过安全检测。统一用「未检出风险信号」「需确认」「有红线」。

### 7.6 放行、申诉与重新扫描

- **红线放行**：仅 `SKILL_ADMIN` / `SUPER_ADMIN`，必须填理由；写审计（谁、何时、哪个版本、命中哪些 code、哪份指纹）；详情页对所有人显示"管理员已确认风险"与理由摘要，**不**显示为"通过"
- **内容不可变**：同一 `name@version` 的扫描结果不覆盖；改代码必须发新版本
- **重新扫描**：`POST .../rescan`（空间 ADMIN / 平台管理员，限流 5 次/小时/版本），每次新增一条 `plugin_scan_report`，`plugin_version` 的冗余字段指向最新一条
- **升级触发的重扫**：后台任务批量重扫，不改版本状态，只新增报告；若结论由 `clear` 变 `red`，自动下架（`HIDDEN`）并告警——不允许"升级后悄悄变危险"
- **误报反馈**：报告里给"这是误报"入口，带 tool 版本与证据指向上游 Issue；本站不自行改判规则表，只做本节的红线放行

### 7.7 扫描器版本策略

- 镜像内**锁定精确版本**，请求期绝不 `npx` 拉包（供应链与可用性）
- 升级条件：该版本 CHANGELOG 的「Affects catalog results」不为 None 才升；为 None 说明检测结果不变，留在旧版本更安全
- `schemaVersion` 变更必须与解析器同一个 PR 改；解析器维护可读集合 `{2}`，遇到未列出的版本按 `error` 处理并告警（不 fail open）
- 每次扫描记录 `tool` 版本与 `schemaVersion`；升级后跑全量重扫并对比结论差异，差异清单进发布说明
- 升级不 bump `schemaVersion` 时也要重扫（`shouldRescan` 不比较 `tool`），否则存量记录停留在旧规则

### 7.8 与技能扫描的关系

技能侧走 `skill-scanner`（cisco-ai-skill-scanner，产出 `severity`/`findings`/`isSafe`），插件侧走 `dsh-trust-check`（纯静态、零 token、能力披露）。二者**不共用表、不共用语义**：

- 不要用 `isSafe` 的口径解释插件结论，也不要把 `capabilities` 塞进 `security_audit`
- 不要复用技能侧的 `SCANNING`/`SCAN_FAILED` 版本状态机；插件扫描是提交同步闸门，失败直接回滚提交

## 8. 索引文件（`/dsh/index.json`）

### 8.1 访问要求（硬约束）

| 项 | 要求 | 原因 |
|---|---|---|
| 方法 | `GET` 返回 2xx | DSH 只接受 2xx |
| 重定向 | **禁止**（不得 301/302 到别处） | 官方明确要求 |
| 鉴权 | 匿名可读 | DSH 拉取索引不带本站会话 |
| 编码 | UTF-8 JSON | 官方要求 |
| 大小 | ≤ 128 MiB（本期实际远小于） | 官方默认上限 |
| 缓存 | `Cache-Control: public, max-age=60` + `ETag` | 降低反复拉取 |
| 子路径部署 | 完整包含 `SKILLHUB_WEB_BASE_PATH` | 否则图标与详情链接全错 |

部署需新增一条直连后端的路由前缀 `^~ /dsh/`（`index.json`、`icons/`、`install/`），不得被 SPA 的 `index.html` fallback 吞掉。

### 8.2 字段映射

| 索引字段 | 本站取值 |
|---|---|
| `format` | 固定 `1` |
| `title` | `{ "zh": "{siteName} 插件市场", "en": "..." }`（必须含 `en`） |
| `description` | 可选，站点一句话介绍（LocalizedText） |
| `metadata.categories` | 9 类，id 用小写 ASCII slug，title 中英（见 §8.3） |
| `metadata.labels` | 本期留空 |
| `metadata.sortKeys` | 本期不声明 |
| `plugins[].name` | npm 包名 |
| `plugins[].title` / `description` | 包内标题/简介，LocalizedText |
| `plugins[].icon` | `icons/{pluginKey}.svg`（相对路径，与 `index.json` 同级解析） |
| `plugins[].author` | 提交者显示名或空间名 |
| `plugins[].homepage` | 本站详情页绝对 URL |
| `plugins[].repository` | `{ url, directory? }`（来自包或 Git 来源） |
| `plugins[].metadata.category` | 分类 slug |
| `plugins[].versions[]` | 该插件所有 LISTED 版本，倒序 |
| `versions[].version` | 精确 SemVer |
| `versions[].publishTimestamp` | Unix 秒（上架时间） |
| `versions[].source` | `npm`（含 `registry`）或 `git`（`url`+`commit`+`path?`） |
| `versions[].engines.dsh` | 包内声明，可选 |

### 8.3 分类（9 类，对齐 dsh-hub）

| id | 中文 | English |
|---|---|---|
| `agent` | 智能体 | Agents |
| `ui` | 界面 | Interface |
| `memory` | 记忆 | Memory |
| `communication` | 通信 | Communication |
| `devtools` | 开发工具 | Developer Tools |
| `search-knowledge` | 搜索与知识 | Search & Knowledge |
| `data` | 数据分析 | Data Analysis |
| `security` | 安全 | Security |
| `other` | 其他 | Other |

id 用 ASCII 是为了 JSON 可读性与后续排序键扩展；页面展示走 i18n，不用中文 id 拼 URL 参数（避免 `?category=%E6%99%BA%E8%83%BD%E4%BD%93` 这类编码噪音）。

### 8.4 收录条件（缺一不可）

- 插件状态 `LISTED`，未被隐藏、未在黑名单
- 所属命名空间为公开空间
- 至少有一个 `LISTED` 版本，且该版本的 source 可解析
- 该版本的扫描裁决不为 `red`（管理员已确认风险放行的除外，§7.6）
- 插件 `name` 在全索引内唯一（同名包由命名空间区分所有权，但索引只保留一条，冲突时取上架时间最早者并告警）

### 8.5 生成与校验

- 触发：任一插件/版本状态变化后重建（事件驱动），非定时扫描
- 生成：先在服务端拼装 → **强校验** → 写对象存储临时键 → 校验通过后原子替换正式键
- 校验项：`format===1`、`title` 含 `en`、`name` 唯一、SemVer 合法、`commit` 为 40 位十六进制、LocalizedText 结构、图标大小与协议、无 `undefined`/`null` 泄漏到必填位
- **双保险**：CI 中用 DSH 官方索引校验库（`@deepseek-ai/dsh-plugin-market-index`）对最近一次产出的 `index.json` 跑一次；任一条不通过即视为发布阻塞。原因是**单条不合规会让 DSH 拒绝整份索引**，本站无法感知用户端的拒绝。
- 生成失败时保留上一版索引（可用性优先），并告警到管理员；不得产出残缺索引

---

## 9. 数据模型

新增独立领域，**不复用 skill 表**。

### 9.1 `plugin`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | bigint PK | |
| `namespace_id` | bigint | 归属空间，FK |
| `slug` | varchar(64) | 对外标识，空间内唯一 |
| `package_name` | varchar(214) | npm 包名，全站唯一 |
| `category` | varchar(32) | 9 类之一 |
| `title_zh`/`title_en` | varchar | 展示标题 |
| `summary_zh`/`summary_en` | text | 一句话简介 |
| `icon_key` | varchar | 图标对象键 |
| `repository_url` | varchar | 源码仓库 |
| `author_display` | varchar | 索引 `author` |
| `status` | varchar(16) | `DRAFT`/`PENDING`/`LISTED`/`HIDDEN`/`REMOVED` |
| `latest_version_id` | bigint | 冗余指针，便于列表 |
| `latest_scan_verdict` | varchar(16) | 冗余：最新版本裁决 `clear`/`review`/`red`，供卡片徽标与筛选，随 `latest_version_id` 一起维护 |
| `prompt_copy_count` | bigint | 复制提示词次数（冗余计数） |
| `search_vector` | tsvector | PG 全文检索生成列（标题/简介/包名） |
| 审计列 | | `created_at`/`updated_at`/`created_by`/`updated_by` |

### 9.2 `plugin_version`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | bigint PK | |
| `plugin_id` | bigint | FK |
| `version` | varchar(64) | 精确 SemVer，插件内唯一 |
| `source_type` | varchar(8) | `npm` / `git` |
| `npm_registry` | varchar | source_type=npm 时必填 |
| `git_url` / `git_commit` / `git_path` | varchar | source_type=git 时必填，commit 40 hex |
| `engines_dsh` | varchar | 可选 |
| `tarball_key` | varchar | 上传/代发的原始包对象键（留存供追溯） |
| `sha512` | varchar | tarball 指纹 |
| `readme_snapshot` | text | 提交时快照 |
| `metadata_snapshot` | jsonb | 解析出的 package.json 关键字段 |
| `publish_timestamp` | bigint | Unix 秒，写入索引 |
| `status` | varchar(16) | `PENDING`/`APPROVED`/`LISTED`/`REJECTED`/`YANKED` |
| `submission_source` | varchar(8) | `npm`/`git`/`zip`/`folder`（仅内部记录，不进索引） |
| `scan_verdict` | varchar(16) | 最近一次**成功**扫描的裁决 `clear`/`review`/`red`；失败不覆盖该列 |
| `scan_report_id` | bigint | 指向最新一次扫描报告（成功或失败），便于按裁决过滤与展示 |
| `scan_waived` | boolean | 是否经管理员确认风险放行（仅 `red` 可能有值） |

### 9.3 `plugin_scan_report`

一次扫描一行，**不覆盖历史**：重扫、扫描器升级重扫都新增行，`plugin_version.scan_report_id` 指向最新一条。理由与审计要求一致——必须能回答"当时为什么放行/拦截、用的是哪版规则"。

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | bigint PK | |
| `plugin_version_id` | bigint | FK，索引 |
| `status` | varchar(16) | `SUCCESS` / `FAILED` |
| `verdict` | varchar(16) | `clear`/`review`/`red`；`FAILED` 时为空 |
| `capabilities` | jsonb | 能力 code 数组（原样） |
| `red_line_codes` | jsonb | `classifyRedLine()` 后的稳定 code 数组 |
| `summary` | jsonb | 展示用：去向前 N 条、`pathEscapes`/`secretTouches` 计数、注入项、`score`/`band` |
| `report` | jsonb | 完整脱敏报告；超 1 MiB 只存稳定字段 + `summary`，并置 `report_truncated` |
| `report_truncated` | boolean | 报告是否被裁剪 |
| `tool_version` | varchar | 如 `dsh-trust-check@0.2.x`（精确值） |
| `schema_version` | int | 报告 `schemaVersion`，当前 `2` |
| `fingerprint` | varchar | `ackFingerprint`，用于漂移对比与放行绑定 |
| `tarball_sha512` | varchar | 与被扫产物对应，证明"扫的就是发的" |
| `duration_ms` | int | 扫描耗时 |
| `error_message` | text | 失败原因（脱敏：去 Token、去 URL userinfo） |
| `waived_by` / `waived_at` / `waive_reason` | | 管理员放行记录（仅 `red` 可填，必填理由） |
| `created_at` | timestamp | |

### 9.4 `plugin_stat_event`

`id` / `plugin_id` / `plugin_version_id`(nullable) / `event`(`view`|`copy_prompt`|`download`) / `dedup_key` / `created_at`；按 `plugin_id + event + 日期` 聚合到计数表，避免前台每次 `count(*)`。

---

## 10. 接口设计

### 10.1 公开（匿名）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/plugins` | 列表：`q`、`category`、`sort`(`newest`/`name`)、`page`、`size` |
| GET | `/api/v1/plugins/{namespace}/{slug}` | 详情（含版本列表摘要） |
| GET | `/api/v1/plugins/{namespace}/{slug}/versions` | 版本列表 |
| GET | `/dsh/index.json` | 索引文件（§8） |
| GET | `/dsh/icons/{key}` | 相对图标 |
| GET | `/dsh/install/index.md` | 安装文档：索引源配置 |
| GET | `/dsh/install/{namespace}/{slug}.md` | 安装文档：单插件 |
| POST | `/api/v1/plugins/{namespace}/{slug}/stats` | 上报 `view`/`copy_prompt`/`download`（按 `pluginId+event+IP hash+日` 去重） |
| GET | `/api/v1/plugins/{namespace}/{slug}/versions/{version}/security-scan` | 安全披露摘要（§7.5）：裁决、能力、红线 code、证据片段（≤200 字符）、工具版本、时间 |

### 10.2 需登录

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/plugins/submissions` | 提交（`multipart/form-data` 或 JSON + 文件两部分） |
| GET | `/api/v1/plugins/submissions/{id}` | 提交进度与校验结果 |
| GET | `/api/v1/dashboard/plugins` | 我的插件（含 PENDING/REJECTED） |
| GET | `/api/v1/dashboard/namespaces/{slug}/plugins` | 空间维度插件列表（§3.4）：`status`、`q`、`category`、`page`、`size`；需空间成员，非成员 403 |
| PATCH | `/api/v1/plugins/{namespace}/{slug}` | 改分类（空间 ADMIN/作者） |
| GET | `/api/v1/plugins/scan-reports/{id}` | 完整报告（提交者本人 / 空间成员 / 审核人 / 管理员） |
| POST | `/api/v1/plugins/{namespace}/{slug}/versions/{version}/rescan` | 重新扫描（空间 ADMIN / 平台管理员，5 次/小时/版本） |
| POST | `/api/v1/plugins/scan-reports/{id}/waiver` | 红线放行 / 撤销放行（`SKILL_ADMIN`/`SUPER_ADMIN`，必填理由） |

### 10.3 提交请求形态

```jsonc
// type=git
{ "namespace": "team", "sourceType": "git", "category": "devtools",
  "gitUrl": "https://git.internal/team/plugins", "ref": "main", "path": "packages/bar" }

// type=npm
{ "namespace": "team", "sourceType": "npm", "category": "ui", "packageName": "@team/bar", "version": "0.3.0" }

// type=zip / folder：multipart 上传文件 + 同名字段
```

响应返回 `submissionId` 与**同步校验结果**（通过项/失败项/扫描裁决），异步阶段（代发 npm、重建索引）另查 `submissions/{id}`。

```jsonc
// 202 通过闸门（review / clear 都继续）
{ "submissionId": 123,
  "scan": { "verdict": "review", "tool": "dsh-trust-check@0.2.x", "schemaVersion": 2,
            "capabilities": ["network", "fs-write"],
            "redLines": [], "reportId": 456, "fingerprint": "sha256:…" } }

// 422 命中红线（不落版本、不写私服）
{ "error": "plugin.error.scanRedLine",
  "scan": { "verdict": "red", "redLines": ["runs code at install time (postinstall)"],
            "codes": ["install-script"], "reportId": null } }
```

### 10.4 错误语义

| 场景 | HTTP | 提示（i18n key） |
|---|---|---|
| 包名或版本已存在 | 409 | `plugin.error.versionExists` |
| `package.json` 缺失/非法 | 422 | `plugin.error.invalidPackage` |
| Git commit 无法解析 | 422 | `plugin.error.gitUnresolved` |
| zip 超限/含非法路径 | 413 / 422 | `plugin.error.archiveTooLarge` / `archiveUnsafe` |
| 代发私服失败 | 502 | `plugin.error.registryPublishFailed` |
| 命中红线 | 422 | `plugin.error.scanRedLine` |
| 扫描超时 | 504 | `plugin.error.scanTimeout` |
| 扫描服务不可用 / 排队超时 | 503 | `plugin.error.scanUnavailable` |
| 报告不可解析（`schemaVersion` 未知、输出损坏） | 502 | `plugin.error.scanInvalidReport` |
| 重扫超限 | 429 | `plugin.error.scanRateLimited` |
| 无空间权限 | 403 | `plugin.error.noPermission` |

全部走现有本地化领域异常体系，不新增裸 `RuntimeException`。

---

## 11. 权限与治理

| 动作 | 角色 |
|---|---|
| 浏览市场、详情、复制提示词、下载 tarball | 匿名 |
| 查看安全披露摘要 | 匿名 |
| 查看完整报告与证据 | 提交者本人、空间成员、审核人、`SKILL_ADMIN`、`SUPER_ADMIN` |
| 提交插件 | 空间 MEMBER 及以上 |
| 查看命名空间插件列表（§3.4） | 该空间成员（含 PERSONAL 空间所有者） |
| 命名空间内下架 / 恢复 / 改分类 / 重新扫描 | 空间 ADMIN（平台管理员保留全站权限） |
| 审核（需审核空间） | 空间 ADMIN（复用现有审核队列，新增 `PLUGIN_VERSION` 类型） |
| 改分类 | 空间 ADMIN / 插件所有者 |
| 重新扫描 | 空间 ADMIN、`SKILL_ADMIN`、`SUPER_ADMIN`（限流） |
| 红线放行 / 撤销放行 | `SKILL_ADMIN`、`SUPER_ADMIN`（必填理由） |
| 下架、恢复 | 空间 ADMIN、`SKILL_ADMIN`、`SUPER_ADMIN` |
| 黑名单（全站禁止上架） | `SUPER_ADMIN` |

- 提交、上架、下架、删除、审核决定**必须写审计日志**（沿用现有审计设施与新事件类型），符合 AGENTS.md 对发布/删除/授权链路的审计要求。
- **扫描相关审计**：红线放行 / 撤销、重新扫描、扫描器版本变更触发的自动下架，都要写审计（含版本、`redLine` code、`fingerprint`、操作人、理由）。
- 私有空间插件不进公开索引，但仍在站内可见（仅空间成员）。
- 删除为软删；已发布版本保留 tarball 与扫描报告供追溯，不可被同名同版本覆盖。

---

## 12. 统计

- 事件：`view`（详情页曝光，1 次/会话/插件）、`copy_prompt`（复制安装提示词）、`download`（站内下载 tarball；仅 `仅收录` 类插件存在）
- 对外展示：卡片与详情页显示 `复制提示词次数`
- 仅后台可见：浏览数、下载数、时间趋势
- 明确不展示"安装量/用户数"——站内无法确认用户是否在 DSH 内安装成功，私服统计也含 CI 拉包噪声（私服指标本期不接）

---

## 13. 前端改造点

| 文件/区域 | 改动 |
|---|---|
| `app/layout.tsx` | `navItems` 移除 `publish`，新增 `plugins`（`t('nav.plugins')`, `/plugins`, `navigation.plugins.open`）；页脚保留发布链接 |
| `app/router.tsx` | 新增 4 条路由（§3.2），`/dashboard/plugins*` 走 `requireAuth` |
| `pages/search.tsx` | 页头按钮组：保留/补齐「我的技能」，新增「发布技能」（登录可见），`data-analytics-id="skill.publish.open"`；与插件市场页头同一布局与顺序 |
| `pages/plugins.tsx`（新） | 两模块页面，拆 `PluginIndexSourceCard`、`PluginGrid`；页头「我的插件」+「提交插件」。**不渲染任何提示词模块** |
| `pages/plugin-detail.tsx`（新） | 详情 + 版本切换 + 提示词复制（全站唯一的提示词生成点）+ **安全披露区块**（`PluginSecurityDisclosure`：裁决徽标、能力 chips、红线、证据折叠、免责与已知局限） |
| `pages/dashboard/plugin-submit.tsx`（新） | 四来源表单（来源切换用 Tabs）；提交返回后展示扫描裁决，`red` 时列出红线证据并引导"改包重提" |
| `pages/dashboard/my-plugins.tsx`（新） | 列表 + 状态 + 重新提交；增加裁决列与「重新扫描」入口 |
| `pages/dashboard/namespace-plugins.tsx`（新） | 命名空间维度插件列表与管理（§3.4），复用 `features/plugin/` 的表格与操作组件 |
| `features/namespace/namespace-management-nav.tsx` | `tabs` 增加 `plugins`（文案 `namespaceManagement.plugins`），`active` 类型扩展 |
| `pages/dashboard.tsx` | 侧栏「技能与数据」组在「我的技能」之后新增「我的插件」；概览卡片区新增「我的插件」卡 |
| `app/router.tsx` | 新增 `/dashboard/namespaces/$slug/plugins`（`requireAuth`），沿用既有空间守卫 |
| `features/plugin/`（新） | query hooks、来源表单、安装提示词组件、复制逻辑、插件列表表格（供「我的插件」与命名空间共用） |
| `features/plugin/scan/`（新） | 裁决→徽标映射、`capability`/`redLine` code→中文文案表（§7.5）、证据折叠、`useSecurityScan` 查询 |
| i18n `zh/en/ko/ru.json` | 新增 `nav.plugins`、`sidebar.myPlugins`、`overview.myPlugins`、`namespaceManagement.plugins`、`plugin.*` 命名空间（含 `plugin.scan.*`：10 个能力、5 个红线 code、免责文案）；四语言必须齐全（现有 locale 测试会校验键一致性） |
| `api/generated` | `make generate-api` 重新生成，**不手改** |

交互约束（沿用 AGENTS.md）：服务端状态走 TanStack Query，禁止 `useEffect` 拉取；复用既有 `Button`/`Card`/`Badge`/`cn()`；错误用 `console.error`，不打印凭据。

披露区块的硬性文案约束：只允许「未检出风险信号 / 需确认 / 有红线 / 管理员已确认风险」四种状态表述，禁止出现"安全 / 已认证 / 无风险"（§7.5 禁用词）。

---

## 14. 后端落地

| 层 | 位置 | 内容 |
|---|---|---|
| domain | `skillhub-domain`：`domain/plugin` | `Plugin`、`PluginVersion`、`PluginCategory` 枚举、仓储接口、`PluginIndexAssembler`（纯业务拼装）、`PluginPackageInspector`（package.json 解析与校验）、`PluginScanVerdict` 枚举与裁决规则（纯函数，不做 IO） |
| 应用服务 | `skillhub-app`：`application/plugin` | `PluginSubmissionService`（四来源编排，扫描在代发之前）、`PluginIndexService`（重建与原子替换）、`PluginQueryService`、`PluginScanService`（裁决 → 放行/重扫编排）、`PluginScanBackfillTask`（升级后批量重扫） |
| 控制器 | `skillhub-app`：`controller/plugin` | `PluginController`、`PluginIndexController`（`/dsh/**`，`text/markdown` / `application/json`）、`PluginSubmissionController`、`PluginSecurityScanController`（摘要/完整报告/重扫/放行） |
| 适配器 | `skillhub-infra`：`plugin` | `NpmRegistryPublishAdapter`（接口 + HTTP 实现，配置 `registry`+`token`）、`GitSourceResolver`（https + 只读 token）、`PluginIconStore`（复用 `skillhub-storage`）、`PluginTrustScanAdapter`（调 scanner `/scan-plugin-upload`，超时/重试/降级语义封在适配器内，不泄漏到应用层） |
| 扫描服务 | `../../../scanner` | 新增 `POST /scan-plugin-upload`（解包 → spawn Node CLI → 透传 `AuditResponse`）；镜像加 Node 22 + 精确锁定的 `dsh-trust-check`，org 内自带镜像源与校验和 |
| 搜索 | 复用 PG 全文检索 | `plugin.search_vector` 生成列 + GIN，不改搜索 SPI |

- 私服对接放适配器接口后，Verdaccio/Nexus 差异不进入业务层；凭据只从配置读取，不落日志、不进索引。
- 代发 npm 的安全边界：不执行包内脚本（`--ignore-scripts` 语义）、只读解析、tarball 规范化为 `package/` 根结构。
- 扫描边界：只在 scanner 服务内 spawn 子进程，后端容器不带 Node；扫描器进程无出网、非 root、per-request 临时目录。
- 索引文件与图标、安装文档走对象存储 + 短缓存，不依赖数据库直查。

---

## 15. 非功能约束

| 类别 | 要求 |
|---|---|
| 性能 | 列表 P95 < 300ms（24 条）；`/dsh/index.json` 生成 P95 < 1s（500 插件规模）；索引走 CDN/对象存储缓存 |
| 扫描性能 | 单包扫描 P95 ≤ 15s（10 MiB / ≤1000 文件）；硬超时 120s；扫描不阻塞列表与 `/dsh/*`；并发 1，排队超 30s 返回 503 |
| 可用性 | 索引生成失败保留上一版；`/dsh/*` 不因登录态或审核队列故障返回 5xx；扫描服务不可用时提交 fail closed 并给明确提示与重试入口 |
| 安全 | 索引与安装文档匿名可读且不含任何 Token；Git 凭据仅服务端持有；zip 解压防穿越/防炸弹；图标仅允许位图与 SVG 且限制大小 |
| 扫描安全 | 扫描进程无出网、非 root、per-request 临时目录；不执行包内脚本；证据片段脱敏（Token、URL userinfo）；报告与错误信息不落凭据 |
| 兼容 | 索引 `format: 1` 跟随 DSH 官方；协议变更时新增生成器版本，不原地改语义；扫描报告 `schemaVersion` 可读集合固定，未知版本 fail closed |
| i18n | 索引内 LocalizedText 必含 `en`；页面四语言键齐平；`capability`/`redLine` code 文案四语言齐平 |
| 可观测 | 索引重建成功/失败、代发私服成功/失败、提交各阶段耗时打点；失败告警到管理员 |
| 扫描可观测 | 扫描耗时、裁决分布（clear/review/red/error）、失败原因、放行次数、扫描器版本打点；红线命中与 `clear→red` 漂移告警到管理员 |
| 部署 | 新增 `^~ /dsh/` 直连后端路由；子路径部署必须正确拼接 base path；scanner 镜像新增 Node 运行时（体积/启动时间变化写进发布说明） |

---

## 16. 验收标准

**A. 索引源可用**

1. Given 已上架 1 个 npm 来源插件，When 用户在 DSH 打开自定义插件索引并添加 `{site}/dsh/index.json`，Then 「发现」页能看到该插件卡片，卡片显示推荐版本与来源。
2. Given 索引文件被请求，Then 返回 200、无重定向、`Content-Type: application/json`、UTF-8，且 `format===1`。
3. Given 索引内含相对图标 `icons/x.svg`，Then DSH 能解析到 `{site}/dsh/icons/x.svg` 并正常显示。
4. Given 索引中任一插件条目缺 `versions` 或 version 非 SemVer，Then 本站的生成校验必须拦截，不得产出该文件（对应"整份索引被拒绝"的防护）。

**B. 安装提示词（仅详情页）**

5. Given 用户打开 `/plugins`，Then 页面只有「索引源」「插件列表」两个模块，**不存在**任何市场页级提示词代码块或"复制提示词"按钮；索引源地址仍可复制。
6. Given 用户打开某插件详情，When 点击复制，Then 提示词为 `请按照 {site}/dsh/install/{ns}/{slug}.md 安装 DSH 插件 {npmName}。`，包名为 npm 包名（非 `namespace/slug`），站点地址含正确 base path。
7. Given Agent 读取 `/dsh/install/{ns}/{slug}.md`，Then 文档含包名、精确版本、Profile 占位、`--ignore-scripts`、校验与卸载命令，且不含任何 Token。

**C. 四种来源提交**

8. npm 包名：给定存在的包与版本 → 提交成功 → 详情页显示 `source.type=npm` 与 registry → 索引出现该版本。
9. 内网 Git：给定 https 仓库 + `path` → 解析出 40 位 commit → 详情页显示 git 来源 → 索引 `versions[].source.type=git` 且 commit 与仓库实际一致。
10. zip：上传合法包 → 后端代发私服成功 → 索引出现 npm 来源；上传缺 `package.json` 的包 → 422 且不进索引。
11. 文件夹：选择目录 → 前端打包为 zip → 与 zip 结果一致；含 `..` 路径或超限 → 拒绝并给出具体原因。
12. 版本冲突：重复提交同 `name@version` → 409，且私服与索引均未被写入。

**D. 导航与发布**

13. Given 任意页面，Then 顶部导航为 首页/插件市场/技能市场/技能套件/控制台，且无「发布」。
14. Given 登录用户在 `/search`，Then 页头出现「发布技能」，点击进入 `/dashboard/publish`。
15. Given 从「我的技能」触发"更新版本"，Then 仍能跳转 `/dashboard/publish?namespace=...&updateSkill=...` 并正常完成发布（回归验证未被导航改动破坏）。
16. 页脚「发布」链接仍可达。

**E. 治理**

17. 需审核空间的提交 → 进审核队列（类型 `PLUGIN_VERSION`）→ 通过后才出现在索引；拒绝则不出现。
18. 下架已上架插件 → 索引重建后该插件消失，且审计日志有记录。
19. 非空间成员访问 `/dashboard/plugins/submit` → 403 或空命名空间列表，不泄漏他人空间。

**F. 统计**

20. 复制提示词 1 次 → 卡片计数 +1；同一 IP 同日重复上报只计 1 次；浏览与下载不对外显示。

**G. 安全扫描（§7）**

21. Given 一个无特权能力的包，When 提交并上架，Then 详情页显示「未检出风险信号」与披露区块（区块不得因 `clear` 而隐藏），且全站任何位置都不出现"安全 / 已认证 / 无风险"字样。
22. Given 一个含 `network` + `fs-write` 的包，Then 裁决为 `review`，允许上架，详情页展示两条能力 chips，卡片带"需确认"徽标。
23. Given `package.json` 声明 `postinstall`，Then 裁决 `red`，提交返回 422 `plugin.error.scanRedLine`，私服无该 `name@version`、无 `plugin_version` 记录、索引不变，红线证据含 `文件:行号` 与片段。
24. Given 源码含明文 `http://` 外连，Then `red_line_codes` 含 `plaintext-http`，页面展示中文固定文案并带 host，不使用英文整句匹配。
25. Given 扫描服务返回 500 或超时，Then 提交失败（fail closed）并提示可重试；服务恢复后重试成功，且不得在该失败过程中静默生成 `clear` 记录。
26. Given 空目录或损坏 tarball，Then 报告 `errors` 非空 → 按扫描失败处理，不落库。
27. Given 管理员对 `red` 版本放行并填写理由，Then 该版本可 `LISTED`，详情页显示"管理员已确认风险"+理由摘要，审计日志含版本、code、指纹、操作人。
28. Given 扫描器返回未知 `schemaVersion`，Then 请求被拒绝并告警，不进入 `clear`。
29. Given 扫描器升级后重扫使某版本由 `clear` 变 `red`，Then 该插件自动 `HIDDEN` 并告警，索引重建后消失。
30. Given 同一 `name@version` 已扫描，Then 重扫新增 `plugin_scan_report` 行而不覆盖历史，`plugin_version.scan_report_id` 指向最新一条。

**H. 我的插件入口与命名空间插件管理（§3.3 / §3.4）**

31. Given 登录用户打开 `/plugins`，Then 页头出现「我的插件」（次级）与「提交插件」（主按钮），点击前者进入 `/dashboard/plugins`；未登录时两个按钮都不渲染。
32. Given 登录用户打开 `/dashboard`，Then 侧栏「技能与数据」组在「我的技能」之后出现「我的插件」并正确高亮，概览卡片区同时有「我的插件」卡；四语言均无缺失键。
33. Given 用户打开 `/dashboard/namespaces/test`，Then tab 顺序为 技能 / 插件 / 成员 / 审核 / 设置（成员仅 TEAM、审核按权限显示），点击「插件」进入 `/dashboard/namespaces/test/plugins` 并展示该空间插件。
34. Given 空间 ADMIN 在空间插件 tab 下架一个已上架插件，Then 状态变 `HIDDEN`、索引重建后该插件从 `/dsh/index.json` 消失、审计日志有记录；点击「恢复」可回到 `LISTED` 并重新进索引。
35. Given 非空间成员访问 `/dashboard/namespaces/other/plugins` 或直接调该列表接口，Then 返回 403 / 空态，不泄漏该空间插件清单。

---

## 17. 风险与开放问题

| # | 风险/问题 | 影响 | 应对 |
|---|---|---|---|
| R1 | 官方索引协议演进（现 `format:1` 为实验特性） | 索引可能被新版 DSH 拒绝 | 生成器版本化 + CI 用官方校验库 + 保留灰度开关回退到上一版文件 |
| R2 | 单条不合规导致**整份索引被拒**，用户端静默失败 | 市场整体不可用 | 生成时强校验 + 原子替换 + 管理员告警 + 冒烟脚本拉取校验 |
| R3 | 代发任意代码到私服 | 供应链风险 | 提交即扫（§7）红线默认拦截在代发之前、原始包留存与 `sha512` 指纹、`--ignore-scripts`、管理员放行必须留痕 |
| R4 | 内网 Git 凭据泄漏 | 合规风险 | 只读 deploy token、仅服务端持有、禁止进日志/索引/文档 |
| R5 | 浏览器目录上传兼容性（`webkitdirectory` 在部分浏览器受限） | 文件夹来源不可用 | 页面提示"不支持时请打包为 zip"，zip 为主路径 |
| R6 | 索引公开 vs 私有插件 | 私有插件意外外泄 | 索引仅收公开空间 + `LISTED`；生成前双重过滤 |
| R7 | 私服实现未定 | 发布适配器无法联调 | 接口先冻结，用 mock registry 跑通单测与 e2e |
| R8 | 静态扫描漏判被当成"安全" | 用户误信、事故归因困难 | 文案契约禁用"安全/已认证"；详情页常驻免责与已知局限；"未检出"不等于"无风险"写进报告与安装文档 |
| R9 | 误报阻塞正常内部插件 | 业务无法上架 | 管理员确认风险放行（写审计、页面可见）；误报反馈到上游 Issue；本站不自行改判规则表 |
| R10 | 扫描器升级后结论漂移 | 已上架插件结论失效 | 锁定版本 + 仅按 CHANGELOG「Affects catalog results」升级；升级后全量重扫，`clear→red` 自动下架并告警 |
| R11 | 扫描成为 DoS 面（压缩炸弹/超大包/慢包） | 扫描服务不可用，提交链路雪崩 | 复用 50 MiB / 2000 文件限制、per-request 临时目录、120s 硬超时、并发 1、排队超时 503 |

**待确认（不阻塞开发启动）**

- Q1 分类是否允许后续运营增删（当前固定 9 类写死在前端与索引发声明里，增删需要重建索引）。
- Q2 是否允许同一 npm 包被两个命名空间收录（当前设计：索引内 `name` 唯一，后者提交直接 409）。
- Q3 图标自动提取优先级：包内 `icon` 字段 → README 首图 → 首字母占位，是否认同。
- Q4 索引文件是否额外提供 `?category=` 分片（当前不做，避免多份索引不同步）。
- Q5 红线放行的有效期：当前绑定版本指纹，内容不变即长期有效；是否需要半年复核一次？
- Q6 是否把披露摘要写进索引（如 `description` 追加能力标签）——当前不做，协议无字段且要冒"整份索引被拒"的风险。

---

## 18. 交付计划（建议）

| 阶段 | 内容 | 出口条件 |
|---|---|---|
| P1 骨架 | 数据模型、提交（npm/zip）、索引生成与 `/dsh/*`、**两模块市场页**、导航改造、市场页/控制台「我的插件」入口、**扫描闸门最小闭环**（scanner 接口 + Node 运行时 + 四裁决分支） | 验收 A/B/C(部分)/D/H(31–33) 通过，G 中 21–26 通过 |
| P2 治理 | 审核接入、Git 来源（含扫描）、文件夹上传、**命名空间插件 tab 与管理动作**、下架与审计、我的插件完整态、**重扫/放行/申诉、升级漂移重扫任务** | 验收 C/E/H(34–35) 通过，G 中 27–30 通过 |
| P3 统计与打磨 | 三事件统计与展示、i18n 四语言（含扫描文案契约）、冒烟脚本、CI 索引校验、扫描器版本升级流程演练 | 验收 F 通过，索引漂移检查进 CI |
