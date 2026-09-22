/**
 * 音乐扫描状态：顶栏呼吸灯 + 总览页 + 系统页 + 音乐库页共用。
 * 管理 REST 的音乐扫描端点是同步的（POST /api/music-roots/{id}/scan 返回最终统计），
 * 状态轮询用于捕捉外部触发的扫描（Subsonic startScan）。
 */
import { defineStore } from 'pinia'

import { fetchMusicScanStatus, scanAllMusicRoots, scanMusicRoot } from '@/api/musicRoots'
import type { MusicRoot, ScanStats } from '@/api/types'
import { parseScanStats } from '@/utils/format'

export const useMusicScanStore = defineStore('music-scan', {
  state: () => ({
    scanning: false,
    roots: [] as MusicRoot[],
  }),
  getters: {
    /** 全局上次扫描统计（聚合各音乐目录，取最新一次） */
    lastStats(): ScanStats | null {
      let latest: ScanStats | null = null
      let latestAt = 0
      for (const root of this.roots) {
        const stats = parseScanStats(root.lastScanStats)
        if (stats) {
          const at = root.lastScanAt ? new Date(root.lastScanAt).getTime() : 0
          if (at >= latestAt) {
            latestAt = at
            latest = stats
          }
        }
      }
      return latest
    },
    lastScanAt(): string | null {
      let latest: string | null = null
      for (const root of this.roots) {
        if (root.lastScanAt && (!latest || root.lastScanAt > latest)) latest = root.lastScanAt
      }
      return latest
    },
  },
  actions: {
    async refresh() {
      const view = await fetchMusicScanStatus()
      this.scanning = view.scanning
      this.roots = view.roots
    },
    /** 全量扫描（同步，完成后刷新状态） */
    async startFull(): Promise<ScanStats> {
      const stats = await scanAllMusicRoots()
      await this.refresh()
      return stats
    },
    /** 单目录扫描 */
    async startRoot(id: number): Promise<ScanStats> {
      const stats = await scanMusicRoot(id)
      await this.refresh()
      return stats
    },
  },
})
