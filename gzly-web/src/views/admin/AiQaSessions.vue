<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchAdminAiQaSessions } from '@/api/admin'
import { showToast } from 'vant'

defineOptions({ name: 'AdminAiQaSessions' })

interface AiQaSessionRow {
  sessionUid: string
  regionCode: string
  regionName: string
  createdAt: string
  lastActiveAt: string
  messageCount: number
  compacted: boolean
  evidenceCount: number
  aiCallFailed: boolean
  lastQuestion: string
}

const loading = ref(false)
const items = ref<AiQaSessionRow[]>([])
const total = ref(0)
const totalSessions = ref(0)
const page = ref(1)
const pageSize = 20
const regionFilter = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function loadData() {
  loading.value = true
  try {
    const res = await fetchAdminAiQaSessions(page.value, pageSize, regionFilter.value.trim() || undefined)
    const data = res.data?.data || {}
    items.value = data.items || []
    total.value = data.total || 0
    totalSessions.value = data.totalSessions || 0
  } catch (error: any) {
    showToast(error?.message || 'AI 问答会话加载失败')
  } finally {
    loading.value = false
  }
}

function doSearch() {
  page.value = 1
  loadData()
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
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(loadData)
</script>

<template>
  <div class="admin-page">
    <div class="page-inner">
      <h1 class="page-title">未上线地区 AI 问答会话</h1>
      <p class="page-desc">只读监控未上线地区的 AI 志愿问答会话。不展示对话码（仅存哈希），仅用于运营观察。</p>

      <section class="toolbar gz-card">
        <input v-model="regionFilter" class="filter-input" placeholder="按地区代码筛选（如 GD/JS/CQ）" @keyup.enter="doSearch" />
        <button class="action-btn action-btn--primary" :disabled="loading" @click="doSearch">筛选</button>
        <span class="toolbar-meta">共 {{ total }} 个会话 · 累计 {{ totalSessions }}</span>
      </section>

      <div class="table-card gz-card">
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>地区</th>
                <th>创建时间</th>
                <th>最近活跃</th>
                <th>消息数</th>
                <th>来源数</th>
                <th>压缩</th>
                <th>AI状态</th>
                <th>最近问题</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in items" :key="row.sessionUid">
                <td><span class="tag">{{ row.regionName }}（{{ row.regionCode }}）</span></td>
                <td class="td-time">{{ formatTime(row.createdAt) }}</td>
                <td class="td-time">{{ formatTime(row.lastActiveAt) }}</td>
                <td>{{ row.messageCount }}</td>
                <td>{{ row.evidenceCount }}</td>
                <td>
                  <span class="badge" :class="row.compacted ? 'badge--info' : 'badge--muted'">
                    {{ row.compacted ? '已压缩' : '未压缩' }}
                  </span>
                </td>
                <td>
                  <span class="badge" :class="row.aiCallFailed ? 'badge--fail' : 'badge--ok'">
                    {{ row.aiCallFailed ? '调用失败' : '正常' }}
                  </span>
                </td>
                <td class="td-question" :title="row.lastQuestion">{{ row.lastQuestion || '-' }}</td>
              </tr>
              <tr v-if="items.length === 0">
                <td colspan="8" class="td-empty">{{ loading ? '加载中…' : '暂无会话' }}</td>
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
.page-inner { max-width: 1200px; margin: 0 auto; padding: 20px 16px; }
.page-title { font-size: 24px; font-weight: 800; color: #111827; }
.page-desc { margin: 6px 0 16px; font-size: 14px; line-height: 1.7; color: #64748b; }
.toolbar {
  margin-bottom: 16px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
.filter-input {
  min-width: 240px;
  min-height: 38px;
  padding: 0 12px;
  border: 1.5px solid rgba(0,0,0,0.08);
  border-radius: 10px;
  outline: none;
  font-size: 14px;
}
.action-btn {
  min-height: 38px;
  padding: 0 16px;
  border: 1.5px solid rgba(0,0,0,0.08);
  border-radius: 10px;
  background: #fff;
  color: #334155;
  font-weight: 700;
  cursor: pointer;
}
.action-btn--primary { background: #0f172a; border-color: #0f172a; color: #fff; }
.toolbar-meta { color: #64748b; font-size: 13px; }
.table-card { padding: 0; overflow: hidden; }
.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; min-width: 940px; }
.data-table th {
  padding: 12px;
  text-align: left;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  background: rgba(0,0,0,0.015);
  border-bottom: 1px solid rgba(0,0,0,0.06);
  white-space: nowrap;
}
.data-table td {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(0,0,0,0.04);
  color: #1f2937;
  white-space: nowrap;
  vertical-align: middle;
}
.td-time { color: #64748b; font-size: 12px; }
.td-question { max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.td-empty { text-align: center; padding: 40px !important; color: #94a3b8; }
.tag { padding: 2px 8px; border-radius: 999px; background: rgba(37,99,235,0.08); color: #1d4ed8; font-weight: 700; }
.badge { display: inline-flex; padding: 2px 8px; border-radius: 999px; font-size: 11px; font-weight: 800; }
.badge--ok { background: #dcfce7; color: #166534; }
.badge--fail { background: #fee2e2; color: #991b1b; }
.badge--info { background: #dbeafe; color: #1d4ed8; }
.badge--muted { background: #f1f5f9; color: #64748b; }
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 16px;
  border-top: 1px solid rgba(0,0,0,0.04);
}
.page-btn {
  min-height: 34px;
  padding: 6px 14px;
  border: 1.5px solid rgba(0,0,0,0.08);
  border-radius: 10px;
  background: #fff;
  color: #334155;
  font-weight: 700;
  cursor: pointer;
}
.page-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.page-info { font-size: 13px; color: #64748b; }
@media (min-width: 768px) { .page-inner { padding: 28px 32px; } }
</style>
