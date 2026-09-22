# 阅读进度同步（KOSync）管理端设计

> **实现状态（2026-09-10）：已按本文档落地。**
> 新增 `src/api/bookSync.ts`、`src/stores/bookSync.ts`、`src/views/BookSyncView.vue`、
> `src/components/{SyncAccountCard,ProgressTable,OrphanTable,DeviceTable}.vue`、`src/utils/clipboard.ts`；
> 路由 `/books/progress`，菜单位于「图书 → 阅读进度」。
> 门禁全绿：`pnpm type-check` / `pnpm test`（41 通过，含 8 条 book-sync store 单测）/ `pnpm lint` / `pnpm build`；
> 接口字段用真实后端 payload 逐个核对过（含"`@JsonInclude(NON_NULL)` 会把 null 字段整个省略"这一点）。
> 查看方式：后端跑起来后 `pnpm dev` → 打开 `http://localhost:5173/books/progress`（dev 走 Vite 代理 /api → 18080）。

> 后端契约见 [`doc/m3-sync/task/06-前端对接.md`](../../doc/m3-sync/task/06-前端对接.md)（端点清单与字段样例）与 [`doc/m3-sync/task/03-管理REST.md`](../../doc/m3-sync/task/03-管理REST.md)（字段级细节）；读源码不易看出的坑（含"图书扫描端点吞错误"）见 [`docs/api-contract-notes.md`](api-contract-notes.md)。
> 本文档只管**管理端**：路由、类型、API 模块、store、页面与组件、交互文案。
> 前置里程碑：M2-book 图书管理（已完成）。对应后端里程碑：**M3-sync**（[`doc/m3-sync/`](../../doc/m3-sync/)）。

---

## 1. 范围

**做**：同步账号的配置与回显（含明文口令、随机生成、改口令）、阅读进度列表（按书展示、可重置）、孤儿进度处理（绑定 / 重新匹配 / 忽略）、设备列表、页面统计。

**不做**（后端已明确不做，前端不要造按钮）：踢设备（协议层无会话，封锁单设备无效）、进度历史时间线、跨格式续读、书签/高亮同步、把进度写回设备。

## 2. 路由与菜单

| 项 | 位置 | 内容 |
|---|---|---|
| 路由 | `src/router/index.ts`，`AppLayout` 的 `children` 数组（现 28–122 行） | `{ path: 'books/progress', name: 'book-sync', component: () => import('@/views/BookSyncView.vue'), meta: { title: '阅读进度' } }` |
| 菜单 | `src/layout/SidebarNav.vue` 的「图书」分组（现 47–53 行） | 追加 `{ label: '阅读进度', to: '/books/progress', icon: <Element Plus 图标> }`，排在「图书库」之后 |

不新增顶层分组（进度同步是图书侧能力，与「图书」「图书库」同级）。

## 3. 文件清单

| 文件 | 动作 | 说明 |
|---|---|---|
| `src/api/types.ts` | 改 | 追加 `SyncAccount`、`ReadingProgress`、`OrphanProgress`、`SyncDevice`、`BookSyncStats` |
| `src/api/bookSync.ts` | 新建 | 11 个端点的调用函数（仿 `src/api/bookRoots.ts` 风格） |
| `src/stores/bookSync.ts` | 新建 | Pinia store（仿 `src/stores/book.ts`，但**不轮询**） |
| `src/views/BookSyncView.vue` | 新建 | 页面骨架：头部统计 + 账号卡片 + 两个 tab |
| `src/components/SyncAccountCard.vue` | 新建 | 账号展示/编辑/生成口令/复制接入信息 |
| `src/components/ProgressTable.vue` | 新建 | 进度列表（分页 + 过滤 + 重置） |
| `src/components/OrphanTable.vue` | 新建 | 孤儿列表（绑定/重新匹配/忽略） |
| `src/components/DeviceTable.vue` | 新建 | 设备列表（只读） |
| `src/utils/clipboard.ts` | 新建（若无既有） | `copyText()`，优先 `navigator.clipboard`，降级 `document.execCommand` |

## 4. 类型（`src/api/types.ts` 追加）

```ts
export interface SyncAccount {
  username: string
  password: string            // 明文（后端可逆存储；页面默认打码）
  registrationEnabled: boolean
  autoScanOnUnmatched: boolean
  serverUrl: string           // 已拼好的、可抄进 KOReader 的地址（带 http://）
  serverUrlHintConfigured: boolean
}

export interface ReadingProgress {
  id: number
  bookId: number | null
  bookTitle: string | null
  bookAuthors: string | null
  libraryRootId: number | null
  coverUrl: string | null
  documentFingerprint: string
  percentage: number          // 0–1
  progress: string
  device: string
  deviceId: string
  reportedAt: string          // ISO-8601
  matchSource: 'AUTO' | 'MANUAL' | null
  ignored: boolean
}

export interface OrphanProgress extends Omit<ReadingProgress, 'bookId' | 'bookTitle' | 'bookAuthors' | 'libraryRootId' | 'coverUrl' | 'matchSource'> {
  createdAt: string
  scanAttemptedAt: string | null      // null = 自动扫描尚未结算
  suggestedBookId: number | null
  suggestedBookTitle: string | null
  suggestionReason: 'FILENAME' | 'OPDS_NAME' | null
}

export interface SyncDevice {
  id: number
  deviceId: string
  deviceName: string
  firstSeenAt: string
  lastSeenAt: string
  reportCount: number
}

export interface BookSyncStats {
  progressCount: number
  matchedCount: number
  orphanCount: number
  deviceCount: number
  lastReportedAt: string | null
}
```

**时间字段都是 ISO-8601 字符串**，直接复用 `utils/format.ts` 的 `formatDateTime`（不要当毫秒时间戳用）。

## 5. API 模块（`src/api/bookSync.ts`）

```ts
fetchSyncAccount(): Promise<SyncAccount>                                  // GET  /api/book-sync/account
updateSyncAccount(patch: { username?: string; password?: string }): Promise<SyncAccount>  // PUT
generateSyncPassword(): Promise<SyncAccount>                              // POST /api/book-sync/account/password
fetchProgress(query): Promise<PageResult<ReadingProgress>>                // GET  /api/book-sync/progress
deleteProgress(id: number): Promise<void>                                 // DELETE
fetchOrphans(query): Promise<PageResult<OrphanProgress>>                  // GET  /api/book-sync/orphans
bindOrphan(id: number, bookId: number): Promise<ReadingProgress>          // POST .../bind
rematchOrphan(id: number): Promise<{ matched: boolean; progress: ReadingProgress | null }>
ignoreOrphan(id: number, ignored = true): Promise<void>                   // POST .../ignore
fetchSyncDevices(): Promise<PageResult<SyncDevice>>                         // GET  /api/book-sync/devices（信封 {total, items}，非裸数组）
fetchBookSyncStats(): Promise<BookSyncStats>                              // GET  /api/book-sync/stats
```

- 全部走 `request<T>()`（自动解信封、非 0 code 抛 `ApiError`）。
- 分页参数 `page` **0-based**、`size` 默认 20（与 `fetchBooks` 一致）。

## 6. Store（`src/stores/bookSync.ts`）

```ts
state: {
  account: SyncAccount | null
  stats: BookSyncStats | null
  progress: ReadingProgress[]
  progressTotal: number
  progressFilter: { title: string; device: string; libraryRootId: number | null; onlyOrphans: boolean }
  page: number; size: number
  orphans: OrphanProgress[]
  orphansTotal: number
  includeIgnored: boolean
  devices: SyncDevice[]
  loading: boolean
}
actions: {
  loadAccount(); loadStats(); loadProgress(); loadOrphans(); loadDevices()
  saveAccount(patch); generatePassword()
  removeProgress(id)          // 成功后 reload progress + stats
  bind(id, bookId); rematch(id); setIgnored(id, ignored)   // 成功后 reload orphans + progress + stats
}
```

- **不做轮询**：进度由设备推送驱动（客户端 10s/25s 去抖、关书才推送），管理端刷新频率再高也只看到同一份数据。进入页面 `onMounted` 全量加载 + 顶部手动刷新按钮即可。
- 与 `stores/book.ts` 的区别：不需要扫描状态机、不需要 `AppLayout` 里的定时器（**不要在 `AppLayout.vue` 的 `onMounted` 注册定时器**）。

## 7. 页面结构（`BookSyncView.vue`）

```
<div class="page">
  <div class="page-header"> 标题「阅读进度」 + 说明 + [刷新] 按钮 </div>
  <StatCard 行>  已同步 N 本 · 孤儿 N 条 · 设备 N 台 · 最后上报 <时间>   </StatCard>
  <SyncAccountCard />                     // 区块 1：同步账号（含接入指引）
  <el-tabs>
    <el-tab-pane label="进度列表">  <ProgressTable />  </el-tab-pane>
    <el-tab-pane label="孤儿进度 (N)"> <OrphanTable /> </el-tab-pane>
    <el-tab-pane label="设备 (N)">   <DeviceTable />  </el-tab-pane>
  </el-tabs>
</div>
```

外层结构与 `BookRootsView.vue` 的 `page / page-header / card` 保持一致（`docs/design.md` 的设计系统；颜色一律用 `styles/tokens.css` 变量，Element Plus 组件沿用现有暗色主题配置）。

## 8. 交互与文案（关键：这些坑必须写在界面上）

**同步账号卡片**

- 展示：`serverUrl`（**等宽字体 + 复制按钮**）、用户名、口令（默认 `••••••`，眼睛图标切换明文）、`registrationEnabled` / `autoScanOnUnmatched` 两个状态标签。
- 动作：改用户名、改口令、**生成随机口令**（生成后立即展示明文并提示"请抄进设备"）。
- 必读提示（用 `el-alert` 常驻，不要藏在 tooltip 里）：
  1. **设备里填地址要手动把默认的 `https://` 改成 `http://`**（KOReader 不自动补 scheme）；
  2. 设备上点 **Register 或 Login 都可以**（两者等价）；
  3. **改口令后，已配置的设备会认证失败，需要在设备上重新登录**；
  4. 设备里 `Document matching method` 必须保持 **Binary**（改成 Filename 会让所有进度变成孤儿）；
  5. 同步口令**不能与管理员口令相同**（后端会拒绝）——页面在提交前先自行校验并给出提示。
- "改口令"用 `el-dialog` 输入 + 二次确认；成功后触发一次 `loadAccount()`。

**进度列表**

- 列：封面（`BookCover` 组件，用 `coverUrl`）、书名/作者、进度（`el-progress` + 百分比文案）、设备名、最后上报时间、操作。
- 过滤：书名（`el-input` 防抖）、设备、图书目录（复用 `book-roots` 的显示名）、"仅看孤儿"开关。
- 操作「重置进度」：`el-popconfirm` 确认，文案必须写明：
  > 只删除服务器上的这条记录。设备本地位置不会改变，设备下次推送会重新建立记录。
- 空状态：`el-empty` 文案"还没有任何设备上报过进度"，并给出跳转到账号卡片的引导。

**孤儿表**

- 列：文档指纹（前 8 位 + 复制全文）、进度 %、设备、首次出现、**结算状态**（`scanAttemptedAt == null` → `el-tag` 警告"等待自动扫描结算"；否则"未匹配到图书"）、建议绑定（书名 + 原因标签 `FILENAME`/`OPDS_NAME`）、操作。
- 操作：
  - **绑定**：`el-dialog` 内用 `el-select` 远程搜索（`fetchBooks({ title })`，`filterable remote`）选书 → 调 `bind`；
  - **重新匹配**：调 `rematch`，返回 `matched=false` 时用 `ElMessage.info('仍未匹配到图书')`（**不是错误**）；
  - **忽略 / 取消忽略**：切换 `ignored`，列表默认隐藏已忽略项（页签标题显示总数）。
- 空状态："没有孤儿进度（所有上报的进度都对应到了库里的书）"。

**设备表**

- 列：设备名、`device_id`（截断 + 复制）、首次见到、最后见到、上报次数。
- **没有"踢设备"按钮**；页脚一句说明：
  > 协议不支持单独封锁某台设备。要让所有设备重新登录，请在账号卡片里改口令。

## 9. 错误处理

- 统一 `ApiError` → `ElMessage.error(e.message)`；401 由 `api/client.ts` 的响应拦截器处理（清登录态 + 跳登录页），页面不额外处理。
- 账号提交类的**校验失败**（1000）在表单内联提示，不用全局 toast。
- `rematch` 的 `matched=false` 是**正常返回**，不要显示为错误。

## 10. 测试

- `src/stores/bookSync.spec.ts`（vitest，仿 `stores/musicScan.spec.ts`）：mock API，断言加载顺序、删除/绑定/忽略后的重载链路、过滤参数透传（含 `page` 0-based）。
- API 层契约快照：对 11 个函数的 URL/method/params 做断言，防后端字段改名静默失效。
- 组件测试可选（`@vue/test-utils` 已就绪）；**没有真实后端时不要写依赖真实网络的测试**。

## 11. 交付顺序建议

1. `types.ts` + `api/bookSync.ts`（可先用后端 hurl 跑通契约）；
2. `stores/bookSync.ts` + 单测；
3. `BookSyncView.vue` + `SyncAccountCard`（**先交付账号卡片**——它决定用户能不能把设备接上，是整条链路的前提）；
4. `ProgressTable` → `OrphanTable` → `DeviceTable`；
5. 路由与菜单接入，最后补文案与空状态。
