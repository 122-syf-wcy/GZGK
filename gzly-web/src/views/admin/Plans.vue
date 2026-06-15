<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  batchDeleteAdminPlans,
  cleanupAdminTestPlans,
  deleteAdminPlan,
  fetchAdminPlans,
  restoreAdminPlan,
} from '@/api/admin'
import type { AdminPlanCleanupResponse, AdminPlanItem } from '@/types'
import { RotateCcw, Search, Trash2 } from 'lucide-vue-next'

const searchQuery = ref('')
const provinceCode = ref('')
const score = ref<number | undefined>()
const rank = ref<number | undefined>()
const startDate = ref('')
const endDate = ref('')
const itemCountZero = ref(false)
const anonymousOnly = ref(false)
const includeDeleted = ref(false)
const plans = ref<AdminPlanItem[]>([])
const selectedIds = ref<number[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const totalPages = ref(1)
const loading = ref(false)
const deleting = ref(false)
const cleanupLoading = ref(false)
const cleanupIdStart = ref<number | undefined>()
const cleanupIdEnd = ref<number | undefined>()
const cleanupBefore = ref('')
const cleanupOnlyZeroVolunteer = ref(true)
const cleanupOnlyAnonymous = ref(true)
const cleanupResult = ref<AdminPlanCleanupResponse | null>(null)

const allVisibleSelected = computed(() => (
  visibleSelectableIds.value.length > 0 && visibleSelectableIds.value.every(id => selectedIds.value.includes(id))
))

const visibleSelectableIds = computed(() => plans.value.filter(plan => !plan.deleted).map(plan => plan.id))

function numberOrUndefined(value: number | undefined): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function syncTotalPages() {
  totalPages.value = Math.max(1, Math.ceil(total.value / pageSize))
}

async function loadPlans() {
  loading.value = true
  try {
    const res = await fetchAdminPlans({
      page: page.value,
      size: pageSize,
      search: searchQuery.value.trim() || undefined,
      provinceCode: provinceCode.value.trim() || undefined,
      score: numberOrUndefined(score.value),
      rank: numberOrUndefined(rank.value),
      itemCountZero: itemCountZero.value || undefined,
      anonymous: anonymousOnly.value || undefined,
      startDate: startDate.value || undefined,
      endDate: endDate.value || undefined,
      includeDeleted: includeDeleted.value,
    })
    const data = res.data.data
    plans.value = data.items || []
    total.value = data.total || 0
    syncTotalPages()
    selectedIds.value = selectedIds.value.filter(id => visibleSelectableIds.value.includes(id))
  } catch (error: unknown) {
    window.alert(error instanceof Error ? error.message : '加载方案记录失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadPlans)

function doSearch() {
  page.value = 1
  void loadPlans()
}

function resetFilters() {
  searchQuery.value = ''
  provinceCode.value = ''
  score.value = undefined
  rank.value = undefined
  startDate.value = ''
  endDate.value = ''
  itemCountZero.value = false
  anonymousOnly.value = false
  includeDeleted.value = false
  selectedIds.value = []
  doSearch()
}

function prevPage() {
  if (page.value <= 1) return
  page.value -= 1
  void loadPlans()
}

function nextPage() {
  if (page.value >= totalPages.value) return
  page.value += 1
  void loadPlans()
}

function toggleSelected(id: number) {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter(item => item !== id)
    : [...selectedIds.value, id]
}

function toggleSelectAllVisible() {
  if (allVisibleSelected.value) {
    selectedIds.value = selectedIds.value.filter(id => !visibleSelectableIds.value.includes(id))
    return
  }
  selectedIds.value = Array.from(new Set([...selectedIds.value, ...visibleSelectableIds.value]))
}

async function deleteOne(plan: AdminPlanItem) {
  if (plan.deleted) return
  const ok = window.confirm(`确认将方案 #${plan.id} 移入已删除状态？该操作不会写正式招生数据。`)
  if (!ok) return
  deleting.value = true
  try {
    await deleteAdminPlan(plan.id, 'admin-ui-single-delete')
    selectedIds.value = selectedIds.value.filter(id => id !== plan.id)
    await loadPlans()
  } catch (error: unknown) {
    window.alert(error instanceof Error ? error.message : '删除失败')
  } finally {
    deleting.value = false
  }
}

async function restoreOne(plan: AdminPlanItem) {
  if (!plan.deleted) return
  const ok = window.confirm(`确认恢复方案 #${plan.id}？将从已删除状态还原为正常。`)
  if (!ok) return
  deleting.value = true
  try {
    await restoreAdminPlan(plan.id)
    await loadPlans()
  } catch (error: unknown) {
    window.alert(error instanceof Error ? error.message : '恢复失败')
  } finally {
    deleting.value = false
  }
}

async function deleteSelected() {
  if (!selectedIds.value.length) {
    window.alert('请先勾选要删除的方案记录')
    return
  }
  const ok = window.confirm(`确认批量软删除 ${selectedIds.value.length} 条方案记录？不会物理删除方案内容，也不会影响正式招生数据。`)
  if (!ok) return
  deleting.value = true
  try {
    await batchDeleteAdminPlans(selectedIds.value, 'admin-ui-batch-delete')
    selectedIds.value = []
    await loadPlans()
  } catch (error: unknown) {
    window.alert(error instanceof Error ? error.message : '批量删除失败')
  } finally {
    deleting.value = false
  }
}

async function previewCleanup() {
  cleanupLoading.value = true
  try {
    const res = await cleanupAdminTestPlans({
      idStart: numberOrUndefined(cleanupIdStart.value),
      idEnd: numberOrUndefined(cleanupIdEnd.value),
      before: cleanupBefore.value || undefined,
      onlyZeroVolunteer: cleanupOnlyZeroVolunteer.value,
      onlyAnonymous: cleanupOnlyAnonymous.value,
      provinceCode: provinceCode.value.trim() || undefined,
      score: numberOrUndefined(score.value),
      rank: numberOrUndefined(rank.value),
      dryRun: true,
    })
    cleanupResult.value = res.data.data
  } catch (error: unknown) {
    window.alert(error instanceof Error ? error.message : '清理预览失败')
  } finally {
    cleanupLoading.value = false
  }
}

async function executeCleanup() {
  if (!cleanupResult.value?.ids.length) {
    window.alert('请先 dryRun 预览候选记录')
    return
  }
  if (!cleanupIdStart.value && !cleanupIdEnd.value && !selectedIds.value.length) {
    window.alert('执行清理必须指定 ID 范围或先勾选精确记录，避免误删真实用户数据')
    return
  }
  const ids = selectedIds.value.length ? selectedIds.value : cleanupResult.value.ids
  const ok = window.confirm(`确认软删除 ${ids.length} 条测试方案记录？此操作需要管理员确认，且不会物理删除数据。`)
  if (!ok) return
  cleanupLoading.value = true
  try {
    const res = await cleanupAdminTestPlans({
      ids: selectedIds.value.length ? selectedIds.value : undefined,
      idStart: selectedIds.value.length ? undefined : numberOrUndefined(cleanupIdStart.value),
      idEnd: selectedIds.value.length ? undefined : numberOrUndefined(cleanupIdEnd.value),
      before: cleanupBefore.value || undefined,
      onlyZeroVolunteer: cleanupOnlyZeroVolunteer.value,
      onlyAnonymous: cleanupOnlyAnonymous.value,
      provinceCode: provinceCode.value.trim() || undefined,
      score: numberOrUndefined(score.value),
      rank: numberOrUndefined(rank.value),
      dryRun: false,
      confirm: true,
      reason: 'admin-ui-cleanup-test-records',
    })
    cleanupResult.value = res.data.data
    selectedIds.value = []
    await loadPlans()
  } catch (error: unknown) {
    window.alert(error instanceof Error ? error.message : '执行清理失败')
  } finally {
    cleanupLoading.value = false
  }
}

function formatDate(value?: string | null) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function formatNumber(value?: number | null) {
  return typeof value === 'number' ? value.toLocaleString() : '-'
}

function openPlanResult(planId: number) {
  window.open(`/volunteer/result?planId=${planId}`, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <div class="admin-page">
    <div class="page-inner">
      <h1 class="page-title">方案记录</h1>
      <p class="page-desc">查看和清理志愿方案生成记录。删除为软删除，不物理删除方案内容，不影响正式招生数据。</p>

      <section class="toolbar gz-card">
        <div class="search-box">
          <Search :size="16" />
          <input v-model="searchQuery" placeholder="搜索 ID / 分数 / 省份 / 选科" class="search-input" @keyup.enter="doSearch" />
        </div>
        <input v-model.trim="provinceCode" class="filter-input filter-input--small" placeholder="省份 GZ" @keyup.enter="doSearch" />
        <input v-model.number="score" class="filter-input filter-input--small" type="number" placeholder="分数" @keyup.enter="doSearch" />
        <input v-model.number="rank" class="filter-input filter-input--small" type="number" placeholder="位次" @keyup.enter="doSearch" />
        <input v-model="startDate" class="filter-input" type="date" title="开始日期" />
        <input v-model="endDate" class="filter-input" type="date" title="结束日期" />
        <label class="filter-check"><input v-model="itemCountZero" type="checkbox" /> 志愿数=0</label>
        <label class="filter-check"><input v-model="anonymousOnly" type="checkbox" /> 匿名</label>
        <label class="filter-check"><input v-model="includeDeleted" type="checkbox" /> 显示已删除</label>
        <button class="action-btn action-btn--primary" :disabled="loading" @click="doSearch">筛选</button>
        <button class="action-btn" :disabled="loading" @click="resetFilters">重置</button>
      </section>

      <section class="batch-bar gz-card">
        <div class="result-count">
          共 <strong>{{ total }}</strong> 条记录，已选 <strong>{{ selectedIds.length }}</strong> 条
        </div>
        <div class="batch-actions">
          <button class="action-btn" :disabled="!plans.length" @click="toggleSelectAllVisible">
            {{ allVisibleSelected ? '取消本页' : '全选本页' }}
          </button>
          <button class="action-btn action-btn--danger" :disabled="deleting || !selectedIds.length" @click="deleteSelected">
            <Trash2 :size="14" />
            批量删除
          </button>
        </div>
      </section>

      <section class="cleanup-card gz-card">
        <div class="cleanup-card__head">
          <div>
            <h2>清理测试记录</h2>
            <p>默认 dryRun 只预览候选；执行清理必须有 ID 范围或精确勾选，并二次确认。</p>
          </div>
        </div>
        <div class="cleanup-form">
          <input v-model.number="cleanupIdStart" class="filter-input" type="number" placeholder="ID 起始" />
          <input v-model.number="cleanupIdEnd" class="filter-input" type="number" placeholder="ID 结束" />
          <input v-model="cleanupBefore" class="filter-input" type="date" title="早于日期" />
          <label class="filter-check"><input v-model="cleanupOnlyZeroVolunteer" type="checkbox" /> 仅志愿数=0</label>
          <label class="filter-check"><input v-model="cleanupOnlyAnonymous" type="checkbox" /> 仅匿名</label>
          <button class="action-btn" :disabled="cleanupLoading" @click="previewCleanup">dryRun 预览</button>
          <button class="action-btn action-btn--danger" :disabled="cleanupLoading || !cleanupResult?.ids.length" @click="executeCleanup">
            执行软删除
          </button>
        </div>
        <div v-if="cleanupResult" class="cleanup-result">
          <strong>{{ cleanupResult.dryRun ? '预览' : '已执行' }}：</strong>
          命中 {{ cleanupResult.matchedCount }} 条，软删除 {{ cleanupResult.deletedCount }} 条。
          <span>{{ cleanupResult.notice }}</span>
        </div>
      </section>

      <div class="table-card gz-card">
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th><input type="checkbox" :checked="allVisibleSelected" :disabled="!visibleSelectableIds.length" @change="toggleSelectAllVisible" /></th>
                <th>ID</th>
                <th>省份</th>
                <th>用户</th>
                <th>总分</th>
                <th>位次</th>
                <th>选科</th>
                <th>批次</th>
                <th>志愿数</th>
                <th>凭证</th>
                <th>状态</th>
                <th>生成时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="plan in plans" :key="plan.id" :class="{ 'is-deleted': plan.deleted }">
                <td>
                  <input
                    type="checkbox"
                    :checked="selectedIds.includes(plan.id)"
                    :disabled="plan.deleted"
                    @change="toggleSelected(plan.id)"
                  />
                </td>
                <td class="td-id">{{ plan.id }}</td>
                <td><span class="subject-tag">{{ plan.provinceCode || '-' }}</span></td>
                <td class="td-user">{{ plan.anonymous ? '匿名' : plan.userId || '-' }}</td>
                <td><span class="score-badge">{{ plan.totalScore ?? '-' }}</span></td>
                <td class="td-rank">{{ formatNumber(plan.provinceRank) }}</td>
                <td>
                  <span class="subject-tag">{{ plan.firstSubject || '-' }}</span>
                  <span class="subject-tag subject-tag--alt">{{ plan.resubjects || '-' }}</span>
                </td>
                <td class="td-code">{{ plan.targetBatch || '-' }}</td>
                <td><span class="count-badge" :class="{ 'count-badge--zero': !plan.itemCount }">{{ plan.itemCount ?? 0 }}</span></td>
                <td>{{ plan.hasSafetyCode ? '已设置' : '缺失' }}</td>
                <td>
                  <span v-if="plan.deleted" class="status-tag status-tag--deleted">已删除</span>
                  <span v-else class="status-tag">正常</span>
                </td>
                <td class="td-time">
                  {{ formatDate(plan.createdAt) }}
                  <small v-if="plan.deleted">删除：{{ formatDate(plan.deletedAt) }}</small>
                </td>
                <td>
                  <button class="row-btn" type="button" @click="openPlanResult(plan.id)">查看</button>
                  <button v-if="!plan.deleted" class="row-btn row-btn--danger" type="button" :disabled="deleting" @click="deleteOne(plan)">删除</button>
                  <button v-else class="row-btn row-btn--restore" type="button" :disabled="deleting" @click="restoreOne(plan)">
                    <RotateCcw :size="12" />
                    恢复
                  </button>
                </td>
              </tr>
              <tr v-if="plans.length === 0">
                <td colspan="13" class="td-empty">{{ loading ? '加载中...' : '暂无数据' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <button class="page-btn" :disabled="page <= 1 || loading" @click="prevPage">上一页</button>
          <span class="page-info">{{ page }} / {{ totalPages }}</span>
          <button class="page-btn" :disabled="page >= totalPages || loading" @click="nextPage">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page { min-height: 100%; }

.page-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: var(--gz-space-5) var(--gz-space-4);
}

.page-title { font-size: 24px; font-weight: 800; color: var(--gz-text-primary); margin-bottom: 4px; }
.page-desc { font-size: 14px; color: var(--gz-text-tertiary); margin-bottom: var(--gz-space-5); }

.toolbar,
.batch-bar,
.cleanup-card {
  margin-bottom: var(--gz-space-4);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gz-space-3);
  align-items: center;
}

.search-box {
  flex: 1;
  min-width: 220px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 var(--gz-space-3);
  background: var(--gz-card-bg);
  border: 1.5px solid rgba(0,0,0,0.08);
  border-radius: var(--gz-radius-sm);
  transition: border-color var(--gz-transition-fast);
}
.search-box:focus-within { border-color: var(--gz-primary); }

.search-input,
.filter-input {
  border: none;
  outline: none;
  background: transparent;
  min-height: 38px;
  font-size: 14px;
  color: var(--gz-text-primary);
}

.search-input { flex: 1; padding: 10px 0; }
.search-input::placeholder,
.filter-input::placeholder { color: var(--gz-text-tertiary); }

.filter-input {
  width: 150px;
  padding: 0 10px;
  border: 1.5px solid rgba(0,0,0,0.08);
  border-radius: var(--gz-radius-sm);
  background: #fff;
}

.filter-input--small { width: 110px; }

.filter-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  font-size: 13px;
  color: #475569;
  white-space: nowrap;
}

.action-btn,
.row-btn,
.page-btn {
  border: 1.5px solid rgba(0,0,0,0.08);
  border-radius: var(--gz-radius-sm);
  background: var(--gz-card-bg-solid);
  color: var(--gz-text-secondary);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all var(--gz-transition-fast);
}

.action-btn {
  min-height: 38px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.action-btn--primary {
  border-color: var(--gz-primary);
  background: var(--gz-primary);
  color: #fff;
}

.action-btn--danger,
.row-btn--danger {
  border-color: rgba(239, 68, 68, 0.18);
  background: rgba(239, 68, 68, 0.08);
  color: #dc2626;
}

.row-btn--restore {
  border-color: rgba(16, 185, 129, 0.22);
  background: rgba(16, 185, 129, 0.08);
  color: #059669;
}

.action-btn:disabled,
.row-btn:disabled,
.page-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.batch-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.result-count {
  font-size: 13px;
  color: var(--gz-text-tertiary);
}
.result-count strong {
  color: var(--gz-primary);
  font-weight: 700;
}

.cleanup-card {
  display: grid;
  gap: 12px;
}

.cleanup-card__head h2 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
  font-weight: 850;
}

.cleanup-card__head p,
.cleanup-result {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.cleanup-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.cleanup-result {
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
}

.table-card { padding: 0; overflow: hidden; }
.table-wrap { overflow-x: auto; }

.data-table { width: 100%; border-collapse: collapse; font-size: 13px; min-width: 1180px; }

.data-table th {
  padding: 12px var(--gz-space-3);
  text-align: left;
  font-weight: 700;
  font-size: 12px;
  color: var(--gz-text-tertiary);
  border-bottom: 1px solid rgba(0,0,0,0.06);
  background: rgba(0,0,0,0.015);
  white-space: nowrap;
}

.data-table td {
  padding: 10px var(--gz-space-3);
  border-bottom: 1px solid rgba(0,0,0,0.04);
  color: var(--gz-text-primary);
  white-space: nowrap;
  vertical-align: middle;
}

.data-table tr:hover td { background: rgba(37, 99, 235, 0.02); }
.data-table tr.is-deleted td { color: #94a3b8; background: #f8fafc; }

.td-id { font-weight: 700; color: var(--gz-text-tertiary); font-variant-numeric: tabular-nums; }
.td-user { font-variant-numeric: tabular-nums; }
.td-rank { font-variant-numeric: tabular-nums; color: var(--gz-text-secondary); }
.td-code { font-family: 'SF Mono', SFMono-Regular, Consolas, monospace; font-size: 12px; color: var(--gz-text-tertiary); }
.td-time { color: var(--gz-text-tertiary); font-size: 12px; }
.td-time small { display: block; margin-top: 3px; color: #dc2626; }
.td-empty { text-align: center; padding: var(--gz-space-8) !important; color: var(--gz-text-tertiary); }

.score-badge,
.subject-tag,
.count-badge,
.status-tag {
  display: inline-flex;
  align-items: center;
  border-radius: var(--gz-radius-full);
  font-size: 12px;
  font-weight: 800;
}

.score-badge {
  padding: 2px 8px;
  background: rgba(249, 115, 22, 0.08);
  color: #ea580c;
}

.subject-tag {
  margin-right: 5px;
  padding: 2px 8px;
  background: rgba(37, 99, 235, 0.06);
  color: var(--gz-primary);
}

.subject-tag--alt {
  background: rgba(124, 58, 237, 0.06);
  color: #7c3aed;
}

.count-badge {
  min-width: 28px;
  justify-content: center;
  padding: 2px 6px;
  background: rgba(16, 185, 129, 0.08);
  color: #059669;
}

.count-badge--zero {
  background: rgba(239, 68, 68, 0.08);
  color: #dc2626;
}

.status-tag {
  padding: 2px 8px;
  background: rgba(16, 185, 129, 0.08);
  color: #059669;
}

.status-tag--deleted {
  background: rgba(239, 68, 68, 0.08);
  color: #dc2626;
}

.row-btn {
  min-height: 28px;
  padding: 0 10px;
  margin-right: 6px;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--gz-space-3);
  padding: var(--gz-space-4);
  border-top: 1px solid rgba(0,0,0,0.04);
}
.page-btn { padding: 6px 14px; }
.page-btn:hover:not(:disabled) { border-color: var(--gz-primary); color: var(--gz-primary); }
.page-info { font-size: 13px; color: var(--gz-text-tertiary); font-variant-numeric: tabular-nums; }

@media (max-width: 640px) {
  .page-inner { padding: var(--gz-space-4) var(--gz-space-3); }
  .toolbar,
  .cleanup-form,
  .batch-bar { align-items: stretch; }
  .search-box,
  .filter-input,
  .action-btn { width: 100%; }
  .batch-actions { width: 100%; }
  .batch-actions .action-btn { flex: 1; justify-content: center; }
}

@media (min-width: 768px) {
  .page-inner { padding: var(--gz-space-8); }
}
</style>
