/**
 * 路由：登录态守卫（ADR-0002）。除 /login 外全部需要认证。
 * 页面标题经 meta.title 供顶栏展示。
 */
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    public?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: () => import('@/layout/AppLayout.vue'),
    redirect: { name: 'overview' },
    children: [
      {
        path: '',
        name: 'overview',
        component: () => import('@/views/OverviewView.vue'),
        meta: { title: '总览' },
      },
      {
        path: 'artists',
        name: 'artists',
        component: () => import('@/views/ArtistsView.vue'),
        meta: { title: '艺术家' },
      },
      {
        path: 'artists/:id(\\d+)',
        name: 'artist-detail',
        component: () => import('@/views/ArtistDetailView.vue'),
        meta: { title: '艺术家详情' },
      },
      {
        path: 'albums',
        name: 'albums',
        component: () => import('@/views/AlbumsView.vue'),
        meta: { title: '专辑' },
      },
      {
        path: 'albums/:id(\\d+)',
        name: 'album-detail',
        component: () => import('@/views/AlbumDetailView.vue'),
        meta: { title: '专辑详情' },
      },
      {
        path: 'tracks',
        name: 'tracks',
        component: () => import('@/views/TracksView.vue'),
        meta: { title: '曲目' },
      },
      {
        path: 'search',
        name: 'search',
        component: () => import('@/views/SearchView.vue'),
        meta: { title: '搜索' },
      },
      {
        path: 'playlists',
        name: 'playlists',
        component: () => import('@/views/PlaylistsView.vue'),
        meta: { title: '歌单' },
      },
      {
        path: 'playlists/:id(\\d+)',
        name: 'playlist-detail',
        component: () => import('@/views/PlaylistDetailView.vue'),
        meta: { title: '歌单详情' },
      },
      {
        path: 'music-library',
        name: 'music-library',
        component: () => import('@/views/MusicLibraryView.vue'),
        meta: { title: '音乐库' },
      },
      // 旧路径（库根管理 / 扫描管理合并进「音乐库」）
      { path: 'library-roots', redirect: { name: 'music-library' } },
      { path: 'scan', redirect: { name: 'music-library' } },
      {
        path: 'books/roots',
        name: 'book-roots',
        component: () => import('@/views/BookRootsView.vue'),
        meta: { title: '图书库' },
      },
      {
        path: 'books/progress',
        name: 'book-sync',
        component: () => import('@/views/BookSyncView.vue'),
        meta: { title: '阅读进度' },
      },
      {
        path: 'books',
        name: 'books',
        component: () => import('@/views/BooksView.vue'),
        meta: { title: '图书' },
      },
      {
        path: 'books/:id(\\d+)',
        name: 'book-detail',
        component: () => import('@/views/BookDetailView.vue'),
        meta: { title: '图书详情' },
      },
      {
        path: 'account',
        name: 'account',
        component: () => import('@/views/AccountView.vue'),
        meta: { title: '账号设置' },
      },
      {
        path: 'system',
        name: 'system',
        component: () => import('@/views/SystemView.vue'),
        meta: { title: '系统信息' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  // base 取 vite 的 BASE_URL：开发时是 '/'，1.0.0 起镜像里由 `vite build --base=/admin/` 注入。
  // 注意不能省略这个参数——vue-router 的 createWebHistory() 无参时只认 <base href> 标签，
  // 否则退化成 '/'，在 /admin/ 下刷新任何页面都会匹配不到路由（见仓库根 docs/adr/0007）。
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'overview' }
  }
  return true
})

export default router
