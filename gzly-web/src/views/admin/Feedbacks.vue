<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchAdminFeedbackOps, updateAdminFeedbackStatus } from '@/api/admin'
import type { FeedbackItem } from '@/types'
import { MessageSquare } from 'lucide-vue-next'
import { showSuccessToast, showToast } from 'vant'

const STATUS_OPTIONS = [
  { value: 0, label: '未处理', tone: 'pending' },
  { value: 1, label: '处理中', tone: 'processing' },
  { value: 2, label: '已解决', tone: 'resolved' },
  { value: 3, label: '已忽略', tone: 'ignored' },
]

const loading = ref(false)
const updatingId = ref<number | null>(null)
const page = ref(1)
const pageSize = 20
const total = ref(0)
const counts = ref<Record<string, number>>({})
const statusFilter = ref<number | undefined>(undefined)
const items = ref<FeedbackItem[]>([])
const expandedIds = ref<number[]>([])

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function statusMeta(value?: number) {
  return STATUS_OPTIONS.find(o => o.value === (value ?? 0)) || STATUS_OPTIONS[0]
}

async function loadData() {
  loading.value = true
  try {
    const res = await fetchAdminFeedbackOps(page.value, pageSize, statusFilter.value)
    const data = res.data?.data || {}
    items.value = data.items || []
    total.value = data.total || 0
    counts.value = data.counts || {}
  } catch (error: any) {
    showToast(error?.message || '反馈列表加载失败')
  } finally {
    loading.value = false
  }
}

function setFilter(status?: number) {
  statusFilter.value = status
  page.value = 1
  loadData()
}

function toggleExpand(id: number) {
  expandedIds.value = expandedIds.value.includes(id)
    ? expandedIds.value.filter(item => item !== id)
    : [...expandedIds.value, id]
}

function isExpanded(id: number) {
  return expandedIds.value.includes(id)
}

async function changeStatus(item: FeedbackItem, handleStatus: number) {
  if ((item.handleStatus ?? 0) === handleStatus) return
  updatingId.value = item.id
  try {
    await updateAdminFeedbackStatus(item.id, handleStatus)
    showSuccessToast('处理状态已更新')
    await loadData()
  } catch (error: any) {
    showToast(error?.message || '状态更新失败')
  } finally {
    updatingId.value = null
  }
}

function prevPage() {
  if (page.value <= 1) return
  page.value -= 1
  loadData()
}

function nextPage() {
  if (page.value >= totalPages.value) return
  page.value += 1
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="feedback-page">
    <div class="page-inner">
      <div class="page-head">
        <div>
          <h1 class="page-title">反馈管理</h1>
          <p class="page-desc">公测期用户意见反馈工单，支持处理状态流转（未处理 / 处理中 / 已解决 / 已忽略）与来源定位。</p>
        </div>
        <div class="head-stat">
          未处理 <strong>{{ counts.pending ?? 0 }}</strong>
        </div>
      </div>

      <div class="toolbar gz-card">
        <div class="filter-row">
          <button class="filter-chip" :class="{ active: statusFilter === undefined }" @click="setFilter(undefined)">全部</button>
          <button
            v-for="opt in STATUS_OPTIONS"
            :key="opt.value"
            class="filter-chip"
            :class="{ active: statusFilter === opt.value }"
            @click="setFilter(opt.value)"
          >
            {{ opt.label }}
          </button>
        </div>
        <div class="toolbar-meta">
          共 {{ total }} 条 · 未处理 {{ counts.pending ?? 0 }} / 处理中 {{ counts.processing ?? 0 }} / 已解决 {{ counts.resolved ?? 0 }} / 已忽略 {{ counts.ignored ?? 0 }}
        </div>
      </div>

      <div class="table-card gz-card">
        <div v-if="loading" class="empty-state">加载中…</div>
        <div v-else-if="items.length === 0" class="empty-state">暂无反馈</div>
        <div v-else class="feedback-list">
          <article v-for="item in items" :key="item.id" class="feedback-item" :class="{ 'is-unread': (item.handleStatus ?? 0) === 0 }">
            <div class="feedback-item__head">
              <div class="feedback-item__title">
                <span class="feedback-id">#{{ item.id }}</span>
                <span class="status-chip" :class="`status-chip--${statusMeta(item.handleStatus).tone}`">
                  {{ statusMeta(item.handleStatus).label }}
                </span>
                <span class="feedback-time">{{ item.createdAt }}</span>
              </div>
              <div class="feedback-item__actions">
                <button class="ghost-btn" @click="toggleExpand(item.id)">
                  <MessageSquare :size="14" />
                  {{ isExpanded(item.id) ? '收起全文' : '展开全文' }}
                </button>
              </div>
            </div>

            <div class="feedback-meta">
              <span>来源页面：{{ item.sourcePage || '未记录' }}</span>
              <span v-if="item.provinceCode">地区：{{ item.provinceCode }}</span>
              <span v-if="item.resultId">关联方案：#{{ item.resultId }}</span>
              <span v-if="item.handledAt">处理时间：{{ item.handledAt }}</span>
            </div>

            <p class="feedback-preview" :class="{ expanded: isExpanded(item.id) }">
              {{ item.content }}
            </p>

            <div class="status-actions">
              <span class="status-actions__label">设为：</span>
              <button
                v-for="opt in STATUS_OPTIONS"
                :key="opt.value"
                class="status-btn"
                :class="{ active: (item.handleStatus ?? 0) === opt.value }"
                :disabled="updatingId === item.id"
                @click="changeStatus(item, opt.value)"
              >
                {{ opt.label }}
              </button>
            </div>
          </article>
        </div>

        <div v-if="totalPages > 1" class="pagination">
          <button class="page-btn" :disabled="page <= 1" @click="prevPage">上一页</button>
          <span class="page-info">{{ page }} / {{ totalPages }}</span>
          <button class="page-btn" :disabled="page >= totalPages" @click="nextPage">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.feedback-page { min-height: 100%; }
.page-inner { max-width: 1120px; margin: 0 auto; padding: 20px 16px; }
.page-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-title { font-size: 24px; font-weight: 800; color: #111827; }
.page-desc { margin-top: 6px; font-size: 14px; line-height: 1.7; color: #64748b; }
.head-stat {
  min-height: 40px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #fff;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}
.toolbar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.filter-row { display: flex; flex-wrap: wrap; gap: 8px; }
.filter-chip,
.ghost-btn,
.page-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 38px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}
.filter-chip.active {
  background: #0f172a;
  border-color: #0f172a;
  color: #fff;
}
.toolbar-meta { color: #64748b; font-size: 13px; }
.table-card { padding: 0; overflow: hidden; }
.empty-state {
  padding: 48px 20px;
  text-align: center;
  color: #94a3b8;
  font-size: 14px;
}
.feedback-list { display: flex; flex-direction: column; }
.feedback-item {
  padding: 18px 20px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}
.feedback-item.is-unread {
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.65), rgba(255, 255, 255, 0));
}
.feedback-item__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}
.feedback-item__title {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.feedback-id {
  font-size: 13px;
  font-weight: 800;
  color: #0f172a;
}
.status-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
}
.status-chip--pending {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}
.status-chip--processing {
  background: rgba(37, 99, 235, 0.12);
  color: #1d4ed8;
}
.status-chip--resolved {
  background: rgba(16, 185, 129, 0.12);
  color: #047857;
}
.status-chip--ignored {
  background: rgba(100, 116, 139, 0.14);
  color: #475569;
}
.status-actions {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.status-actions__label { font-size: 12px; color: #94a3b8; }
.status-btn {
  min-height: 32px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  background: #fff;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}
.status-btn.active {
  background: #0f172a;
  border-color: #0f172a;
  color: #fff;
}
.status-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.feedback-time {
  font-size: 12px;
  color: #94a3b8;
}
.feedback-item__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.feedback-meta {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: #64748b;
}
.feedback-preview {
  margin-top: 12px;
  font-size: 14px;
  line-height: 1.7;
  color: #1f2937;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-wrap;
}
.feedback-preview.expanded {
  display: block;
  overflow: visible;
}
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 16px;
  border-top: 1px solid rgba(15, 23, 42, 0.06);
}
.page-btn:disabled,
.ghost-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.page-info { font-size: 13px; color: #64748b; }

@media (min-width: 768px) {
  .page-inner { padding: 28px 32px; }
}
</style>
