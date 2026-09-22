/**
 * 阅读进度同步 API（M3-sync，/api/book-sync/**）
 *
 * 契约：doc/m3-sync/task/03-管理REST.md（仓库根）；速查 docs/api-contract-notes.md
 * 注意：POST /api/book-roots/{id}/scan 会吞掉扫描错误（见速查 §3）。
 * 统一信封 / 0-based 分页 / 错误码与其它 /api 端点一致（由 client.ts 的 request() 处理）。
 */
import { request } from './client'
import type {
  BookSyncStats,
  OrphanProgress,
  PageResult,
  ReadingProgress,
  RematchResult,
  SyncAccount,
  SyncDevice,
} from './types'

export interface ProgressQuery {
  /** 0-based，默认 0 */
  page?: number
  /** 默认 20，后端钳到 1–200 */
  size?: number
  /** 按书标题模糊 */
  title?: string
  /** 按设备名模糊 */
  device?: string
  libraryRootId?: number
  /** 只看孤儿（未匹配到图书的进度） */
  onlyOrphans?: boolean
}

export interface OrphanQuery {
  page?: number
  size?: number
  /** 默认 false（隐藏已忽略的孤儿） */
  includeIgnored?: boolean
}

/** 读同步账号（含明文口令，用于抄进阅读设备） */
export function fetchSyncAccount(): Promise<SyncAccount> {
  return request<SyncAccount>({ method: 'get', url: '/api/book-sync/account' })
}

/** 改用户名 / 改口令（只传要改的字段）；口令不得与管理员口令相同（后端 1000 拒绝） */
export function updateSyncAccount(patch: {
  username?: string
  password?: string
}): Promise<SyncAccount> {
  return request<SyncAccount>({ method: 'put', url: '/api/book-sync/account', data: patch })
}

/** 生成随机口令并落库（返回明文） */
export function generateSyncPassword(): Promise<SyncAccount> {
  return request<SyncAccount>({ method: 'post', url: '/api/book-sync/account/password' })
}

/** 进度列表 */
export function fetchProgress(query: ProgressQuery = {}): Promise<PageResult<ReadingProgress>> {
  return request<PageResult<ReadingProgress>>({
    method: 'get',
    url: '/api/book-sync/progress',
    params: query,
  })
}

/**
 * 重置（删除）单条进度。
 * 只删服务端记录：设备本地位置不变，设备下次推送会重新建立记录。
 */
export function deleteProgress(id: number): Promise<void> {
  return request<void>({ method: 'delete', url: `/api/book-sync/progress/${id}` })
}

/** 孤儿列表（含"建议绑定"） */
export function fetchOrphans(query: OrphanQuery = {}): Promise<PageResult<OrphanProgress>> {
  return request<PageResult<OrphanProgress>>({
    method: 'get',
    url: '/api/book-sync/orphans',
    params: query,
  })
}

/** 人工绑定孤儿到指定图书 */
export function bindOrphan(id: number, bookId: number): Promise<ReadingProgress> {
  return request<ReadingProgress>({
    method: 'post',
    url: `/api/book-sync/orphans/${id}/bind`,
    data: { bookId },
  })
}

/** 人工重新匹配一次（"我后来把书放进库了"）；matched=false 是正常结果 */
export function rematchOrphan(id: number): Promise<RematchResult> {
  return request<RematchResult>({ method: 'post', url: `/api/book-sync/orphans/${id}/rematch` })
}

/** 忽略 / 取消忽略 */
export function ignoreOrphan(id: number, ignored = true): Promise<void> {
  return request<void>({
    method: 'post',
    url: `/api/book-sync/orphans/${id}/ignore`,
    data: { ignored },
  })
}

/** 设备列表（信封形态 {total, items}，不分页） */
export function fetchSyncDevices(): Promise<PageResult<SyncDevice>> {
  return request<PageResult<SyncDevice>>({ method: 'get', url: '/api/book-sync/devices' })
}

/** 概览统计（页面头部卡片） */
export function fetchBookSyncStats(): Promise<BookSyncStats> {
  return request<BookSyncStats>({ method: 'get', url: '/api/book-sync/stats' })
}
