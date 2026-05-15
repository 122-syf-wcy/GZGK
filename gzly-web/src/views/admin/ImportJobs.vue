<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  AlertTriangle,
  CheckCircle2,
  ClipboardList,
  Database,
  FileText,
  FolderOpen,
  Lock,
  PackageCheck,
  RefreshCcw,
  ShieldAlert,
} from 'lucide-vue-next'
import {
  createAdminImportJob,
  fetchAdminDataYearReadiness,
  fetchAdminImportJobDetail,
  fetchAdminImportJobs,
  generateAdminImportJobFormalSql,
  generateAdminImportJobRollbackPlan,
  generateAdminImportJobStaging,
  runAdminImportJobQualityCheck,
} from '@/api/admin'
import type {
  AdminImportJobDetail,
  AdminImportJobSummary,
  CreateAdminImportJobRequest,
  DataYearReadinessDto,
} from '@/types'

const loading = ref(false)
const actionLoading = ref('')
const errorMsg = ref('')
const successMsg = ref('')
const jobs = ref<AdminImportJobSummary[]>([])
const total = ref(0)
const selected = ref<AdminImportJobDetail | null>(null)
const readiness = ref<DataYearReadinessDto | null>(null)
const filters = ref({ page: 1, size: 20, provinceCode: 'GZ', year: 2026, status: '' })
const form = ref<CreateAdminImportJobRequest>({
  provinceCode: 'GZ',
  year: 2026,
  batchCode: 'NORMAL_UNDERGRADUATE',
  subjectType: '物理类',
  importType: 'OFFICIAL_DATA_IMPORT',
  sourceType: 'OFFICIAL_SOURCE',
  sourceDir: '/opt/gzly/data-sources/guizhou/2026',
})

const selectedJob = computed(() => selected.value?.job || null)
const readinessPhase = computed(() => readiness.value?.recommendationPhase || '—')
const fullRecommendLocked = computed(() => readiness.value?.modelRetrained ? '需批次门禁复核' : 'FULL_RECOMMEND=0 / 未开放')

onMounted(async () => {
  await refreshAll()
})

async function refreshAll() {
  await Promise.all([loadJobs(), loadReadiness()])
}

async function loadJobs() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await fetchAdminImportJobs(filters.value)
    jobs.value = res.data.data.items || []
    total.value = res.data.data.total || 0
    if (!selected.value && jobs.value.length) {
      await loadDetail(jobs.value[0].id)
    }
  } catch (e: any) {
    errorMsg.value = e?.message || '加载导入任务失败'
  } finally {
    loading.value = false
  }
}

async function loadReadiness() {
  try {
    const res = await fetchAdminDataYearReadiness(filters.value.provinceCode, filters.value.year)
    readiness.value = res.data.data
  } catch (e: any) {
    errorMsg.value = e?.message || '加载 readiness 失败'
  }
}

async function createJob() {
  actionLoading.value = 'create'
  errorMsg.value = ''
  successMsg.value = ''
  try {
    const res = await createAdminImportJob(form.value)
    selected.value = res.data.data
    successMsg.value = '导入任务已创建，状态为 CREATED。'
    await loadJobs()
  } catch (e: any) {
    errorMsg.value = e?.message || '创建导入任务失败'
  } finally {
    actionLoading.value = ''
  }
}

async function loadDetail(jobId: number) {
  actionLoading.value = `detail-${jobId}`
  errorMsg.value = ''
  try {
    const res = await fetchAdminImportJobDetail(jobId)
    selected.value = res.data.data
  } catch (e: any) {
    errorMsg.value = e?.message || '加载任务详情失败'
  } finally {
    actionLoading.value = ''
  }
}

async function runAction(name: string, action: () => Promise<unknown>) {
  const job = selectedJob.value
  if (!job) return
  actionLoading.value = name
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await action()
    successMsg.value = '操作完成，仅生成 staging/质检/SQL 路径和 rollback 计划，未执行正式导入。'
    await loadDetail(job.id)
    await loadJobs()
  } catch (e: any) {
    errorMsg.value = e?.message || '操作失败'
  } finally {
    actionLoading.value = ''
  }
}

async function runSelectedAction(name: string, action: (jobId: number) => Promise<unknown>) {
  const job = selectedJob.value
  if (!job) return
  await runAction(name, () => action(job.id))
}

function statusClass(status?: string) {
  if (status === 'FAILED') return 'status status--fail'
  if (status === 'READY_FOR_MANUAL_CONFIRMATION') return 'status status--ready'
  if (status === 'QUALITY_PASSED' || status === 'PACKAGE_GENERATED') return 'status status--pass'
  return 'status status--neutral'
}

function gateClass(status?: string) {
  return status === 'PASS' ? 'gate-status gate-status--pass' : 'gate-status gate-status--fail'
}
</script>

<template>
  <div class="import-page">
    <div class="page-inner">
      <div class="page-head">
        <div>
          <h1 class="page-title">导入任务 MVP</h1>
          <p class="page-desc">用于官方数据发布后的任务登记、staging dry-run、quality gate、SQL 与 rollback 计划生成。</p>
        </div>
        <div class="head-actions">
          <span class="locked-badge"><Lock :size="13" /> 不提供 formal promote / readiness / FULL / 训练 / Redis 操作</span>
          <button class="refresh-btn" :disabled="loading" @click="refreshAll">
            <RefreshCcw :size="14" /> {{ loading ? '加载中…' : '刷新' }}
          </button>
        </div>
      </div>

      <div class="guard-grid">
        <div class="guard-card">
          <ShieldAlert :size="18" />
          <div><strong>{{ readinessPhase }}</strong><span>当前 readiness 阶段</span></div>
        </div>
        <div class="guard-card">
          <Lock :size="18" />
          <div><strong>{{ fullRecommendLocked }}</strong><span>正式推荐门禁</span></div>
        </div>
        <div class="guard-card">
          <Database :size="18" />
          <div><strong>正式表写入禁用</strong><span>仅元数据表与 output 产物</span></div>
        </div>
      </div>

      <div v-if="errorMsg" class="notice notice--error"><AlertTriangle :size="14" /> {{ errorMsg }}</div>
      <div v-if="successMsg" class="notice notice--success"><CheckCircle2 :size="14" /> {{ successMsg }}</div>

      <div class="content-grid">
        <section class="panel gz-card">
          <div class="panel-head">
            <h2><ClipboardList :size="16" /> 创建任务</h2>
            <span>只登记目录，不移动大文件</span>
          </div>
          <div class="form-grid">
            <label>省份 <input v-model="form.provinceCode" /></label>
            <label>年份 <input v-model.number="form.year" type="number" /></label>
            <label>批次 <input v-model="form.batchCode" /></label>
            <label>科类 <input v-model="form.subjectType" /></label>
            <label>导入类型 <input v-model="form.importType" /></label>
            <label>源类型 <input v-model="form.sourceType" /></label>
            <label class="form-full">源目录 <input v-model="form.sourceDir" /></label>
            <label class="form-full">输出目录（可空） <input v-model="form.outputDir" placeholder="默认写入 /opt/gzly/data-output/guizhou/import-jobs" /></label>
          </div>
          <button class="primary-btn" :disabled="actionLoading === 'create'" @click="createJob">
            {{ actionLoading === 'create' ? '创建中…' : '创建导入任务' }}
          </button>
        </section>

        <section class="panel gz-card">
          <div class="panel-head">
            <h2><FolderOpen :size="16" /> 任务列表</h2>
            <span>{{ total }} 个任务</span>
          </div>
          <div class="job-list">
            <button
              v-for="job in jobs"
              :key="job.id"
              class="job-row"
              :class="{ active: selectedJob?.id === job.id }"
              @click="loadDetail(job.id)"
            >
              <div><strong>#{{ job.id }} · {{ job.provinceCode }} {{ job.year }}</strong><span>{{ job.batchCode || '未指定批次' }} / {{ job.subjectType || '未指定科类' }}</span></div>
              <span :class="statusClass(job.status)">{{ job.status }}</span>
            </button>
            <div v-if="!jobs.length && !loading" class="empty-tip">暂无任务。</div>
          </div>
        </section>
      </div>

      <section v-if="selected" class="panel gz-card detail-panel">
        <div class="panel-head">
          <h2><PackageCheck :size="16" /> 任务详情 #{{ selected.job.id }}</h2>
          <span :class="statusClass(selected.job.status)">{{ selected.job.status }}</span>
        </div>

        <div class="meta-grid">
          <div><span>source_dir</span><strong>{{ selected.job.sourceDir }}</strong></div>
          <div><span>output_dir</span><strong>{{ selected.job.outputDir }}</strong></div>
          <div><span>import_type</span><strong>{{ selected.job.importType || '—' }}</strong></div>
          <div><span>source_type</span><strong>{{ selected.job.sourceType || '—' }}</strong></div>
        </div>

        <div class="safe-actions">
          <button @click="runSelectedAction('staging', generateAdminImportJobStaging)">生成 staging dry-run</button>
          <button @click="runSelectedAction('quality', runAdminImportJobQualityCheck)">执行 quality gate</button>
          <button @click="runSelectedAction('sql', generateAdminImportJobFormalSql)">生成 formal SQL 路径</button>
          <button @click="runSelectedAction('rollback', generateAdminImportJobRollbackPlan)">生成 rollback 计划</button>
        </div>

        <div class="danger-ban">
          <Lock :size="14" /> 页面不提供：正式导入、切换 readiness、开启 FULL_RECOMMEND、训练模型、清 Redis。
        </div>

        <div class="detail-grid">
          <div class="sub-panel">
            <h3><FileText :size="15" /> 源文件</h3>
            <table>
              <thead><tr><th>文件</th><th>类型</th><th>大小</th><th>SHA256</th></tr></thead>
              <tbody>
                <tr v-for="file in selected.files" :key="file.id">
                  <td><code>{{ file.filePath }}</code></td><td>{{ file.fileType }}</td><td>{{ file.fileSize }}</td><td><code>{{ file.sha256 }}</code></td>
                </tr>
                <tr v-if="!selected.files.length"><td colspan="4">尚未生成 staging 文件清单。</td></tr>
              </tbody>
            </table>
          </div>
          <div class="sub-panel">
            <h3><CheckCircle2 :size="15" /> Quality gate</h3>
            <table>
              <thead><tr><th>gate</th><th>状态</th><th>期望</th><th>实际</th></tr></thead>
              <tbody>
                <tr v-for="gate in selected.gates" :key="gate.id">
                  <td>{{ gate.gateName }}</td><td><span :class="gateClass(gate.gateStatus)">{{ gate.gateStatus }}</span></td><td>{{ gate.expectedValue }}</td><td>{{ gate.actualValue }}</td>
                </tr>
                <tr v-if="!selected.gates.length"><td colspan="4">尚未执行 quality gate。</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="sub-panel artifact-panel">
          <h3><PackageCheck :size="15" /> 产物路径</h3>
          <table>
            <thead><tr><th>类型</th><th>路径</th><th>SHA256</th></tr></thead>
            <tbody>
              <tr v-for="artifact in selected.artifacts" :key="artifact.id">
                <td>{{ artifact.artifactType }}</td><td><code>{{ artifact.artifactPath }}</code></td><td><code>{{ artifact.sha256 }}</code></td>
              </tr>
              <tr v-if="!selected.artifacts.length"><td colspan="3">尚未生成产物。</td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.import-page { min-height: 100%; }
.page-inner { padding: 24px; max-width: 1440px; margin: 0 auto; }
.page-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 18px; }
.page-title { margin: 0 0 6px; font-size: 26px; color: #111827; }
.page-desc { margin: 0; color: #6b7280; }
.head-actions { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; justify-content: flex-end; }
.locked-badge { display: inline-flex; align-items: center; gap: 6px; padding: 8px 10px; border-radius: 999px; background: #fff7ed; color: #9a3412; font-size: 12px; font-weight: 700; }
.refresh-btn, .primary-btn, .safe-actions button { border: none; border-radius: 10px; padding: 9px 12px; background: #111827; color: #fff; font-weight: 700; cursor: pointer; }
.refresh-btn { display: inline-flex; align-items: center; gap: 6px; background: #1d4ed8; }
.guard-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.guard-card { display: flex; gap: 10px; align-items: center; padding: 14px; background: #fffdfa; border: 1px solid rgba(15, 23, 42, .08); border-radius: 16px; }
.guard-card strong { display: block; color: #111827; }
.guard-card span { display: block; color: #6b7280; font-size: 12px; }
.notice { display: flex; align-items: center; gap: 6px; padding: 10px 12px; border-radius: 12px; margin-bottom: 12px; font-weight: 700; }
.notice--error { background: #fef2f2; color: #991b1b; }
.notice--success { background: #ecfdf5; color: #047857; }
.content-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.panel { padding: 18px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.panel-head h2 { display: flex; align-items: center; gap: 8px; margin: 0; font-size: 18px; }
.panel-head span { color: #6b7280; font-size: 12px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 14px; }
.form-grid label { display: flex; flex-direction: column; gap: 6px; color: #374151; font-size: 12px; font-weight: 700; }
.form-full { grid-column: 1 / -1; }
input { border: 1px solid #e5e7eb; border-radius: 10px; padding: 9px 10px; background: #fff; color: #111827; }
.job-list { display: flex; flex-direction: column; gap: 8px; }
.job-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; width: 100%; border: 1px solid #e5e7eb; border-radius: 12px; padding: 12px; background: #fff; text-align: left; cursor: pointer; }
.job-row.active { border-color: #2563eb; background: #eff6ff; }
.job-row strong, .job-row span { display: block; }
.job-row div span { color: #6b7280; font-size: 12px; margin-top: 4px; }
.status { display: inline-flex; padding: 4px 8px; border-radius: 999px; font-size: 11px; font-weight: 800; }
.status--neutral { background: #f3f4f6; color: #374151; }
.status--pass { background: #ecfdf5; color: #047857; }
.status--ready { background: #eff6ff; color: #1d4ed8; }
.status--fail { background: #fef2f2; color: #b91c1c; }
.detail-panel { margin-bottom: 24px; }
.meta-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; }
.meta-grid div { padding: 10px; border-radius: 12px; background: #f9fafb; border: 1px solid #eef2f7; }
.meta-grid span { display: block; color: #6b7280; font-size: 12px; margin-bottom: 4px; }
.meta-grid strong, code { word-break: break-all; }
.safe-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 12px; }
.safe-actions button { background: #1d4ed8; }
.danger-ban { display: flex; align-items: center; gap: 6px; padding: 10px 12px; border-radius: 12px; background: #fff7ed; color: #9a3412; font-weight: 700; margin-bottom: 14px; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.sub-panel { border: 1px solid #e5e7eb; border-radius: 14px; background: #fff; overflow: hidden; }
.sub-panel h3 { display: flex; align-items: center; gap: 6px; margin: 0; padding: 12px; border-bottom: 1px solid #eef2f7; font-size: 15px; }
table { width: 100%; border-collapse: collapse; font-size: 12px; }
th, td { padding: 10px; border-bottom: 1px solid #f1f5f9; text-align: left; vertical-align: top; }
th { color: #6b7280; font-weight: 800; background: #f9fafb; }
.gate-status { display: inline-flex; padding: 3px 7px; border-radius: 999px; font-weight: 800; }
.gate-status--pass { background: #ecfdf5; color: #047857; }
.gate-status--fail { background: #fef2f2; color: #b91c1c; }
.artifact-panel { margin-top: 16px; }
.empty-tip { padding: 16px; color: #6b7280; text-align: center; }
@media (max-width: 980px) {
  .page-head, .content-grid, .detail-grid, .guard-grid, .meta-grid { grid-template-columns: 1fr; display: grid; }
  .head-actions { justify-content: flex-start; }
  .form-grid { grid-template-columns: 1fr; }
}
</style>
