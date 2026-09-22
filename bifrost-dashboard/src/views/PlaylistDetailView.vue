<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'

import {
  addPlaylistEntry,
  deletePlaylist,
  fetchPlaylist,
  removePlaylistEntry,
  updatePlaylist,
} from '@/api/playlists'
import { search } from '@/api/search'
import type { PlaylistEntryView, Track } from '@/api/types'
import CoverArt from '@/components/CoverArt.vue'
import RatingStars from '@/components/RatingStars.vue'
import StarButton from '@/components/StarButton.vue'
import { formatDuration } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const playlist = ref<{ id: number; name: string; comment: string | null } | null>(null)
const entries = ref<PlaylistEntryView[]>([])
const loading = ref(true)

const editDialog = reactive({ visible: false, saving: false })
const editForm = reactive({ name: '', comment: '' })
const editFormRef = ref<FormInstance>()
const editRules: FormRules = {
  name: [{ required: true, message: '请输入歌单名称', trigger: 'blur' }],
}

// —— 添加曲目 ——
const addDialog = reactive({ visible: false, adding: false, trackId: 0 })
const candidates = ref<Track[]>([])
const addQuery = ref('')
const searchLoading = ref(false)

async function load() {
  loading.value = true
  try {
    const detail = await fetchPlaylist(id)
    playlist.value = {
      id: detail.playlist.id,
      name: detail.playlist.name,
      comment: detail.playlist.comment,
    }
    entries.value = detail.entries
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function saveEdit() {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) return
  editDialog.saving = true
  try {
    await updatePlaylist(id, { ...editForm })
    ElMessage.success('歌单已更新')
    editDialog.visible = false
    await load()
  } finally {
    editDialog.saving = false
  }
}

function openEdit() {
  editForm.name = playlist.value?.name ?? ''
  editForm.comment = playlist.value?.comment ?? ''
  editDialog.visible = true
}

async function removePlaylist() {
  await ElMessageBox.confirm(`删除歌单「${playlist.value?.name}」？此操作不可恢复。`, '删除歌单', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await deletePlaylist(id)
  ElMessage.success('歌单已删除')
  router.back()
}

async function runAddSearch() {
  const q = addQuery.value.trim()
  if (!q) {
    candidates.value = []
    return
  }
  searchLoading.value = true
  try {
    const res = await search(q, 20)
    candidates.value = res.tracks
  } finally {
    searchLoading.value = false
  }
}

async function confirmAdd() {
  if (!addDialog.trackId) return
  addDialog.adding = true
  try {
    await addPlaylistEntry(id, addDialog.trackId)
    ElMessage.success('已添加')
    addDialog.visible = false
    addDialog.trackId = 0
    await load()
  } finally {
    addDialog.adding = false
  }
}

async function removeEntry(entry: PlaylistEntryView) {
  await removePlaylistEntry(id, entry.entryId)
  ElMessage.success('已移除')
  await load()
}

const trackRows = computed(() =>
  entries.value
    .map((e) => e.track)
    .filter((t): t is Track => t !== null)
    .sort((a, b) => a.trackNo - b.trackNo),
)
</script>

<template>
  <div class="page">
    <div v-loading="loading">
      <template v-if="playlist">
        <div class="page-header">
          <div class="head-main">
            <el-button link :icon="ArrowLeft" @click="$router.back()">返回</el-button>
            <h2 class="page-title">{{ playlist.name }}</h2>
          </div>
          <div class="page-actions">
            <el-button :icon="Plus" @click="addDialog.visible = true">添加曲目</el-button>
            <el-button @click="openEdit">编辑</el-button>
            <el-button type="danger" plain @click="removePlaylist">删除歌单</el-button>
          </div>
        </div>
        <p v-if="playlist.comment" class="page-sub playlist-comment">{{ playlist.comment }}</p>

        <div class="card table-card">
          <el-table :data="trackRows">
            <el-table-column width="56" align="center">
              <template #default="{ row }">
                <CoverArt :album-id="row.albumId" :size="40" :title="row.title" />
              </template>
            </el-table-column>
            <el-table-column label="标题" min-width="220">
              <template #default="{ row }">
                <span class="tt-title">{{ row.title }}</span>
              </template>
            </el-table-column>
            <el-table-column label="艺术家" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.artistName || '未知艺术家' }}
              </template>
            </el-table-column>
            <el-table-column label="专辑" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.albumArtistName || '未知专辑' }}
              </template>
            </el-table-column>
            <el-table-column label="时长" width="90" align="right">
              <template #default="{ row }">
                <span class="data-mono dim">{{ formatDuration(row.duration) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="收藏" width="64" align="center">
              <template #default="{ row }">
                <StarButton :starred="!!row.starredAt" type="track" :id="row.id" @change="load()" />
              </template>
            </el-table-column>
            <el-table-column label="评分" width="140">
              <template #default="{ row }">
                <RatingStars :model-value="row.rating" type="track" :id="row.id" @change="load()" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="right">
              <template #default="{ row }">
                <el-button
                  link
                  type="danger"
                  @click="removeEntry(entries.find((e) => e.track?.id === row.id)!)"
                >
                  移除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <p v-if="!entries.length" class="empty-hint">歌单还是空的 —— 点击「添加曲目」开始整理</p>
        </div>
      </template>
    </div>

    <el-dialog v-model="editDialog.visible" title="编辑歌单" width="440px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.comment" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="editDialog.saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="addDialog.visible" title="添加曲目" width="640px" destroy-on-close>
      <div class="add-bar">
        <el-input
          v-model="addQuery"
          placeholder="搜索曲目，如：晴天"
          clearable
          @keyup.enter="runAddSearch"
        />
        <el-button :loading="searchLoading" @click="runAddSearch">搜索</el-button>
      </div>
      <div class="candidate-list">
        <label
          v-for="track in candidates"
          :key="track.id"
          class="candidate"
          :class="{ picked: addDialog.trackId === track.id }"
        >
          <input
            v-model="addDialog.trackId"
            type="radio"
            :value="track.id"
            class="candidate-radio"
          />
          <CoverArt :album-id="track.albumId" :size="34" :title="track.title" />
          <span class="candidate-title">{{ track.title }}</span>
          <span class="candidate-sub">{{ track.artistName || '未知艺术家' }}</span>
          <span class="candidate-dur data-mono">{{ formatDuration(track.duration) }}</span>
        </label>
        <p v-if="!candidates.length" class="empty-hint">搜索后选择要添加的曲目</p>
      </div>
      <template #footer>
        <el-button @click="addDialog.visible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!addDialog.trackId"
          :loading="addDialog.adding"
          @click="confirmAdd"
        >
          添加
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head-main {
  display: flex;
  align-items: center;
  gap: 6px;
}

.playlist-comment {
  margin-top: -12px;
}

.table-card {
  padding: 8px 14px 14px;
}

.tt-title {
  font-weight: 500;
}

.dim {
  color: var(--text-dim);
}

.add-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.candidate-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 320px;
  overflow-y: auto;
}

.candidate {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid transparent;
  cursor: pointer;
  transition:
    border-color 0.13s ease,
    background-color 0.13s ease;
}

.candidate:hover {
  background: var(--panel-raised);
}

.candidate.picked {
  border-color: var(--spectrum-5);
  background: rgba(123, 108, 255, 0.08);
}

.candidate-radio {
  accent-color: var(--spectrum-5);
}

.candidate-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13.5px;
}

.candidate-sub {
  color: var(--text-dim);
  font-size: 12.5px;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.candidate-dur {
  color: var(--text-dim);
  font-size: 12px;
}
</style>
