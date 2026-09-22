<script setup lang="ts">
/**
 * 阅读进度（M3-sync）——KOReader 多端续读的管理页。
 *
 * 三块内容：同步账号（设备接入的前提）、进度列表（哪本书读到哪）、孤儿进度与设备。
 * 首屏由本页统一 loadAll；子组件自己不做自动加载，避免 el-tabs 把三个 pane 都挂载时重复请求。
 * **不做轮询**：进度由设备推送驱动，管理端刷得再勤也只会看到同一份数据。
 */
import { computed, onMounted, ref } from 'vue'

import DeviceTable from '@/components/DeviceTable.vue'
import OrphanTable from '@/components/OrphanTable.vue'
import ProgressTable from '@/components/ProgressTable.vue'
import SyncAccountCard from '@/components/SyncAccountCard.vue'
import { useBookSyncStore } from '@/stores/bookSync'
import { formatDateTime } from '@/utils/format'

const store = useBookSyncStore()
const activeTab = ref('progress')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    await store.loadAll()
  } finally {
    loading.value = false
  }
}

onMounted(load)

const lastReported = computed(() => formatDateTime(store.stats?.lastReportedAt ?? null))
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">阅读进度</h2>
        <p class="page-sub">
          阅读设备把「读到哪」推给 Bifrost，多端续读；这里配同步账号、看进度、处理孤儿
        </p>
      </div>
      <div class="page-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat">
        <span class="stat-value data-mono">{{ store.stats?.matchedCount ?? 0 }}</span>
        <span class="stat-label">已同步图书</span>
      </div>
      <div class="stat">
        <span class="stat-value data-mono">{{ store.stats?.orphanCount ?? 0 }}</span>
        <span class="stat-label">孤儿进度</span>
      </div>
      <div class="stat">
        <span class="stat-value data-mono">{{ store.stats?.deviceCount ?? 0 }}</span>
        <span class="stat-label">设备</span>
      </div>
      <div class="stat">
        <span class="stat-value data-mono small">{{ lastReported }}</span>
        <span class="stat-label">最后上报</span>
      </div>
    </div>

    <SyncAccountCard class="account-block" />

    <div class="card table-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane :label="`进度列表 (${store.progressTotal})`" name="progress">
          <ProgressTable />
        </el-tab-pane>
        <el-tab-pane :label="`孤儿进度 (${store.orphansTotal})`" name="orphan">
          <OrphanTable />
        </el-tab-pane>
        <el-tab-pane :label="`设备 (${store.devicesTotal})`" name="device">
          <DeviceTable />
        </el-tab-pane>
      </el-tabs>
    </div>

    <p class="foot-note">
      进度由设备推送驱动：客户端有 10 秒翻页防抖 + 25 秒全局去抖，且<b>关书时才自动推送</b>，
      所以这里的数字可能比设备上的实际位置<b>滞后</b>——在设备上手动
      「Push progress from this device now」可立即同步。
    </p>
  </div>
</template>

<style scoped>
.stat-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 120px;
  padding: 10px 16px;
  background: var(--panel-raised);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-value.small {
  font-size: 13px;
  font-weight: 500;
}

.stat-label {
  font-size: 11.5px;
  color: var(--text-dim);
}

.account-block {
  margin-bottom: 14px;
}

.table-card {
  padding: 6px 14px 14px;
}

.foot-note {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-dim);
  line-height: 1.7;
}
</style>
