<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    value: string | number
    /** 辅助说明（如单位、对比） */
    hint?: string
    /** 数据等宽 */
    mono?: boolean
  }>(),
  { hint: '', mono: true },
)
</script>

<template>
  <div class="stat-card card card-hover">
    <div class="stat-label">{{ label }}</div>
    <div class="stat-value" :class="{ 'data-mono': mono }">{{ value }}</div>
    <div v-if="hint" class="stat-hint">{{ hint }}</div>
  </div>
</template>

<style scoped>
.stat-card {
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 12px;
  color: var(--text-dim);
}

.stat-value {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.stat-hint {
  font-size: 12px;
  color: #5d6a82;
}
</style>
