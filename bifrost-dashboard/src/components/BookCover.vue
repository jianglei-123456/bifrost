<script setup lang="ts">
import { computed, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    /** BookDto.coverUrl（相对路径，无封面为 null） */
    coverUrl?: string | null
    title?: string
    /** 请求档位（拼 ?size=N，后端生成缩略图）；缺省取原图 */
    size?: number
    width?: number
    height?: number
    /** 圆角覆写（如表格内小图用 --radius-sm） */
    rounded?: boolean
  }>(),
  { coverUrl: null, title: '', size: undefined, width: undefined, height: undefined, rounded: true },
)

const failed = ref(false)

/** 相对路径必须拼 origin（后端无协议/端口，KOReader 跨网会破图） */
const url = computed(() => {
  if (!props.coverUrl || failed.value) return ''
  const u = `${window.location.origin}${props.coverUrl}`
  return props.size ? `${u}?size=${props.size}` : u
})

const boxStyle = computed(() => {
  const w = props.width ?? props.size
  const h = props.height ?? props.width ?? props.size
  return { width: w ? `${w}px` : '100%', height: h ? `${h}px` : '100%' }
})

const rootClass = computed(() => ({ rounded: props.rounded }))
</script>

<template>
  <div class="book-cover" :class="rootClass" :style="boxStyle" :title="title || undefined">
    <img
      v-if="url"
      :src="url"
      :alt="title || '图书封面'"
      loading="lazy"
      @error="failed = true"
    />
    <div v-else class="cover-fallback">
      <span class="cover-emoji">📖</span>
      <span class="cover-rule" />
    </div>
  </div>
</template>

<style scoped>
.book-cover {
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--panel-raised);
  border: 1px solid var(--line);
  flex-shrink: 0;
  display: block;
}

.book-cover.rounded {
  border-radius: var(--radius-sm);
}

.book-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 无封面兜底：暗色渐变 + 📖 + 光谱底线（与 CoverArt 同语言） */
.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  background: linear-gradient(160deg, var(--panel-raised) 0%, var(--panel) 70%);
}

.cover-emoji {
  font-size: 17px;
  line-height: 1;
  filter: saturate(0.75);
}

.cover-rule {
  width: 56%;
  height: 2px;
  border-radius: 2px;
  background: var(--spectrum);
  opacity: 0.55;
}
</style>
