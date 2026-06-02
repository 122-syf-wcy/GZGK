<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { fetchAdminStats } from '@/api/admin'
import {
  TrendingUp,
  Activity,
  Bot,
  Database,
  FileText,
  GraduationCap,
  Link2,
  Users,
} from 'lucide-vue-next'

const router = useRouter()
const stats = ref({
  totalUsers: 0,
  totalPlans: 0,
  todayPlans: 0,
  todayUsers: 0,
  totalUniversities: 0,
  totalScoreLines: 0,
  totalMajorScoreLines: 0,
  totalOfficialLinks: 0,
  totalSpecialAdmissionPolicies: 0,
  pendingAlumni: 0,
  dataQuality: null as any,
  volunteerQuality: null as any,
})

const loading = ref(true)

onMounted(async () => {
  try {
    const res = await fetchAdminStats()
    if (res.data?.data) stats.value = { ...stats.value, ...res.data.data }
  } catch { /* fallback to zeros */ }
  loading.value = false
})

const trend = {
  labels: Array.from({ length: 7 }, (_, i) => {
    const d = new Date(); d.setDate(d.getDate() - (6 - i))
    return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
  }),
  plans: [0, 0, 0, 0, 0, 0, 0],
  users: [0, 0, 0, 0, 0, 0, 0],
}

const avgPlansPerUser = computed(() => {
  if (!stats.value.totalUsers) return '0.0'
  return (stats.value.totalPlans / stats.value.totalUsers).toFixed(1)
})

const qualitySummary = computed(() => stats.value.dataQuality?.summary || [])
const officialLinks = computed(() => stats.value.dataQuality?.officialLinks || {})
const aiConfig = computed(() => stats.value.dataQuality?.aiConfig || {})
const officialCoverage = computed(() => stats.value.dataQuality?.officialCoverage || 0)
const volunteerQuality = computed(() => stats.value.volunteerQuality || {})

const officialGaps = computed(() => [
  { label: '缺招生网', value: officialLinks.value.missingAdmissionSite || 0, field: 'admissionSite' },
  { label: '缺招生章程', value: officialLinks.value.missingBrochure || 0, field: 'admissionBrochureUrl' },
  { label: '缺专业目录', value: officialLinks.value.missingMajorCatalog || 0, field: 'majorCatalogUrl' },
  { label: '缺学费摘要', value: officialLinks.value.missingTuition || 0, field: 'tuitionSummary' },
  { label: '缺调剂规则', value: officialLinks.value.missingAdjustmentRule || 0, field: 'adjustmentRule' },
])

const generationQualityItems = computed(() => [
  { label: '生成完成率', value: `${volunteerQuality.value.generateSuccessRate || 0}%`, tone: 'ok' },
  { label: '不足96条', value: volunteerQuality.value.generateIncomplete || 0, tone: (volunteerQuality.value.generateIncomplete || 0) > 0 ? 'warn' : 'ok' },
  { label: '平均耗时', value: `${volunteerQuality.value.generateAvgCostMs || 0}ms`, tone: 'neutral' },
  { label: '最高耗时', value: `${volunteerQuality.value.generateMaxCostMs || 0}ms`, tone: (volunteerQuality.value.generateMaxCostMs || 0) > 5000 ? 'warn' : 'neutral' },
  { label: '质量警告方案', value: volunteerQuality.value.persistedWarnings || 0, tone: (volunteerQuality.value.persistedWarnings || 0) > 0 ? 'warn' : 'ok' },
  { label: '人工复核方案', value: volunteerQuality.value.persistedManualReviews || 0, tone: (volunteerQuality.value.persistedManualReviews || 0) > 0 ? 'warn' : 'ok' },
])

function goOfficialGap(field: string) {
  router.push({ path: '/admin/official-links', query: { missingField: field } })
}
</script>

<template>
  <div class="dashboard-page">
    <div class="page-inner">
      <h1 class="page-title">数据看板</h1>
      <p class="page-desc">系统运营数据概览</p>

      <!-- Stats Grid -->
      <div class="stats-grid">
        <div class="stat-card stat-card--blue">
          <div class="stat-icon"><Users :size="20" /></div>
          <div class="stat-body">
            <span class="stat-value">{{ stats.totalUsers }}</span>
            <span class="stat-label">注册用户</span>
          </div>
          <div class="stat-badge">今日 +{{ stats.todayUsers }}</div>
        </div>

        <div class="stat-card stat-card--purple">
          <div class="stat-icon"><FileText :size="20" /></div>
          <div class="stat-body">
            <span class="stat-value">{{ stats.totalPlans }}</span>
            <span class="stat-label">生成方案</span>
          </div>
          <div class="stat-badge">今日 +{{ stats.todayPlans }}</div>
        </div>

        <div class="stat-card stat-card--amber">
          <div class="stat-icon"><GraduationCap :size="20" /></div>
          <div class="stat-body">
            <span class="stat-value">{{ stats.totalUniversities }}</span>
            <span class="stat-label">院校数据</span>
          </div>
          <div class="stat-badge">{{ stats.totalScoreLines }} 条分数线</div>
        </div>

        <div class="stat-card stat-card--green">
          <div class="stat-icon"><Link2 :size="20" /></div>
          <div class="stat-body">
            <span class="stat-value">{{ stats.totalOfficialLinks }}</span>
            <span class="stat-label">官方链接</span>
          </div>
          <div class="stat-badge">{{ stats.totalSpecialAdmissionPolicies }} 条特招政策</div>
        </div>
      </div>

      <section class="quality-card gz-card">
        <div class="quality-head">
          <div class="chart-title-row">
            <Database :size="16" />
            <h3>数据质量巡检</h3>
          </div>
          <button class="quality-action" @click="router.push('/admin/official-links')">查看官方链接</button>
        </div>

        <div class="quality-grid">
          <div
            v-for="item in qualitySummary"
            :key="item.label"
            class="quality-metric"
            :class="{ 'quality-metric--warn': item.status === 'warn' }"
          >
            <span class="quality-value">{{ item.value }}</span>
            <span class="quality-label">{{ item.label }}{{ item.unit }}</span>
          </div>
        </div>

        <div class="quality-detail-grid">
          <div class="quality-panel">
            <div class="quality-panel__title">
              <Link2 :size="15" />
              官方链接覆盖率 {{ officialCoverage }}%
            </div>
            <div class="coverage-bar">
              <span :style="{ width: `${Math.min(officialCoverage, 100)}%` }"></span>
            </div>
            <div class="gap-list">
              <button v-for="gap in officialGaps" :key="gap.field" class="gap-item" @click="goOfficialGap(gap.field)">
                <span>{{ gap.label }}</span>
                <strong>{{ gap.value }}</strong>
              </button>
            </div>
          </div>

          <div class="quality-panel">
            <div class="quality-panel__title">
              <Bot :size="15" />
              AI 配置状态
            </div>
            <div class="ai-status" :class="{ 'ai-status--ok': aiConfig.enabled && aiConfig.hasApiKey }">
              {{ aiConfig.enabled && aiConfig.hasApiKey ? '已启用且密钥已配置' : '未完整配置' }}
            </div>
            <p class="quality-note">
              来源：{{ aiConfig.configSource === 'database' ? '数据库配置' : '环境变量兜底' }}
              <span v-if="aiConfig.updatedAt"> · {{ aiConfig.updatedAt }}</span>
            </p>
            <button class="quality-action quality-action--full" @click="router.push('/admin/ai-config')">进入 AI 配置</button>
          </div>
        </div>
      </section>

      <section class="quality-card gz-card">
        <div class="quality-head">
          <div class="chart-title-row">
            <Activity :size="16" />
            <h3>志愿生成质量监控</h3>
          </div>
          <span class="quality-pill">运行期 + 历史方案</span>
        </div>
        <div class="generation-grid">
          <div
            v-for="item in generationQualityItems"
            :key="item.label"
            class="generation-metric"
            :class="{
              'generation-metric--warn': item.tone === 'warn',
              'generation-metric--ok': item.tone === 'ok',
            }"
          >
            <span class="generation-value">{{ item.value }}</span>
            <span class="generation-label">{{ item.label }}</span>
          </div>
        </div>
        <p class="quality-note">
          运行期指标会随服务重启清零；“质量警告方案”和“人工复核方案”来自历史方案记录，可用于判断数据缺口是否影响真实用户。
        </p>
      </section>

      <!-- Charts Row -->
      <div class="charts-row">
        <!-- Trend Chart -->
        <div class="chart-card gz-card">
          <div class="chart-header">
            <div class="chart-title-row">
              <TrendingUp :size="16" />
              <h3>最近7天趋势</h3>
            </div>
          </div>
          <div class="chart-body">
            <div class="mini-chart">
              <div class="chart-bars">
                <div
                  v-for="(val, i) in trend.plans"
                  :key="i"
                  class="bar-group"
                >
                  <div class="bar-pair">
                    <div
                      class="bar bar--plans"
                      :style="{ height: (val / 45) * 100 + '%' }"
                    ></div>
                    <div
                      class="bar bar--users"
                      :style="{ height: (trend.users[i] / 35) * 100 + '%' }"
                    ></div>
                  </div>
                  <span class="bar-label">{{ trend.labels[i] }}</span>
                </div>
              </div>
            </div>
            <div class="chart-legend">
              <span class="legend-item"><span class="legend-dot legend-dot--plans"></span>方案数</span>
              <span class="legend-item"><span class="legend-dot legend-dot--users"></span>新增用户</span>
            </div>
          </div>
        </div>

        <!-- Quick Stats -->
        <div class="chart-card gz-card">
          <div class="chart-header">
            <div class="chart-title-row">
              <Activity :size="16" />
              <h3>运营快报</h3>
            </div>
          </div>
          <div class="chart-body">
            <div class="quick-list">
              <div class="quick-item">
                <div class="quick-dot quick-dot--green"></div>
                <span class="quick-text">平均每用户方案数</span>
                <span class="quick-val">{{ avgPlansPerUser }}</span>
              </div>
              <div class="quick-item">
                <div class="quick-dot quick-dot--purple"></div>
                <span class="quick-text">今日活跃用户</span>
                <span class="quick-val">{{ stats.todayUsers }}</span>
              </div>
              <div class="quick-item">
                <div class="quick-dot quick-dot--amber"></div>
                <span class="quick-text">今日生成方案</span>
                <span class="quick-val">{{ stats.todayPlans }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page {
  min-height: 100%;
}

.page-inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: var(--gz-space-5) var(--gz-space-4);
}

.page-title {
  font-size: 24px;
  font-weight: 800;
  color: var(--gz-text-primary);
  margin-bottom: 4px;
}

.page-desc {
  font-size: 14px;
  color: var(--gz-text-tertiary);
  margin-bottom: var(--gz-space-6);
}

/* ---- Stats Grid ---- */
.stats-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--gz-space-4);
  margin-bottom: var(--gz-space-6);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--gz-space-4);
  padding: var(--gz-space-5);
  border-radius: var(--gz-radius-lg);
  background: var(--gz-card-bg);
  backdrop-filter: blur(var(--gz-glass-blur));
  border: var(--gz-glass-border);
  box-shadow: var(--gz-card-shadow);
  position: relative;
  overflow: hidden;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--gz-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.stat-card--blue .stat-icon { background: linear-gradient(135deg, #2563eb, #3b82f6); }
.stat-card--purple .stat-icon { background: linear-gradient(135deg, #7c3aed, #a855f7); }
.stat-card--green .stat-icon { background: linear-gradient(135deg, #059669, #10b981); }
.stat-card--amber .stat-icon { background: linear-gradient(135deg, #d97706, #f59e0b); }

.stat-body {
  flex: 1;
  min-width: 0;
}

.stat-value {
  display: block;
  font-size: 22px;
  font-weight: 800;
  color: var(--gz-text-primary);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--gz-text-tertiary);
}

.stat-badge {
  position: absolute;
  top: var(--gz-space-3);
  right: var(--gz-space-3);
  padding: 2px 8px;
  border-radius: var(--gz-radius-full);
  background: rgba(37, 99, 235, 0.08);
  color: var(--gz-primary);
  font-size: 11px;
  font-weight: 600;
}

.quality-card {
  padding: var(--gz-space-5);
  margin-bottom: var(--gz-space-6);
}

.quality-head,
.quality-panel__title,
.gap-item {
  display: flex;
  align-items: center;
}

.quality-head {
  justify-content: space-between;
  gap: var(--gz-space-3);
  margin-bottom: var(--gz-space-4);
}

.quality-action {
  min-height: 34px;
  padding: 0 12px;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  background: #111827;
  border: 0;
  border-radius: var(--gz-radius-full);
}

.quality-action--full {
  width: 100%;
  margin-top: var(--gz-space-3);
}

.quality-pill {
  padding: 4px 10px;
  color: var(--gz-text-secondary);
  font-size: 12px;
  font-weight: 700;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: var(--gz-radius-full);
}

.quality-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--gz-space-3);
  margin-bottom: var(--gz-space-4);
}

.quality-metric {
  padding: var(--gz-space-4);
  background: rgba(16, 185, 129, 0.08);
  border: 1px solid rgba(16, 185, 129, 0.16);
  border-radius: var(--gz-radius-md);
}

.quality-metric--warn {
  background: rgba(245, 158, 11, 0.1);
  border-color: rgba(245, 158, 11, 0.22);
}

.quality-value {
  display: block;
  color: var(--gz-text-primary);
  font-size: 20px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.quality-label,
.quality-note {
  color: var(--gz-text-tertiary);
  font-size: 12px;
}

.quality-detail-grid {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: var(--gz-space-4);
}

.quality-panel {
  padding: var(--gz-space-4);
  background: rgba(255, 255, 255, 0.68);
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: var(--gz-radius-md);
}

.quality-panel__title {
  gap: 6px;
  margin-bottom: var(--gz-space-3);
  color: var(--gz-text-primary);
  font-size: 13px;
  font-weight: 800;
}

.coverage-bar {
  height: 8px;
  overflow: hidden;
  background: #e5e7eb;
  border-radius: var(--gz-radius-full);
}

.coverage-bar span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #2563eb, #10b981);
  border-radius: inherit;
}

.gap-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: var(--gz-space-3);
}

.gap-item {
  justify-content: space-between;
  gap: 8px;
  min-height: 34px;
  padding: 0 10px;
  color: var(--gz-text-secondary);
  font-size: 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: var(--gz-radius-sm);
}

.gap-item strong {
  color: var(--gz-text-primary);
  font-variant-numeric: tabular-nums;
}

.ai-status {
  display: inline-flex;
  padding: 6px 10px;
  color: #991b1b;
  font-size: 12px;
  font-weight: 800;
  background: #fee2e2;
  border-radius: var(--gz-radius-full);
}

.ai-status--ok {
  color: #166534;
  background: #dcfce7;
}

.quality-note {
  margin: var(--gz-space-3) 0 0;
  line-height: 1.6;
}

.generation-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--gz-space-3);
}

.generation-metric {
  padding: var(--gz-space-4);
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: var(--gz-radius-md);
}

.generation-metric--ok {
  background: rgba(16, 185, 129, 0.08);
  border-color: rgba(16, 185, 129, 0.18);
}

.generation-metric--warn {
  background: rgba(245, 158, 11, 0.1);
  border-color: rgba(245, 158, 11, 0.22);
}

.generation-value {
  display: block;
  color: var(--gz-text-primary);
  font-size: 20px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.generation-label {
  color: var(--gz-text-tertiary);
  font-size: 12px;
}

/* ---- Charts ---- */
.charts-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--gz-space-4);
}

.chart-card {
  padding: var(--gz-space-5);
}

.chart-header {
  margin-bottom: var(--gz-space-4);
}

.chart-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--gz-text-primary);
}

.chart-title-row h3 {
  font-size: 16px;
  font-weight: 600;
}

/* ---- Bar Chart ---- */
.mini-chart {
  height: 180px;
}

.chart-bars {
  display: flex;
  align-items: flex-end;
  gap: var(--gz-space-2);
  height: 150px;
  padding-bottom: var(--gz-space-5);
}

.bar-group {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

.bar-pair {
  flex: 1;
  display: flex;
  align-items: flex-end;
  gap: 3px;
  width: 100%;
  max-width: 32px;
}

.bar {
  flex: 1;
  min-height: 4px;
  border-radius: 3px 3px 0 0;
  transition: height var(--gz-transition-normal);
}

.bar--plans {
  background: linear-gradient(to top, #2563eb, #60a5fa);
}

.bar--users {
  background: linear-gradient(to top, #7c3aed, #c084fc);
}

.bar-label {
  font-size: 10px;
  color: var(--gz-text-tertiary);
  margin-top: 6px;
  white-space: nowrap;
}

.chart-legend {
  display: flex;
  gap: var(--gz-space-4);
  justify-content: center;
  margin-top: var(--gz-space-2);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--gz-text-secondary);
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend-dot--plans { background: #2563eb; }
.legend-dot--users { background: #7c3aed; }

/* ---- Quick Stats ---- */
.quick-list {
  display: flex;
  flex-direction: column;
  gap: var(--gz-space-3);
}

.quick-item {
  display: flex;
  align-items: center;
  gap: var(--gz-space-3);
  padding: var(--gz-space-3);
  border-radius: var(--gz-radius-sm);
  background: rgba(0, 0, 0, 0.015);
}

.quick-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.quick-dot--blue { background: #2563eb; }
.quick-dot--green { background: #10b981; }
.quick-dot--purple { background: #7c3aed; }
.quick-dot--amber { background: #f59e0b; }
.quick-dot--red { background: #ef4444; }

.quick-text {
  flex: 1;
  font-size: 13px;
  color: var(--gz-text-secondary);
}

.quick-val {
  font-size: 15px;
  font-weight: 700;
  color: var(--gz-text-primary);
  font-variant-numeric: tabular-nums;
}

/* ---- Desktop ---- */
@media (min-width: 768px) {
  .page-inner {
    padding: var(--gz-space-8);
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .charts-row {
    grid-template-columns: 3fr 2fr;
  }
}

@media (min-width: 1024px) {
  .stats-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

@media (max-width: 720px) {
  .quality-head,
  .quality-detail-grid {
    display: block;
  }

  .quality-action {
    margin-top: var(--gz-space-3);
  }

  .quality-panel + .quality-panel {
    margin-top: var(--gz-space-3);
  }

  .gap-list,
  .quality-grid {
    grid-template-columns: 1fr;
  }
}
</style>
