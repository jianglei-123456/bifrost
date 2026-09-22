/**
 * 图书域 store（M2-book）：图书目录 + 图书列表 + 过滤/分页 + 全局扫描状态。
 *
 * 扫描是异步的：触发后由 AppLayout 的自适应轮询收敛到 IDLE——
 * SCANNING 时 1s、空闲 15s 静默兜底（捕捉其他会话触发的扫描）。
 * 本 store 只存"后端轮询回来的状态"（scanStatus 由 GET /scan/status 赋值），
 * 不在触发动作里乐观置 SCANNING，避免把"尚未落库的启动"误判为"已完成"。
 */
import { defineStore } from 'pinia'

import {
  createBookRoot,
  deleteBookRoot,
  fetchBookRoots,
  fetchBookScanStatus,
  scanAllBookRoots,
  scanBookRoot,
  updateBookRoot,
} from '@/api/bookRoots'
import type { BookRootRequest } from '@/api/bookRoots'
import {
  deleteBook as apiDeleteBook,
  fetchBooks,
  updateBook as apiUpdateBook,
} from '@/api/books'
import type {
  Book,
  BookRoot,
  BookScanStatusView,
  BookScanTriggerView,
} from '@/api/types'
import type { BookPatch } from '@/api/books'

export interface BookListFilter {
  title: string
  author: string
  series: string
  libraryRootId: number | null
  isAvailable: boolean | null
}

function emptyScanStatus(): BookScanStatusView {
  return {
    scanStatus: 'IDLE',
    currentRootId: null,
    startedAt: null,
    lastScanAt: null,
    lastStats: null,
  }
}

export const useBookStore = defineStore('book', {
  state: () => ({
    roots: [] as BookRoot[],
    books: [] as Book[],
    total: 0,
    filter: {
      title: '',
      author: '',
      series: '',
      libraryRootId: null,
      isAvailable: null,
    } as BookListFilter,
    page: 0,
    size: 20,
    scanStatus: emptyScanStatus(),
    /** 本会话是否刚触发过扫描且尚未收敛到 IDLE（驱动 1s 轮询） */
    pendingScan: false,
    /** 触发时刻；用于把"启动竞态"与"快速完成"区分开 */
    pendingSince: 0,
    /** 每完成一次扫描自增；视图 watch 后刷新列表 */
    completionTick: 0,
  }),
  getters: {
    scanning(): boolean {
      return this.scanStatus.scanStatus === 'SCANNING'
    },
    /** UI 上"扫描进行中" = 后端 SCANNING 或本会话触发后待收敛 */
    busy(): boolean {
      return this.scanning || this.pendingScan
    },
    currentScanRoot(): BookRoot | null {
      const id = this.scanStatus.currentRootId
      return id == null ? null : (this.roots.find((r) => r.id === id) ?? null)
    },
  },
  actions: {
    async loadRoots() {
      this.roots = await fetchBookRoots()
    },
    /** 按当前 filter/page/size 拉图书列表 */
    async loadBooks() {
      const res = await fetchBooks({
        page: this.page,
        size: this.size,
        title: this.filter.title || undefined,
        author: this.filter.author || undefined,
        series: this.filter.series || undefined,
        libraryRootId: this.filter.libraryRootId ?? undefined,
        isAvailable: this.filter.isAvailable ?? undefined,
      })
      this.books = res.items
      this.total = res.total
    },
    async createRoot(body: BookRootRequest) {
      await createBookRoot(body)
      await this.loadRoots()
    },
    async updateRoot(id: number, body: Partial<BookRootRequest>) {
      await updateBookRoot(id, body)
      await this.loadRoots()
    },
    async deleteRoot(id: number) {
      await deleteBookRoot(id)
      await this.loadRoots()
    },
    /** 触发单根扫描；置 pendingScan，AppLayout watch 后立即以 1s 收敛 */
    async scanRoot(id: number): Promise<BookScanTriggerView> {
      const view = await scanBookRoot(id)
      this.pendingScan = true
      this.pendingSince = Date.now()
      return view
    },
    /** 触发全部 BOOK 根扫描（rootId 为 null） */
    async scanAll(): Promise<BookScanTriggerView> {
      const view = await scanAllBookRoots()
      this.pendingScan = true
      this.pendingSince = Date.now()
      return view
    },
    async updateBook(id: number, patch: BookPatch): Promise<Book> {
      return apiUpdateBook(id, patch)
    },
    async deleteBook(id: number) {
      await apiDeleteBook(id)
    },
    /**
     * 轮询一次全局扫描状态（AppLayout 定时调用）。
     * 检测 SCANNING→IDLE：刷新图书目录行并 bump completionTick（视图据此重拉列表）。
     */
    async refreshScanStatus() {
      const prev = this.scanStatus.scanStatus
      const view = await fetchBookScanStatus()
      this.scanStatus = view

      if (view.scanStatus === 'SCANNING') {
        this.pendingScan = false
        this.pendingSince = 0
      }
      const started = prev !== 'SCANNING' && view.scanStatus === 'SCANNING'
      // 完成：观察到 SCANNING→IDLE；或本会话触发的扫描（启动竞态 1.5s 后仍 IDLE 视为已快速完成）
      const finished =
        (prev === 'SCANNING' && view.scanStatus === 'IDLE') ||
        (this.pendingScan &&
          view.scanStatus === 'IDLE' &&
          Date.now() - this.pendingSince > 1500)

      if (finished) {
        this.pendingScan = false
        this.pendingSince = 0
        this.completionTick += 1
        await this.loadRoots().catch(() => {})
      } else if (started) {
        // 扫描开始：刷新图书目录行（哪一行 SCANNING）
        await this.loadRoots().catch(() => {})
      }
    },
  },
})
