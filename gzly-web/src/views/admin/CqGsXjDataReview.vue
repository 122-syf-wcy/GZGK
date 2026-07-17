<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showSuccessToast, showToast } from 'vant'
import {
  AlertTriangle,
  Download,
  ExternalLink,
  FileText,
  RefreshCcw,
  Save,
  ShieldAlert,
} from 'lucide-vue-next'
import {
  exportAdminCqGsXjDataReview,
  fetchAdminCqGsXjDataReview,
  saveAdminCqGsXjDataReviewDecision,
} from '@/api/admin'
import type {
  DataReviewExportResponse,
  DataReviewListData,
  DataReviewRow,
  DataReviewStatus,
} from '@/types'
import { sanitizeHttpUrl } from '@/utils/markdown'

const page = ref(1)
const pageSize = 50
const loading = ref(false)
const saving = ref(false)
const exporting = ref(false)
const items = ref<DataReviewRow[]>([])
const total = ref(0)
const meta = ref<DataReviewListData | null>(null)
const selectedLineNo = ref<number | null>(null)
const exportResult = ref<DataReviewExportResponse | null>(null)

const filters = reactive({
  provinceCode: '',
  school: '',
  status: '' as DataReviewStatus | '',
  sourceUrlEmpty: '',
  planCountAnomaly: '',
})

const form = reactive({
  decision: 'PENDING' as DataReviewStatus,
  reviewer: '',
  reviewNote: '',
  rejectReason: '',
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const selectedRow = computed(() => items.value.find(row => row.lineNo === selectedLineNo.value) || null)
const stats = computed(() => meta.value?.stats)

onMounted(loadData)

async function loadData(preferredLineNo?: number) {
  loading.value = true
  try {
    const res = await fetchAdminCqGsXjDataReview({
      page: page.value,
      size: pageSize,
      provinceCode: filters.provinceCode || undefined,
      school: filters.school || undefined,
      status: filters.status || undefined,
      sourceUrlEmpty: filters.sourceUrlEmpty === '' ? undefined : filters.sourceUrlEmpty === 'true',
      planCountAnomaly: filters.planCountAnomaly === '' ? undefined : filters.planCountAnomaly === 'true',
    })
    const data = res.data.data
    meta.value = data
    items.value = data.items || []
    total.value = data.total || 0
    const target = preferredLineNo ?? selectedLineNo.value
    const found = target ? items.value.find(row => row.lineNo === target) : null
    if (found) {
      selectRow(found)
    } else if (items.value.length) {
      selectRow(items.value[0])
    } else {
      selectedLineNo.value = null
      resetForm()
    }
  } catch (e: any) {
    showToast(e?.message || '加载人工审核列表失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.decision = 'PENDING'
  form.reviewer = ''
  form.reviewNote = ''
  form.rejectReason = ''
}

function selectRow(row: DataReviewRow) {
  selectedLineNo.value = row.lineNo
  form.decision = row.currentStatus || 'PENDING'
  form.reviewer = row.reviewerToFill || row.reviewer || ''
  form.reviewNote = row.reviewNote || ''
  form.rejectReason = row.rejectReason || ''
}

async function saveDecision(decision?: DataReviewStatus) {
  const row = selectedRow.value
  if (!row) return
  const finalDecision = decision || form.decision
  if (finalDecision === 'APPROVED' && !form.reviewer.trim()) {
    showToast('通过审核必须填写 reviewer')
    return
  }
  if (finalDecision === 'REJECTED' && !form.rejectReason.trim()) {
    showToast('拒绝审核必须填写拒绝原因')
    return
  }
  saving.value = true
  try {
    await saveAdminCqGsXjDataReviewDecision({
      lineNo: row.lineNo,
      decision: finalDecision,
      reviewer: form.reviewer,
      reviewNote: form.reviewNote,
      rejectReason: form.rejectReason,
    })
    showSuccessToast('审核状态已写入 sidecar')
    await loadData(row.lineNo)
  } catch (e: any) {
    showToast(e?.message || '保存审核状态失败')
  } finally {
    saving.value = false
  }
}

async function exportReviewed() {
  exporting.value = true
  try {
    const res = await exportAdminCqGsXjDataReview()
    exportResult.value = res.data.data
    showSuccessToast('已导出 review-only 审核结果')
    await loadData(selectedLineNo.value || undefined)
  } catch (e: any) {
    showToast(e?.message || '导出审核结果失败')
  } finally {
    exporting.value = false
  }
}

async function copyValidatorCommand() {
  const command = meta.value?.validatorCommand
  if (!command) {
    showToast('命令尚未加载')
    return
  }
  try {
    await navigator.clipboard.writeText(command)
    showSuccessToast('已复制 validator dry-run 命令')
  } catch {
    showToast('复制失败，请手动选择命令')
  }
}

function applyFilters() {
  page.value = 1
  loadData()
}

function resetFilters() {
  filters.provinceCode = ''
  filters.school = ''
  filters.status = ''
  filters.sourceUrlEmpty = ''
  filters.planCountAnomaly = ''
  applyFilters()
}

function openSource(row: DataReviewRow) {
  const url = sanitizeHttpUrl(row.sourceUrl)
  if (!url) {
    showToast('source_url 为空或格式不支持')
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}

function statusLabel(status: string) {
  if (status === 'APPROVED') return '已通过'
  if (status === 'REJECTED') return '已拒绝'
  return '待复核'
}

function statusClass(status: string) {
  return {
    APPROVED: 'status-pill status-pill--approved',
    REJECTED: 'status-pill status-pill--rejected',
    PENDING: 'status-pill status-pill--pending',
  }[status] || 'status-pill'
}
</script>

<template>
  <div class="review-page">
    <div class="page-head">
      <div>
        <h1>CQ/GS/XJ 人工审核工作台</h1>
        <p>只处理 review-only 候选，不写 DB、不生成 processed、不 import、不切 Level 2。</p>
      </div>
      <div class="head-actions">
        <button class="ghost-btn" :disabled="loading" @click="loadData()">
          <RefreshCcw :size="14" /> {{ loading ? '加载中…' : '刷新' }}
        </button>
        <button class="primary-btn" :disabled="exporting" @click="exportReviewed">
          <Download :size="14" /> {{ exporting ? '导出中…' : '导出审核结果' }}
        </button>
      </div>
    </div>

    <section class="guard-card">
      <ShieldAlert :size="18" />
      <div>
        <strong>人工审核通过不等于可导入正式库。</strong>
        <p>{{ meta?.safetyNotice || '导入前仍需 dry-run、preflight 和用户确认。当前 CQ/GS/XJ 保持 AI_QA_ONLY + PRE_OFFICIAL_DATA。' }}</p>
      </div>
    </section>

    <section class="stats-grid" v-if="stats">
      <div class="stat-card"><span>总数</span><strong>{{ stats.total }}</strong></div>
      <div class="stat-card"><span>待复核</span><strong>{{ stats.pending }}</strong></div>
      <div class="stat-card"><span>已通过</span><strong>{{ stats.approved }}</strong></div>
      <div class="stat-card"><span>已拒绝</span><strong>{{ stats.rejected }}</strong></div>
      <div class="stat-card"><span>URL 空</span><strong>{{ stats.sourceUrlEmpty }}</strong></div>
      <div class="stat-card"><span>计划数异常</span><strong>{{ stats.planCountAnomaly }}</strong></div>
    </section>

    <section class="filter-bar">
      <select v-model="filters.provinceCode">
        <option value="">全部地区</option>
        <option value="CQ">重庆 CQ</option>
        <option value="GS">甘肃 GS</option>
        <option value="XJ">新疆 XJ</option>
      </select>
      <input v-model.trim="filters.school" placeholder="学校名称 / school_code" />
      <select v-model="filters.status">
        <option value="">全部状态</option>
        <option value="PENDING">PENDING</option>
        <option value="APPROVED">APPROVED</option>
        <option value="REJECTED">REJECTED</option>
      </select>
      <select v-model="filters.sourceUrlEmpty">
        <option value="">source_url 全部</option>
        <option value="true">source_url 为空</option>
        <option value="false">source_url 不为空</option>
      </select>
      <select v-model="filters.planCountAnomaly">
        <option value="">计划数全部</option>
        <option value="true">计划数异常</option>
        <option value="false">计划数正常</option>
      </select>
      <button class="primary-btn" @click="applyFilters">筛选</button>
      <button class="ghost-btn" @click="resetFilters">重置</button>
    </section>

    <div class="workbench-grid">
      <section class="list-panel">
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>行</th>
                <th>状态</th>
                <th>地区</th>
                <th>学校</th>
                <th>批次/科类</th>
                <th>专业</th>
                <th>计划</th>
                <th>来源</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in items"
                :key="row.lineNo"
                :class="{ selected: row.lineNo === selectedLineNo }"
                @click="selectRow(row)"
              >
                <td>{{ row.lineNo }}</td>
                <td><span :class="statusClass(row.currentStatus)">{{ statusLabel(row.currentStatus) }}</span></td>
                <td>{{ row.provinceCode }}</td>
                <td>
                  <strong>{{ row.schoolName }}</strong>
                  <small>{{ row.schoolCode || '无代码' }}</small>
                </td>
                <td>
                  <span>{{ row.batch }}</span>
                  <small>{{ row.subjectCategory }}</small>
                </td>
                <td>{{ row.majorName }}</td>
                <td :class="{ danger: row.planCountAnomaly }">{{ row.planCount || '—' }}</td>
                <td>
                  <button class="link-btn" :disabled="!row.sourceUrl" @click.stop="openSource(row)">
                    <ExternalLink :size="13" /> 打开
                  </button>
                </td>
              </tr>
              <tr v-if="!loading && items.length === 0">
                <td colspan="8" class="empty-cell">暂无匹配候选</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pager">
          <button :disabled="page <= 1" @click="page--; loadData()">上一页</button>
          <span>{{ page }} / {{ totalPages }} · 共 {{ total }}</span>
          <button :disabled="page >= totalPages" @click="page++; loadData()">下一页</button>
        </div>
      </section>

      <aside class="detail-panel">
        <template v-if="selectedRow">
          <div class="detail-head">
            <span :class="statusClass(selectedRow.currentStatus)">{{ selectedRow.currentStatus }}</span>
            <strong>#{{ selectedRow.lineNo }} {{ selectedRow.schoolName }}</strong>
          </div>

          <dl class="detail-list">
            <div><dt>省份/年份</dt><dd>{{ selectedRow.provinceCode }} / {{ selectedRow.year }}</dd></div>
            <div><dt>学校代码</dt><dd>{{ selectedRow.schoolCode || '—' }}</dd></div>
            <div><dt>批次/科类</dt><dd>{{ selectedRow.batch }} / {{ selectedRow.subjectCategory }}</dd></div>
            <div><dt>专业</dt><dd>{{ selectedRow.majorName }}</dd></div>
            <div><dt>计划数</dt><dd>{{ selectedRow.planCount }}</dd></div>
            <div><dt>来源</dt><dd>{{ selectedRow.sourceName }}</dd></div>
            <div><dt>证据文件</dt><dd class="mono">{{ selectedRow.evidenceFile }}</dd></div>
          </dl>

          <button class="ghost-btn detail-open" :disabled="!selectedRow.sourceUrl" @click="openSource(selectedRow)">
            <ExternalLink :size="14" /> 打开 source_url
          </button>

          <div class="form-grid">
            <label>
              审核状态
              <select v-model="form.decision">
                <option value="PENDING">保持 PENDING</option>
                <option value="APPROVED">通过 APPROVED</option>
                <option value="REJECTED">拒绝 REJECTED</option>
              </select>
            </label>
            <label>
              reviewer
              <input v-model.trim="form.reviewer" placeholder="人工复核人，APPROVED 必填" />
            </label>
            <label>
              review_note
              <textarea v-model.trim="form.reviewNote" rows="3" placeholder="核验说明、证据页位置、备注"></textarea>
            </label>
            <label>
              reject_reason
              <textarea v-model.trim="form.rejectReason" rows="3" placeholder="拒绝时必填：批次/科类/计划数/来源等原因"></textarea>
            </label>
          </div>

          <div class="decision-actions">
            <button class="ghost-btn" :disabled="saving" @click="saveDecision('PENDING')">保持 PENDING</button>
            <button class="danger-btn" :disabled="saving" @click="saveDecision('REJECTED')">拒绝</button>
            <button class="primary-btn" :disabled="saving" @click="saveDecision('APPROVED')">
              <Save :size="14" /> 通过
            </button>
          </div>

          <div class="raw-note">
            <AlertTriangle :size="14" />
            <span>{{ selectedRow.notes }}</span>
          </div>
        </template>
        <div v-else class="empty-detail">请选择一行候选</div>
      </aside>
    </div>

    <section class="command-card">
      <div>
        <h3><FileText :size="16" /> Validator dry-run 命令</h3>
        <p>这里只提供命令提示，不自动 import。validator 通过后仍不得写 DB。</p>
      </div>
      <button class="ghost-btn copy-command" @click="copyValidatorCommand">复制命令</button>
      <pre>{{ meta?.validatorCommand || '加载后显示命令' }}</pre>
    </section>

    <section v-if="exportResult" class="export-card">
      <h3>导出结果</h3>
      <ul>
        <li>reviewed: <span class="mono">{{ exportResult.reviewedPath }}</span></li>
        <li>approved: <span class="mono">{{ exportResult.approvedPath }}</span></li>
        <li>rejected: <span class="mono">{{ exportResult.rejectedPath }}</span></li>
        <li>summary: <span class="mono">{{ exportResult.summaryPath }}</span></li>
      </ul>
      <p>{{ exportResult.notice }}</p>
    </section>
  </div>
</template>

<style scoped>
.review-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px;
  color: #111827;
}
.page-head,
.head-actions,
.filter-bar,
.decision-actions,
.detail-head,
.command-card h3 {
  display: flex;
  align-items: center;
}
.page-head {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.page-head h1 {
  margin: 0 0 6px;
  font-size: 24px;
}
.page-head p,
.guard-card p,
.command-card p,
.export-card p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}
.head-actions {
  gap: 10px;
  flex-wrap: wrap;
}
.guard-card,
.command-card,
.export-card,
.list-panel,
.detail-panel,
.stat-card {
  background: #fffdfa;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.guard-card {
  display: flex;
  gap: 12px;
  padding: 14px;
  margin-bottom: 16px;
  color: #92400e;
  background: #fffbeb;
  border-color: #fde68a;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}
.stat-card {
  padding: 12px;
}
.stat-card span {
  display: block;
  color: #64748b;
  font-size: 12px;
}
.stat-card strong {
  display: block;
  margin-top: 4px;
  font-size: 20px;
}
.filter-bar {
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
input,
select,
textarea {
  width: 100%;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 8px 10px;
  font: inherit;
  background: #fff;
  box-sizing: border-box;
}
.filter-bar input,
.filter-bar select {
  width: auto;
  min-width: 150px;
}
.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
}
.list-panel,
.detail-panel {
  min-width: 0;
}
.table-wrap {
  overflow-x: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  min-width: 900px;
}
th,
td {
  padding: 10px 12px;
  border-bottom: 1px solid #eef2f7;
  text-align: left;
  vertical-align: top;
  font-size: 13px;
}
th {
  color: #475569;
  background: #f8fafc;
  font-weight: 700;
}
tr {
  cursor: pointer;
}
tr.selected {
  background: #eff6ff;
}
td small {
  display: block;
  margin-top: 4px;
  color: #64748b;
}
.danger {
  color: #b91c1c;
  font-weight: 700;
}
.status-pill {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}
.status-pill--pending {
  background: #f1f5f9;
  color: #475569;
}
.status-pill--approved {
  background: #dcfce7;
  color: #166534;
}
.status-pill--rejected {
  background: #fee2e2;
  color: #991b1b;
}
.pager {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  padding: 12px;
}
.detail-panel {
  position: sticky;
  top: 16px;
  align-self: start;
  padding: 16px;
}
.detail-head {
  gap: 8px;
  margin-bottom: 12px;
}
.detail-list {
  display: grid;
  gap: 8px;
  margin: 0 0 12px;
}
.detail-list div {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 10px;
}
dt {
  color: #64748b;
  font-size: 12px;
}
dd {
  margin: 0;
  font-size: 13px;
  overflow-wrap: anywhere;
}
.form-grid {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}
label {
  display: grid;
  gap: 6px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}
.decision-actions {
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
  flex-wrap: wrap;
}
.raw-note {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding: 10px;
  border-radius: 6px;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
}
.command-card,
.export-card {
  margin-top: 16px;
  padding: 16px;
}
.command-card {
  position: relative;
}
.command-card h3 {
  gap: 8px;
  margin: 0 0 6px;
}
.copy-command {
  position: absolute;
  top: 14px;
  right: 14px;
}
pre {
  overflow-x: auto;
  margin: 12px 0 0;
  padding: 12px;
  border-radius: 6px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}
.primary-btn,
.ghost-btn,
.danger-btn,
.link-btn,
.pager button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: 6px;
  border: 1px solid transparent;
  padding: 8px 12px;
  font-weight: 700;
  cursor: pointer;
  background: #fff;
}
.primary-btn {
  background: #111827;
  color: #fff;
}
.ghost-btn,
.pager button {
  border-color: #d1d5db;
  color: #111827;
}
.danger-btn {
  background: #fee2e2;
  color: #991b1b;
}
.link-btn {
  padding: 4px 6px;
  color: #2563eb;
}
button:disabled {
  opacity: .5;
  cursor: not-allowed;
}
.empty-cell,
.empty-detail {
  padding: 24px;
  text-align: center;
  color: #64748b;
}
@media (max-width: 900px) {
  .review-page {
    padding: 16px;
  }
  .page-head,
  .workbench-grid {
    display: block;
  }
  .head-actions {
    margin-top: 12px;
  }
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .filter-bar input,
  .filter-bar select,
  .filter-bar button {
    width: 100%;
  }
  .detail-panel {
    position: static;
    margin-top: 16px;
  }
  .copy-command {
    position: static;
    margin-top: 12px;
  }
}
</style>
