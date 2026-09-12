<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  BadgeCheck,
  CalendarDays,
  ChevronRight,
  ExternalLink,
  GraduationCap,
  Loader2,
  Medal,
  ShieldCheck,
} from 'lucide-vue-next'
import {
  fetchSpecialAdmissionCategories,
  fetchSpecialAdmissionPolicies,
} from '@/api/specialAdmission'
import { getProvinceConfig, normalizeProvinceCode } from '@/constants/provinces'
import type { SpecialAdmissionCategory, SpecialAdmissionPolicy } from '@/types'
import { renderMarkdown, sanitizeHttpUrl } from '@/utils/markdown'
import { showToast } from 'vant'

defineOptions({ name: 'SpecialAdmissions' })

const router = useRouter()
const route = useRoute()
const year = 2026
const loading = ref(false)
const categories = ref<SpecialAdmissionCategory[]>([])
const policies = ref<SpecialAdmissionPolicy[]>([])
const selectedCategory = ref('')
const expandedId = ref<number | null>(null)
const currentProvinceCode = computed(() => normalizeProvinceCode(route.query.provinceCode))
const currentProvince = computed(() => getProvinceConfig(currentProvinceCode.value))
const isPolicyQueryOnlyProvince = computed(() => currentProvinceCode.value !== 'GZ')

const selectedCategoryName = computed(() => {
  if (!selectedCategory.value) return '全部政策'
  return categories.value.find(item => item.category === selectedCategory.value)?.categoryName || '分类政策'
})

const policyCountText = computed(() => `${policies.value.length} 条官方政策线索`)
const focusPaths = computed(() => [
  { name: '艺术类', category: 'art', hint: '查询统考、校考、综合分规则和缺口，不套普通位次模型。' },
  { name: '体育类', category: 'sports', hint: '查询专业考试、综合分规则和历史政策，不生成普通志愿。' },
  { name: '专项/特殊类型', category: 'special_plan', hint: '核对户籍学籍、资格审核、强基综评和提前批要求。' },
  { name: '政策与规则', category: 'early_batch', hint: '优先看官方口径、数据缺口和后续需要的源文件。' },
])
const deadlinePolicies = computed(() => policies.value
  .filter(item => item.applyEnd || item.examTime)
  .slice(0, 3))
const expectedCoverage = [
  { category: 'application', name: '补报名与考试确认' },
  { category: 'art', name: '艺术类' },
  { category: 'sports', name: '体育类' },
  { category: 'special_plan', name: '专项计划' },
  { category: 'qualification', name: '加分与资格条件' },
  { category: 'early_batch', name: '强基综评等提前批特殊类型' },
  { category: 'military_police', name: '军队、公安、司法、消防招生' },
  { category: 'aviation', name: '飞行技术、民航招飞' },
]
const missingCoverage = computed(() => {
  const covered = new Set(categories.value.map(item => item.category))
  return expectedCoverage.filter(item => !covered.has(item.category))
})
const sourceStats = computed(() => {
  const officialCount = policies.value.filter(item => item.sourceType === 'official' || item.sourceType === 'wechat').length
  return {
    officialCount,
    reprintCount: Math.max(policies.value.length - officialCount, 0),
  }
})

function sourceLabel(type?: string) {
  if (type === 'official') return '官网原文'
  if (type === 'wechat') return '官方微信'
  return '权威转载'
}

function sourceClass(type?: string) {
  if (type === 'official') return 'source--official'
  if (type === 'wechat') return 'source--wechat'
  return 'source--reprint'
}

function formatDateRange(policy: SpecialAdmissionPolicy) {
  if (policy.applyStart && policy.applyEnd) {
    return `${policy.applyStart} 至 ${policy.applyEnd}`
  }
  if (policy.applyStart) return `${policy.applyStart} 起`
  if (policy.applyEnd) return `${policy.applyEnd} 截止`
  return policy.examTime || '以官方后续通知为准'
}

function togglePolicy(policy: SpecialAdmissionPolicy) {
  expandedId.value = expandedId.value === policy.id ? null : policy.id
}

function openOfficial(policy: SpecialAdmissionPolicy) {
  const url = sanitizeHttpUrl(policy.officialUrl)
  if (!url) {
    showToast('暂无可打开的官方链接')
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function loadCategories() {
  if (isPolicyQueryOnlyProvince.value) {
    categories.value = []
    return
  }
  const res = await fetchSpecialAdmissionCategories(year, currentProvinceCode.value)
  categories.value = res.data?.data || []
}

async function loadPolicies() {
  if (isPolicyQueryOnlyProvince.value) {
    policies.value = []
    expandedId.value = null
    return
  }
  loading.value = true
  try {
    const res = await fetchSpecialAdmissionPolicies({
      year,
      provinceCode: currentProvinceCode.value,
      category: selectedCategory.value || undefined,
      limit: 100,
    })
    policies.value = res.data?.data || []
    expandedId.value = policies.value[0]?.id || null
  } catch (error: any) {
    policies.value = []
    showToast(error?.message || '政策数据加载失败')
  } finally {
    loading.value = false
  }
}

async function selectCategory(category: string) {
  if (isPolicyQueryOnlyProvince.value) {
    showToast(`${currentProvince.value.shortName}特长生专区当前为只查策略，先核对官方入口和历史规则`)
    return
  }
  if (selectedCategory.value === category) return
  selectedCategory.value = category
  await loadPolicies()
}

async function loadForCurrentProvince() {
  try {
    selectedCategory.value = ''
    await Promise.all([loadCategories(), loadPolicies()])
  } catch (error: any) {
    showToast(error?.message || '特殊类型招生数据加载失败')
  }
}

onMounted(loadForCurrentProvince)

watch(() => route.query.provinceCode, () => {
  void loadForCurrentProvince()
})
</script>

<template>
  <div class="special-page">
    <header class="special-hero">
      <button class="back-btn" aria-label="返回地区工作台" @click="router.push({ path: `/region/${currentProvince.code}`, query: currentProvince.routeQuery })">
        <ArrowLeft :size="18" />
      </button>
      <div class="special-hero__content">
        <div class="eyebrow">
          <ShieldCheck :size="15" />
          {{ currentProvince.name }} 2026 高考政策专区
        </div>
        <h1>特殊类型招生</h1>
        <p>
          {{ currentProvince.specialAdmissionsHint }}
        </p>
      </div>
      <div class="special-hero__wave">
        <svg viewBox="0 0 1440 80" preserveAspectRatio="none" aria-hidden="true">
          <path d="M0,40 C360,80 720,0 1080,40 C1260,60 1380,50 1440,40 L1440,80 L0,80 Z" fill="var(--gz-bg)" />
        </svg>
      </div>
    </header>

    <main class="page-container special-main">
      <section class="quick-panel gz-card">
        <div class="quick-panel__item">
          <span class="quick-num">{{ isPolicyQueryOnlyProvince ? '—' : categories.length || '—' }}</span>
          <span>政策分类</span>
        </div>
        <div class="quick-panel__item">
          <span class="quick-num">{{ isPolicyQueryOnlyProvince ? '—' : policies.length || '—' }}</span>
          <span>当前列表</span>
        </div>
        <div class="quick-panel__item">
          <span class="quick-num">{{ currentProvince.name }}</span>
          <span>{{ currentProvince.officialSource }}</span>
        </div>
      </section>

      <section v-if="isPolicyQueryOnlyProvince" class="sc-special-lock gz-card">
        <ShieldCheck :size="20" />
        <div>
          <h2>{{ currentProvince.shortName }}特长生专区当前为政策查询与缺口说明</h2>
          <p>
            当前{{ currentProvince.shortName }}专区已开放入口，展示艺术类、体育类、专项/特殊类型和政策规则四类入口；数据未完整时只展示官方数据待发布、政策查询和历史规则提醒，不套普通位次模型，也不生成普通志愿草稿。
          </p>
        </div>
      </section>

      <section v-if="isPolicyQueryOnlyProvince" class="focus-section gz-card">
        <div class="section-head">
          <div>
            <h2>专区入口</h2>
            <p>入口保持开放，当前以查询、政策和缺口为主，等待官方文件补齐后再开放结构化数据。</p>
          </div>
        </div>
        <div class="focus-grid">
          <div v-for="path in focusPaths" :key="path.category" class="focus-card focus-card--readonly">
            <Medal :size="18" />
            <strong>{{ path.name }}</strong>
            <span>{{ path.hint }}</span>
          </div>
        </div>
      </section>

      <section v-if="!isPolicyQueryOnlyProvince" class="focus-section gz-card">
        <div class="section-head">
          <div>
            <h2>重点路径先看这里</h2>
            <p>特长、专项和提前批类信息容易错过时间节点，建议先按身份路径核对。</p>
          </div>
        </div>
        <div class="focus-grid">
          <button
            v-for="path in focusPaths"
            :key="path.category"
            class="focus-card"
            @click="selectCategory(path.category)"
          >
            <Medal :size="18" />
            <strong>{{ path.name }}</strong>
            <span>{{ path.hint }}</span>
          </button>
        </div>
        <div v-if="deadlinePolicies.length" class="deadline-strip">
          <span class="deadline-title">近期需要盯住</span>
          <button
            v-for="policy in deadlinePolicies"
            :key="policy.id"
            class="deadline-chip"
            @click="togglePolicy(policy)"
          >
            {{ policy.title }} · {{ formatDateRange(policy) }}
          </button>
        </div>
      </section>

      <section v-if="!isPolicyQueryOnlyProvince && (missingCoverage.length || sourceStats.reprintCount)" class="coverage-section gz-card">
        <div class="section-head coverage-head">
          <div>
            <h2>数据补齐提示</h2>
            <p>当前页面优先展示已核验线索，以下类型仍建议继续补官方原文或院校简章。</p>
          </div>
        </div>
        <div class="coverage-summary">
          <span>官方/官微 {{ sourceStats.officialCount }} 条</span>
          <span>权威转载 {{ sourceStats.reprintCount }} 条</span>
        </div>
        <div v-if="missingCoverage.length" class="coverage-gap-list">
          <span v-for="item in missingCoverage" :key="item.category" class="coverage-gap">
            {{ item.name }}
          </span>
        </div>
      </section>

      <section v-if="!isPolicyQueryOnlyProvince" class="category-section">
        <div class="section-head">
          <div>
            <h2>按类型查看</h2>
            <p>选择考生符合的路径，先核对资格和时间。</p>
          </div>
        </div>
        <div class="category-scroll" aria-label="特殊类型招生分类">
          <button
            class="category-chip"
            :class="{ active: selectedCategory === '' }"
            @click="selectCategory('')"
          >
            全部
          </button>
          <button
            v-for="item in categories"
            :key="item.category"
            class="category-chip"
            :class="{ active: selectedCategory === item.category }"
            @click="selectCategory(item.category)"
          >
            {{ item.categoryName }}
            <span>{{ item.count }}</span>
          </button>
        </div>
      </section>

      <section v-if="!isPolicyQueryOnlyProvince" class="policy-section">
        <div class="section-head">
          <div>
            <h2>{{ selectedCategoryName }}</h2>
            <p>{{ policyCountText }}</p>
          </div>
          <div class="verified-mark">
            <BadgeCheck :size="16" />
            官方口径优先
          </div>
        </div>

        <div v-if="loading" class="state-card gz-card">
          <Loader2 :size="18" class="spin" />
          正在加载政策数据…
        </div>

        <div v-else-if="policies.length === 0" class="state-card gz-card">
          <GraduationCap :size="18" />
          <span>暂无该分类政策，后续会随官方通知持续补齐。</span>
          <button v-if="selectedCategory" class="state-link" @click="selectCategory('')">返回全部政策</button>
        </div>

        <div v-else class="policy-list">
          <article
            v-for="policy in policies"
            :key="policy.id"
            class="policy-card gz-card"
          >
            <button class="policy-card__head" @click="togglePolicy(policy)">
              <div>
                <div class="policy-card__meta">
                  <span class="category-label">{{ policy.categoryName }}</span>
                  <span class="source-label" :class="sourceClass(policy.sourceType)">
                    {{ sourceLabel(policy.sourceType) }}
                  </span>
                </div>
                <h3>{{ policy.title }}</h3>
                <p>{{ policy.summary }}</p>
              </div>
              <ChevronRight
                :size="18"
                class="expand-icon"
                :class="{ expanded: expandedId === policy.id }"
              />
            </button>

            <div class="policy-facts">
              <div class="fact-item">
                <CalendarDays :size="15" />
                <span>{{ formatDateRange(policy) }}</span>
              </div>
              <div class="fact-item">
                <GraduationCap :size="15" />
                <span>{{ policy.targetStudents || '符合对应类型报考条件的考生' }}</span>
              </div>
            </div>

            <div v-if="expandedId === policy.id" class="policy-detail">
              <div v-if="policy.requirements" class="requirement-box">
                <strong>资格要点</strong>
                <span>{{ policy.requirements }}</span>
              </div>
              <div class="markdown-body" v-html="renderMarkdown(policy.contentMd || '')"></div>
              <button class="official-btn" @click="openOfficial(policy)">
                <ExternalLink :size="15" />
                查看来源
              </button>
            </div>
          </article>
        </div>
      </section>

      <section class="notice-card gz-card">
        <h2>使用提醒</h2>
        <p>
          特殊类型招生强依赖资格审核、院校测试和当年政策变化。本页面用于公益信息汇总，
          最终填报和录取资格请以{{ currentProvince.officialSource }}、教育部阳光高考平台及目标院校招生章程为准。
        </p>
      </section>
    </main>
  </div>
</template>

<style scoped>
.special-page {
  min-height: 100dvh;
  background: var(--gz-bg);
}

.special-hero {
  position: relative;
  overflow: hidden;
  min-height: 260px;
  padding: calc(env(safe-area-inset-top, 0px) + 20px) 20px 40px;
  color: #fff;
  background:
    linear-gradient(180deg, rgba(12, 16, 14, 0.55) 0%, rgba(12, 16, 14, 0.3) 55%, rgba(12, 16, 14, 0.6) 100%),
    url('/icons/hero-landscape.png') center 55% / cover no-repeat #29302b;
}

.back-btn {
  position: relative;
  z-index: 1;
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 999px;
}

.special-hero__content {
  position: relative;
  z-index: 1;
  max-width: 760px;
  margin: 28px auto 0;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  margin-bottom: 14px;
  font-size: 13px;
  font-weight: 700;
  color: #e7e6e1;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999px;
}

.special-hero h1 {
  margin: 0;
  font-family: var(--gz-font-display);
  font-size: clamp(32px, 8vw, 52px);
  line-height: 1.2;
  letter-spacing: 0.02em;
  font-weight: 700;
  text-shadow: 0 2px 18px rgba(12, 16, 14, 0.4);
}

.special-hero p {
  max-width: 680px;
  margin: 16px 0 0;
  font-size: 15px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.86);
}

.special-hero__wave {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  line-height: 0;
}

.special-hero__wave svg {
  width: 100%;
  height: 40px;
}

.sc-special-lock {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  border-color: rgba(138, 109, 59, 0.24);
  background: #ffffff;
}

.sc-special-lock svg {
  flex-shrink: 0;
  margin-top: 3px;
  color: #7c5f33;
}

.sc-special-lock h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.3;
  color: #17181c;
}

.sc-special-lock p {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.8;
  color: #6a6c72;
}

.special-main {
  position: relative;
  z-index: 2;
  margin-top: -30px;
}

.quick-panel {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  padding: 16px;
}

.quick-panel__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 0;
  color: var(--gz-text-secondary);
  font-size: 12px;
}

.quick-num {
  font-size: 21px;
  font-weight: 800;
  color: var(--gz-text-primary);
  letter-spacing: -0.04em;
}

.focus-section {
  padding: 18px;
  margin-top: 14px;
}

.focus-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.focus-card {
  display: grid;
  gap: 6px;
  min-height: 110px;
  padding: 14px;
  text-align: left;
  color: var(--gz-text-primary);
  background: #ffffff;
  border: 1px solid rgba(23, 24, 28, 0.1);
  border-radius: 18px;
}

.focus-card--readonly {
  cursor: default;
}

.focus-card--readonly:hover {
  transform: none;
}

.focus-card svg {
  color: #a5793a;
}

.focus-card strong {
  font-size: 15px;
}

.focus-card span {
  color: var(--gz-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.deadline-strip {
  display: grid;
  gap: 8px;
  margin-top: 14px;
}

.deadline-title {
  color: var(--gz-text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.deadline-chip {
  padding: 9px 10px;
  color: #7c5f33;
  font-size: 12px;
  text-align: left;
  background: #faf7ef;
  border: 1px solid #e6dcbd;
  border-radius: 12px;
}

.coverage-section {
  padding: 18px;
  margin-top: 14px;
}

.coverage-head {
  margin-top: 0;
}

.coverage-summary,
.coverage-gap-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.coverage-summary {
  margin-bottom: 10px;
}

.coverage-summary span,
.coverage-gap {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 700;
  border-radius: 999px;
}

.coverage-summary span {
  color: #1e3a8a;
  background: #e7e6e1;
}

.coverage-gap {
  color: #7c5f33;
  background: #faf7ef;
  border: 1px solid #e6dcbd;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
  margin: 22px 0 12px;
}

.section-head h2 {
  margin: 0;
  font-size: 20px;
  line-height: 1.2;
}

.section-head p {
  margin-top: 4px;
  color: var(--gz-text-secondary);
  font-size: 13px;
}

.category-scroll {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
  scrollbar-width: none;
}

.category-scroll::-webkit-scrollbar {
  display: none;
}

.category-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 16px;
  color: #383a40;
  white-space: nowrap;
  background: #ffffff;
  border: 1px solid rgba(23, 24, 28, 0.08);
  border-radius: 999px;
  box-shadow: 0 8px 20px rgba(23, 24, 28, 0.04);
}

.category-chip span {
  padding: 1px 7px;
  font-size: 11px;
  color: #17181c;
  background: #e7e6e1;
  border-radius: 999px;
}

.category-chip.active {
  color: #fff;
  background: #17181c;
  border-color: #17181c;
}

.category-chip.active span {
  color: #17181c;
  background: #fff;
}

.verified-mark {
  display: none;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding: 7px 10px;
  font-size: 12px;
  font-weight: 700;
  color: #166534;
  background: #dcfce7;
  border-radius: 999px;
}

.state-card {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--gz-text-secondary);
  font-size: 14px;
}

.state-link {
  min-height: 32px;
  padding: 0 10px;
  color: #17181c;
  font-weight: 700;
  background: #e7e6e1;
  border: 0;
  border-radius: 999px;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.policy-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.policy-card {
  padding: 0;
  overflow: hidden;
}

.policy-card__head {
  display: flex;
  width: 100%;
  gap: 14px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 18px 18px 12px;
  text-align: left;
  background: transparent;
  border: 0;
}

.policy-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.category-label,
.source-label {
  display: inline-flex;
  align-items: center;
  padding: 3px 9px;
  font-size: 11px;
  font-weight: 700;
  border-radius: 999px;
}

.category-label {
  color: #1e3a8a;
  background: #e7e6e1;
}

.source--official {
  color: #166534;
  background: #dcfce7;
}

.source--wechat {
  color: #7c5f33;
  background: #f3ecd9;
}

.source--reprint {
  color: #4b4d54;
  background: #f2f2ef;
}

.policy-card h3 {
  margin: 0;
  font-size: 17px;
  line-height: 1.42;
  color: var(--gz-text-primary);
}

.policy-card p {
  margin: 8px 0 0;
  color: var(--gz-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.expand-icon {
  flex-shrink: 0;
  margin-top: 34px;
  color: var(--gz-text-tertiary);
  transition: transform var(--gz-transition-fast);
}

.expand-icon.expanded {
  transform: rotate(90deg);
}

.policy-facts {
  display: grid;
  gap: 8px;
  padding: 0 18px 16px;
}

.fact-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: var(--gz-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.fact-item svg {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--gz-accent);
}

.policy-detail {
  padding: 16px 18px 18px;
  border-top: 1px solid rgba(23, 24, 28, 0.08);
  background: var(--gz-bg-subtle);
}

.requirement-box {
  display: grid;
  gap: 5px;
  padding: 12px;
  margin-bottom: 12px;
  color: #713f12;
  background: #faf7ef;
  border: 1px solid #e6dcbd;
  border-radius: 14px;
  font-size: 13px;
}

.markdown-body {
  color: var(--gz-text-secondary);
  font-size: 14px;
  line-height: 1.8;
}

.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 12px 0 6px;
  color: var(--gz-text-primary);
  font-size: 16px;
}

.markdown-body :deep(ul) {
  padding-left: 18px;
  margin: 8px 0;
}

.markdown-body :deep(li) {
  margin: 4px 0;
}

.official-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 40px;
  padding: 0 14px;
  margin-top: 14px;
  color: #fff;
  font-weight: 700;
  background: #17181c;
  border: 0;
  border-radius: 999px;
}

.notice-card {
  margin-top: 18px;
}

.notice-card h2 {
  margin: 0 0 8px;
  font-size: 18px;
}

.notice-card p {
  color: var(--gz-text-secondary);
  font-size: 13px;
  line-height: 1.8;
}

@media (min-width: 768px) {
  .special-hero {
    min-height: 330px;
    padding-left: 40px;
    padding-right: 40px;
  }

  .special-hero__wave svg {
    height: 60px;
  }

  .verified-mark {
    display: inline-flex;
  }

  .policy-facts {
    grid-template-columns: 1fr 1fr;
  }

  .focus-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
</style>
