/** 浏览 API（T4.4）：艺术家 / 专辑 / 曲目 */
import { request } from './client'
import type {
  Album,
  AlbumDetail,
  AlbumListType,
  Artist,
  ArtistDetail,
  ArtistView,
  PageResult,
  Track,
} from './types'

export interface PageQuery {
  page?: number
  size?: number
  q?: string
}

export interface ArtistsQuery extends PageQuery {
  indexLetter?: string
}

export interface AlbumsQuery extends PageQuery {
  type?: AlbumListType
  genre?: string
  fromYear?: number
  toYear?: number
  artistId?: number
}

export interface TracksQuery extends PageQuery {
  albumId?: number
  artistId?: number
}

export function fetchArtists(query: ArtistsQuery = {}): Promise<PageResult<ArtistView>> {
  return request<PageResult<ArtistView>>({
    method: 'get',
    url: '/api/artists',
    params: { page: 0, size: 200, ...query },
  })
}

export function fetchArtist(id: number): Promise<ArtistDetail> {
  return request<ArtistDetail>({ method: 'get', url: `/api/artists/${id}` })
}

export function fetchAlbums(query: AlbumsQuery = {}): Promise<PageResult<Album>> {
  return request<PageResult<Album>>({
    method: 'get',
    url: '/api/albums',
    params: { page: 0, size: 200, ...query },
  })
}

export function fetchAlbum(id: number): Promise<AlbumDetail> {
  return request<AlbumDetail>({ method: 'get', url: `/api/albums/${id}` })
}

export function fetchTracks(query: TracksQuery = {}): Promise<PageResult<Track>> {
  return request<PageResult<Track>>({
    method: 'get',
    url: '/api/tracks',
    params: { page: 0, size: 200, ...query },
  })
}

export function fetchTrack(id: number): Promise<Track> {
  return request<Track>({ method: 'get', url: `/api/tracks/${id}` })
}

/** 导出类型便于视图复用 */
export type { Album, AlbumListType, Artist, ArtistDetail, ArtistView, Track }
