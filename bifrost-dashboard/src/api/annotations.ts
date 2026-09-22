/** 标注 API（T4.7）：三态收藏 + 评分 */
import { request } from './client'
import type { AnnotationType } from './types'

export interface AnnotationBody {
  type: AnnotationType
  id: number
}

/** 收藏（type ∈ track|album|artist） */
export function star(body: AnnotationBody): Promise<void> {
  return request<void>({ method: 'post', url: '/api/starred', data: body })
}

/** 取消收藏 */
export function unstar(body: AnnotationBody): Promise<void> {
  return request<void>({ method: 'delete', url: '/api/starred', data: body })
}

/** 评分 1–5；0=取消 */
export function rate(type: AnnotationType, id: number, rating: number): Promise<void> {
  return request<void>({
    method: 'put',
    url: '/api/rating',
    data: { type, id, rating },
  })
}
