<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSchoolScoreLineHistory, getSchoolScoreLineList, getScoreLineYears } from '@/api/scoreLine'
import {
  getProvinceConfig,
  normalizeProvinceCode,
  PROVINCE_LIST,
  type ProvinceCode,
} from '@/constants/provinces'
import {
  ArrowLeft,
  BookOpen,
  Building2,
  ChevronLeft,
  ChevronRight,
  Hash,
  History,
  Search,
} from 'lucide-vue-next'
import type { SchoolScoreSummary, ScoreLine } from '@/types'

const router = useRouter()
const route = useRoute()

const provinceCode = ref<ProvinceCode>(normalizeProvinceCode(route.query.provinceCode))
const year = ref(2025)
const subjectType = ref('物理类')
const searchKeyword = ref('')
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = 24

const yearOptions = ref<number[]>([2025, 2024, 2023, 2022, 2021])
const provinceOptions = PROVINCE_LIST.map(item => ({
  key: item.code,
  label: item.name,
  unit: item.volunteerUnitType === 'PROFESSIONAL_GROUP_45' ? '专业组调档线' : '院校分',
}))
const subjectOptions = ['物理类', '历史类']
const schools = ref<SchoolScoreSummary[]>([])

const detailVisible = ref(false)
const detailLoading = ref(false)
const selectedSchool = ref<SchoolScoreSummary | null>(null)
const historyLines = ref<ScoreLine[]>([])
const isDesktop = ref(false)
let mediaQuery: MediaQueryList | null = null

const currentProvince = computed(() => getProvinceConfig(provinceCode.value))
const isProfessionalGroupProvince = computed(() => currentProvince.value.volunteerUnitType === 'PROFESSIONAL_GROUP_45')
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const visibleRangeText = computed(() => {
  if (total.value === 0) return '0'
  const start = (page.value - 1) * pageSize + 1
  const end = Math.min(page.value * pageSize, total.value)
  return `${start}-${end}`
})
const activeKeywords = computed(() => {
  const tags = [`${year.value}年${isProfessionalGroupProvince.value ? '专业组调档线' : '院校分'}`, currentProvince.value.shortName, subjectType.value]
  const keyword = searchKeyword.value.trim()
  if (keyword) tags.push(`院校：${keyword}`)
  return tags
})
const subjectDescription = computed(() => (
  subjectType.value === '物理类'
    ? `按首选物理口径展示${currentProvince.value.shortName}${isProfessionalGroupProvince.value ? '院校专业组调档线' : '院校级投档线'}，点击查看历年分数和位次。`
    : `按首选历史口径展示${currentProvince.value.shortName}${isProfessionalGroupProvince.value ? '院校专业组调档线' : '院校级投档线'}，点击查看历年分数和位次。`
))
const selectedYearRange = computed(() => {
  if (!selectedSchool.value) return ''
  const first = selectedSchool.value.firstYear
  const last = selectedSchool.value.lastYear
  if (!first || !last) return `${selectedSchool.value.availableYearCount || 0} 年记录`
  if (first === last) return `${last}年`
  return `${first}年-${last}年`
})
const emptyDescription = computed(() => {
  if (isProfessionalGroupProvince.value && yearOptions.value.length === 0) {
    return `${currentProvince.value.shortName}院校专业组调档线数据准备中，当前没有可核验年份。`
  }
  if (isProfessionalGroupProvince.value) {
    return `当前条件下暂无${currentProvince.value.shortName}院校专业组调档线；只展示已导入且可核验的数据。`
  }
  return '当前条件下暂无匹配院校'
})

async function fetchYears(): Promise<void> {
  try {
    const res = await getScoreLineYears(provinceCode.value)
    if (res.data.code === 0) {
      yearOptions.value = res.data.data || []
      year.value = yearOptions.value[0] ?? 2025
    }
  } catch {
    yearOptions.value = []
    year.value = 2025
  }
}

async function fetchSchools(): Promise<void> {
  if (isProfessionalGroupProvince.value && yearOptions.value.length === 0) {
    schools.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const keyword = searchKeyword.value.trim()
    const res = await getSchoolScoreLineList({
      provinceCode: provinceCode.value,
      year: year.value,
      subjectType: subjectType.value,
      universityName: keyword || undefined,
      page: page.value,
      pageSize,
    })
    if (res.data.code === 0) {
      schools.value = res.data.data.items || []
      total.value = res.data.data.total || 0
      return
    }
    schools.value = []
    total.value = 0
  } catch {
    schools.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function openHistory(school: SchoolScoreSummary): Promise<void> {
  selectedSchool.value = school
  if (!isDesktop.value) {
    detailVisible.value = true
  }
  detailLoading.value = true
  historyLines.value = []
  try {
    const res = await getSchoolScoreLineHistory({
      provinceCode: provinceCode.value,
      schoolId: school.schoolId,
      groupCode: school.groupCode,
      subjectType: subjectType.value,
      maxRecords: 10,
    })
    if (res.data.code === 0) {
      historyLines.value = res.data.data || []
    }
  } catch {
    historyLines.value = []
  } finally {
    detailLoading.value = false
  }
}

function onRefresh(): void {
  page.value = 1
  fetchSchools()
}

function selectYear(targetYear: number): void {
  if (year.value === targetYear) return
  year.value = targetYear
  onRefresh()
}

function selectSubject(target: string): void {
  if (subjectType.value === target) return
  subjectType.value = target
  onRefresh()
}

async function selectProvince(target: ProvinceCode): Promise<void> {
  if (provinceCode.value === target) return
  provinceCode.value = target
  router.replace({
    query: {
      ...route.query,
      provinceCode: target,
    },
  })
  page.value = 1
  selectedSchool.value = null
  historyLines.value = []
  await fetchYears()
  await fetchSchools()
}

function resetFilters(): void {
  searchKeyword.value = ''
  year.value = yearOptions.value[0] ?? 2025
  subjectType.value = subjectOptions[0]
  onRefresh()
}

function prevPage(): void {
  if (page.value <= 1) return
  page.value -= 1
  fetchSchools()
}

function nextPage(): void {
  if (page.value >= totalPages.value) return
  page.value += 1
  fetchSchools()
}

function formatRank(rank?: number | null): string {
  return rank && rank > 0 ? rank.toLocaleString() : '—'
}

function formatScore(score?: number | null): string {
  return score && score > 0 ? `${score}` : '—'
}

function rankSourceLabel(type?: string): string {
  if (type === 'original') return '原始位次'
  if (type === 'score_rank_converted') return '一分一段换算'
  return '缺位次复核'
}

function rankSourceClass(type?: string): string {
  if (type === 'original') return 'is-original'
  if (type === 'score_rank_converted') return 'is-converted'
  return 'is-missing'
}

function syncDesktopMode(): void {
  isDesktop.value = Boolean(mediaQuery?.matches)
  if (isDesktop.value) {
    detailVisible.value = false
  }
}

onMounted(async () => {
  mediaQuery = window.matchMedia('(min-width: 1024px)')
  syncDesktopMode()
  mediaQuery.addEventListener('change', syncDesktopMode)
  await fetchYears()
  await fetchSchools()
})

onBeforeUnmount(() => {
  mediaQuery?.removeEventListener('change', syncDesktopMode)
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
          <div class="gz-shell-title">院校分数线</div>
          <div class="gz-shell-subtitle">{{ isProfessionalGroupProvince ? `${currentProvince.shortName}按院校专业组看调档线` : '先按院校看投档门槛，再点进学校查看历年走势' }}</div>
        </div>
        <div class="gz-shell-header-extra">{{ total.toLocaleString() }} {{ isProfessionalGroupProvince ? '组' : '所' }}</div>
      </div>
    </header>

    <div class="gz-shell-main score-main">
      <section class="gz-shell-hero score-hero">
        <div class="score-hero__copy">
          <span class="gz-shell-kicker">school score lines</span>
          <h1 class="gz-shell-hero-title">{{ isProfessionalGroupProvince ? '按院校专业组浏览调档线' : '按院校分浏览，再看历年分数线' }}</h1>
          <p class="gz-shell-hero-desc">
            {{ subjectDescription }} {{ isProfessionalGroupProvince ? `${currentProvince.shortName}只展示已导入且可核验的院校专业组数据。` : '当前页面只展示院校级数据；专业级数据请在生成志愿或院校详情中继续复核。' }}
            最终以{{ currentProvince.officialSource }}和高校官方材料为准。
          </p>
          <div class="gz-shell-chip-row score-hero__filters">
            <span v-for="tag in activeKeywords" :key="tag" class="gz-shell-chip is-soft-active">{{ tag }}</span>
          </div>
        </div>

        <div class="gz-shell-metrics">
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">{{ isProfessionalGroupProvince ? '当前专业组' : '当前院校' }}</span>
            <span class="gz-shell-metric-value">{{ total.toLocaleString() }}</span>
            <span class="gz-shell-metric-note">{{ isProfessionalGroupProvince ? '按院校专业组聚合' : '按学校聚合，不重复铺年份' }}</span>
          </div>
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">基准年份</span>
            <span class="gz-shell-metric-value">{{ year }}</span>
            <span class="gz-shell-metric-note">卡片展示该年院校分</span>
          </div>
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">科类口径</span>
            <span class="gz-shell-metric-value">{{ subjectType }}</span>
            <span class="gz-shell-metric-note">{{ currentProvince.shortName }}新高考口径</span>
          </div>
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">当前页</span>
            <span class="gz-shell-metric-value">{{ visibleRangeText }}</span>
            <span class="gz-shell-metric-note">点击卡片看历年记录</span>
          </div>
        </div>
      </section>

      <section class="gz-shell-panel score-toolbar">
        <div class="score-toolbar__grid">
          <div class="toolbar-block">
            <div class="gz-shell-panel-head">
              <div class="gz-shell-panel-title">省份</div>
              <div class="gz-shell-panel-desc">不同省份志愿单位不同：贵州看院校分，四川、湖北、安徽看院校专业组调档线。</div>
            </div>
            <div class="score-segment">
              <button
                v-for="item in provinceOptions"
                :key="item.key"
                type="button"
                class="score-segment__btn"
                :class="{ 'is-active': provinceCode === item.key }"
                @click="selectProvince(item.key)"
              >
                <Building2 :size="16" />
                <span>{{ item.label }}</span>
              </button>
            </div>
          </div>

          <div class="toolbar-block">
            <div class="gz-shell-panel-head">
              <div class="gz-shell-panel-title">基准年份</div>
              <div class="gz-shell-panel-desc">列表按所选年份的{{ isProfessionalGroupProvince ? '专业组调档线' : '院校分' }}排序，详情中展示历年记录。</div>
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

          <div class="toolbar-block">
            <div class="gz-shell-panel-head">
              <div class="gz-shell-panel-title">科类口径</div>
              <div class="gz-shell-panel-desc">切换物理类 / 历史类，避免把不同口径的位次混在一起看。</div>
            </div>
            <div class="score-segment">
              <button
                v-for="item in subjectOptions"
                :key="item"
                type="button"
                class="score-segment__btn"
                :class="{ 'is-active': subjectType === item }"
                @click="selectSubject(item)"
              >
                <Building2 v-if="item === '物理类'" :size="16" />
                <BookOpen v-else :size="16" />
                <span>{{ item }}</span>
              </button>
            </div>
          </div>

          <div class="toolbar-block toolbar-block--search">
            <div class="gz-shell-panel-head">
              <div class="gz-shell-panel-title">院校检索</div>
              <div class="gz-shell-panel-desc">输入院校名称，快速查看{{ isProfessionalGroupProvince ? '相关院校专业组' : '某所学校的院校级历年分数线' }}。</div>
            </div>
            <div class="score-search-field">
              <Search :size="18" class="score-search-field__icon" />
              <input
                v-model="searchKeyword"
                class="score-search-field__input"
                type="text"
                placeholder="例如：贵州大学、南京大学、武汉大学、安徽大学"
                @keyup.enter="onRefresh"
              >
            </div>
            <div class="gz-shell-actions">
              <button type="button" class="gz-shell-action gz-shell-action--primary" @click="onRefresh">查询{{ isProfessionalGroupProvince ? '专业组' : '院校' }}</button>
              <button type="button" class="gz-shell-action" @click="resetFilters">恢复默认</button>
            </div>
          </div>
        </div>
      </section>

      <div class="score-results-head">
        <div>
          <h2 class="score-results-head__title">{{ isProfessionalGroupProvince ? '院校专业组调档线列表' : '院校分列表' }}</h2>
          <p class="score-results-head__desc">卡片显示基准年份的最低分和最低位次；点击后查看历年{{ isProfessionalGroupProvince ? '专业组调档线' : '院校级投档线' }}。</p>
        </div>
        <div class="score-results-head__meta">
          第 {{ page }} / {{ totalPages }} 页 · 当前展示 {{ visibleRangeText }}
        </div>
      </div>

      <div v-if="loading" class="score-state gz-shell-panel">
        <van-loading size="24" color="#0f172a" />
        <span>正在加载院校分数据…</span>
      </div>

      <div v-else-if="schools.length === 0" class="gz-shell-empty">
        <van-empty :description="emptyDescription" />
      </div>

      <div v-else class="score-workbench">
        <div class="score-workbench__list">
          <div class="school-list">
            <article
              v-for="school in schools"
              :key="`${school.schoolId}-${school.groupCode || ''}-${school.subjectType}`"
              class="gz-shell-panel school-card"
              :class="{ 'is-selected': selectedSchool?.schoolId === school.schoolId }"
              role="button"
              tabindex="0"
              @click="openHistory(school)"
              @keyup.enter="openHistory(school)"
            >
              <div class="school-card__rank">{{ formatRank(school.minRank) }}</div>
              <div class="school-card__main">
                <div class="school-card__badges">
                  <span class="school-card__badge school-card__badge--year">{{ school.latestYear }}年</span>
                  <span class="school-card__badge">{{ school.subjectType }}</span>
                  <span class="school-card__badge">{{ school.dataSourceType || (isProfessionalGroupProvince ? '院校专业组' : '院校级') }}</span>
                </div>
                <h3 class="school-card__name">{{ school.universityName }}</h3>
                <p class="school-card__desc">
                  <template v-if="isProfessionalGroupProvince && school.groupCode">{{ school.groupCode }} · {{ school.groupName || '院校专业组' }} · </template>
                  {{ school.batch || '普通批次' }} · {{ school.firstYear && school.lastYear ? `${school.firstYear}年-${school.lastYear}年` : '历年记录' }}
                </p>
              </div>

              <div class="school-card__numbers">
                <div class="school-number">
                  <span class="school-number__label">最低分</span>
                  <strong class="school-number__value">{{ formatScore(school.minScore) }}</strong>
                </div>
                <div class="school-number school-number--years">
                  <span class="school-number__label">年份</span>
                  <strong class="school-number__value">{{ school.availableYearCount || 0 }}</strong>
                </div>
              </div>

              <button type="button" class="school-card__detail" @click.stop="openHistory(school)">
                查看历年
                <ChevronRight :size="16" />
              </button>
            </article>
          </div>

          <div v-if="totalPages > 1" class="gz-shell-panel score-pagination">
            <button type="button" class="gz-shell-action" :disabled="page <= 1" @click="prevPage">
              <ChevronLeft :size="16" />
              上一页
            </button>
            <div class="score-pagination__text">第 {{ page }} / {{ totalPages }} 页 · 共 {{ total.toLocaleString() }} {{ isProfessionalGroupProvince ? '个专业组' : '所院校' }}</div>
            <button type="button" class="gz-shell-action" :disabled="page >= totalPages" @click="nextPage">
              下一页
              <ChevronRight :size="16" />
            </button>
          </div>
        </div>

        <aside v-if="isDesktop" class="gz-shell-panel history-sidebar">
          <div v-if="!selectedSchool" class="history-empty">
            <History :size="28" />
            <h3>选择一所院校</h3>
            <p>点击左侧行后，这里会显示历年{{ isProfessionalGroupProvince ? '院校专业组调档线' : '院校级投档线' }}。</p>
          </div>
          <section v-else class="history-panel history-panel--sidebar">
            <div class="history-panel__header">
              <div>
                <div class="history-panel__kicker">{{ isProfessionalGroupProvince ? '院校专业组历年调档线' : '院校历年分数线' }}</div>
                <h2 class="history-panel__title">{{ selectedSchool.universityName }}</h2>
                <p class="history-panel__desc">
                  {{ subjectType }} · {{ selectedYearRange }} · {{ isProfessionalGroupProvince ? '院校专业组调档线' : '院校级投档线' }}，仅供填报复核参考。
                </p>
              </div>
            </div>

            <div v-if="detailLoading" class="history-state">
              <van-loading size="24" color="#0f172a" />
              <span>正在加载历年分数线…</span>
            </div>

            <div v-else-if="historyLines.length === 0" class="history-state">
              <van-empty description="暂无历年院校分记录" />
            </div>

            <div v-else class="history-table">
              <div class="history-table__head">
                <span>年份</span>
                <span>最低分</span>
                <span>最低位次</span>
                <span>批次</span>
                <span>位次来源</span>
              </div>
              <div v-for="line in historyLines" :key="`${line.schoolId}-${line.year}-${line.id}`" class="history-table__row">
                <strong>{{ line.year }}年</strong>
                <span>{{ formatScore(line.minScore) }}</span>
                <span>{{ formatRank(line.minRank) }}</span>
                <span>{{ line.batch || '—' }}</span>
                <span
                  :class="['rank-source-tag', rankSourceClass(line.rankSourceType)]"
                  :title="line.rankSourceNote || rankSourceLabel(line.rankSourceType)"
                >
                  {{ rankSourceLabel(line.rankSourceType) }}
                </span>
              </div>
            </div>

            <div class="history-panel__notice">
              <Hash :size="16" />
              <span>同分排序、招生计划变化、专业热度变化会影响实际填报，最终以{{ currentProvince.officialSource }}和高校官方信息为准。</span>
            </div>
          </section>
        </aside>
      </div>
    </div>

    <van-popup
      v-if="!isDesktop"
      v-model:show="detailVisible"
      round
      closeable
      position="bottom"
      :style="{ maxHeight: '86vh' }"
    >
      <section class="history-panel">
        <div class="history-panel__header">
          <div>
            <div class="history-panel__kicker">{{ isProfessionalGroupProvince ? '院校专业组历年调档线' : '院校历年分数线' }}</div>
            <h2 class="history-panel__title">{{ selectedSchool?.universityName || '院校详情' }}</h2>
            <p class="history-panel__desc">
              {{ subjectType }} · {{ selectedYearRange }} · {{ isProfessionalGroupProvince ? '院校专业组调档线' : '院校级投档线' }}，仅供填报复核参考。
            </p>
          </div>
        </div>

        <div v-if="detailLoading" class="history-state">
          <van-loading size="24" color="#0f172a" />
          <span>正在加载历年分数线…</span>
        </div>

        <div v-else-if="historyLines.length === 0" class="history-state">
          <van-empty description="暂无历年院校分记录" />
        </div>

        <div v-else class="history-table">
          <div class="history-table__head">
            <span>年份</span>
            <span>最低分</span>
            <span>最低位次</span>
            <span>批次</span>
            <span>位次来源</span>
          </div>
          <div v-for="line in historyLines" :key="`${line.schoolId}-${line.year}-${line.id}`" class="history-table__row">
            <strong>{{ line.year }}年</strong>
            <span>{{ formatScore(line.minScore) }}</span>
            <span>{{ formatRank(line.minRank) }}</span>
            <span>{{ line.batch || '—' }}</span>
            <span
              :class="['rank-source-tag', rankSourceClass(line.rankSourceType)]"
              :title="line.rankSourceNote || rankSourceLabel(line.rankSourceType)"
            >
              {{ rankSourceLabel(line.rankSourceType) }}
            </span>
          </div>
        </div>

        <div class="history-panel__notice">
          <Hash :size="16" />
          <span>同分排序、招生计划变化、专业热度变化会影响实际填报，最终以{{ currentProvince.officialSource }}和高校官方信息为准。</span>
        </div>
        <button type="button" class="history-panel__close" @click="detailVisible = false">
          关闭
        </button>
      </section>
    </van-popup>
  </div>
</template>

<style scoped>
.score-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.score-toolbar {
  padding: 20px;
}

.score-toolbar__grid {
  display: grid;
  gap: 18px;
}

.toolbar-block {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.score-segment {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 6px;
  border-radius: 18px;
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
  border-radius: 14px;
  background: transparent;
  color: #475569;
  font-size: 14px;
  font-weight: 600;
  transition: background 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
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
  border-radius: 16px;
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

.score-search-field__input::placeholder {
  color: #94a3b8;
}

.score-results-head {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 4px;
}

.score-results-head__title {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
  color: #0f172a;
}

.score-results-head__desc,
.score-results-head__meta {
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.score-state {
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #64748b;
}

.score-workbench {
  display: grid;
  gap: 16px;
}

.score-workbench__list,
.school-list {
  display: grid;
  gap: 10px;
}

.school-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 14px;
  cursor: pointer;
  transition: transform 0.16s ease, border-color 0.16s ease, box-shadow 0.16s ease;
}

.school-card:hover {
  transform: translateY(-1px);
  border-color: #bfdbfe;
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.08);
}

.school-card:focus-visible {
  outline: 3px solid rgba(37, 99, 235, 0.22);
  outline-offset: 2px;
}

.school-card.is-selected {
  border-color: #93c5fd;
  box-shadow: 0 12px 30px rgba(37, 99, 235, 0.12);
}

.school-card__rank {
  display: none;
}

.school-card__main {
  min-width: 0;
}

.school-card__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.school-card__badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  border: 1px solid #dbe4ef;
  background: #f8fafc;
  color: #475569;
  font-size: 11px;
  font-weight: 600;
}

.school-card__badge--year {
  border-color: rgba(37, 99, 235, 0.14);
  background: #eff6ff;
  color: #1d4ed8;
}

.school-card__name {
  font-size: 17px;
  line-height: 1.35;
  font-weight: 750;
  color: #0f172a;
}

.school-card__desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.5;
  color: #64748b;
}

.school-card__numbers {
  display: flex;
  gap: 8px;
}

.school-number {
  min-width: 82px;
  min-height: 58px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #edf2f7;
  background: #f8fafc;
}

.school-number__label {
  display: block;
  font-size: 12px;
  line-height: 1.4;
  color: #64748b;
}

.school-number__value {
  display: block;
  margin-top: 5px;
  font-size: 20px;
  line-height: 1.05;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.school-card__detail {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 38px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid #bfdbfe;
  background: #fff;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
  grid-column: 1 / -1;
  justify-self: end;
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

.history-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 22px 18px calc(22px + env(safe-area-inset-bottom));
  background: #fff;
}

.history-sidebar {
  padding: 0;
  overflow: hidden;
}

.history-panel--sidebar {
  padding: 18px;
}

.history-empty {
  min-height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px;
  text-align: center;
  color: #64748b;
}

.history-empty svg {
  color: #2563eb;
}

.history-empty h3 {
  font-size: 18px;
  line-height: 1.3;
  color: #0f172a;
}

.history-empty p {
  max-width: 260px;
  font-size: 13px;
  line-height: 1.7;
}

.history-panel__header {
  padding-right: 32px;
}

.history-panel__kicker {
  font-size: 12px;
  line-height: 1.5;
  font-weight: 700;
  color: #2563eb;
  text-transform: uppercase;
}

.history-panel__title {
  margin-top: 4px;
  font-size: 22px;
  line-height: 1.25;
  color: #0f172a;
}

.history-panel__desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.history-state {
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #64748b;
}

.history-table {
  display: grid;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
}

.history-table__head,
.history-table__row {
  display: grid;
  grid-template-columns: 70px 70px minmax(92px, 1fr) minmax(76px, 0.9fr) minmax(112px, 1fr);
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
}

.history-table__head {
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.history-table__row {
  min-height: 54px;
  border-bottom: 1px solid #edf2f7;
  font-size: 15px;
  line-height: 1.4;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.history-table__row:last-child {
  border-bottom: none;
}

.history-table__row span:last-child {
  font-size: 13px;
}

.rank-source-tag {
  justify-self: start;
  max-width: 100%;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  line-height: 1.2;
  white-space: nowrap;
}

.rank-source-tag.is-original {
  color: #047857;
  background: #ecfdf5;
}

.rank-source-tag.is-converted {
  color: #1d4ed8;
  background: #eff6ff;
}

.rank-source-tag.is-missing {
  color: #b45309;
  background: #fff7ed;
}

.history-panel__notice {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid #fde68a;
  background: #fffbeb;
  color: #92400e;
  font-size: 12px;
  line-height: 1.7;
}

.history-panel__notice svg {
  flex-shrink: 0;
  margin-top: 2px;
}

.history-panel__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  border-radius: 12px;
  border: none;
  background: #0f172a;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
}

@media (min-width: 768px) {
  .score-toolbar {
    padding: 24px;
  }

  .score-results-head {
    flex-direction: row;
    align-items: flex-end;
    justify-content: space-between;
  }

  .score-pagination {
    flex-direction: row;
    justify-content: space-between;
  }

  .history-panel {
    max-width: 880px;
    margin: 0 auto;
    padding: 26px 26px calc(26px + env(safe-area-inset-bottom));
  }

  .history-table__head,
  .history-table__row {
    grid-template-columns: 82px 78px minmax(104px, 1fr) minmax(88px, 0.9fr) minmax(126px, 1fr);
  }
}

@media (min-width: 1024px) {
  .score-hero {
    grid-template-columns: minmax(0, 1.15fr) minmax(340px, 0.85fr);
    align-items: end;
  }

  .score-toolbar__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    align-items: start;
  }

  .toolbar-block--search {
    min-height: 100%;
  }

  .score-workbench {
    grid-template-columns: minmax(0, 1fr) 430px;
    align-items: start;
  }

  .history-sidebar {
    position: sticky;
    top: 92px;
  }

  .school-card {
    grid-template-columns: 100px minmax(0, 1fr) 178px 112px;
    min-height: 86px;
  }

  .school-card__rank {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 56px;
    border-radius: 14px;
    background: #f8fafc;
    border: 1px solid #edf2f7;
    color: #0f172a;
    font-size: 20px;
    font-weight: 800;
    font-variant-numeric: tabular-nums;
  }

  .school-card__detail {
    grid-column: auto;
    justify-self: stretch;
  }

  .history-panel__title {
    font-size: 20px;
  }
}

@media (max-width: 640px) {
  .school-card__numbers {
    flex-direction: column;
  }

  .school-card {
    grid-template-columns: minmax(0, 1fr);
  }

  .school-card__detail {
    width: 100%;
    justify-self: stretch;
  }

  .history-table {
    overflow-x: auto;
  }

  .history-table__head,
  .history-table__row {
    min-width: 600px;
  }
}
</style>
