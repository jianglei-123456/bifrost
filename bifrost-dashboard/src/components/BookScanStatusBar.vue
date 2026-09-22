<script setup lang="ts">
import { useRouter } from 'vue-router'

import ScanStatusDot from './ScanStatusDot.vue'
import { useBookStore } from '@/stores/book'

const book = useBookStore()
const router = useRouter()

function goRoots() {
  if (router.currentRoute.value.name !== 'book-roots') {
    router.push({ name: 'book-roots' })
  }
}
</script>

<template>
  <transition name="scanbar">
    <div v-if="book.scanning" class="book-scanbar" role="status" @click="goRoots">
      <ScanStatusDot scanning />
      <span class="text">图书扫描中</span>
      <template v-if="book.currentScanRoot">
        <span class="sep">·</span>
        <span class="root">{{ book.currentScanRoot.name }}</span>
      </template>
      <span v-else class="sep">·</span>
      <span class="dim">{{ book.currentScanRoot ? '正在入库' : '全部图书目录排队中' }}</span>
      <span class="hint">点击查看图书库 →</span>
    </div>
  </transition>
</template>

<style scoped>
.book-scanbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 28px;
  font-size: 12.5px;
  color: var(--text);
  background: rgba(255, 180, 84, 0.1);
  border-bottom: 1px solid rgba(255, 180, 84, 0.28);
  cursor: pointer;
  user-select: none;
}

.text {
  font-weight: 600;
  color: var(--warn);
}

.root {
  font-family: var(--font-mono);
  color: var(--text);
}

.sep {
  color: #5d6a82;
}

.dim {
  color: var(--text-dim);
}

.hint {
  margin-left: auto;
  color: var(--text-dim);
  font-size: 12px;
}

.scanbar-enter-active,
.scanbar-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.scanbar-enter-from,
.scanbar-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
