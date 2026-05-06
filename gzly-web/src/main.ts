import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'

import 'vant/lib/index.css'
import './styles/global.css'
import './styles/page-shell.css'

function printOpenSourceConsoleMessage() {
  if (!import.meta.env.PROD) return
  if (typeof window === 'undefined' || typeof console === 'undefined') return

  const message = [
    '一卷试题，曾定你我来路。',
    '',
    '一方代码，亦可照后来人归途。',
    '',
    '此处既开源，便盼同道者同行。',
    '',
    '若有余力，何妨添一笔春风。',
  ].join('\n')

  const style = [
    'color: #1a4fff',
    'font-size: 31px',
    'font-weight: 700',
    'line-height: 1.95',
    'font-family: "Inter", "PingFang SC", "Helvetica Neue", "Microsoft YaHei", sans-serif',
    'letter-spacing: 0.15px',
    'padding: 12px 18px',
    'text-rendering: optimizeLegibility',
    '-webkit-font-smoothing: antialiased',
  ].join(';')

  console.log(`%c${message}`, style)
}

const app = createApp(App)
app.use(createPinia())
app.use(router)
printOpenSourceConsoleMessage()
app.mount('#app')
