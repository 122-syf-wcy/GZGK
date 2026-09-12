<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchEncouragementMessages, submitEncouragementMessage } from '@/api/encouragement'
import type { EncouragementMessage } from '@/types'
import { showSuccessToast, showToast } from 'vant'
import { ArrowLeft, MessageSquare, RefreshCw, Send, ShieldCheck } from 'lucide-vue-next'

defineOptions({ name: 'EncouragementWall' })

const router = useRouter()
const messages = ref<EncouragementMessage[]>([])
const nickname = ref('')
const content = ref('')
const loading = ref(true)
const submitting = ref(false)
const loadError = ref('')

const contentLength = computed(() => content.value.trim().length)
const carouselMessages = computed(() => (messages.value.length ? [...messages.value, ...messages.value] : []))

onMounted(loadMessages)

async function loadMessages() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await fetchEncouragementMessages(24)
    messages.value = res.data?.data || []
  } catch (error: any) {
    messages.value = []
    loadError.value = error?.message || '留言加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function submitMessage() {
  const text = content.value.trim()
  if (text.length < 4 || text.length > 120) {
    showToast('留言需控制在 4-120 字')
    return
  }
  submitting.value = true
  try {
    const res = await submitEncouragementMessage({
      nickname: nickname.value.trim(),
      content: text,
    })
    const saved = res.data?.data
    if (saved) {
      messages.value = [saved, ...messages.value].slice(0, 24)
    } else {
      await loadMessages()
    }
    content.value = ''
    showSuccessToast('已发布，祝大家都一路顺利')
  } catch (error: any) {
    showToast(error?.message || '留言失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="wall-page">
    <header class="wall-header">
      <button class="back-btn" @click="router.back()">
        <ArrowLeft :size="19" />
      </button>
      <div>
        <h1>考生加油墙</h1>
        <p>给正在填志愿的人留一句稳定心态的话</p>
      </div>
    </header>

    <main class="wall-main">
      <section class="hero-shell">
        <div class="hero-copy">
          <span class="wall-kicker">写给每一位考生</span>
          <h2>把焦虑放轻一点，把祝福留给正在选择未来的人。</h2>
          <p>这里不做预测，不制造承诺，只收集来自同路人的一句稳定心态。最终填报仍以考试院、一分一段表和学校招生章程为准。</p>
          <div class="hero-metrics" aria-label="留言墙状态">
            <div>
              <strong>{{ messages.length || 0 }}</strong>
              <span>条最新鼓励</span>
            </div>
            <div>
              <strong>120</strong>
              <span>字以内，更容易被读完</span>
            </div>
          </div>
        </div>

        <section class="compose-card" aria-labelledby="compose-title">
          <div class="compose-title" id="compose-title">
            <ShieldCheck :size="18" />
            <span>写给考生的一句话</span>
          </div>
          <div class="compose-grid">
            <label>
              <span>昵称</span>
              <input v-model="nickname" maxlength="12" placeholder="可不填，默认贵州考生" />
            </label>
            <label>
              <span>鼓励内容</span>
              <textarea
                v-model="content"
                maxlength="120"
                placeholder="例如：稳住心态，按自己的节奏走。每一步都算数。"
              ></textarea>
            </label>
          </div>
          <div class="compose-actions">
            <span :class="{ danger: contentLength > 120 || (contentLength > 0 && contentLength < 4) }">{{ contentLength }}/120</span>
            <button :disabled="submitting" @click="submitMessage">
              <Send :size="15" />
              {{ submitting ? '发布中...' : '发布鼓励' }}
            </button>
          </div>
        </section>
      </section>

      <section class="message-board">
        <div class="section-head">
          <div>
            <span class="section-kicker">留言播放中</span>
            <h3>让鼓励自己往前走</h3>
          </div>
          <button :disabled="loading" @click="loadMessages">
            <RefreshCw :size="15" />
            刷新
          </button>
        </div>
        <div v-if="loading" class="empty-card">正在加载留言...</div>
        <div v-else-if="loadError" class="empty-card empty-card--error">{{ loadError }}</div>
        <div v-else-if="!messages.length" class="empty-card">还没有留言，写下第一句鼓励吧。</div>
        <div v-else class="marquee" aria-label="最新鼓励留言横向播放">
          <div class="marquee-track">
            <article
              v-for="(message, index) in carouselMessages"
              :key="`${message.id}-${index}`"
              class="message-card"
            >
              <span class="message-index">{{ String((index % messages.length) + 1).padStart(2, '0') }}</span>
              <p>{{ message.content }}</p>
              <footer>
                <span>{{ message.nickname || '贵州考生' }}</span>
                <time>{{ message.createdAt?.slice(5, 16).replace('T', ' ') }}</time>
              </footer>
            </article>
          </div>
          <div class="board-hint">
            <MessageSquare :size="16" />
            <span>悬停或触摸可暂停阅读</span>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.wall-page {
  min-height: 100dvh;
  background: var(--gz-bg);
  color: #17181c;
}

.wall-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
  background: rgba(255, 253, 250, 0.9);
  border-bottom: 1px solid rgba(23, 24, 28, 0.08);
  backdrop-filter: blur(12px);
}

.back-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 1px solid #e3e2de;
  border-radius: 14px;
  background: #fff;
  color: #17181c;
  cursor: pointer;
}

.wall-header h1 {
  font-size: 17px;
  font-weight: 800;
}

.wall-header p {
  color: #6a6c72;
  font-size: 12px;
}

.wall-main {
  max-width: 1180px;
  margin: 0 auto;
  padding: 18px 16px calc(env(safe-area-inset-bottom, 0px) + 48px);
}

.hero-shell,
.compose-card,
.featured-card,
.message-card,
.empty-card {
  border: 1px solid rgba(23, 24, 28, 0.08);
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 18px 36px rgba(23, 24, 28, 0.06);
}

.hero-shell {
  display: grid;
  gap: 14px;
  padding: 14px;
  border-radius: 24px;
  overflow: hidden;
  position: relative;
}

.hero-shell::before {
  content: "";
  display: none;
  position: absolute;
  inset: auto -12% -44% 45%;
  height: 280px;
  border-radius: 999px;
  background: transparent;
  filter: none;
  pointer-events: none;
}

.hero-copy,
.compose-card {
  position: relative;
  z-index: 1;
}

.hero-copy {
  padding: 4px 4px 0;
}

.wall-kicker {
  display: inline-flex;
  width: fit-content;
  padding: 6px 12px;
  border: 1px solid rgba(23, 24, 28, 0.16);
  border-radius: 999px;
  background: #ffffff;
  color: #4b4d54;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.18em;
}

.hero-copy h2 {
  margin-top: 14px;
  max-width: 16ch;
  font-family: var(--gz-font-display);
  font-size: clamp(28px, 6vw, 46px);
  line-height: 1.28;
  letter-spacing: 0.01em;
  font-weight: 700;
}

.hero-copy p {
  margin-top: 12px;
  max-width: 58ch;
  color: #4b4d54;
  font-size: 14px;
  line-height: 1.72;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  max-width: 520px;
  margin-top: 14px;
}

.hero-metrics div {
  min-height: 68px;
  padding: 11px 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(23, 24, 28, 0.08);
}

.hero-metrics strong {
  display: block;
  color: #17181c;
  font-size: 26px;
  line-height: 1;
}

.hero-metrics span {
  display: block;
  margin-top: 8px;
  color: #6a6c72;
  font-size: 12px;
  line-height: 1.45;
}

.compose-card {
  padding: 16px;
  border: 1px solid rgba(23, 24, 28, 0.1);
  border-radius: 18px;
  background: #ffffff;
  color: var(--gz-ink);
  box-shadow: 0 1px 2px rgba(23, 24, 28, 0.04);
}

.compose-title,
.compose-actions,
.section-head,
.message-card footer {
  display: flex;
  align-items: center;
}

.compose-title {
  gap: 8px;
  font-weight: 800;
}

.compose-grid {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.compose-grid label {
  display: grid;
  gap: 7px;
}

.compose-grid label span {
  color: #6a6c72;
  font-size: 12px;
  font-weight: 700;
}

.compose-grid input,
.compose-grid textarea {
  width: 100%;
  border: 1px solid rgba(23, 24, 28, 0.14);
  border-radius: 14px;
  background: var(--gz-bg-subtle);
  color: var(--gz-ink);
  font-size: 14px;
  outline: none;
}

.compose-grid input::placeholder,
.compose-grid textarea::placeholder {
  color: #97999e;
}

.compose-grid input:focus,
.compose-grid textarea:focus {
  border-color: rgba(23, 24, 28, 0.45);
  box-shadow: 0 0 0 3px rgba(23, 24, 28, 0.06);
}

.compose-grid input {
  height: 44px;
  padding: 0 14px;
}

.compose-grid textarea {
  min-height: 84px;
  padding: 13px 14px;
  line-height: 1.65;
  resize: vertical;
}

.compose-actions {
  justify-content: space-between;
  margin-top: 12px;
  color: #8e9097;
  font-size: 12px;
}

.compose-actions .danger {
  color: #fca5a5;
}

.compose-actions button,
.section-head button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 40px;
  padding: 0 18px;
  border: 0;
  border-radius: 999px;
  background: var(--gz-ink);
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.compose-actions button:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.message-board {
  margin-top: 20px;
}

.section-head {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-head h3 {
  margin-top: 3px;
  font-size: 20px;
  font-weight: 900;
  letter-spacing: -0.03em;
}

.section-kicker {
  color: #17181c;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.12em;
}

.section-head button {
  min-height: 34px;
  background: #fff;
  color: #17181c;
  border: 1px solid #d9d8d3;
}

.marquee {
  position: relative;
  overflow: hidden;
  border-radius: 22px;
  border: 1px solid rgba(23, 24, 28, 0.08);
  background:
    linear-gradient(90deg, rgba(23, 24, 28, 0.04), transparent 18%, transparent 82%, rgba(23, 24, 28, 0.04)),
    rgba(255, 255, 255, 0.78);
  box-shadow: 0 18px 36px rgba(23, 24, 28, 0.06);
}

.marquee::before,
.marquee::after {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  z-index: 2;
  width: 72px;
  pointer-events: none;
}

.marquee::before {
  left: 0;
  background: linear-gradient(90deg, rgba(248, 250, 252, 0.98), rgba(248, 250, 252, 0));
}

.marquee::after {
  right: 0;
  background: linear-gradient(270deg, rgba(248, 250, 252, 0.98), rgba(248, 250, 252, 0));
}

.marquee-track {
  display: flex;
  gap: 10px;
  width: max-content;
  padding: 14px;
  animation: wall-marquee 46s linear infinite;
}

.marquee:hover .marquee-track,
.marquee:focus-within .marquee-track {
  animation-play-state: paused;
}

.message-card,
.empty-card {
  border-radius: 16px;
  padding: 13px;
}

.message-card {
  width: clamp(220px, 26vw, 310px);
  min-height: 136px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: #fff;
}

.message-index {
  color: #17181c;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.12em;
}

.message-card p {
  margin-top: 9px;
  color: #17181c;
  font-size: 14px;
  line-height: 1.62;
  font-weight: 720;
  letter-spacing: -0.02em;
}

.message-card footer {
  justify-content: space-between;
  gap: 12px;
  display: flex;
  align-items: center;
  color: #97999e;
  font-size: 12px;
}

.message-card footer {
  margin-top: 12px;
}

.board-hint {
  position: absolute;
  right: 14px;
  bottom: 12px;
  z-index: 3;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(23, 24, 28, 0.78);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  backdrop-filter: blur(8px);
}

.empty-card {
  color: #6a6c72;
  text-align: center;
}

@keyframes wall-marquee {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-50%);
  }
}

@media (min-width: 768px) {
  .wall-main {
    padding: 34px 24px 70px;
  }

  .hero-shell {
    grid-template-columns: minmax(0, 1fr) 340px;
    align-items: stretch;
    padding: 26px;
  }

  .compose-card {
    padding: 20px;
    align-self: center;
  }

  .compose-grid {
    gap: 14px;
  }
}

@media (max-width: 520px) {
  .hero-metrics {
    grid-template-columns: 1fr;
  }

  .section-head {
    align-items: flex-start;
  }

  .marquee::before,
  .marquee::after {
    width: 32px;
  }

  .marquee-track {
    padding: 12px;
  }

  .board-hint {
    position: static;
    width: fit-content;
    margin: 0 16px 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .marquee {
    overflow-x: auto;
  }

  .marquee-track {
    animation: none;
  }
}
</style>
