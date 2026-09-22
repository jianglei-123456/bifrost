<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent])

import { fetchAlbums, fetchArtists, fetchTracks } from '@/api/browse'
import { fetchMusicRoots } from '@/api/musicRoots'
import type { Album } from '@/api/types'
import AlbumCard from '@/components/AlbumCard.vue'
import StatCard from '@/components/StatCard.vue'
import { useMusicScanStore } from '@/stores/musicScan'
import { formatCount, formatDateTime } from '@/utils/format'

const musicScan = useMusicScanStore()
const loading = ref(true)

const stats = reactive({ artists: 0, albums: 0, tracks: 0, roots: 0 })
const frequent = ref<Album[]>([])
const newest = ref<Album[]>([])

onMounted(async () => {
  try {
    const [artists, albums, tracks, roots, freq, fresh] = await Promise.all([
      fetchArtists({ size: 1 }),
      fetchAlbums({ size: 1 }),
      fetchTracks({ size: 1 }),
      fetchMusicRoots(),
      fetchAlbums({ type: 'frequent', size: 8 }),
      fetchAlbums({ type: 'newest', size: 8 }),
      musicScan.refresh(),
    ])
    stats.artists = artists.total
    stats.albums = albums.total
    stats.tracks = tracks.total
    stats.roots = roots.length
    frequent.value = freq.items
    newest.value = fresh.items
  } finally {
    loading.value = false
  }
})

const musicScanBarOption = computed(() => {
  const s = musicScan.lastStats
  const data = s
    ? [
        { value: s.added, itemStyle: { color: 'var(--spectrum-3)' } },
        { value: s.updated, itemStyle: { color: 'var(--spectrum-4)' } },
        { value: s.missing, itemStyle: { color: 'var(--spectrum-2)' } },
        { value: s.error, itemStyle: { color: 'var(--spectrum-1)' } },
      ]
    : [
        { value: 0, itemStyle: { color: 'var(--spectrum-3)' } },
        { value: 0, itemStyle: { color: 'var(--spectrum-4)' } },
        { value: 0, itemStyle: { color: 'var(--spectrum-2)' } },
        { value: 0, itemStyle: { color: 'var(--spectrum-1)' } },
      ]
  return {
    grid: { left: 8, right: 8, top: 28, bottom: 0, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'var(--panel-raised)',
      borderColor: 'var(--line)',
      textStyle: { color: 'var(--text)', fontSize: 12 },
    },
    xAxis: {
      type: 'category',
      data: ['新增', '更新', '缺失', '错误'],
      axisLine: { lineStyle: { color: 'var(--line)' } },
      axisTick: { show: false },
      axisLabel: { color: 'var(--text-dim)' },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: 'var(--line)' } },
      axisLabel: { color: '#5d6a82' },
    },
    series: [
      {
        type: 'bar',
        data,
        barWidth: 28,
        itemStyle: { borderRadius: [6, 6, 0, 0] },
      },
    ],
  }
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">音乐库总览</h2>
        <p class="page-sub">彩虹桥联通的一切 —— 音乐库的当前面貌</p>
      </div>
      <div class="page-actions">
        <span v-if="musicScan.lastScanAt" class="data-mono last-scan">
          最近扫描 {{ formatDateTime(musicScan.lastScanAt) }}
        </span>
      </div>
    </div>

    <div v-loading="loading" class="overview">
      <div class="stat-row">
        <StatCard label="艺术家" :value="formatCount(stats.artists)" />
        <StatCard label="专辑" :value="formatCount(stats.albums)" />
        <StatCard label="曲目" :value="formatCount(stats.tracks)" />
        <StatCard label="音乐目录" :value="formatCount(stats.roots)" :mono="true" />
      </div>

      <div class="grid-2">
        <section class="card panel">
          <h3 class="panel-title">最近扫描统计</h3>
          <VChart v-if="musicScan.lastStats" class="chart" :option="musicScanBarOption" autoresize />
          <div v-else class="empty-hint">还没有扫描记录 —— 去「音乐库」发起第一次扫描</div>
        </section>

        <section class="card panel">
          <h3 class="panel-title">音乐目录状态</h3>
          <ul v-if="musicScan.roots.length" class="root-list">
            <li v-for="root in musicScan.roots" :key="root.id" class="root-item">
              <span class="root-dot" :class="{ off: !root.enabled }" />
              <div class="root-main">
                <span class="root-name">{{ root.name }}</span>
                <span class="data-mono root-path">{{ root.path }}</span>
              </div>
              <span class="root-time data-mono">{{ formatDateTime(root.lastScanAt) }}</span>
            </li>
          </ul>
          <div v-else class="empty-hint">
            还没有音乐目录 —— 去「音乐库」添加第一个目录，架起你的桥
          </div>
        </section>
      </div>

      <section v-if="frequent.length">
        <div class="section-head">
          <h3 class="panel-title">常听专辑</h3>
          <router-link class="more-link" :to="{ name: 'albums', query: { type: 'frequent' } }">
            查看全部
          </router-link>
        </div>
        <div class="album-grid">
          <AlbumCard v-for="album in frequent" :key="album.id" :album="album" />
        </div>
      </section>

      <section v-if="newest.length">
        <div class="section-head">
          <h3 class="panel-title">最新专辑</h3>
          <router-link class="more-link" :to="{ name: 'albums', query: { type: 'newest' } }">
            查看全部
          </router-link>
        </div>
        <div class="album-grid">
          <AlbumCard v-for="album in newest" :key="album.id" :album="album" />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.last-scan {
  font-size: 12px;
  color: var(--text-dim);
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 24px;
}

.panel {
  padding: 18px 20px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 14px;
}

.chart {
  height: 220px;
}

.root-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.root-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.root-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--success);
  flex-shrink: 0;
}

.root-dot.off {
  background: #4a5570;
}

.root-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.root-name {
  font-size: 13.5px;
  font-weight: 600;
}

.root-path {
  font-size: 11.5px;
  color: var(--text-dim);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.root-time {
  font-size: 11.5px;
  color: #5d6a82;
  flex-shrink: 0;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 20px 0 12px;
}

.more-link {
  color: var(--info);
  font-size: 13px;
  text-decoration: none;
}

.more-link:hover {
  text-decoration: underline;
}

.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 14px;
}

@media (max-width: 900px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
