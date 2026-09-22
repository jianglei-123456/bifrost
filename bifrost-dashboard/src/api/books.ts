/** 图书 API（M2-book，/api/books CRUD + 封面） */
import type { AxiosProgressEvent } from 'axios'

import { request } from './client'
import type { Book, PageResult } from './types'

export interface BooksQuery {
  /** 0-based */
  page?: number
  /** 默认 20，后端钳到 1–200 */
  size?: number
  title?: string
  /** LIKE 匹配（参数名是 author 单数，匹配 authors 字段） */
  author?: string
  series?: string
  libraryRootId?: number
  isAvailable?: boolean
}

/** PATCH 可写字段（白名单见后端 BookController.WRITABLE；day-one 不含 rating/starredAt） */
export type BookPatch = Record<string, unknown>

export function fetchBooks(query: BooksQuery = {}): Promise<PageResult<Book>> {
  return request<PageResult<Book>>({ method: 'get', url: '/api/books', params: query })
}

export function fetchBook(id: number): Promise<Book> {
  return request<Book>({ method: 'get', url: `/api/books/${id}` })
}

export function updateBook(id: number, patch: BookPatch): Promise<Book> {
  return request<Book>({ method: 'patch', url: `/api/books/${id}`, data: patch })
}

/** 删 DB 行（不删文件） */
export function deleteBook(id: number): Promise<void> {
  return request<void>({ method: 'delete', url: `/api/books/${id}` })
}

/** 上传替换封面（multipart; file=<File>；浏览器自动设 Content-Type+boundary） */
export function uploadBookCover(
  id: number,
  file: File,
  onProgress?: (e: AxiosProgressEvent) => void,
): Promise<void> {
  const form = new FormData()
  form.append('file', file)
  return request<void>({
    method: 'post',
    url: `/api/books/${id}/cover`,
    data: form,
    onUploadProgress: onProgress,
  })
}

/** 删除封面（coverSource=null；下次扫描遇 EMBEDDED 自动重抽取） */
export function deleteBookCover(id: number): Promise<void> {
  return request<void>({ method: 'delete', url: `/api/books/${id}/cover` })
}
