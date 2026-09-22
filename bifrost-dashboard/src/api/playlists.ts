/** 歌单 API（T4.6） */
import { request } from './client'
import type { Playlist, PlaylistDetailView } from './types'

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

/** 追加曲目（position 自动追加） */
export function addPlaylistEntry(playlistId: number, trackId: number): Promise<void> {
  return request<void>({
    method: 'post',
    url: `/api/playlists/${playlistId}/entries`,
    data: { trackId },
  })
}

export function removePlaylistEntry(playlistId: number, entryId: number): Promise<void> {
  return request<void>({
    method: 'delete',
    url: `/api/playlists/${playlistId}/entries/${entryId}`,
  })
}
