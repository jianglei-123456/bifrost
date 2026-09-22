<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

import { createPlaylist, deletePlaylist, fetchPlaylist, fetchPlaylists } from '@/api/playlists'
import type { Playlist } from '@/api/types'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

const playlists = ref<Playlist[]>([])
const loading = ref(false)
const counts = ref<Record<number, number>>({})

const dialog = reactive({ visible: false, saving: false })
const formRef = ref<FormInstance>()
const form = reactive({ name: '', comment: '' })

const rules: FormRules = {
  name: [{ required: true, message: '请输入歌单名称', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    playlists.value = await fetchPlaylists()
    // 逐单取条目数（v1 歌单数量少，可接受）
    counts.value = {}
    await Promise.all(
      playlists.value.map(async (p) => {
        try {
          const detail = await fetchPlaylist(p.id)
          counts.value[p.id] = detail.entries.length
        } catch {
          counts.value[p.id] = 0
        }
      }),
    )
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  dialog.saving = true
  try {
    await createPlaylist({ ...form })
    ElMessage.success('歌单已创建')
    dialog.visible = false
    form.name = ''
    form.comment = ''
    await load()
  } finally {
    dialog.saving = false
  }
}

async function remove(p: Playlist) {
  await ElMessageBox.confirm(`删除歌单「${p.name}」？此操作不可恢复。`, '删除歌单', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await deletePlaylist(p.id)
  ElMessage.success('歌单已删除')
  await load()
}

function open(p: Playlist) {
  router.push({ name: 'playlist-detail', params: { id: p.id } })
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">歌单</h2>
        <p class="page-sub">按自己方式组织的曲目集合</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" :icon="Plus" @click="dialog.visible = true">新建歌单</el-button>
      </div>
    </div>

    <div v-loading="loading" class="playlist-grid">
      <p v-if="!loading && !playlists.length" class="empty-hint">
        还没有歌单 —— 新建一个，把喜欢的曲目收进来
      </p>
      <div
        v-for="p in playlists"
        :key="p.id"
        class="playlist-card card card-hover"
        role="button"
        tabindex="0"
        @click="open(p)"
        @keyup.enter="open(p)"
      >
        <div class="playlist-head">
          <span class="playlist-name">{{ p.name }}</span>
          <span class="playlist-count data-mono">{{ counts[p.id] ?? 0 }}</span>
        </div>
        <p v-if="p.comment" class="playlist-comment">{{ p.comment }}</p>
        <div class="playlist-foot">
          <span class="dim">{{ formatDateTime(p.updatedAt) }}</span>
          <span class="playlist-actions" @click.stop>
            <el-button link type="danger" @click="remove(p)">删除</el-button>
          </span>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialog.visible" title="新建歌单" width="440px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：通勤路上" />
        </el-form-item>
        <el-form-item label="备注（可选）">
          <el-input v-model="form.comment" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.playlist-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}

.playlist-card {
  padding: 16px 18px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.playlist-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.playlist-name {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.playlist-count {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-dim);
  flex-shrink: 0;
}

.playlist-comment {
  margin: 0;
  font-size: 12.5px;
  color: var(--text-dim);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.playlist-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}

.dim {
  color: #5d6a82;
}
</style>
