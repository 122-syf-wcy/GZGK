<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ShieldCheck, AlertTriangle } from 'lucide-vue-next'
import { COMPLIANCE_SECTIONS } from '@/constants/compliance'
import {
  ACCEPT_KEYS,
  FULL_DISCLAIMER,
  SITE_DISCLAIMER_VERSION,
  SITE_GATE_AGREE_BTN,
  SITE_GATE_AGREE_LABEL,
  SITE_GATE_INTRO,
  SITE_GATE_REJECT_BTN,
  SITE_GATE_REJECTED_DESC,
  SITE_GATE_REJECTED_TITLE,
  SITE_GATE_REREAD_BTN,
  SITE_GATE_SCROLL_HINT,
  SITE_GATE_TITLE,
} from '@/constants/disclaimer'

const visible = ref(false)
const rejected = ref(false)
const scrolledToBottom = ref(false)
const agreed = ref(false)
const bodyRef = ref<HTMLElement | null>(null)

function readAccepted(): boolean {
  try {
    return localStorage.getItem(ACCEPT_KEYS.site) === SITE_DISCLAIMER_VERSION
  } catch {
    return true
  }
}

function lockScroll(lock: boolean) {
  try {
    document.body.style.overflow = lock ? 'hidden' : ''
  } catch {
    // ignore
  }
}

function onBodyScroll() {
  const el = bodyRef.value
  if (!el) return
  // 距底部 32px 内即视为读到底：兼容移动端分数像素、惯性滚动、不同缩放/字号
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 32) {
    scrolledToBottom.value = true
  }
}

function accept() {
  if (!scrolledToBottom.value || !agreed.value) return
  try {
    localStorage.setItem(ACCEPT_KEYS.site, SITE_DISCLAIMER_VERSION)
  } catch {
    // ignore storage failure；仍然放行本次会话
  }
  visible.value = false
  lockScroll(false)
}

function reject() {
  rejected.value = true
  // 脚本未打开的标签页通常无法被关闭，关不掉则停留在拦截页
  try {
    window.open('', '_self')
    window.close()
  } catch {
    // ignore
  }
}

function reread() {
  rejected.value = false
  scrolledToBottom.value = false
  agreed.value = false
  // 回到顶部重新阅读
  requestAnimationFrame(() => {
    if (bodyRef.value) bodyRef.value.scrollTop = 0
  })
}

onMounted(() => {
  if (!readAccepted()) {
    visible.value = true
    lockScroll(true)
    // 内容过短（无需滚动）时直接允许勾选
    requestAnimationFrame(() => {
      const el = bodyRef.value
      if (el && el.scrollHeight <= el.clientHeight + 32) scrolledToBottom.value = true
    })
  }
})

onBeforeUnmount(() => lockScroll(false))
</script>

<template>
  <div v-if="visible" class="site-gate" role="dialog" aria-modal="true">
    <!-- 拒绝后的拦截页 -->
    <div v-if="rejected" class="site-gate__panel site-gate__panel--rejected">
      <AlertTriangle :size="40" class="site-gate__reject-icon" />
      <h2 class="site-gate__reject-title">{{ SITE_GATE_REJECTED_TITLE }}</h2>
      <p class="site-gate__reject-desc">{{ SITE_GATE_REJECTED_DESC }}</p>
      <button type="button" class="site-gate__btn site-gate__btn--primary" @click="reread">
        {{ SITE_GATE_REREAD_BTN }}
      </button>
    </div>

    <!-- 阅读 + 确认 -->
    <div v-else class="site-gate__panel">
      <header class="site-gate__head">
        <ShieldCheck :size="22" class="site-gate__head-icon" />
        <div>
          <h2 class="site-gate__title">{{ SITE_GATE_TITLE }}</h2>
          <p class="site-gate__intro">{{ SITE_GATE_INTRO }}</p>
        </div>
      </header>

      <div ref="bodyRef" class="site-gate__body" @scroll.passive="onBodyScroll">
        <p class="site-gate__lead">{{ FULL_DISCLAIMER }}</p>
        <section v-for="sec in COMPLIANCE_SECTIONS" :key="sec.title" class="site-gate__section">
          <h3 class="site-gate__section-title">{{ sec.title }}</h3>
          <p v-for="(p, i) in sec.paragraphs" :key="i" class="site-gate__section-text">{{ p }}</p>
          <ul v-if="sec.bullets && sec.bullets.length" class="site-gate__bullets">
            <li v-for="(b, i) in sec.bullets" :key="i">{{ b }}</li>
          </ul>
        </section>
        <p class="site-gate__end">—— 以上为全部免责声明内容 ——</p>
      </div>

      <footer class="site-gate__foot">
        <p v-if="!scrolledToBottom" class="site-gate__hint">
          <AlertTriangle :size="14" /> {{ SITE_GATE_SCROLL_HINT }}
        </p>
        <label class="site-gate__agree" :class="{ disabled: !scrolledToBottom }">
          <input type="checkbox" :checked="agreed" :disabled="!scrolledToBottom" @change="agreed = !agreed" />
          <span>{{ SITE_GATE_AGREE_LABEL }}</span>
        </label>
        <div class="site-gate__actions">
          <button type="button" class="site-gate__btn site-gate__btn--ghost" @click="reject">
            {{ SITE_GATE_REJECT_BTN }}
          </button>
          <button
            type="button"
            class="site-gate__btn site-gate__btn--primary"
            :disabled="!scrolledToBottom || !agreed"
            @click="accept"
          >
            {{ SITE_GATE_AGREE_BTN }}
          </button>
        </div>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.site-gate {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(15, 23, 42, 0.62);
  backdrop-filter: blur(2px);
}
.site-gate__panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 560px;
  max-height: 88dvh;
  background: #fff;
  border-radius: 18px;
  overflow: hidden;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.32);
}
.site-gate__head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 18px 20px 12px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}
.site-gate__head-icon {
  flex-shrink: 0;
  color: #0f172a;
  margin-top: 2px;
}
.site-gate__title {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  color: #0f172a;
}
.site-gate__intro {
  margin: 4px 0 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: #64748b;
}
.site-gate__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  padding: 14px 20px;
}
.site-gate__lead {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.75;
  color: #334155;
  font-weight: 600;
}
.site-gate__section {
  margin-bottom: 12px;
}
.site-gate__section-title {
  margin: 0 0 4px;
  font-size: 13.5px;
  font-weight: 800;
  color: #0f172a;
}
.site-gate__section-text {
  margin: 0 0 4px;
  font-size: 12.5px;
  line-height: 1.7;
  color: #475569;
}
.site-gate__bullets {
  margin: 4px 0 0;
  padding-left: 18px;
}
.site-gate__bullets li {
  font-size: 12.5px;
  line-height: 1.7;
  color: #475569;
}
.site-gate__end {
  margin: 8px 0 0;
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
}
.site-gate__foot {
  padding: 12px 20px 16px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
  background: #f8fafc;
}
.site-gate__hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 8px;
  font-size: 12px;
  color: #b45309;
}
.site-gate__agree {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12.5px;
  line-height: 1.6;
  color: #0f172a;
  cursor: pointer;
}
.site-gate__agree.disabled {
  color: #94a3b8;
  cursor: not-allowed;
}
.site-gate__agree input {
  margin-top: 2px;
  flex-shrink: 0;
}
.site-gate__actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}
.site-gate__btn {
  flex: 1 1 0;
  min-height: 44px;
  padding: 0 12px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid transparent;
}
.site-gate__btn--ghost {
  background: #fff;
  border-color: rgba(15, 23, 42, 0.16);
  color: #475569;
}
.site-gate__btn--primary {
  background: #0f172a;
  color: #fff;
}
.site-gate__btn--primary:disabled {
  background: #cbd5e1;
  cursor: not-allowed;
}
.site-gate__panel--rejected {
  align-items: center;
  text-align: center;
  padding: 32px 24px;
  gap: 6px;
}
.site-gate__reject-icon {
  color: #dc2626;
}
.site-gate__reject-title {
  margin: 10px 0 0;
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}
.site-gate__reject-desc {
  margin: 8px 0 18px;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}
.site-gate__panel--rejected .site-gate__btn {
  flex: none;
  width: 100%;
  max-width: 260px;
}
@media (max-width: 400px) {
  .site-gate {
    padding: 8px;
    align-items: stretch;
  }
  .site-gate__panel {
    max-height: none;
    height: 100%;
    border-radius: 14px;
  }
  .site-gate__head {
    padding: 14px 16px 10px;
  }
  .site-gate__body {
    padding: 12px 16px;
  }
  .site-gate__foot {
    padding: 10px 16px calc(12px + env(safe-area-inset-bottom));
  }
  .site-gate__actions {
    flex-direction: column-reverse;
    gap: 8px;
  }
  .site-gate__actions .site-gate__btn {
    flex: none;
    width: 100%;
  }
}
</style>
