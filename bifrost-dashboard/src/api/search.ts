/** 搜索 API（T4.5）：全局搜索艺术家 / 专辑 / 曲目 */
import { request } from './client'
import type { SearchResult } from './types'

export function search(query: string, size = 20): Promise<SearchResult> {
  return request<SearchResult>({
    method: 'get',
    url: '/api/search',
    params: { q: query, size },
  })
}
