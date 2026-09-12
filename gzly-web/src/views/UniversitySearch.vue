<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUniversityList } from '@/api/university'
import { ArrowLeft, MapPin, Search, Crown, Award, Star, Building2, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { getProvinceConfig, normalizeProvinceCode } from '@/constants/provinces'
import type { University } from '@/types'

defineOptions({ name: 'UniversitySearch' })

const router = useRouter()
const route = useRoute()
const keyword = ref('')
const activeTag = ref('全部')
const activeRegion = ref('全部地区')
const loading = ref(false)
const errorMessage = ref('')
const total = ref(0)
const page = ref(1)
const pageSize = 20

const tagOptions = [
  { key: '全部', icon: null, color: '#17181c' },
  { key: '985', icon: Crown, color: '#a03535' },
  { key: '211', icon: Award, color: '#8a6d3b' },
  { key: '双一流', icon: Star, color: '#17181c' },
  { key: '公办', icon: Building2, color: '#4b4d54' },
  { key: '民办', icon: Building2, color: '#7c5f33' },
]

const regionOptions = [
  '全部地区',
  '华北',
  '东北',
  '华东',
  '华中',
  '华南',
  '西南',
  '西北',
  '港澳台',
]

const allUniversities = ref<University[]>([])

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const currentProvinceCode = computed(() => normalizeProvinceCode(route.query.provinceCode))
const currentProvince = computed(() => getProvinceConfig(currentProvinceCode.value))
const visibleRangeText = computed(() => {
  if (total.value === 0) return '0'
  const start = (page.value - 1) * pageSize + 1
  const end = Math.min(page.value * pageSize, total.value)
  return `${start}-${end}`
})
const activeFilterLabel = computed(() => activeTag.value === '全部' ? '全部院校' : activeTag.value)
const heroTags = computed(() => {
  const tags = [`${currentProvince.value.name}专区`, activeFilterLabel.value, activeRegion.value, `第 ${page.value} / ${totalPages.value} 页`]
  if (keyword.value.trim()) {
    tags.push(`关键词：${keyword.value.trim()}`)
  } else {
    tags.push('未输入关键词')
  }
  return tags
})

function wait(ms: number) {
  return new Promise(resolve => window.setTimeout(resolve, ms))
}

async function fetchList(allowRetry = true) {
  loading.value = true
  errorMessage.value = ''
  try {
    const tag = activeTag.value !== '全部' ? activeTag.value : undefined
    const region = activeRegion.value !== '全部地区' ? activeRegion.value : undefined
    const res = await getUniversityList({
      keyword: keyword.value.trim() || undefined,
      provinceCode: currentProvinceCode.value,
      tag,
      region,
      page: page.value,
      pageSize,
    })
    if (res.data.code === 0) {
      allUniversities.value = res.data.data.items || []
      total.value = res.data.data.total || 0
    }
  } catch (error: any) {
    if (allowRetry) {
      errorMessage.value = '院校数据加载失败，正在自动重试…'
      await wait(800)
      return fetchList(false)
    }
    allUniversities.value = []
    total.value = 0
    errorMessage.value = error?.message || '院校数据加载失败，请点击“开始检索”重试'
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  fetchList()
}

function onTagClick(key: string) {
  activeTag.value = key === activeTag.value && key !== '全部' ? '全部' : key
  page.value = 1
  fetchList()
}

function onRegionClick(region: string) {
  activeRegion.value = region === activeRegion.value && region !== '全部地区' ? '全部地区' : region
  page.value = 1
  fetchList()
}

function resetFilters() {
  keyword.value = ''
  activeTag.value = '全部'
  activeRegion.value = '全部地区'
  page.value = 1
  fetchList()
}

function prevPage() {
  if (page.value <= 1) return
  page.value -= 1
  fetchList()
}

function nextPage() {
  if (page.value >= totalPages.value) return
  page.value += 1
  fetchList()
}

function tagColor(tag: string): string {
  const map: Record<string, string> = {
    '985': '#a03535',
    '211': '#8a6d3b',
    '双一流': '#17181c',
    '公办': '#4b4d54',
    '民办': '#7c5f33',
    '中外合作办学': '#4b4d54',
  }
  return map[tag] || '#4b4d54'
}

function tierStyle(tags: string[]) {
  if (tags.includes('985')) return { accent: '#a03535', soft: 'rgba(185, 28, 28, 0.08)', label: '985' }
  if (tags.includes('211')) return { accent: '#8a6d3b', soft: 'rgba(138, 109, 59, 0.08)', label: '211' }
  if (tags.includes('双一流')) return { accent: '#17181c', soft: 'rgba(23, 24, 28, 0.08)', label: '双一流' }
  if (tags.includes('民办')) return { accent: '#7c5f33', soft: 'rgba(146, 64, 14, 0.08)', label: '民办' }
  return { accent: '#4b4d54', soft: 'rgba(15, 118, 110, 0.08)', label: '公办' }
}

function displayTags(uni: University) {
  const tags = [...(uni.tags || [])]
  if (!tags.length) {
    if (uni.natureName) tags.push(uni.natureName)
    if (uni.typeName) tags.push(uni.typeName)
  }
  return tags.slice(0, 4)
}

function headlineTag(uni: University) {
  const priorities = ['985', '211', '双一流', '公办', '民办']
  const matched = priorities.find(tag => (uni.tags || []).includes(tag))
  return matched || uni.natureName || uni.typeName || '院校'
}

function specialTag(uni: University) {
  return displayTags(uni)
    .find(tag => !['985', '211', '双一流', '公办', '民办'].includes(tag)) || ''
}

function primaryMeta(uni: University) {
  return specialTag(uni) || uni.natureName || '办学性质待补充'
}

function secondaryMeta(uni: University) {
  return uni.typeName || '院校类型待补充'
}

function activeTagStyle(key: string, color: string) {
  if (activeTag.value !== key) return {}
  if (key === '全部') {
    return { background: '#17181c', borderColor: '#17181c', color: '#fff' }
  }
  return {
    background: `${color}14`,
    borderColor: `${color}33`,
    color,
  }
}

onMounted(() => {
  fetchList()
})

function goRegionHome(): void {
  router.push({ path: `/region/${currentProvinceCode.value}`, query: currentProvince.value.routeQuery })
}
</script>

<template>
  <div class="gz-shell-page university-page">
    <header class="gz-shell-header">
      <div class="gz-shell-header-inner">
        <button type="button" class="gz-shell-back" aria-label="返回地区工作台" @click="goRegionHome">
          <ArrowLeft :size="20" />
        </button>
        <div class="gz-shell-heading">
          <div class="gz-shell-title">院校查询</div>
          <div class="gz-shell-subtitle">先缩小学校池，再进详情页核对章程、专业目录和城市机会</div>
        </div>
        <div class="gz-shell-header-extra">{{ total.toLocaleString() }} 所</div>
      </div>
    </header>

    <div class="gz-shell-main university-main">
      <section class="gz-shell-hero gz-shell-hero--photo university-hero" style="--gz-hero-photo-y: 78%">
        <div class="university-hero__copy">
          <span class="gz-shell-kicker">全国院校库</span>
          <h1 class="gz-shell-hero-title">把学校筛到足够窄，再决定值不值得重点看</h1>
          <p class="gz-shell-hero-desc">
            先按标签、关键词和城市缩小范围，再进入详情页仔细比较。{{ currentProvince.universityContextHint }}
          </p>
          <div class="gz-shell-chip-row university-hero__filters">
            <span v-for="tag in heroTags" :key="tag" class="gz-shell-chip is-soft-active">{{ tag }}</span>
          </div>
        </div>

        <div class="gz-shell-metrics">
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">命中院校</span>
            <span class="gz-shell-metric-value">{{ total.toLocaleString() }}</span>
            <span class="gz-shell-metric-note">按当前标签与关键词筛选</span>
          </div>
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">当前标签</span>
            <span class="gz-shell-metric-value">{{ activeFilterLabel }}</span>
            <span class="gz-shell-metric-note">支持 985 / 211 / 双一流 / 公办 / 民办</span>
          </div>
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">当前页范围</span>
            <span class="gz-shell-metric-value">{{ visibleRangeText }}</span>
            <span class="gz-shell-metric-note">每页 20 所院校</span>
          </div>
          <div class="gz-shell-metric">
            <span class="gz-shell-metric-label">检索状态</span>
            <span class="gz-shell-metric-value">{{ keyword.trim() ? '已输入' : '未输入' }}</span>
            <span class="gz-shell-metric-note">{{ keyword.trim() || '可按校名、省份关键词快速过滤' }}</span>
          </div>
        </div>
      </section>

      <section class="gz-shell-panel university-context-card">
        <div>
          <h2>{{ currentProvince.name }}地区上下文</h2>
          <p>{{ currentProvince.universityContextHint }}</p>
        </div>
        <div class="university-context-card__facts">
          <span>{{ currentProvince.targetBatch }}</span>
          <span>{{ currentProvince.targetCount }} 个{{ currentProvince.volunteerUnit }}</span>
          <span>{{ currentProvince.officialSource }}</span>
        </div>
      </section>

      <section class="gz-shell-panel university-toolbar">
        <div class="university-toolbar__top">
          <div class="university-search-field">
            <Search :size="18" class="university-search-field__icon" />
            <input
              v-model="keyword"
              class="university-search-field__input"
              type="text"
              placeholder="搜索院校名称、省份或城市"
              @keyup.enter="onSearch"
            >
          </div>
          <div class="gz-shell-actions">
            <button type="button" class="gz-shell-action gz-shell-action--primary" @click="onSearch">开始检索</button>
            <button type="button" class="gz-shell-action" @click="resetFilters">恢复默认</button>
          </div>
        </div>

        <div class="gz-shell-divider"></div>

        <div class="university-filter-row">
          <div class="gz-shell-panel-head">
            <div class="gz-shell-panel-title">常用标签</div>
            <div class="gz-shell-panel-desc">标签用于快速缩池，减少无效点击；再次点击已选标签可回到“全部”。</div>
          </div>
          <div class="university-tag-row">
            <button
              v-for="tag in tagOptions"
              :key="tag.key"
              type="button"
              class="university-tag-chip"
              :style="activeTagStyle(tag.key, tag.color)"
              @click="onTagClick(tag.key)"
            >
              <component :is="tag.icon" v-if="tag.icon" :size="14" />
              <span>{{ tag.key }}</span>
            </button>
          </div>
        </div>

        <div class="university-filter-row">
          <div class="gz-shell-panel-head">
            <div class="gz-shell-panel-title">地区分类</div>
            <div class="gz-shell-panel-desc">按大区快速查看学校分布，适合先按地域缩小候选范围。</div>
          </div>
          <div class="university-tag-row">
            <button
              v-for="region in regionOptions"
              :key="region"
              type="button"
              class="university-tag-chip university-tag-chip--region"
              :class="{ 'is-active': activeRegion === region }"
              @click="onRegionClick(region)"
            >
              <span>{{ region }}</span>
            </button>
          </div>
        </div>
      </section>

      <div class="university-results-head">
        <div>
          <h2 class="university-results-head__title">院校列表</h2>
          <p class="university-results-head__desc">卡片里只放最关键的信息：平台标签、办学性质、所在地和入口提示。</p>
        </div>
        <div class="university-results-head__meta">第 {{ page }} / {{ totalPages }} 页 · 当前展示 {{ visibleRangeText }}</div>
      </div>

      <div v-if="loading" class="university-state gz-shell-panel">
        <van-loading size="24" color="#17181c" />
        <span>正在加载院校数据…</span>
      </div>

      <div v-else-if="errorMessage" class="gz-shell-empty">
        <van-empty :description="errorMessage" />
        <button type="button" class="gz-shell-action gz-shell-action--primary" @click="fetchList()">
          重新加载数据
        </button>
      </div>

      <div v-else-if="allUniversities.length === 0" class="gz-shell-empty">
        <van-empty :description="`${currentProvince.shortName}当前条件下未找到匹配院校；入口保持开放，可调整关键词或先查看数据缺口。`" />
        <div class="university-empty-gap">
          <span>{{ currentProvince.scorelineStatusLabel }}</span>
          <span>{{ currentProvince.scorelineGapTypes.slice(0, 3).join('、') }}</span>
        </div>
      </div>

      <div v-else class="university-list">
        <article
          v-for="uni in allUniversities"
          :key="uni.id"
          class="gz-shell-panel school-row"
          @click="router.push({ path: `/university/${uni.id}`, query: currentProvince.routeQuery })"
        >
          <div class="school-row__main">
            <div class="school-avatar-wrap">
              <img
                v-if="uni.logoUrl"
                class="school-avatar school-avatar--logo"
                :src="uni.logoUrl"
                :alt="uni.name"
                loading="lazy"
                @error="($event.target as HTMLImageElement).style.display = 'none'; (($event.target as HTMLImageElement).nextElementSibling as HTMLElement).style.display = 'flex'"
              >
              <div
                class="school-avatar"
                :style="{ display: uni.logoUrl ? 'none' : 'flex', background: 'var(--school-soft)', color: 'var(--school-accent)' }"
              >
                {{ uni.name.charAt(0) }}
              </div>
            </div>

            <div class="school-row__body">
              <div class="school-row__eyebrow">
                <span class="school-row__label">{{ headlineTag(uni) }}</span>
                <span class="school-row__city">{{ uni.province }} / {{ uni.city }}</span>
              </div>
              <h3 class="school-row__name">{{ uni.name }}</h3>
              <div class="school-row__meta">
                <MapPin :size="13" />
                <span>{{ uni.province }} · {{ uni.city }}</span>
              </div>
              <div class="school-row__type">
                <span>{{ primaryMeta(uni) }}</span>
                <span class="school-row__sep">·</span>
                <span>{{ secondaryMeta(uni) }}</span>
              </div>
            </div>
          </div>

          <div class="school-row__side">
            <span class="school-row__side-city">{{ uni.province }} / {{ uni.city }}</span>
            <span class="school-row__side-type">{{ primaryMeta(uni) }}</span>
          </div>

          <div class="school-row__action">
            <span class="school-row__cta">查看详情</span>
            <ChevronRight :size="16" />
          </div>
        </article>
      </div>

      <div v-if="totalPages > 1" class="gz-shell-panel university-pagination">
        <button type="button" class="gz-shell-action" :disabled="page <= 1" @click="prevPage">
          <ChevronLeft :size="16" />
          上一页
        </button>
        <div class="university-pagination__text">第 {{ page }} / {{ totalPages }} 页 · 共 {{ total.toLocaleString() }} 所</div>
        <button type="button" class="gz-shell-action" :disabled="page >= totalPages" @click="nextPage">
          下一页
          <ChevronRight :size="16" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.university-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.university-context-card {
  display: grid;
  gap: 14px;
  padding: 18px;
}

.university-context-card h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.3;
  color: #17181c;
}

.university-context-card p {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.75;
  color: #6a6c72;
}

.university-context-card__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.university-context-card__facts span {
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

.university-empty-gap {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 12px;
}

.university-empty-gap span {
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  border-radius: 999px;
  background: #faf7ef;
  color: #7c5f33;
  font-size: 12px;
  font-weight: 700;
}

.university-toolbar {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.university-toolbar__top {
  display: grid;
  gap: 12px;
}

.university-search-field {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 0 16px;
  border-radius: 16px;
  border: 1px solid #e3e2de;
  background: #fafaf8;
}

.university-search-field__icon {
  color: #97999e;
  flex-shrink: 0;
}

.university-search-field__input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: #17181c;
}

.university-search-field__input::placeholder {
  color: #97999e;
}

.university-filter-row {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.university-tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.university-tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 40px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid #e3e2de;
  background: #fff;
  color: #383a40;
  font-size: 13px;
  font-weight: 600;
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.university-tag-chip:hover {
  transform: translateY(-1px);
  border-color: #cdccc7;
}

.university-tag-chip--region.is-active {
  background: #17181c;
  border-color: #17181c;
  color: #fff;
}

.university-results-head {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 4px;
}

.university-results-head__title {
  font-size: 18px;
  line-height: 1.2;
  font-weight: 700;
  color: #17181c;
}

.university-results-head__desc,
.university-results-head__meta,
.university-pagination__text {
  font-size: 13px;
  line-height: 1.7;
  color: #6a6c72;
}

.university-state {
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #6a6c72;
}

.university-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.school-row {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 20px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.school-row:hover {
  transform: translateY(-2px);
  border-color: rgba(23, 24, 28, 0.14);
  box-shadow: 0 16px 32px rgba(23, 24, 28, 0.06);
}

.school-row__main {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.school-avatar-wrap {
  flex-shrink: 0;
}

.school-avatar {
  width: 52px;
  height: 52px;
  border-radius: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.03em;
  border: 1px solid rgba(23, 24, 28, 0.08);
  background: #fafaf8;
  color: #17181c;
}

.school-avatar--logo {
  object-fit: contain;
  padding: 5px;
  background: #fff;
}

.school-row__body {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.school-row__eyebrow {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.school-row__label {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #17181c;
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.school-row__city {
  font-size: 12px;
  color: #97999e;
}

.school-row__name {
  margin-top: 8px;
  font-size: 18px;
  line-height: 1.32;
  font-weight: 800;
  color: #17181c;
  letter-spacing: -0.02em;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.school-row__meta,
.school-row__type {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: #6a6c72;
}

.school-row__meta {
  margin-top: 10px;
}

.school-row__type {
  flex-wrap: wrap;
}

.school-row__sep {
  color: #cdccc7;
}

.school-row__side {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #edf2f7;
}

.school-row__side-city,
.school-row__side-type {
  font-size: 12px;
  color: #97999e;
}

.school-row__action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #17181c;
}

.school-row__cta {
  font-size: 13px;
  font-weight: 800;
}

.university-pagination {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 18px;
}

@media (min-width: 768px) {
  .university-toolbar {
    padding: 24px;
  }

  .university-toolbar__top,
  .university-results-head {
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: end;
  }

  .university-toolbar__top {
    display: grid;
  }

  .university-results-head {
    display: grid;
  }

  .school-row {
    display: grid;
    grid-template-columns: minmax(0, 1.7fr) 220px 120px;
    align-items: center;
    gap: 20px;
    padding: 18px 22px;
  }

  .school-row__main {
    align-items: center;
  }

  .school-row__side {
    border-top: none;
    padding-top: 0;
    flex-direction: column;
    align-items: flex-end;
    justify-content: center;
    gap: 6px;
  }

  .school-row__action {
    justify-content: flex-end;
  }

  .university-pagination {
    flex-direction: row;
    justify-content: space-between;
  }
}

@media (min-width: 1024px) {
  .university-hero {
    grid-template-columns: minmax(0, 1.2fr) minmax(340px, 0.8fr);
    align-items: end;
  }

  .school-row {
    grid-template-columns: minmax(0, 1.9fr) 240px 120px;
  }
}

@media (min-width: 1400px) {
  .school-row {
    grid-template-columns: minmax(0, 2fr) 260px 120px;
  }
}

@media (max-width: 480px) {
  .school-row__eyebrow {
    flex-direction: column;
    align-items: flex-start;
  }

  .school-row__side {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
