<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  deleteAdminEncouragementMessage,
  fetchAdminEncouragementMessages,
} from '@/api/admin'
import type { AdminEncouragementMessage } from '@/types'
import { EyeOff, MessageSquare, RefreshCw, Trash2 } from 'lucide-vue-next'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'

defineOptions({ name: 'AdminEncouragementMessages' })

const loading = ref(false)
const deletingId = ref<number | null>(null)
const page = ref(1)
const pageSize = 20
const total = ref(0)
const visibleCount = ref(0)
const hiddenCount = ref(0)
const statusFilter = ref<number | undefined>(1)
const items = ref<AdminEncouragementMessage[]>([])
const expandedIds = ref<number[]>([])

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function loadData() {
  loading.value = true
  try {
    const res = await fetchAdminEncouragementMessages(page.value, pageSize, statusFilter.value)
    const data = res.data?.data
    items.value = data?.items || []
    total.value = data?.total || 0
    visibleCount.value = data?.visibleCount || 0
    hiddenCount.value = data?.hiddenCount || 0
  } catch (error: any) {
    showToast(error?.message || '留言列表加载失败')
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

async function handleDelete(item: AdminEncouragementMessage) {
  try {
    await showConfirmDialog({
      title: '下架这条留言？',
      message: `下架后前台留言墙将不再展示「${item.nickname || '贵州考生'}」的这条留言。`,
      confirmButtonText: '确认下架',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  deletingId.value = item.id
  try {
    await deleteAdminEncouragementMessage(item.id)
    showSuccessToast('留言已下架')
    await loadData()
  } catch (error: any) {
    showToast(error?.message || '下架失败')
  } finally {
    deletingId.value = null
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

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '未记录'
}

onMounted(loadData)
</script>

<template>
  <div class="messages-page">
    <div class="page-inner">
      <div class="page-head">
        <div>
          <h1 class="page-title">留言管理</h1>
          <p class="page-desc">管理考生加油墙的公开留言，明显广告、联系方式或不合适内容可直接下架。</p>
        </div>
        <div class="head-stats">
          <span>展示中 <strong>{{ visibleCount }}</strong></span>
          <span>已下架 <strong>{{ hiddenCount }}</strong></span>
        </div>
      </div>

      <div class="toolbar gz-card">
        <div class="filter-row">
          <button class="filter-chip" :class="{ active: statusFilter === 1 }" @click="setFilter(1)">展示中</button>
          <button class="filter-chip" :class="{ active: statusFilter === 0 }" @click="setFilter(0)">已下架</button>
          <button class="filter-chip" :class="{ active: statusFilter === undefined }" @click="setFilter(undefined)">全部</button>
        </div>
        <button class="refresh-btn" :disabled="loading" @click="loadData">
          <RefreshCw :size="14" />
          刷新
        </button>
      </div>

      <div class="table-card gz-card">
        <div v-if="loading" class="empty-state">加载中...</div>
        <div v-else-if="items.length === 0" class="empty-state">暂无留言</div>
        <div v-else class="message-list">
          <article
            v-for="item in items"
            :key="item.id"
            class="message-item"
            :class="{ 'is-hidden': item.status === 0 }"
          >
            <div class="message-item__head">
              <div class="message-title">
                <span class="message-id">#{{ item.id }}</span>
                <span class="status-chip" :class="item.status === 1 ? 'status-chip--visible' : 'status-chip--hidden'">
                  {{ item.status === 1 ? '展示中' : '已下架' }}
                </span>
                <span class="message-time">{{ formatTime(item.createdAt) }}</span>
              </div>
              <div class="message-actions">
                <button class="ghost-btn" @click="toggleExpand(item.id)">
                  <MessageSquare :size="14" />
                  {{ isExpanded(item.id) ? '收起全文' : '展开全文' }}
                </button>
                <button
                  v-if="item.status !== 0"
                  class="danger-btn"
                  :disabled="deletingId === item.id"
                  @click="handleDelete(item)"
                >
                  <Trash2 :size="14" />
                  {{ deletingId === item.id ? '下架中' : '下架' }}
                </button>
                <span v-else class="hidden-note">
                  <EyeOff :size="14" />
                  前台不可见
                </span>
              </div>
            </div>

            <div class="message-meta">
              <span>昵称：{{ item.nickname || '贵州考生' }}</span>
              <span v-if="item.updatedAt">更新时间：{{ formatTime(item.updatedAt) }}</span>
            </div>

            <p class="message-content" :class="{ expanded: isExpanded(item.id) }">
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
.messages-page { min-height: 100%; }
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
.head-stats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.head-stats span {
  min-height: 40px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
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
.danger-btn,
.refresh-btn,
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
.refresh-btn:disabled,
.danger-btn:disabled,
.page-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.table-card { padding: 0; overflow: hidden; }
.empty-state {
  padding: 48px 20px;
  text-align: center;
  color: #97999e;
  font-size: 14px;
}
.message-list { display: flex; flex-direction: column; }
.message-item {
  padding: 18px 20px;
  border-bottom: 1px solid rgba(23, 24, 28, 0.06);
  background: #fff;
}
.message-item.is-hidden {
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.9), rgba(255, 255, 255, 0.7));
}
.message-item__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}
.message-title,
.message-actions,
.message-meta,
.hidden-note {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
.message-title { gap: 8px; }
.message-id {
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
.status-chip--visible {
  background: rgba(47, 125, 93, 0.12);
  color: #2f6650;
}
.status-chip--hidden {
  background: rgba(100, 116, 139, 0.12);
  color: #4b4d54;
}
.message-time {
  font-size: 12px;
  color: #97999e;
}
.message-actions { gap: 8px; }
.danger-btn {
  border-color: rgba(179, 64, 64, 0.18);
  color: #a03535;
  background: #fff7f7;
}
.hidden-note {
  gap: 6px;
  min-height: 38px;
  color: #97999e;
  font-size: 13px;
  font-weight: 700;
}
.message-meta {
  margin-top: 10px;
  gap: 10px;
  font-size: 12px;
  color: #6a6c72;
}
.message-content {
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
.message-content.expanded {
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
.page-info { font-size: 13px; color: #6a6c72; }

@media (min-width: 768px) {
  .page-inner { padding: 28px 32px; }
}

@media (max-width: 640px) {
  .page-head { flex-direction: column; }
  .head-stats { justify-content: flex-start; }
}
</style>
