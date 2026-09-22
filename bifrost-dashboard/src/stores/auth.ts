/**
 * 登录态：口令每次请求由 axios 拦截器注入 Basic 头（ADR-0002）；
 * 登录成功后凭据连同过期时间持久化到 localStorage，有效期 1 天（刷新页面保持登录，
 * 过期自动清除并要求重新登录）；封面请求用同一口令派生 Subsonic 令牌（ADR-0001）。
 */
import { defineStore } from 'pinia'

import { fetchUser } from '@/api/user'
import { generateSalt, toBasicHeader } from '@/utils/auth'

export interface Credentials {
  username: string
  password: string
  salt: string
}

/** 登录有效期：1 天（毫秒） */
export const LOGIN_TTL_MS = 24 * 60 * 60 * 1000

/** localStorage 存储键（v1：含过期时间戳的凭据快照） */
export const AUTH_STORAGE_KEY = 'bifrost.auth.v1'

interface StoredSession {
  username: string
  password: string
  salt: string
  expiresAt: number
}

const EMPTY_SESSION = { username: '', password: '', salt: '' } as const

function readStoredSession(): { username: string; password: string; salt: string } {
  let raw: string | null = null
  try {
    raw = localStorage.getItem(AUTH_STORAGE_KEY)
  } catch {
    return { ...EMPTY_SESSION }
  }
  if (!raw) return { ...EMPTY_SESSION }

  let session: StoredSession
  try {
    session = JSON.parse(raw) as StoredSession
  } catch {
    // 快照损坏：清除，避免每次启动都解析失败
    clearStoredSession()
    return { ...EMPTY_SESSION }
  }
  if (!session.username || !session.password || typeof session.expiresAt !== 'number') {
    clearStoredSession()
    return { ...EMPTY_SESSION }
  }
  // 已过期：清除本地快照，视为未登录
  if (Date.now() >= session.expiresAt) {
    clearStoredSession()
    return { ...EMPTY_SESSION }
  }
  return { username: session.username, password: session.password, salt: session.salt }
}

function persistSession(username: string, password: string, salt: string): void {
  try {
    const session: StoredSession = { username, password, salt, expiresAt: Date.now() + LOGIN_TTL_MS }
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
  } catch {
    // localStorage 不可用（隐私模式等）：退化为纯内存会话，不阻断登录
  }
}

function clearStoredSession(): void {
  try {
    localStorage.removeItem(AUTH_STORAGE_KEY)
  } catch {
    // 同上，忽略
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => {
    const saved = readStoredSession()
    return {
      username: saved.username,
      password: saved.password,
      /** Subsonic 令牌盐值（每登录会话随机，ADR-0001） */
      salt: saved.salt,
    }
  },
  getters: {
    credentials(state): Credentials | null {
      return state.username && state.password
        ? { username: state.username, password: state.password, salt: state.salt || generateSalt() }
        : null
    },
    basicHeader(state): string {
      return toBasicHeader(state.username, state.password)
    },
    isAuthenticated(): boolean {
      return Boolean(this.username && this.password)
    },
  },
  actions: {
    /**
     * 登录：先写凭据（让请求拦截器带上 Basic 头），再调 GET /api/user 验证；
     * 401 即失败并清空。silent=true 时不弹全局错误（登录页自给文案）。
     * 成功后持久化本地快照（有效期 LOGIN_TTL_MS）。
     */
    async login(username: string, password: string, silent = false): Promise<void> {
      this.username = username
      this.password = password
      this.salt = generateSalt()
      try {
        const user = await fetchUser(silent)
        this.username = user.username
        persistSession(this.username, this.password, this.salt)
      } catch (error) {
        this.clear()
        throw error
      }
    },
    clear() {
      this.username = ''
      this.password = ''
      this.salt = ''
      clearStoredSession()
    },
  },
})
