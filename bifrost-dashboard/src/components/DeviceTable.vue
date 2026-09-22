<script setup lang="ts">
/**
 * 同步设备（M3-sync）：只读列表。
 *
 * 刻意**没有"踢设备"**：协议层没有会话，凭据只有一对用户名/口令，
 * 封锁单台设备在协议层无法生效——做成按钮就是假能力。要强制所有设备重新登录，
 * 只能改同步口令（见上方账号卡片）。
 */
import { useBookSyncStore } from '@/stores/bookSync'
import { formatDateTime } from '@/utils/format'

const store = useBookSyncStore()
</script>

<template>
  <div>
    <p class="hint">
      协议层没有设备注册，"设备"是随每次上报捎带的标识——这里列的是<b>见到过并记录下来</b>的设备。
      要让某台设备停止同步，只能改同步口令（会让所有设备都需要重新登录）。
    </p>

    <el-table :data="store.devices" empty-text="还没有设备上报过进度">
      <el-table-column label="设备名" min-width="180">
        <template #default="{ row }">
          <span class="device-name">{{ row.deviceName }}</span>
        </template>
      </el-table-column>

      <el-table-column label="设备 ID" min-width="220">
        <template #default="{ row }">
          <span class="data-mono dim">{{ row.deviceId }}</span>
        </template>
      </el-table-column>

      <el-table-column label="首次见到" width="160">
        <template #default="{ row }">
          <span class="data-mono dim">{{ formatDateTime(row.firstSeenAt) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="最近见到" width="160">
        <template #default="{ row }">
          <span class="data-mono dim">{{ formatDateTime(row.lastSeenAt) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="上报次数" width="110" align="right">
        <template #default="{ row }">
          <span class="data-mono">{{ row.reportCount }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.hint {
  margin: 4px 0 12px;
  font-size: 12.5px;
  color: var(--text-dim);
  line-height: 1.7;
}

.device-name {
  font-weight: 600;
}

.dim {
  color: var(--text-dim);
}
</style>
