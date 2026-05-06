<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { batchUpdateAdminOfficialLinkStatus, fetchAdminOfficialLinks, saveAdminOfficialLink } from '@/api/admin'
import type { AdminOfficialLinkItem, AdminOfficialLinkStats, OfficialLink } from '@/types'
import { showSuccessToast, showToast } from 'vant'
import { ArrowRight, CheckCheck, ExternalLink, Flame, Save, Search } from 'lucide-vue-next'
import { sanitizeHttpUrl } from '@/utils/markdown'

const route = useRoute()
const page = ref(1)
const pageSize = 20
const searchQuery = ref('')
const provinceFilter = ref('')
const statusFilter = ref<number | undefined>(undefined)
const missingField = ref(typeof route.query.missingField === 'string' ? route.query.missingField : '')
const sortBy = ref<'priority' | 'hot' | 'id'>('priority')
const priorityOnly = ref(true)
const windowDays = ref<30 | 90>(30)
const preserveNonEmpty = ref(true)
const loading = ref(false)
const saving = ref(false)
const batchSaving = ref(false)
const items = ref<AdminOfficialLinkItem[]>([])
const total = ref(0)
const stats = ref<AdminOfficialLinkStats>(createEmptyStats())
const selectedSchoolId = ref('')
const selectedSchoolIds = ref<string[]>([])
const LINK_FIELD_COUNT = 5
const STRUCTURED_FIELD_COUNT = 6

const MISSING_FIELD_LABELS: Record<string, string> = {
  tuitionInfoUrl: '缺收费链接',
  tuitionSummary: '缺收费摘要',
  admissionBrochureUrl: '缺招生章程',
  majorCatalogUrl: '缺专业目录',
  parsedContent: '未完成解析',
  majorCatalogSummary: '缺专业摘要',
  adjustmentRule: '缺调剂规则',
  foreignLanguageRule: '缺外语要求',
  physicalExamRule: '缺体检限制',
  singleSubjectRule: '缺单科要求',
  admissionSite: '缺招生网',
}

const PRIORITY_LEVEL_META: Record<'P0' | 'P1' | 'P2', { label: string; desc: string }> = {
  P0: { label: 'P0 · 先核验', desc: '高热度且存在关键缺口' },
  P1: { label: 'P1 · 本轮处理', desc: '有真实需求或缺口较多' },
  P2: { label: 'P2 · 后续补齐', desc: '热度较低，排在后面' },
}

const form = reactive<OfficialLink>({
  schoolId: '',
  schoolName: '',
  sourceDomain: '',
  schoolSite: '',
  admissionSite: '',
  admissionBrochureUrl: '',
  majorCatalogUrl: '',
  tuitionInfoUrl: '',
  tuitionRemark: '',
  tuitionSummary: '',
  majorCatalogSummary: '',
  adjustmentRule: '',
  foreignLanguageRule: '',
  physicalExamRule: '',
  singleSubjectRule: '',
  parserNotes: '',
  captureMethod: 'manual',
  captureStatus: 1,
  parseStatus: 0,
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const currentItem = computed<AdminOfficialLinkItem | null>(() => items.value.find(item => item.schoolId === selectedSchoolId.value) || null)
const allVisibleSelected = computed(() => items.value.length > 0 && items.value.every(item => selectedSchoolIds.value.includes(item.schoolId)))
const currentMissingFields = computed(() => currentItem.value?.missingFields || [])
const currentPriorityMeta = computed(() => currentItem.value ? PRIORITY_LEVEL_META[currentItem.value.priorityLevel] : null)

function createEmptyStats(): AdminOfficialLinkStats {
  return {
    totalUniversities: 0,
    linkedCount: 0,
    pendingCount: 0,
    emptyCount: 0,
    brochureCount: 0,
    majorCatalogCount: 0,
    tuitionCount: 0,
    tuitionSummaryCount: 0,
    majorSummaryCount: 0,
    parsedCount: 0,
    adjustmentCount: 0,
    foreignRuleCount: 0,
    physicalRuleCount: 0,
    singleSubjectCount: 0,
    activeWindowDays: 30,
    recentPlanCount: 0,
    fallbackTriggered: false,
    priorityQueueCount: 0,
    priorityP0Count: 0,
    priorityP1Count: 0,
    priorityP2Count: 0,
  }
}

function getLinkProgress(item: AdminOfficialLinkItem): number {
  return [
    item.officialLink?.schoolSite || item.schoolSite,
    item.officialLink?.admissionSite,
    item.officialLink?.admissionBrochureUrl,
    item.officialLink?.majorCatalogUrl,
    item.officialLink?.tuitionInfoUrl,
  ].filter(Boolean).length
}

function getStructuredProgress(item: AdminOfficialLinkItem): number {
  return [
    item.officialLink?.tuitionSummary,
    item.officialLink?.majorCatalogSummary,
    item.officialLink?.adjustmentRule,
    item.officialLink?.foreignLanguageRule,
    item.officialLink?.physicalExamRule,
    item.officialLink?.singleSubjectRule,
  ].filter((value) => typeof value === 'string' && value.trim()).length
}

function getPriorityText(level: 'P0' | 'P1' | 'P2'): string {
  return PRIORITY_LEVEL_META[level].label
}

function getMissingFieldLabel(field: string): string {
  return MISSING_FIELD_LABELS[field] || field
}

function isMissingFieldActive(field: string): boolean {
  return currentMissingFields.value.includes(field)
}

async function loadData(preferredSchoolId?: string) {
  loading.value = true
  try {
    const res = await fetchAdminOfficialLinks(
      page.value,
      pageSize,
      searchQuery.value,
      provinceFilter.value,
      statusFilter.value,
      missingField.value || undefined,
      sortBy.value,
      priorityOnly.value,
      windowDays.value,
    )
    const data = res.data?.data
    items.value = data?.items || []
    total.value = data?.total || 0
    stats.value = { ...createEmptyStats(), ...(data?.stats || {}) }

    const targetSchoolId = preferredSchoolId || selectedSchoolId.value
    if (targetSchoolId) {
      const found = items.value.find(item => item.schoolId === targetSchoolId)
      if (found) {
        selectItem(found)
        return
      }
    }

    if (items.value.length > 0) {
      selectItem(items.value[0])
    } else {
      selectedSchoolId.value = ''
    }
  } catch (error) {
    const err = error as Error
    showToast(err.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function selectItem(item: AdminOfficialLinkItem) {
  selectedSchoolId.value = item.schoolId
  const official: Partial<OfficialLink> = item.officialLink || {}
  form.schoolId = item.schoolId
  form.schoolName = item.name
  form.sourceDomain = official.sourceDomain || ''
  form.schoolSite = official.schoolSite || item.schoolSite || ''
  form.admissionSite = official.admissionSite || ''
  form.admissionBrochureUrl = official.admissionBrochureUrl || ''
  form.majorCatalogUrl = official.majorCatalogUrl || ''
  form.tuitionInfoUrl = official.tuitionInfoUrl || ''
  form.tuitionRemark = official.tuitionRemark || ''
  form.tuitionSummary = official.tuitionSummary || ''
  form.majorCatalogSummary = official.majorCatalogSummary || ''
  form.adjustmentRule = official.adjustmentRule || ''
  form.foreignLanguageRule = official.foreignLanguageRule || ''
  form.physicalExamRule = official.physicalExamRule || ''
  form.singleSubjectRule = official.singleSubjectRule || ''
  form.parserNotes = official.parserNotes || ''
  form.captureMethod = official.captureMethod || 'manual'
  form.captureStatus = official.captureStatus ?? (official.id ? 1 : 0)
  form.parseStatus = official.parseStatus ?? 0
}

function toggleSelect(schoolId: string) {
  const idx = selectedSchoolIds.value.indexOf(schoolId)
  if (idx > -1) {
    selectedSchoolIds.value.splice(idx, 1)
  } else {
    selectedSchoolIds.value.push(schoolId)
  }
}

function toggleSelectAllVisible() {
  const visibleIds = items.value.map(item => item.schoolId)
  if (allVisibleSelected.value) {
    selectedSchoolIds.value = selectedSchoolIds.value.filter(id => !visibleIds.includes(id))
    return
  }
  selectedSchoolIds.value = Array.from(new Set([...selectedSchoolIds.value, ...visibleIds]))
}

async function persistCurrent(advanceToNext: boolean) {
  if (!form.schoolId) {
    showToast('请先选择院校')
    return
  }

  const currentVisibleIds = items.value.map(item => item.schoolId)
  const currentIndex = currentVisibleIds.indexOf(form.schoolId)
  const nextCandidateIds = currentIndex > -1
    ? [...currentVisibleIds.slice(currentIndex + 1), ...currentVisibleIds.slice(0, currentIndex)]
    : []

  saving.value = true
  try {
    await saveAdminOfficialLink({ ...form, preserveNonEmpty: preserveNonEmpty.value })
    const successText = preserveNonEmpty.value
      ? (advanceToNext ? '已按仅补空策略保存并切到下一所' : '已按仅补空策略保存')
      : (advanceToNext ? '已覆盖保存并切到下一所' : '已覆盖保存')
    showSuccessToast(successText)
    await loadData()

    if (advanceToNext && items.value.length > 0) {
      const nextSchoolId = nextCandidateIds.find(id => items.value.some(item => item.schoolId === id)) || items.value[0]?.schoolId
      if (nextSchoolId) {
        const nextItem = items.value.find(item => item.schoolId === nextSchoolId)
        if (nextItem) selectItem(nextItem)
      }
    }
  } catch (error) {
    const err = error as Error
    showToast(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function batchUpdateStatus(status: number) {
  if (!selectedSchoolIds.value.length) {
    showToast('请先勾选学校')
    return
  }
  batchSaving.value = true
  try {
    await batchUpdateAdminOfficialLinkStatus(selectedSchoolIds.value, status)
    showSuccessToast(status === 1 ? '已批量标记为已收录' : '已批量标记为待核验')
    selectedSchoolIds.value = []
    await loadData(selectedSchoolId.value)
  } catch (error) {
    const err = error as Error
    showToast(err.message || '批量操作失败')
  } finally {
    batchSaving.value = false
  }
}

function openLink(url?: string) {
  const safe = sanitizeHttpUrl(url)
  if (!safe) {
    showToast('链接格式异常，已阻止打开')
    return
  }
  window.open(safe, '_blank', 'noopener,noreferrer')
}

function openVerificationBundle() {
  if (!currentItem.value) return

  const preferredUrls = [form.schoolSite, form.admissionSite]
  if (isMissingFieldActive('tuitionInfoUrl') || isMissingFieldActive('tuitionSummary')) {
    preferredUrls.push(form.tuitionInfoUrl, form.admissionBrochureUrl, form.admissionSite)
  }
  if (isMissingFieldActive('admissionBrochureUrl')) {
    preferredUrls.push(form.admissionBrochureUrl, form.admissionSite)
  }
  if (isMissingFieldActive('majorCatalogUrl')) {
    preferredUrls.push(form.majorCatalogUrl, form.admissionSite)
  }
  if (isMissingFieldActive('parsedContent')) {
    preferredUrls.push(form.admissionBrochureUrl, form.majorCatalogUrl, form.tuitionInfoUrl)
  }

  const urls = Array.from(new Set(preferredUrls.map(sanitizeHttpUrl).filter(Boolean)))
  if (!urls.length) {
    showToast('当前学校暂无可打开的核验入口')
    return
  }
  urls.slice(0, 4).forEach((url) => window.open(url, '_blank', 'noopener,noreferrer'))
}

let searchTimer: ReturnType<typeof setTimeout>
watch(searchQuery, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    loadData()
  }, 400)
})

watch([provinceFilter, statusFilter, missingField, sortBy, priorityOnly, windowDays], () => {
  page.value = 1
  loadData()
})

watch(page, () => loadData())

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="official-page">
    <div class="page-inner">
      <div class="hero-card gz-card">
        <div class="hero-copy">
          <div class="hero-eyebrow">热门学校人工核验工作台</div>
          <h1 class="page-title">按真实需求优先补“收费 / 章程 / 专业目录 / 解析规则”</h1>
          <p class="page-desc">
            默认只展示热门待核验学校，热度来自近 {{ stats.activeWindowDays }} 天志愿方案命中；
            <span v-if="stats.fallbackTriggered">近 30 天样本不足，已自动回退 90 天。</span>
            <span v-else>支持切换热度窗口与排序方式。</span>
          </p>
          <div class="hero-tags">
            <span class="hero-tag"><Flame :size="14" /> 队列 {{ stats.priorityQueueCount }}</span>
            <span class="hero-tag">样本 {{ stats.recentPlanCount }}</span>
            <span class="hero-tag">P0 {{ stats.priorityP0Count }}</span>
            <span class="hero-tag">P1 {{ stats.priorityP1Count }}</span>
            <span class="hero-tag">P2 {{ stats.priorityP2Count }}</span>
          </div>
        </div>
        <div class="hero-stats">
          <div class="hero-stat-card">
            <span>收费摘要覆盖</span>
            <strong>{{ stats.tuitionSummaryCount }}</strong>
          </div>
          <div class="hero-stat-card">
            <span>招生章程覆盖</span>
            <strong>{{ stats.brochureCount }}</strong>
          </div>
          <div class="hero-stat-card">
            <span>专业目录覆盖</span>
            <strong>{{ stats.majorCatalogCount }}</strong>
          </div>
          <div class="hero-stat-card">
            <span>规则已解析</span>
            <strong>{{ stats.parsedCount }}</strong>
          </div>
        </div>
      </div>

      <div class="toolbar toolbar--top">
        <div class="search-box">
          <Search :size="16" />
          <input v-model="searchQuery" placeholder="搜索院校名称" class="search-input" />
        </div>
        <div class="result-info">当前结果 <strong>{{ total }}</strong> 所院校</div>
      </div>

      <div class="control-grid">
        <section class="control-card gz-card">
          <span class="control-title">视图范围</span>
          <div class="filter-tabs compact-tabs">
            <button class="filter-tab" :class="{ active: priorityOnly }" @click="priorityOnly = true">热门待核验</button>
            <button class="filter-tab" :class="{ active: !priorityOnly }" @click="priorityOnly = false">查看全部</button>
          </div>
        </section>

        <section class="control-card gz-card">
          <span class="control-title">热度窗口</span>
          <div class="filter-tabs compact-tabs">
            <button class="filter-tab" :class="{ active: windowDays === 30 }" @click="windowDays = 30">近 30 天</button>
            <button class="filter-tab" :class="{ active: windowDays === 90 }" @click="windowDays = 90">近 90 天</button>
          </div>
        </section>

        <section class="control-card gz-card">
          <span class="control-title">排序方式</span>
          <div class="filter-tabs compact-tabs">
            <button class="filter-tab" :class="{ active: sortBy === 'priority' }" @click="sortBy = 'priority'">优先级</button>
            <button class="filter-tab" :class="{ active: sortBy === 'hot' }" @click="sortBy = 'hot'">热度</button>
            <button class="filter-tab" :class="{ active: sortBy === 'id' }" @click="sortBy = 'id'">学校ID</button>
          </div>
        </section>
      </div>

      <div class="stats-grid">
        <div class="stat-card">
          <span class="stat-label">总学校数</span>
          <strong class="stat-value">{{ stats.totalUniversities }}</strong>
        </div>
        <div class="stat-card">
          <span class="stat-label">已收录</span>
          <strong class="stat-value">{{ stats.linkedCount }}</strong>
        </div>
        <div class="stat-card">
          <span class="stat-label">待核验</span>
          <strong class="stat-value">{{ stats.pendingCount }}</strong>
        </div>
        <div class="stat-card">
          <span class="stat-label">待补充</span>
          <strong class="stat-value">{{ stats.emptyCount }}</strong>
        </div>
        <div class="stat-card stat-card--accent">
          <span class="stat-label">热门待核验队列</span>
          <strong class="stat-value">{{ stats.priorityQueueCount }}</strong>
        </div>
      </div>

      <div class="filter-tabs">
        <button class="filter-tab" :class="{ active: statusFilter === undefined }" @click="statusFilter = undefined">全部状态</button>
        <button class="filter-tab" :class="{ active: statusFilter === 1 }" @click="statusFilter = 1">已收录</button>
        <button class="filter-tab" :class="{ active: statusFilter === 2 }" @click="statusFilter = 2">待核验</button>
        <button class="filter-tab" :class="{ active: statusFilter === 0 }" @click="statusFilter = 0">待补充</button>
      </div>

      <div class="filter-section">
        <span class="filter-label">区域范围</span>
        <div class="filter-tabs">
          <button class="filter-tab" :class="{ active: provinceFilter === '' }" @click="provinceFilter = ''">全部省份</button>
          <button class="filter-tab" :class="{ active: provinceFilter === '贵州' }" @click="provinceFilter = '贵州'">贵州本省</button>
        </div>
      </div>

      <div class="filter-section">
        <span class="filter-label">关键缺口</span>
        <div class="filter-tabs">
          <button class="filter-tab" :class="{ active: missingField === '' }" @click="missingField = ''">缺口不限</button>
          <button class="filter-tab" :class="{ active: missingField === 'tuitionInfoUrl' }" @click="missingField = 'tuitionInfoUrl'">缺收费链接</button>
          <button class="filter-tab" :class="{ active: missingField === 'tuitionSummary' }" @click="missingField = 'tuitionSummary'">缺收费摘要</button>
          <button class="filter-tab" :class="{ active: missingField === 'admissionBrochureUrl' }" @click="missingField = 'admissionBrochureUrl'">缺招生章程</button>
          <button class="filter-tab" :class="{ active: missingField === 'majorCatalogUrl' }" @click="missingField = 'majorCatalogUrl'">缺专业目录</button>
          <button class="filter-tab" :class="{ active: missingField === 'parsedContent' }" @click="missingField = 'parsedContent'">未完成解析</button>
        </div>
      </div>

      <div class="filter-section">
        <span class="filter-label">结构化字段</span>
        <div class="filter-tabs">
          <button class="filter-tab" :class="{ active: missingField === 'majorCatalogSummary' }" @click="missingField = 'majorCatalogSummary'">缺专业摘要</button>
          <button class="filter-tab" :class="{ active: missingField === 'adjustmentRule' }" @click="missingField = 'adjustmentRule'">缺调剂规则</button>
          <button class="filter-tab" :class="{ active: missingField === 'foreignLanguageRule' }" @click="missingField = 'foreignLanguageRule'">缺外语要求</button>
          <button class="filter-tab" :class="{ active: missingField === 'physicalExamRule' }" @click="missingField = 'physicalExamRule'">缺体检限制</button>
          <button class="filter-tab" :class="{ active: missingField === 'singleSubjectRule' }" @click="missingField = 'singleSubjectRule'">缺单科要求</button>
        </div>
      </div>

      <div class="batch-toolbar">
        <button class="batch-btn" @click="toggleSelectAllVisible">
          <CheckCheck :size="14" />
          {{ allVisibleSelected ? '取消全选本页' : '全选本页' }}
        </button>
        <button class="batch-btn" :disabled="batchSaving || !selectedSchoolIds.length" @click="batchUpdateStatus(1)">批量标记已收录</button>
        <button class="batch-btn" :disabled="batchSaving || !selectedSchoolIds.length" @click="batchUpdateStatus(2)">批量标记待核验</button>
      </div>

      <div class="layout-grid">
        <div class="table-card gz-card">
          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th style="width: 44px">选</th>
                  <th>院校</th>
                  <th>热度</th>
                  <th>缺口</th>
                  <th>优先级</th>
                  <th>状态</th>
                  <th>进度</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in items"
                  :key="item.schoolId"
                  class="table-row"
                  :class="{ active: selectedSchoolId === item.schoolId }"
                  @click="selectItem(item)"
                >
                  <td @click.stop="">
                    <input type="checkbox" :checked="selectedSchoolIds.includes(item.schoolId)" @change="toggleSelect(item.schoolId)" />
                  </td>
                  <td>
                    <div class="uni-name">{{ item.name }}</div>
                    <div class="uni-sub">{{ item.schoolId }} · {{ item.province }} · {{ item.city }}</div>
                  </td>
                  <td>
                    <div class="heat-box">
                      <strong>{{ item.hotScore }}</strong>
                      <span>命中 {{ item.planHitCount }}</span>
                    </div>
                  </td>
                  <td>
                    <div class="missing-list">
                      <span v-for="field in item.missingFields.slice(0, 3)" :key="field" class="missing-chip">
                        {{ getMissingFieldLabel(field) }}
                      </span>
                      <span v-if="item.missingFields.length > 3" class="missing-chip missing-chip--muted">+{{ item.missingFields.length - 3 }}</span>
                    </div>
                  </td>
                  <td>
                    <div class="priority-box" :class="`priority-box--${item.priorityLevel.toLowerCase()}`">
                      <strong>{{ getPriorityText(item.priorityLevel) }}</strong>
                      <span>{{ item.priorityReasons[0] || '等待人工核验' }}</span>
                    </div>
                  </td>
                  <td>
                    <span
                      class="status-chip"
                      :class="item.officialLink?.captureStatus === 1 ? 'status--ok' : item.officialLink?.captureStatus === 2 ? 'status--pending' : 'status--empty'"
                    >
                      {{ item.officialLink?.captureStatus === 1 ? '已收录' : item.officialLink?.captureStatus === 2 ? '待核验' : '待补充' }}
                    </span>
                  </td>
                  <td>
                    <div class="field-progress">
                      <span class="field-badge">入口 {{ getLinkProgress(item) }}/{{ LINK_FIELD_COUNT }}</span>
                      <span class="field-badge field-badge--parsed">规则 {{ getStructuredProgress(item) }}/{{ STRUCTURED_FIELD_COUNT }}</span>
                    </div>
                  </td>
                </tr>
                <tr v-if="!items.length">
                  <td colspan="7" class="td-empty">{{ loading ? '加载中...' : '暂无数据' }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="totalPages > 1" class="pagination">
            <button class="page-btn" :disabled="page <= 1" @click="page--">上一页</button>
            <span class="page-info">{{ page }} / {{ totalPages }}</span>
            <button class="page-btn" :disabled="page >= totalPages" @click="page++">下一页</button>
          </div>
        </div>

        <div class="editor-card gz-card">
          <div v-if="currentItem" class="editor-head">
            <div>
              <div class="editor-kicker">{{ currentPriorityMeta?.label }}</div>
              <h2>{{ currentItem.name }}</h2>
              <p>{{ currentItem.province }} · {{ currentItem.city }} · {{ currentItem.natureName || '普通' }} · 热度 {{ currentItem.hotScore }} / 命中 {{ currentItem.planHitCount }}</p>
            </div>
            <div class="editor-actions">
              <button class="secondary-btn" @click="openVerificationBundle">一键核验当前学校</button>
              <button class="save-btn save-btn--ghost" :disabled="saving" @click="persistCurrent(false)">
                <Save :size="15" />
                {{ saving ? '保存中...' : '保存' }}
              </button>
              <button class="save-btn" :disabled="saving" @click="persistCurrent(true)">
                <ArrowRight :size="15" />
                {{ saving ? '保存中...' : '保存并下一所' }}
              </button>
            </div>
          </div>

          <div v-if="currentItem" class="editor-summary">
            <div class="summary-card">
              <span class="summary-label">保存策略</span>
              <div class="filter-tabs filter-tabs--tight">
                <button class="filter-tab" :class="{ active: preserveNonEmpty }" @click="preserveNonEmpty = true">仅补空（推荐）</button>
                <button class="filter-tab" :class="{ active: !preserveNonEmpty }" @click="preserveNonEmpty = false">允许覆盖</button>
              </div>
              <p class="save-mode-tip">
                <template v-if="preserveNonEmpty">
                  仅补生产空字段，不覆盖已有非空值，也不会因表单留空把线上内容清掉。
                </template>
                <template v-else>
                  会按当前表单覆盖线上内容，请仅在确认已有值需要修正时使用。
                </template>
              </p>
            </div>
            <div class="summary-card summary-card--priority">
              <span class="summary-label">优先原因</span>
              <div class="summary-values">
                <span v-for="reason in currentItem.priorityReasons" :key="reason" class="summary-tag">{{ reason }}</span>
              </div>
            </div>
            <div class="summary-card">
              <span class="summary-label">当前缺口</span>
              <div class="summary-values">
                <span v-for="field in currentMissingFields" :key="field" class="summary-tag summary-tag--warn">
                  {{ getMissingFieldLabel(field) }}
                </span>
              </div>
            </div>
          </div>

          <div v-if="currentItem" class="form-grid">
            <div class="field-block">
              <label>学校官网</label>
              <div class="input-row">
                <input v-model="form.schoolSite" class="text-input" placeholder="https://..." />
                <button class="link-btn" @click="openLink(form.schoolSite)"><ExternalLink :size="14" /></button>
              </div>
            </div>

            <div class="field-block">
              <label>招生网</label>
              <div class="input-row">
                <input v-model="form.admissionSite" class="text-input" placeholder="https://..." />
                <button class="link-btn" @click="openLink(form.admissionSite)"><ExternalLink :size="14" /></button>
              </div>
            </div>

            <div class="field-block" :class="{ 'field-block--highlight': isMissingFieldActive('admissionBrochureUrl') }">
              <label>招生章程</label>
              <div class="input-row">
                <input v-model="form.admissionBrochureUrl" class="text-input" placeholder="https://..." />
                <button class="link-btn" @click="openLink(form.admissionBrochureUrl)"><ExternalLink :size="14" /></button>
              </div>
            </div>

            <div class="field-block" :class="{ 'field-block--highlight': isMissingFieldActive('majorCatalogUrl') }">
              <label>专业目录</label>
              <div class="input-row">
                <input v-model="form.majorCatalogUrl" class="text-input" placeholder="https://..." />
                <button class="link-btn" @click="openLink(form.majorCatalogUrl)"><ExternalLink :size="14" /></button>
              </div>
            </div>

            <div class="field-block" :class="{ 'field-block--highlight': isMissingFieldActive('tuitionInfoUrl') }">
              <label>收费标准</label>
              <div class="input-row">
                <input v-model="form.tuitionInfoUrl" class="text-input" placeholder="https://..." />
                <button class="link-btn" @click="openLink(form.tuitionInfoUrl)"><ExternalLink :size="14" /></button>
              </div>
            </div>

            <div class="field-block">
              <label>来源域名</label>
              <input v-model="form.sourceDomain" class="text-input" placeholder="zsb.xxx.edu.cn" />
            </div>

            <div class="field-block">
              <label>收费备注</label>
              <textarea v-model="form.tuitionRemark" class="text-area" placeholder="例如：中外合作学费较高，务必复核当年章程。"></textarea>
            </div>

            <div class="field-block" :class="{ 'field-block--highlight': isMissingFieldActive('tuitionSummary') }">
              <label>收费摘要</label>
              <textarea v-model="form.tuitionSummary" class="text-area" placeholder="自动解析或人工整理的收费摘要"></textarea>
            </div>

            <div class="field-block">
              <label>专业目录摘要</label>
              <textarea v-model="form.majorCatalogSummary" class="text-area" placeholder="自动解析出的主要专业或人工整理摘要"></textarea>
            </div>

            <div class="field-block">
              <label>调剂规则</label>
              <textarea v-model="form.adjustmentRule" class="text-area" placeholder="是否服从调剂、调剂范围等"></textarea>
            </div>

            <div class="field-block">
              <label>外语要求</label>
              <textarea v-model="form.foreignLanguageRule" class="text-area" placeholder="外语语种、口语考试要求等"></textarea>
            </div>

            <div class="field-block">
              <label>体检限制</label>
              <textarea v-model="form.physicalExamRule" class="text-area" placeholder="体检标准、色盲色弱、视力等要求"></textarea>
            </div>

            <div class="field-block">
              <label>单科要求</label>
              <textarea v-model="form.singleSubjectRule" class="text-area" placeholder="数学/英语/语文等单科成绩要求"></textarea>
            </div>

            <div class="field-block" :class="{ 'field-block--highlight': isMissingFieldActive('parsedContent') }">
              <label>解析备注</label>
              <textarea v-model="form.parserNotes" class="text-area" placeholder="统一建议：来源页 + 核验日期 + 人工备注"></textarea>
            </div>

            <div class="field-block field-inline">
              <label>来源方式</label>
              <select v-model="form.captureMethod" class="text-input">
                <option value="manual">manual</option>
                <option value="scraper">scraper</option>
              </select>
            </div>

            <div class="field-block field-inline">
              <label>状态</label>
              <select v-model="form.captureStatus" class="text-input">
                <option :value="0">待补充</option>
                <option :value="1">已收录</option>
                <option :value="2">待核验</option>
              </select>
            </div>

            <div class="field-block field-inline">
              <label>解析状态</label>
              <select v-model="form.parseStatus" class="text-input">
                <option :value="0">未解析</option>
                <option :value="1">已解析</option>
                <option :value="2">待复核</option>
              </select>
            </div>
          </div>
          <div v-else class="empty-editor">请选择左侧院校开始维护</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.official-page {
  min-height: 100%;
}

.page-inner {
  max-width: 1380px;
  margin: 0 auto;
  padding: var(--gz-space-5) var(--gz-space-4);
}

.hero-card {
  display: grid;
  gap: 18px;
  padding: 24px;
  margin-bottom: 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: linear-gradient(135deg, #ffffff 0%, #f8fbff 100%);
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.hero-eyebrow {
  display: inline-flex;
  width: fit-content;
  padding: 6px 12px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.page-title {
  font-size: 28px;
  line-height: 1.25;
  font-weight: 900;
  color: var(--gz-text-primary);
  margin: 0;
}

.page-desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--gz-text-secondary);
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hero-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.04);
  color: #334155;
  font-size: 12px;
  font-weight: 800;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.hero-stat-card {
  padding: 16px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.hero-stat-card span {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.hero-stat-card strong {
  display: block;
  margin-top: 8px;
  font-size: 26px;
  font-weight: 900;
  color: #0f172a;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
}

.search-box {
  flex: 1;
  min-width: 240px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  background: var(--gz-card-bg);
  border: 1.5px solid rgba(0, 0, 0, 0.08);
  border-radius: 14px;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  padding: 12px 0;
  font-size: 14px;
  color: var(--gz-text-primary);
}

.result-info {
  font-size: 13px;
  color: var(--gz-text-secondary);
}

.control-grid {
  display: grid;
  gap: 12px;
  margin-bottom: 14px;
}

.control-card {
  padding: 16px;
}

.control-title {
  display: block;
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 800;
  color: #64748b;
  letter-spacing: 0.04em;
}

.compact-tabs {
  margin-bottom: 0;
}

.stats-grid {
  display: grid;
  gap: 10px;
  margin-bottom: 14px;
}

.stat-card {
  padding: 16px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.stat-card--accent {
  background: linear-gradient(135deg, #eff6ff 0%, #f8fbff 100%);
  border-color: #bfdbfe;
}

.stat-label {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.stat-value {
  display: block;
  margin-top: 6px;
  font-size: 24px;
  font-weight: 900;
  color: #0f172a;
}

.filter-section {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
}

.filter-label {
  min-width: 72px;
  padding-top: 8px;
  font-size: 12px;
  font-weight: 800;
  color: #64748b;
  letter-spacing: 0.04em;
}

.filter-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.filter-section .filter-tabs {
  flex: 1;
  margin-bottom: 0;
}

.filter-tab {
  padding: 8px 14px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 999px;
  background: #fff;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.filter-tab.active {
  background: #eff6ff;
  color: #1d4ed8;
  border-color: #93c5fd;
}

.batch-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.batch-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 999px;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.batch-btn:disabled {
  opacity: 0.45;
}

.layout-grid {
  display: grid;
  gap: 16px;
}

.table-card,
.editor-card {
  padding: 0;
  overflow: hidden;
}

.table-wrap {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  min-width: 1020px;
}

.data-table th {
  padding: 12px var(--gz-space-3);
  text-align: left;
  font-weight: 700;
  font-size: 12px;
  color: var(--gz-text-tertiary);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(0, 0, 0, 0.015);
}

.data-table td {
  padding: 12px var(--gz-space-3);
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
  color: var(--gz-text-primary);
  vertical-align: top;
}

.table-row {
  cursor: pointer;
}

.table-row.active td {
  background: #eff6ff;
}

.uni-name {
  font-weight: 700;
  color: #111827;
}

.uni-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #94a3b8;
}

.heat-box,
.priority-box {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.heat-box strong,
.priority-box strong {
  font-size: 14px;
  color: #0f172a;
}

.heat-box span,
.priority-box span {
  font-size: 12px;
  color: #64748b;
}

.priority-box--p0 strong {
  color: #b91c1c;
}

.priority-box--p1 strong {
  color: #b45309;
}

.priority-box--p2 strong {
  color: #334155;
}

.missing-list,
.field-progress {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.missing-chip,
.field-badge,
.summary-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.missing-chip {
  background: #fff7ed;
  color: #c2410c;
}

.missing-chip--muted {
  background: #f1f5f9;
  color: #64748b;
}

.field-badge {
  background: #f8fafc;
  color: #475569;
}

.field-badge--parsed {
  background: #eef2ff;
  color: #4338ca;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status--ok {
  background: #ecfdf5;
  color: #047857;
}

.status--pending {
  background: #fffbeb;
  color: #b45309;
}

.status--empty {
  background: #f1f5f9;
  color: #64748b;
}

.td-empty {
  text-align: center;
  padding: 24px !important;
  color: #94a3b8;
}

.editor-card {
  padding: 18px;
}

.editor-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.editor-kicker {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12px;
  font-weight: 800;
}

.editor-head h2 {
  margin: 10px 0 0;
  font-size: 22px;
  font-weight: 900;
  color: #111827;
}

.editor-head p {
  margin-top: 6px;
  font-size: 13px;
  color: #64748b;
}

.editor-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.save-btn,
.secondary-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 40px;
  padding: 0 14px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 700;
}

.save-btn {
  border: none;
  background: #111827;
  color: #fff;
}

.save-btn--ghost {
  background: #fff;
  color: #111827;
  border: 1px solid rgba(15, 23, 42, 0.12);
}

.save-btn:disabled,
.secondary-btn:disabled {
  opacity: 0.45;
}

.secondary-btn {
  border: 1px solid rgba(37, 99, 235, 0.15);
  background: #eff6ff;
  color: #1d4ed8;
}

.editor-summary {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.05);
}

.summary-card--priority {
  background: linear-gradient(135deg, #fff7ed 0%, #ffffff 100%);
}

.summary-label {
  display: block;
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 800;
  color: #64748b;
  letter-spacing: 0.04em;
}

.summary-values {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-tabs--tight {
  margin-bottom: 10px;
}

.summary-tag {
  background: #ffffff;
  color: #334155;
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.summary-tag--warn {
  background: #fff7ed;
  color: #c2410c;
  border-color: rgba(234, 88, 12, 0.15);
}

.form-grid {
  display: grid;
  gap: 14px;
}

.field-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.save-mode-tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.field-block--highlight {
  padding: 12px;
  border-radius: 14px;
  background: #fff7ed;
  border: 1px solid rgba(249, 115, 22, 0.12);
}

.field-block label {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

.text-input,
.text-area {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  font-size: 14px;
  color: #111827;
  background: #fff;
  box-sizing: border-box;
}

.text-area {
  min-height: 96px;
  resize: vertical;
}

.input-row {
  display: flex;
  gap: 8px;
}

.input-row .text-input {
  flex: 1;
}

.link-btn {
  width: 40px;
  border: 1px solid rgba(37, 99, 235, 0.15);
  border-radius: 12px;
  background: #eff6ff;
  color: #1d4ed8;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.field-inline {
  max-width: 240px;
}

.empty-editor {
  padding: 40px 12px;
  text-align: center;
  color: #94a3b8;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--gz-space-3);
  padding: var(--gz-space-4);
  border-top: 1px solid rgba(0, 0, 0, 0.04);
}

.page-btn {
  padding: 6px 14px;
  border: 1.5px solid rgba(0, 0, 0, 0.08);
  border-radius: var(--gz-radius-sm);
  background: var(--gz-card-bg-solid);
  font-size: 13px;
  color: var(--gz-text-secondary);
  cursor: pointer;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: var(--gz-text-tertiary);
}

@media (min-width: 860px) {
  .control-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .stats-grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }

  .editor-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1100px) {
  .hero-card {
    grid-template-columns: minmax(0, 1.6fr) minmax(360px, 0.9fr);
    align-items: stretch;
  }

  .layout-grid {
    grid-template-columns: minmax(0, 1fr) 520px;
    align-items: start;
  }

  .editor-card {
    position: sticky;
    top: 74px;
  }
}
</style>
