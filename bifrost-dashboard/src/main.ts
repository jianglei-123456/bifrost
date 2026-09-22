import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

// Element Plus 深色主题变量（html.dark 已由 index.html 声明）
import 'element-plus/theme-chalk/dark/css-vars.css'
// 命令式 API（ElMessage/ElMessageBox/ElNotification）样式
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'

// 自托管字体（design.md §3：Display=Space Grotesk，Mono=JetBrains Mono）
import '@fontsource/space-grotesk/500.css'
import '@fontsource/space-grotesk/700.css'
import '@fontsource/jetbrains-mono/400.css'
import '@fontsource/jetbrains-mono/500.css'

import './styles/tokens.css'
import './styles/base.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
