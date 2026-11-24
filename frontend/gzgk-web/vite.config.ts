import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'node:path';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api/v2/core': {
        target: process.env.VITE_CORE_TARGET || 'http://127.0.0.1:8082',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api\/v2\/core/, ''),
      },
      '/api/v2/agent': {
        target: process.env.VITE_AGENT_TARGET || 'http://127.0.0.1:8001',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api\/v2\/agent/, ''),
      },
    },
  },
});
