<script setup lang="ts">
import { useRoute } from 'vue-router'
import {
  Collection,
  Folder,
  FolderOpened,
  Headset,
  InfoFilled,
  Mic,
  Odometer,
  Position,
  Reading,
  Search,
  Setting,
  User,
  VideoCamera,
} from '@element-plus/icons-vue'

import { isNavItemActive } from '@/utils/nav'
import type { NavItem } from '@/utils/nav'

interface NavGroup {
  title: string
  items: NavItem[]
  /** 预留分区（design.md §4：影音/图书 · 即将联通） */
  comingSoon?: string[]
}

const route = useRoute()

const groups: NavGroup[] = [
  {
    title: '音乐',
    items: [
      { label: '总览', to: '/', icon: Odometer, exact: true },
      { label: '艺术家', to: '/artists', icon: User },
      { label: '专辑', to: '/albums', icon: Headset },
      { label: '曲目', to: '/tracks', icon: Mic },
      { label: '搜索', to: '/search', icon: Search },
      { label: '歌单', to: '/playlists', icon: Collection },
      { label: '音乐库', to: '/music-library', icon: FolderOpened },
    ],
  },
  {
    title: '图书',
    items: [
      { label: '图书', to: '/books', icon: Reading },
      { label: '图书库', to: '/books/roots', icon: Folder },
      { label: '阅读进度', to: '/books/progress', icon: Position },
    ],
  },
  {
    title: '系统',
    items: [
      { label: '账号', to: '/account', icon: Setting },
      { label: '系统信息', to: '/system', icon: InfoFilled },
    ],
  },
  {
    title: '即将联通',
    items: [],
    comingSoon: ['影音'],
  },
]

function isActive(item: NavItem): boolean {
  return isNavItemActive(item, route.path)
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <svg class="brand-arc" viewBox="0 0 64 32" aria-hidden="true">
        <defs>
          <linearGradient id="spec-arc" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0" stop-color="#FF5D73" />
            <stop offset="0.25" stop-color="#FFB454" />
            <stop offset="0.5" stop-color="#3DDC97" />
            <stop offset="0.75" stop-color="#4CC9F0" />
            <stop offset="1" stop-color="#7B6CFF" />
          </linearGradient>
        </defs>
        <path
          d="M6 26a26 26 0 0 1 52 0"
          fill="none"
          stroke="url(#spec-arc)"
          stroke-width="6"
          stroke-linecap="round"
        />
        <path
          d="M16 26a16 16 0 0 1 32 0"
          fill="none"
          stroke="url(#spec-arc)"
          stroke-width="4"
          stroke-linecap="round"
          opacity="0.5"
        />
      </svg>
      <div class="brand-text">
        <span class="brand-name">Bifrost</span>
        <span class="brand-sub">彩虹桥 · 管理端</span>
      </div>
    </div>

    <nav class="nav" aria-label="主导航">
      <template v-for="group in groups" :key="group.title">
        <div class="nav-group-title">{{ group.title }}</div>
        <router-link
          v-for="item in group.items"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ active: isActive(item) }"
        >
          <el-icon :size="16"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
        <div v-for="name in group.comingSoon" :key="name" class="nav-item disabled">
          <el-icon :size="16">
            <component :is="name === '影音' ? VideoCamera : Reading" />
          </el-icon>
          <span>{{ name }}</span>
          <span class="coming-tag">即将联通</span>
        </div>
      </template>
    </nav>

    <div class="sidebar-foot">
      <span class="data-mono">v0.1.0</span>
      <span class="foot-text">Bifrost Dashboard</span>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: var(--sidebar-w);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--panel);
  border-right: 1px solid var(--line);
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 20px 16px;
}

.brand-arc {
  width: 40px;
  height: 20px;
  flex-shrink: 0;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-name {
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.brand-sub {
  font-size: 11px;
  color: var(--text-dim);
}

.nav {
  flex: 1;
  padding: 4px 12px 16px;
}

.nav-group-title {
  font-size: 11px;
  letter-spacing: 0.12em;
  color: #5d6a82;
  padding: 14px 8px 6px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  margin: 2px 0;
  border-radius: 8px;
  color: var(--text-dim);
  text-decoration: none;
  font-size: 13.5px;
  position: relative;
  transition:
    color 0.15s ease,
    background-color 0.15s ease;
}

.nav-item:hover {
  color: var(--text);
  background: var(--panel-raised);
}

/* 激活项：左侧 2px 光谱竖线（签名元素的日常形态） */
.nav-item.active {
  color: var(--text);
  background: var(--panel-raised);
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: -12px;
  top: 6px;
  bottom: 6px;
  width: 2px;
  border-radius: 2px;
  background: var(--spectrum);
}

.nav-item.disabled {
  color: #4a5570;
  cursor: not-allowed;
  justify-content: flex-start;
}

.coming-tag {
  margin-left: auto;
  font-size: 10px;
  color: #5d6a82;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 0 6px;
  line-height: 16px;
}

.sidebar-foot {
  padding: 12px 20px 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #5d6a82;
  font-size: 11px;
  border-top: 1px solid var(--line);
}

.foot-text {
  color: #4a5570;
}
</style>
