import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/musicRoots', () => ({
  fetchMusicScanStatus: vi.fn(),
  scanAllMusicRoots: vi.fn(),
  scanMusicRoot: vi.fn(),
}))

import { fetchMusicScanStatus, scanAllMusicRoots, scanMusicRoot } from '@/api/musicRoots'
import type { MusicRoot } from '@/api/types'
import { useMusicScanStore } from '@/stores/musicScan'

const mockedStatus = vi.mocked(fetchMusicScanStatus)
const mockedScanAll = vi.mocked(scanAllMusicRoots)
const mockedScanRoot = vi.mocked(scanMusicRoot)

function root(overrides: Partial<MusicRoot> = {}): MusicRoot {
  return {
    id: 1,
    name: '测试音乐',
    path: 'E:/Music',
    enabled: true,
    mediaType: 'MUSIC',
    lastScanAt: null,
    scanStatus: 'IDLE',
    lastScanStats: null,
    ...overrides,
  }
}

describe('music scan store', () => {
  beforeEach(() => {
    mockedStatus.mockReset()
    mockedScanAll.mockReset()
    mockedScanRoot.mockReset()
  })

  it('refresh 载入状态与音乐目录', async () => {
    mockedStatus.mockResolvedValue({
      scanning: false,
      roots: [root({ id: 1 }), root({ id: 2, name: '音乐目录B' })],
    })
    const store = useMusicScanStore()

    await store.refresh()

    expect(store.scanning).toBe(false)
    expect(store.roots).toHaveLength(2)
  })

  it('refresh 把后端 scanning=true 反映到 store', async () => {
    mockedStatus.mockResolvedValue({ scanning: true, roots: [root()] })
    const store = useMusicScanStore()

    await store.refresh()

    expect(store.scanning).toBe(true)
  })

  it('lastScanAt 取最近的扫描时间', async () => {
    mockedStatus.mockResolvedValue({
      scanning: false,
      roots: [
        root({ id: 1, lastScanAt: '2026-08-13T00:00:00Z' }),
        root({ id: 2, lastScanAt: '2026-08-14T00:00:00Z' }),
        root({ id: 3, lastScanAt: null }),
      ],
    })
    const store = useMusicScanStore()
    await store.refresh()

    expect(store.lastScanAt).toBe('2026-08-14T00:00:00Z')
  })

  it('lastStats 取最近一次扫描统计', async () => {
    mockedStatus.mockResolvedValue({
      scanning: false,
      roots: [
        root({
          lastScanAt: '2026-08-13T00:00:00Z',
          lastScanStats: '{"added":1,"updated":0,"missing":0,"error":0}',
        }),
        root({
          id: 2,
          lastScanAt: '2026-08-14T00:00:00Z',
          lastScanStats: '{"added":5,"updated":2,"missing":1,"error":1}',
        }),
      ],
    })
    const store = useMusicScanStore()
    await store.refresh()

    expect(store.lastStats).toEqual({ added: 5, updated: 2, missing: 1, error: 1 })
  })

  it('无扫描记录 → lastStats 为 null', async () => {
    mockedStatus.mockResolvedValue({ scanning: false, roots: [root()] })
    const store = useMusicScanStore()
    await store.refresh()

    expect(store.lastStats).toBeNull()
  })

  it('startFull 触发全量扫描并刷新', async () => {
    mockedScanAll.mockResolvedValue({ added: 3, updated: 0, missing: 0, error: 0 })
    mockedStatus.mockResolvedValue({ scanning: false, roots: [] })
    const store = useMusicScanStore()

    const stats = await store.startFull()

    expect(stats.added).toBe(3)
    expect(mockedStatus).toHaveBeenCalledTimes(1)
  })

  it('startRoot 触发单目录扫描并刷新', async () => {
    mockedScanRoot.mockResolvedValue({ added: 1, updated: 0, missing: 0, error: 0 })
    mockedStatus.mockResolvedValue({ scanning: false, roots: [] })
    const store = useMusicScanStore()

    await store.startRoot(7)

    expect(mockedScanRoot).toHaveBeenCalledWith(7)
    expect(mockedStatus).toHaveBeenCalledTimes(1)
  })
})
