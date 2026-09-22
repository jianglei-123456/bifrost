/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 同源路线（ADR-0002）：开发期代理到后端，生产由反代/后端托管，绕开 CORS
      '/api': { target: 'http://localhost:18080', changeOrigin: true },
      '/rest': { target: 'http://localhost:18080', changeOrigin: true },
      // 图书封面/下载直链（/opds/v1.2/catalog/...）走 OPDS 端点
      '/opds': { target: 'http://localhost:18080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.spec.ts'],
    setupFiles: ['src/test/setup.ts'],
    css: false,
  },
})
