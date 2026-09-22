/** 展示格式化工具 */
import type { ScanStats } from '@/api/types'

/** 秒 → "3:45" / "1:02:03" */
export function formatDuration(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds || 0))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const mm = h > 0 ? String(m).padStart(2, '0') : String(m)
  const ss = String(sec).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}

/** 字节 → "1 KB" / "12.4 MB" */
export function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  const value = bytes / 1024 ** i
  let display: string
  if (i === 0 || Number.isInteger(value) || value >= 100) {
    display = String(Math.round(value))
  } else {
    display = value.toFixed(1)
  }
  return `${display} ${units[i]}`
}

/** ISO-8601 UTC → "2026-08-14 15:21"（本地时区） */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** ISO-8601 UTC → "2026-08-14" */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 千分位数字 */
export function formatCount(n: number): string {
  return new Intl.NumberFormat('zh-CN').format(n || 0)
}

/** 解析音乐目录 / 图书目录 lastScanStats 的 JSON 字符串 */
export function parseScanStats(raw: string | null | undefined): ScanStats | null {
  if (!raw) return null
  try {
    const v = JSON.parse(raw) as Partial<ScanStats>
    if (typeof v !== 'object' || v === null) return null
    return {
      added: Number(v.added) || 0,
      updated: Number(v.updated) || 0,
      missing: Number(v.missing) || 0,
      error: Number(v.error) || 0,
    }
  } catch {
    return null
  }
}
