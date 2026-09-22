/** 图书目录与扫描 API（M2-book，/api/book-roots） */
import { request } from './client'
import type { BookRoot, BookScanStatusView, BookScanTriggerView } from './types'

export interface BookRootRequest {
  name: string
  path: string
  enabled?: boolean
}

/** 列表（含停用根，供启用开关往返） */
export function fetchBookRoots(): Promise<BookRoot[]> {
  return request<BookRoot[]>({ method: 'get', url: '/api/book-roots' })
}

export function createBookRoot(body: BookRootRequest): Promise<BookRoot> {
  return request<BookRoot>({ method: 'post', url: '/api/book-roots', data: body })
}

/** 部分更新（name/path/enabled 均可选；不支持改 mediaType） */
export function updateBookRoot(
  id: number,
  body: Partial<BookRootRequest>,
): Promise<BookRoot> {
  return request<BookRoot>({ method: 'patch', url: `/api/book-roots/${id}`, data: body })
}

/** 删除（级联 Book.isAvailable=false） */
export function deleteBookRoot(id: number): Promise<void> {
  return request<void>({ method: 'delete', url: `/api/book-roots/${id}` })
}

/** 触发单根扫描（异步：立即返回 SCANNING，须轮询 /scan/status 观察） */
export function scanBookRoot(id: number, force = false): Promise<BookScanTriggerView> {
  return request<BookScanTriggerView>({
    method: 'post',
    url: `/api/book-roots/${id}/scan`,
    params: { force },
  })
}

/** 触发全部 BOOK 根扫描（串行异步；rootId 为 null） */
export function scanAllBookRoots(force = false): Promise<BookScanTriggerView> {
  return request<BookScanTriggerView>({
    method: 'post',
    url: '/api/book-roots/scan/all',
    params: { force },
  })
}

/** 全局 BOOK 扫描状态（前端 1s/15s 自适应轮询） */
export function fetchBookScanStatus(): Promise<BookScanStatusView> {
  return request<BookScanStatusView>({ method: 'get', url: '/api/book-roots/scan/status' })
}
