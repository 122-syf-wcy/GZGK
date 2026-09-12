<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchAdminFeedbacks, markAdminFeedbackRead } from '@/api/admin'
import type { FeedbackItem } from '@/types'
import { MessageSquare, MailOpen, Mail } from 'lucide-vue-next'
import { showSuccessToast, showToast } from 'vant'

const loading = ref(false)
const updatingId = ref<number | null>(null)
const page = ref(1)
const pageSize = 20
const total = ref(0)
const unreadCount = ref(0)
const statusFilter = ref<number | undefined>(undefined)
const items = ref<FeedbackItem[]>([])
const expandedIds = ref<number[]>([])

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function loadData() {
  loading.value = true
  try {
    const res = await fetchAdminFeedbacks(page.value, pageSize, statusFilter.value)
    const data = res.data?.data || {}
    items.value = data.items || []
    total.value = data.total || 0
    unreadCount.value = data.unreadCount || 0
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

async function toggleRead(item: FeedbackItem, read: boolean) {
  updatingId.value = item.id
  try {
    await markAdminFeedbackRead(item.id, read)
    showSuccessToast(read ? '已标记为已读' : '已恢复为未读')
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
          <p class="page-desc">公测期用户意见反馈列表，支持查看和手动标记已读。</p>
        </div>
        <div class="head-stat">
          未读 <strong>{{ unreadCount }}</strong>
        </div>
      </div>

      <div class="toolbar gz-card">
        <div class="filter-row">
          <button class="filter-chip" :class="{ active: statusFilter === undefined }" @click="setFilter(undefined)">全部</button>
          <button class="filter-chip" :class="{ active: statusFilter === 0 }" @click="setFilter(0)">未读</button>
          <button class="filter-chip" :class="{ active: statusFilter === 1 }" @click="setFilter(1)">已读</button>
        </div>
        <div class="toolbar-meta">共 {{ total }} 条反馈</div>
      </div>

      <div class="table-card gz-card">
        <div v-if="loading" class="empty-state">加载中…</div>
        <div v-else-if="items.length === 0" class="empty-state">暂无反馈</div>
        <div v-else class="feedback-list">
          <article v-for="item in items" :key="item.id" class="feedback-item" :class="{ 'is-unread': item.status === 0 }">
            <div class="feedback-item__head">
              <div class="feedback-item__title">
                <span class="feedback-id">#{{ item.id }}</span>
                <span class="status-chip" :class="item.status === 0 ? 'status-chip--unread' : 'status-chip--read'">
                  {{ item.status === 0 ? '未读' : '已读' }}
                </span>
                <span class="feedback-time">{{ item.createdAt }}</span>
              </div>
              <div class="feedback-item__actions">
                <button class="ghost-btn" @click="toggleExpand(item.id)">
                  <MessageSquare :size="14" />
                  {{ isExpanded(item.id) ? '收起全文' : '展开全文' }}
                </button>
                <button
                  class="ghost-btn"
                  :disabled="updatingId === item.id"
                  @click="toggleRead(item, item.status !== 0)"
                >
                  <component :is="item.status === 0 ? MailOpen : Mail" :size="14" />
                  {{ item.status === 0 ? '标记已读' : '标记未读' }}
                </button>
              </div>
            </div>

            <div class="feedback-meta">
              <span>来源页面：{{ item.sourcePage || '未记录' }}</span>
              <span v-if="item.readAt">已读时间：{{ item.readAt }}</span>
            </div>

            <p class="feedback-preview" :class="{ expanded: isExpanded(item.id) }">
              {{ item.content }}
            </p>
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
.page-title { font-size: 24px; font-weight: 800; color: #17181c; }
.page-desc { margin-top: 6px; font-size: 14px; line-height: 1.7; color: #6a6c72; }
.head-stat {
  min-height: 40px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  border: 1px solid rgba(23, 24, 28, 0.08);
  background: #fff;
  color: #4b4d54;
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
  border: 1px solid rgba(23, 24, 28, 0.08);
  background: #fff;
  color: #383a40;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}
.filter-chip.active {
  background: #17181c;
  border-color: #17181c;
  color: #fff;
}
.toolbar-meta { color: #6a6c72; font-size: 13px; }
.table-card { padding: 0; overflow: hidden; }
.empty-state {
  padding: 48px 20px;
  text-align: center;
  color: #97999e;
  font-size: 14px;
}
.feedback-list { display: flex; flex-direction: column; }
.feedback-item {
  padding: 18px 20px;
  border-bottom: 1px solid rgba(23, 24, 28, 0.06);
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
  color: #17181c;
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
.status-chip--unread {
  background: rgba(185, 138, 47, 0.12);
  color: #8a6d3b;
}
.status-chip--read {
  background: rgba(47, 125, 93, 0.12);
  color: #2f6650;
}
.feedback-time {
  font-size: 12px;
  color: #97999e;
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
  color: #6a6c72;
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
  border-top: 1px solid rgba(23, 24, 28, 0.06);
}
.page-btn:disabled,
.ghost-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.page-info { font-size: 13px; color: #6a6c72; }

@media (min-width: 768px) {
  .page-inner { padding: 28px 32px; }
}
</style>
