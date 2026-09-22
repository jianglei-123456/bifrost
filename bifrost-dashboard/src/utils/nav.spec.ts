/**
 * 侧边栏激活判定单测：钉住"详情页仍高亮所属分区、但同级子页面不互相点亮"。
 */
import { describe, expect, it } from 'vitest'

import { isNavItemActive } from '@/utils/nav'

const books = { to: '/books' }
const bookRoots = { to: '/books/roots' }
const progress = { to: '/books/progress' }
const overview = { to: '/', exact: true }
const artists = { to: '/artists' }

describe('isNavItemActive', () => {
  it('列表页自身激活', () => {
    expect(isNavItemActive(books, '/books')).toBe(true)
    expect(isNavItemActive(artists, '/artists')).toBe(true)
  })

  it('详情页仍算所属分区（否则详情页会没有任何高亮）', () => {
    expect(isNavItemActive(books, '/books/12')).toBe(true)
    expect(isNavItemActive(artists, '/artists/7')).toBe(true)
    expect(isNavItemActive({ to: '/albums' }, '/albums/3')).toBe(true)
    expect(isNavItemActive({ to: '/playlists' }, '/playlists/5')).toBe(true)
    // 详情页再跟子路径也算
    expect(isNavItemActive(books, '/books/12/notes')).toBe(true)
  })

  it('同级子页面不再被父项误点亮（本次修复的核心）', () => {
    expect(isNavItemActive(books, '/books/roots')).toBe(false)
    expect(isNavItemActive(books, '/books/progress')).toBe(false)
  })

  it('子页面各自点亮，且不被父路径点亮', () => {
    expect(isNavItemActive(bookRoots, '/books/roots')).toBe(true)
    expect(isNavItemActive(progress, '/books/progress')).toBe(true)
    expect(isNavItemActive(bookRoots, '/books')).toBe(false)
    expect(isNavItemActive(progress, '/books')).toBe(false)
  })

  it('非数字子路径不算详情（避免把任意子页面并进父分区）', () => {
    expect(isNavItemActive({ to: '/music-library' }, '/music-library')).toBe(true)
    expect(isNavItemActive({ to: '/music-library' }, '/music-library-x')).toBe(false)
    expect(isNavItemActive({ to: '/music-library' }, '/music-library/extra')).toBe(false)
  })

  it('前缀相同的不同分区不互相点亮', () => {
    expect(isNavItemActive({ to: '/book' }, '/books')).toBe(false)
    expect(isNavItemActive({ to: '/books' }, '/book')).toBe(false)
  })

  it('exact 项只认精确路径', () => {
    expect(isNavItemActive(overview, '/')).toBe(true)
    expect(isNavItemActive(overview, '/artists')).toBe(false)
    expect(isNavItemActive(overview, '/12')).toBe(false)
  })
})
