import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'

import 'vant/lib/index.css'
import './styles/global.css'
import './styles/page-shell.css'

const appRootSelector = '#app'

function showStartupFallback(reason?: unknown) {
  if (typeof document === 'undefined') return

  const root = document.querySelector(appRootSelector)
  if (!root || root.childElementCount > 0) return

  const isChunkError = String(reason || '').toLowerCase().includes('chunk')
    || String(reason || '').toLowerCase().includes('dynamically imported')

  root.innerHTML = `
    <main style="min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;background:#f7f9fc;color:#172033;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;">
      <section style="width:min(100%,520px);border:1px solid #dbe3ef;border-radius:16px;background:#fff;padding:24px;box-shadow:0 18px 48px rgba(23,32,51,.08);">
        <h1 style="margin:0 0 12px;font-size:22px;line-height:1.35;">页面加载遇到问题</h1>
        <p style="margin:0 0 16px;color:#536172;line-height:1.7;">请刷新页面后重试。如果仍然失败，请清理浏览器缓存，或打开兼容诊断页检查当前浏览器环境。</p>
        ${isChunkError ? '<p style="margin:0 0 16px;color:#9a5b00;line-height:1.7;">检测到静态资源加载失败，通常和浏览器缓存中的旧页面有关。</p>' : ''}
        <div style="display:flex;flex-wrap:wrap;gap:10px;">
          <button type="button" onclick="location.reload()" style="height:40px;padding:0 16px;border:0;border-radius:10px;background:#1a4fff;color:#fff;font-weight:700;">刷新页面</button>
          <a href="/compat-check.html" style="height:40px;padding:0 16px;border-radius:10px;border:1px solid #c9d3e2;color:#1a4fff;text-decoration:none;display:inline-flex;align-items:center;font-weight:700;">打开诊断页</a>
        </div>
      </section>
    </main>
  `
}

function installStartupErrorGuards() {
  if (typeof window === 'undefined') return

  window.addEventListener('error', (event) => {
    showStartupFallback(event.error || event.message)
  })

  window.addEventListener('unhandledrejection', (event) => {
    showStartupFallback(event.reason)
  })
}

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

installStartupErrorGuards()

try {
  const app = createApp(App)
  app.config.errorHandler = (error) => {
    showStartupFallback(error)
    throw error
  }
  app.use(createPinia())
  app.use(router)
  printOpenSourceConsoleMessage()
  app.mount(appRootSelector)
} catch (error) {
  showStartupFallback(error)
  throw error
}
