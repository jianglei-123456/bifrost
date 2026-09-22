<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

import {
  createMusicRoot,
  deleteMusicRoot,
  fetchMusicRoots,
  updateMusicRoot,
} from '@/api/musicRoots'
import type { MusicRoot } from '@/api/types'
import ScanStatusDot from '@/components/ScanStatusDot.vue'
import { useMusicScanStore } from '@/stores/musicScan'
import { formatDateTime, parseScanStats } from '@/utils/format'

/**
 * 音乐库：音乐目录管理与扫描合一页（原「库根管理」+「扫描管理」合并）。
 * 列表取 /api/music-roots（仅 MUSIC、含停用）；音乐扫描是同步接口，
 * 全局/行内扫描按钮以 loading 呈现，完成后重载列表与全局状态。
 */
const musicScan = useMusicScanStore()

const roots = ref<MusicRoot[]>([])
const loading = ref(false)
const scanningAll = ref(false)
const scanningId = ref<number | null>(null)
/** 正在切换启停的目录 id（防止快速双击发出两次相同 PATCH） */
const togglingId = ref<number | null>(null)

const dialog = reactive({
  visible: false,
  editing: false,
  saving: false,
  id: 0,
})
const formRef = ref<FormInstance>()
const form = reactive({ name: '', path: '', enabled: true })

const rules: FormRules = {
  name: [{ required: true, message: '请输入音乐目录名称', trigger: 'blur' }],
  path: [{ required: true, message: '请输入目录绝对路径', trigger: 'blur' }],
}

/** 任一扫描进行中（含 Subsonic 等外部触发）→ 禁用全部扫描入口 */
const busy = computed(() => scanningAll.value || scanningId.value !== null || musicScan.scanning)

let loadSeq = 0

/** 载入音乐目录列表；带序号护栏，避免轮询与手动刷新并发时旧响应覆盖新数据 */
async function load() {
  const seq = ++loadSeq
  loading.value = true
  try {
    const items = await fetchMusicRoots()
    if (seq === loadSeq) roots.value = items
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

onMounted(load)

// scanning 来自 /api/music-roots/scan/status：本页发起的扫描与外部触发（Subsonic startScan）
// 都会让它置真/置假；任一方向变化都重载列表，使行内「扫描中」与顶栏药丸保持一致
watch(
  () => musicScan.scanning,
  () => {
    void load()
  },
)

function openCreate() {
  dialog.visible = true
  dialog.editing = false
  dialog.id = 0
  form.name = ''
  form.path = ''
  form.enabled = true
}

function openEdit(row: unknown) {
  const root = row as MusicRoot
  dialog.visible = true
  dialog.editing = true
  dialog.id = root.id
  form.name = root.name
  form.path = root.path
  form.enabled = root.enabled
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  dialog.saving = true
  try {
    if (dialog.editing) {
      await updateMusicRoot(dialog.id, { ...form })
      ElMessage.success('音乐目录已更新')
    } else {
      await createMusicRoot({ ...form })
      ElMessage.success('音乐目录已添加')
    }
    dialog.visible = false
    await load()
  } finally {
    dialog.saving = false
  }
}

async function toggleEnabled(row: unknown, enabled: unknown) {
  const root = row as MusicRoot
  if (togglingId.value !== null) return
  const enable = Boolean(enabled)
  togglingId.value = root.id
  try {
    await updateMusicRoot(root.id, { enabled: enable })
    ElMessage.success(enable ? '已启用' : '已停用（曲目将隐藏）')
  } catch {
    // 请求拦截器已弹错；失败时行内开关不刷新 = 保持原状
  } finally {
    togglingId.value = null
    await load()
  }
}

async function remove(row: unknown) {
  const root = row as MusicRoot
  try {
    await ElMessageBox.confirm(
      `删除音乐目录「${root.name}」后，其曲目将标记为缺失并隐藏（记录保留）。确定删除？`,
      '删除音乐目录',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return // 用户取消
  }
  await deleteMusicRoot(root.id)
  ElMessage.success('音乐目录已删除')
  await load()
}

/** 单目录扫描（同步：请求返回时统计已完成）；走 store action，完成后 store 已同步刷新 */
async function scanOne(row: unknown) {
  const root = row as MusicRoot
  scanningId.value = root.id
  try {
    const stats = await musicScan.startRoot(root.id)
    ElMessage.success(
      `「${root.name}」扫描完成：新增 ${stats.added} · 更新 ${stats.updated} · 缺失 ${stats.missing} · 错误 ${stats.error}`,
    )
  } catch {
    // 拦截器已弹错（如 1100 扫描进行中）
  } finally {
    scanningId.value = null
    await load()
    await musicScan.refresh().catch(() => {})
  }
}

/** 全部音乐目录扫描（同步，串行；仅 MUSIC，不触碰图书目录） */
async function scanAll() {
  scanningAll.value = true
  try {
    const stats = await musicScan.startFull()
    ElMessage.success(
      `扫描完成：新增 ${stats.added} · 更新 ${stats.updated} · 缺失 ${stats.missing} · 错误 ${stats.error}`,
    )
  } catch {
    // 拦截器已弹错
  } finally {
    scanningAll.value = false
    await load()
    await musicScan.refresh().catch(() => {})
  }
}

function statsOf(row: unknown) {
  return parseScanStats((row as MusicRoot).lastScanStats)
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">音乐库</h2>
        <p class="page-sub">
          挂载进音乐库的顶层目录（音乐目录）；停用的目录不参与扫描，其曲目对客户端隐藏
        </p>
      </div>
      <div class="page-actions">
        <span v-if="busy" class="scan-hint">扫描进行中（同步执行，请勿关闭页面）</span>
        <el-button :loading="scanningAll" :disabled="busy" @click="scanAll">扫描全部</el-button>
        <el-button type="primary" @click="openCreate">添加音乐目录</el-button>
      </div>
    </div>

    <div class="card table-card">
      <el-table v-loading="loading" :data="roots">
        <el-table-column label="名称" min-width="140">
          <template #default="{ row }">
            <span class="root-name">{{ row.name }}</span>
          </template>
        </el-table-column>

        <el-table-column label="路径" min-width="240">
          <template #default="{ row }">
            <span class="data-mono path">{{ row.path }}</span>
          </template>
        </el-table-column>

        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              :loading="togglingId === row.id"
              @change="(val) => toggleEnabled(row, val)"
            />
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="status">
              <ScanStatusDot :scanning="row.scanStatus === 'SCANNING'" />
              {{ row.scanStatus === 'SCANNING' ? '扫描中' : '空闲' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="最近扫描" width="150">
          <template #default="{ row }">
            <span class="data-mono dim">{{ formatDateTime(row.lastScanAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="上次统计" min-width="200">
          <template #default="{ row }">
            <template v-if="statsOf(row)">
              <span class="chip add">+{{ statsOf(row)!.added }}</span>
              <span class="chip upd">~{{ statsOf(row)!.updated }}</span>
              <span class="chip miss">-{{ statsOf(row)!.missing }}</span>
              <span class="chip err">!{{ statsOf(row)!.error }}</span>
            </template>
            <span v-else class="dim">—</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="190" align="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="scanningId === row.id"
              :disabled="busy && scanningId !== row.id"
              @click="scanOne(row)"
            >
              扫描
            </el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialog.visible"
      :title="dialog.editing ? '编辑音乐目录' : '添加音乐目录'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：我的音乐" />
        </el-form-item>
        <el-form-item label="目录绝对路径" prop="path">
          <el-input v-model="form.path" placeholder="如：E:/Music" class="data-mono" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.table-card {
  padding: 6px 14px 14px;
}

.scan-hint {
  font-size: 12px;
  color: var(--warn);
}

.root-name {
  font-weight: 600;
}

.path {
  font-size: 12.5px;
  color: var(--text-dim);
}

.dim {
  color: var(--text-dim);
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--text-dim);
}

.chip {
  display: inline-block;
  font-family: var(--font-mono);
  font-size: 11.5px;
  border-radius: 5px;
  padding: 0 6px;
  margin-right: 6px;
  line-height: 18px;
}

.chip.add {
  color: var(--success);
  background: rgba(61, 220, 151, 0.12);
}
.chip.upd {
  color: var(--info);
  background: rgba(76, 201, 240, 0.12);
}
.chip.miss {
  color: var(--warn);
  background: rgba(255, 180, 84, 0.12);
}
.chip.err {
  color: var(--danger);
  background: rgba(255, 93, 115, 0.12);
}
</style>
