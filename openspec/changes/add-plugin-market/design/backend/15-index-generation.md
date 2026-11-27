# §8　字段映射、分发来源判定、pluginKey、构建期强校验

> 所属：后端　｜　覆盖章节：§8
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

## 8. 索引生成与发布

### 8.1 字段映射（索引字段的取值规则；PRD 已不含字段映射表）

**收录前置条件（`PRD §8.2` v2.16，缺一不可；不满足即整插件不进索引——这是**过滤**，不是构建失败）**

| # | 条件 | 不满足时 |
|---|---|---|
| 1 | 插件 `status = 'LISTED'`、未下架（`removed_at IS NULL`）、未在黑名单 | 不进索引；对外原因按 `PRD §3.4` 的既有字面量（「未收录：无已上架版本」/「未收录：最新版本裁决为有红线」） |
| 2 | 所属命名空间为**公开空间**（`namespace.is_public = true`） | 不进索引；对外原因「未收录：空间为私有」 |
| 3 | **`plugin.visibility = 'PUBLIC'`**（v2.16 新增） | 不进索引；对外原因「未收录：可见范围非公开」（`PRD §3.4`，**既有字面量，不新造词**） |
| 4 | 至少一个 `LISTED` 版本，且该版本分发来源可解析（§8.1.1、§8.4 第 16 条） | 不进索引；但"来源探测不确定"（超时 / 5xx）按 §8.1.1 第 3 步**整份构建失败**，与第 1–3 条的"过滤"语义不同，不得混为一谈 |

第 2、3 条是**两个正交维度**，不能互相替代：`visibility='PUBLIC'` 落在私有空间里仍不进索引（`PRD §8.4` 第 4 行），反过来 `NAMESPACE_ONLY` 落在公开空间里也不进索引（第 2 行）。

**"能不能看"只有一个判定入口（新增，`PRD §8.4` v2.16）**：`PluginVisibilityPolicy.evaluate(plugin, namespace, actor)` 是纯函数式判定，返回 `{ inIndex, canView, statusKey, noticeKey }`，供**详情页、安装文档、图标、包内文件、站内列表与索引生成**六处共用；任何第二处"自己按 `is_public` 或 `visibility` 拼逻辑"的实现都算缺陷（与 §8.1.1 的 `resolveDistributionSource` 同一约束）。索引生成只读 `inIndex` 一个布尔，不看 `statusKey`；站内渲染只读 `statusKey` / `noticeKey`，不重新判范围——两条消费路径都不许再碰原始字段。

| 索引字段 | 取值规则 | 缺省/异常处理 |
|---|---|---|
| `format` | 常量 `1` | 不可配置 |
| `title` | `{ zh: "{siteName} 插件市场", en: "{siteName} Plugin Market", ko: "{siteName} 플러그인 마켓", ru: "{siteName} Маркетплейс плагинов" }`；`siteName` 取 `SitePresentationService` 的 `siteName` | `siteName` 为空 → 用 `SkillHub`；**`en` 必须有值**，缺失即构建失败 |
| `description` | 站点一句话介绍（LocalizedText），来自站点展示设置 | 缺失即整体省略该键（不写 `null`） |
| `metadata.categories` | **可见标签清单**：与技能侧同一张 `label_definition`，取 `visible_in_filter = TRUE` 且未删除者，按 `sort_order` 升序；`id` = 标签 slug，`title` 取 `label_translation`（`PRD §8.3` v2.21） | 词表为空 → 输出空数组（**不省略该键**）；`ko` / `ru` 缺翻译回落 `en`，`en` 必需 |
| `metadata.labels` | 不输出 | — |
| `metadata.sortKeys` | 不输出（`PRD §4.2` 明确不声明外部指标） | — |
| `plugins[].name` | `plugin.package_name`（**不是** `namespace/slug`） | — |
| `plugins[].title` | `{zh: title_zh, en: title_en}`；`title_en` 缺失时回落 `package_name` | 两字段皆空 → `package_name` |
| `plugins[].description` | `{zh: summary_zh, en: summary_en}` | 两字段皆空 → 省略该键 |
| `plugins[].icon` | `icons/{pluginKey}.{ext}`（相对路径，与 `index.json` 同级解析）。SVG 图标即团队裁决的 `icons/{pluginKey}.svg`；位图按实际字节类型取 `png`/`jpg`/`webp`，不把 PNG 改名成 `.svg`。**图标唯一来源是包内声明**，≤32 KiB，校验规则见 §4.2 第 6 条 | `icon_key` 为空 → **省略该键**（不写占位图 URL，占位由站内前端做）；非法图标在物化阶段就被拦下，不可能走到索引生成 |
| `plugins[].author` | `plugin.author_display`；为空回落空间 `display_name` | 两者皆空 → 省略 |
| `plugins[].homepage` | 详情页绝对 URL：`{publicBaseUrl}{basePath}/plugins/{ns}/{slug}` | base url 未配置 → **构建失败**（不能产出相对 homepage） |
| `plugins[].repository` | `plugin.repository_url` → `{url}`；一插件一仓库、包在根目录，**不输出 `directory` 字段** | 无仓库 → 省略 |
| `plugins[].metadata.category` | 该插件的**主标签**：`plugin_label` join `label_definition`，取 `sort_order` 数值最小者的 slug | **无标签 → 整体省略 `category` 字段**（不写空串、不写占位；该字段不在 required 内） |
| `plugins[].versions[]` | 该插件全部 `status='LISTED'` 版本，按 `publish_timestamp` **倒序** | 0 个 → 整个插件不进索引（`versions` 不能为空） |
| `versions[].version` | 精确 SemVer | 生成期校验，非法即构建失败 |
| `versions[].publishTimestamp` | `plugin_version.publish_timestamp`（Unix 秒，上架时间；不是提交时间） | 为 NULL 的 LISTED 版本视为缺陷 → 构建失败 |
| `versions[].source` | **只由 `resolveDistributionSource(version)` 的判定结果填充**（§8.1.1）：镜像里有该包该版本 → `{type:"npm", registry}`；明确 404 才 → `{type:"git", url, commit}`（包固定在仓库根目录，**无 `path`**）。**不按 `submission_source` 回填、不双写** | registry 为空 / commit 非 40 hex → 构建失败；探测超时或 5xx → **构建失败并保留上一版**（不降级）；两者都无 → 构建失败（§8.4 第 16 条） |
| `versions[].engines.dsh` | `plugin_version.engines_dsh` | 为空 → `engines` 整体省略；**不得**填入"未声明"等文案 |

不写入索引的内容（硬约束）：任何扫描字段（裁决、能力、红线、指纹、工具版本）、任何凭据（token、URL userinfo）、任何 `null`、任何 `undefined`、站内统计数字。

**对已有索引的影响**：`directory` 与 `source.path` 两个字段已随子目录口径删除（包固定在仓库根目录）。v1 尚未上线，既有索引中不会存在用户子目录语义的这两个字段；生成期不再输出它们，强校验清单也不再接受其存在。若从旧草案索引迁移，含 `directory`/`source.path` 的条目会被下次重建覆盖为不含这两字段的版本，DSH 端忽略未知字段、不受影响。

### 8.1.1 分发来源判定：`resolveDistributionSource(pluginVersion)`（唯一判定点）

`PRD §8.6` v2.15 + `§13` R13 的口径是「每个条目的分发来源与用户的上传方式无关」。落地方式：**把判定收敛成一个方法，索引生成与详情页共用，任何第二处判定都算缺陷**。

```java
// domain/plugin/PluginDistributionSourceResolver.java
public sealed interface DistributionSource {
    record Npm(String registry) implements DistributionSource {}
    record Git(String url, String commit) implements DistributionSource {}
    enum Unresolved implements DistributionSource { NONE }   // 两者都无 → 调用方必须让构建失败
}

public interface PluginDistributionSourceResolver {
    // 唯一入口。入参：版本行 + 本次构建的探测上下文（记忆化表 / 超时预算 / 并发闸）
    DistributionSource resolve(PluginVersion version, ProbeContext ctx);
}
```

判定顺序（**固定，不许调换**）：

| 步骤 | 条件 | 结果 |
|---|---|---|
| 1 | 该 `name@version` 在内部 npm 镜像里**存在**（`GET {registry}/{name}/{version}` 200 且 tarball 可取） | `Npm(registry)` |
| 2 | 镜像返回**明确 404**（HTTP 404 / `E404`） | 退 `Git(git_url, git_commit)`；`source_type='git'` 时 `git_commit` 必非空（DB 约束已保证） |
| 3 | 镜像返回**超时 / 5xx / 连接失败 / 429 / 401**——任何"我不确定"的答案 | **构建失败**：不切换指针、保留上一版索引、`last_error` 记首条、P1 告警（`PRD §8.6`："生成失败时保留上一版索引继续服务"） |
| 4 | 镜像 404 **且**该版本没有可用的 `git_url + git_commit` | `Unresolved` → **构建失败**（§8.4 第 16 条）；该版本已不满足收录条件（§8.2），须补制品或下架 |

**为什么"抖动必须失败、绝不能降级到 git"（红线）**：降级看起来更"可用"，但它会让同一个 `name@version` 在两次构建里给出**两个不同的安装路径**——第 41 代索引写 npm，第 42 代因为镜像一次 502 改写成 git。后果有两层：① **用户装到的那份可能不是详情页承诺的那份**（git 路径依赖仓库地址 + 提交号仍可用，而 sha512 断言管不到"索引到底写了什么"这一层）；② 索引变成**抖动放大器**：镜像抖一下，全站上传型来源的条目集体改写成 git，DSH 端缓存与安装行为随之翻动。`PRD §13` R13 的立论正是"索引写什么就装什么"——**不确定的答案不允许写进索引**。宁可陈旧一分钟（甚至等到运维修好镜像），也不给 DSH 一份会抖的索引。

探测的工程约束：

| 约束 | 值 / 做法 | 理由 |
|---|---|---|
| 超时 | 单次 `PT2S`（连接 `PT1S`），整体预算 `PT20S` 覆盖全量条目 | 500 个条目不能把构建从 3s 拖到几分钟；超总预算即按上表第 3 步构建失败 |
| 并发 | 有界并发 8；单次构建内**每个 `name@version` 只探一次**（`Map<name@version, DistributionSource>` 记忆化） | 同一版本可能被索引与详情页同时问；重复探测既慢又可能出现两次答案不一致 |
| 缓存 | **不做跨构建缓存**（不进 Redis、不做进程内长 TTL） | 一次构建内记忆化就够。跨构建缓存会让"镜像刚补好制品"晚一个 TTL 才生效，也会把 401 这类**可修复**错误粘住 |
| 失败可见 | 探测失败必须带 `{name, version, status, cause}` 进告警 `detailJson` | 运维要能一眼看出"是全挂还是个别条目" |
| 详情页复用 | 详情页「分发来源」**调用同一个方法**（仅此调用点允许 60s 只读缓存） | `PRD §8.6`："详情页「分发来源」显示的是这个判定结果，不是用户当初选了哪种上传方式" |
| 写索引 | `source` **只由本方法结果填充**；`plugin_version.submission_source` 永不进索引、永不进详情页 | 上传方式是内部实现（`PRD §6.2`：不得出现在任何用户可见文案里） |

**两处调用方共用同一方法的验收口径**（就是 §16 S18）：zip 来源上架版本、镜像里有制品 → 索引写 `npm`、详情页显示 npm registry；把该版本从镜像移除 → 索引写 `git`、详情页显示仓库地址 + 提交号；让镜像返回 502 → **索引不变（保留上一版）**、告警触发、详情页沿用上一次成功判定的结果并标注"来源判定暂不可用"。

### 8.2 `pluginKey` 规则

```
pluginKey = lower(hex(sha1(utf8(package_name))))[0:16]
```

理由：包名可能是 scoped（`@team/bar`），直接做路径段需要 URL 转义（`%40team%2Fbar`），DSH 与各类代理对 `%2F` 的处理不一致；16 位十六进制的碰撞概率在 `n ≪ 2^32` 时远低于可接受阈值（本场景插件数 ≤ 数千）。碰撞不作为运维关注点，但生成期可加断言（同一索引内 `pluginKey` 唯一，冲突即构建失败并告警）。

### 8.3 键名与指针对象约定

| 对象 | 键 | 特性 |
|---|---|---|
| 索引正文 | `dsh/index/v{gen:010d}.json` | 不可变（内容寻址语义由 `gen` 保证；同 `gen` 不重写） |
| 指针 | `dsh/index/current.json` | 覆盖 PUT；单键 PUT 在 S3 与本地文件系统实现上都是原子可见（读方要么旧要么新） |
| 图标 | `dsh/icons/{pluginKey}.{svg\|png\|jpg\|webp}` | 不可变（包内容变则版本变，但图标键不随版本变——见下） |
| tarball 留存 | `plugin/tarballs/{pluginKey}/{version}/{sha512[0:16]}.tgz` | 不可变，用于追溯 |
| 提交暂存 | `plugin/staging/{submissionId}/package.tgz` | 状态机终态后 24h 清理 |

图标键**不含版本**：图标属于插件级元数据，取"最新 LISTED 版本"的图标；上传新图标即覆盖同键（覆盖 PUT），无需版本化——如果做得版本化，索引里的相对路径就得随版本变化，反而破坏 DSH 的缓存。

指针对象结构：

```json
{
  "format": 1,
  "gen": 42,
  "key": "dsh/index/v0000000042.json",
  "sha256": "9f2c…",
  "size": 182734,
  "builtAt": "2026-11-25T10:12:31Z",
  "previous": "dsh/index/v0000000041.json"
}
```

`previous` 字段让回退变成一次可审计的指针覆盖（运维手册见 §12.4），不需要重新生成。

**关于"S3 无 rename"**：`PRD §8.6` 写的"写临时键 → 原子替换正式键"在 S3 上做不到（无 rename）。替代机制是"不可变版本键 + 指针覆盖 PUT"。正确性依据：S3 的 PUT Object 是原子的，读方要么看到旧对象要么看到新对象，不存在读到半个对象的窗口；而"覆盖正式键"的做法反而会让 DSH 在高频拉取时命中半写状态（S3 保证不会，但本地文件实现会因为覆盖写而短暂截断）。因此版本化键在两种存储实现上都安全。

**发布时延的目标值（端到端 ≤ 2 分钟）**：从"代发成功 / 下架 / 撤版 / 挂摘标签"这类改变可见性的动作提交，到 DSH 用默认 60s 缓存能拉到新索引，预算拆解如下（同时也是 §12.1 的 SLO 与 §12.3 的告警基线）：

| 环节 | 预算 | 依据 |
|---|---|---|
| 动作落库 → `markDirty` | 同步（同事务） | 不排队、不发事件，写一行 `plugin_index_state.is_dirty = true` |
| `markDirty` → 下一轮 worker 认领 | ≤ 10s | `SKILLHUB_PLUGIN_INDEX_INTERVAL_MS=10000`（最坏等待一个间隔） |
| 认领 → 生成 + 17 条校验 + 指针覆盖 | P95 < 1s @ 500 插件 / P99 < 3s | 单表扫描 + 一次 PUT；500 插件约 2–3 MB，本地/内网 RTT 可忽略。**分发来源探测（§8.1.1）不在这 1s 内**：它有独立预算 `PT20S`，与生成耗时分开计量（§12.1） |
| 指针切换 → DSH 下次拉取 | ≤ 60s + ε | `Cache-Control: public, max-age=60`；ETag 命中时 304 不加大体传输 |
| 合计（最坏可感延迟） | **≤ 72s，取整承诺 ≤ 2 分钟** | 给 worker 抖动、S3 慢请求留 48s 余量 |

超过 5 分钟未生效（`plugin_index_state.dirty = true` 且 `updated_at` 距今 > 5min）触发告警，见 §12.3。文案上"通常 1 分钟内生效"由产品侧决定，本设计只保证 2 分钟这个上界。

### 8.4 强校验清单（构建期，任一失败即不切换指针）

1. JSON 可被严格解析（无重复 key、无尾逗号、无 BOM）。
2. `format === 1`。
3. `title` 存在且 `title.en` 是非空字符串。
4. `plugins[].name` 全索引唯一（冲突：**断言失败**并告警，不用"取最早者"降级——已裁决为提交时 409，见 §1.3 D6）。
5. `plugins[].name` 符合 npm 包名正则（含 scoped）。
6. `versions` 非空，每个 `version` 是精确 SemVer（不接受 range、不接受 `v` 前缀）。
7. `versions[].source.type ∈ {npm, git}`，且字段自洽（`npm` 必须有 `registry`；`git` 必须有 40 位小写 hex `commit`）；**该字段必须等于 `resolveDistributionSource` 的返回**（不是从 `submission_source` 抄的）。
8. `publishTimestamp` 为正整数（秒级，且不晚于 `now + 300`）。
9. 所有 LocalizedText 结构合法（对象、值非空、无 `null`）。
10. `icon` 若存在：相对路径、以 `icons/` 开头、键符合 `^[a-f0-9]{16}(\.(svg|png|jpg|webp))?$`。
11. 序列化后不含 `"null"` / `"undefined"` 出现在必填位（用严格的 JSON writer + 显式不允许 null 的 schema）。
12. 总字节数 ≤ `maxIndexBytes`（默认 32 MiB，远低于 DSH 的 128 MiB 上限，留余量）。
13. 每个条目的 `homepage` 是绝对 `https:`/`http:` URL 且包含 base path。
14. 同一索引内 `pluginKey` 唯一。
15. "扫的就是发的"：`plugin_version.sha512` 与对应最新成功报告的 `tarball_sha512` 相等（不等即构建失败，属供应链告警）。**这条断言一律保留，是本设计的审核与溯源根基，不得以任何"删字段"改动为由移除。** 对 npm 来源，被比对的版本是提交那一刻冻结的 `source_payload.resolvedVersion`（不是构建时的 registry latest）——扫描、代发、索引都读同一个冻结版本，才保证 `sha512` 三方一致（§4.2 第 0/2 条）；若某处改成重解析 latest，这条断言就会失败并暴露供应链绕过。
16. **分发来源可解析**（本次新增）：每个 `LISTED` 版本经 `resolveDistributionSource` 判定后必须得到 `Npm` 或 `Git` 的**确定结果**；`Unresolved`（镜像 404 且无 git 落点）即构建失败并告警——不许"跳过这一条把插件放进去"，也不许写一个 `type:"none"` 之类的占位（DSH 会整份索引拒绝）。**探测过程中任何超时 / 5xx / 网络错误同样视为本条失败，且不得降级到 git**（§8.1.1 红线）。
17. **上传方式不得泄漏到索引**：索引字节里**不得出现** `submission_source` 的任何取值（`zip` / `folder` 字面量）与"上传方式"相关字段。做法：生成期对序列化后的字节做一次 `contains("\"zip\"") || contains("\"folder\"")` 检查太脆弱，改为**结构断言**——`DshIndexVersion` 是白名单字段集，序列化用显式 writer（只写声明过的键），未知键根本进不去；再加一条单测：用 `submission_source='zip'` 且镜像有制品的夹具生成索引，断言 `versions[0].source.type == 'npm'` 且整个文档不含 `"zip"`。

17 条全部通过才 `switchPointer`。失败时：指针不动、`dirty` 保持、`last_error` 记录首条错误、告警到管理员（`PRD §13` R2）。

### 8.5 CI 校验接入

| 位置 | 内容 |
|---|---|
| `make` 目标 | `make plugin-index-verify`：从本地存储/指定 S3 拉最近一次 `current.json` 指向的对象，落临时文件 |
| 校验器 | `npx --yes @deepseek-ai/dsh-plugin-market-index@<锁定的精确版本> validate <file>`（CI 内允许联网装依赖；**运行时不 `npx`**） |
| 触发 | PR 流水线（仅当改动 `domain/plugin`、`infra/plugin/index`、迁移或索引相关代码时）；夜间定时任务对线上产物再跑一次 |
| 失败处理 | 视为发布阻塞（`PRD §8.6` 的"双保险"）。CI 只校验"生成器能否产出合规文件"——用测试夹具构造一份含 1 个 npm 插件、1 个 git 插件、0 个图标的最小索引 |
| 版本锁定 | 校验库版本与运行时无耦合，但必须锁精确版本并在升级 PR 中说明 |

### 8.6 `metadata.categories` 与 i18n 的关系（v2.21：该键改承载标签）

- 索引里的 `categories[].title` 是**协议字段**，`en` 必需；来源是 `label_translation`（与技能市场同一张表），构建期查库读取，**不写死在 Java 常量、不在插件侧维护第二份词表**。
- 站内筛选 chip 的文案同样取 `label_translation`（按请求 locale 解析，缺失回落 `en`）；索引里的 title 取四语言，`ko` / `ru` 缺翻译时回落 `en`——标签是运营数据，缺翻译不该让索引构建失败（与插件数据校验的严格度刻意不同）。
- 标签清单**随词表变化**：运营新增或删除标签后，下一轮重建即生效；`visible_in_filter = FALSE` 的标签不进索引、不进筛选栏，但仍可挂在插件上（用于推荐类展示）。
- 插件无标签时 `plugins[].metadata.category` 整体省略，索引仍合法（该字段不在 `required` 内，见 §7.3）。**不要**用空串或"其他"兜底——那是给协议塞假数据。
- 索引协议字段名沿用 `categories` / `category` 不动，是为了让 DSH 客户端零改动；多标签只在站内生效（`PRD §8.3` v2.21）。

---

