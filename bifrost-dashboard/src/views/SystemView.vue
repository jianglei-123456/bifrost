<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { ping } from '@/api/client'
import { useMusicScanStore } from '@/stores/musicScan'
import { formatDateTime } from '@/utils/format'

const musicScan = useMusicScanStore()

const checking = ref(false)
const pong = ref<string | null>(null)
const pingError = ref('')

async function check() {
  checking.value = true
  pingError.value = ''
  try {
    pong.value = await ping()
  } catch {
    pong.value = null
    pingError.value = '无法连接后端服务'
  } finally {
    checking.value = false
  }
}

onMounted(() => {
  check()
  musicScan.refresh().catch(() => {})
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">系统信息</h2>
        <p class="page-sub">客户端与服务端状态</p>
      </div>
      <div class="page-actions">
        <el-button :loading="checking" @click="check">重新检测</el-button>
      </div>
    </div>

    <div class="grid-2">
      <section class="card panel">
        <h3 class="panel-title">后端连通性</h3>
        <div class="ping-row">
          <span class="ping-dot" :class="{ ok: pong === 'pong', fail: pingError }" />
          <template v-if="pong === 'pong'">
            <span class="ping-text">服务正常</span>
            <span class="data-mono ping-value">pong</span>
          </template>
          <template v-else-if="pingError">
            <span class="ping-text fail-text">{{ pingError }}</span>
          </template>
          <template v-else>
            <span class="ping-text dim">检测中…</span>
          </template>
        </div>
        <div class="meta">
          管理 REST 基址 <span class="data-mono">/api</span> · 端口
          <span class="data-mono">18080</span>
          （开发期经 Vite 代理）
        </div>
      </section>

      <section class="card panel">
        <h3 class="panel-title">音乐库</h3>
        <div class="meta">
          音乐目录数 <span class="data-mono">{{ musicScan.roots.length }}</span>
          <template v-if="musicScan.lastScanAt">
            · 最近扫描 <span class="data-mono">{{ formatDateTime(musicScan.lastScanAt) }}</span>
          </template>
        </div>
      </section>
    </div>

    <section class="card panel about">
      <h3 class="panel-title">关于彩虹桥</h3>
      <p class="about-text">
        Bifrost 源自北欧神话中联通九界的彩虹桥。这里，它联通你的音乐、影音与图书 ——
        家庭媒体库的管理端。v1 交付音乐，影音与图书即将联通。
      </p>
      <div class="about-tags">
        <span class="tag">Vue 3 · TypeScript · Vite</span>
        <span class="tag">Pinia · Vue Router · Element Plus</span>
        <span class="tag">ECharts</span>
        <span class="tag data-mono">v0.1.0</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}

.panel {
  padding: 18px 20px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 14px;
}

.ping-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ping-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #4a5570;
}

.ping-dot.ok {
  background: var(--success);
}

.ping-dot.fail {
  background: var(--danger);
}

.ping-text {
  font-size: 14px;
  font-weight: 600;
}

.fail-text {
  color: var(--danger);
}

.ping-value {
  font-size: 12.5px;
  color: var(--text-dim);
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 1px 8px;
}

.meta {
  font-size: 12.5px;
  color: var(--text-dim);
  margin-top: 12px;
}

.dim {
  color: #5d6a82;
}

.about {
  margin-top: 14px;
}

.about-text {
  margin: 0 0 14px;
  font-size: 13.5px;
  color: var(--text-dim);
  line-height: 1.8;
  max-width: 640px;
}

.about-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  font-size: 12px;
  color: var(--text-dim);
  background: var(--panel-raised);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 2px 10px;
}

@media (max-width: 900px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
