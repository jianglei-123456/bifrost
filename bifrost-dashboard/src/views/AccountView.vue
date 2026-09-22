<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

import { changePassword, fetchUser } from '@/api/user'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const username = ref('')

const formRef = ref<FormInstance>()
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const saving = ref(false)

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前口令', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新口令', trigger: 'blur' },
    { min: 6, message: '新口令至少 6 位', trigger: 'blur' },
  ],
  confirm: [
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.newPassword) callback(new Error('两次输入不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

onMounted(async () => {
  try {
    const user = await fetchUser()
    username.value = user.username
  } catch {
    username.value = auth.username
  }
})

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await changePassword(form.oldPassword, form.newPassword)
    const newPassword = form.newPassword
    ElMessage.success('口令已更新')
    form.oldPassword = ''
    form.newPassword = ''
    form.confirm = ''
    // 更新内存凭据，避免旧口令失效（silent：失败不弹全局提示）
    auth.login(username.value, newPassword, true).catch(() => {})
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">账号设置</h2>
        <p class="page-sub">v1 单管理员账号</p>
      </div>
    </div>

    <section class="card account-card">
      <div class="account-head">
        <span class="account-avatar">{{ (username[0] || 'A').toUpperCase() }}</span>
        <div>
          <div class="account-name">{{ username }}</div>
          <div class="account-role">ADMIN</div>
        </div>
      </div>
      <hr class="spectrum-rule" />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="pwd-form">
        <el-form-item label="当前口令" prop="oldPassword">
          <el-input
            v-model="form.oldPassword"
            type="password"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item label="新口令" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="确认新口令" prop="confirm">
          <el-input
            v-model="form.confirm"
            type="password"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-button type="primary" :loading="saving" @click="submit">更新口令</el-button>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
.account-card {
  max-width: 460px;
  padding: 24px;
}

.account-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
}

.account-avatar {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 24px;
  color: #0b0e14;
  background: var(--spectrum);
}

.account-name {
  font-size: 17px;
  font-weight: 700;
}

.account-role {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-dim);
  letter-spacing: 0.08em;
}

.pwd-form {
  margin-top: 18px;
  max-width: 360px;
}
</style>
