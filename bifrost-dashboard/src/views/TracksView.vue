<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'

import { fetchTracks } from '@/api/browse'
import type { Track } from '@/api/types'
import TrackTable from '@/components/TrackTable.vue'

const tracks = ref<Track[]>([])
const total = ref(0)
const loading = ref(false)
const q = ref('')

async function load(page = 0, pageSize = 50) {
  loading.value = true
  try {
    const res = await fetchTracks({
      page,
      size: pageSize,
      q: q.value || undefined,
    })
    tracks.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">曲目</h2>
        <p class="page-sub">全部曲目浏览（缺失文件默认隐藏）</p>
      </div>
      <div class="page-actions">
        <el-input
          v-model="q"
          :prefix-icon="Search"
          placeholder="搜索曲目"
          clearable
          style="width: 220px"
          @keyup.enter="load()"
          @clear="load()"
        />
        <el-button @click="load()">查询</el-button>
      </div>
    </div>

    <div class="card table-card">
      <TrackTable
        :tracks="tracks"
        :loading="loading"
        show-album
        show-file-info
        show-stats
        @refresh="load()"
      />
      <p v-if="!loading && !tracks.length" class="empty-hint">
        没有曲目 —— 在「音乐库」添加音乐目录并扫描后这里会出现内容
      </p>
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
.table-card {
  padding: 8px 14px 14px;
}

.pager {
  display: flex;
  justify-content: center;
  padding-top: 14px;
}
</style>
