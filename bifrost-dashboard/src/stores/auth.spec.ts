import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/user', () => ({
  fetchUser: vi.fn(),
}))

import { fetchUser } from '@/api/user'
import { AUTH_STORAGE_KEY, LOGIN_TTL_MS, useAuthStore } from '@/stores/auth'
import { toBasicHeader } from '@/utils/auth'

const mockedFetchUser = vi.mocked(fetchUser)

/** 模拟页面刷新：新建独立的 Pinia 实例，store 从 localStorage 恢复 */
function freshStore() {
  setActivePinia(createPinia())
  return useAuthStore()
}

describe('auth store', () => {
  beforeEach(() => {
    mockedFetchUser.mockReset()
    localStorage.clear()
  })

  it('初始未认证', () => {
    const store = freshStore()
    expect(store.isAuthenticated).toBe(false)
    expect(store.credentials).toBeNull()
  })

  it('登录成功：凭据入内存、Basic 头正确', async () => {
    mockedFetchUser.mockResolvedValue({ username: 'admin', role: 'ADMIN' })
    const store = freshStore()

    await store.login('admin', 'secret123', true)

    expect(store.isAuthenticated).toBe(true)
    expect(store.username).toBe('admin')
    expect(store.basicHeader).toBe(toBasicHeader('admin', 'secret123'))
    // 请求拦截器读取的就是 credentials
    expect(store.credentials).toEqual({
      username: 'admin',
      password: 'secret123',
      salt: store.salt,
    })
  })

  it('登录成功：凭据持久化到 localStorage（有效期 1 天）', async () => {
    mockedFetchUser.mockResolvedValue({ username: 'admin', role: 'ADMIN' })
    const store = freshStore()

    await store.login('admin', 'secret123', true)

    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    expect(raw).not.toBeNull()
    const saved = JSON.parse(raw!) as {
      username: string
      password: string
      salt: string
      expiresAt: number
    }
    expect(saved.username).toBe('admin')
    expect(saved.password).toBe('secret123')
    expect(saved.salt).toBe(store.salt)
    expect(saved.expiresAt).toBeGreaterThanOrEqual(Date.now() + LOGIN_TTL_MS - 1000)
    expect(saved.expiresAt).toBeLessThanOrEqual(Date.now() + LOGIN_TTL_MS + 1000)
  })

  it('刷新页面（新 store）：1 天内恢复登录态', async () => {
    mockedFetchUser.mockResolvedValue({ username: 'admin', role: 'ADMIN' })
    const store = freshStore()
    await store.login('admin', 'secret123', true)

    const restored = freshStore()
    expect(restored.isAuthenticated).toBe(true)
    expect(restored.username).toBe('admin')
    expect(restored.password).toBe('secret123')
    expect(restored.salt).toBe(store.salt)
  })

  it('超过 1 天：登录态过期，新 store 未认证并清除本地快照', () => {
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        username: 'admin',
        password: 'secret123',
        salt: 'abc1234567',
        expiresAt: Date.now() - 1,
      }),
    )

    const store = freshStore()
    expect(store.isAuthenticated).toBe(false)
    expect(store.username).toBe('')
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it('快照格式损坏：忽略并清除', () => {
    localStorage.setItem(AUTH_STORAGE_KEY, 'not-json')

    const store = freshStore()
    expect(store.isAuthenticated).toBe(false)
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it('登录失败：清空凭据、本地快照并抛错', async () => {
    mockedFetchUser.mockRejectedValue(new Error('401'))
    const store = freshStore()

    await expect(store.login('admin', 'wrong')).rejects.toThrow()

    expect(store.isAuthenticated).toBe(false)
    expect(store.password).toBe('')
    expect(store.credentials).toBeNull()
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it('clear 后恢复未认证并清除本地快照', () => {
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({ username: 'admin', password: 'p', salt: 's', expiresAt: Date.now() + LOGIN_TTL_MS }),
    )
    const store = freshStore()
    store.clear()

    expect(store.isAuthenticated).toBe(false)
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })
})
