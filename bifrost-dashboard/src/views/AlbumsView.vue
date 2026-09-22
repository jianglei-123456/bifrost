<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { fetchAlbums } from '@/api/browse'
import type { Album, AlbumListType } from '@/api/types'
import AlbumCard from '@/components/AlbumCard.vue'
import { formatCount } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const TABS: { label: string; type: AlbumListType }[] = [
  { label: '最新', type: 'newest' },
  { label: '常听', type: 'frequent' },
  { label: '高分', type: 'highest' },
  { label: '随机', type: 'random' },
  { label: '收藏', type: 'starred' },
]

const active = ref<AlbumListType>((route.query.type as AlbumListType) || 'newest')
const albums = ref<Album[]>([])
const total = ref(0)
const loading = ref(false)

async function load(page = 0, pageSize = 24) {
  loading.value = true
  try {
    const res = await fetchAlbums({ page, size: pageSize, type: active.value })
    albums.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function switchTab(type: AlbumListType) {
  active.value = type
  router.replace({ query: { type } })
  load()
}

watch(
  () => route.query.type,
  (type) => {
    if (type && type !== active.value) {
      active.value = type as AlbumListType
      load()
    }
  },
)

onMounted(() => load())
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">专辑</h2>
        <p class="page-sub">共 {{ formatCount(total) }} 张专辑</p>
      </div>
    </div>

    <el-tabs v-model="active" class="album-tabs" @tab-change="switchTab($event as AlbumListType)">
      <el-tab-pane v-for="tab in TABS" :key="tab.type" :label="tab.label" :name="tab.type" />
    </el-tabs>

    <div v-loading="loading" class="album-grid-wrap">
      <p v-if="!loading && !albums.length" class="empty-hint">这里还没有专辑</p>
      <div v-else class="album-grid">
        <AlbumCard v-for="album in albums" :key="album.id" :album="album" />
      </div>
      <div v-if="total > 24" class="pager">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="total"
          :page-size="24"
          @current-change="(p: number) => load(p - 1)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.album-tabs {
  margin-bottom: 6px;
}

.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 14px;
  min-height: 120px;
}

.pager {
  display: flex;
  justify-content: center;
  padding-top: 18px;
}
</style>
