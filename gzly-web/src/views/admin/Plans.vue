<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fetchAdminPlans } from '@/api/admin'
import { Search } from 'lucide-vue-next'

const searchQuery = ref('')
const plans = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)

async function loadPlans() {
  loading.value = true
  try {
    const res = await fetchAdminPlans(page.value, pageSize, searchQuery.value)
    if (res.data?.data) {
      plans.value = res.data.data.items || []
      total.value = res.data.data.total || 0
    }
  } catch { /* */ }
  loading.value = false
}

onMounted(loadPlans)

const totalPages = ref(1)
function updatePages() { totalPages.value = Math.ceil(total.value / pageSize) }

function doSearch() { page.value = 1; loadPlans().then(updatePages) }
function prevPage() { if (page.value > 1) { page.value--; loadPlans().then(updatePages) } }
function nextPage() { if (page.value < totalPages.value) { page.value++; loadPlans().then(updatePages) } }
</script>

<template>
  <div class="admin-page">
    <div class="page-inner">
      <h1 class="page-title">方案记录</h1>
      <p class="page-desc">查看所有用户的志愿方案生成记录</p>

      <!-- Toolbar -->
      <div class="toolbar">
        <div class="search-box">
          <Search :size="16" />
          <input v-model="searchQuery" placeholder="搜索分数" class="search-input" @keyup.enter="doSearch" />
        </div>
        <div class="result-count">
          共 <strong>{{ total }}</strong> 条记录
        </div>
      </div>

      <!-- Table -->
      <div class="table-card gz-card">
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>用户</th>
                <th>总分</th>
                <th>位次</th>
                <th>首选</th>
                <th>再选</th>
                <th>志愿数</th>
                <th>生成时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="plan in plans" :key="plan.id">
                <td class="td-id">{{ plan.id }}</td>
                <td class="td-user">{{ plan.userId || '-' }}</td>
                <td>
                  <span class="score-badge">
                    {{ plan.totalScore }}
                  </span>
                </td>
                <td class="td-rank">{{ plan.provinceRank?.toLocaleString() || '-' }}</td>
                <td>
                  <span class="subject-tag">{{ plan.firstSubject }}</span>
                </td>
                <td>
                  <span class="subject-tag subject-tag--alt">{{ plan.resubjects || '-' }}</span>
                </td>
                <td>
                  <span class="count-badge">{{ plan.itemCount }}</span>
                </td>
                <td class="td-time">{{ plan.createdAt }}</td>
              </tr>
              <tr v-if="plans.length === 0">
                <td colspan="8" class="td-empty">{{ loading ? '加载中...' : '暂无数据' }}</td>
              </tr>
            </tbody>
          </table>
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
.admin-page { min-height: 100%; }

.page-inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: var(--gz-space-5) var(--gz-space-4);
}

.page-title { font-size: 24px; font-weight: 800; color: var(--gz-text-primary); margin-bottom: 4px; }
.page-desc { font-size: 14px; color: var(--gz-text-tertiary); margin-bottom: var(--gz-space-5); }

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gz-space-3);
  margin-bottom: var(--gz-space-4);
  align-items: center;
}

.search-box {
  flex: 1; min-width: 200px;
  display: flex; align-items: center; gap: 8px;
  padding: 0 var(--gz-space-3);
  background: var(--gz-card-bg);
  border: 1.5px solid rgba(0,0,0,0.08);
  border-radius: var(--gz-radius-sm);
  transition: border-color var(--gz-transition-fast);
}
.search-box:focus-within { border-color: var(--gz-primary); }

.search-input {
  flex: 1; border: none; outline: none; background: transparent;
  padding: 10px 0; font-size: 14px; color: var(--gz-text-primary);
}
.search-input::placeholder { color: var(--gz-text-tertiary); }

.result-count {
  font-size: 13px;
  color: var(--gz-text-tertiary);
}
.result-count strong {
  color: var(--gz-primary);
  font-weight: 700;
}

.table-card { padding: 0; overflow: hidden; }
.table-wrap { overflow-x: auto; }

.data-table { width: 100%; border-collapse: collapse; font-size: 13px; min-width: 850px; }

.data-table th {
  padding: 12px var(--gz-space-3); text-align: left;
  font-weight: 600; font-size: 12px; color: var(--gz-text-tertiary);
  text-transform: uppercase; letter-spacing: 0.04em;
  border-bottom: 1px solid rgba(0,0,0,0.06);
  background: rgba(0,0,0,0.015); white-space: nowrap;
}

.data-table td {
  padding: 10px var(--gz-space-3);
  border-bottom: 1px solid rgba(0,0,0,0.04);
  color: var(--gz-text-primary); white-space: nowrap;
}

.data-table tr:hover td { background: rgba(37, 99, 235, 0.02); }

.td-id { font-weight: 600; color: var(--gz-text-tertiary); font-variant-numeric: tabular-nums; }
.td-user { font-variant-numeric: tabular-nums; }
.td-rank { font-variant-numeric: tabular-nums; color: var(--gz-text-secondary); }
.td-code { font-family: 'SF Mono', SFMono-Regular, Consolas, monospace; font-size: 12px; color: var(--gz-text-tertiary); }
.td-time { color: var(--gz-text-tertiary); font-size: 12px; }
.td-empty { text-align: center; padding: var(--gz-space-8) !important; color: var(--gz-text-tertiary); }

.score-badge {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 2px 8px; border-radius: var(--gz-radius-full);
  background: rgba(249, 115, 22, 0.08); color: #ea580c;
  font-size: 12px; font-weight: 700;
}

.subject-tag {
  display: inline-block;
  padding: 2px 8px; border-radius: var(--gz-radius-full);
  background: rgba(37, 99, 235, 0.06); color: var(--gz-primary);
  font-size: 12px; font-weight: 600;
}

.subject-tag--alt {
  background: rgba(124, 58, 237, 0.06); color: #7c3aed;
}

.count-badge {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 28px; padding: 2px 6px; border-radius: var(--gz-radius-full);
  background: rgba(16, 185, 129, 0.08); color: #059669;
  font-size: 12px; font-weight: 700;
}

.pagination {
  display: flex; align-items: center; justify-content: center;
  gap: var(--gz-space-3); padding: var(--gz-space-4);
  border-top: 1px solid rgba(0,0,0,0.04);
}
.page-btn {
  padding: 6px 14px; border: 1.5px solid rgba(0,0,0,0.08);
  border-radius: var(--gz-radius-sm); background: var(--gz-card-bg-solid);
  font-size: 13px; color: var(--gz-text-secondary); cursor: pointer;
  transition: all var(--gz-transition-fast);
}
.page-btn:hover:not(:disabled) { border-color: var(--gz-primary); color: var(--gz-primary); }
.page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.page-info { font-size: 13px; color: var(--gz-text-tertiary); font-variant-numeric: tabular-nums; }

@media (min-width: 768px) { .page-inner { padding: var(--gz-space-8); } }
</style>
