<script setup lang="ts">
/**
 * 状态呼吸灯点（原 .status-dot 三处重复实现 → 抽公共）。
 * 空闲=绿点；扫描中=琥珀点 + breathe。
 */
defineProps<{
  scanning?: boolean
}>()
</script>

<template>
  <span class="status-dot" :class="{ scanning }" aria-hidden="true" />
</template>

<style scoped>
.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--success);
  display: inline-block;
  flex-shrink: 0;
}

.status-dot.scanning {
  background: var(--warn);
  animation: breathe 2s ease-in-out infinite;
}
</style>
