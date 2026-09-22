<script setup lang="ts">
import { ref } from 'vue'
import { Star, StarFilled } from '@element-plus/icons-vue'

import { rate } from '@/api/annotations'
import type { AnnotationType } from '@/api/types'

const props = defineProps<{
  /** 当前评分 0–5 */
  modelValue: number
  type: AnnotationType
  id: number
  /** 只读展示（如总览 Top） */
  readonly?: boolean
}>()

const emit = defineEmits<{ change: [rating: number] }>()

const busy = ref(false)
const hover = ref(0)

async function setRating(value: number) {
  if (props.readonly || busy.value) return
  busy.value = true
  try {
    // 再点同一分值 = 取消（0）
    const next = props.modelValue === value ? 0 : value
    await rate(props.type, props.id, next)
    emit('change', next)
  } finally {
    busy.value = false
  }
}

function displayValue(): number {
  return hover.value || props.modelValue || 0
}
</script>

<template>
  <div
    class="rating"
    :class="{ readonly, busy }"
    :aria-label="`评分 ${modelValue}/5`"
    @mouseleave="hover = 0"
  >
    <button
      v-for="n in 5"
      :key="n"
      type="button"
      class="star"
      :class="{ filled: n <= displayValue() }"
      :disabled="readonly || busy"
      :aria-label="`${n} 分`"
      @mouseenter="!readonly && (hover = n)"
      @click.stop="setRating(n)"
    >
      <el-icon :size="15">
        <StarFilled v-if="n <= displayValue()" />
        <Star v-else />
      </el-icon>
    </button>
  </div>
</template>

<style scoped>
.rating {
  display: inline-flex;
  align-items: center;
  gap: 1px;
}

.star {
  display: inline-flex;
  border: none;
  background: none;
  padding: 2px;
  cursor: pointer;
  color: #3a4256;
  transition: color 0.12s ease;
}

.rating:not(.readonly) .star:hover {
  color: var(--warn);
}

.star.filled {
  color: var(--warn);
}

.rating.busy .star {
  cursor: wait;
}
</style>
