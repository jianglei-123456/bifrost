<script setup lang="ts">
/**
 * 进度列表（M3-sync）：哪本书读到多少、来自哪台设备、什么时候。
 *
 * 「重置」只删服务端记录——设备本地位置不变、下次推送会重建记录，
 * 确认文案必须写明这一点，否则用户会以为重置能让设备回到开头。
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import BookCover from '@/components/BookCover.vue'
import { useBookSyncStore } from '@/stores/bookSync'
import type { ReadingProgress } from '@/api/types'
import { formatDateTime } from '@/utils/format'

const store = useBookSyncStore()
const loading = ref(false)

/** 首屏由 BookSyncView.loadAll 统一加载；这里只服务于查询/翻页/重置后的刷新 */
async function load() {
  loading.value = true
  try {
    await store.loadProgress()
  } finally {
    loading.value = false
  }
}

function search() {
  store.page = 0
  load()
}

function resetFilter() {
  store.filter.title = ''
  store.filter.device = ''
  store.filter.onlyOrphans = false
  search()
}

function changePage(page: number) {
  store.page = page - 1 // el-pagination 是 1-based，接口是 0-based
  load()
}

async function reset(row: unknown) {
  const item = row as ReadingProgress
  try {
    await ElMessageBox.confirm(
      `只删除服务器上的这条阅读进度记录。设备本地位置不会改变，设备下次推送会重新建立记录。\n\n确定重置「${item.bookTitle ?? item.documentFingerprint}」的进度？`,
      '重置阅读进度',
      { confirmButtonText: '重置', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return // 用户取消
  }
  await store.removeProgress(item.id)
  ElMessage.success('进度记录已删除')
}

function percentText(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}
</script>

<template>
  <div>
    <div class="filters">
      <el-input
        v-model="store.filter.title"
        placeholder="按书名过滤"
        clearable
        class="filter-input"
        @keyup.enter="search"
        @clear="search"
      />
      <el-input
        v-model="store.filter.device"
        placeholder="按设备名过滤"
        clearable
        class="filter-input"
        @keyup.enter="search"
        @clear="search"
      />
      <el-checkbox v-model="store.filter.onlyOrphans" @change="search">仅看孤儿</el-checkbox>
      <el-button @click="search">查询</el-button>
      <el-button text @click="resetFilter">重置条件</el-button>
    </div>

    <el-table v-loading="loading" :data="store.progress" empty-text="还没有任何设备上报过进度">
      <el-table-column label="图书" min-width="260">
        <template #default="{ row }">
          <div class="book-cell">
            <BookCover :cover-url="row.coverUrl" :title="row.bookTitle ?? ''" :size="34" />
            <div class="book-meta">
              <span class="book-title">{{ row.bookTitle ?? '（未匹配到图书）' }}</span>
              <span class="book-sub data-mono">{{ row.bookAuthors ?? row.documentFingerprint }}</span>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="进度" width="200">
        <template #default="{ row }">
          <el-progress
            :percentage="Math.round(row.percentage * 100)"
            :stroke-width="8"
            :show-text="false"
          />
          <span class="percent data-mono">{{ percentText(row.percentage) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="设备" min-width="150">
        <template #default="{ row }">
          <span>{{ row.device }}</span>
          <div class="device-id data-mono">{{ row.deviceId.slice(0, 8) }}…</div>
        </template>
      </el-table-column>

      <el-table-column label="最后上报" width="160">
        <template #default="{ row }">
          <span class="data-mono dim">{{ formatDateTime(row.reportedAt) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="关联" width="110" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.bookId" size="small" :type="row.matchSource === 'MANUAL' ? 'warning' : 'success'">
            {{ row.matchSource === 'MANUAL' ? '人工绑定' : '自动匹配' }}
          </el-tag>
          <el-tag v-else size="small" type="danger">孤儿</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="100" align="right">
        <template #default="{ row }">
          <el-button link type="danger" @click="reset(row)">重置进度</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="store.progressTotal"
        :page-size="store.size"
        :current-page="store.page + 1"
        @current-change="changePage"
      />
    </div>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0 12px;
  flex-wrap: wrap;
}

.filter-input {
  width: 210px;
}

.book-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.book-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.book-title {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-sub {
  font-size: 11.5px;
  color: var(--text-dim);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.percent {
  font-size: 12px;
  color: var(--text-dim);
}

.device-id {
  font-size: 11px;
  color: var(--text-dim);
}

.dim {
  color: var(--text-dim);
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
