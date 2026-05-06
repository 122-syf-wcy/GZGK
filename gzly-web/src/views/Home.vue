<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { submitFeedback } from '@/api/feedback'
import { PROVINCE_LIST, type ProvinceConfig } from '@/constants/provinces'
import OnlineCounter from '@/components/OnlineCounter.vue'
import { showSuccessToast, showToast } from 'vant'
import {
  ArrowRight,
  CheckCircle2,
  Clock3,
  MessageSquare,
  ShieldAlert,
  User,
  X,
} from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

defineOptions({ name: 'Home' })

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const showFeedbackPopup = ref(false)
const feedbackContent = ref('')
const feedbackSubmitting = ref(false)

const feedbackLength = computed(() => feedbackContent.value.trim().length)
const openCount = computed(() => PROVINCE_LIST.filter(item => item.status === 'open').length)

function provinceRoute(province: ProvinceConfig): { path: string; query: ProvinceConfig['routeQuery'] } {
  return {
    path: `/region/${province.code}`,
    query: province.routeQuery,
  }
}

function openFeedbackPopup(): void {
  showFeedbackPopup.value = true
}

function closeFeedbackPopup(): void {
  if (feedbackSubmitting.value) return
  showFeedbackPopup.value = false
  feedbackContent.value = ''
}

async function submitFeedbackForm(): Promise<void> {
  const content = feedbackContent.value.trim()
  if (content.length < 10 || content.length > 500) {
    showToast('反馈内容需控制在 10-500 字')
    return
  }
  feedbackSubmitting.value = true
  try {
    await submitFeedback({
      content,
      sourcePage: route.path || '/',
    })
    showSuccessToast('反馈已收到，感谢你的建议')
    showFeedbackPopup.value = false
    feedbackContent.value = ''
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '提交失败，请稍后重试'
    showToast(message)
  } finally {
    feedbackSubmitting.value = false
  }
}
</script>

<template>
  <div class="home-page">
    <section class="home-hero">
      <div class="home-hero__inner">
        <div class="home-hero__top">
          <div class="home-hero__badge">
            <ShieldAlert :size="14" />
            公益高考志愿辅助
          </div>
          <div class="home-hero__top-right">
            <OnlineCounter class="home-hero__online" />
            <router-link
              v-if="authStore.isAuthenticated"
              to="/me/plans"
              class="auth-chip auth-chip--me"
              :aria-label="`我的志愿空间，剩余 ${authStore.remainPlans} 次`"
            >
              <User :size="13" />
              <span class="auth-chip__main">{{ authStore.profile.nickname || '我的空间' }}</span>
              <span class="auth-chip__count">剩 {{ authStore.remainPlans }} 次</span>
            </router-link>
            <router-link v-else to="/volunteer" class="auth-chip auth-chip--activate" aria-label="直接生成方案">
              <ArrowRight :size="13" /> 直接生成
            </router-link>
          </div>
        </div>
        <h1 class="home-hero__title">先选择地区，再进入对应工作台</h1>
        <p class="home-hero__desc">
          同一套页面结构、组件和流程；地区政策、志愿单位、数据状态和生成能力全部由地区配置隔离。
        </p>
        <div class="home-hero__metrics" aria-label="地区入口状态">
          <div class="home-hero__metric">
            <strong>{{ PROVINCE_LIST.length }}</strong>
            <span>个地区入口</span>
          </div>
          <div class="home-hero__metric">
            <strong>{{ openCount }}</strong>
            <span>个生成开放</span>
          </div>
          <div class="home-hero__metric">
            <strong>0</strong>
            <span>套重复页面</span>
          </div>
        </div>
      </div>
    </section>

    <main class="page-container home-main">
      <section class="province-grid" aria-label="地区选择">
        <router-link
          v-for="province in PROVINCE_LIST"
          :key="province.code"
          :to="provinceRoute(province)"
          class="gz-card province-card"
          :class="`is-${province.statusTone}`"
          :data-testid="`province-card-${province.code}`"
          :aria-label="`${province.name}专区，${province.statusLabel}`"
        >
          <span class="province-card__status">
            <CheckCircle2 v-if="province.status === 'open'" :size="15" />
            <Clock3 v-else :size="15" />
            {{ province.statusLabel }}
          </span>
          <span class="province-card__title">{{ province.name }}专区</span>
          <span class="province-card__desc">{{ province.heroDescription }}</span>
          <span class="province-card__facts">
            <span>{{ province.targetBatch }}</span>
            <span>{{ province.targetCount }} 个{{ province.volunteerUnit }}</span>
            <span>{{ province.officialSource }}</span>
          </span>
          <span class="province-card__action">
            {{ province.status === 'open' ? '进入工作台' : '查看准备状态' }}
            <ArrowRight :size="16" />
          </span>
        </router-link>
      </section>

      <section class="gz-card home-notice">
        <ShieldAlert :size="18" />
        <div>
          <h2>使用边界</h2>
          <p>系统仅提供公益辅助参考，不替代考试院、高校招生章程、招生专业目录和考生本人正式决策。</p>
        </div>
      </section>
    </main>

    <footer class="home-footer">
      <div class="page-container">
        <div class="footer-inner">
          <ShieldAlert :size="14" />
          <span>数据与建议仅供参考，请以对应省级考试院和高校官方信息为准</span>
          <span class="footer-link" @click="router.push('/disclaimer')">查看《免责声明》</span>
          <span class="footer-link footer-link--feedback" @click="openFeedbackPopup">
            <MessageSquare :size="13" />
            公测反馈
          </span>
        </div>
      </div>
    </footer>

    <van-popup
      v-model:show="showFeedbackPopup"
      position="bottom"
      round
      :close-on-click-overlay="!feedbackSubmitting"
      :style="{ maxHeight: '82%', background: '#fffdfa' }"
      @closed="feedbackContent = ''"
    >
      <div class="feedback-popup">
        <div class="feedback-popup__head">
          <div>
            <div class="feedback-popup__kicker">public beta</div>
            <h3 class="feedback-popup__title">公测意见反馈</h3>
            <p class="feedback-popup__desc">如果你发现问题、体验不顺或有建议，欢迎直接留言。</p>
          </div>
          <button class="feedback-popup__close" :disabled="feedbackSubmitting" @click="closeFeedbackPopup">
            <X :size="18" />
          </button>
        </div>

        <div class="feedback-popup__body">
          <textarea
            v-model="feedbackContent"
            class="feedback-popup__textarea"
            placeholder="例如：某个页面加载慢、某个筛选不顺手、某项数据看不明白，或你希望新增什么功能。"
            maxlength="500"
          />
          <div class="feedback-popup__meta">
            <span>仅支持匿名留言，管理员后台可查看。</span>
            <span :class="{ 'is-danger': feedbackLength > 500 || (feedbackLength > 0 && feedbackLength < 10) }">
              {{ feedbackLength }}/500
            </span>
          </div>
        </div>

        <div class="feedback-popup__actions">
          <button class="feedback-popup__btn feedback-popup__btn--ghost" :disabled="feedbackSubmitting" @click="closeFeedbackPopup">
            取消
          </button>
          <button class="feedback-popup__btn feedback-popup__btn--primary" :disabled="feedbackSubmitting" @click="submitFeedbackForm">
            {{ feedbackSubmitting ? '提交中…' : '提交反馈' }}
          </button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100dvh;
  overflow-x: hidden;
}

.home-hero {
  position: relative;
  padding: 64px 20px 52px;
  color: #ffffff;
  background:
    linear-gradient(135deg, rgba(15, 23, 42, 0.82) 0%, rgba(29, 78, 216, 0.7) 100%),
    url('/hero-bg.png') center/cover no-repeat;
}

.home-hero::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 48px;
  background: linear-gradient(180deg, rgba(245, 243, 238, 0), var(--gz-bg));
}

.home-hero__inner {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 880px;
  margin: 0 auto;
}

.home-hero__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.home-hero__top-right {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.auth-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  height: 28px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
  border: 1px solid rgba(255, 255, 255, 0.32);
  color: #fffdf7;
  background: rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(2px);
  transition: background 0.16s ease, border-color 0.16s ease;
}

.auth-chip:hover {
  background: rgba(255, 255, 255, 0.26);
}

.auth-chip--activate {
  background: rgba(255, 215, 130, 0.32);
  border-color: rgba(255, 215, 130, 0.4);
}

.auth-chip--me {
  background: rgba(255, 255, 255, 0.94);
  color: #1f2933;
  border-color: rgba(31, 41, 51, 0.18);
}

.auth-chip__count {
  margin-left: 4px;
  padding: 0 6px;
  border-radius: 999px;
  background: rgba(31, 41, 51, 0.08);
  color: #5f4630;
  font-size: 11px;
  font-weight: 800;
}

.home-hero__badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 999px;
  background: rgba(255, 253, 250, 0.14);
  font-size: 13px;
  font-weight: 700;
}

.home-hero__online {
  background: rgba(255, 255, 255, 0.92);
  color: #0f172a;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.18);
}

.home-hero__title {
  max-width: 760px;
  margin-top: 22px;
  font-size: clamp(34px, 8vw, 60px);
  line-height: 1.08;
  font-weight: 850;
  letter-spacing: -0.04em;
}

.home-hero__desc {
  max-width: 620px;
  margin-top: 16px;
  font-size: 15px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.88);
}

.home-hero__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  max-width: 560px;
  margin-top: 28px;
}

.home-hero__metric {
  min-width: 0;
  padding: 14px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.12);
}

.home-hero__metric strong {
  display: block;
  font-size: 26px;
  line-height: 1;
  font-weight: 850;
}

.home-hero__metric span {
  display: block;
  margin-top: 7px;
  font-size: 12px;
  line-height: 1.4;
  color: rgba(255, 255, 255, 0.82);
}

.home-main {
  padding-top: 10px;
}

.province-grid {
  display: grid;
  gap: 14px;
}

.province-card {
  display: flex;
  width: 100%;
  min-height: 260px;
  margin-bottom: 0;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
  color: inherit;
  text-decoration: none;
  cursor: pointer;
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.province-card:focus-visible {
  outline: 3px solid rgba(29, 78, 216, 0.32);
  outline-offset: 3px;
}

.province-card.is-preparing {
  background: linear-gradient(180deg, rgba(255, 253, 250, 0.98), rgba(255, 251, 235, 0.94));
}

.province-card__status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 12px;
  font-weight: 800;
}

.province-card.is-preparing .province-card__status {
  background: #fffbeb;
  color: #92400e;
}

.province-card__title {
  margin-top: 18px;
  font-size: 26px;
  line-height: 1.15;
  font-weight: 850;
  letter-spacing: -0.03em;
  color: #0f172a;
}

.province-card__desc {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.8;
  color: #475569;
}

.province-card__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.province-card__facts span {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.province-card__action {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  margin-top: auto;
  padding: 0 16px;
  border-radius: 12px;
  background: #0f172a;
  color: #ffffff;
  font-size: 14px;
  font-weight: 800;
}

.home-notice {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-top: 14px;
}

.home-notice svg {
  flex-shrink: 0;
  margin-top: 3px;
  color: #92400e;
}

.home-notice h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.25;
  color: #0f172a;
}

.home-notice p {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.75;
  color: #64748b;
}

.home-footer {
  padding-bottom: var(--gz-space-6);
}

.footer-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--gz-text-tertiary);
}

.footer-link {
  color: var(--gz-primary);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.footer-link--feedback {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.feedback-popup {
  padding: 22px 18px calc(env(safe-area-inset-bottom, 0px) + 18px);
}

.feedback-popup__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.feedback-popup__kicker {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: #1d4ed8;
  text-transform: uppercase;
}

.feedback-popup__title {
  margin-top: 6px;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 800;
  color: #0f172a;
}

.feedback-popup__desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.feedback-popup__close {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: #f8fafc;
  color: #475569;
  flex-shrink: 0;
}

.feedback-popup__body {
  margin-top: 18px;
}

.feedback-popup__textarea {
  width: 100%;
  min-height: 170px;
  padding: 14px 16px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 16px;
  background: #fff;
  font-size: 14px;
  line-height: 1.7;
  color: #0f172a;
  resize: vertical;
  outline: none;
}

.feedback-popup__textarea:focus {
  border-color: rgba(29, 78, 216, 0.35);
  box-shadow: 0 0 0 3px rgba(29, 78, 216, 0.08);
}

.feedback-popup__meta {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: #94a3b8;
}

.feedback-popup__meta .is-danger {
  color: #dc2626;
}

.feedback-popup__actions {
  margin-top: 18px;
  display: flex;
  gap: 10px;
}

.feedback-popup__btn {
  flex: 1;
  min-height: 44px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 800;
  border: none;
}

.feedback-popup__btn--ghost {
  background: #f8fafc;
  color: #334155;
}

.feedback-popup__btn--primary {
  background: #0f172a;
  color: #fff;
}

@media (max-width: 430px) {
  .home-hero {
    padding: 52px 16px 44px;
  }

  .home-hero__metrics {
    grid-template-columns: 1fr;
  }

  .province-card {
    min-height: 0;
  }
}

@media (min-width: 768px) {
  .home-hero {
    padding: 84px 48px 76px;
  }

  .province-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 18px;
  }

  .province-card {
    padding: 28px;
  }
}
</style>
