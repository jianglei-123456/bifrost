<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

import ScanStatusDot from '@/components/ScanStatusDot.vue'
import { useBookStore } from '@/stores/book'
import type { BookRoot } from '@/api/types'
import { formatDateTime } from '@/utils/format'

const book = useBookStore()

const loading = ref(false)

const dialog = reactive({
  visible: false,
  editing: false,
  saving: false,
  id: 0,
})
const formRef = ref<FormInstance>()
const form = reactive({ name: '', path: '', enabled: true })

const rules: FormRules = {
  name: [{ required: true, message: '请输入图书目录名称', trigger: 'blur' }],
  path: [{ required: true, message: '请输入目录绝对路径', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    await book.loadRoots()
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  dialog.visible = true
  dialog.editing = false
  dialog.id = 0
  form.name = ''
  form.path = ''
  form.enabled = true
}

function openEdit(row: unknown) {
  const root = row as BookRoot
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
      await book.updateRoot(dialog.id, { ...form })
      ElMessage.success('图书目录已更新')
    } else {
      await book.createRoot({ ...form })
      ElMessage.success('图书目录已添加')
    }
    dialog.visible = false
  } finally {
    dialog.saving = false
  }
}

async function toggleEnabled(row: unknown, enabled: unknown) {
  const root = row as BookRoot
  const enable = Boolean(enabled)
  try {
    await book.updateRoot(root.id, { enabled: enable })
    ElMessage.success(enable ? '已启用' : '已停用（图书将隐藏）')
  } catch {
    // 请求拦截器已弹错；失败时行内开关不刷新 = 保持原状
  }
}

async function remove(row: unknown) {
  const root = row as BookRoot
  try {
    await ElMessageBox.confirm(
      `删除图书目录「${root.name}」后，其图书将标记为不可用并隐藏（记录保留）。确定删除？`,
      '删除图书目录',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return // 用户取消
  }
  await book.deleteRoot(root.id)
  ElMessage.success('图书目录已删除')
}

/** 触发单目录异步扫描（立即返回；进度见顶部 BookScanStatusBar） */
async function scanRoot(row: unknown) {
  const root = row as BookRoot
  try {
    const view = await book.scanRoot(root.id)
    ElMessage.success(view.message || '扫描已启动')
  } catch {
    // 拦截器已弹错（如 1100 扫描进行中）
  }
}

/** 触发全部图书目录扫描 */
async function scanAll() {
  try {
    const view = await book.scanAll()
    ElMessage.success(view.message || '已启动全部图书目录扫描')
  } catch {
    // noop
  }
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">图书库</h2>
        <p class="page-sub">挂载进图书库的顶层目录（图书目录）；停用的目录不参与扫描，书籍会标记隐藏</p>
      </div>
      <div class="page-actions">
        <el-button :disabled="book.busy" @click="scanAll">扫描全部</el-button>
        <el-button type="primary" @click="openCreate">添加图书目录</el-button>
      </div>
    </div>

    <div class="card table-card">
      <el-table v-loading="loading" :data="book.roots">
        <el-table-column label="名称" min-width="160">
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
              @change="(val) => toggleEnabled(row, val)"
            />
          </template>
        </el-table-column>

        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <span class="type-chip data-mono">{{ row.mediaType }}</span>
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

        <el-table-column label="操作" width="190" align="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="book.busy" @click="scanRoot(row)">
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
      :title="dialog.editing ? '编辑图书目录' : '添加图书目录'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：我的图书库" />
        </el-form-item>
        <el-form-item label="目录绝对路径" prop="path">
          <el-input v-model="form.path" placeholder="如：D:/Books" class="data-mono" />
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

.type-chip {
  font-size: 11px;
  color: var(--info);
  background: rgba(76, 201, 240, 0.1);
  border: 1px solid rgba(76, 201, 240, 0.3);
  border-radius: 999px;
  padding: 0 8px;
  line-height: 18px;
}
</style>
