# §10.1　路由与守卫

> 所属：前端　｜　覆盖章节：§10.1
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

## 10. 前端设计

### 10.1 路由与守卫

| 路径 | 文件 | 守卫 |
|---|---|---|
| `/plugins` | `pages/plugins.tsx` | 无 |
| `/plugins/$namespace/$slug` | `pages/plugin-detail.tsx` | 无（404 走路由错误页） |
| `/dashboard/plugins` | `pages/dashboard/my-plugins.tsx` | `requireAuth` |
| `/dashboard/plugins/submit` | `pages/dashboard/plugin-submit.tsx` | `requireAuth` + 空间成员（后端 403 兜底） |
| `/dashboard/namespaces/$slug/plugins` | `pages/dashboard/namespace-plugins.tsx` | `requireAuth` + 既有空间守卫 |

导航高亮沿用严格匹配：`/plugins` 不与 `/dashboard/plugins` 互相点亮（前缀不同，天然隔离）；`/dashboard/namespaces/$slug/plugins` 属控制台分组。

新增路由必须在 `router.tsx` 内懒加载（`createLazyRouteComponent`），与既有页面一致。

**提交页返回入口固定目标（PRD §6.1 v2.20 ③）**：`/dashboard/plugins/submit` 页头的 ghost + ArrowLeft 返回按钮文案固定「返回我的插件」，**固定返回 `/dashboard/plugins`**，不读 `returnTo`、不依赖浏览器后退、不按来源改目标（v2.19 的"无来源回退 `/plugins`"口径作废）。该按钮是产品意图，**不代表真实发布页代码已新增**（header 的 "intentionally omitted" 决定见 §14.2 开放问题 Q13）。

**`/dashboard/publish` 是回归红线（handover 缺口 #7）**：插件提交是**新页面**（`/dashboard/plugins/submit`），`/dashboard/publish` 保持"发布技能"语义不变，本变更只在它的入口区加一个指向插件提交的次按钮。硬约束：

1. 不改 `dashboardPublishRoute.validateSearch` 的 6 个查询参数：`namespace`、`visibility`、`updateName`、`updateSkill`、`resubmitSkill`、`resubmitVersion`（`web/src/app/router.tsx:390` 起的 `validateSearch`）。这些深链来自"更新技能/重新提交"按钮，任何改名或收窄都会让既有入口静默失效。
2. 不改该路由的 `beforeLoad: requireAuth`、不改 `pages/dashboard/publish.tsx` 的提交链路；新增入口按钮**只做导航**，不读写上述参数（避免与"更新技能"预填逻辑互相污染）。
3. 回归项写进 §16 末尾的「改造前行为回归保护」表（第 1 条），逐一构造这 6 个参数的深链，断言页面预填与自动化行为与改造前一致。

**提交入口可用性必须由能力开关驱动（`PRD §6.1` 通用规则）**：入口是否可用，由"该空间当前是否开放提交能力"决定；不可用时**置灰并说明原因，不隐藏**——入口消失会让用户以为功能没了，反而产生工单。落成三条前端约束：

1. **数据来源唯一**：`usePluginSubmitCapability()`（TanStack Query，`queryKey: ['plugin','submit-capability']`，`staleTime: 60_000`）读 `GET /api/v1/plugins/submit-capabilities`（§7.2）。组件里**不得**出现 `if (space.kind === 'TEAM' && phase === 'P0')` 这类分期判定——P1 开放团队空间时服务端开关一翻，前端不应有 diff。
2. **三处入口同一实现**：`/plugins` 页头、`/dashboard/plugins` 页头、命名空间插件 tab 页头共用一个 `PluginSubmitButton`，`disabled = !available`，旁边固定渲染 `PluginSubmitDisabledHint`（`reasonCode → i18n`）。三处必须同态，不允许一处隐藏。
3. **提交页也要挡**：`/dashboard/plugins/submit` 的空间选择器把不可用空间列出但 `disabled` + 原因文案（不静默从列表里删掉——用户会以为"我的空间丢了"）；深链带 `?namespace=team-a` 进来时停在空间步骤并展示原因，不跳错误页。

原因是**场景变量**，P0 只是当前实例：P0 团队空间的文案是「**审核将在治理阶段开放**」（`PRD §12` v2.11 原句，照抄不改写），P1 打开开关后该文案自动不再出现；其它场景（全局止血 `publishDisabled`）用各自的原因键，见 §10.5。

