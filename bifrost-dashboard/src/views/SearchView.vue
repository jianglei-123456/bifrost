<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'

import { search } from '@/api/search'
import type { Album, Artist, Track } from '@/api/types'
import AlbumCard from '@/components/AlbumCard.vue'
import TrackTable from '@/components/TrackTable.vue'

const query = ref('')
const searched = ref(false)
const loading = ref(false)

const artists = ref<Artist[]>([])
const albums = ref<Album[]>([])
const tracks = ref<Track[]>([])

async function run() {
  const q = query.value.trim()
  if (!q) return
  loading.value = true
  searched.value = true
  try {
    const res = await search(q, 20)
    artists.value = res.artists
    albums.value = res.albums
    tracks.value = res.tracks
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">搜索</h2>
        <p class="page-sub">全局搜索艺术家、专辑与曲目</p>
      </div>
    </div>

    <div class="search-bar">
      <el-input
        v-model="query"
        :prefix-icon="Search"
        size="large"
        placeholder="输入关键词，如：周杰伦 / 叶惠美 / 晴天"
        clearable
        @keyup.enter="run"
      />
      <el-button type="primary" size="large" :loading="loading" @click="run">搜索</el-button>
    </div>

    <div v-loading="loading">
      <template v-if="searched">
        <p v-if="!artists.length && !albums.length && !tracks.length" class="empty-hint">
          没有找到与「{{ query }}」相关的内容
        </p>

        <section v-if="artists.length">
          <h3 class="panel-title">艺术家</h3>
          <div class="card list-card">
            <ul class="artist-list">
              <li
                v-for="artist in artists"
                :key="artist.id"
                class="artist-row"
                role="button"
                tabindex="0"
                @click="$router.push({ name: 'artist-detail', params: { id: artist.id } })"
                @keyup.enter="$router.push({ name: 'artist-detail', params: { id: artist.id } })"
              >
                <span class="artist-tile">{{ artist.name[0]?.toUpperCase() }}</span>
                <div class="artist-main">
                  <span class="artist-name">{{ artist.name }}</span>
                  <span class="artist-sub">{{ artist.indexLetter }} · 索引分组</span>
                </div>
              </li>
            </ul>
          </div>
        </section>

        <section v-if="albums.length">
          <h3 class="panel-title">专辑</h3>
          <div class="album-grid">
            <AlbumCard v-for="album in albums" :key="album.id" :album="album" />
          </div>
        </section>

        <section v-if="tracks.length">
          <h3 class="panel-title">曲目</h3>
          <div class="card table-card">
            <TrackTable :tracks="tracks" show-album show-file-info @refresh="run" />
          </div>
        </section>
      </template>
      <p v-else class="empty-hint">跨过彩虹桥，找到你的音乐 —— 输入关键词开始</p>
    </div>
  </div>
</template>

<style scoped>
.search-bar {
  display: flex;
  gap: 10px;
  max-width: 640px;
  margin-bottom: 24px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin: 22px 0 12px;
}

.list-card {
  padding: 6px 14px;
}

.artist-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.artist-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.13s ease;
}

.artist-row:hover {
  background: var(--panel-raised);
}

.artist-tile {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 15px;
  color: #0b0e14;
  background: var(--spectrum);
}

.artist-main {
  display: flex;
  flex-direction: column;
}

.artist-name {
  font-weight: 600;
}

.artist-sub {
  font-size: 12px;
  color: var(--text-dim);
}

.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 14px;
}

.table-card {
  padding: 8px 14px 14px;
}
</style>
