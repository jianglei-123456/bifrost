<script setup lang="ts">
import { onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'

import SidebarNav from './SidebarNav.vue'
import TopBar from './TopBar.vue'
import BookScanStatusBar from '@/components/BookScanStatusBar.vue'
import { useBookStore } from '@/stores/book'
import { useMusicScanStore } from '@/stores/musicScan'
import { parseScanStats } from '@/utils/format'

const musicScan = useMusicScanStore()
const book = useBookStore()

let timer: number | undefined
let bookTimer: number | undefined

function clearBookTimer() {
  if (bookTimer !== undefined) {
    window.clearTimeout(bookTimer)
    bookTimer = undefined
  }
}

/** 图书扫描自适应轮询：SCANNING/触发后 1s，空闲 15s 静默兜底 */
async function bookTick() {
  await book.refreshScanStatus().catch(() => {})
  scheduleBookPoll()
}

function scheduleBookPoll() {
  clearBookTimer()
  const delay = book.scanStatus.scanStatus === 'SCANNING' || book.pendingScan ? 1000 : 15000
  bookTimer = window.setTimeout(() => {
    void bookTick()
  }, delay)
}

// 触发扫描后尽快切到 1s 收敛（给后端落 SCANNING 留 400ms）
watch(
  () => book.pendingScan,
  (v) => {
    if (v) {
      clearBookTimer()
      bookTimer = window.setTimeout(() => {
        void bookTick()
      }, 400)
    }
  },
)

// 扫描完成（SCANNING→IDLE）提示 + 图书目录/列表已由 store 刷新
watch(
  () => book.completionTick,
  (tick, prev) => {
    if (tick === prev) return
    const stats = parseScanStats(book.scanStatus.lastStats)
    ElMessage.success(
      stats
        ? `图书扫描完成：新增 ${stats.added} · 更新 ${stats.updated} · 缺失 ${stats.missing} · 错误 ${stats.error}`
        : '图书扫描完成',
    )
  },
)

onMounted(() => {
  // 音乐扫描：加载一次 + 每 10s 静默轮询（捕捉 Subsonic 等外部触发的扫描）
  musicScan.refresh().catch(() => {})
  timer = window.setInterval(() => {
    musicScan.refresh().catch(() => {})
  }, 10_000)

  // 图书扫描：先读一次再进自适应节奏
  book.refreshScanStatus().catch(() => {})
  scheduleBookPoll()
})

onUnmounted(() => {
  if (timer !== undefined) window.clearInterval(timer)
  clearBookTimer()
})
</script>

<template>
  <div class="app-shell">
    <SidebarNav />
    <div class="app-main">
      <TopBar />
      <BookScanStatusBar />
      <main class="app-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
}

.app-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.app-content {
  flex: 1;
}

@media (max-width: 1024px) {
  .app-main {
    margin-left: 0;
  }
}
</style>
