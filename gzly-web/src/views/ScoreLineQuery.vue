<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getScoreLineCapability,
  queryProvinceScoreLines,
  type ScoreLineCapability,
  type ScoreLineCapabilityType,
  type ScoreLineQueryRow,
  type ScoreLineType,
} from '@/api/scoreLine'
import {
  getProvinceConfig,
  normalizeProvinceCode,
  PROVINCE_LIST,
  type ProvinceCode,
} from '@/constants/provinces'
import {
  ArrowLeft,
  BookOpenCheck,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Database,
  FileWarning,
  Search,
} from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()

const provinceCode = ref<ProvinceCode>(normalizeProvinceCode(route.query.provinceCode))
const capability = ref<ScoreLineCapability | null>(null)
const activeType = ref<ScoreLineType | string>('score_rank')
const year = ref(2025)
const subjectCategory = ref('物理类')
const selectedSubjects = ref<string[]>([])
const keyword = ref('')
const loadingCapability = ref(false)
const loadingRows = ref(false)
const errorMessage = ref('')
const rows = ref<ScoreLineQueryRow[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const expandedSourceId = ref<string | null>(null)

const currentProvince = computed(() => getProvinceConfig(provinceCode.value))
const isHi = computed(() => provinceCode.value === 'HI')
const isFirstYear = computed(() => currentProvince.value.subjectMode === '3+1+2_FIRST_YEAR')
const scoreLineTypes = computed(() => capability.value?.scoreLineTypes || [])
const activeTypeMeta = computed(() => scoreLineTypes.value.find(item => item.type === activeType.value) || scoreLineTypes.value[0])
const availableYears = computed(() => {
  const years = activeTypeMeta.value?.availableYears?.length
    ? activeTypeMeta.value.availableYears
    : capability.value?.availableYears || [2025]
  return Array.from(new Set(years)).sort((a, b) => b - a)
})
const queryableTypes = computed(() => scoreLineTypes.value.filter(item => item.queryable).length)
const missingTypes = computed(() => scoreLineTypes.value.filter(item => !item.queryable).length)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const provinceOptions = computed(() => PROVINCE_LIST.map(item => ({ code: item.code, name: item.shortName, status: item.scorelineStatusLabel })))
const subjectOptions = computed(() => {
  if (isHi.value) return ['综合改革']
  return capability.value?.subjectOptions?.length ? capability.value.subjectOptions : ['物理类', '历史类']
})
const selectedSubjectOptions = computed(() => capability.value?.selectedSubjectOptions || ['物理', '化学', '生物', '思想政治', '历史', '地理'])
const visibleRangeText = computed(() => {
  if (!total.value) return '0'
  const start = (page.value - 1) * pageSize + 1
  const end = Math.min(page.value * pageSize, total.value)
  return `${start}-${end}`
})
const activeStatusText = computed(() => {
  if (!activeTypeMeta.value) return '待读取'
  if (activeTypeMeta.value.queryable && rows.value.length) return '真实表格'
  if (activeTypeMeta.value.queryable) return '可查询'
  return '暂缺官方源'
})
const currentMissingReason = computed(() => {
  if (loadingRows.value) return ''
  return activeTypeMeta.value?.missingReason || capability.value?.missingReasonByType?.[String(activeType.value)] || currentProvince.value.scorelineSummary
})
const noticeList = computed(() => {
  const notices = isHi.value
    ? (capability.value?.notices || []).filter(item => !/物理|历史/.test(item))
    : [...(capability.value?.notices || [])]
  if (isFirstYear.value) notices.unshift('2025 首年新高考，旧文理数据仅作历史参考，不进入新高考主查询。')
  if (isHi.value) notices.unshift('海南 3+3 综合改革按选科组合与专业组要求复核。')
  return Array.from(new Set(notices)).slice(0, 5)
})

onMounted(() => {
  void loadCapability()
})

watch(() => route.query.provinceCode, (value) => {
  const next = normalizeProvinceCode(value)
  if (provinceCode.value !== next) provinceCode.value = next
})

watch(provinceCode, () => {
  router.replace({ query: { ...route.query, provinceCode: provinceCode.value } })
  void loadCapability()
})

watch(activeType, () => {
  const years = availableYears.value
  year.value = years[0] || 2025
  page.value = 1
  expandedSourceId.value = null
  void loadRows()
})

async function loadCapability(): Promise<void> {
  loadingCapability.value = true
  errorMessage.value = ''
  rows.value = []
  total.value = 0
  try {
    const res = await getScoreLineCapability(provinceCode.value)
    capability.value = res.data.data
    const firstQueryable = capability.value.scoreLineTypes.find(item => item.queryable) || capability.value.scoreLineTypes[0]
    activeType.value = firstQueryable?.type || 'score_rank'
    year.value = firstQueryable?.availableYears?.[0] || capability.value.availableYears?.[0] || 2025
    subjectCategory.value = isHi.value ? '综合改革' : (capability.value.subjectOptions?.[0] || '物理类')
    selectedSubjects.value = isHi.value ? ['物理', '化学', '生物'] : []
    page.value = 1
    await loadRows()
  } catch (error: any) {
    capability.value = null
    errorMessage.value = error?.message || '分数线能力读取失败'
  } finally {
    loadingCapability.value = false
  }
}

async function loadRows(): Promise<void> {
  const meta = activeTypeMeta.value
  if (!meta || !meta.queryable) {
    rows.value = []
    total.value = 0
    return
  }
  loadingRows.value = true
  errorMessage.value = ''
  try {
    const res = await queryProvinceScoreLines(provinceCode.value, activeType.value, {
      year: year.value,
      subjectCategory: isHi.value ? '综合改革' : subjectCategory.value,
      keyword: keyword.value.trim() || undefined,
      schoolName: keyword.value.trim() || undefined,
      page: page.value,
      pageSize,
    })
    const data = res.data.data
    rows.value = data.pageResult?.items || []
    total.value = data.pageResult?.total || 0
    if (data.missingReason && !rows.value.length) {
      errorMessage.value = data.missingReason
    }
  } catch (error: any) {
    rows.value = []
    total.value = 0
    errorMessage.value = error?.message || '分数线数据加载失败'
  } finally {
    loadingRows.value = false
  }
}

function selectProvince(target: ProvinceCode): void {
  if (provinceCode.value === target) return
  provinceCode.value = target
}

function selectType(type: string): void {
  if (activeType.value === type) return
  activeType.value = type
}

function selectYear(target: number): void {
  if (year.value === target) return
  year.value = target
  page.value = 1
  void loadRows()
}

function selectSubject(target: string): void {
  if (subjectCategory.value === target) return
  subjectCategory.value = target
  page.value = 1
  void loadRows()
}

function toggleSelectedSubject(subject: string): void {
  const idx = selectedSubjects.value.indexOf(subject)
  if (idx >= 0) {
    selectedSubjects.value.splice(idx, 1)
  } else if (selectedSubjects.value.length < 3) {
    selectedSubjects.value.push(subject)
  }
}

function onSearch(): void {
  page.value = 1
  expandedSourceId.value = null
  void loadRows()
}

function resetFilters(): void {
  keyword.value = ''
  subjectCategory.value = isHi.value ? '综合改革' : (subjectOptions.value[0] || '物理类')
  selectedSubjects.value = isHi.value ? ['物理', '化学', '生物'] : []
  page.value = 1
  void loadRows()
}

function prevPage(): void {
  if (page.value <= 1) return
  page.value -= 1
  void loadRows()
}

function nextPage(): void {
  if (page.value >= totalPages.value) return
  page.value += 1
  void loadRows()
}

function goRegionHome(): void {
  router.push({ path: `/region/${provinceCode.value}`, query: currentProvince.value.routeQuery })
}

function typeTone(item: ScoreLineCapabilityType): string {
  if (item.queryable && item.dataStatus === 'AVAILABLE') return 'is-available'
  if (item.queryable) return 'is-partial'
  return 'is-missing'
}

function typeStatus(item: ScoreLineCapabilityType): string {
  if (item.queryable && item.dataStatus === 'AVAILABLE') return '可查询'
  if (item.queryable) return '部分可用'
  return '暂缺官方源'
}

function publicScoreLineText(text?: string): string {
  const value = String(text || '')
  if (!isHi.value) return value
  return value
    .replace(/海南 3\+3 不分物理\/历史；?/g, '海南 3+3 按综合改革口径查询；')
    .replace(/不分物理类\/历史类/g, '按综合改革口径')
    .replace(/不按物理\/历史或普通位次模型/g, '不套普通位次模型')
    .replace(/如传入物理类或历史类，接口返回空结果和明确原因。?/g, '')
    .replace(/物理类|历史类|物理\/历史/g, '综合改革')
}

function formatNumber(value?: number | null): string {
  return value === undefined || value === null || value <= 0 ? '-' : value.toLocaleString()
}

function formatText(value?: string | number | null): string {
  if (value === undefined || value === null || value === '') return '-'
  return String(value)
}

function rowKey(row: ScoreLineQueryRow, index: number): string {
  return `${row.id || index}-${row.schoolCode || ''}-${row.majorGroupCode || ''}-${row.score || row.minScore || ''}`
}

function isScoreRankType(): boolean {
  return activeType.value === 'score_rank'
}

function sourceSummary(row: ScoreLineQueryRow): string {
  if (row.sourceUrl?.startsWith('local_official_cache')) return '官方缓存源'
  if (row.sourceUrl) return row.sourceUrl.replace(/^https?:\/\//, '').slice(0, 44)
  if (row.sourceFile) return row.sourceFile.split('/').pop() || '源文件'
  return '来源待补'
}

function toggleSource(row: ScoreLineQueryRow, index: number): void {
  const key = rowKey(row, index)
  expandedSourceId.value = expandedSourceId.value === key ? null : key
}
</script>

<template>
  <div class="gz-shell-page score-page">
    <header class="gz-shell-header">
      <div class="gz-shell-header-inner">
        <button type="button" class="gz-shell-back" aria-label="返回地区工作台" @click="goRegionHome">
          <ArrowLeft :size="20" />
        </button>
        <div class="gz-shell-heading">
          <div class="gz-shell-title">分数线查询</div>
          <div class="gz-shell-subtitle">{{ currentProvince.name }} · {{ currentProvince.scorelineStatusLabel }} · {{ currentProvince.officialSource }}</div>
        </div>
        <div class="gz-shell-header-extra">{{ activeStatusText }}</div>
      </div>
    </header>

    <main class="gz-shell-main score-main">
      <section class="gz-shell-hero score-hero">
        <div class="score-hero__copy">
          <span class="gz-shell-kicker">score line workspace</span>
          <h1 class="gz-shell-hero-title">{{ currentProvince.name }}分数线工作台</h1>
          <p class="gz-shell-hero-desc">
            {{ currentProvince.scorelineSummary }} 当前为 2026 官方数据待发布阶段，只展示已导入且可核验的历史数据；缺少官方源的类型会保留入口和缺口说明。
          </p>
          <div class="gz-shell-chip-row score-hero__filters">
            <span class="gz-shell-chip is-soft-active">{{ currentProvince.scorelineStatusLabel }}</span>
            <span class="gz-shell-chip is-soft-active">最新官方数据年 {{ capability?.latestOfficialDataYear || 2025 }}</span>
            <span class="gz-shell-chip is-soft-active">targetYear=2026 仅展示</span>
            <span v-if="isHi" class="gz-shell-chip is-soft-active">3+3 综合改革</span>
            <span v-if="isFirstYear" class="gz-shell-chip is-soft-active">2025 首年新高考</span>
          </div>
        </div>

        <div class="gz-shell-metrics">
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">可查询类型</span>
            <span class="gz-shell-metric-value">{{ queryableTypes }}</span>
            <span class="gz-shell-metric-note">真实表格优先展示</span>
          </div>
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">缺口类型</span>
            <span class="gz-shell-metric-value">{{ missingTypes }}</span>
            <span class="gz-shell-metric-note">保留入口与原因</span>
          </div>
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">当前类型</span>
            <span class="gz-shell-metric-value">{{ activeTypeMeta?.label || '-' }}</span>
            <span class="gz-shell-metric-note">{{ activeStatusText }}</span>
          </div>
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">当前记录</span>
            <span class="gz-shell-metric-value">{{ total.toLocaleString() }}</span>
            <span class="gz-shell-metric-note">{{ visibleRangeText }}</span>
          </div>
        </div>
      </section>

      <section class="gz-shell-panel score-toolbar">
        <div class="score-toolbar__topline">
          <div>
            <div class="gz-shell-panel-title">省份与数据类型</div>
            <div class="gz-shell-panel-desc">UI 可共用，业务口径按省份 adapter 返回；海南使用 3+3 综合改革选科口径。</div>
          </div>
          <div class="score-status-pill" :class="`is-${currentProvince.scorelineStatusTone}`">{{ currentProvince.scorelineStatusLabel }}</div>
        </div>

        <div class="score-province-row" aria-label="省份切换">
          <button
            v-for="item in provinceOptions"
            :key="item.code"
            type="button"
            class="score-province-chip"
            :class="{ 'is-active': provinceCode === item.code }"
            @click="selectProvince(item.code)"
          >
            <span>{{ item.name }}</span>
            <small>{{ item.status }}</small>
          </button>
        </div>

        <div v-if="loadingCapability" class="score-state score-state--compact">
          <van-loading size="22" color="#0f172a" />
          <span>正在读取分数线能力…</span>
        </div>

        <div v-else class="score-capability-grid">
          <button
            v-for="item in scoreLineTypes"
            :key="item.type"
            type="button"
            class="score-capability-card"
            :class="[typeTone(item), { 'is-active': activeType === item.type }]"
            @click="selectType(item.type)"
          >
            <span class="score-capability-card__status">{{ typeStatus(item) }}</span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.queryable ? (item.availableYears?.length ? `${item.availableYears.join('/')} 可查` : '历史数据可查') : publicScoreLineText(item.missingReason) }}</small>
          </button>
        </div>
      </section>

      <section class="score-content-grid">
        <div class="score-left-stack">
          <section class="gz-shell-panel score-query-panel">
            <div class="gz-shell-panel-head">
              <div class="gz-shell-panel-title">查询条件</div>
              <div class="gz-shell-panel-desc">只对当前省份和当前类型生效；不可查询类型会展示缺口，不返回空白页。</div>
            </div>

            <div class="score-filter-grid">
              <div class="score-filter-block">
                <span class="score-filter-label">年份</span>
                <div class="gz-shell-chip-row">
                  <button
                    v-for="item in availableYears"
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

              <div v-if="!isHi" class="score-filter-block">
                <span class="score-filter-label">科类</span>
                <div class="score-segment">
                  <button
                    v-for="item in subjectOptions"
                    :key="item"
                    type="button"
                    class="score-segment__btn"
                    :class="{ 'is-active': subjectCategory === item }"
                    @click="selectSubject(item)"
                  >
                    {{ item }}
                  </button>
                </div>
              </div>

              <div v-else class="score-filter-block score-filter-block--wide">
                <span class="score-filter-label">选科组合（3+3）</span>
                <div class="score-subject-grid">
                  <button
                    v-for="item in selectedSubjectOptions"
                    :key="item"
                    type="button"
                    class="score-subject-chip"
                    :class="{ 'is-active': selectedSubjects.includes(item) }"
                    @click="toggleSelectedSubject(item)"
                  >
                    {{ item }}
                  </button>
                </div>
                <p class="score-filter-note">当前查询按“综合改革”展示，选科组合用于你人工复核 requiredSubjects，不作为物理/历史筛选。</p>
              </div>

              <div class="score-filter-block score-filter-block--search">
                <span class="score-filter-label">院校 / 专业组搜索</span>
                <div class="score-search-field">
                  <Search :size="17" class="score-search-field__icon" />
                  <input
                    v-model="keyword"
                    class="score-search-field__input"
                    type="text"
                    placeholder="输入院校名称、学校代码或专业组关键词"
                    @keyup.enter="onSearch"
                  >
                </div>
              </div>
            </div>

            <div class="gz-shell-actions score-actions">
              <button type="button" class="gz-shell-action gz-shell-action--primary" :disabled="!activeTypeMeta?.queryable" @click="onSearch">查询真实表格</button>
              <button type="button" class="gz-shell-action" @click="resetFilters">恢复默认</button>
            </div>
          </section>

          <section class="score-results-section">
            <div class="score-results-head">
              <div>
                <h2 class="score-results-head__title">{{ activeTypeMeta?.label || '分数线' }}</h2>
                <p class="score-results-head__desc">{{ activeTypeMeta?.description || currentProvince.scoreLineDescription }}</p>
              </div>
              <div class="score-results-head__meta">第 {{ page }} / {{ totalPages }} 页 · 当前展示 {{ visibleRangeText }}</div>
            </div>

            <div v-if="loadingRows" class="score-state gz-shell-panel">
              <van-loading size="24" color="#0f172a" />
              <span>正在加载分数线数据…</span>
            </div>

            <div v-else-if="!activeTypeMeta?.queryable" class="gz-shell-panel score-gap-card">
              <FileWarning :size="24" />
              <div>
                <h3>{{ activeTypeMeta?.label || '该类型' }}暂缺官方源</h3>
                <p>{{ publicScoreLineText(currentMissingReason) }}</p>
                <details>
                  <summary>需要补齐的官方源</summary>
                  <div class="score-gap-list">
                    <span v-for="gap in currentProvince.scorelineGapTypes" :key="gap">{{ gap }}</span>
                  </div>
                </details>
              </div>
            </div>

            <div v-else-if="rows.length === 0" class="gz-shell-panel score-gap-card">
              <FileWarning :size="24" />
              <div>
                <h3>当前条件下暂无真实表格</h3>
                <p>{{ publicScoreLineText(errorMessage || currentMissingReason || '请调整年份、科类或关键词后重试。') }}</p>
              </div>
            </div>

            <div v-else class="gz-shell-panel score-table-panel">
              <div class="score-table-wrap">
                <table class="score-table">
                  <thead>
                    <tr v-if="isScoreRankType()">
                      <th>分数</th>
                      <th>同分人数</th>
                      <th>累计人数</th>
                      <th>位次区间</th>
                      <th>科类</th>
                      <th>来源</th>
                    </tr>
                    <tr v-else>
                      <th>院校</th>
                      <th>批次</th>
                      <th>专业组</th>
                      <th>最低分</th>
                      <th>最低位次</th>
                      <th>选科/科类</th>
                      <th>来源</th>
                    </tr>
                  </thead>
                  <tbody>
                    <template v-for="(row, index) in rows" :key="rowKey(row, index)">
                      <tr v-if="isScoreRankType()">
                        <td><strong>{{ formatNumber(row.score) }}</strong></td>
                        <td>{{ formatNumber(row.sameScoreCount) }}</td>
                        <td>{{ formatNumber(row.cumulativeCount) }}</td>
                        <td>{{ formatNumber(row.rankLow) }} - {{ formatNumber(row.rankHigh) }}</td>
                        <td>{{ formatText(row.subjectCategory) }}</td>
                        <td>
                          <button type="button" class="source-toggle" @click="toggleSource(row, index)">
                            {{ sourceSummary(row) }}
                            <ChevronDown :size="14" />
                          </button>
                        </td>
                      </tr>
                      <tr v-else>
                        <td>
                          <strong>{{ formatText(row.schoolName) }}</strong>
                          <small>{{ formatText(row.schoolCode) }}</small>
                        </td>
                        <td>{{ formatText(row.batchName || row.batchCode) }}</td>
                        <td>
                          <span>{{ formatText(row.majorGroupCode || row.majorGroupName) }}</span>
                          <small v-if="row.majorName && row.majorName !== row.majorGroupCode">{{ row.majorName }}</small>
                        </td>
                        <td><strong>{{ formatNumber(row.minScore) }}</strong></td>
                        <td>{{ formatNumber(row.minRank) }}</td>
                        <td>{{ formatText(row.requiredSubjects || row.subjectCategory) }}</td>
                        <td>
                          <button type="button" class="source-toggle" @click="toggleSource(row, index)">
                            {{ sourceSummary(row) }}
                            <ChevronDown :size="14" />
                          </button>
                        </td>
                      </tr>
                      <tr v-if="expandedSourceId === rowKey(row, index)" class="source-row">
                        <td :colspan="isScoreRankType() ? 6 : 7">
                          <div class="source-detail">
                            <span><b>source_file</b>{{ row.sourceFile || '-' }}</span>
                            <span><b>source_url</b>{{ row.sourceUrl || '-' }}</span>
                            <span><b>source_page</b>{{ row.sourcePage || '-' }}</span>
                            <span v-if="row.rawText"><b>raw_text</b>{{ row.rawText }}</span>
                          </div>
                        </td>
                      </tr>
                    </template>
                  </tbody>
                </table>
              </div>

              <div v-if="totalPages > 1" class="score-pagination">
                <button type="button" class="gz-shell-action" :disabled="page <= 1" @click="prevPage">
                  <ChevronLeft :size="16" />
                  上一页
                </button>
                <div class="score-pagination__text">第 {{ page }} / {{ totalPages }} 页 · 共 {{ total.toLocaleString() }} 行</div>
                <button type="button" class="gz-shell-action" :disabled="page >= totalPages" @click="nextPage">
                  下一页
                  <ChevronRight :size="16" />
                </button>
              </div>
            </div>
          </section>
        </div>

        <aside class="score-right-stack">
          <section class="gz-shell-panel score-status-card">
            <div class="score-status-card__icon"><Database :size="22" /></div>
            <div>
              <h2>当前省份数据状态</h2>
              <p>{{ currentProvince.scorelineSummary }}</p>
              <div class="score-mini-grid">
                <span>{{ capability?.dataStatus ? '官方数据待发布' : '状态读取中' }}</span>
                <span>2026 不造数据</span>
                <span>{{ currentProvince.scorelineStatusLabel }}</span>
              </div>
            </div>
          </section>

          <section class="gz-shell-panel score-notice-card">
            <h2>口径提醒</h2>
            <ul>
              <li v-for="notice in noticeList" :key="notice">{{ notice }}</li>
            </ul>
          </section>

          <section class="gz-shell-panel score-gap-summary-card">
            <h2>缺口说明</h2>
            <details open>
              <summary>当前缺什么</summary>
              <div class="score-gap-list">
                <span v-for="gap in currentProvince.scorelineGapTypes" :key="gap">{{ gap }}</span>
              </div>
            </details>
            <details>
              <summary>当前可查什么</summary>
              <div class="score-gap-list score-gap-list--ok">
                <span v-for="item in currentProvince.scorelineAvailableTypes" :key="item">{{ item }}</span>
                <span v-if="!currentProvince.scorelineAvailableTypes.length">暂无真实表格</span>
              </div>
            </details>
          </section>

          <button type="button" class="gz-shell-action score-region-btn" @click="goRegionHome">
            <BookOpenCheck :size="16" />
            返回{{ currentProvince.shortName }}工作台
          </button>
        </aside>
      </section>
    </main>
  </div>
</template>

<style scoped>
.score-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.score-hero,
.score-toolbar,
.score-query-panel,
.score-table-panel,
.score-status-card,
.score-notice-card,
.score-gap-summary-card {
  border-radius: 8px;
  box-shadow: none;
}

.score-hero {
  padding: 22px;
}

.score-hero__filters {
  margin-top: 14px;
}

.score-toolbar {
  padding: 16px;
}

.score-toolbar__topline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.score-status-pill {
  flex-shrink: 0;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 800;
}

.score-status-pill.is-green,
.score-status-pill.is-blue {
  color: #047857;
  background: #ecfdf5;
}

.score-status-pill.is-amber,
.score-status-pill.is-red {
  color: #92400e;
  background: #fffbeb;
}

.score-province-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}

.score-province-chip {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  align-items: flex-start;
  min-height: 50px;
  padding: 9px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  color: #334155;
  text-align: left;
}

.score-province-chip span {
  font-size: 14px;
  font-weight: 800;
  color: #0f172a;
}

.score-province-chip small {
  font-size: 11px;
  color: #64748b;
}

.score-province-chip.is-active {
  border-color: #2563eb;
  background: #eff6ff;
}

.score-capability-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 10px;
}

.score-capability-card {
  min-height: 118px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  color: #334155;
  text-align: left;
}

.score-capability-card.is-active {
  border-color: #2563eb;
  background: #f8fbff;
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.08);
}

.score-capability-card__status {
  min-height: 24px;
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 11px;
  font-weight: 800;
}

.score-capability-card.is-available .score-capability-card__status,
.score-capability-card.is-partial .score-capability-card__status {
  background: #ecfdf5;
  color: #047857;
}

.score-capability-card.is-missing .score-capability-card__status {
  background: #fffbeb;
  color: #92400e;
}

.score-capability-card strong {
  font-size: 16px;
  line-height: 1.25;
  color: #0f172a;
}

.score-capability-card small {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.55;
  color: #64748b;
}

.score-content-grid {
  display: grid;
  gap: 16px;
}

.score-left-stack,
.score-right-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.score-query-panel {
  padding: 16px;
}

.score-filter-grid {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.score-filter-block {
  min-width: 0;
}

.score-filter-label {
  display: block;
  margin-bottom: 8px;
  color: #475569;
  font-size: 13px;
  font-weight: 800;
}

.score-segment {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  padding: 5px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.score-segment__btn {
  min-height: 38px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.score-segment__btn.is-active {
  background: #fff;
  color: #0f172a;
  box-shadow: 0 3px 10px rgba(15, 23, 42, 0.05);
}

.score-subject-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.score-subject-chip {
  min-height: 36px;
  padding: 0 12px;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.score-subject-chip.is-active {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1d4ed8;
}

.score-filter-note {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.score-search-field {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.score-search-field__icon {
  color: #94a3b8;
  flex-shrink: 0;
}

.score-search-field__input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #0f172a;
  font-size: 14px;
}

.score-actions {
  margin-top: 14px;
}

.score-results-head {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 2px;
}

.score-results-head__title {
  font-size: 18px;
  line-height: 1.2;
  color: #0f172a;
}

.score-results-head__desc,
.score-results-head__meta,
.score-pagination__text {
  color: #64748b;
  font-size: 13px;
  line-height: 1.65;
}

.score-state {
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #64748b;
}

.score-state--compact {
  min-height: 80px;
}

.score-gap-card {
  display: flex;
  gap: 12px;
  padding: 18px;
  border-color: #fde68a;
  background: #fffbeb;
}

.score-gap-card svg {
  flex-shrink: 0;
  color: #92400e;
  margin-top: 2px;
}

.score-gap-card h3,
.score-status-card h2,
.score-notice-card h2,
.score-gap-summary-card h2 {
  margin: 0;
  color: #0f172a;
  font-size: 17px;
  line-height: 1.3;
}

.score-gap-card p,
.score-status-card p {
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.score-gap-card details,
.score-gap-summary-card details {
  margin-top: 10px;
}

.score-gap-card summary,
.score-gap-summary-card summary {
  cursor: pointer;
  color: #334155;
  font-size: 13px;
  font-weight: 800;
}

.score-gap-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.score-gap-list span {
  min-height: 28px;
  display: inline-flex;
  align-items: center;
  padding: 0 9px;
  border-radius: 999px;
  border: 1px solid #fde68a;
  background: #fff7ed;
  color: #92400e;
  font-size: 12px;
  font-weight: 700;
}

.score-gap-list--ok span {
  border-color: #bbf7d0;
  background: #ecfdf5;
  color: #047857;
}

.score-table-panel {
  padding: 0;
  overflow: hidden;
}

.score-table-wrap {
  max-height: 620px;
  overflow: auto;
}

.score-table {
  width: 100%;
  min-width: 820px;
  border-collapse: collapse;
  background: #fff;
}

.score-table th,
.score-table td {
  padding: 12px 14px;
  border-bottom: 1px solid #edf2f7;
  text-align: left;
  vertical-align: top;
  font-size: 13px;
  line-height: 1.5;
  color: #334155;
}

.score-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.score-table td strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
}

.score-table td small {
  display: block;
  margin-top: 3px;
  color: #94a3b8;
  font-size: 12px;
}

.source-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 190px;
  min-height: 30px;
  padding: 0 8px;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-row td {
  background: #f8fafc;
}

.source-detail {
  display: grid;
  gap: 6px;
}

.source-detail span {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr);
  gap: 8px;
  color: #475569;
  word-break: break-all;
}

.source-detail b {
  color: #0f172a;
}

.score-pagination {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 14px;
  border-top: 1px solid #edf2f7;
}

.score-status-card {
  display: flex;
  gap: 12px;
  padding: 16px;
}

.score-status-card__icon {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #eff6ff;
  color: #1d4ed8;
}

.score-mini-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.score-mini-grid span {
  min-height: 28px;
  display: inline-flex;
  align-items: center;
  padding: 0 9px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.score-notice-card,
.score-gap-summary-card {
  padding: 16px;
}

.score-notice-card ul {
  display: grid;
  gap: 8px;
  margin: 12px 0 0;
  padding-left: 18px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.65;
}

.score-region-btn {
  width: 100%;
}

@media (min-width: 768px) {
  .score-province-row {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .score-filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .score-filter-block--wide,
  .score-filter-block--search {
    grid-column: span 2;
  }

  .score-results-head,
  .score-pagination {
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
  }
}

@media (min-width: 1180px) {
  .score-main {
    max-width: 1440px;
  }

  .score-content-grid {
    grid-template-columns: minmax(0, 1fr) 340px;
    align-items: start;
  }

  .score-right-stack {
    position: sticky;
    top: 92px;
  }
}

@media (max-width: 540px) {
  .score-toolbar__topline,
  .score-gap-card,
  .score-status-card {
    flex-direction: column;
  }

  .score-province-row {
    grid-template-columns: 1fr;
  }
}
</style>
