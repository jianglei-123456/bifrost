/** 歌单 API（T4.6） */
import { request } from './client'
import type { PageResult, Playlist, PlaylistDetailView, Track } from './types'

export interface PlaylistBody {
  name?: string
  comment?: string
}

export function fetchPlaylists(): Promise<Playlist[]> {
  return request<Playlist[]>({ method: 'get', url: '/api/playlists' })
}

export function createPlaylist(body: PlaylistBody): Promise<Playlist> {
  return request<Playlist>({ method: 'post', url: '/api/playlists', data: body })
}

export function fetchPlaylist(id: number): Promise<PlaylistDetailView> {
  return request<PlaylistDetailView>({ method: 'get', url: `/api/playlists/${id}` })
}

export function updatePlaylist(id: number, body: PlaylistBody): Promise<Playlist> {
  return request<Playlist>({ method: 'put', url: `/api/playlists/${id}`, data: body })
}

export function deletePlaylist(id: number): Promise<void> {
  return request<void>({ method: 'delete', url: `/api/playlists/${id}` })
}

/**
 * 候选曲目（分页）：可加入本歌单的曲目——后端已排除「已在歌单的」与「文件缺失的」。
 *
 * `keyword` 为空即默认列表，非空即搜索；两者排序一致（入库时间倒序），
 * 所以「默认列表」与「搜索」是同一个端点换参数。
 */
export function fetchCandidateTracks(
  playlistId: number,
  params: { page: number; size: number; keyword: string },
): Promise<PageResult<Track>> {
  return request<PageResult<Track>>({
    method: 'get',
    url: `/api/playlists/${playlistId}/candidate-tracks`,
    params: {
      page: params.page,
      size: params.size,
      q: params.keyword.trim() || undefined,
    },
  })
}

/** 批量追加曲目（position 依次追加在末尾）；入参已由调用方保证非空 */
export function addPlaylistEntries(playlistId: number, trackIds: number[]): Promise<number> {
  return request<number>({
    method: 'post',
    url: `/api/playlists/${playlistId}/entries`,
    data: { trackIds },
  })
}

export function removePlaylistEntry(playlistId: number, entryId: number): Promise<void> {
  return request<void>({
    method: 'delete',
    url: `/api/playlists/${playlistId}/entries/${entryId}`,
  })
}
