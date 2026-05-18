/**
 * v7.54: vitest 配置（前端单测）
 *
 * 用法：
 *   npm install   # 拉 vitest devDep
 *   npm test      # 跑全量单测一次
 *   npm run test:watch  # watch 模式开发
 *
 * 测试文件放在 src/__tests__/**.test.ts 或 tests/**.test.ts
 */
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    globals: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
    },
  },
})
