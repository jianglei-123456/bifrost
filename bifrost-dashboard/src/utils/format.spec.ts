import { describe, expect, it } from 'vitest'

import {
  formatBytes,
  formatCount,
  formatDate,
  formatDateTime,
  formatDuration,
  parseScanStats,
} from './format'

describe('formatDuration', () => {
  it('秒 → 分:秒', () => {
    expect(formatDuration(0)).toBe('0:00')
    expect(formatDuration(59)).toBe('0:59')
    expect(formatDuration(225)).toBe('3:45')
  })

  it('超过 1 小时 → 时:分:秒', () => {
    expect(formatDuration(3725)).toBe('1:02:05')
    expect(formatDuration(3600 * 24 + 61)).toBe('24:01:01')
  })

  it('负数与小数安全', () => {
    expect(formatDuration(-5)).toBe('0:00')
    expect(formatDuration(90.9)).toBe('1:30')
  })
})

describe('formatBytes', () => {
  it('各档位', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(1024)).toBe('1 KB')
    expect(formatBytes(1024 * 1024 * 12.4)).toBe('12.4 MB')
    expect(formatBytes(1024 ** 3 * 2)).toBe('2 GB')
  })
})

describe('formatDateTime / formatDate', () => {
  it('ISO 时间 → 本地格式', () => {
    // 使用固定时区无关的断言：仅校验形状
    expect(formatDateTime('2026-08-14T07:21:34Z')).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)
    expect(formatDate('2026-08-14T07:21:34Z')).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('空值 → —', () => {
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime(undefined)).toBe('—')
    expect(formatDate('not-a-date')).toBe('—')
  })
})

describe('formatCount', () => {
  it('千分位', () => {
    expect(formatCount(0)).toBe('0')
    expect(formatCount(1234)).toBe('1,234')
    expect(formatCount(1234567)).toBe('1,234,567')
  })
})

describe('parseScanStats', () => {
  it('解析 JSON 字符串', () => {
    expect(parseScanStats('{"added":3,"updated":1,"missing":2,"error":0}')).toEqual({
      added: 3,
      updated: 1,
      missing: 2,
      error: 0,
    })
  })

  it('非法输入 → null', () => {
    expect(parseScanStats(null)).toBeNull()
    expect(parseScanStats(undefined)).toBeNull()
    expect(parseScanStats('')).toBeNull()
    expect(parseScanStats('not json')).toBeNull()
  })

  it('缺字段容错为 0', () => {
    expect(parseScanStats('{"added":1}')).toEqual({ added: 1, updated: 0, missing: 0, error: 0 })
  })
})
