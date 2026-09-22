import { describe, expect, it } from 'vitest'

import { buildCoverUrl, subsonicToken } from './subsonic'

describe('subsonicToken', () => {
  it('md5(password + salt) 小写 hex（对照已知向量）', () => {
    // md5('abc') = 900150983cd24fb0d6963f7d28e17f72
    expect(subsonicToken('a', 'bc')).toBe('900150983cd24fb0d6963f7d28e17f72')
  })
})

describe('buildCoverUrl', () => {
  it('生成 /rest/getCoverArt.view 参数完备的 URL', () => {
    const url = buildCoverUrl(5, 200, 'admin', 'pw', 's1234567')
    expect(url.startsWith('/rest/getCoverArt.view?')).toBe(true)

    const params = new URLSearchParams(url.split('?')[1])
    expect(params.get('id')).toBe('al-5') // 数字主键 → Subsonic ID 前缀
    expect(params.get('size')).toBe('200')
    expect(params.get('u')).toBe('admin')
    expect(params.get('t')).toBe(subsonicToken('pw', 's1234567'))
    expect(params.get('s')).toBe('s1234567')
    expect(params.get('v')).toBe('1.16.1')
    expect(params.get('c')).toBe('bifrost-dashboard')
  })

  it('id 与 size 正确映射', () => {
    const params = new URLSearchParams(buildCoverUrl(42, 64, 'u', 'p', 'salt1234').split('?')[1])
    expect(params.get('id')).toBe('al-42')
    expect(params.get('size')).toBe('64')
  })
})
