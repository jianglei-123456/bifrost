/**
 * book-sync store 单测（M3-sync）。
 *
 * 重点盯三件事：分页参数是 0-based、过滤参数透传、以及"变更后重载了哪几块"
 * （孤儿变化会同时影响进度列表与概览——漏一处界面就会自相矛盾）。
 */
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as api from '@/api/bookSync'
import { useBookSyncStore } from '@/stores/bookSync'
import type { BookSyncStats, OrphanProgress, ReadingProgress, SyncAccount, SyncDevice } from '@/api/types'

vi.mock('@/api/bookSync', () => ({
  fetchSyncAccount: vi.fn(),
  updateSyncAccount: vi.fn(),
  generateSyncPassword: vi.fn(),
  fetchProgress: vi.fn(),
  deleteProgress: vi.fn(),
  fetchOrphans: vi.fn(),
  bindOrphan: vi.fn(),
  rematchOrphan: vi.fn(),
  ignoreOrphan: vi.fn(),
  fetchSyncDevices: vi.fn(),
  fetchBookSyncStats: vi.fn(),
}))

const mocked = vi.mocked(api)

function accountFixture(overrides: Partial<SyncAccount> = {}): SyncAccount {
  return {
    username: 'reader',
    password: 'synctest',
    registrationEnabled: true,
    autoScanOnUnmatched: true,
    serverUrl: 'http://192.168.1.10:18080',
    serverUrlHintConfigured: false,
    ...overrides,
  }
}

function progressFixture(overrides: Partial<ReadingProgress> = {}): ReadingProgress {
  return {
    id: 1,
    bookId: 7,
    bookTitle: 'Pride and Prejudice',
    bookAuthors: 'Jane Austen',
    libraryRootId: 2,
    coverUrl: null,
    documentFingerprint: 'a'.repeat(32),
    percentage: 0.42,
    progress: '42',
    device: 'Kobo_nova',
    deviceId: 'dev-1',
    reportedAt: '2026-09-16T12:00:00Z',
    matchSource: 'AUTO',
    ignored: false,
    ...overrides,
  }
}

function orphanFixture(overrides: Partial<OrphanProgress> = {}): OrphanProgress {
  return {
    id: 9,
    documentFingerprint: 'b'.repeat(32),
    percentage: 0.05,
    progress: '12',
    device: 'Kindle',
    deviceId: 'dev-9',
    createdAt: '2026-09-16T12:00:00Z',
    scanAttemptedAt: '2026-09-16T12:00:30Z',
    ignored: false,
    suggestedBookId: null,
    suggestedBookTitle: null,
    suggestionReason: null,
    ...overrides,
  }
}

function statsFixture(): BookSyncStats {
  return {
    progressCount: 2,
    matchedCount: 1,
    orphanCount: 1,
    deviceCount: 2,
    lastReportedAt: '2026-09-16T12:00:00Z',
  }
}

function deviceFixture(): SyncDevice {
  return {
    id: 3,
    deviceId: 'dev-1',
    deviceName: 'Kobo_nova',
    firstSeenAt: '2026-09-10T10:00:00Z',
    lastSeenAt: '2026-09-16T12:00:00Z',
    reportCount: 128,
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  mocked.fetchSyncAccount.mockResolvedValue(accountFixture())
  mocked.updateSyncAccount.mockResolvedValue(accountFixture({ username: 'reader2' }))
  mocked.generateSyncPassword.mockResolvedValue(accountFixture({ password: 'generated' }))
  mocked.fetchProgress.mockResolvedValue({ total: 1, items: [progressFixture()] })
  mocked.fetchOrphans.mockResolvedValue({ total: 1, items: [orphanFixture()] })
  mocked.fetchSyncDevices.mockResolvedValue({ total: 1, items: [deviceFixture()] })
  mocked.fetchBookSyncStats.mockResolvedValue(statsFixture())
  mocked.deleteProgress.mockResolvedValue(undefined)
  mocked.bindOrphan.mockResolvedValue(progressFixture())
  mocked.ignoreOrphan.mockResolvedValue(undefined)
  mocked.rematchOrphan.mockResolvedValue({ matched: false, progress: null })
})

describe('useBookSyncStore', () => {
  it('loadProgress 透传 0-based 分页与过滤参数', async () => {
    const store = useBookSyncStore()
    store.page = 2
    store.size = 50
    store.filter.title = 'Pride'
    store.filter.device = 'Kobo'
    store.filter.libraryRootId = 4

    await store.loadProgress()

    expect(mocked.fetchProgress).toHaveBeenCalledWith({
      page: 2, // 0-based，与 /api/books 一致
      size: 50,
      title: 'Pride',
      device: 'Kobo',
      libraryRootId: 4,
      onlyOrphans: undefined, // false 时不传，保持 URL 干净
    })
    expect(store.progress).toHaveLength(1)
    expect(store.progressTotal).toBe(1)
  })

  it('onlyOrphans=true 时透传该参数', async () => {
    const store = useBookSyncStore()
    store.filter.onlyOrphans = true

    await store.loadProgress()

    expect(mocked.fetchProgress).toHaveBeenCalledWith(
      expect.objectContaining({ onlyOrphans: true }),
    )
  })

  it('loadOrphans 透传 includeIgnored，loadAll 一次拉齐五块', async () => {
    const store = useBookSyncStore()
    store.includeIgnored = true

    await store.loadAll()

    expect(mocked.fetchOrphans).toHaveBeenCalledWith({ page: 0, size: 20, includeIgnored: true })
    expect(mocked.fetchSyncAccount).toHaveBeenCalledTimes(1)
    expect(mocked.fetchBookSyncStats).toHaveBeenCalledTimes(1)
    expect(mocked.fetchProgress).toHaveBeenCalledTimes(1)
    expect(mocked.fetchSyncDevices).toHaveBeenCalledTimes(1)
    expect(store.account?.username).toBe('reader')
    expect(store.devices).toHaveLength(1)
    expect(store.devicesTotal).toBe(1)
    expect(store.stats?.orphanCount).toBe(1)
    expect(store.loading).toBe(false)
  })

  it('removeProgress 删除后重载进度与概览', async () => {
    const store = useBookSyncStore()

    await store.removeProgress(1)

    expect(mocked.deleteProgress).toHaveBeenCalledWith(1)
    expect(mocked.fetchProgress).toHaveBeenCalledTimes(1)
    expect(mocked.fetchBookSyncStats).toHaveBeenCalledTimes(1)
  })

  it('rematch 未命中返回 false（正常结果）并刷新孤儿/进度/概览', async () => {
    const store = useBookSyncStore()

    const matched = await store.rematch(9)

    expect(matched).toBe(false)
    expect(mocked.rematchOrphan).toHaveBeenCalledWith(9)
    expect(mocked.fetchOrphans).toHaveBeenCalledTimes(1)
    expect(mocked.fetchProgress).toHaveBeenCalledTimes(1)
    expect(mocked.fetchBookSyncStats).toHaveBeenCalledTimes(1)
  })

  it('bind 绑定后刷新三块（绑定后不再是孤儿）', async () => {
    const store = useBookSyncStore()

    await store.bind(9, 7)

    expect(mocked.bindOrphan).toHaveBeenCalledWith(9, 7)
    expect(mocked.fetchOrphans).toHaveBeenCalledTimes(1)
    expect(mocked.fetchProgress).toHaveBeenCalledTimes(1)
    expect(mocked.fetchBookSyncStats).toHaveBeenCalledTimes(1)
  })

  it('setIgnored 默认忽略并刷新', async () => {
    const store = useBookSyncStore()

    await store.setIgnored(9)

    expect(mocked.ignoreOrphan).toHaveBeenCalledWith(9, true)
    expect(mocked.fetchOrphans).toHaveBeenCalledTimes(1)
  })

  it('saveAccount / generatePassword 更新 account', async () => {
    const store = useBookSyncStore()

    await store.saveAccount({ username: 'reader2' })
    expect(store.account?.username).toBe('reader2')

    await store.generatePassword()
    expect(mocked.generateSyncPassword).toHaveBeenCalledTimes(1)
    expect(store.account?.password).toBe('generated')
  })
})
