import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// H5 开发时把 /api 代理到后端；App / 小程序 端走 src/config/env.ts 的绝对地址。
// 默认代理到公网后端，便于开箱预览；本地起了后端可改成 http://127.0.0.1:8090。
export default defineConfig({
  plugins: [uni()],
  server: {
    host: '0.0.0.0',
    port: 5180,
    proxy: {
      '/api': {
        target: 'http://39.97.232.141',
        changeOrigin: true,
      },
      // 院校详情的校园图/排名等 CDN 数据（info.json），H5 经此代理规避跨域；App/小程序直连。
      '/cdn-proxy': {
        target: 'https://static-data.gaokao.cn',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/cdn-proxy/, ''),
      },
    },
  },
})
