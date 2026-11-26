# §10.3–§10.4　query key 与轮询、状态与乐观更新的边界

> 所属：前端　｜　覆盖章节：§10.3–§10.4
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

### 10.3 TanStack Query：query key 与轮询

```ts
// features/plugin/plugin-queries.ts
export const pluginKeys = {
  all: ['plugins'] as const,
  market: () => [...pluginKeys.all, 'market'] as const,
  list: (f: PluginListFilter) => [...pluginKeys.all, 'list', f] as const,
  detail: (ns: string, slug: string) => [...pluginKeys.all, 'detail', ns, slug] as const,
  versions: (ns: string, slug: string) => [...pluginKeys.all, 'versions', ns, slug] as const,
  scan: (versionId: number) => [...pluginKeys.all, 'scan', versionId] as const,
  submission: (id: number) => [...pluginKeys.all, 'submission', id] as const,
  inFlight: (ns?: string) => [...pluginKeys.all, 'inFlight', ns ?? null] as const,
  mine: (f: MyPluginFilter) => [...pluginKeys.all, 'mine', f] as const,
  namespace: (slug: string, f: PluginListFilter) => [...pluginKeys.all, 'namespace', slug, f] as const,
  reports: (f: PluginReportFilter) => [...pluginKeys.all, 'reports', f] as const,
  submitCapability: () => [...pluginKeys.all, 'submit-capability'] as const,
  stagedFiles: (submissionId: number) => [...pluginKeys.all, 'staged-files', submissionId] as const,
  stagedFileContent: (submissionId: number, path: string) =>
    [...pluginKeys.all, 'staged-file-content', submissionId, path] as const,
}
```

| 查询 | `staleTime` | `refetchInterval` | 说明 |
|---|---|---|---|
| `market` | 30s | `dirty ? 10s : false` | 索引状态只在脏时轮询 |
| `list` / `detail` / `versions` | 30s | false | 变更后 `invalidateQueries` |
| `scan(versionId)` | 5min | false | 扫描结果不可变 |
| `submission(id)` | 0 | `terminal ? false : pollAfterMs` | 服务端下发 `pollAfterMs`，前端不自造节奏；从 2000ms 起，服务端在重试退避时提高到 5000ms；硬上限 10 分钟后停止并提示"仍在处理，稍后回来看" |
| `mine` / `namespace` | 15s | false | 操作后 `invalidate` |
| `inFlight` | 0 | `hasNonTerminal ? 3000 : false` | 在途列表：有非终态时 3s 轮询；单条详情仍按 `pollAfterMs`；提交成功后 `invalidate(inFlight)` |
| `reports` | 30s | false | **只读**列表，无处置动作，因此不存在"处置后 invalidate"；进 tab 拉一次即可 |
| `submitCapability` | **60s** | false | 三处入口共用一份缓存（同一次挂载只打一次接口）；提交成功后 `invalidate` 一次（团队空间可能刚好被开通） |
| `stagedFiles(submissionId)` | **5min** | false | 暂存包内容在提交生命周期内不可变（sha512 为键），5min 足够一次审核会话；**进审核页才拉**（`enabled` 依赖 `subjectType === 'PLUGIN_VERSION'`） |
| `stagedFileContent(submissionId, path)` | **10min** | false | 按路径分别缓存；**懒加载**（`enabled: !!selectedPath`），树节点不预取（§10.2 第 3 条）；`retry: 0`——410/413 重试没有意义 |

轮询放大防护：
- `refetchIntervalInBackground: false`（标签页不可见不轮询）。
- 轮询只按 `submissionId` 单条进行，**不轮询列表**。
- 终态立即停止（服务端 `pollAfterMs = 0`）。
- 提交页在轮询期间禁止重复提交（按钮 loading + 服务端 `uq_plugin_submission_active` 双保险）。

### 10.4 状态与乐观更新的边界

| 操作 | 策略 | 理由 |
|---|---|---|
| 改分类 | **乐观更新**（先改本地 cache，失败回滚并 toast） | 幂等、单字段、无审计强约束以外的副作用 |
| 下架 / 恢复 | 不做乐观更新；`isPending` 期间禁用按钮，成功后 `invalidate(mine/namespace/detail)` + 提示"索引将在 1 分钟内更新" | 有审计 + 索引异步重建，乐观会撒谎 |
| 提交插件 | 不做乐观更新；拿到 `submissionId` 后跳进度视图 | 结果由状态机决定 |
| 复制提示词 | 本地成功态 2s + 上报 `copy_prompt`（失败静默） | 埋点失败不能阻断复制 |
| 重新扫描 / 放行 | 不做乐观更新 | 需审核语义与审计 |

全局规则（`AGENTS.md`）：服务端状态一律 TanStack Query，禁止 `useEffect` 拉取；错误 `console.error`，不打印凭据；复用 `Button`/`Card`/`Badge`/`cn()`。

