<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import BookCover from '@/components/BookCover.vue'
import BookEditDialog from '@/components/BookEditDialog.vue'
import { useBookStore } from '@/stores/book'
import type { Book } from '@/api/types'
import { formatCount } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const book = useBookStore()

const loading = ref(false)
const navigating = ref(false)

/** 过滤输入草稿（回车/点筛选才应用，避免逐键触发请求） */
const draft = reactive({
  title: '',
  author: '',
  series: '',
  libraryRootId: null as number | null,
  isAvailable: null as boolean | null,
})

const edit = reactive({ visible: false, target: null as Book | null })

function clampPage(raw: number): number {
  return Number.isFinite(raw) && raw >= 0 ? Math.floor(raw) : 0
}

function clampSize(raw: number): number {
  if (!Number.isFinite(raw)) return 20
  return Math.min(200, Math.max(1, Math.floor(raw)))
}

function qstr(v: unknown): string {
  return typeof v === 'string' ? v : ''
}

function syncFromQuery() {
  const q = route.query
  const libraryRootId = qstr(q.libraryRootId)
  const avail = qstr(q.isAvailable)
  const parsedRoot = Number(libraryRootId)
  book.filter = {
    title: qstr(q.title),
    author: qstr(q.author),
    series: qstr(q.series),
    libraryRootId: libraryRootId && Number.isFinite(parsedRoot) ? parsedRoot : null,
    isAvailable: avail === 'true' ? true : avail === 'false' ? false : null,
  }
  book.page = clampPage(Number(qstr(q.page)))
  book.size = clampSize(Number(qstr(q.size)) || 20)
  Object.assign(draft, book.filter)
}

function buildQuery(): Record<string, string> {
  const query: Record<string, string> = {}
  if (book.filter.title) query.title = book.filter.title
  if (book.filter.author) query.author = book.filter.author
  if (book.filter.series) query.series = book.filter.series
  if (book.filter.libraryRootId != null) query.libraryRootId = String(book.filter.libraryRootId)
  if (book.filter.isAvailable != null) query.isAvailable = book.filter.isAvailable ? 'true' : 'false'
  if (book.page > 0) query.page = String(book.page)
  if (book.size !== 20) query.size = String(book.size)
  return query
}

async function load() {
  loading.value = true
  try {
    await book.loadBooks()
  } finally {
    loading.value = false
  }
}

/** 已把 filter/page/size 写入 store 后：同步 URL + 拉数据 */
async function navigateAndLoad() {
  navigating.value = true
  try {
    await router.replace({ query: buildQuery() })
  } finally {
    navigating.value = false
  }
  await load()
}

function applyFilter() {
  book.filter = { ...draft }
  book.page = 0
  void navigateAndLoad()
}

function resetFilter() {
  draft.title = ''
  draft.author = ''
  draft.series = ''
  draft.libraryRootId = null
  draft.isAvailable = null
  applyFilter()
}

function goPage(p: number) {
  // el-pagination 1-based → store 0-based
  book.page = Math.max(0, p - 1)
  void navigateAndLoad()
}

function changeSize(size: number) {
  book.size = clampSize(size)
  book.page = 0
  void navigateAndLoad()
}

async function remove(row: unknown) {
  const item = row as Book
  await ElMessageBox.confirm(
    `删除图书「${item.title}」？仅移除数据库记录，不会删除文件。`,
    '删除图书',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
  )
  await book.deleteBook(item.id)
  ElMessage.success('图书已删除')
  await load()
}

function openEdit(row: unknown) {
  edit.target = row as Book
  edit.visible = true
}

function onSaved() {
  void load()
}

// 外部导航（前进/后退/直链）：URL 是过滤的单一事实源
watch(
  () => route.query,
  () => {
    if (navigating.value) return
    syncFromQuery()
    void load()
  },
)

// 扫描完成（SCANNING→IDLE）后自动刷新列表（新书入库）
watch(
  () => book.completionTick,
  () => {
    if (route.name === 'books') void load()
  },
)

onMounted(() => {
  syncFromQuery()
  if (!book.roots.length) void book.loadRoots().catch(() => {})
  void load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">图书</h2>
        <p class="page-sub">
          共 {{ formatCount(book.total) }} 本
          <template v-if="Object.values(book.filter).some((v) => v !== '' && v != null)">
            · 已按条件过滤
          </template>
        </p>
      </div>
    </div>

    <div class="card filter-card">
      <el-input
        v-model="draft.title"
        clearable
        placeholder="按书名筛选"
        class="f-item f-flex"
        @keyup.enter="applyFilter"
        @clear="applyFilter"
      />
      <el-input
        v-model="draft.author"
        clearable
        placeholder="按作者筛选"
        class="f-item f-flex"
        @keyup.enter="applyFilter"
        @clear="applyFilter"
      />
      <el-input
        v-model="draft.series"
        clearable
        placeholder="按系列筛选"
        class="f-item f-flex"
        @keyup.enter="applyFilter"
        @clear="applyFilter"
      />
      <el-select
        v-model="draft.libraryRootId"
        clearable
        placeholder="全部图书目录"
        class="f-item f-root"
        @change="applyFilter"
      >
        <el-option v-for="r in book.roots" :key="r.id" :label="r.name" :value="r.id" />
      </el-select>
      <el-select
        :model-value="draft.isAvailable"
        clearable
        placeholder="可用状态"
        class="f-item f-avail"
        @update:model-value="(v) => (draft.isAvailable = v === true || v === false ? v : null)"
        @change="applyFilter"
      >
        <el-option label="可用" :value="true" />
        <el-option label="不可用" :value="false" />
      </el-select>
      <el-button type="primary" @click="applyFilter">筛选</el-button>
      <el-button @click="resetFilter">重置</el-button>
    </div>

    <div class="card table-card">
      <el-table v-loading="loading" :data="book.books" class="book-table">
        <el-table-column label="封面" width="76">
          <template #default="{ row }">
            <BookCover
              :cover-url="row.coverUrl"
              :title="row.title"
              :size="64"
              :width="42"
              :height="58"
              class="thumb"
            />
          </template>
        </el-table-column>

        <el-table-column label="标题" min-width="240">
          <template #default="{ row }">
            <router-link
              :to="{ name: 'book-detail', params: { id: row.id } }"
              class="title-link"
            >
              {{ row.title }}
            </router-link>
          </template>
        </el-table-column>

        <el-table-column label="作者" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ dim: !row.authors }">{{ row.authors || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="可用性" width="100" align="center">
          <template #default="{ row }">
            <span class="avail" :class="row.isAvailable ? 'on' : 'off'">
              {{ row.isAvailable ? '可用' : '不可用' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" align="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="router.push({ name: 'book-detail', params: { id: row.id } })"
            >
              详情
            </el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="book.total > book.size" class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="book.total"
          :current-page="book.page + 1"
          :page-size="book.size"
          :page-sizes="[20, 50, 100, 200]"
          @current-change="goPage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <BookEditDialog v-model="edit.visible" :book="edit.target" @saved="onSaved" />
  </div>
</template>

<style scoped>
.filter-card {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 14px;
  margin-bottom: 14px;
}

.f-item {
  --el-input-width: auto;
}

.f-flex {
  width: 170px;
}

.f-root {
  width: 150px;
}

.f-avail {
  width: 120px;
}

.table-card {
  padding: 6px 14px 14px;
}

.thumb {
  vertical-align: middle;
}

.title-link {
  color: var(--text);
  text-decoration: none;
  font-weight: 600;
}

.title-link:hover {
  color: var(--info);
  text-decoration: underline;
}

.dim {
  color: var(--text-dim);
}

.avail {
  font-size: 12px;
  border-radius: 999px;
  padding: 0 10px;
  line-height: 19px;
  display: inline-block;
}

.avail.on {
  color: var(--success);
  background: rgba(61, 220, 151, 0.12);
  border: 1px solid rgba(61, 220, 151, 0.3);
}

.avail.off {
  color: var(--text-dim);
  background: var(--panel-raised);
  border: 1px solid var(--line);
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}
</style>
