<script setup lang="ts">
import type { Album } from '@/api/types'
import CoverArt from './CoverArt.vue'

defineProps<{ album: Album }>()
</script>

<template>
  <router-link
    :to="{ name: 'album-detail', params: { id: album.id } }"
    class="album-card card card-hover"
  >
    <CoverArt :album-id="album.id" :size="200" :title="album.title" fluid />
    <div class="album-meta">
      <div class="album-title" :title="album.title">{{ album.title }}</div>
      <div class="album-sub" :title="album.albumArtistName || '未知艺术家'">
        {{ album.albumArtistName || '未知艺术家' }}
        <template v-if="album.year"> · {{ album.year }}</template>
      </div>
    </div>
  </router-link>
</template>

<style scoped>
.album-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px;
  text-decoration: none;
  color: inherit;
}

.album-cover {
  width: 100%;
}

.album-meta {
  padding: 0 2px 2px;
  min-width: 0;
}

.album-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.album-sub {
  font-size: 12px;
  color: var(--text-dim);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
}
</style>
