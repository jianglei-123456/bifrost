<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({
  username: 'admin',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入口令', trigger: 'blur' }],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  errorMsg.value = ''
  try {
    // silent：登录验证失败由本页给出明确文案（后端契约不区分用户/口令）
    await auth.login(form.username, form.password, true)
    const redirect = (route.query.redirect as string | undefined) || '/'
    router.replace(redirect)
  } catch {
    errorMsg.value = '用户名或口令不正确'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <!-- 签名元素：彩虹拱桥（design.md §5 —— 唯一的戏剧性时刻） -->
    <svg
      class="bridge"
      viewBox="0 0 1200 420"
      preserveAspectRatio="xMidYMax meet"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="bridge-spec" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stop-color="#FF5D73" />
          <stop offset="0.2" stop-color="#FFB454" />
          <stop offset="0.4" stop-color="#3DDC97" />
          <stop offset="0.6" stop-color="#4CC9F0" />
          <stop offset="0.8" stop-color="#7B6CFF" />
          <stop offset="1" stop-color="#FF5D73" />
        </linearGradient>
        <linearGradient id="bridge-fade" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#7B6CFF" stop-opacity="0.28" />
          <stop offset="1" stop-color="#7B6CFF" stop-opacity="0" />
        </linearGradient>
      </defs>
      <path
        d="M60 400 A 540 400 0 0 1 1140 400"
        fill="none"
        stroke="url(#bridge-spec)"
        stroke-width="10"
        stroke-linecap="round"
      />
      <path
        d="M60 400 A 540 400 0 0 1 1140 400"
        fill="none"
        stroke="url(#bridge-spec)"
        stroke-width="26"
        stroke-linecap="round"
        opacity="0.18"
      />
      <path d="M60 400 A 540 400 0 0 1 1140 400" fill="url(#bridge-fade)" opacity="0.5" />
    </svg>

    <div class="login-panel card">
      <div class="login-head">
        <h1 class="login-title">Bifrost</h1>
        <p class="login-sub">彩虹桥 · 联通音乐、影音与图书</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @keyup.enter="submit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :prefix-icon="User" autocomplete="username" />
        </el-form-item>
        <el-form-item label="口令" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>

        <p v-if="errorMsg" class="login-error" role="alert">{{ errorMsg }}</p>

        <el-button class="login-btn" type="primary" size="large" :loading="loading" @click="submit">
          进入管理端
        </el-button>
      </el-form>

      <p class="login-foot">登录有效期为 1 天 · <span class="data-mono">v0.1.0</span></p>
    </div>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 24px;
}

/* 拱桥横跨画面底部 */
.bridge {
  position: absolute;
  left: 0;
  right: 0;
  bottom: -140px;
  width: 100%;
  height: 46vh;
  pointer-events: none;
}

.login-panel {
  position: relative;
  z-index: 1;
  width: 380px;
  max-width: 100%;
  padding: 32px 32px 24px;
  background: rgba(18, 22, 31, 0.9);
  backdrop-filter: blur(10px);
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.5);
}

.login-head {
  margin-bottom: 22px;
}

.login-title {
  font-family: var(--font-display);
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 0.05em;
  margin: 0;
  background: var(--spectrum);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.login-sub {
  color: var(--text-dim);
  font-size: 13px;
  margin: 6px 0 0;
}

.login-error {
  color: var(--danger);
  font-size: 13px;
  margin: -4px 0 12px;
}

.login-btn {
  width: 100%;
  margin-top: 4px;
}

.login-foot {
  margin: 18px 0 0;
  font-size: 12px;
  color: #5d6a82;
  text-align: center;
}

@media (max-width: 640px) {
  .bridge {
    bottom: -80px;
    height: 32vh;
  }
}
</style>
