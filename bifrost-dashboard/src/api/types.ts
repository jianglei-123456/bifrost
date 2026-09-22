/**
 * 领域类型 —— 与本仓库后端（bifrost-api）的管理 REST 契约一一对应
 * 契约速查：docs/api-contract-notes.md；权威来源是 bifrost-api 源码与 hurl/
 */

// ── 通用信封与分页 ──

/** 统一信封 {code, message, data}；code===0 为成功 */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

/** 分页结果；page 从 0 起，size 默认 20 最大 200 */
export interface PageResult<T> {
  total: number
  items: T[]
}

// ── 实体 ──

export type ScanStatus = 'IDLE' | 'SCANNING'

/** 音乐目录（/api/music-roots MusicRootDto；mediaType 恒为 MUSIC） */
export interface MusicRoot {
  id: number
  name: string
  path: string
  enabled: boolean
  mediaType: string
  lastScanAt: string | null
  scanStatus: ScanStatus
  /** 上次扫描统计 JSON 字符串（{added,updated,missing,error}）或 null */
  lastScanStats: string | null
}

// ── 图书（M2-book，契约：doc/m2-book/task/03-管理REST.md）──

/** 封面来源；null = 无封面 */
export type BookCoverSource = 'EMBEDDED' | 'UPLOADED' | null

/** 图书目录（/api/book-roots BookRootDto；mediaType 恒为 BOOK） */
export interface BookRoot {
  id: number
  name: string
  path: string
  enabled: boolean
  mediaType: string
  lastScanAt: string | null
  scanStatus: ScanStatus
}

/** 图书（/api/books BookDto） */
export interface Book {
  id: number
  title: string
  authors: string | null
  language: string | null
  publisher: string | null
  /** 出版年（仅年份，1–9999） */
  pubDate: number | null
  description: string | null
  subject: string | null
  identifier: string | null
  series: string | null
  seriesIndex: number | null
  rights: string | null
  /** EPUB | PDF */
  format: string
  extension: string
  fileSize: number
  /** ISO-8601 UTC（后端 Long epoch ms 转 Instant） */
  fileLastModified: string | null
  /**
   * 文档指纹（M3-sync）：KOSync 客户端对文件内容算出的 partial MD5；
   * 未算过为 null。与扫描用的 fingerprint（path+size+mtime）是两套东西。
   */
  partialMd5?: string | null
  coverSource: BookCoverSource
  /**
   * 相对路径（渲染须拼 window.location.origin）；无封面时为 null。
   * 后端 OPDS 端点支持 ?size=N 缩略图。
   */
  coverUrl: string | null
  isAvailable: boolean
  libraryRootId: number
  createdAt: string
  updatedAt: string
}

/** 扫描触发响应（/api/book-roots/{id}/scan 或 /scan/all） */
export interface BookScanTriggerView {
  scanStatus: ScanStatus
  /** 单根扫描=该根 id；scan/all 时为 null（用 /scan/status 的 currentRootId） */
  rootId: number | null
  message: string
}

/** 全局 BOOK 扫描状态（/api/book-roots/scan/status） */
export interface BookScanStatusView {
  scanStatus: ScanStatus
  currentRootId: number | null
  startedAt: string | null
  lastScanAt: string | null
  /** 上次扫描统计 JSON 字符串（{added,updated,missing,error}）或 null */
  lastStats: string | null
}

export interface Artist {
  id: number
  name: string
  indexLetter: string
  musicBrainzId: string | null
  /** 收藏时间；null=未收藏 */
  starredAt: string | null
  /** 评分 1–5；0=无 */
  rating: number
  playCount: number
  lastPlayed: string | null
  createdAt: string
  updatedAt: string
}

/** 艺术家列表视图（含专辑数） */
export interface ArtistView {
  id: number
  name: string
  indexLetter: string
  albumCount: number
  starredAt: string | null
  rating: number
  playCount: number
  lastPlayed: string | null
}

export interface Album {
  id: number
  title: string
  artistId: number | null
  albumArtistName: string | null
  year: number | null
  genre: string | null
  coverSource: string | null
  /** 总时长（秒） */
  duration: number
  playCount: number
  lastPlayed: string | null
  starredAt: string | null
  rating: number
  createdAt: string
  updatedAt: string
}

export interface Track {
  id: number
  title: string
  trackNo: number
  discNo: number
  artistId: number | null
  artistName: string | null
  albumArtistName: string | null
  albumId: number
  genre: string | null
  year: number | null
  /** 时长（秒） */
  duration: number
  /** 码率 kbps */
  bitrate: number
  /** 采样率 Hz */
  sampleRate: number
  /** 容器格式 mp3/flac/m4a/wav */
  format: string
  filePath: string
  fileSize: number
  fileLastModified: number
  fingerprint: string
  playCount: number
  lastPlayed: string | null
  starredAt: string | null
  rating: number
  /** 文件是否仍存在；缺失=隐藏 */
  isAvailable: boolean
  libraryRootId: number
  createdAt: string
  updatedAt: string
}

export interface ArtistDetail {
  artist: Artist
  albums: Album[]
}

export interface AlbumDetail {
  album: Album
  tracks: Track[]
}

export interface SearchResult {
  artists: Artist[]
  albums: Album[]
  tracks: Track[]
}

export interface ScanStats {
  added: number
  updated: number
  missing: number
  error: number
}

/** 全局音乐扫描状态（/api/music-roots/scan/status） */
export interface MusicScanStatusView {
  scanning: boolean
  roots: MusicRoot[]
}

export interface Playlist {
  id: number
  name: string
  ownerId: number
  isPublic: boolean
  comment: string | null
  createdAt: string
  updatedAt: string
}

export interface PlaylistEntryView {
  entryId: number
  position: number
  /** 曲目可能为 null（缺失文件时后端保留引用） */
  track: Track | null
}

export interface PlaylistDetailView {
  playlist: Playlist
  entries: PlaylistEntryView[]
}

export interface UserView {
  username: string
  role: string
}

export type AnnotationType = 'track' | 'album' | 'artist'

/** /api/albums 的 type 参数（对应 Subsonic getAlbumList 语义） */
export type AlbumListType =
  | 'alphabeticalByName'
  | 'highest'
  | 'frequent'
  | 'recent'
  | 'newest'
  | 'starred'
  | 'alphabeticalByArtist'
  | 'byYear'
  | 'byGenre'
  | 'random'

// ── 阅读进度同步（M3-sync，契约：doc/m3-sync/task/03-管理REST.md）──
// 注意：本域的时间字段都是 ISO-8601 字符串（与 /api/books 一致），
// 秒级 epoch 只出现在 KOSync 协议端点给设备用的 timestamp 里。

/** 同步账号（/api/book-sync/account）；password 为明文回显（设计决策 R2a） */
export interface SyncAccount {
  username: string
  password: string
  /** 是否放行设备端自助注册（Register 按钮） */
  registrationEnabled: boolean
  /** 未匹配的新指纹是否触发一次图书扫描 */
  autoScanOnUnmatched: boolean
  /** 已拼好的服务地址（带 http://），可直接抄进 KOReader */
  serverUrl: string
  /** true = 地址来自 bifrost.kosync.publicBaseUrl 配置，false = 按当前主机名+端口拼的兜底值 */
  serverUrlHintConfigured: boolean
}

/** 进度与图书的匹配来源 */
export type ProgressMatchSource = 'AUTO' | 'MANUAL'

/** 阅读进度（/api/book-sync/progress）
 *  注意：后端 DTO 是 @JsonInclude(NON_NULL)——**值为 null 的字段会被整个省略**，
 *  所以这些字段是"可选且可能为 null"（孤儿行就没有 bookXxx/coverUrl/matchSource）。 */
export interface ReadingProgress {
  id: number
  /** null/缺省 = 未匹配（孤儿） */
  bookId?: number | null
  bookTitle?: string | null
  bookAuthors?: string | null
  libraryRootId?: number | null
  coverUrl?: string | null
  documentFingerprint: string
  /** 0–1 */
  percentage: number
  /** 位置串（PDF=页码 / EPUB=XPointer），原样来自设备 */
  progress: string
  device: string
  deviceId: string
  reportedAt: string
  matchSource?: ProgressMatchSource | null
  ignored: boolean
}

/** 孤儿进度（/api/book-sync/orphans）；同样按"null 即省略"处理 */
export interface OrphanProgress {
  id: number
  documentFingerprint: string
  percentage: number
  progress: string
  device: string
  deviceId: string
  createdAt: string
  /** null/缺省 = 自动扫描尚未结算（正常是极短窗口；长挂说明自动扫描被跳过或失败） */
  scanAttemptedAt?: string | null
  ignored: boolean
  /** 建议绑定目标（只建议、不自动绑） */
  suggestedBookId?: number | null
  suggestedBookTitle?: string | null
  /** 建议依据：FILENAME = md5(basename)，OPDS_NAME = md5(标题.扩展名) */
  suggestionReason?: 'FILENAME' | 'OPDS_NAME' | null
}

/** 同步设备（/api/book-sync/devices）—— 只读，协议层没有"踢设备" */
export interface SyncDevice {
  id: number
  deviceId: string
  deviceName: string
  firstSeenAt: string
  lastSeenAt: string
  reportCount: number
}

/** 阅读进度同步概览（/api/book-sync/stats） */
export interface BookSyncStats {
  progressCount: number
  matchedCount: number
  orphanCount: number
  deviceCount: number
  lastReportedAt: string | null
}

/** 人工"重新匹配"结果；matched=false 是正常结果（这本书确实还不在库里） */
export interface RematchResult {
  matched: boolean
  progress: ReadingProgress | null
}
