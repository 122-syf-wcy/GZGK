<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { submitFeedback } from '@/api/feedback'
import { PROVINCE_LIST, type ProvinceConfig } from '@/constants/provinces'
import OnlineCounter from '@/components/OnlineCounter.vue'
import OnboardingGuide from '@/components/OnboardingGuide.vue'
import { showSuccessToast, showToast } from 'vant'
import {
  ArrowRight,
  BookOpenCheck,
  Building2,
  CheckCircle2,
  Clock3,
  FolderOpen,
  GraduationCap,
  HeartHandshake,
  MessageSquare,
  ShieldAlert,
  Table2,
  X,
} from 'lucide-vue-next'

defineOptions({ name: 'Home' })

const router = useRouter()
const route = useRoute()
const showFeedbackPopup = ref(false)
const feedbackContent = ref('')
const feedbackSubmitting = ref(false)

const feedbackLength = computed(() => feedbackContent.value.trim().length)
const openCount = computed(() => PROVINCE_LIST.filter(item => item.status === 'open').length)
const moduleLinks = [
  { label: 'AI 志愿', path: '/volunteer', icon: GraduationCap },
  { label: '院校查询', path: '/university', icon: Building2 },
  { label: '分数线查询', path: '/score-line', icon: Table2 },
  { label: '特长生专区', path: '/special-admissions', icon: BookOpenCheck },
] as const

/** 首页核心能力导航：AI 志愿依赖省份上下文，点击滚动到省份选择区。img 缺失时回退线性图标。 */
type FeatureLink = {
  key: string
  label: string
  desc: string
  icon: typeof GraduationCap
  img: string
  path?: string
}

const featureLinks: FeatureLink[] = [
  { key: 'volunteer', label: 'AI 志愿', desc: '96/45 平行志愿草稿', icon: GraduationCap, img: '/icons/volunteer.png' },
  { key: 'university', label: '院校查询', desc: '2198 所院校档案', icon: Building2, img: '/icons/university.png', path: '/university' },
  { key: 'scoreline', label: '分数线', desc: '历年投档位次', icon: Table2, img: '/icons/scoreline.png', path: '/score-line' },
  { key: 'special', label: '特长生', desc: '强基/艺体/专项', icon: BookOpenCheck, img: '/icons/special.png', path: '/special-admissions' },
  { key: 'encourage', label: '加油墙', desc: '考生互相打气', icon: HeartHandshake, img: '/icons/encourage.png', path: '/encouragement' },
  { key: 'myplans', label: '我的方案', desc: '找回历史志愿表', icon: FolderOpen, img: '/icons/myplans.png', path: '/my-plans' },
]

function onFeatureClick(item: FeatureLink): void {
  if (item.path) {
    router.push(item.path)
    return
  }
  document.querySelector('.province-grid')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function provinceRoute(province: ProvinceConfig): { path: string; query: ProvinceConfig['routeQuery'] } {
  return {
    path: `/region/${province.code}`,
    query: province.routeQuery,
  }
}

function moduleRoute(province: ProvinceConfig, path: string): { path: string; query: ProvinceConfig['routeQuery'] } {
  return {
    path,
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
    <OnboardingGuide />
    <section class="home-hero">
      <div class="home-hero__inner">
        <nav class="home-hero__nav">
          <span class="home-hero__wordmark">志愿辅助</span>
          <div class="home-hero__nav-actions">
            <OnlineCounter class="home-hero__online" />
            <button
              class="home-hero__nav-btn"
              type="button"
              data-testid="home-my-plans-entry"
              @click="router.push('/my-plans')"
            >
              我的方案
            </button>
          </div>
        </nav>

        <div class="home-hero__center">
          <span class="home-hero__eyebrow">公益 · 免费 · 不代替官方</span>
          <h1 class="home-hero__title">把每一分，<br />都填在最合适的地方。</h1>
          <p class="home-hero__desc">
            基于官方一分一段与历年投档数据，为新高考考生整理 96/45 个平行志愿参考草稿。
          </p>
          <div class="home-hero__cta-row">
            <button type="button" class="home-hero__cta" @click="onFeatureClick(featureLinks[0])">
              选择省份，开始填报
              <ArrowRight :size="15" />
            </button>
          </div>
        </div>

        <div class="home-hero__metrics" aria-label="地区入口状态">
          <div class="home-hero__metric">
            <strong>{{ PROVINCE_LIST.length }}</strong>
            <span>个地区入口</span>
          </div>
          <div class="home-hero__metric">
            <strong>{{ openCount }}</strong>
            <span>个工作台开放</span>
          </div>
          <div class="home-hero__metric">
            <strong>2198</strong>
            <span>所院校档案</span>
          </div>
        </div>
      </div>
    </section>

    <main class="page-container home-main">
      <section class="feature-grid" aria-label="核心功能">
        <button
          v-for="feature in featureLinks"
          :key="feature.key"
          type="button"
          class="feature-card"
          :data-testid="`home-feature-${feature.key}`"
          @click="onFeatureClick(feature)"
        >
          <span class="feature-card__icon" :class="{ 'has-img': feature.img }">
            <img v-if="feature.img" :src="feature.img" :alt="''" loading="lazy" />
            <component :is="feature.icon" v-else :size="21" :stroke-width="1.6" />
          </span>
          <strong class="feature-card__label">{{ feature.label }}</strong>
          <span class="feature-card__desc">{{ feature.desc }}</span>
        </button>
      </section>

      <section class="province-grid" aria-label="地区选择">
        <article
          v-for="province in PROVINCE_LIST"
          :key="province.code"
          class="gz-card province-card"
          :class="`is-${province.statusTone}`"
          :data-testid="`province-card-${province.code}`"
          :aria-label="`${province.name}专区，${province.statusLabel}`"
          role="button"
          tabindex="0"
          @click="router.push(provinceRoute(province))"
          @keyup.enter="router.push(provinceRoute(province))"
          @keyup.space.prevent="router.push(provinceRoute(province))"
        >
          <span class="province-card__status">
            <CheckCircle2 v-if="province.status === 'open'" :size="15" />
            <Clock3 v-else :size="15" />
            {{ province.statusLabel }}
          </span>
          <span class="province-card__data-status" :class="`is-${province.scorelineStatusTone}`">
            {{ province.scorelineStatusLabel }}
          </span>
          <span class="province-card__title">{{ province.name }}专区</span>
          <span class="province-card__desc">{{ province.scorelineSummary }}</span>
          <span class="province-card__facts">
            <span>{{ province.targetBatch }}</span>
            <span>{{ province.targetCount }} 个{{ province.volunteerUnit }}</span>
            <span>{{ province.officialSource }}</span>
          </span>
          <span class="province-card__modules" @click.stop>
            <router-link
              v-for="module in moduleLinks"
              :key="module.label"
              :to="moduleRoute(province, module.path)"
              class="province-card__module"
              :data-testid="`province-card-${province.code}-${module.label}`"
            >
              <component :is="module.icon" :size="13" :stroke-width="1.8" />
              {{ module.label }}
            </router-link>
          </span>
          <span class="province-card__action">
            进入工作台
            <ArrowRight :size="16" />
          </span>
        </article>
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
      :style="{ maxHeight: '82%', background: '#ffffff' }"
      @closed="feedbackContent = ''"
    >
      <div class="feedback-popup">
        <div class="feedback-popup__head">
          <div>
            <div class="feedback-popup__kicker">公测期</div>
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
  display: flex;
  min-height: 72vh;
  padding: 14px 18px 46px;
  color: #ffffff;
  background:
    linear-gradient(180deg, rgba(12, 16, 14, 0.38) 0%, rgba(12, 16, 14, 0.06) 30%, rgba(12, 16, 14, 0.1) 62%, rgba(12, 16, 14, 0.52) 100%),
    url('/icons/hero-landscape.png') center 58% / cover no-repeat #29302b;
}

.home-hero__inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
}

.home-hero__nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.home-hero__wordmark {
  font-family: var(--gz-font-display);
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-shadow: 0 1px 12px rgba(12, 16, 14, 0.4);
}

.home-hero__nav-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.home-hero__online {
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.28);
  color: #fff;
  backdrop-filter: blur(8px);
}

.home-hero__nav-btn {
  min-height: 34px;
  padding: 0 16px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(8px);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}

.home-hero__nav-btn:hover,
.home-hero__nav-btn:focus-visible {
  background: rgba(255, 255, 255, 0.24);
}

.home-hero__center {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  margin: auto 0;
  padding: 64px 0 48px;
}

.home-hero__eyebrow {
  padding: 6px 14px;
  border: 1px solid rgba(255, 255, 255, 0.36);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(8px);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.2em;
}

.home-hero__title {
  margin-top: 22px;
  font-family: var(--gz-font-display);
  font-size: clamp(32px, 7vw, 58px);
  line-height: 1.22;
  font-weight: 700;
  letter-spacing: 0.02em;
  text-shadow: 0 2px 22px rgba(12, 16, 14, 0.45);
}

.home-hero__desc {
  max-width: 520px;
  margin-top: 16px;
  font-size: 14px;
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.92);
  text-shadow: 0 1px 10px rgba(12, 16, 14, 0.4);
}

.home-hero__cta-row {
  margin-top: 26px;
}

.home-hero__cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 46px;
  padding: 0 26px;
  border: none;
  border-radius: 999px;
  background: #ffffff;
  color: var(--gz-ink);
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 10px 28px rgba(12, 16, 14, 0.32);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.home-hero__cta:hover,
.home-hero__cta:focus-visible {
  transform: translateY(-2px);
  box-shadow: 0 14px 34px rgba(12, 16, 14, 0.4);
}

.home-hero__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  max-width: 520px;
  width: 100%;
  margin: 0 auto;
}

.home-hero__metric {
  min-width: 0;
  padding: 12px 10px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 14px;
  background: rgba(12, 16, 14, 0.28);
  backdrop-filter: blur(10px);
  text-align: center;
}

.home-hero__metric strong {
  display: block;
  font-size: 22px;
  line-height: 1;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.home-hero__metric span {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.4;
  color: rgba(255, 255, 255, 0.8);
  letter-spacing: 0.06em;
}

.home-main {
  padding-top: 10px;
}

/* ---- 核心功能导航：黑白细线卡 ---- */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 24px;
}

.feature-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  padding: 16px 8px 14px;
  border: 1px solid var(--gz-card-border);
  border-radius: var(--gz-radius-md);
  background: var(--gz-card-bg);
  box-shadow: var(--gz-card-shadow);
  cursor: pointer;
  transition: transform var(--gz-transition-normal), box-shadow var(--gz-transition-normal), border-color var(--gz-transition-normal);
}

.feature-card:hover,
.feature-card:focus-visible {
  transform: translateY(-2px);
  border-color: rgba(23, 24, 28, 0.32);
  box-shadow: var(--gz-card-shadow-hover);
}

.feature-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: 1px solid rgba(23, 24, 28, 0.14);
  border-radius: 999px;
  background: var(--gz-bg-subtle);
  color: var(--gz-ink);
  overflow: hidden;
}

.feature-card__icon.has-img {
  width: 56px;
  height: 56px;
  border: none;
  background: transparent;
}

.feature-card__icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 999px;
  transform: scale(1.02);
}

.feature-card__label {
  font-size: 13px;
  font-weight: 700;
  color: var(--gz-text-primary);
}

.feature-card__desc {
  font-size: 10px;
  line-height: 1.4;
  color: var(--gz-text-tertiary);
  text-align: center;
}

@media (min-width: 768px) {
  .feature-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: 12px;
  }

  .feature-card__label {
    font-size: 14px;
  }

  .feature-card__desc {
    font-size: 11px;
  }
}

.province-grid {
  display: grid;
  gap: 14px;
}

.province-card {
  display: flex;
  width: 100%;
  min-height: 238px;
  margin-bottom: 0;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
  color: inherit;
  text-decoration: none;
  cursor: pointer;
  border: 1px solid rgba(23, 24, 28, 0.08);
}

.province-card:focus-visible {
  outline: 3px solid rgba(23, 24, 28, 0.3);
  outline-offset: 3px;
}

.province-card.is-preparing {
  background: var(--gz-bg-subtle);
}

.province-card__status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding: 0 11px;
  border-radius: 999px;
  border: 1px solid rgba(23, 24, 28, 0.12);
  background: #ffffff;
  color: var(--gz-ink-soft);
  font-size: 12px;
  font-weight: 600;
}

.province-card__status svg {
  color: var(--gz-success);
}

.province-card__data-status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 28px;
  margin-top: 10px;
  padding: 0 11px;
  border-radius: 999px;
  border: 1px solid rgba(23, 24, 28, 0.1);
  background: var(--gz-bg-subtle);
  color: var(--gz-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.province-card__data-status::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--gz-text-tertiary);
}

.province-card__data-status.is-green::before,
.province-card__data-status.is-blue::before {
  background: var(--gz-success);
}

.province-card__data-status.is-amber::before,
.province-card__data-status.is-red::before {
  background: var(--gz-warning-yellow);
}

.province-card.is-preparing .province-card__status svg {
  color: var(--gz-warning-yellow);
}

.province-card__title {
  margin-top: 14px;
  font-family: var(--gz-font-display);
  font-size: 24px;
  line-height: 1.15;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--gz-ink);
}

.province-card__desc {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.65;
  color: #4b4d54;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
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
  border: 1px solid rgba(23, 24, 28, 0.08);
  background: #fafaf8;
  color: #4b4d54;
  font-size: 12px;
  font-weight: 700;
}

.province-card__modules {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
  margin-top: 18px;
}

.province-card__module {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 38px;
  padding: 0 10px;
  border: 1px solid var(--gz-card-border);
  border-radius: 999px;
  background: #ffffff;
  color: var(--gz-ink-soft);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.province-card__module:hover,
.province-card__module:focus-visible {
  border-color: rgba(23, 24, 28, 0.4);
  color: var(--gz-ink);
  outline: none;
}

.province-card__action {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  margin-top: auto;
  padding: 0 16px;
  border-radius: 12px;
  background: #17181c;
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
  color: #7c5f33;
}

.home-notice h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.25;
  color: #17181c;
}

.home-notice p {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.75;
  color: #6a6c72;
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
  color: var(--gz-text-tertiary);
  text-transform: uppercase;
}

.feedback-popup__title {
  margin-top: 6px;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 800;
  color: #17181c;
}

.feedback-popup__desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: #6a6c72;
}

.feedback-popup__close {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: #fafaf8;
  color: #4b4d54;
  flex-shrink: 0;
}

.feedback-popup__body {
  margin-top: 18px;
}

.feedback-popup__textarea {
  width: 100%;
  min-height: 170px;
  padding: 14px 16px;
  border: 1px solid rgba(23, 24, 28, 0.12);
  border-radius: 16px;
  background: #fff;
  font-size: 14px;
  line-height: 1.7;
  color: #17181c;
  resize: vertical;
  outline: none;
}

.feedback-popup__textarea:focus {
  border-color: rgba(23, 24, 28, 0.4);
  box-shadow: 0 0 0 3px rgba(23, 24, 28, 0.06);
}

.feedback-popup__meta {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: #97999e;
}

.feedback-popup__meta .is-danger {
  color: #b34040;
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
  background: #fafaf8;
  color: #383a40;
}

.feedback-popup__btn--primary {
  background: #17181c;
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

  .province-card__modules {
    grid-template-columns: 1fr;
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

@media (min-width: 1180px) {
  .province-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .province-card {
    padding: 20px;
  }
}
</style>
