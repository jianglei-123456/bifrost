import { config } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { vi } from 'vitest'

// Element Plus 组件在 jsdom 下的 stub（避免真实渲染开销与 matchMedia 缺失）
config.global.stubs = {
  'el-icon': true,
}

// jsdom 缺失的 API
if (!window.matchMedia) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }))
}

// 每个用例独立的 Pinia 实例
beforeEach(() => {
  setActivePinia(createPinia())
})
