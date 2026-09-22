<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'

import ScanStatusDot from '@/components/ScanStatusDot.vue'
import { useAuthStore } from '@/stores/auth'
import { useMusicScanStore } from '@/stores/musicScan'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const musicScan = useMusicScanStore()

const title = computed(() => (route.meta.title as string | undefined) ?? 'Bifrost')
const initial = computed(() => (auth.username[0] ?? 'B').toUpperCase())

async function onCommand(command: string) {
  if (command === 'logout') {
    await ElMessageBox.confirm('退出后需要重新输入口令。', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
    auth.clear()
    router.push({ name: 'login' })
  }
}
</script>

<template>
  <header class="topbar">
    <h1 class="topbar-title">{{ title }}</h1>
    <div class="topbar-right">
      <div class="scan-pill" :title="musicScan.scanning ? '音乐扫描中' : '音乐扫描空闲'">
        <ScanStatusDot :scanning="musicScan.scanning" />
        <span>{{ musicScan.scanning ? '扫描中' : '空闲' }}</span>
      </div>
      <el-dropdown trigger="click" @command="onCommand">
        <button class="user-chip" type="button">
          <span class="user-avatar">{{ initial }}</span>
          <span class="user-name">{{ auth.username }}</span>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="account">修改密码</el-dropdown-item>
            <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 28px;
  border-bottom: 1px solid var(--line);
  background: rgba(11, 14, 20, 0.72);
  backdrop-filter: blur(8px);
  position: sticky;
  top: 0;
  z-index: 10;
}

.topbar-title {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
  margin: 0;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.scan-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-dim);
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 3px 10px;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: none;
  border: 1px solid transparent;
  border-radius: 999px;
  padding: 4px 10px 4px 4px;
  cursor: pointer;
  color: var(--text);
  font-size: 13px;
  transition: border-color 0.15s ease;
}

.user-chip:hover {
  border-color: var(--line);
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 13px;
  color: #0b0e14;
  background: var(--spectrum);
}
</style>
