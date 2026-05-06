<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { fetchAdminMajorScores } from '@/api/admin'
import { showToast } from 'vant'
import {
  Search,
} from 'lucide-vue-next'

const searchQuery = ref('')
const yearFilter = ref<string>('all')
const subjectFilter = ref<string>('all')
const page = ref(1)
const pageSize = 20
const items = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const totalPages = ref(0)

async function loadData() {
  loading.value = true
  try {
    const res = await fetchAdminMajorScores(
      page.value, pageSize, searchQuery.value,
      yearFilter.value !== 'all' ? Number(yearFilter.value) : undefined,
      subjectFilter.value !== 'all' ? subjectFilter.value : undefined
    )
    const d = res.data?.data || {}
    items.value = d.items || []
    total.value = d.total || 0
    totalPages.value = Math.ceil(total.value / pageSize)
  } catch {
    showToast('加载失败')
  } finally {
    loading.value = false
  }
}

watch([page], loadData)
watch([yearFilter, subjectFilter], () => { page.value = 1; loadData() })

let searchTimer: ReturnType<typeof setTimeout>
watch(searchQuery, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; loadData() }, 400)
})

onMounted(loadData)
</script>

<template>
  <div class="admin-page">
    <div class="page-inner">
      <h1 class="page-title">分数线管理</h1>
      <p class="page-desc">优先管理专业级分数线数据，公开查询也会优先展示这些记录</p>

      <!-- Toolbar -->
      <div class="toolbar">
        <div class="search-box">
          <Search :size="16" />
          <input v-model="searchQuery" placeholder="搜索院校名/专业名" class="search-input" />
        </div>
        <div class="filter-group">
          <div class="filter-tabs">
            <button
              v-for="opt in [{ key: 'all', label: '全部年份' }, { key: '2025', label: '2025' }, { key: '2024', label: '2024' }, { key: '2023', label: '2023' }]"
              :key="opt.key"
              class="filter-tab"
              :class="{ active: yearFilter === opt.key }"
              @click="yearFilter = opt.key; page = 1"
            >{{ opt.label }}</button>
          </div>
          <div class="filter-tabs">
            <button
              v-for="opt in [{ key: 'all', label: '全部科类' }, { key: '物理类', label: '物理类' }, { key: '历史类', label: '历史类' }]"
              :key="opt.key"
              class="filter-tab"
              :class="{ active: subjectFilter === opt.key }"
              @click="subjectFilter = opt.key; page = 1"
            >{{ opt.label }}</button>
          </div>
        </div>
      </div>

      <div class="result-info">
        共 <strong>{{ total }}</strong> 条分数线数据
      </div>

      <!-- Table -->
      <div class="table-card gz-card">
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>院校</th>
                <th>专业</th>
                <th>年份</th>
                <th>科类</th>
                <th>最低分</th>
                <th>最低位次</th>
                <th>批次</th>
                <th>再选要求</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="line in items" :key="line.id">
                <td class="td-id">{{ line.id }}</td>
                <td class="td-uni">{{ line.universityName }}</td>
                <td class="td-major">{{ line.majorName }}</td>
                <td>
                  <span class="year-tag">{{ line.year }}</span>
                </td>
                <td>
                  <span
                    class="subject-tag"
                    :class="line.subjectType === '物理类' ? 'subject--physics' : 'subject--history'"
                  >{{ line.subjectType }}</span>
                </td>
                <td class="td-score">{{ line.minScore }}</td>
                <td class="td-rank">{{ line.minRank?.toLocaleString() || '-' }}</td>
                <td class="td-plan">{{ line.batch || '-' }}</td>
                <td class="td-plan">{{ line.resubjectRequirement || '不限/待补' }}</td>
              </tr>
              <tr v-if="items.length === 0">
                <td colspan="8" class="td-empty">暂无数据</td>
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
    </div>
  </div>
</template>

<style scoped>
.admin-page { min-height: 100%; }

.page-inner {
  max-width: 1100px; margin: 0 auto;
  padding: var(--gz-space-5) var(--gz-space-4);
}

.page-title { font-size: 24px; font-weight: 800; color: var(--gz-text-primary); margin-bottom: 4px; }
.page-desc { font-size: 14px; color: var(--gz-text-tertiary); margin-bottom: var(--gz-space-5); }

.toolbar {
  display: flex; flex-direction: column; gap: var(--gz-space-3);
  margin-bottom: var(--gz-space-3);
}

.search-box {
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

.filter-group {
  display: flex; flex-wrap: wrap; gap: var(--gz-space-3);
}

.filter-tabs { display: flex; gap: var(--gz-space-2); }

.filter-tab {
  padding: 6px 14px; border: 1.5px solid rgba(0,0,0,0.08);
  border-radius: var(--gz-radius-full); background: var(--gz-card-bg-solid);
  font-size: 13px; font-weight: 500; color: var(--gz-text-secondary);
  cursor: pointer; transition: all var(--gz-transition-fast); white-space: nowrap;
}
.filter-tab.active { background: var(--gz-primary); color: #fff; border-color: var(--gz-primary); }

.result-info {
  font-size: 13px; color: var(--gz-text-tertiary);
  margin-bottom: var(--gz-space-4);
}
.result-info strong { color: var(--gz-primary); font-weight: 700; }

.table-card { padding: 0; overflow: hidden; }
.table-wrap { overflow-x: auto; }

.data-table { width: 100%; border-collapse: collapse; font-size: 13px; min-width: 750px; }

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
.td-uni { font-weight: 600; }
.td-major { color: var(--gz-text-secondary); }
.td-score { font-weight: 700; color: var(--gz-primary); font-variant-numeric: tabular-nums; }
.td-rank { font-variant-numeric: tabular-nums; color: var(--gz-text-secondary); }
.td-plan { color: var(--gz-text-tertiary); font-size: 12px; }
.td-empty { text-align: center; padding: var(--gz-space-8) !important; color: var(--gz-text-tertiary); }

.year-tag {
  display: inline-block; padding: 2px 8px;
  border-radius: var(--gz-radius-full);
  background: rgba(0,0,0,0.04); color: var(--gz-text-secondary);
  font-size: 12px; font-weight: 600;
}

.subject-tag {
  display: inline-block; padding: 2px 10px;
  border-radius: var(--gz-radius-full);
  font-size: 12px; font-weight: 600;
}
.subject--physics { background: rgba(37, 99, 235, 0.08); color: #2563eb; }
.subject--history { background: rgba(124, 58, 237, 0.08); color: #7c3aed; }

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

@media (min-width: 768px) {
  .page-inner { padding: var(--gz-space-8); }
  .toolbar { flex-direction: row; align-items: center; }
  .search-box { flex: 1; min-width: 240px; }
}
</style>
