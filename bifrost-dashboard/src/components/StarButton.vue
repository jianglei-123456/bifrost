<script setup lang="ts">
import { ref } from 'vue'
import { Star, StarFilled } from '@element-plus/icons-vue'

import { star, unstar } from '@/api/annotations'
import type { AnnotationType } from '@/api/types'

const props = defineProps<{
  starred: boolean
  type: AnnotationType
  id: number
}>()

const emit = defineEmits<{ change: [starred: boolean] }>()

const busy = ref(false)

async function toggle() {
  if (busy.value) return
  busy.value = true
  try {
    if (props.starred) {
      await unstar({ type: props.type, id: props.id })
      emit('change', false)
    } else {
      await star({ type: props.type, id: props.id })
      emit('change', true)
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <el-tooltip :content="starred ? '取消收藏' : '收藏'" placement="top">
    <button
      type="button"
      class="star-btn"
      :class="{ starred, busy }"
      :disabled="busy"
      :aria-label="starred ? '取消收藏' : '收藏'"
      @click.stop="toggle"
    >
      <el-icon :size="16">
        <StarFilled v-if="starred" />
        <Star v-else />
      </el-icon>
    </button>
  </el-tooltip>
</template>

<style scoped>
.star-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: none;
  color: #4a5570;
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease,
    transform 0.1s ease;
}

.star-btn:hover {
  color: var(--warn);
  border-color: var(--line);
}

.star-btn.starred {
  color: var(--warn);
}

.star-btn.busy {
  opacity: 0.5;
  cursor: wait;
}
</style>
