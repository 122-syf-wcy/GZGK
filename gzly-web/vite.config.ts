import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from '@vant/auto-import-resolver'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [VantResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 3001,
    proxy: {
      '/api': {
        // 本地后端端口可用 GZLY_DEV_API 覆盖（如 8082 被其他程序占用时）
        target: process.env.GZLY_DEV_API || 'http://localhost:8082',
        changeOrigin: true,
      },
      '/uploads': {
        target: (process.env.GZLY_DEV_API || 'http://localhost:8082') + '/api',
        changeOrigin: true,
      },
      '/gaokao-proxy': {
        target: 'https://www.gaokao.cn',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/gaokao-proxy/, ''),
      },
      '/cdn-proxy': {
        target: 'https://static-data.gaokao.cn',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/cdn-proxy/, ''),
      },
    },
  },
})
