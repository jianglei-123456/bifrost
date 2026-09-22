<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'

import {
  addPlaylistEntries,
  deletePlaylist,
  fetchCandidateTracks,
  fetchPlaylist,
  removePlaylistEntry,
  updatePlaylist,
} from '@/api/playlists'
import type { PlaylistEntryView, Track } from '@/api/types'
import CoverArt from '@/components/CoverArt.vue'
import RatingStars from '@/components/RatingStars.vue'
import StarButton from '@/components/StarButton.vue'
import { formatDateTime, formatDuration } from '@/utils/format'

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
// 候选列表 = 服务端分页的「可添加曲目」（后端已排除已在歌单的、文件缺失的）。
// 已选列表 = 弹窗内的待提交暂存区，非持久化，与「歌单条目」不是一回事。
const ADD_PAGE_SIZE = 10
const addDialog = reactive({ visible: false, adding: false })
const candidates = ref<Track[]>([])
const candidateTotal = ref(0)
const addQuery = ref('')
const addPage = ref(0)
const searchLoading = ref(false)
const selected = ref<Track[]>([])

const selectedIds = computed(() => new Set(selected.value.map((t) => t.id)))

/** 加载候选曲目：默认列表（无关键词）与搜索是同一个端点换参数，排序一致。 */
async function loadCandidates() {
  searchLoading.value = true
  try {
    const res = await fetchCandidateTracks(id, {
      page: addPage.value,
      size: ADD_PAGE_SIZE,
      keyword: addQuery.value,
    })
    candidates.value = res.items
    candidateTotal.value = res.total
  } finally {
    searchLoading.value = false
  }
}

/** 关键词一变就回第 1 页，否则会出现「搜索后停在空白第 3 页」。 */
watch(addQuery, () => {
  addPage.value = 0
  void loadCandidates()
})

/** 打开弹窗：每次都是干净默认态（无关键词、第 1 页、已选清空）。 */
function openAdd() {
  addQuery.value = ''
  addPage.value = 0
  selected.value = []
  candidates.value = []
  candidateTotal.value = 0
  addDialog.visible = true
  void loadCandidates()
}

function changeAddPage(page: number) {
  addPage.value = page - 1
  void loadCandidates()
}

/** 整行可点：未选的移入已选列表，已选的移回（即从已选列表移除）。 */
function toggleCandidate(track: Track) {
  const idx = selected.value.findIndex((t) => t.id === track.id)
  if (idx >= 0) {
    selected.value.splice(idx, 1)
  } else {
    selected.value.push(track)
  }
}

function dropSelected(trackId: number) {
  selected.value = selected.value.filter((t) => t.id !== trackId)
}

function clearSelected() {
  selected.value = []
}

async function confirmAdd() {
  if (!selected.value.length) return
  const ids = selected.value.map((t) => t.id)
  addDialog.adding = true
  try {
    const added = await addPlaylistEntries(id, ids)
    ElMessage.success(`已添加 ${added} 首`)
    // 弹窗保持打开以便连续加：清空已选、回第 1 页并重新拉取候选列表
    // （刚加的曲目因服务端排除而从列表消失，使去重效果可见）。这里显式调用，
    // 不能只依赖 addQuery 的 watch——关键词本来就是空时 watch 不会触发。
    selected.value = []
    addQuery.value = ''
    addPage.value = 0
    await loadCandidates()
    await load()
  } finally {
    addDialog.adding = false
  }
}

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

async function removeEntry(entry: PlaylistEntryView) {
  await removePlaylistEntry(id, entry.entryId)
  ElMessage.success('已移除')
  await load()
}

const trackRows = computed(() => entries.value.map((e) => e.track).filter((t): t is Track => t !== null))
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
            <el-button :icon="Plus" @click="openAdd">添加曲目</el-button>
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
            <el-table-column label="入库时间" width="150">
              <template #default="{ row }">
                <span class="data-mono dim">{{ formatDateTime(row.createdAt) }}</span>
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

    <el-dialog v-model="addDialog.visible" title="添加曲目" width="720px">
      <div class="add-bar">
        <el-input
          v-model="addQuery"
          placeholder="搜索歌曲标题或艺术家（留空即按入库时间倒序浏览全部）"
          clearable
        />
      </div>

      <div v-loading="searchLoading" class="candidate-list">
        <div
          v-for="track in candidates"
          :key="track.id"
          class="candidate"
          :class="{ picked: selectedIds.has(track.id) }"
          @click="toggleCandidate(track)"
        >
          <CoverArt :album-id="track.albumId" :size="34" :title="track.title" />
          <span class="candidate-title">{{ track.title }}</span>
          <span class="candidate-sub">{{ track.artistName || '未知艺术家' }}</span>
          <span class="candidate-dur data-mono">{{ formatDuration(track.duration) }}</span>
          <span class="candidate-added">{{ formatDateTime(track.createdAt) }}</span>
          <span v-if="selectedIds.has(track.id)" class="candidate-badge">已选</span>
        </div>
        <p v-if="!searchLoading && !candidates.length" class="empty-hint">
          {{ addQuery.trim() ? '没有匹配的歌曲' : '这个歌单已经把库里的歌都收进来了' }}
        </p>
      </div>

      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="candidateTotal"
          :current-page="addPage + 1"
          :page-size="ADD_PAGE_SIZE"
          @current-change="changeAddPage"
        />
      </div>

      <div v-if="selected.length" class="picked-box">
        <div class="picked-head">
          <span>已选 {{ selected.length }} 首</span>
          <el-button link type="primary" @click="clearSelected">清空</el-button>
        </div>
        <div class="picked-list">
          <span v-for="track in selected" :key="track.id" class="picked-chip">
            <span class="picked-chip-title">{{ track.title }}</span>
            <button class="picked-chip-x" title="移回候选列表" @click="dropSelected(track.id)">
              ×
            </button>
          </span>
        </div>
      </div>

      <template #footer>
        <el-button @click="addDialog.visible = false">关闭</el-button>
        <el-button
          type="primary"
          :disabled="!selected.length"
          :loading="addDialog.adding"
          @click="confirmAdd"
        >
          {{ selected.length ? `添加 ${selected.length} 首` : '添加' }}
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
  min-height: 200px;
  max-height: 340px;
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
    background-color 0.13s ease,
    opacity 0.13s ease;
}

.candidate:hover {
  background: var(--panel-raised);
}

/* 已选曲目留在原位、置灰：列表长度不变，翻页才不会漏曲目 */
.candidate.picked {
  border-color: var(--spectrum-5);
  background: rgba(123, 108, 255, 0.08);
  opacity: 0.55;
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

.candidate-added {
  color: var(--text-dim);
  font-size: 11.5px;
  white-space: nowrap;
}

.candidate-badge {
  flex: none;
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 6px;
  color: var(--spectrum-5);
  border: 1px solid var(--spectrum-5);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 10px;
}

.picked-box {
  margin-top: 12px;
  border-top: 1px solid var(--border, rgba(127, 127, 127, 0.2));
  padding-top: 10px;
}

.picked-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 8px;
}

.picked-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 120px;
  overflow-y: auto;
}

.picked-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 220px;
  padding: 3px 4px 3px 10px;
  border-radius: 999px;
  background: var(--panel-raised);
  font-size: 12.5px;
}

.picked-chip-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picked-chip-x {
  flex: none;
  border: none;
  background: transparent;
  color: var(--text-dim);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0 4px;
}

.picked-chip-x:hover {
  color: var(--spectrum-5);
}
</style>
