<script setup lang="ts">
/**
 * 同步账号卡片（M3-sync）：设备接入的全部前提。
 *
 * 页面上的地址/用户名/口令是给用户「抄进阅读设备」的，所以：
 * - 口令默认打码，但可一键显示明文（后端可逆存储就是为了这个，R2a）；
 * - 地址必须带 http:// —— KOReader 的输入框预填 https:// 且协议不自动补 scheme；
 * - 改口令会让已配置的设备全部失效，界面必须提前说清。
 */
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { useAuthStore } from '@/stores/auth'
import { useBookSyncStore } from '@/stores/bookSync'
import { copyText } from '@/utils/clipboard'

const store = useBookSyncStore()
const auth = useAuthStore()

const showPassword = ref(false)
const generating = ref(false)

const dialog = reactive({ visible: false, saving: false, username: '', password: '' })

const account = computed(() => store.account)

function openEdit() {
  dialog.visible = true
  dialog.username = account.value?.username ?? ''
  dialog.password = ''
}

async function copy(value: string | null | undefined, label: string) {
  const ok = await copyText(value ?? '')
  if (ok) ElMessage.success(`${label}已复制`)
  else ElMessage.warning('复制失败，请手动选中复制')
}

async function save() {
  const username = dialog.username.trim()
  const password = dialog.password
  if (!username) {
    ElMessage.warning('同步用户名不能为空')
    return
  }
  if (username.includes(':')) {
    ElMessage.warning("同步用户名不能包含 ':'")
    return
  }
  // R2c 前置校验（后端也会拒绝并返回 code=1000）：同步口令不能与管理员口令相同
  if (password && auth.password && password === auth.password) {
    ElMessage.warning('同步口令不能与管理员口令相同')
    return
  }
  dialog.saving = true
  try {
    await store.saveAccount({ username, ...(password ? { password } : {}) })
    if (password) {
      ElMessage.success('同步口令已更新：已配置的设备需要重新登录')
    } else {
      ElMessage.success('同步用户名已更新')
    }
    dialog.visible = false
  } finally {
    dialog.saving = false
  }
}

async function generate() {
  generating.value = true
  try {
    await store.generatePassword()
    showPassword.value = true
    ElMessage.success('已生成新口令：请抄进阅读设备（旧设备需重新登录）')
  } finally {
    generating.value = false
  }
}
</script>

<template>
  <div class="card account-card">
    <div class="card-head">
      <div>
        <h3 class="card-title">同步账号</h3>
        <p class="card-sub">
          阅读设备用它登录进度同步；与管理员账号相互独立（同步口令不能与管理员口令相同）
        </p>
      </div>
      <div class="card-actions">
        <el-button :loading="generating" @click="generate">生成随机口令</el-button>
        <el-button type="primary" @click="openEdit">修改账号</el-button>
      </div>
    </div>

    <el-descriptions v-if="account" :column="1" border>
      <el-descriptions-item label="服务器地址">
        <span class="data-mono">{{ account.serverUrl }}</span>
        <el-button link type="primary" @click="copy(account.serverUrl, '服务器地址')">复制</el-button>
        <el-tag v-if="!account.serverUrlHintConfigured" size="small" type="info" class="tag-gap">
          按当前访问地址推断，请核对设备可达性
        </el-tag>
      </el-descriptions-item>

      <el-descriptions-item label="用户名">
        <span class="data-mono">{{ account.username }}</span>
        <el-button link type="primary" @click="copy(account.username, '用户名')">复制</el-button>
      </el-descriptions-item>

      <el-descriptions-item label="口令">
        <span class="data-mono">{{ showPassword ? account.password : '••••••••••••' }}</span>
        <el-button link type="primary" @click="showPassword = !showPassword">
          {{ showPassword ? '隐藏' : '显示' }}
        </el-button>
        <el-button link type="primary" @click="copy(account.password, '口令')">复制</el-button>
      </el-descriptions-item>

      <el-descriptions-item label="服务开关">
        <el-tag :type="account.registrationEnabled ? 'success' : 'info'" size="small">
          设备端注册{{ account.registrationEnabled ? '已放行' : '已关闭' }}
        </el-tag>
        <el-tag :type="account.autoScanOnUnmatched ? 'success' : 'info'" size="small" class="tag-gap">
          未匹配进度{{ account.autoScanOnUnmatched ? '自动扫描一次' : '不自动扫描' }}
        </el-tag>
      </el-descriptions-item>
    </el-descriptions>

    <el-alert type="info" :closable="false" class="tips">
      <template #title>把设备接上（KOReader）</template>
      <ol class="tip-list">
        <li>
          *Tools → Progress sync → Custom sync server* 填
          <span class="data-mono">{{ account?.serverUrl }}</span> —— 输入框预填的是
          <span class="data-mono">https://</span>，<b>必须手动改成 <span class="data-mono">http://</span></b>
          （协议不会自动补 scheme）。
        </li>
        <li>回到 *Progress sync* 点 <b>Register 或 Login</b>（两者都可用），填上面的用户名与口令。</li>
        <li>打开 <b>"Automatically keep documents in sync"</b>（默认是关的）。</li>
        <li>
          <b>Document matching method 保持 "Binary"</b>——改成 "Filename" 会让已有进度全部匹配不上。
        </li>
        <li>多台设备重复上述步骤，<b>共用同一个同步账号</b>即可互相续读。</li>
        <li><b>改口令后所有设备都会认证失败</b>，需要在设备上重新 Login（已同步的进度数据不受影响）。</li>
      </ol>
    </el-alert>

    <el-dialog v-model="dialog.visible" title="修改同步账号" width="480px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="dialog.username" placeholder="如：reader" />
        </el-form-item>
        <el-form-item label="新口令（留空表示不改）">
          <el-input v-model="dialog.password" type="password" show-password placeholder="留空则只改用户名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.account-card {
  padding: 16px 18px;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.card-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.card-sub {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--text-dim);
}

.card-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.tag-gap {
  margin-left: 6px;
}

.tips {
  margin-top: 14px;
}

.tip-list {
  margin: 6px 0 0;
  padding-left: 18px;
  line-height: 1.9;
  font-size: 12.5px;
}
</style>
