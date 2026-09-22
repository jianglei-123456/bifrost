<script setup lang="ts">
/**
 * 孤儿进度（M3-sync）：上报了进度、但在库里找不到对应文件的记录
 * （侧载文件、重打包、格式转换后的版本等）。
 *
 * 规则（后端 R5/C1）：未匹配的新指纹会触发**一次**自动扫描；扫描后仍未命中即固定为孤儿，
 * 此后只能人工处理——绑定 / 重新匹配 / 忽略。
 * 「重新匹配」未命中是**正常结果**（"这本书确实还不在库里"），提示而不是报错。
 */
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { fetchBooks } from '@/api/books'
import { useBookSyncStore } from '@/stores/bookSync'
import type { Book, OrphanProgress } from '@/api/types'
import { copyText } from '@/utils/clipboard'
import { formatDateTime } from '@/utils/format'

const store = useBookSyncStore()
const loading = ref(false)

const bindDialog = reactive({ visible: false, saving: false, orphanId: 0, bookId: null as number | null })
const bookOptions = ref<Book[]>([])
const bookLoading = ref(false)

async function load() {
  loading.value = true
  try {
    await store.loadOrphans()
  } finally {
    loading.value = false
  }
}

function changePage(page: number) {
  store.orphanPage = page - 1 // el-pagination 1-based，接口 0-based
  load()
}

async function searchBooks(keyword: string) {
  if (!keyword) {
    bookOptions.value = []
    return
  }
  bookLoading.value = true
  try {
    bookOptions.value = (await fetchBooks({ title: keyword, size: 20 })).items
  } finally {
    bookLoading.value = false
  }
}

async function openBind(row: unknown) {
  const item = row as OrphanProgress
  bindDialog.visible = true
  bindDialog.orphanId = item.id
  // 后端 null 字段会被省略，落到这里统一成 null
  bindDialog.bookId = item.suggestedBookId ?? null
  bookOptions.value = []
  if (item.suggestedBookTitle) {
    // 建议是猜的（按文件名），把候选拉出来让用户确认
    await searchBooks(item.suggestedBookTitle)
  }
}

async function confirmBind() {
  const bookId = bindDialog.bookId
  if (!bookId) {
    ElMessage.warning('请选择要绑定的图书')
    return
  }
  bindDialog.saving = true
  try {
    await store.bind(bindDialog.orphanId, bookId)
    ElMessage.success('已绑定到所选图书')
    bindDialog.visible = false
  } finally {
    bindDialog.saving = false
  }
}

async function rematch(row: unknown) {
  const item = row as OrphanProgress
  const matched = await store.rematch(item.id)
  if (matched) {
    ElMessage.success('已匹配到库里的图书并绑定')
  } else {
    ElMessage.info('仍未匹配到图书——可先把书放进图书目录并扫描，再点「重新匹配」')
  }
}

async function toggleIgnored(row: unknown) {
  const item = row as OrphanProgress
  await store.setIgnored(item.id, !item.ignored)
  ElMessage.success(item.ignored ? '已取消忽略' : '已忽略（默认列表里不再显示）')
}

async function copyFingerprint(value: string) {
  const ok = await copyText(value)
  if (ok) ElMessage.success('文档指纹已复制')
  else ElMessage.warning('复制失败，请手动选中复制')
}
</script>

<template>
  <div>
    <div class="filters">
      <el-switch v-model="store.includeIgnored" active-text="显示已忽略" @change="load" />
      <span class="hint">
        孤儿 = 设备上报的文档指纹在库里找不到对应文件；绑定/重新匹配/忽略都只影响服务端记录
      </span>
    </div>

    <el-table v-loading="loading" :data="store.orphans" empty-text="没有孤儿进度（所有上报都对应到了库里的书）">
      <el-table-column label="文档指纹" min-width="200">
        <template #default="{ row }">
          <span class="data-mono fp">{{ row.documentFingerprint.slice(0, 16) }}…</span>
          <el-button link type="primary" @click="copyFingerprint(row.documentFingerprint)">复制</el-button>
        </template>
      </el-table-column>

      <el-table-column label="进度" width="90">
        <template #default="{ row }">
          <span class="data-mono">{{ (row.percentage * 100).toFixed(1) }}%</span>
        </template>
      </el-table-column>

      <el-table-column label="设备" min-width="140">
        <template #default="{ row }">{{ row.device }}</template>
      </el-table-column>

      <el-table-column label="首次出现" width="150">
        <template #default="{ row }">
          <span class="data-mono dim">{{ formatDateTime(row.createdAt) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="结算状态" width="140" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.scanAttemptedAt" size="small" type="info">未匹配到图书</el-tag>
          <el-tag v-else size="small" type="warning">等待自动扫描结算</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="建议绑定" min-width="160">
        <template #default="{ row }">
          <span v-if="row.suggestedBookId">
            {{ row.suggestedBookTitle }}
            <el-tag size="small" type="info" class="tag-gap">
              {{ row.suggestionReason === 'FILENAME' ? '同名文件' : '下载文件名' }}
            </el-tag>
          </span>
          <span v-else class="dim">—</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="210" align="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openBind(row)">绑定</el-button>
          <el-button link type="primary" @click="rematch(row)">重新匹配</el-button>
          <el-button link :type="row.ignored ? 'info' : 'danger'" @click="toggleIgnored(row)">
            {{ row.ignored ? '取消忽略' : '忽略' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="store.orphansTotal"
        :page-size="store.orphanSize"
        :current-page="store.orphanPage + 1"
        @current-change="changePage"
      />
    </div>

    <el-dialog v-model="bindDialog.visible" title="绑定到图书" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="选择图书（输入书名搜索）">
          <el-select
            v-model="bindDialog.bookId"
            filterable
            remote
            :remote-method="searchBooks"
            :loading="bookLoading"
            placeholder="输入书名搜索，如：Pride"
            class="book-select"
          >
            <el-option v-for="b in bookOptions" :key="b.id" :label="b.title" :value="b.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="bindDialog.saving" @click="confirmBind">绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 4px 0 12px;
  flex-wrap: wrap;
}

.hint {
  font-size: 12.5px;
  color: var(--text-dim);
}

.fp {
  font-size: 12px;
}

.dim {
  color: var(--text-dim);
}

.tag-gap {
  margin-left: 4px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

.book-select {
  width: 100%;
}
</style>
