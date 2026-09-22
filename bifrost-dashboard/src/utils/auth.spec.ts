import { describe, expect, it } from 'vitest'

import { generateSalt, toBasicHeader } from './auth'

describe('toBasicHeader', () => {
  it('base64(username:password)', () => {
    // RFC 例：Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ== 对应 Aladdin:open sesame
    expect(toBasicHeader('Aladdin', 'open sesame')).toBe('QWxhZGRpbjpvcGVuIHNlc2FtZQ==')
  })

  it('中文口令 UTF-8 安全', () => {
    const header = toBasicHeader('admin', '雷雨来了')
    // atob 得到的是 UTF-8 字节的 Latin-1 视图，需再按 UTF-8 解码回原串
    const decoded = new TextDecoder().decode(Uint8Array.from(atob(header), (c) => c.charCodeAt(0)))
    expect(decoded).toBe('admin:雷雨来了')
  })
})

describe('generateSalt', () => {
  it('默认长度且字符集安全', () => {
    const salt = generateSalt()
    expect(salt).toHaveLength(10)
    expect(salt).toMatch(/^[A-Za-z0-9]+$/)
  })

  it('两次生成大概率不同', () => {
    const a = generateSalt()
    const b = generateSalt()
    expect(a).not.toBe(b)
  })

  it('满足 Subsonic ≥6 字符要求', () => {
    expect(generateSalt(6)).toHaveLength(6)
  })
})
