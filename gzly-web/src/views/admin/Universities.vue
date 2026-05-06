<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { fetchAdminUniversities } from '@/api/admin'
import { showToast } from 'vant'
import {
  Search,
  GraduationCap,
  MapPin,
} from 'lucide-vue-next'

const searchQuery = ref('')
const page = ref(1)
const pageSize = 20
const items = ref<any[]>([])
const total = ref(0)
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await fetchAdminUniversities(page.value, pageSize, searchQuery.value)
    const d = res.data?.data || {}
    items.value = d.items || []
    total.value = d.total || 0
  } catch {
    showToast('加载失败')
  } finally {
    loading.value = false
  }
}

const totalPages = ref(0)
watch([page], loadData)
watch(total, () => { totalPages.value = Math.ceil(total.value / pageSize) })

let searchTimer: ReturnType<typeof setTimeout>
watch(searchQuery, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; loadData() }, 400)
})

function levelColor(level: string): string {
  const map: Record<string, string> = {
    '985': '#ef4444',
    '211': '#2563eb',
    '双一流': '#7c3aed',
    '一本': '#059669',
    '二本': '#f59e0b',
  }
  return map[level] || '#94a3b8'
}

onMounted(loadData)
</script>

<template>
  <div class="admin-page">
    <div class="page-inner">
      <h1 class="page-title">院校管理</h1>
      <p class="page-desc">管理系统中的院校数据</p>

      <div class="toolbar">
        <div class="search-box">
          <Search :size="16" />
          <input v-model="searchQuery" placeholder="搜索院校名称" class="search-input" />
        </div>
        <div class="result-info">
          共 <strong>{{ total }}</strong> 所院校
        </div>
      </div>

      <div class="table-card gz-card">
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>院校名称</th>
                <th>省份</th>
                <th>城市</th>
                <th>层次</th>
                <th>标签</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="uni in items" :key="uni.id">
                <td class="td-id">{{ uni.id }}</td>
                <td>
                  <span class="uni-name">
                    <GraduationCap :size="14" />
                    {{ uni.name }}
                  </span>
                </td>
                <td>
                  <span class="location-cell">
                    <MapPin :size="11" />
                    {{ uni.province || '-' }}
                  </span>
                </td>
                <td class="td-city">{{ uni.city || '-' }}</td>
                <td>
                  <span v-if="uni.level985 || uni.level211 || uni.dualClass" class="level-tag" :style="{ color: uni.level985 ? '#ef4444' : uni.level211 ? '#2563eb' : '#7c3aed', background: (uni.level985 ? '#ef4444' : uni.level211 ? '#2563eb' : '#7c3aed') + '12' }">
                    {{ uni.level985 ? '985' : uni.level211 ? '211' : '双一流' }}
                  </span>
                  <span v-else class="td-na">-</span>
                </td>
                <td>
                  <span class="level-tag" :style="{ color: uni.schoolType === '公办' ? '#059669' : '#f59e0b', background: (uni.schoolType === '公办' ? '#059669' : '#f59e0b') + '12' }">
                    {{ uni.schoolType || '普通' }}
                  </span>
                </td>
                <td class="td-na">{{ uni.belongs || '-' }}</td>
                <td class="td-na">-</td>
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
  display: flex; flex-wrap: wrap; gap: var(--gz-space-3);
  margin-bottom: var(--gz-space-4); align-items: center;
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

.filter-tabs { display: flex; gap: var(--gz-space-2); }

.filter-tab {
  padding: 6px 14px; border: 1.5px solid rgba(0,0,0,0.08);
  border-radius: var(--gz-radius-full); background: var(--gz-card-bg-solid);
  font-size: 13px; font-weight: 500; color: var(--gz-text-secondary);
  cursor: pointer; transition: all var(--gz-transition-fast);
}
.filter-tab.active { background: var(--gz-primary); color: #fff; border-color: var(--gz-primary); }

.table-card { padding: 0; overflow: hidden; }
.table-wrap { overflow-x: auto; }

.data-table { width: 100%; border-collapse: collapse; font-size: 13px; min-width: 780px; }

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
.td-city { color: var(--gz-text-tertiary); }
.td-na { color: var(--gz-text-tertiary); }
.td-empty { text-align: center; padding: var(--gz-space-8) !important; color: var(--gz-text-tertiary); }

.uni-name {
  display: inline-flex; align-items: center; gap: 6px;
  font-weight: 600; color: var(--gz-text-primary);
}

.location-cell {
  display: inline-flex; align-items: center; gap: 3px;
  color: var(--gz-text-secondary); font-size: 13px;
}

.level-tag {
  display: inline-block; padding: 2px 10px;
  border-radius: var(--gz-radius-full);
  font-size: 12px; font-weight: 700;
}

.tag-list { display: flex; gap: 4px; }

.mini-tag {
  display: inline-block; padding: 1px 8px;
  border-radius: var(--gz-radius-full);
  background: rgba(37, 99, 235, 0.06); color: var(--gz-primary);
  font-size: 11px; font-weight: 600;
}

.status-tag {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 10px; border-radius: var(--gz-radius-full);
  font-size: 12px; font-weight: 600;
}
.status--active { background: rgba(16, 185, 129, 0.08); color: #059669; }
.status--disabled { background: rgba(239, 68, 68, 0.08); color: #ef4444; }

.btn-sm {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 4px 10px; border: none; border-radius: var(--gz-radius-full);
  font-size: 12px; font-weight: 600; cursor: pointer;
  transition: all var(--gz-transition-fast);
}
.btn-sm--danger { background: rgba(239, 68, 68, 0.08); color: #ef4444; }
.btn-sm--danger:hover { background: #ef4444; color: #fff; }
.btn-sm--success { background: rgba(16, 185, 129, 0.08); color: #059669; }
.btn-sm--success:hover { background: #10b981; color: #fff; }

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
