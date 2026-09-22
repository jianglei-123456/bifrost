/** 账号 API（T4.8） */
import { request } from './client'
import type { UserView } from './types'

/** 当前账号（200=凭据有效；401=未认证 → 拦截器处理）；silent 用于登录验证 */
export function fetchUser(silent = false): Promise<UserView> {
  return request<UserView>({ method: 'get', url: '/api/user', silent })
}

export function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  return request<void>({
    method: 'put',
    url: '/api/user/password',
    data: { oldPassword, newPassword },
  })
}
