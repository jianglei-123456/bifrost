<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'

import { fetchArtists } from '@/api/browse'
import type { ArtistView } from '@/api/types'
import RatingStars from '@/components/RatingStars.vue'
import StarButton from '@/components/StarButton.vue'
import { formatCount } from '@/utils/format'

const router = useRouter()

const artists = ref<ArtistView[]>([])
const total = ref(0)
const loading = ref(false)
const q = ref('')
const indexLetter = ref('')

const LETTERS = [...Array.from({ length: 26 }, (_, i) => String.fromCharCode(65 + i)), '#']

async function load(page = 0, pageSize = 50) {
  loading.value = true
  try {
    const res = await fetchArtists({
      page,
      size: pageSize,
      q: q.value || undefined,
      indexLetter: indexLetter.value || undefined,
    })
    artists.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(() => load())

function pickLetter(letter: string) {
  indexLetter.value = letter === indexLetter.value ? '' : letter
  load()
}

function goDetail(artist: ArtistView) {
  router.push({ name: 'artist-detail', params: { id: artist.id } })
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">艺术家</h2>
        <p class="page-sub">按索引分组浏览 —— 中文按拼音首字母</p>
      </div>
      <div class="page-actions">
        <el-input
          v-model="q"
          :prefix-icon="Search"
          placeholder="搜索艺术家"
          clearable
          style="width: 220px"
          @keyup.enter="load()"
          @clear="load()"
        />
        <el-button @click="load()">查询</el-button>
      </div>
    </div>

    <nav class="letter-nav" aria-label="索引分组">
      <button
        v-for="letter in LETTERS"
        :key="letter"
        type="button"
        class="letter"
        :class="{ active: indexLetter === letter }"
        @click="pickLetter(letter)"
      >
        {{ letter }}
      </button>
    </nav>

    <div v-loading="loading" class="card list-card">
      <p v-if="!loading && !artists.length" class="empty-hint">
        没有艺术家 —— 在「音乐库」添加音乐目录并扫描后这里会出现内容
      </p>
      <ul v-else class="artist-list">
        <li
          v-for="artist in artists"
          :key="artist.id"
          class="artist-row"
          role="button"
          tabindex="0"
          @click="goDetail(artist)"
          @keyup.enter="goDetail(artist)"
        >
          <span class="artist-tile">{{ artist.name[0]?.toUpperCase() }}</span>
          <div class="artist-main">
            <span class="artist-name">{{ artist.name }}</span>
            <span class="artist-sub">
              {{ artist.indexLetter }} · {{ formatCount(artist.albumCount) }} 张专辑 · 播放
              {{ formatCount(artist.playCount) }}
            </span>
          </div>
          <span class="artist-actions" @click.stop>
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
          </span>
        </li>
      </ul>
      <div v-if="total > 50" class="pager">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="total"
          :page-size="50"
          @current-change="(p: number) => load(p - 1)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.letter-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 14px;
}

.letter {
  min-width: 30px;
  height: 30px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: var(--panel);
  color: var(--text-dim);
  font-family: var(--font-display);
  font-size: 13px;
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease;
}

.letter:hover {
  color: var(--text);
  border-color: #2a3550;
}

.letter.active {
  color: #0b0e14;
  background: var(--spectrum);
  border-color: transparent;
  font-weight: 700;
}

.list-card {
  padding: 6px 14px 14px;
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
  padding: 10px 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.13s ease;
}

.artist-row:hover {
  background: var(--panel-raised);
}

.artist-tile {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 17px;
  color: #0b0e14;
  background: var(--spectrum);
}

.artist-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.artist-name {
  font-weight: 600;
  font-size: 14.5px;
}

.artist-sub {
  font-size: 12px;
  color: var(--text-dim);
  margin-top: 2px;
}

.artist-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.pager {
  display: flex;
  justify-content: center;
  padding-top: 14px;
}
</style>
