<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchAdminOpsHealthCheck } from '@/api/admin'
import { showToast } from 'vant'
import { RefreshCw } from 'lucide-vue-next'

defineOptions({ name: 'AdminOps' })

interface CheckItem {
  key: string
  label: string
  status: string
  detail: string
}

const loading = ref(false)
const checkedAt = ref('')
const checks = ref<CheckItem[]>([])
const guards = ref<Record<string, any>>({})
const summary = ref<Record<string, any>>({})

async function loadData() {
  loading.value = true
  try {
    const res = await fetchAdminOpsHealthCheck()
    const data = res.data?.data || {}
    checkedAt.value = data.checkedAt || ''
    checks.value = data.checks || []
    guards.value = data.guards || {}
    summary.value = data.summary || {}
  } catch (error: any) {
    showToast(error?.message || '巡检加载失败')
  } finally {
    loading.value = false
  }
}

function statusLabel(status: string) {
  switch (status) {
    case 'up': return '正常'
    case 'down': return '异常'
    case 'external': return '需服务器巡检'
    default: return '未知'
  }
}

onMounted(loadData)
</script>

<template>
  <div class="admin-page">
    <div class="page-inner">
      <div class="page-head">
        <div>
          <h1 class="page-title">巡检状态</h1>
          <p class="page-desc">
            应用内可直采：数据库 / Redis / AI 通道 / 后端关键接口 / 磁盘。
            nginx 5xx、应用 ERROR 日志、首页与 /CQ/ 静态页属服务器/网关层，标记为“需服务器巡检”，不在应用内臆测。
          </p>
        </div>
        <button class="refresh-btn" :disabled="loading" @click="loadData">
          <RefreshCw :size="15" />
          {{ loading ? '巡检中…' : '重新巡检' }}
        </button>
      </div>

      <section class="summary-bar gz-card">
        <span>巡检时间：{{ checkedAt ? checkedAt.replace('T', ' ').slice(0, 19) : '-' }}</span>
        <span class="summary-tag" :class="summary.overall === 'healthy' ? 'is-ok' : 'is-bad'">
          {{ summary.overall === 'healthy' ? '整体健康' : '存在异常' }}
        </span>
        <span>正常 {{ summary.up ?? 0 }} · 异常 {{ summary.down ?? 0 }} · 外部 {{ summary.external ?? 0 }}</span>
      </section>

      <section class="check-grid">
        <div v-for="c in checks" :key="c.key" class="check-card gz-card" :class="`is-${c.status}`">
          <div class="check-card__head">
            <span class="check-label">{{ c.label }}</span>
            <span class="check-status" :class="`status-${c.status}`">{{ statusLabel(c.status) }}</span>
          </div>
          <p class="check-detail">{{ c.detail }}</p>
        </div>
      </section>

      <section class="guards-card gz-card">
        <h2>合规守卫</h2>
        <ul class="guards-list">
          <li>推荐阶段：<strong>{{ guards.recommendationPhase || '-' }}</strong></li>
          <li>FULL_RECOMMEND：<strong>{{ guards.fullRecommendEnabled ? '已开启（需排查）' : '未开启（0）' }}</strong></li>
          <li>fake2026：<strong>{{ guards.fake2026 || '-' }}</strong><small>{{ guards.fake2026Note }}</small></li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped>
.admin-page { min-height: 100%; }
.page-inner { max-width: 1120px; margin: 0 auto; padding: 20px 16px; }
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}
.page-title { font-size: 24px; font-weight: 800; color: #111827; }
.page-desc { margin-top: 6px; font-size: 13px; line-height: 1.7; color: #64748b; max-width: 720px; }
.refresh-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid rgba(15,23,42,0.1);
  border-radius: 10px;
  background: #0f172a;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}
.refresh-btn:disabled { opacity: 0.5; }
.summary-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #475569;
}
.summary-tag { padding: 3px 10px; border-radius: 999px; font-weight: 800; font-size: 12px; }
.summary-tag.is-ok { background: #dcfce7; color: #166534; }
.summary-tag.is-bad { background: #fee2e2; color: #991b1b; }
.check-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.check-card { padding: 14px 16px; border-left: 4px solid #cbd5e1; }
.check-card.is-up { border-left-color: #10b981; }
.check-card.is-down { border-left-color: #ef4444; }
.check-card.is-external { border-left-color: #f59e0b; }
.check-card__head { display: flex; justify-content: space-between; align-items: center; }
.check-label { font-size: 15px; font-weight: 800; color: #0f172a; }
.check-status { font-size: 12px; font-weight: 800; padding: 2px 8px; border-radius: 999px; }
.status-up { background: #dcfce7; color: #166534; }
.status-down { background: #fee2e2; color: #991b1b; }
.status-external { background: #fef3c7; color: #92400e; }
.status-unknown { background: #f1f5f9; color: #64748b; }
.check-detail { margin-top: 8px; font-size: 12px; line-height: 1.6; color: #64748b; }
.guards-card { padding: 16px 18px; }
.guards-card h2 { font-size: 16px; font-weight: 800; color: #0f172a; margin-bottom: 10px; }
.guards-list { list-style: none; display: flex; flex-direction: column; gap: 8px; font-size: 13px; color: #475569; }
.guards-list strong { color: #0f172a; }
.guards-list small { display: block; margin-top: 2px; color: #94a3b8; font-size: 12px; }
@media (min-width: 768px) { .page-inner { padding: 28px 32px; } }
</style>
