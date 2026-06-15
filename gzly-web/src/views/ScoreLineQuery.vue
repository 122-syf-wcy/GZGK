<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DisclaimerNotice from '@/components/DisclaimerNotice.vue'
import { SCORE_LINE_NOTICE } from '@/constants/disclaimer'
import {
  getProvinceScoreLineCapability,
  queryProvinceScoreLines,
} from '@/api/scoreLine'
import {
  getProvinceConfig,
  normalizeProvinceCode,
  PROVINCE_LIST,
  type ProvinceCode,
} from '@/constants/provinces'
import type {
  ProvinceScoreLineCapability,
  ProvinceScoreLineRecord,
  ProvinceScoreLineTypeCapability,
} from '@/types'
import {
  ArrowLeft,
  BookOpen,
  Building2,
  ChevronLeft,
  ChevronRight,
  Database,
  FileSearch,
  Hash,
  Search,
} from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()

const provinceCode = ref<ProvinceCode>(normalizeProvinceCode(route.query.provinceCode))
const capability = ref<ProvinceScoreLineCapability | null>(null)
const activeType = ref('admission_line')
const year = ref<number | undefined>(2025)
const subjectCategory = ref('')
const selectedSubjects = ref<string[]>([])
const searchKeyword = ref('')
const loading = ref(false)
const capabilityLoading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = 24
const records = ref<ProvinceScoreLineRecord[]>([])
const missingReason = ref('')
const resultStatus = ref<'AVAILABLE' | 'MISSING' | string>('MISSING')

const currentProvince = computed(() => getProvinceConfig(provinceCode.value))
const provinceOptions = computed(() => PROVINCE_LIST.map(item => ({
  key: item.code,
  label: item.name,
  hint: item.code === 'HI' ? '3+3 选科' : item.volunteerUnit,
})))
const capabilityTypes = computed<ProvinceScoreLineTypeCapability[]>(() => capability.value?.scoreLineTypes || [])
const activeTypeConfig = computed(() => capabilityTypes.value.find(item => item.type === activeType.value) || capabilityTypes.value[0])
const isHainan = computed(() => provinceCode.value === 'HI' || capability.value?.subjectMode === 'SELECTED_SUBJECTS_3_3')
const isFirstYearNewGaokao = computed(() => ['SC', 'YN', 'HA'].includes(provinceCode.value))
const yearOptions = computed(() => capability.value?.availableYears?.length ? capability.value.availableYears : [2025])
const subjectOptions = computed(() => capability.value?.subjectOptions || [])
const selectedSubjectOptions = computed(() => capability.value?.selectedSubjectOptions || [])
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const canQueryActiveType = computed(() => Boolean(activeTypeConfig.value?.queryable))
const visibleRangeText = computed(() => {
  if (total.value === 0) return '0'
  const start = (page.value - 1) * pageSize + 1
  const end = Math.min(page.value * pageSize, total.value)
  return `${start}-${end}`
})
const activeTypeLabel = computed(() => activeTypeConfig.value?.label || '院校投档线')
const sourceTables = computed(() => activeTypeConfig.value?.sourceTables?.join(' / ') || '待补官方源')
const pageSubtitle = computed(() => {
  if (isHainan.value) return '海南按 3+3 selectedSubjects 与 requiredSubjects 匹配，不显示物理/历史分轨。'
  if (isFirstYearNewGaokao.value) return `${currentProvince.value.shortName}为首年新高考口径，旧文理数据仅作参考，不进入主查询逻辑。`
  return `${currentProvince.value.shortName}按本省分数线口径查询，接口不会默认回退到其他省份。`
})
const emptyText = computed(() => missingReason.value || activeTypeConfig.value?.missingReason || `${currentProvince.value.shortName}当前类型暂无可核验结构化数据。`)

async function fetchCapability(): Promise<void> {
  capabilityLoading.value = true
  try {
    const res = await getProvinceScoreLineCapability(provinceCode.value)
    capability.value = res.data.data
    const queryableType = capability.value.scoreLineTypes.find(item => item.queryable)
    activeType.value = queryableType?.type || capability.value.scoreLineTypes[0]?.type || 'admission_line'
    year.value = capability.value.availableYears[0] || 2025
    subjectCategory.value = capability.value.subjectOptions[0] || ''
    selectedSubjects.value = []
  } catch {
    capability.value = null
    activeType.value = 'admission_line'
    year.value = 2025
    subjectCategory.value = ''
  } finally {
    capabilityLoading.value = false
  }
}

async function fetchRecords(): Promise<void> {
  if (!capability.value) return
  if (!canQueryActiveType.value) {
    records.value = []
    total.value = 0
    resultStatus.value = 'MISSING'
    missingReason.value = activeTypeConfig.value?.missingReason || ''
    return
  }
  loading.value = true
  try {
    const res = await queryProvinceScoreLines(provinceCode.value, activeType.value, {
      year: year.value,
      subjectCategory: isHainan.value ? undefined : subjectCategory.value || undefined,
      subjectType: isHainan.value ? undefined : subjectCategory.value || undefined,
      selectedSubjects: selectedSubjects.value.join(',') || undefined,
      schoolName: searchKeyword.value.trim() || undefined,
      majorName: searchKeyword.value.trim() || undefined,
      page: page.value,
      pageSize,
    })
    const data = res.data.data
    records.value = data.pageResult?.items || []
    total.value = data.pageResult?.total || 0
    resultStatus.value = data.dataStatus
    missingReason.value = data.missingReason || ''
  } catch (error) {
    records.value = []
    total.value = 0
    resultStatus.value = 'MISSING'
    missingReason.value = error instanceof Error ? error.message : '分数线查询失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function reloadProvince(): Promise<void> {
  page.value = 1
  records.value = []
  total.value = 0
  missingReason.value = ''
  await fetchCapability()
  await fetchRecords()
}

async function selectProvince(target: ProvinceCode): Promise<void> {
  if (provinceCode.value === target) return
  provinceCode.value = target
  await router.replace({ query: { ...route.query, provinceCode: target } })
  await reloadProvince()
}

function selectType(type: string): void {
  activeType.value = type
  page.value = 1
  void fetchRecords()
}

function selectYear(target: number): void {
  year.value = target
  page.value = 1
  void fetchRecords()
}

function selectSubject(target: string): void {
  subjectCategory.value = target
  page.value = 1
  void fetchRecords()
}

function toggleSelectedSubject(subject: string): void {
  if (selectedSubjects.value.includes(subject)) {
    selectedSubjects.value = selectedSubjects.value.filter(item => item !== subject)
  } else if (selectedSubjects.value.length < 3) {
    selectedSubjects.value = [...selectedSubjects.value, subject]
  }
  page.value = 1
  void fetchRecords()
}

function resetFilters(): void {
  searchKeyword.value = ''
  subjectCategory.value = subjectOptions.value[0] || ''
  selectedSubjects.value = []
  year.value = yearOptions.value[0] || 2025
  page.value = 1
  void fetchRecords()
}

function prevPage(): void {
  if (page.value <= 1) return
  page.value -= 1
  void fetchRecords()
}

function nextPage(): void {
  if (page.value >= totalPages.value) return
  page.value += 1
  void fetchRecords()
}

function formatNumber(value?: number | null): string {
  return value && value > 0 ? value.toLocaleString() : '-'
}

function formatScore(value?: number | null): string {
  return value && value > 0 ? `${value}` : '-'
}

function recordTitle(record: ProvinceScoreLineRecord): string {
  if (activeType.value === 'score_rank') return `${record.score ?? '-'} 分`
  return record.schoolName || record.majorName || record.batchName || '分数线记录'
}

function recordSubtitle(record: ProvinceScoreLineRecord): string {
  const parts = [
    record.majorGroupCode,
    record.majorGroupName,
    record.majorName,
    record.batchName,
    record.requiredSubjects ? `选科：${record.requiredSubjects}` : '',
  ].filter(Boolean)
  return parts.join(' · ') || `${record.year || year.value}年${activeTypeLabel.value}`
}

function activeTypeClass(type: ProvinceScoreLineTypeCapability): Record<string, boolean> {
  return {
    'is-active': activeType.value === type.type,
    'is-missing': !type.queryable,
  }
}

onMounted(() => {
  void reloadProvince()
})

watch(() => route.query.provinceCode, () => {
  const next = normalizeProvinceCode(route.query.provinceCode)
  if (next === provinceCode.value) return
  provinceCode.value = next
  void reloadProvince()
})
</script>

<template>
  <div class="gz-shell-page score-page">
    <header class="gz-shell-header">
      <div class="gz-shell-header-inner">
        <button type="button" class="gz-shell-back" aria-label="返回上一页" @click="router.back()">
          <ArrowLeft :size="20" />
        </button>
        <div class="gz-shell-heading">
          <div class="gz-shell-title">{{ currentProvince.shortName }}分数线查询</div>
          <div class="gz-shell-subtitle">{{ pageSubtitle }}</div>
        </div>
        <div class="gz-shell-header-extra">{{ resultStatus === 'AVAILABLE' ? '可查询' : '缺口展示' }}</div>
      </div>
    </header>

    <main class="gz-shell-main score-main">
      <section class="gz-shell-hero score-hero">
        <div class="score-hero__copy">
          <span class="gz-shell-kicker">province score-line adapter</span>
          <h1 class="gz-shell-hero-title">{{ currentProvince.shortName }}{{ activeTypeLabel }}</h1>
          <p class="gz-shell-hero-desc">
            UI 和接口结构可以共用，但查询逻辑按 {{ currentProvince.shortName }} adapter 执行；没有官方结构化数据时返回缺口说明，不跨省、不回退、不伪造 2026。
          </p>
          <div class="gz-shell-chip-row">
            <span class="gz-shell-chip is-soft-active">{{ capability?.dataStatus || 'PRE_OFFICIAL_DATA' }}</span>
            <span class="gz-shell-chip is-soft-active">目标 {{ capability?.targetYear || 2026 }}</span>
            <span class="gz-shell-chip is-soft-active">历史 {{ capability?.latestOfficialDataYear || 2025 }}</span>
          </div>
        </div>

        <div class="gz-shell-metrics">
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">当前省份</span>
            <span class="gz-shell-metric-value">{{ currentProvince.shortName }}</span>
            <span class="gz-shell-metric-note">{{ capability?.policyMode || currentProvince.volunteerUnit }}</span>
          </div>
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">基准年份</span>
            <span class="gz-shell-metric-value">{{ year || '-' }}</span>
            <span class="gz-shell-metric-note">不查询 2026 空表</span>
          </div>
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">查询口径</span>
            <span class="gz-shell-metric-value">{{ isHainan ? '3+3' : (subjectCategory || '本省') }}</span>
            <span class="gz-shell-metric-note">{{ isHainan ? '选科匹配' : '物理/历史隔离' }}</span>
          </div>
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">当前结果</span>
            <span class="gz-shell-metric-value">{{ total.toLocaleString() }}</span>
            <span class="gz-shell-metric-note">{{ sourceTables }}</span>
          </div>
        </div>
      </section>

      <section class="gz-shell-panel score-toolbar">
        <div class="score-toolbar__grid">
          <div class="toolbar-block">
            <div class="gz-shell-panel-head">
              <div class="gz-shell-panel-title">省份</div>
              <div class="gz-shell-panel-desc">每个省份独立 adapter，切换后保留 provinceCode，不串省。</div>
            </div>
            <div class="score-province-grid">
              <button
                v-for="item in provinceOptions"
                :key="item.key"
                type="button"
                class="score-province-btn"
                :class="{ 'is-active': provinceCode === item.key }"
                @click="selectProvince(item.key)"
              >
                <Building2 :size="16" />
                <span>{{ item.label }}</span>
                <small>{{ item.hint }}</small>
              </button>
            </div>
          </div>

          <div class="toolbar-block toolbar-block--wide">
            <div class="gz-shell-panel-head">
              <div class="gz-shell-panel-title">分数线类型</div>
              <div class="gz-shell-panel-desc">不可查询类型保留入口和缺口原因，不展示空白页。</div>
            </div>
            <div class="score-type-grid">
              <button
                v-for="type in capabilityTypes"
                :key="type.type"
                type="button"
                class="score-type-card"
                :class="activeTypeClass(type)"
                @click="selectType(type.type)"
              >
                <Database :size="18" />
                <span>{{ type.label }}</span>
                <small>{{ type.queryable ? '可查询' : '待补官方源' }}</small>
              </button>
            </div>
          </div>
        </div>
      </section>

      <section class="gz-shell-panel score-toolbar">
        <div class="score-filter-grid">
          <div class="toolbar-block">
            <div class="gz-shell-panel-head">
              <div class="gz-shell-panel-title">年份</div>
              <div class="gz-shell-panel-desc">当前仅使用历史官方数据；2026 待发布。</div>
            </div>
            <div class="gz-shell-chip-row">
              <button
                v-for="item in yearOptions"
                :key="item"
                type="button"
                class="gz-shell-chip"
                :class="{ 'is-active': year === item }"
                @click="selectYear(item)"
              >
                {{ item }}年
              </button>
            </div>
          </div>

          <div v-if="!isHainan" class="toolbar-block">
            <div class="gz-shell-panel-head">
              <div class="gz-shell-panel-title">科类</div>
              <div class="gz-shell-panel-desc">物理/历史不能互相映射，旧文理不进主查询。</div>
            </div>
            <div class="score-segment">
              <button
                v-for="item in subjectOptions"
                :key="item"
                type="button"
                class="score-segment__btn"
                :class="{ 'is-active': subjectCategory === item }"
                @click="selectSubject(item)"
              >
                <BookOpen :size="16" />
                <span>{{ item }}</span>
              </button>
            </div>
          </div>

          <div v-else class="toolbar-block">
            <div class="gz-shell-panel-head">
              <div class="gz-shell-panel-title">3+3 选科</div>
              <div class="gz-shell-panel-desc">海南不显示物理/历史分轨，按选科组合匹配 requiredSubjects。</div>
            </div>
            <div class="gz-shell-chip-row">
              <button
                v-for="item in selectedSubjectOptions"
                :key="item"
                type="button"
                class="gz-shell-chip"
                :class="{ 'is-active': selectedSubjects.includes(item) }"
                @click="toggleSelectedSubject(item)"
              >
                {{ item }}
              </button>
            </div>
          </div>

          <div class="toolbar-block toolbar-block--search">
            <div class="gz-shell-panel-head">
              <div class="gz-shell-panel-title">检索</div>
              <div class="gz-shell-panel-desc">按院校或专业关键词过滤当前省份数据。</div>
            </div>
            <div class="score-search-field">
              <Search :size="18" class="score-search-field__icon" />
              <input
                v-model="searchKeyword"
                class="score-search-field__input"
                type="text"
                placeholder="输入院校或专业名称"
                @keyup.enter="fetchRecords"
              >
            </div>
            <div class="gz-shell-actions">
              <button type="button" class="gz-shell-action gz-shell-action--primary" @click="fetchRecords">查询</button>
              <button type="button" class="gz-shell-action" @click="resetFilters">重置</button>
            </div>
          </div>
        </div>
      </section>

      <section v-if="capability?.notices?.length" class="score-notices">
        <article v-for="notice in capability.notices" :key="notice" class="score-notice">
          <Hash :size="16" />
          <span>{{ notice }}</span>
        </article>
      </section>

      <DisclaimerNotice :text="SCORE_LINE_NOTICE" tone="warn" class="score-disclaimer" />

      <div class="score-results-head">
        <div>
          <h2 class="score-results-head__title">{{ activeTypeLabel }}</h2>
          <p class="score-results-head__desc">
            {{ activeTypeConfig?.description || '按本省规则展示分数线。' }}
            <template v-if="isFirstYearNewGaokao"> 首年新高考省份旧文理只作弱参考，不进入主查询。</template>
          </p>
        </div>
        <div class="score-results-head__meta">第 {{ page }} / {{ totalPages }} 页 · 当前展示 {{ visibleRangeText }}</div>
      </div>

      <div v-if="loading || capabilityLoading" class="score-state gz-shell-panel">
        <van-loading size="24" color="#0f172a" />
        <span>正在加载{{ currentProvince.shortName }}分数线能力与数据…</span>
      </div>

      <div v-else-if="records.length === 0" class="gz-shell-empty score-empty">
        <FileSearch :size="32" />
        <h3>{{ canQueryActiveType ? '暂无可核验记录' : '该类型待补官方源' }}</h3>
        <p>{{ emptyText }}</p>
      </div>

      <div v-else class="score-record-grid">
        <article
          v-for="record in records"
          :key="`${record.id || record.schoolCode || record.score}-${record.year}-${record.majorGroupCode || record.scoreLineType}`"
          class="gz-shell-panel score-record-card"
        >
          <div class="score-record-card__head">
            <div>
              <div class="score-record-card__kicker">{{ record.year || year }}年 · {{ record.subjectCategory || (isHainan ? '综合改革' : subjectCategory) }}</div>
              <h3>{{ recordTitle(record) }}</h3>
            </div>
            <span class="score-record-card__status">{{ record.dataStatus || 'AVAILABLE' }}</span>
          </div>

          <p class="score-record-card__desc">{{ recordSubtitle(record) }}</p>

          <div class="score-record-card__numbers">
            <div class="score-number">
              <span>{{ activeType === 'score_rank' ? '同分人数' : '最低分' }}</span>
              <strong>{{ activeType === 'score_rank' ? formatNumber(record.sameScoreCount) : formatScore(record.minScore) }}</strong>
            </div>
            <div class="score-number">
              <span>{{ activeType === 'score_rank' ? '累计人数' : '最低位次' }}</span>
              <strong>{{ activeType === 'score_rank' ? formatNumber(record.cumulativeCount) : formatNumber(record.minRank) }}</strong>
            </div>
            <div class="score-number">
              <span>{{ activeType === 'score_rank' ? '位次区间' : '计划数' }}</span>
              <strong>{{ activeType === 'score_rank' ? `${formatNumber(record.rankLow)}-${formatNumber(record.rankHigh)}` : formatNumber(record.planCount) }}</strong>
            </div>
          </div>

          <div class="score-record-card__source">
            <span>{{ record.sourceFile || record.sourcePage || record.sourceUrl || '来源待补结构化元数据' }}</span>
          </div>
        </article>
      </div>

      <div v-if="totalPages > 1" class="gz-shell-panel score-pagination">
        <button type="button" class="gz-shell-action" :disabled="page <= 1" @click="prevPage">
          <ChevronLeft :size="16" />
          上一页
        </button>
        <div class="score-pagination__text">第 {{ page }} / {{ totalPages }} 页 · 共 {{ total.toLocaleString() }} 条</div>
        <button type="button" class="gz-shell-action" :disabled="page >= totalPages" @click="nextPage">
          下一页
          <ChevronRight :size="16" />
        </button>
      </div>
    </main>
  </div>
</template>

<style scoped>
.score-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.score-hero {
  align-items: stretch;
}

.score-toolbar {
  padding: 20px;
}

.score-toolbar__grid,
.score-filter-grid {
  display: grid;
  gap: 18px;
}

.toolbar-block {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.score-province-grid,
.score-type-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
}

.score-province-btn,
.score-type-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 6px 8px;
  min-height: 58px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #0f172a;
  text-align: left;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.score-province-btn:hover,
.score-type-card:hover {
  transform: translateY(-1px);
  border-color: #bfdbfe;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.08);
}

.score-province-btn.is-active,
.score-type-card.is-active {
  border-color: #2563eb;
  box-shadow: 0 12px 30px rgba(37, 99, 235, 0.14);
}

.score-type-card.is-missing {
  background: #f8fafc;
  color: #64748b;
}

.score-province-btn span,
.score-type-card span {
  font-size: 14px;
  font-weight: 750;
}

.score-province-btn small,
.score-type-card small {
  grid-column: 2;
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
}

.score-segment {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 6px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.score-segment__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #475569;
  font-size: 14px;
  font-weight: 700;
}

.score-segment__btn.is-active {
  background: #fff;
  color: #0f172a;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.06);
}

.score-search-field {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 50px;
  padding: 0 14px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.score-search-field__icon {
  color: #94a3b8;
  flex-shrink: 0;
}

.score-search-field__input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: #0f172a;
}

.score-notices {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 10px;
}

.score-notice {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  min-height: 52px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid #fde68a;
  background: #fffbeb;
  color: #92400e;
  font-size: 12px;
  line-height: 1.7;
}

.score-notice svg {
  flex-shrink: 0;
  margin-top: 2px;
}

.score-results-head {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 4px;
}

.score-results-head__title {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.score-results-head__desc,
.score-results-head__meta {
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.score-state,
.score-empty {
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #64748b;
  text-align: center;
}

.score-empty {
  padding: 28px;
  border: 1px dashed #cbd5e1;
  border-radius: 16px;
  background: #f8fafc;
}

.score-empty h3 {
  color: #0f172a;
  font-size: 18px;
  line-height: 1.35;
}

.score-empty p {
  max-width: 720px;
  font-size: 13px;
  line-height: 1.8;
}

.score-record-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 12px;
}

.score-record-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 220px;
  padding: 16px;
}

.score-record-card__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.score-record-card__kicker {
  font-size: 12px;
  line-height: 1.5;
  color: #2563eb;
  font-weight: 800;
}

.score-record-card h3 {
  margin-top: 4px;
  font-size: 18px;
  line-height: 1.35;
  color: #0f172a;
}

.score-record-card__status {
  align-self: flex-start;
  padding: 4px 8px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 11px;
  font-weight: 800;
}

.score-record-card__desc {
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.score-record-card__numbers {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: auto;
}

.score-number {
  min-height: 64px;
  padding: 10px;
  border-radius: 12px;
  border: 1px solid #edf2f7;
  background: #f8fafc;
}

.score-number span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.score-number strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 19px;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.score-record-card__source {
  min-height: 32px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
}

.score-pagination {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 18px;
}

.score-pagination__text {
  font-size: 13px;
  line-height: 1.6;
  color: #64748b;
  text-align: center;
}

@media (min-width: 768px) {
  .score-toolbar {
    padding: 24px;
  }

  .score-results-head,
  .score-pagination {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}

@media (min-width: 1024px) {
  .score-hero {
    grid-template-columns: minmax(0, 1.08fr) minmax(380px, 0.92fr);
  }

  .score-toolbar__grid {
    grid-template-columns: minmax(260px, 0.82fr) minmax(0, 1.18fr);
  }

  .score-filter-grid {
    grid-template-columns: minmax(220px, 0.7fr) minmax(260px, 0.8fr) minmax(340px, 1fr);
  }
}

@media (max-width: 640px) {
  .score-record-card__numbers {
    grid-template-columns: 1fr;
  }
}
</style>
