/** 音乐目录与音乐扫描 API（/api/music-roots，ADR-0005 媒体前缀命名） */
import { request } from './client'
import type { MusicRoot, MusicScanStatusView, ScanStats } from './types'

export interface MusicRootRequest {
  name: string
  path: string
  enabled?: boolean
}

/** 列表（仅 MUSIC，含停用根，供启用开关往返） */
export function fetchMusicRoots(): Promise<MusicRoot[]> {
  return request<MusicRoot[]>({ method: 'get', url: '/api/music-roots' })
}

export function createMusicRoot(body: MusicRootRequest): Promise<MusicRoot> {
  return request<MusicRoot>({ method: 'post', url: '/api/music-roots', data: body })
}

/** 部分更新（name/path/enabled 均可选；不支持改 mediaType） */
export function updateMusicRoot(id: number, body: Partial<MusicRootRequest>): Promise<MusicRoot> {
  return request<MusicRoot>({ method: 'patch', url: `/api/music-roots/${id}`, data: body })
}

/** 删除（级联隐藏该目录下的曲目，记录保留） */
export function deleteMusicRoot(id: number): Promise<void> {
  return request<void>({ method: 'delete', url: `/api/music-roots/${id}` })
}

/**
 * 扫描类请求的超时：音乐扫描是**同步**接口（请求返回时扫描才结束），真实曲库一次全量扫描
 * 可能远超全局 30s。用 0 = 不超时——超时只会让 UI 误报失败而服务端仍在扫，得不偿失；
 * 互斥由后端全局锁保证（并发触发返回 409/1100）。
 */
const SCAN_TIMEOUT = 0

/** 单目录扫描（同步：扫描完成才返回统计）；fullScan=true 强制全量重解析 */
export function scanMusicRoot(id: number, fullScan = false): Promise<ScanStats> {
  return request<ScanStats>({
    method: 'post',
    url: `/api/music-roots/${id}/scan`,
    params: { fullScan },
    timeout: SCAN_TIMEOUT,
  })
}

/** 全部音乐目录扫描（同步、串行，仅 MUSIC） */
export function scanAllMusicRoots(fullScan = false): Promise<ScanStats> {
  return request<ScanStats>({
    method: 'post',
    url: '/api/music-roots/scan/all',
    params: { fullScan },
    timeout: SCAN_TIMEOUT,
  })
}

/** 全局音乐扫描状态（顶栏呼吸灯 / 总览页 / 系统页共用轮询源） */
export function fetchMusicScanStatus(): Promise<MusicScanStatusView> {
  return request<MusicScanStatusView>({ method: 'get', url: '/api/music-roots/scan/status' })
}
