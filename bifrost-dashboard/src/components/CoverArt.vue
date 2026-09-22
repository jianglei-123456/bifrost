<script setup lang="ts">
import { computed, ref } from 'vue'

import { useAuthStore } from '@/stores/auth'
import { buildCoverUrl } from '@/utils/subsonic'

const props = withDefaults(
  defineProps<{
    albumId: number
    /** 请求档位（后端按原图/200/64 最近档位生成） */
    size?: number
    title?: string
    /** 流体模式：填满父容器（配合 aspect-ratio），用于响应式封面网格 */
    fluid?: boolean
  }>(),
  { size: 200, title: '', fluid: false },
)

const auth = useAuthStore()
const failed = ref(false)

const url = computed(() => {
  const creds = auth.credentials
  if (!creds || failed.value) return ''
  return buildCoverUrl(props.albumId, props.size, creds.username, creds.password, creds.salt)
})

const fallbackChar = computed(() => {
  const t = props.title.trim()
  return t ? t[0] : '♫'
})

/** 兜底字符字号随封面尺寸缩放 */
const boxStyle = computed(() =>
  props.fluid
    ? { width: '100%', aspectRatio: '1 / 1' }
    : { width: `${props.size}px`, height: `${props.size}px` },
)

const fallbackStyle = computed(() => ({
  fontSize: `${Math.max(14, Math.round(props.size * 0.24))}px`,
}))
</script>

<template>
  <div class="cover" :style="boxStyle" :title="title || undefined">
    <img v-if="url" :src="url" :alt="title || '封面'" loading="lazy" @error="failed = true" />
    <div v-else class="cover-fallback">
      <span class="cover-char" :style="fallbackStyle">{{ fallbackChar }}</span>
      <span class="cover-rule" />
    </div>
  </div>
</template>

<style scoped>
.cover {
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--panel-raised);
  border: 1px solid var(--line);
  flex-shrink: 0;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 无封面兜底：暗色渐变 + 首字符 + 光谱底线（design.md §5） */
.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: linear-gradient(160deg, var(--panel-raised) 0%, var(--panel) 70%);
}

.cover-char {
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--text-dim);
  line-height: 1;
}

.cover-rule {
  width: 56%;
  height: 2px;
  border-radius: 2px;
  background: var(--spectrum);
  opacity: 0.6;
}
</style>
