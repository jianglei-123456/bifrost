<script setup lang="ts">
import { useRouter } from 'vue-router'

import type { Track } from '@/api/types'
import CoverArt from './CoverArt.vue'
import RatingStars from './RatingStars.vue'
import StarButton from './StarButton.vue'
import { formatBytes, formatDuration } from '@/utils/format'

withDefaults(
  defineProps<{
    tracks: Track[]
    /** 显示专辑列（曲目浏览/搜索时） */
    showAlbum?: boolean
    /** 显示文件信息列（码率/格式/大小） */
    showFileInfo?: boolean
    /** 显示播放统计列 */
    showStats?: boolean
    loading?: boolean
  }>(),
  { showAlbum: false, showFileInfo: false, showStats: false, loading: false },
)

const emit = defineEmits<{ refresh: [] }>()

const router = useRouter()

function goAlbum(row: { albumId?: number }) {
  if (!row.albumId) return
  router.push({ name: 'album-detail', params: { id: row.albumId } })
}
</script>

<template>
  <el-table :data="tracks" v-loading="loading" class="track-table" size="default">
    <el-table-column width="56" align="center">
      <template #default="{ row }">
        <CoverArt :album-id="row.albumId" :size="40" :title="row.title" />
      </template>
    </el-table-column>

    <el-table-column label="标题" min-width="220">
      <template #default="{ row }">
        <div class="tt-title">
          <span class="tt-title-text">{{ row.title }}</span>
          <span v-if="!row.isAvailable" class="tt-missing" title="文件缺失，已隐藏">缺失</span>
        </div>
      </template>
    </el-table-column>

    <el-table-column label="艺术家" min-width="140" show-overflow-tooltip>
      <template #default="{ row }">
        {{ row.artistName || '未知艺术家' }}
      </template>
    </el-table-column>

    <el-table-column v-if="showAlbum" label="专辑" min-width="160" show-overflow-tooltip>
      <template #default="{ row }">
        <button type="button" class="link-btn" @click="goAlbum(row)">
          {{ row.albumArtistName || '未知专辑' }}
        </button>
      </template>
    </el-table-column>

    <el-table-column label="时长" width="90" align="right">
      <template #default="{ row }">
        <span class="data-mono dim">{{ formatDuration(row.duration) }}</span>
      </template>
    </el-table-column>

    <el-table-column v-if="showFileInfo" label="格式" width="80">
      <template #default="{ row }">
        <span class="data-mono dim">{{ row.format?.toUpperCase() }}</span>
      </template>
    </el-table-column>

    <el-table-column v-if="showFileInfo" label="码率" width="90" align="right">
      <template #default="{ row }">
        <span class="data-mono dim">{{ row.bitrate ? `${row.bitrate} kbps` : '—' }}</span>
      </template>
    </el-table-column>

    <el-table-column v-if="showFileInfo" label="大小" width="100" align="right">
      <template #default="{ row }">
        <span class="data-mono dim">{{ formatBytes(row.fileSize) }}</span>
      </template>
    </el-table-column>

    <el-table-column v-if="showStats" label="播放" width="80" align="right">
      <template #default="{ row }">
        <span class="data-mono dim">{{ row.playCount }}</span>
      </template>
    </el-table-column>

    <el-table-column label="收藏" width="64" align="center">
      <template #default="{ row }">
        <StarButton
          :starred="!!row.starredAt"
          type="track"
          :id="row.id"
          @change="emit('refresh')"
        />
      </template>
    </el-table-column>

    <el-table-column label="评分" width="140">
      <template #default="{ row }">
        <RatingStars
          :model-value="row.rating"
          type="track"
          :id="row.id"
          @change="emit('refresh')"
        />
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.track-table :deep(.el-table__row) {
  cursor: default;
}

.tt-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.tt-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tt-missing {
  flex-shrink: 0;
  font-size: 10px;
  color: var(--danger);
  border: 1px solid var(--danger);
  border-radius: 999px;
  padding: 0 5px;
  line-height: 15px;
  opacity: 0.8;
}

.dim {
  color: var(--text-dim);
}

.link-btn {
  background: none;
  border: none;
  color: var(--info);
  cursor: pointer;
  padding: 0;
  font-size: inherit;
  font-family: inherit;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.link-btn:hover {
  text-decoration: underline;
}
</style>
