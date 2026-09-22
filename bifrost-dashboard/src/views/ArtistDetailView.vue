<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

import { fetchArtist } from '@/api/browse'
import type { Album, Artist } from '@/api/types'
import AlbumCard from '@/components/AlbumCard.vue'
import RatingStars from '@/components/RatingStars.vue'
import StarButton from '@/components/StarButton.vue'
import { formatCount } from '@/utils/format'

const route = useRoute()
const id = Number(route.params.id)

const artist = ref<Artist | null>(null)
const albums = ref<Album[]>([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const detail = await fetchArtist(id)
    artist.value = detail.artist
    albums.value = detail.albums
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div v-loading="loading">
      <template v-if="artist">
        <div class="page-header">
          <div class="head-main">
            <el-button link :icon="ArrowLeft" @click="$router.back()">返回</el-button>
            <h2 class="page-title">{{ artist.name }}</h2>
          </div>
          <div class="page-actions">
            <StarButton
              :starred="!!artist.starredAt"
              type="artist"
              :id="artist.id"
              @change="load()"
            />
            <RatingStars
              :model-value="artist.rating"
              type="artist"
              :id="artist.id"
              @change="load()"
            />
          </div>
        </div>

        <section class="card artist-hero">
          <span class="hero-tile">{{ artist.name[0]?.toUpperCase() }}</span>
          <div class="hero-meta">
            <div class="hero-name">{{ artist.name }}</div>
            <div class="hero-sub">
              索引分组 <span class="data-mono">{{ artist.indexLetter }}</span>
              <template v-if="artist.musicBrainzId">
                · MusicBrainz <span class="data-mono">{{ artist.musicBrainzId }}</span>
              </template>
            </div>
          </div>
          <div class="hero-stats">
            <div class="hero-stat">
              <div class="hero-num data-mono">{{ formatCount(albums.length) }}</div>
              <div class="hero-label">专辑</div>
            </div>
            <div class="hero-stat">
              <div class="hero-num data-mono">{{ formatCount(artist.playCount) }}</div>
              <div class="hero-label">播放</div>
            </div>
          </div>
        </section>

        <section v-if="albums.length">
          <div class="section-head">
            <h3 class="panel-title">专辑</h3>
          </div>
          <div class="album-grid">
            <AlbumCard v-for="album in albums" :key="album.id" :album="album" />
          </div>
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

.artist-hero {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 22px 24px;
  margin-bottom: 8px;
}

.hero-tile {
  width: 72px;
  height: 72px;
  border-radius: 16px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 30px;
  color: #0b0e14;
  background: var(--spectrum);
}

.hero-meta {
  flex: 1;
  min-width: 0;
}

.hero-name {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
}

.hero-sub {
  font-size: 12.5px;
  color: var(--text-dim);
  margin-top: 4px;
}

.hero-stats {
  display: flex;
  gap: 28px;
}

.hero-stat {
  text-align: center;
}

.hero-num {
  font-size: 22px;
  font-weight: 700;
}

.hero-label {
  font-size: 12px;
  color: var(--text-dim);
}

.section-head {
  margin: 20px 0 12px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
}

.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 14px;
}

@media (max-width: 640px) {
  .artist-hero {
    flex-wrap: wrap;
  }
}
</style>
