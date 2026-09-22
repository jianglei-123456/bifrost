<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

import { fetchAlbum } from '@/api/browse'
import type { Album, Track } from '@/api/types'
import CoverArt from '@/components/CoverArt.vue'
import RatingStars from '@/components/RatingStars.vue'
import StarButton from '@/components/StarButton.vue'
import TrackTable from '@/components/TrackTable.vue'
import { formatCount, formatDateTime, formatDuration } from '@/utils/format'

const route = useRoute()
const id = Number(route.params.id)

const album = ref<Album | null>(null)
const tracks = ref<Track[]>([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const detail = await fetchAlbum(id)
    album.value = detail.album
    tracks.value = detail.tracks
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div v-loading="loading">
      <template v-if="album">
        <div class="page-header">
          <div class="head-main">
            <el-button link :icon="ArrowLeft" @click="$router.back()">返回</el-button>
            <h2 class="page-title">{{ album.title }}</h2>
          </div>
          <div class="page-actions">
            <StarButton :starred="!!album.starredAt" type="album" :id="album.id" @change="load()" />
            <RatingStars :model-value="album.rating" type="album" :id="album.id" @change="load()" />
          </div>
        </div>

        <section class="card album-hero">
          <CoverArt :album-id="album.id" :size="220" :title="album.title" />
          <div class="hero-meta">
            <div class="hero-title">{{ album.title }}</div>
            <div class="hero-artist">
              <router-link
                v-if="album.artistId"
                :to="{ name: 'artist-detail', params: { id: album.artistId } }"
                class="artist-link"
              >
                {{ album.albumArtistName || '未知艺术家' }}
              </router-link>
              <span v-else>{{ album.albumArtistName || '未知艺术家' }}</span>
            </div>
            <div class="hero-tags">
              <span v-if="album.year" class="tag">{{ album.year }}</span>
              <span v-if="album.genre" class="tag">{{ album.genre }}</span>
              <span class="tag data-mono">{{ formatDuration(album.duration) }}</span>
              <span v-if="album.coverSource" class="tag tag-dim"
                >封面：{{ album.coverSource }}</span
              >
            </div>
            <div class="hero-stats">
              <div class="hero-stat">
                <div class="hero-num data-mono">{{ formatCount(tracks.length) }}</div>
                <div class="hero-label">曲目</div>
              </div>
              <div class="hero-stat">
                <div class="hero-num data-mono">{{ formatCount(album.playCount) }}</div>
                <div class="hero-label">播放</div>
              </div>
              <div v-if="album.lastPlayed" class="hero-stat">
                <div class="hero-num data-mono last">
                  {{ formatDateTime(album.lastPlayed).slice(0, 10) }}
                </div>
                <div class="hero-label">最近播放</div>
              </div>
            </div>
          </div>
        </section>

        <section class="card table-card">
          <TrackTable :tracks="tracks" show-file-info show-stats @refresh="load" />
          <p v-if="!tracks.length" class="empty-hint">这张专辑还没有可展示的曲目</p>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.head-main {
  display: flex;
  align-items: center;
  gap: 6px;
}

.album-hero {
  display: flex;
  gap: 24px;
  padding: 24px;
  margin-bottom: 16px;
}

.hero-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}

.hero-title {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
}

.artist-link {
  color: var(--info);
  text-decoration: none;
}

.artist-link:hover {
  text-decoration: underline;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  font-size: 12px;
  color: var(--text-dim);
  background: var(--panel-raised);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 2px 10px;
}

.tag-dim {
  color: #5d6a82;
}

.hero-stats {
  display: flex;
  gap: 28px;
  margin-top: 6px;
}

.hero-stat {
  text-align: center;
}

.hero-num {
  font-size: 22px;
  font-weight: 700;
}

.hero-num.last {
  font-size: 15px;
  line-height: 28px;
}

.hero-label {
  font-size: 12px;
  color: var(--text-dim);
}

.table-card {
  padding: 8px 14px 14px;
}

@media (max-width: 720px) {
  .album-hero {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .hero-tags,
  .hero-stats {
    justify-content: center;
  }
}
</style>
