import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'

// uni-app 要求导出 createApp，并使用 createSSRApp 创建实例
export function createApp() {
  const app = createSSRApp(App)
  app.use(createPinia())
  return {
    app,
  }
}
