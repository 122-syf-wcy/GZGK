<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  BookOpenCheck,
  Building2,
  CheckCircle2,
  GraduationCap,
  LockKeyhole,
  MessageSquare,
  ShieldAlert,
  Table2,
} from 'lucide-vue-next'
import { getProvinceBatchSupport, type ProvinceBatchSupportResponse } from '@/api/volunteer'
import {
  getProvinceConfig,
  normalizeProvinceCode,
  type ProvinceConfig,
} from '@/constants/provinces'

defineOptions({ name: 'RegionHome' })

const route = useRoute()
const router = useRouter()
const supportLoading = ref(false)
const batchSupport = ref<ProvinceBatchSupportResponse | null>(null)
const supportError = ref('')

const provinceCode = computed(() => normalizeProvinceCode(route.params.provinceCode))
const province = computed(() => getProvinceConfig(provinceCode.value))
const isPreparing = computed(() => province.value.status !== 'open')
const isPreEstimateOpen = computed(() => province.value.status === 'open')
const isProfessionalGroupProvince = computed(() => province.value.volunteerUnitType === 'PROFESSIONAL_GROUP_45')
const readablePhase = computed(() => phaseText(batchSupport.value?.recommendationPhase || 'PRE_OFFICIAL_DATA'))
const estimateCount = computed(() => batchSupport.value?.summary?.ESTIMATE_RECOMMEND || 0)
const queryOnlyCount = computed(() => batchSupport.value?.summary?.QUERY_ONLY || 0)
const representativeGaps = computed(() => {
  const seen = new Set<string>()
  const gaps: string[] = []
  for (const item of batchSupport.value?.items || []) {
    for (const key of item.missingData || []) {
      const label = missingDataText(key)
      if (!seen.has(label)) {
        seen.add(label)
        gaps.push(label)
      }
      if (gaps.length >= 5) return gaps
    }
  }
  return gaps
})
const supportReasons = computed(() => (batchSupport.value?.items || [])
  .map(item => publicFacingText(item.supportReason || ''))
  .filter(Boolean)
  .slice(0, 3))

const featureCards = computed(() => [
  {
    key: 'university',
    title: '院校查询',
    desc: '全国院校库保持统一，进入详情后按当前地区招生数据复核。',
    status: '全国库',
    icon: Building2,
    path: '/university',
    tone: 'blue',
  },
  {
    key: 'score-line',
    title: '分数线查询',
    desc: province.value.scoreLineDescription,
    status: province.value.scorelineStatusLabel,
    icon: Table2,
    path: '/score-line',
    tone: 'green',
  },
  {
    key: 'volunteer',
    title: 'AI 志愿',
    desc: isPreEstimateOpen.value
      ? `${province.value.volunteerCta}，普通主批生成历史估算草稿，非普通批展示数据缺口。`
      : province.value.volunteerLockDescription,
    status: isPreEstimateOpen.value ? '历史估算' : '锁定',
    icon: isPreEstimateOpen.value ? GraduationCap : LockKeyhole,
    path: '/volunteer',
    tone: isPreEstimateOpen.value ? 'dark' : 'amber',
    locked: isPreparing.value,
  },
  {
    key: 'data-status',
    title: '政策/数据状态',
    desc: `${readablePhase.value}；普通主批历史估算，非普通批只查策略并展示缺口。`,
    status: supportLoading.value ? '读取中' : readablePhase.value,
    icon: ShieldAlert,
    path: '',
    tone: 'slate',
  },
  {
    key: 'special',
    title: '特长生专区',
    desc: province.value.specialAdmissionsHint,
    status: isPreparing.value ? '需复核' : '政策线索',
    icon: BookOpenCheck,
    path: '/special-admissions',
    tone: 'amber',
  },
])

const metrics = computed(() => [
  { label: '志愿单位', value: province.value.volunteerUnit, note: province.value.targetBatch },
  { label: '目标数量', value: `${province.value.targetCount}`, note: isProfessionalGroupProvince.value ? '院校专业组' : '平行志愿' },
  { label: '分数线', value: province.value.scorelineStatusLabel, note: province.value.scorelineAvailableTypes.length ? province.value.scorelineAvailableTypes.join(' / ') : '展示缺口说明' },
  { label: '入口状态', value: '工作台开放', note: isPreEstimateOpen.value ? 'AI 志愿可进入' : '先展示专区' },
])

watch(provinceCode, (code) => {
  if (route.params.provinceCode !== code) {
    router.replace({ path: `/region/${code}`, query: province.value.routeQuery })
  }
}, { immediate: true })

watch(provinceCode, () => {
  void loadBatchSupport()
})

onMounted(() => {
  void loadBatchSupport()
})

function goFeature(path: string): void {
  if (!path) {
    document.getElementById('region-data-status')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  router.push({ path, query: province.value.routeQuery })
}

function goHome(): void {
  router.push('/')
}

function statusText(item: ProvinceConfig): string {
  return item.status === 'open' ? item.statusLabel : '官方数据待发布'
}

async function loadBatchSupport(): Promise<void> {
  supportLoading.value = true
  supportError.value = ''
  try {
    const res = await getProvinceBatchSupport(provinceCode.value)
    if (res.data.code === 0) {
      batchSupport.value = res.data.data
      return
    }
    batchSupport.value = null
    supportError.value = '数据状态暂时读取失败'
  } catch {
    batchSupport.value = null
    supportError.value = '数据状态暂时读取失败'
  } finally {
    supportLoading.value = false
  }
}

function phaseText(phase?: string): string {
  if (phase === 'PRE_OFFICIAL_DATA') return '官方数据待发布'
  if (phase === 'OFFICIAL_DATA_PARTIAL') return '官方数据部分导入'
  if (phase === 'OFFICIAL_DATA_IMPORTED') return '官方数据已导入待复核'
  if (phase === 'MODEL_RETRAINED') return '模型已重训待验收'
  if (phase === 'FULL_RECOMMEND_READY') return '完整数据生成待确认'
  return phase || '官方数据待发布'
}

function publicFacingText(text?: string): string {
  return String(text || '')
    .replace(/PRE_OFFICIAL_DATA/g, '官方数据待发布')
    .replace(/ESTIMATE_RECOMMEND/g, '历史估算')
    .replace(/QUERY_ONLY/g, '只查策略')
    .replace(/FULL_RECOMMEND/g, '完整数据生成')
    .replace(/QERY_ONLY/g, '只查策略')
}

function missingDataText(key: string): string {
  const labels: Record<string, string> = {
    official_2026_admission_plan: '2026官方招生计划未发布/未导入',
    official_2026_score_or_rank: '2026官方分数位次未发布/未导入',
    official_2026_composite_score_rule: '综合分折算规则待核验',
    official_2026_special_qualification: '艺术/体育资格条件待核验',
    official_2026_skill_exam_rule: '技能高考规则待核验',
    official_2026_skill_qualification: '技能高考资格条件待核验',
    official_2026_qualification_rule: '资格/提前批条件待核验',
    data_score_rank: '一分一段/位次表门禁未全绿',
    data_admission_group_plan: '院校专业组计划门禁未全绿',
    data_admission_plan_gz: '招生计划门禁未全绿',
    data_major_requirement: '选科/资格要求门禁未全绿',
    data_major_meta: '专业元数据门禁未全绿',
    ml_training: '模型训练门禁未激活',
    HB_A00306_manual_rank_review: '湖北清华A00306位次需人工确认',
    formal_import_strategy_confirmation: '生产已有同年数据，导入策略待确认',
  }
  return labels[key] || key
}
</script>

<template>
  <div class="gz-shell-page region-page">
    <header class="gz-shell-header">
      <div class="gz-shell-header-inner">
        <button type="button" class="gz-shell-back" aria-label="返回地区选择" @click="goHome">
          <ArrowLeft :size="20" />
        </button>
        <div class="gz-shell-heading">
          <div class="gz-shell-title">{{ province.name }}专区</div>
          <div class="gz-shell-subtitle">{{ province.targetBatch }} · {{ province.volunteerUnit }}</div>
        </div>
        <div class="gz-shell-header-extra" :class="`is-${province.statusTone}`">
          {{ statusText(province) }}
        </div>
      </div>
    </header>

    <main class="gz-shell-main region-main">
      <section class="gz-shell-hero region-hero">
        <div class="region-hero__copy">
          <span class="gz-shell-kicker">province workspace</span>
          <h1 class="gz-shell-hero-title">{{ province.heroTitle }}</h1>
          <p class="gz-shell-hero-desc">{{ province.heroDescription }}</p>
          <div class="gz-shell-chip-row region-hero__chips">
            <span class="gz-shell-chip is-soft-active">{{ province.targetBatch }}</span>
            <span class="gz-shell-chip is-soft-active">{{ province.targetCount }} 个{{ province.volunteerUnit }}</span>
            <span class="gz-shell-chip is-soft-active">{{ province.officialSource }}</span>
          </div>
          <div class="region-identity-row">
            <span v-for="identity in province.supportedIdentities" :key="identity">{{ identity }}</span>
          </div>
        </div>

        <div class="gz-shell-metrics">
          <div v-for="metric in metrics" :key="metric.label" class="gz-shell-metric">
            <span class="gz-shell-metric-label">{{ metric.label }}</span>
            <span class="gz-shell-metric-value">{{ metric.value }}</span>
            <span class="gz-shell-metric-note">{{ metric.note }}</span>
          </div>
        </div>
      </section>

      <section id="region-data-status" class="gz-shell-panel region-status-card" :class="{ 'is-preparing': isPreparing }">
        <div class="region-status-card__icon">
          <LockKeyhole v-if="isPreparing" :size="22" />
          <CheckCircle2 v-else :size="22" />
        </div>
        <div>
          <h2>{{ province.dataStatusTitle }}</h2>
          <p>{{ province.dataStatusDescription }}</p>
          <div class="region-status-card__facts">
            <span>{{ supportLoading ? '数据状态读取中' : readablePhase }}</span>
            <span>历史估算 {{ estimateCount }} 个批次</span>
            <span>只查策略 {{ queryOnlyCount }} 个批次</span>
            <span>{{ province.scorelineStatusLabel }}</span>
          </div>
          <div class="region-scoreline-summary">
            <strong>分数线能力</strong>
            <span v-if="province.scorelineAvailableTypes.length">可查：{{ province.scorelineAvailableTypes.join('、') }}</span>
            <span>缺口：{{ province.scorelineGapTypes.join('、') }}</span>
          </div>
          <div v-if="supportError" class="region-status-card__warning">{{ supportError }}</div>
          <div v-if="representativeGaps.length" class="region-status-card__gaps">
            <strong>主要缺口</strong>
            <span v-for="gap in representativeGaps" :key="gap">{{ gap }}</span>
          </div>
          <ul v-if="supportReasons.length" class="region-status-card__reasons">
            <li v-for="reason in supportReasons" :key="reason">{{ reason }}</li>
          </ul>
        </div>
      </section>

      <section class="gz-shell-panel region-policy-card">
        <h2>身份批次策略</h2>
        <p>{{ province.identityStrategySummary }}</p>
        <small>{{ province.batchSupportNote }}</small>
      </section>

      <section class="region-feature-grid" aria-label="地区功能入口">
        <button
          v-for="feature in featureCards"
          :key="feature.key"
          type="button"
          class="gz-shell-panel region-feature-card"
          :class="[`is-${feature.tone}`, { 'is-locked': feature.locked }]"
          @click="goFeature(feature.path)"
        >
          <span class="region-feature-card__icon">
            <component :is="feature.icon" :size="22" />
          </span>
          <span class="region-feature-card__body">
            <span class="region-feature-card__meta">{{ feature.status }}</span>
            <strong>{{ feature.title }}</strong>
            <small>{{ feature.desc }}</small>
          </span>
          <ArrowRight :size="18" class="region-feature-card__arrow" />
        </button>
      </section>

      <section class="gz-shell-panel region-source-card">
        <div>
          <h2>官方来源提醒</h2>
          <p>{{ province.footerReminder }}</p>
        </div>
        <div class="region-source-card__actions">
          <button type="button" class="gz-shell-action gz-shell-action--primary" @click="goFeature('/volunteer')">
            {{ province.volunteerCta }}
          </button>
          <button type="button" class="gz-shell-action" @click="goFeature('/score-line')">
            查看分数线
          </button>
        </div>
      </section>

      <section class="region-link-row">
        <button type="button" class="region-link" @click="router.push('/disclaimer')">
          <ShieldAlert :size="15" />
          查看免责声明
        </button>
        <button type="button" class="region-link" @click="router.push('/encouragement')">
          <MessageSquare :size="15" />
          考生留言
        </button>
      </section>
    </main>
  </div>
</template>

<style scoped>
.region-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.gz-shell-header-extra.is-open {
  border-color: rgba(16, 185, 129, 0.22);
  background: #ecfdf5;
  color: #047857;
}

.gz-shell-header-extra.is-preparing {
  border-color: rgba(180, 83, 9, 0.22);
  background: #fffbeb;
  color: #92400e;
}

.region-hero__chips {
  margin-top: 18px;
}

.region-identity-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.region-identity-row span {
  padding: 5px 10px;
  border-radius: 999px;
  background: #ecfeff;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.region-policy-card {
  padding: 18px 20px;
}

.region-policy-card h2 {
  margin: 0 0 8px;
  font-size: 18px;
}

.region-policy-card p {
  margin: 0;
  color: #475569;
  line-height: 1.65;
}

.region-policy-card small {
  display: block;
  margin-top: 8px;
  color: #64748b;
  line-height: 1.55;
}

.region-status-card {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 20px;
}

.region-status-card.is-preparing {
  border-color: rgba(180, 83, 9, 0.18);
  background: linear-gradient(180deg, rgba(255, 253, 250, 0.98), rgba(255, 251, 235, 0.94));
}

.region-status-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 14px;
  background: #eff6ff;
  color: #1d4ed8;
}

.region-status-card.is-preparing .region-status-card__icon {
  background: #fffbeb;
  color: #92400e;
}

.region-status-card h2,
.region-source-card h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.25;
  color: #0f172a;
}

.region-status-card p,
.region-source-card p {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.8;
  color: #64748b;
}

.region-status-card__facts,
.region-status-card__gaps {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.region-status-card__facts span,
.region-status-card__gaps span {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.region-status-card__facts span {
  background: #ecfeff;
  color: #0f766e;
}

.region-status-card__gaps strong {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  color: #92400e;
  font-size: 12px;
  font-weight: 850;
}

.region-status-card__gaps span {
  background: #fffbeb;
  color: #92400e;
  border: 1px solid #fde68a;
}

.region-status-card__warning {
  margin-top: 10px;
  color: #b45309;
  font-size: 13px;
  font-weight: 700;
}

.region-status-card__reasons {
  display: grid;
  gap: 6px;
  margin: 12px 0 0;
  padding-left: 18px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.65;
}

.region-feature-grid {
  display: grid;
  gap: 12px;
}

.region-scoreline-summary {
  display: grid;
  gap: 6px;
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #dbeafe;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  line-height: 1.6;
}

.region-scoreline-summary strong {
  color: #0f172a;
}

.region-feature-card {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 18px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
  color: inherit;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.region-feature-card:hover {
  transform: translateY(-1px);
  border-color: rgba(29, 78, 216, 0.2);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.07);
}

.region-feature-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: #eff6ff;
  color: #1d4ed8;
}

.region-feature-card.is-green .region-feature-card__icon {
  background: #ecfdf5;
  color: #047857;
}

.region-feature-card.is-amber .region-feature-card__icon {
  background: #fffbeb;
  color: #92400e;
}

.region-feature-card.is-dark .region-feature-card__icon {
  background: #0f172a;
  color: #ffffff;
}

.region-feature-card__body {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.region-feature-card__meta {
  font-size: 12px;
  font-weight: 800;
  line-height: 1.2;
  color: #1d4ed8;
}

.region-feature-card.is-locked .region-feature-card__meta {
  color: #92400e;
}

.region-feature-card strong {
  font-size: 17px;
  line-height: 1.25;
  color: #0f172a;
}

.region-feature-card small {
  font-size: 13px;
  line-height: 1.6;
  color: #64748b;
}

.region-feature-card__arrow {
  color: #94a3b8;
}

.region-source-card {
  display: grid;
  gap: 18px;
  padding: 20px;
}

.region-source-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.region-link-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.region-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 999px;
  background: #fffdfa;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

@media (min-width: 768px) {
  .region-feature-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1180px) {
  .region-main {
    max-width: 1320px;
  }

  .region-hero,
  .region-status-card,
  .region-policy-card,
  .region-source-card,
  .region-feature-card {
    border-radius: 8px;
    box-shadow: none;
  }

  .region-feature-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 1024px) {
  .region-source-card {
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
  }
}
</style>
