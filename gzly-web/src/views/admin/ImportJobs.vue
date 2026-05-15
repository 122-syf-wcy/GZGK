<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showSuccessToast, showToast } from 'vant'
import {
  AlertTriangle,
  CheckCircle2,
  ClipboardCheck,
  Database,
  FileCode2,
  FilePlus2,
  Loader2,
  Play,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
} from 'lucide-vue-next'
import {
  createAdminImportJob,
  fetchAdminDataYearReadiness,
  fetchAdminImportJobs,
  generateAdminImportJobFormalSql,
  generateAdminImportJobRollbackPlan,
  runAdminImportJobQualityCheck,
  runAdminImportJobStaging,
} from '@/api/admin'
import type {
  AdminCreateImportJobRequest,
  AdminDataYearReadiness,
  AdminImportDataType,
  AdminImportJob,
  AdminImportJobStatus,
} from '@/types'

type Tone = 'idle' | 'ok' | 'warn' | 'info'

interface DataTypeMeta {
  value: AdminImportDataType
  label: string
  required: boolean
}

const DATA_TYPES: DataTypeMeta[] = [
  { value: 'SCORE_SEGMENT', label: '一分一段表', required: true },
  { value: 'ADMISSION_PLAN', label: '招生计划', required: true },
  { value: 'POLICY_RULE', label: '批次政策', required: true },
  { value: 'MAJOR_REQUIREMENT', label: '选科要求', required: true },
  { value: 'MAJOR_META', label: '专业限制', required: true },
  { value: 'SCORE_LINE', label: '批次线', required: true },
  { value: 'ART_SPORTS_RULE', label: '艺术体育规则', required: false },
  { value: 'SPECIAL_ELIGIBILITY', label: '特殊计划资格', required: false },
]

const STATUS_META: Record<string, { label: string; tone: Tone; rank: number }> = {
  CREATED: { label: '已创建', tone: 'idle', rank: 1 },
  FILE_REGISTERED: { label: '来源已登记', tone: 'info', rank: 2 },
  STAGING_READY: { label: 'staging 就绪', tone: 'info', rank: 3 },
  QUALITY_CHECKED: { label: '质检已生成', tone: 'ok', rank: 4 },
  FORMAL_SQL_GENERATED: { label: '确认 SQL 已生成', tone: 'ok', rank: 5 },
  WAITING_CONFIRMATION: { label: '等待确认', tone: 'warn', rank: 6 },
  PROMOTED: { label: '已晋级', tone: 'ok', rank: 7 },
  ROLLBACK_READY: { label: '回滚计划已生成', tone: 'warn', rank: 6 },
  FAILED: { label: '失败', tone: 'warn', rank: 0 },
}

const PHASE_META: Record<string, { label: string; desc: string; tone: Tone }> = {
  PRE_OFFICIAL_DATA: {
    label: 'PRE_OFFICIAL_DATA',
    desc: '2026 官方关键数据未确认导入，普通用户仍停留在准备期。',
    tone: 'warn',
  },
  OFFICIAL_DATA_PARTIAL: {
    label: 'OFFICIAL_DATA_PARTIAL',
    desc: '已有部分 2026 数据进入准备链路，仍不开放完整推荐。',
    tone: 'info',
  },
  OFFICIAL_DATA_IMPORTED: {
    label: 'OFFICIAL_DATA_IMPORTED',
    desc: '关键数据已导入，可进入试运行校验。',
    tone: 'ok',
  },
  MODEL_RETRAINED: {
    label: 'MODEL_RETRAINED',
    desc: '模型重训与质检完成后，可按门禁升级。',
    tone: 'ok',
  },
}

const READINESS_FLAGS = [
  { key: 'policyReady', label: 'policy_ready' },
  { key: 'scoreSegmentReady', label: 'score_segment_ready' },
  { key: 'admissionPlanReady', label: 'admission_plan_ready' },
  { key: 'majorRequirementReady', label: 'major_requirement_ready' },
  { key: 'majorMetaReady', label: 'major_meta_ready' },
  { key: 'mlTrainingReady', label: 'ml_training_ready' },
  { key: 'historicalTrainingReady', label: 'historical_training_ready' },
] as const

const jobs = ref<AdminImportJob[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)
const readinessLoading = ref(false)
const readiness = ref<AdminDataYearReadiness | null>(null)
const showCreateDialog = ref(false)
const creating = ref(false)
const actionKey = ref('')

const filters = reactive({
  dataType: '',
  status: '',
})

const form = reactive<AdminCreateImportJobRequest>({
  provinceCode: 'GZ',
  year: 2026,
  dataType: 'SCORE_SEGMENT',
  sourceFile: '',
  sourceUrl: '',
  sourceManifest: '',
  fileHash: '',
  rawText: '',
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const phase = computed(() => readiness.value?.recommendationPhase || 'PRE_OFFICIAL_DATA')
const phaseMeta = computed(() => PHASE_META[phase.value] || { label: phase.value, desc: '当前阶段需管理员复核。', tone: 'warn' as Tone })
const readyFlagCount = computed(() => READINESS_FLAGS.filter(item => Boolean(readiness.value?.[item.key])).length)
const importProgressItems = computed(() => DATA_TYPES.map((item) => {
  const progress = readiness.value?.importProgress?.[item.value]
  return {
    ...item,
    latestStatus: progress?.latestStatus || 'NOT_STARTED',
    importBatchId: progress?.importBatchId || '',
    qualityReportPath: progress?.qualityReportPath || '',
    rollbackSqlPath: progress?.rollbackSqlPath || '',
    updatedAt: progress?.updatedAt || '',
  }
}))

onMounted(() => {
  refreshAll()
})

function getDataTypeLabel(value: string): string {
  return DATA_TYPES.find(item => item.value === value)?.label || value
}

function getStatusMeta(status: AdminImportJobStatus | string) {
  return STATUS_META[status] || { label: status || '未知状态', tone: 'warn' as Tone, rank: 0 }
}

function getStatusLabel(status: AdminImportJobStatus | string): string {
  return getStatusMeta(status).label
}

function getStatusTone(status: AdminImportJobStatus | string): Tone {
  return getStatusMeta(status).tone
}

function formatTime(value?: string): string {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function isActionRunning(job: AdminImportJob, name: string): boolean {
  return actionKey.value === `${job.jobId}:${name}`
}

function canQualityCheck(job: AdminImportJob): boolean {
  return getStatusMeta(job.status).rank >= STATUS_META.STAGING_READY.rank && job.status !== 'PROMOTED'
}

function canGenerateFormalSql(job: AdminImportJob): boolean {
  return getStatusMeta(job.status).rank >= STATUS_META.QUALITY_CHECKED.rank && job.status !== 'PROMOTED'
}

function canGenerateRollback(job: AdminImportJob): boolean {
  return getStatusMeta(job.status).rank >= STATUS_META.FORMAL_SQL_GENERATED.rank && job.status !== 'PROMOTED'
}

function resetCreateForm() {
  form.provinceCode = 'GZ'
  form.year = 2026
  form.dataType = 'SCORE_SEGMENT'
  form.sourceFile = ''
  form.sourceUrl = ''
  form.sourceManifest = ''
  form.fileHash = ''
  form.rawText = ''
}

async function refreshAll() {
  await Promise.all([loadJobs(), loadReadiness()])
}

async function loadJobs() {
  loading.value = true
  try {
    const res = await fetchAdminImportJobs({
      provinceCode: 'GZ',
      year: 2026,
      dataType: filters.dataType || undefined,
      status: filters.status || undefined,
      page: page.value,
      pageSize,
    })
    const data = res.data.data
    jobs.value = data?.items || []
    total.value = data?.total || 0
  } catch (error) {
    const err = error as Error
    showToast(err.message || '导入任务加载失败')
  } finally {
    loading.value = false
  }
}

async function loadReadiness() {
  readinessLoading.value = true
  try {
    const res = await fetchAdminDataYearReadiness('GZ', 2026)
    readiness.value = res.data.data
  } catch (error) {
    const err = error as Error
    showToast(err.message || 'readiness 加载失败')
  } finally {
    readinessLoading.value = false
  }
}

async function createJob() {
  if (!form.sourceFile?.trim() && !form.sourceUrl?.trim()) {
    showToast('请填写 source_file 或 source_url')
    return
  }
  creating.value = true
  try {
    await createAdminImportJob({
      ...form,
      provinceCode: 'GZ',
      year: 2026,
      sourceFile: form.sourceFile?.trim(),
      sourceUrl: form.sourceUrl?.trim(),
      sourceManifest: form.sourceManifest?.trim(),
      fileHash: form.fileHash?.trim(),
      rawText: form.rawText?.trim(),
    })
    showCreateDialog.value = false
    resetCreateForm()
    showSuccessToast('导入任务已创建')
    page.value = 1
    await refreshAll()
  } catch (error) {
    const err = error as Error
    showToast(err.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function runAction(job: AdminImportJob, name: string, runner: (jobId: string) => Promise<unknown>, successText: string) {
  actionKey.value = `${job.jobId}:${name}`
  try {
    await runner(job.jobId)
    showSuccessToast(successText)
    await refreshAll()
  } catch (error) {
    const err = error as Error
    showToast(err.message || '操作失败')
  } finally {
    actionKey.value = ''
  }
}

function applyFilters() {
  page.value = 1
  loadJobs()
}

function goPage(nextPage: number) {
  page.value = Math.min(Math.max(1, nextPage), totalPages.value)
  loadJobs()
}
</script>

<template>
  <div class="import-page">
    <div class="page-inner">
      <div class="page-head">
        <div>
          <h1 class="page-title">2026 官方数据导入任务</h1>
          <p class="page-desc">管理员登记官方来源、生成 staging 与质检确认包，当前页面不执行正式晋级。</p>
        </div>
        <div class="head-actions">
          <button class="secondary-btn" :disabled="loading || readinessLoading" @click="refreshAll">
            <RefreshCw :size="16" :class="{ spin: loading || readinessLoading }" />
            刷新
          </button>
          <button class="primary-btn" @click="showCreateDialog = true">
            <FilePlus2 :size="16" />
            创建任务
          </button>
        </div>
      </div>

      <section class="safety-panel">
        <AlertTriangle :size="18" />
        <div>
          <strong>仅生成确认包，未正式导入</strong>
          <p>本轮接口只登记来源、更新任务状态、生成质量报告路径、确认 SQL 路径和回滚计划路径；不会写正式招生计划表，也不会开启 FULL_RECOMMEND。</p>
        </div>
      </section>

      <section class="readiness-panel gz-card">
        <div class="section-head">
          <div>
            <div class="section-title">
              <ShieldCheck :size="17" />
              <h2>GZ 2026 readiness</h2>
            </div>
            <p class="section-desc">{{ phaseMeta.desc }}</p>
          </div>
          <span class="phase-badge" :class="`tone-${phaseMeta.tone}`">{{ phaseMeta.label }}</span>
        </div>

        <div class="readiness-summary">
          <div class="summary-number">
            <span>{{ readyFlagCount }}</span>
            <small>/ {{ READINESS_FLAGS.length }}</small>
          </div>
          <div class="summary-copy">
            <strong>readiness 字段就绪数</strong>
            <p>官方关键数据和模型训练标记仍以服务器 readiness 为准。</p>
          </div>
        </div>

        <div class="flag-grid">
          <div
            v-for="item in READINESS_FLAGS"
            :key="item.key"
            class="flag-item"
            :class="{ ready: Boolean(readiness?.[item.key]) }"
          >
            <CheckCircle2 :size="15" />
            <span>{{ item.label }}</span>
            <strong>{{ readiness?.[item.key] ? 'ready' : 'pending' }}</strong>
          </div>
        </div>

        <div class="progress-table">
          <div class="progress-row progress-row--head">
            <span>数据类型</span>
            <span>最近状态</span>
            <span>import_batch_id</span>
            <span>报告</span>
          </div>
          <div v-for="item in importProgressItems" :key="item.value" class="progress-row">
            <span>
              {{ item.label }}
              <small>{{ item.required ? '必须' : '按官方发布' }}</small>
            </span>
            <span class="status-pill" :class="`tone-${getStatusTone(item.latestStatus)}`">{{ getStatusLabel(item.latestStatus) }}</span>
            <code>{{ item.importBatchId || '-' }}</code>
            <span class="path-text">{{ item.qualityReportPath || item.rollbackSqlPath || '-' }}</span>
          </div>
        </div>
      </section>

      <section class="jobs-panel gz-card">
        <div class="section-head">
          <div>
            <div class="section-title">
              <Database :size="17" />
              <h2>导入任务列表</h2>
            </div>
            <p class="section-desc">普通用户入口不会显示本页面，也不会看到导入操作。</p>
          </div>
          <span class="total-pill">共 {{ total }} 个任务</span>
        </div>

        <div class="toolbar">
          <label>
            数据类型
            <select v-model="filters.dataType" @change="applyFilters">
              <option value="">全部</option>
              <option v-for="item in DATA_TYPES" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
          <label>
            状态
            <select v-model="filters.status" @change="applyFilters">
              <option value="">全部</option>
              <option v-for="(meta, key) in STATUS_META" :key="key" :value="key">{{ meta.label }}</option>
            </select>
          </label>
        </div>

        <div class="job-list">
          <div class="job-row job-row--head">
            <span>任务</span>
            <span>来源</span>
            <span>质检摘要</span>
            <span>确认包</span>
            <span>操作</span>
          </div>

          <div v-if="loading" class="state-row">
            <Loader2 :size="18" class="spin" />
            正在加载导入任务
          </div>

          <div v-else-if="jobs.length === 0" class="state-row">
            <FilePlus2 :size="18" />
            当前没有导入任务，可先登记官方来源。
          </div>

          <article v-for="job in jobs" v-else :key="job.jobId" class="job-row">
            <div class="job-main">
              <span class="status-pill" :class="`tone-${getStatusTone(job.status)}`">{{ getStatusLabel(job.status) }}</span>
              <strong>{{ getDataTypeLabel(job.dataType) }}</strong>
              <code>{{ job.importBatchId }}</code>
              <small>{{ formatTime(job.updatedAt) }}</small>
            </div>

            <div class="source-cell">
              <span>{{ job.sourceFile || '-' }}</span>
              <a v-if="job.sourceUrl" :href="job.sourceUrl" target="_blank" rel="noreferrer">{{ job.sourceUrl }}</a>
              <small v-if="job.sourceManifest">manifest: {{ job.sourceManifest }}</small>
            </div>

            <div class="quality-cell">
              <span>total {{ job.totalRows }}</span>
              <span>clean {{ job.cleanRows }}</span>
              <span>review {{ job.reviewRows }}</span>
              <span>error {{ job.errorRows }}</span>
            </div>

            <div class="package-cell">
              <span><ClipboardCheck :size="14" /> {{ job.qualityReportPath || '-' }}</span>
              <span><FileCode2 :size="14" /> {{ job.formalSqlPath || '-' }}</span>
              <span><RotateCcw :size="14" /> {{ job.rollbackSqlPath || '-' }}</span>
              <small>{{ job.message }}</small>
            </div>

            <div class="action-cell">
              <button
                class="row-btn"
                :disabled="job.status === 'PROMOTED' || isActionRunning(job, 'staging')"
                @click="runAction(job, 'staging', runAdminImportJobStaging, 'staging dry-run 已生成')"
              >
                <Play :size="14" />
                staging
              </button>
              <button
                class="row-btn"
                :disabled="!canQualityCheck(job) || isActionRunning(job, 'quality')"
                @click="runAction(job, 'quality', runAdminImportJobQualityCheck, '质量报告路径已生成')"
              >
                <ClipboardCheck :size="14" />
                质检
              </button>
              <button
                class="row-btn"
                :disabled="!canGenerateFormalSql(job) || isActionRunning(job, 'formal')"
                @click="runAction(job, 'formal', generateAdminImportJobFormalSql, '确认 SQL 路径已生成')"
              >
                <FileCode2 :size="14" />
                SQL
              </button>
              <button
                class="row-btn"
                :disabled="!canGenerateRollback(job) || isActionRunning(job, 'rollback')"
                @click="runAction(job, 'rollback', generateAdminImportJobRollbackPlan, '回滚计划路径已生成')"
              >
                <RotateCcw :size="14" />
                回滚
              </button>
            </div>
          </article>
        </div>

        <div class="pagination">
          <button class="page-btn" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
          <span class="page-info">第 {{ page }} / {{ totalPages }} 页</span>
          <button class="page-btn" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
        </div>
      </section>
    </div>

    <div v-if="showCreateDialog" class="modal-mask" @click.self="showCreateDialog = false">
      <section class="create-modal">
        <div class="modal-head">
          <div>
            <h2>创建导入任务</h2>
            <p>年份固定为 2026，省份固定为 GZ；import_batch_id 将由后端生成。</p>
          </div>
          <button class="icon-btn" @click="showCreateDialog = false">×</button>
        </div>

        <div class="form-grid">
          <label>
            年份
            <input v-model.number="form.year" type="number" disabled />
          </label>
          <label>
            数据类型
            <select v-model="form.dataType">
              <option v-for="item in DATA_TYPES" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
          <label>
            source_file
            <input v-model="form.sourceFile" placeholder="例如 gz_2026_score_segment.pdf" />
          </label>
          <label>
            source_url
            <input v-model="form.sourceUrl" placeholder="官方发布页面 URL" />
          </label>
          <label>
            source_manifest
            <input v-model="form.sourceManifest" placeholder="manifest 路径或 URL" />
          </label>
          <label>
            file_hash
            <input v-model="form.fileHash" placeholder="官方文件 hash，可后补" />
          </label>
          <label class="wide-field">
            raw_text
            <textarea v-model="form.rawText" rows="4" placeholder="官方原文摘录或管理员登记说明"></textarea>
          </label>
        </div>

        <div class="modal-note">
          <AlertTriangle :size="15" />
          任务创建只登记来源并生成批次号，不解析文件、不写正式表。
        </div>

        <div class="modal-actions">
          <button class="secondary-btn" :disabled="creating" @click="showCreateDialog = false">取消</button>
          <button class="primary-btn" :disabled="creating" @click="createJob">
            <Loader2 v-if="creating" :size="16" class="spin" />
            <FilePlus2 v-else :size="16" />
            创建任务
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.import-page {
  min-height: 100dvh;
}

.page-inner {
  max-width: 1320px;
  margin: 0 auto;
  padding: var(--gz-space-5) var(--gz-space-4);
}

.page-head,
.section-head,
.head-actions,
.section-title,
.modal-head,
.modal-actions {
  display: flex;
  align-items: center;
}

.page-head,
.section-head {
  justify-content: space-between;
  gap: var(--gz-space-4);
  margin-bottom: var(--gz-space-4);
}

.page-title {
  font-size: 24px;
  line-height: 1.25;
  font-weight: 800;
  color: var(--gz-text-primary);
}

.page-desc,
.section-desc {
  margin-top: 6px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--gz-text-tertiary);
}

.head-actions {
  flex-wrap: wrap;
  gap: var(--gz-space-2);
}

.primary-btn,
.secondary-btn,
.row-btn,
.page-btn,
.icon-btn {
  border: 1px solid transparent;
  background: #fffdfa;
  color: var(--gz-text-primary);
  font: inherit;
  transition: background var(--gz-transition-fast), border-color var(--gz-transition-fast), color var(--gz-transition-fast), transform var(--gz-transition-fast);
}

.primary-btn,
.secondary-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 14px;
  border-radius: var(--gz-radius-sm);
  font-size: 14px;
  font-weight: 700;
}

.primary-btn {
  background: #0f172a;
  color: #fff;
}

.secondary-btn {
  border-color: rgba(15, 23, 42, 0.12);
  color: var(--gz-text-secondary);
}

.primary-btn:hover:not(:disabled),
.secondary-btn:hover:not(:disabled),
.row-btn:hover:not(:disabled),
.page-btn:hover:not(:disabled),
.icon-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.48;
}

.safety-panel {
  display: flex;
  gap: var(--gz-space-3);
  margin-bottom: var(--gz-space-4);
  padding: var(--gz-space-4);
  border: 1px solid #fde68a;
  border-radius: var(--gz-radius-md);
  background: #fffbeb;
  color: #92400e;
}

.safety-panel strong {
  display: block;
  font-size: 14px;
  line-height: 1.4;
}

.safety-panel p {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.7;
}

.readiness-panel,
.jobs-panel {
  padding: var(--gz-space-5);
}

.section-title {
  gap: 8px;
}

.section-title h2 {
  font-size: 17px;
  line-height: 1.3;
  font-weight: 800;
  color: var(--gz-text-primary);
}

.phase-badge,
.status-pill,
.total-pill {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: var(--gz-radius-full);
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.total-pill {
  border: 1px solid rgba(15, 23, 42, 0.08);
  color: var(--gz-text-secondary);
  background: #f8fafc;
}

.tone-ok {
  background: #ecfdf5;
  color: #047857;
}

.tone-warn {
  background: #fffbeb;
  color: #b45309;
}

.tone-info {
  background: #eff6ff;
  color: #1d4ed8;
}

.tone-idle {
  background: #f1f5f9;
  color: #475569;
}

.readiness-summary {
  display: flex;
  align-items: center;
  gap: var(--gz-space-4);
  padding: var(--gz-space-4);
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: var(--gz-radius-md);
  background: #f8fafc;
}

.summary-number {
  min-width: 92px;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.summary-number span {
  font-size: 30px;
  font-weight: 800;
}

.summary-number small,
.summary-copy p {
  color: var(--gz-text-tertiary);
}

.summary-copy strong {
  font-size: 14px;
  color: var(--gz-text-primary);
}

.summary-copy p {
  margin-top: 3px;
  font-size: 13px;
}

.flag-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--gz-space-3);
  margin-top: var(--gz-space-4);
}

.flag-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: var(--gz-radius-sm);
  background: #fffdfa;
  color: var(--gz-text-secondary);
}

.flag-item.ready {
  border-color: rgba(4, 120, 87, 0.18);
  background: #f0fdf4;
  color: #047857;
}

.flag-item span,
.flag-item strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

.flag-item strong {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.progress-table,
.job-list {
  margin-top: var(--gz-space-4);
  overflow-x: auto;
}

.progress-row,
.job-row {
  display: grid;
  gap: var(--gz-space-3);
  min-width: 940px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.progress-row {
  grid-template-columns: 190px 150px 260px minmax(240px, 1fr);
  align-items: center;
  padding: 12px 0;
  font-size: 13px;
}

.job-row {
  grid-template-columns: 230px 240px 160px minmax(240px, 1fr) 210px;
  align-items: start;
  padding: 14px 0;
}

.progress-row--head,
.job-row--head {
  color: var(--gz-text-tertiary);
  font-size: 12px;
  font-weight: 800;
}

.progress-row small,
.job-main small,
.source-cell small,
.package-cell small {
  display: block;
  margin-top: 4px;
  color: var(--gz-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.path-text,
.package-cell span,
.source-cell a,
code {
  overflow-wrap: anywhere;
}

code {
  font-family: 'SF Mono', SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  color: #334155;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gz-space-3);
  margin-bottom: var(--gz-space-4);
}

.toolbar label,
.form-grid label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--gz-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

select,
input,
textarea {
  width: 100%;
  border: 1px solid #dbe3ef;
  border-radius: var(--gz-radius-sm);
  background: #fffdfa;
  color: var(--gz-text-primary);
  font: inherit;
  font-size: 14px;
  outline: none;
}

select,
input {
  min-height: 38px;
  padding: 0 10px;
}

textarea {
  resize: vertical;
  min-height: 96px;
  padding: 10px;
  line-height: 1.6;
}

select:focus,
input:focus,
textarea:focus {
  border-color: var(--gz-primary);
}

input:disabled {
  color: var(--gz-text-tertiary);
  background: #f8fafc;
}

.job-main,
.source-cell,
.quality-cell,
.package-cell,
.action-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}

.job-main strong {
  font-size: 14px;
  color: var(--gz-text-primary);
}

.source-cell,
.package-cell,
.quality-cell {
  color: var(--gz-text-secondary);
  font-size: 13px;
}

.source-cell a {
  max-width: 100%;
  font-size: 12px;
}

.quality-cell {
  font-variant-numeric: tabular-nums;
}

.package-cell span {
  display: inline-flex;
  align-items: flex-start;
  gap: 6px;
}

.action-cell {
  flex-direction: row;
  flex-wrap: wrap;
}

.row-btn,
.page-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 30px;
  padding: 0 9px;
  border-color: #dbe3ef;
  border-radius: var(--gz-radius-sm);
  font-size: 12px;
  font-weight: 700;
}

.state-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: var(--gz-space-8) 0;
  color: var(--gz-text-tertiary);
  font-size: 14px;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: var(--gz-space-3);
  padding-top: var(--gz-space-4);
}

.page-info {
  font-size: 13px;
  color: var(--gz-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: var(--gz-z-modal);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--gz-space-4);
  background: rgba(15, 23, 42, 0.42);
}

.create-modal {
  width: min(760px, 100%);
  max-height: min(88dvh, 860px);
  overflow-y: auto;
  padding: var(--gz-space-5);
  border-radius: var(--gz-radius-lg);
  border: 1px solid rgba(15, 23, 42, 0.1);
  background: #fffdfa;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.22);
}

.modal-head {
  justify-content: space-between;
  gap: var(--gz-space-4);
  margin-bottom: var(--gz-space-4);
}

.modal-head h2 {
  font-size: 18px;
  font-weight: 800;
}

.modal-head p,
.modal-note {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--gz-text-tertiary);
}

.icon-btn {
  width: 34px;
  height: 34px;
  border-color: #dbe3ef;
  border-radius: var(--gz-radius-sm);
  font-size: 22px;
  line-height: 1;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--gz-space-4);
}

.wide-field {
  grid-column: 1 / -1;
}

.modal-note {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: var(--gz-space-4);
  padding: 10px 12px;
  border-radius: var(--gz-radius-sm);
  background: #fffbeb;
  color: #92400e;
}

.modal-actions {
  justify-content: flex-end;
  gap: var(--gz-space-2);
  margin-top: var(--gz-space-4);
}

.spin {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (min-width: 768px) {
  .page-inner {
    padding: var(--gz-space-8);
  }
}

@media (max-width: 767px) {
  .page-head,
  .section-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .head-actions,
  .primary-btn,
  .secondary-btn {
    width: 100%;
  }

  .readiness-summary {
    align-items: flex-start;
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
