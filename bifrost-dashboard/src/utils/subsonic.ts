/**
 * Subsonic 令牌与封面 URL 构建（ADR-0001：管理端封面经 /rest/getCoverArt 获取）
 * 协议：t = md5(口令 + salt) 小写 hex；salt ≥6 字符（见《Subsonic_API_参考》）
 */
import md5 from 'blueimp-md5'

export const SUBSONIC_API_VERSION = '1.16.1'
export const SUBSONIC_CLIENT_ID = 'bifrost-dashboard'

/** Subsonic 令牌：md5(password + salt) 小写 hex */
export function subsonicToken(password: string, salt: string): string {
  return md5(`${password}${salt}`)
}

/** 生成 /rest/getCoverArt.view 封面 URL（id 为数字专辑主键，映射为 al-<id>） */
export function buildCoverUrl(
  albumId: number,
  size: number,
  username: string,
  password: string,
  salt: string,
): string {
  const t = subsonicToken(password, salt)
  const params = new URLSearchParams({
    id: `al-${albumId}`,
    size: String(size),
    u: username,
    t,
    s: salt,
    v: SUBSONIC_API_VERSION,
    c: SUBSONIC_CLIENT_ID,
    f: 'json',
  })
  return `/rest/getCoverArt.view?${params.toString()}`
}
