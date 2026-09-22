/** 认证工具 —— Basic 头（ADR-0002：凭据本地持久化，有效期 1 天） */

/** 生成 UTF-8 安全的 Basic 认证头值（base64(username:password)） */
export function toBasicHeader(username: string, password: string): string {
  const utf8 = new TextEncoder().encode(`${username}:${password}`)
  let binary = ''
  utf8.forEach((b) => (binary += String.fromCharCode(b)))
  return btoa(binary)
}

/** 生成 Subsonic 令牌盐值（协议要求 ≥6 字符；每会话随机） */
export function generateSalt(length = 10): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  const bytes = new Uint8Array(length)
  crypto.getRandomValues(bytes)
  let out = ''
  for (let i = 0; i < length; i++) out += chars[bytes[i] % chars.length]
  return out
}
