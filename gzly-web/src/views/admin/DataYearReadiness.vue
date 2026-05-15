<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  Activity,
  AlertTriangle,
  CalendarClock,
  Check,
  ClipboardList,
  Database,
  Info,
  Lock,
  RefreshCcw,
} from 'lucide-vue-next'
import { fetchAdminDataYearReadiness } from '@/api/admin'
import type { DataYearReadinessDto, RecommendationPhase } from '@/types'

const dto = ref<DataYearReadinessDto | null>(null)
const loading = ref(false)
const errorMsg = ref('')
const provinceCode = ref('GZ')
const year = ref(2026)

async function loadReadiness() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await fetchAdminDataYearReadiness(provinceCode.value, year.value)
    if (res.data?.code === 0 && res.data.data) {
      dto.value = res.data.data
    } else {
      errorMsg.value = res.data?.message || '加载 readiness 失败'
    }
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || e?.message || '请求 readiness 接口失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadReadiness)

const PHASE_ORDER: RecommendationPhase[] = [
  'PRE_OFFICIAL_DATA',
  'OFFICIAL_DATA_PARTIAL',
  'OFFICIAL_DATA_IMPORTED',
  'MODEL_RETRAINED',
]

const PHASE_META: Record<string, { label: string; desc: string; tone: 'gray' | 'amber' | 'blue' | 'green' }> = {
  PRE_OFFICIAL_DATA: { label: '阶段 1 · 历史预估', desc: '官方数据未发布或未导入，仅历史趋势可用', tone: 'gray' },
  OFFICIAL_DATA_PARTIAL: { label: '阶段 2 · 分批导入', desc: '部分官方数据已落库，仍禁止完整推荐', tone: 'amber' },
  OFFICIAL_DATA_IMPORTED: { label: '阶段 3 · 数据齐备', desc: '关键官方数据完成，等待模型重训', tone: 'blue' },
  MODEL_RETRAINED: { label: '阶段 4 · 模型重训', desc: '模型完成切换，按批次门禁开放完整推荐', tone: 'green' },
}

const currentPhase = computed<RecommendationPhase | ''>(() => dto.value?.recommendationPhase || '')
const currentPhaseIndex = computed(() => {
  const idx = PHASE_ORDER.indexOf(currentPhase.value as RecommendationPhase)
  return idx < 0 ? 0 : idx
})

const overallProgress = computed(() => {
  if (!dto.value) return 0
  const r = dto.value.dataReadiness
  const flags = [
    r.policyReady,
    r.scoreSegmentReady,
    r.admissionPlanReady,
    r.majorRequirementReady,
    r.majorMetaReady,
    r.mlTrainingReady,
  ]
  const done = flags.filter(Boolean).length
  return Math.round((done / flags.length) * 100)
})

const readinessRows = computed(() => {
  if (!dto.value) return []
  const r = dto.value.dataReadiness
  return [
    { key: 'policy', label: '批次政策规则 (policy_ready)', ready: r.policyReady, hint: '招生计划、最大志愿数、调剂规则' },
    { key: 'score_segment', label: '一分一段表 (score_segment_ready)', ready: r.scoreSegmentReady, hint: '位次估算与梯度划分基础' },
    { key: 'admission_plan', label: '招生计划 (admission_plan_ready)', ready: r.admissionPlanReady, hint: '院校专业组与计划数' },
    { key: 'major_requirement', label: '选科要求 (major_requirement_ready)', ready: r.majorRequirementReady, hint: '物理/历史 + 再选科目门槛' },
    { key: 'major_meta', label: '专业元数据 (major_meta_ready)', ready: r.majorMetaReady, hint: '专业备注、限制条件、单科要求' },
    { key: 'ml_training', label: '模型训练 (ml_training_ready)', ready: r.mlTrainingReady, hint: '重训完成且推理服务已切换' },
    { key: 'historical_training', label: '历史训练数据 (historical_training_ready)', ready: r.historicalTrainingReady, hint: '2024/2025 历史数据可用，常驻 true' },
  ]
})

const formattedLastChecked = computed(() => dto.value?.dataReadiness?.lastCheckedAt || '尚未记录')
const formattedBatchId = computed(() => dto.value?.dataReadiness?.latestImportBatchId || '未生成')
const supportLevelHint = computed(() => {
  if (!dto.value) return ''
  if (dto.value.modelRetrained) return '阶段 4：批次质量门禁通过即可对外开放 FULL_RECOMMEND。'
  if (dto.value.officialDataReady) return '阶段 3：仅允许 TRIAL_RECOMMEND，禁止暴露 FULL_RECOMMEND。'
  if (dto.value.recommendationPhase === 'OFFICIAL_DATA_PARTIAL') return '阶段 2：仍仅展示政策、趋势和数据缺口说明。'
  return '阶段 1：所有公开批次强制 QUERY_ONLY，仅历史趋势。'
})

function phaseToneClass(phase: RecommendationPhase, idx: number) {
  if (idx < currentPhaseIndex.value) return 'phase-step phase-step--done'
  if (idx === currentPhaseIndex.value) return `phase-step phase-step--active phase-step--${PHASE_META[phase]?.tone || 'gray'}`
  return 'phase-step phase-step--pending'
}
</script>

<template>
  <div class="readiness-page">
    <div class="page-inner">
      <div class="page-head">
        <div>
          <h1 class="page-title">数据准备进度</h1>
          <p class="page-desc">2026 官方数据导入、模型重训和正式推荐开放进度（只读视图）</p>
        </div>
        <div class="head-actions">
          <span class="locked-badge">
            <Lock :size="13" /> 只读视图，不可触发导入/重训/切换
          </span>
          <button class="refresh-btn" :disabled="loading" @click="loadReadiness">
            <RefreshCcw :size="14" />
            <span>{{ loading ? '加载中…' : '刷新' }}</span>
          </button>
        </div>
      </div>

      <div v-if="errorMsg" class="error-bar">
        <AlertTriangle :size="14" /> {{ errorMsg }}
      </div>

      <section v-if="dto" class="phase-card gz-card">
        <div class="phase-head">
          <div class="chart-title-row">
            <Activity :size="16" />
            <h3>推荐阶段时间线</h3>
          </div>
          <span class="phase-pill">{{ provinceCode }} · {{ year }}</span>
        </div>

        <div class="phase-timeline">
          <div
            v-for="(phase, idx) in PHASE_ORDER"
            :key="phase"
            :class="phaseToneClass(phase, idx)"
          >
            <div class="phase-dot">
              <Check v-if="idx < currentPhaseIndex" :size="14" />
              <span v-else>{{ idx + 1 }}</span>
            </div>
            <div class="phase-info">
              <strong>{{ PHASE_META[phase]?.label || phase }}</strong>
              <span>{{ PHASE_META[phase]?.desc || '' }}</span>
            </div>
          </div>
        </div>

        <p class="phase-desc-text">
          <Info :size="14" /> {{ dto.phaseDescription || '当前阶段说明不可用' }}
        </p>
        <p class="phase-desc-text phase-desc-text--note">
          <ClipboardList :size="14" /> {{ supportLevelHint }}
        </p>
      </section>

      <section v-if="dto" class="phase-card gz-card">
        <div class="phase-head">
          <div class="chart-title-row">
            <Database :size="16" />
            <h3>关键字段就绪状态（{{ overallProgress }}%）</h3>
          </div>
          <span class="phase-pill phase-pill--neutral">
            数据源年份：{{ dto.dataSourceYears.join(' / ') || '—' }}
          </span>
        </div>

        <div class="coverage-bar">
          <span :style="{ width: overallProgress + '%' }"></span>
        </div>

        <ul class="ready-list">
          <li
            v-for="row in readinessRows"
            :key="row.key"
            class="ready-item"
            :class="{ 'ready-item--done': row.ready, 'ready-item--pending': !row.ready }"
          >
            <span class="ready-status">
              <Check v-if="row.ready" :size="14" />
              <span v-else>—</span>
            </span>
            <div class="ready-text">
              <strong>{{ row.label }}</strong>
              <span>{{ row.hint }}</span>
            </div>
            <span class="ready-flag">{{ row.ready ? 'READY' : 'PENDING' }}</span>
          </li>
        </ul>
      </section>

      <section v-if="dto" class="phase-card gz-card">
        <div class="phase-head">
          <div class="chart-title-row">
            <ClipboardList :size="16" />
            <h3>下一步建议（按数据缺口给出，仅供参考）</h3>
          </div>
          <span class="phase-pill phase-pill--neutral">不开放正式推荐按钮</span>
        </div>
        <ol v-if="dto.nextActions?.length" class="next-actions">
          <li v-for="(action, idx) in dto.nextActions" :key="idx">{{ action }}</li>
        </ol>
        <p v-else class="phase-desc-text">暂无后续建议，readiness 已经完成。</p>
      </section>

      <section v-if="dto" class="phase-card gz-card">
        <div class="phase-head">
          <div class="chart-title-row">
            <CalendarClock :size="16" />
            <h3>导入与校准元数据</h3>
          </div>
          <span class="phase-pill phase-pill--neutral">来自 data_year_readiness</span>
        </div>
        <div class="meta-grid">
          <div class="meta-item">
            <span class="meta-label">最近 import_batch_id</span>
            <span class="meta-value">{{ formattedBatchId }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">last_checked_at</span>
            <span class="meta-value">{{ formattedLastChecked }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">活跃高考年份</span>
            <span class="meta-value">{{ dto.activeAdmissionYear }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">最新官方数据年份</span>
            <span class="meta-value">{{ dto.latestOfficialDataYear }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">训练数据年份</span>
            <span class="meta-value">{{ dto.trainingYears.join(', ') || '—' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">备注 / remarks</span>
            <span class="meta-value">{{ dto.dataReadiness?.remarks || '—' }}</span>
          </div>
        </div>
      </section>

      <div v-if="!dto && !loading && !errorMsg" class="empty-tip">
        <Info :size="14" /> 暂无 readiness 数据。
      </div>
    </div>
  </div>
</template>

<style scoped>
.readiness-page {
  min-height: 100%;
}

.page-inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: var(--gz-space-5) var(--gz-space-4);
}

.page-head {
  display: flex;
  flex-direction: column;
  gap: var(--gz-space-3);
  margin-bottom: var(--gz-space-6);
}

.page-title {
  font-size: 24px;
  font-weight: 800;
  color: var(--gz-text-primary);
  margin-bottom: 4px;
}

.page-desc {
  font-size: 14px;
  color: var(--gz-text-tertiary);
}

.head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gz-space-3);
  align-items: center;
}

.locked-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 700;
  color: #92400e;
  background: #fef3c7;
  border: 1px solid #fcd34d;
  border-radius: var(--gz-radius-full);
}

.refresh-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: #111827;
  border: 0;
  border-radius: var(--gz-radius-full);
  cursor: pointer;
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  margin-bottom: var(--gz-space-4);
  font-size: 13px;
  color: #991b1b;
  background: #fee2e2;
  border: 1px solid #fecaca;
  border-radius: var(--gz-radius-md);
}

.phase-card {
  padding: var(--gz-space-5);
  margin-bottom: var(--gz-space-5);
}

.phase-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--gz-space-3);
  margin-bottom: var(--gz-space-4);
}

.chart-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--gz-text-primary);
}

.chart-title-row h3 {
  font-size: 16px;
  font-weight: 700;
}

.phase-pill {
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 700;
  color: var(--gz-text-secondary);
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: var(--gz-radius-full);
}

.phase-pill--neutral {
  background: #f1f5f9;
}

.phase-timeline {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--gz-space-3);
  margin-bottom: var(--gz-space-4);
}

.phase-step {
  display: flex;
  gap: var(--gz-space-3);
  padding: var(--gz-space-3);
  border-radius: var(--gz-radius-md);
  border: 1px solid #e5e7eb;
  background: #fff;
}

.phase-step--done {
  border-color: rgba(16, 185, 129, 0.32);
  background: rgba(16, 185, 129, 0.06);
}

.phase-step--active.phase-step--gray {
  border-color: #cbd5e1;
  background: #f1f5f9;
}

.phase-step--active.phase-step--amber {
  border-color: rgba(245, 158, 11, 0.36);
  background: rgba(245, 158, 11, 0.1);
}

.phase-step--active.phase-step--blue {
  border-color: rgba(37, 99, 235, 0.32);
  background: rgba(37, 99, 235, 0.08);
}

.phase-step--active.phase-step--green {
  border-color: rgba(16, 185, 129, 0.4);
  background: rgba(16, 185, 129, 0.12);
}

.phase-step--pending {
  opacity: 0.55;
}

.phase-dot {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #fff;
  border: 1px solid #cbd5e1;
  font-size: 12px;
  font-weight: 800;
  color: var(--gz-text-secondary);
}

.phase-step--done .phase-dot {
  background: #10b981;
  border-color: #10b981;
  color: #fff;
}

.phase-step--active.phase-step--amber .phase-dot {
  background: #f59e0b;
  border-color: #f59e0b;
  color: #fff;
}

.phase-step--active.phase-step--blue .phase-dot {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.phase-step--active.phase-step--green .phase-dot {
  background: #10b981;
  border-color: #10b981;
  color: #fff;
}

.phase-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.phase-info strong {
  font-size: 14px;
  font-weight: 800;
  color: var(--gz-text-primary);
}

.phase-info span {
  font-size: 12px;
  color: var(--gz-text-tertiary);
}

.phase-desc-text {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 13px;
  color: var(--gz-text-secondary);
  line-height: 1.6;
  margin: 0;
}

.phase-desc-text--note {
  margin-top: var(--gz-space-2);
  color: #1e3a8a;
}

.coverage-bar {
  height: 8px;
  overflow: hidden;
  background: #e5e7eb;
  border-radius: var(--gz-radius-full);
  margin-bottom: var(--gz-space-4);
}

.coverage-bar span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #2563eb, #10b981);
  border-radius: inherit;
  transition: width var(--gz-transition-normal);
}

.ready-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--gz-space-2);
}

.ready-item {
  display: grid;
  grid-template-columns: 28px 1fr auto;
  gap: var(--gz-space-3);
  align-items: center;
  padding: 10px 12px;
  border-radius: var(--gz-radius-md);
  border: 1px solid #e5e7eb;
  background: #fff;
}

.ready-item--done {
  border-color: rgba(16, 185, 129, 0.3);
  background: rgba(16, 185, 129, 0.06);
}

.ready-item--pending {
  border-color: #fde68a;
  background: rgba(245, 158, 11, 0.06);
}

.ready-status {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f1f5f9;
  color: var(--gz-text-tertiary);
  font-weight: 800;
}

.ready-item--done .ready-status {
  background: #10b981;
  color: #fff;
}

.ready-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ready-text strong {
  font-size: 13px;
  color: var(--gz-text-primary);
}

.ready-text span {
  font-size: 12px;
  color: var(--gz-text-tertiary);
}

.ready-flag {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: var(--gz-text-tertiary);
}

.ready-item--done .ready-flag {
  color: #047857;
}

.ready-item--pending .ready-flag {
  color: #b45309;
}

.next-actions {
  margin: 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--gz-text-secondary);
}

.meta-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--gz-space-3);
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: var(--gz-radius-md);
}

.meta-label {
  font-size: 12px;
  color: var(--gz-text-tertiary);
}

.meta-value {
  font-size: 13px;
  font-weight: 700;
  color: var(--gz-text-primary);
  word-break: break-all;
}

.empty-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 16px;
  font-size: 13px;
  color: var(--gz-text-tertiary);
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: var(--gz-radius-md);
}

@media (min-width: 768px) {
  .page-head {
    flex-direction: row;
    justify-content: space-between;
    align-items: flex-end;
  }

  .meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1024px) {
  .meta-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
