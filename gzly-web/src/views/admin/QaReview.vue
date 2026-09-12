<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchQaMonitorLogs, fetchQaMonitorSchools, hideQaByAdmin, toggleQaSchoolStatus } from '@/api/qa'
import { showSuccessToast, showToast } from 'vant'
import { AlertTriangle, Ban, Bot, Eye, Filter, MessageCircle, Search, ShieldAlert, X } from 'lucide-vue-next'

interface MonitorSchool {
  schoolId: string
  schoolName: string
  askCount24h: number
  replyCount24h: number
  autoRejectedCount24h: number
  manualReviewCount24h: number
  highRisk: boolean
  qaDisabled: boolean
  qaDisabledReason?: string
  qaDisabledUntil?: string
  latestRiskAt?: string
  recentRiskLogs?: Array<{ id: number; content: string; authorName: string; status: number; reviewNote?: string; createdAt: string }>
}

interface MonitorLogItem {
  id: number
  schoolId: string
  parentId: number | null
  content: string
  authorName: string
  authorType: string
  status: number
  aiReviewResult: string | null
  reviewNote?: string
  createdAt: string
}

interface TrendPoint {
  label: string
  askCount: number
  replyCount: number
  approvedCount: number
  autoRejectedCount: number
  manualReviewCount: number
}

const activeTab = ref<'schools' | 'logs'>('schools')
const schoolFilter = ref<'all' | 'highRisk' | 'manualReview' | 'rejected' | 'approved'>('all')
const statusFilter = ref<number | undefined>(undefined)
const schoolSearch = ref('')
const schools = ref<MonitorSchool[]>([])
const schoolSummary = ref<any>({})
const logs = ref<MonitorLogItem[]>([])
const logTotal = ref(0)
const page = ref(1)
const loading = ref(false)
const selectedSchool = ref<string>('')

const hideDialogVisible = ref(false)
const hideTargetId = ref<number | null>(null)
const hideReason = ref('')
const toggleDialogVisible = ref(false)
const toggleTargetSchool = ref<MonitorSchool | null>(null)
const toggleReason = ref('')
const toggleUntil = ref('')

const trendPoints = computed<TrendPoint[]>(() => schoolSummary.value?.trend || [])
const activityMax = computed(() => Math.max(1, ...trendPoints.value.map(item => Math.max(item.askCount, item.replyCount))))
const riskMax = computed(() => Math.max(1, ...trendPoints.value.map(item => Math.max(item.autoRejectedCount, item.manualReviewCount))))

const filteredSchools = computed(() => {
  if (!schoolSearch.value.trim()) return schools.value
  const keyword = schoolSearch.value.trim()
  return schools.value.filter(item => item.schoolName.includes(keyword) || item.schoolId.includes(keyword))
})

const statusTabs = [
  { label: '全部日志', value: undefined },
  { label: '已通过', value: 1 },
  { label: '已退回', value: 2 },
  { label: '待校友复核', value: 3 },
]

function parseAiLog(raw: string | null): { label: string; reason: string; cls: string } {
  if (!raw) return { label: 'AI待审核', reason: '尚未进行AI审核', cls: 'ai-none' }
  try {
    const obj = JSON.parse(raw)
    if (obj.pass === true) return { label: 'AI通过', reason: obj.reason || '未说明', cls: 'ai-pass' }
    if (obj.pass === false) return { label: 'AI风险', reason: obj.reason || '未说明', cls: 'ai-reject' }
    if (obj.mode === 'error' || obj.pass === null || obj.error) {
      return { label: 'AI异常', reason: obj.reason || obj.error || '审核流程异常', cls: 'ai-error' }
    }
    return { label: 'AI原始', reason: raw, cls: 'ai-none' }
  } catch {
    return { label: 'AI原始', reason: raw, cls: 'ai-none' }
  }
}

function statusLabel(status: number) {
  return { 1: '已通过', 2: '已退回', 3: '待校友复核' }[status] || '未知'
}

async function loadSchools() {
  loading.value = true
  try {
    const res = await fetchQaMonitorSchools(schoolFilter.value)
    schools.value = res.data?.data?.items || []
    schoolSummary.value = res.data?.data?.summary || {}
  } catch (error: any) {
    showToast(error.message || '加载学校风险失败')
  } finally {
    loading.value = false
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const res = await fetchQaMonitorLogs({
      schoolId: selectedSchool.value || undefined,
      status: statusFilter.value,
      page: page.value,
      size: 20,
    })
    logs.value = res.data?.data?.items || []
    logTotal.value = res.data?.data?.total || 0
  } catch (error: any) {
    showToast(error.message || '加载异常日志失败')
  } finally {
    loading.value = false
  }
}

async function refreshCurrent() {
  if (activeTab.value === 'schools') {
    await loadSchools()
  } else {
    await loadLogs()
  }
}

function openHideDialog(id: number) {
  hideTargetId.value = id
  hideReason.value = ''
  hideDialogVisible.value = true
}

async function confirmHide() {
  if (!hideTargetId.value) return
  if (!hideReason.value.trim()) {
    showToast('请输入下架原因')
    return
  }
  try {
    await hideQaByAdmin(hideTargetId.value, hideReason.value.trim())
    showSuccessToast('已下架异常问答')
    hideDialogVisible.value = false
    await loadLogs()
    await loadSchools()
  } catch (error: any) {
    showToast(error.message || '下架失败')
  }
}

function openToggleDialog(item: MonitorSchool) {
  toggleTargetSchool.value = item
  toggleReason.value = item.qaDisabledReason || ''
  toggleUntil.value = item.qaDisabledUntil ? item.qaDisabledUntil.slice(0, 16) : ''
  toggleDialogVisible.value = true
}

async function confirmToggle(disabled: boolean) {
  if (!toggleTargetSchool.value) return
  if (disabled && !toggleReason.value.trim()) {
    showToast('请输入禁评原因')
    return
  }
  try {
    await toggleQaSchoolStatus({
      schoolId: toggleTargetSchool.value.schoolId,
      disabled,
      reason: disabled ? toggleReason.value.trim() : '',
      disabledUntil: disabled && toggleUntil.value ? `${toggleUntil.value}:00` : null,
    })
    showSuccessToast(disabled ? '已关闭该校问答功能' : '已恢复该校问答功能')
    toggleDialogVisible.value = false
    await loadSchools()
  } catch (error: any) {
    showToast(error.message || '操作失败')
  }
}

function selectSchoolLogs(schoolId: string) {
  selectedSchool.value = schoolId
  activeTab.value = 'logs'
  page.value = 1
  loadLogs()
}

onMounted(() => loadSchools())
</script>

<template>
  <div class="qa-monitor-page">
    <div class="page-inner">
      <div class="page-head">
        <div>
          <h1 class="page-title"><MessageCircle :size="22" /> 问答风控</h1>
          <p class="page-desc">系统管理员只做学校级 AI 监控、异常下架和临时禁评，不再逐条审核普通问答。</p>
        </div>
        <button class="refresh-btn" @click="refreshCurrent">刷新</button>
      </div>

      <div class="tab-switch">
        <button class="tab-btn" :class="{ active: activeTab === 'schools' }" @click="activeTab = 'schools'; loadSchools()">学校风险总览</button>
        <button class="tab-btn" :class="{ active: activeTab === 'logs' }" @click="activeTab = 'logs'; loadLogs()">异常问答日志</button>
      </div>

      <div v-if="activeTab === 'schools'">
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-value">{{ schoolSummary.schoolCount || 0 }}</div>
            <div class="stat-label">监控学校数</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ schoolSummary.highRiskCount || 0 }}</div>
            <div class="stat-label">高风险学校</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ schoolSummary.manualReviewCount || 0 }}</div>
            <div class="stat-label">待校友复核</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ schoolSummary.autoRejectedCount || 0 }}</div>
            <div class="stat-label">自动退回</div>
          </div>
        </div>

        <div class="charts-row" v-if="trendPoints.length">
          <div class="chart-card">
            <div class="chart-header">
              <div>
                <h3>近 7 天问答趋势</h3>
                <p>按天统计提问量与回复量</p>
              </div>
            </div>
            <div class="chart-body">
              <div class="mini-chart">
                <div class="chart-bars">
                  <div v-for="point in trendPoints" :key="point.label" class="bar-group">
                    <div class="bar-pair">
                      <div class="bar bar--ask" :style="{ height: `${(point.askCount / activityMax) * 100}%` }"></div>
                      <div class="bar bar--reply" :style="{ height: `${(point.replyCount / activityMax) * 100}%` }"></div>
                    </div>
                    <span class="bar-label">{{ point.label }}</span>
                  </div>
                </div>
              </div>
              <div class="chart-legend">
                <span class="legend-item"><span class="legend-dot legend-dot--ask"></span>提问量</span>
                <span class="legend-item"><span class="legend-dot legend-dot--reply"></span>回复量</span>
              </div>
            </div>
          </div>

          <div class="chart-card">
            <div class="chart-header">
              <div>
                <h3>近 7 天风险波动</h3>
                <p>关注自动退回和待校友复核的波动情况</p>
              </div>
            </div>
            <div class="chart-body">
              <div class="mini-chart">
                <div class="chart-bars">
                  <div v-for="point in trendPoints" :key="`risk-${point.label}`" class="bar-group">
                    <div class="bar-pair">
                      <div class="bar bar--risk" :style="{ height: `${(point.autoRejectedCount / riskMax) * 100}%` }"></div>
                      <div class="bar bar--manual" :style="{ height: `${(point.manualReviewCount / riskMax) * 100}%` }"></div>
                    </div>
                    <span class="bar-label">{{ point.label }}</span>
                  </div>
                </div>
              </div>
              <div class="chart-legend">
                <span class="legend-item"><span class="legend-dot legend-dot--risk"></span>自动退回</span>
                <span class="legend-item"><span class="legend-dot legend-dot--manual"></span>待校友复核</span>
              </div>
            </div>
          </div>
        </div>

        <div class="filter-bar">
          <div class="status-tabs status-tabs--segment">
            <button v-for="tab in [
              { label: '全部', value: 'all' },
              { label: '高风险学校', value: 'highRisk' },
              { label: '只看待校友复核', value: 'manualReview' },
              { label: '只看已退回', value: 'rejected' },
              { label: '只看已通过', value: 'approved' },
            ]" :key="tab.value" :class="{ active: schoolFilter === tab.value }" @click="schoolFilter = tab.value as any; loadSchools()">
              {{ tab.label }}
            </button>
          </div>
          <div class="search-bar">
            <Search :size="14" />
            <input v-model="schoolSearch" placeholder="搜索学校名称 / schoolId" />
          </div>
        </div>

        <div v-if="loading" class="loading-state">加载中...</div>
        <div v-else-if="!filteredSchools.length" class="empty-state">
          <Filter :size="40" />
          <p>当前没有命中的风险学校</p>
        </div>
        <div v-else class="school-grid">
          <div v-for="item in filteredSchools" :key="item.schoolId" class="school-card" :class="{ 'school-card--high': item.highRisk }">
            <div class="school-head">
              <div>
                <div class="school-name-row">
                  <h3>{{ item.schoolName }}</h3>
                  <span class="school-id">{{ item.schoolId }}</span>
                </div>
                <p class="school-meta">最近异常时间：{{ item.latestRiskAt ? new Date(item.latestRiskAt).toLocaleString('zh-CN') : '无' }}</p>
              </div>
              <span class="risk-badge" :class="{ high: item.highRisk }">{{ item.highRisk ? '高风险' : '监控中' }}</span>
            </div>

            <div class="metric-row">
              <span>24h提问 {{ item.askCount24h }}</span>
              <span>24h回复 {{ item.replyCount24h }}</span>
              <span>自动退回 {{ item.autoRejectedCount24h }}</span>
              <span>待校友复核 {{ item.manualReviewCount24h }}</span>
            </div>

            <div v-if="item.qaDisabled" class="school-disabled-tip">
              <ShieldAlert :size="14" />
              <span>当前已禁评{{ item.qaDisabledReason ? `：${item.qaDisabledReason}` : '' }}</span>
            </div>

            <div class="school-risk-list" v-if="item.recentRiskLogs?.length">
              <div v-for="log in item.recentRiskLogs" :key="log.id" class="risk-log">
                <div class="risk-log-head">
                  <span>{{ log.authorName }}</span>
                  <span>{{ statusLabel(log.status) }}</span>
                </div>
                <p>{{ log.content }}</p>
              </div>
            </div>

            <div class="school-actions">
              <button class="plain-btn" @click="selectSchoolLogs(item.schoolId)"><Eye :size="14" /> 查看异常日志</button>
              <button class="warn-btn" @click="openToggleDialog(item)">
                <Ban :size="14" /> {{ item.qaDisabled ? '恢复问答' : '临时禁评' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-else>
        <div class="filter-bar">
          <div class="status-tabs">
            <button v-for="tab in statusTabs" :key="String(tab.value)" :class="{ active: statusFilter === tab.value }" @click="statusFilter = tab.value; page = 1; loadLogs()">
              {{ tab.label }}
            </button>
          </div>
          <div class="search-bar">
            <Search :size="14" />
            <input v-model="selectedSchool" placeholder="输入 schoolId 筛选" @keyup.enter="page = 1; loadLogs()" />
          </div>
        </div>

        <div v-if="loading" class="loading-state">加载中...</div>
        <div v-else-if="!logs.length" class="empty-state">
          <AlertTriangle :size="40" />
          <p>暂无异常问答日志</p>
        </div>
        <div v-else class="log-list">
          <div v-for="item in logs" :key="item.id" class="log-card">
            <div class="log-head">
              <span class="log-school">{{ item.schoolId }}</span>
              <span class="log-status">{{ statusLabel(item.status) }}</span>
              <span class="log-time">{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</span>
            </div>
            <p class="log-content">{{ item.content }}</p>
            <div class="ai-log" :class="parseAiLog(item.aiReviewResult).cls">
              <Bot :size="13" />
              <span class="ai-log-label">{{ parseAiLog(item.aiReviewResult).label }}</span>
              <span class="ai-log-reason">{{ parseAiLog(item.aiReviewResult).reason }}</span>
            </div>
            <div v-if="item.reviewNote" class="review-note">
              <strong>当前处理说明：</strong>{{ item.reviewNote }}
            </div>
            <div class="log-actions">
              <button class="warn-btn" @click="openHideDialog(item.id)"><X :size="14" /> 下架此问答</button>
            </div>
          </div>
        </div>

        <div v-if="logTotal > 20" class="pagination">
          <button :disabled="page <= 1" @click="page--; loadLogs()">上一页</button>
          <span>第 {{ page }} 页 / 共 {{ Math.ceil(logTotal / 20) }} 页</span>
          <button :disabled="logs.length < 20" @click="page++; loadLogs()">下一页</button>
        </div>
      </div>
    </div>

    <van-overlay :show="hideDialogVisible" @click="hideDialogVisible = false">
      <div class="dialog-wrap" @click.stop>
        <div class="dialog-card">
          <div class="dialog-header">
            <h3>下架异常问答</h3>
            <button class="dialog-close" @click="hideDialogVisible = false"><X :size="18" /></button>
          </div>
          <div class="dialog-body">
            <textarea v-model="hideReason" class="dialog-textarea" rows="4" placeholder="请输入下架原因"></textarea>
          </div>
          <div class="dialog-footer">
            <button class="dialog-btn dialog-btn--ghost" @click="hideDialogVisible = false">取消</button>
            <button class="dialog-btn dialog-btn--danger" @click="confirmHide">确认下架</button>
          </div>
        </div>
      </div>
    </van-overlay>

    <van-overlay :show="toggleDialogVisible" @click="toggleDialogVisible = false">
      <div class="dialog-wrap" @click.stop>
        <div class="dialog-card">
          <div class="dialog-header">
            <h3>{{ toggleTargetSchool?.qaDisabled ? '恢复问答功能' : '临时关闭问答功能' }}</h3>
            <button class="dialog-close" @click="toggleDialogVisible = false"><X :size="18" /></button>
          </div>
          <div class="dialog-body">
            <div class="form-field">
              <label>原因</label>
              <textarea v-model="toggleReason" class="dialog-textarea" rows="3" placeholder="请输入禁评原因"></textarea>
            </div>
            <div class="form-field">
              <label>截止时间（可选）</label>
              <input v-model="toggleUntil" class="dialog-input" type="datetime-local" />
            </div>
          </div>
          <div class="dialog-footer">
            <button class="dialog-btn dialog-btn--ghost" @click="toggleDialogVisible = false">取消</button>
            <button v-if="toggleTargetSchool?.qaDisabled" class="dialog-btn dialog-btn--primary" @click="confirmToggle(false)">恢复问答</button>
            <button v-else class="dialog-btn dialog-btn--danger" @click="confirmToggle(true)">确认禁评</button>
          </div>
        </div>
      </div>
    </van-overlay>
  </div>
</template>

<style scoped>
.qa-monitor-page { padding: 24px; }
.page-inner { max-width: 1180px; margin: 0 auto; }
.page-head { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.page-title { display: flex; align-items: center; gap: 8px; font-size: 22px; font-weight: 800; color: #17181c; }
.page-desc { margin-top: 6px; color: #6a6c72; font-size: 14px; }
.refresh-btn { padding: 8px 16px; border: 1px solid #e5e7eb; border-radius: 10px; background: #fff; cursor: pointer; }
.tab-switch {
  display: inline-flex;
  gap: 6px;
  padding: 4px;
  margin-bottom: 16px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(23, 24, 28, 0.06);
}
.tab-btn {
  padding: 10px 16px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: #4b4d54;
  cursor: pointer;
  font-weight: 700;
  transition: all 0.18s;
}
.tab-btn.active {
  background: linear-gradient(135deg, #17181c, #2b2d33);
  color: #fff;
  box-shadow: 0 6px 16px rgba(23, 24, 28, 0.22);
}
.stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
.stat-card { padding: 16px 18px; background: #fff; border-radius: 16px; border: 1px solid #e5e7eb; box-shadow: 0 8px 24px rgba(23, 24, 28, 0.04); }
.stat-value { font-size: 28px; font-weight: 800; color: #17181c; }
.stat-label { font-size: 12px; color: #6a6c72; margin-top: 4px; }
.charts-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.chart-card {
  padding: 18px;
  background: #fff;
  border-radius: 18px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 8px 24px rgba(23, 24, 28, 0.04);
}
.chart-header h3 {
  font-size: 16px;
  font-weight: 700;
  color: #17181c;
}
.chart-header p {
  margin-top: 4px;
  font-size: 12px;
  color: #6a6c72;
}
.mini-chart {
  margin-top: 16px;
  height: 180px;
  display: flex;
  align-items: flex-end;
}
.chart-bars {
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 10px;
  align-items: end;
}
.bar-group {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}
.bar-pair {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 5px;
}
.bar {
  width: 18px;
  min-height: 4px;
  border-radius: 10px 10px 4px 4px;
}
.bar--ask { background: linear-gradient(180deg, #17181c, #55575e); }
.bar--reply { background: linear-gradient(180deg, #7c3aed, #c084fc); }
.bar--risk { background: linear-gradient(180deg, #f97316, #fb923c); }
.bar--manual { background: linear-gradient(180deg, #4b4d54, #14b8a6); }
.bar-label {
  font-size: 11px;
  color: #6a6c72;
}
.chart-legend {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #6a6c72;
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
}
.legend-dot--ask { background: #17181c; }
.legend-dot--reply { background: #7c3aed; }
.legend-dot--risk { background: #f97316; }
.legend-dot--manual { background: #4b4d54; }
.filter-bar { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.status-tabs { display: flex; gap: 8px; flex-wrap: wrap; }
.status-tabs button {
  padding: 7px 14px;
  border: 1px solid #dbe2ea;
  border-radius: 999px;
  background: #fff;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  color: #6a6c72;
  transition: all 0.18s;
}
.status-tabs button.active {
  background: linear-gradient(135deg, #17181c, #2b2d33);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 6px 16px rgba(23, 24, 28, 0.22);
}

.status-tabs button:hover:not(.active) { background: #fafaf8; color: #383a40; }

.status-tabs--segment {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  background: #fafaf8;
  border: 1px solid #dbe2ea;
  border-radius: 14px;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.85);
}

.status-tabs--segment button {
  min-height: 38px;
  padding: 0 16px;
  border: none;
  border-radius: 10px;
  background: transparent;
  box-shadow: none;
}

.status-tabs--segment button.active {
  background: linear-gradient(135deg, #17181c, #2b2d33);
  color: #fff;
  box-shadow: 0 8px 18px rgba(23, 24, 28, 0.22);
}
.search-bar { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border: 1px solid #e5e7eb; border-radius: 10px; background: #fff; min-width: 280px; }
.search-bar input { border: none; outline: none; flex: 1; }
.loading-state, .empty-state { text-align: center; padding: 64px 20px; color: #97999e; }
.school-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
.school-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 18px; padding: 18px; }
.school-card--high { border-color: #fdba74; box-shadow: 0 10px 30px rgba(251, 146, 60, 0.12); }
.school-head { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.school-name-row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.school-name-row h3 { font-size: 18px; color: #17181c; }
.school-id { font-size: 12px; color: #97999e; }
.school-meta { font-size: 12px; color: #6a6c72; margin-top: 6px; }
.risk-badge { padding: 4px 10px; border-radius: 999px; background: #f4f4f2; color: #17181c; font-size: 12px; font-weight: 700; }
.risk-badge.high { background: #fff7ed; color: #c2410c; }
.metric-row { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; font-size: 12px; color: #4b4d54; margin-bottom: 12px; }
.school-disabled-tip { display: flex; align-items: center; gap: 6px; padding: 8px 10px; border-radius: 10px; background: #f7efef; color: #8f3030; font-size: 12px; margin-bottom: 12px; }
.school-risk-list { display: flex; flex-direction: column; gap: 10px; margin-bottom: 14px; }
.risk-log { padding: 10px 12px; border-radius: 12px; background: #fafaf8; }
.risk-log-head { display: flex; justify-content: space-between; gap: 8px; font-size: 12px; color: #6a6c72; margin-bottom: 6px; }
.risk-log p { font-size: 13px; color: #1f2937; line-height: 1.6; }
.school-actions, .log-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.plain-btn, .warn-btn { display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 10px; border: 1px solid #e5e7eb; background: #fff; cursor: pointer; }
.warn-btn { color: #b34040; border-color: #e3cbcb; }
.log-list { display: flex; flex-direction: column; gap: 12px; }
.log-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 16px; padding: 16px; }
.log-head { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 8px; }
.log-school { font-weight: 700; color: #17181c; }
.log-status, .log-time { font-size: 12px; color: #6a6c72; }
.log-time { margin-left: auto; }
.log-content { font-size: 14px; line-height: 1.6; color: #1f2937; margin-bottom: 10px; }
.ai-log { display: flex; align-items: flex-start; gap: 6px; padding: 10px 12px; border-radius: 10px; background: #fafaf8; border: 1px solid #e3e2de; margin-bottom: 10px; }
.ai-pass { background: #f0fdf4; border-color: #bbf7d0; color: #166534; }
.ai-reject { background: #f7efef; border-color: #e3cbcb; color: #8f3030; }
.ai-error { background: #faf7ef; border-color: #e6dcbd; color: #7c5f33; }
.ai-log-label { font-weight: 700; flex-shrink: 0; }
.ai-log-reason { line-height: 1.5; }
.review-note { padding: 10px 12px; border-radius: 10px; background: #fff7ed; border: 1px solid #fdba74; color: #9a3412; font-size: 12px; line-height: 1.6; margin-bottom: 10px; }
.pagination { display: flex; justify-content: center; gap: 14px; margin-top: 18px; align-items: center; }
.pagination button { padding: 8px 14px; border-radius: 10px; border: 1px solid #e5e7eb; background: #fff; }
.dialog-wrap { display: flex; align-items: center; justify-content: center; height: 100%; padding: 20px; }
.dialog-card { width: 100%; max-width: 520px; background: #fff; border-radius: 18px; overflow: hidden; }
.dialog-header, .dialog-footer { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid #f2f2ef; }
.dialog-footer { border-bottom: none; border-top: 1px solid #f2f2ef; justify-content: flex-end; gap: 10px; }
.dialog-close { width: 32px; height: 32px; border: none; border-radius: 8px; background: #f2f2ef; display: flex; align-items: center; justify-content: center; cursor: pointer; }
.dialog-body { padding: 18px 20px; }
.dialog-textarea, .dialog-input { width: 100%; box-sizing: border-box; border: 1px solid #e5e7eb; border-radius: 10px; padding: 10px 12px; font: inherit; }
.dialog-textarea { min-height: 110px; resize: vertical; }
.dialog-btn { padding: 8px 16px; border-radius: 8px; border: none; cursor: pointer; }
.dialog-btn--ghost { background: #f2f2ef; color: #4b4d54; }
.dialog-btn--primary { background: #17181c; color: #fff; }
.dialog-btn--danger { background: #c04848; color: #fff; }
.form-field label { display: block; font-size: 13px; color: #4b4d54; margin-bottom: 6px; }

@media (max-width: 767px) {
  .qa-monitor-page { padding: 16px; }
  .page-head, .filter-bar { flex-direction: column; }
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
  .charts-row { grid-template-columns: 1fr; }
  .search-bar { min-width: 0; width: 100%; }
}
</style>
