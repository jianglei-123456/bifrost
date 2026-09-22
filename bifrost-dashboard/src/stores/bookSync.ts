/**
 * 阅读进度同步 store（M3-sync）：同步账号 + 进度列表 + 孤儿进度 + 设备 + 概览。
 *
 * 刻意**不做轮询**：进度由设备推送驱动（客户端 10s 翻页防抖 / 25s 全局去抖、关书才推送），
 * 管理端刷得再勤也只会看到同一份数据；因此进入页面加载一次 + 变更后局部刷新即可。
 * 也因此**不在 AppLayout 注册定时器**（与 stores/musicScan、stores/book 不同）。
 */
import { defineStore } from 'pinia'

import {
  bindOrphan,
  deleteProgress,
  fetchBookSyncStats,
  fetchOrphans,
  fetchProgress,
  fetchSyncAccount,
  fetchSyncDevices,
  generateSyncPassword,
  ignoreOrphan,
  rematchOrphan,
  updateSyncAccount,
} from '@/api/bookSync'
import type { ProgressQuery } from '@/api/bookSync'
import type { BookSyncStats, OrphanProgress, ReadingProgress, SyncAccount, SyncDevice } from '@/api/types'

export interface ProgressFilter {
  title: string
  device: string
  libraryRootId: number | null
  onlyOrphans: boolean
}

export const useBookSyncStore = defineStore('book-sync', {
  state: () => ({
    account: null as SyncAccount | null,
    stats: null as BookSyncStats | null,

    progress: [] as ReadingProgress[],
    progressTotal: 0,
    filter: {
      title: '',
      device: '',
      libraryRootId: null,
      onlyOrphans: false,
    } as ProgressFilter,
    page: 0,
    size: 20,

    orphans: [] as OrphanProgress[],
    orphansTotal: 0,
    includeIgnored: false,
    orphanPage: 0,
    orphanSize: 20,

    devices: [] as SyncDevice[],
    devicesTotal: 0,

    loading: false,
  }),

  actions: {
    async loadAccount() {
      this.account = await fetchSyncAccount()
    },

    async saveAccount(patch: { username?: string; password?: string }) {
      this.account = await updateSyncAccount(patch)
    },

    async generatePassword() {
      this.account = await generateSyncPassword()
    },

    async loadStats() {
      this.stats = await fetchBookSyncStats()
    },

    /** 按当前 filter/page/size 拉进度（page 0-based，与 /api/books 一致） */
    async loadProgress() {
      const query: ProgressQuery = {
        page: this.page,
        size: this.size,
        title: this.filter.title || undefined,
        device: this.filter.device || undefined,
        libraryRootId: this.filter.libraryRootId ?? undefined,
        onlyOrphans: this.filter.onlyOrphans || undefined,
      }
      const res = await fetchProgress(query)
      this.progress = res.items
      this.progressTotal = res.total
    },

    async loadOrphans() {
      const res = await fetchOrphans({
        page: this.orphanPage,
        size: this.orphanSize,
        includeIgnored: this.includeIgnored,
      })
      this.orphans = res.items
      this.orphansTotal = res.total
    },

    async loadDevices() {
      const res = await fetchSyncDevices()
      this.devices = res.items
      this.devicesTotal = res.total
    },

    /** 进入页面：一次拉全部 */
    async loadAll() {
      this.loading = true
      try {
        await Promise.all([
          this.loadAccount(),
          this.loadStats(),
          this.loadProgress(),
          this.loadOrphans(),
          this.loadDevices(),
        ])
      } finally {
        this.loading = false
      }
    },

    /**
     * 重置（删除）单条进度。
     * 只删服务端记录——设备本地位置不变，设备下次推送会重新建立记录。
     */
    async removeProgress(id: number) {
      await deleteProgress(id)
      await Promise.all([this.loadProgress(), this.loadStats()])
    },

    async bind(id: number, bookId: number) {
      await bindOrphan(id, bookId)
      await this.refreshAfterOrphanChange()
    },

    /** 人工重新匹配；返回是否命中（false 是正常结果，界面提示而不是报错） */
    async rematch(id: number): Promise<boolean> {
      const result = await rematchOrphan(id)
      await this.refreshAfterOrphanChange()
      return result.matched
    },

    async setIgnored(id: number, ignored = true) {
      await ignoreOrphan(id, ignored)
      await this.refreshAfterOrphanChange()
    },

    /** 孤儿变化会影响进度列表与概览（绑定后它就不再是孤儿） */
    async refreshAfterOrphanChange() {
      await Promise.all([this.loadOrphans(), this.loadProgress(), this.loadStats()])
    },
  },
})
