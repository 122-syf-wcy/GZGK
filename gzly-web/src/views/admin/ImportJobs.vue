<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  createAdminImportJob,
  fetchAdminImportJob,
  fetchAdminImportJobs,
  registerAdminImportJobFile,
  runAdminImportJobAction,
  type AdminImportJob,
} from '@/api/admin'

const jobs = ref<AdminImportJob[]>([])
const selectedJob = ref<AdminImportJob | null>(null)
const loading = ref(false)
const actionLoading = ref('')
const message = ref('')
const error = ref('')

const form = ref({
  provinceCode: 'HB',
  year: 2026,
  batchCode: '',
  subjectType: '',
  importType: 'bundle',
  sourceType: 'official_package',
  sourceDir: '',
  outputDir: '',
  createdBy: 'admin',
})

const fileForm = ref({
  filePath: '',
  fileName: '',
  fileType: 'official_source',
  sourceUrl: '',
})

const readinessPhase = computed(() => String(selectedJob.value?.readiness?.recommendationPhase || 'PRE_OFFICIAL_DATA'))
const sortedArtifacts = computed(() => selectedJob.value?.artifacts || [])
const sortedGates = computed(() => selectedJob.value?.gates || [])
const sortedFiles = computed(() => selectedJob.value?.files || [])

async function loadJobs() {
  loading.value = true
  error.value = ''
  try {
    const res = await fetchAdminImportJobs({ limit: 50 })
    jobs.value = res.data.data || []
    if (!selectedJob.value && jobs.value.length) {
      await loadJob(jobs.value[0].id)
    }
  } catch (e: any) {
    error.value = e.message || '导入任务读取失败'
  } finally {
    loading.value = false
  }
}

async function loadJob(jobId: number) {
  const res = await fetchAdminImportJob(jobId)
  selectedJob.value = res.data.data
}

async function createJob() {
  actionLoading.value = 'create'
  error.value = ''
  message.value = ''
  try {
    const res = await createAdminImportJob(form.value)
    selectedJob.value = res.data.data
    message.value = `已创建任务 #${selectedJob.value.id}`
    await loadJobs()
  } catch (e: any) {
    error.value = e.message || '创建失败'
  } finally {
    actionLoading.value = ''
  }
}

async function registerFile() {
  if (!selectedJob.value) return
  actionLoading.value = 'file'
  error.value = ''
  message.value = ''
  try {
    const res = await registerAdminImportJobFile(selectedJob.value.id, fileForm.value)
    selectedJob.value = res.data.data
    message.value = '源文件已登记并计算 SHA256'
  } catch (e: any) {
    error.value = e.message || '文件登记失败'
  } finally {
    actionLoading.value = ''
  }
}

async function runAction(action: 'staging-dry-run' | 'quality-gate' | 'generate-formal-sql' | 'rollback-plan' | 'post-check') {
  if (!selectedJob.value) return
  actionLoading.value = action
  error.value = ''
  message.value = ''
  try {
    const res = await runAdminImportJobAction(selectedJob.value.id, action)
    selectedJob.value = res.data.data
    message.value = `${action} 已完成，未执行 formal import`
    await loadJobs()
  } catch (e: any) {
    error.value = e.message || `${action} 失败`
  } finally {
    actionLoading.value = ''
  }
}

function shortPath(value: unknown) {
  const text = String(value || '')
  return text.length > 72 ? `...${text.slice(-69)}` : text
}

onMounted(loadJobs)
</script>

<template>
  <div class="admin-page import-jobs-page">
    <div class="page-header">
      <div>
        <h1>导入任务确认包</h1>
        <p>只做 staging dry-run、质量门禁和确认 SQL 产物；不执行正式导入，不切 readiness，不开启完整数据生成。</p>
      </div>
      <button class="admin-btn" :disabled="loading" @click="loadJobs">刷新</button>
    </div>

    <div v-if="message" class="admin-alert is-ok">{{ message }}</div>
    <div v-if="error" class="admin-alert is-error">{{ error }}</div>

    <section class="admin-panel guard-panel">
      <strong>硬门禁</strong>
      <span>formal promote：关闭</span>
      <span>FULL_RECOMMEND：关闭</span>
      <span>当前 readiness：{{ readinessPhase }}</span>
    </section>

    <div class="import-grid">
      <section class="admin-panel">
        <h2>创建任务</h2>
        <div class="form-grid">
          <label>省份<select v-model="form.provinceCode"><option>GZ</option><option>SC</option><option>AH</option><option>HB</option></select></label>
          <label>年份<input v-model.number="form.year" type="number" min="2024" max="2026" /></label>
          <label>批次<input v-model="form.batchCode" placeholder="如 HB_BENKE" /></label>
          <label>科类<input v-model="form.subjectType" placeholder="物理类/历史类" /></label>
          <label>类型<select v-model="form.importType"><option>bundle</option><option>score_rank</option><option>group_line</option><option>group_plan</option><option>major_requirement</option><option>major_meta</option></select></label>
          <label>来源类型<input v-model="form.sourceType" placeholder="official_package" /></label>
          <label class="is-wide">源目录<input v-model="form.sourceDir" placeholder="/root/..." /></label>
          <label class="is-wide">输出目录<input v-model="form.outputDir" placeholder="为空时使用 /opt/gzly/import-jobs/job-id" /></label>
        </div>
        <button class="admin-btn admin-btn--primary" :disabled="actionLoading === 'create'" @click="createJob">创建 import job</button>
      </section>

      <section class="admin-panel">
        <h2>任务列表</h2>
        <div v-if="loading" class="muted">读取中...</div>
        <button v-for="job in jobs" :key="job.id" class="job-row" :class="{ active: selectedJob?.id === job.id }" @click="loadJob(job.id)">
          <span>#{{ job.id }} {{ job.provinceCode }} {{ job.year }}</span>
          <b>{{ job.status }}</b>
        </button>
      </section>
    </div>

    <section v-if="selectedJob" class="admin-panel detail-panel">
      <div class="detail-head">
        <div>
          <h2>#{{ selectedJob.id }} {{ selectedJob.provinceCode }} {{ selectedJob.year }}</h2>
          <p>{{ selectedJob.guardrail }}</p>
        </div>
        <span class="status-pill">{{ selectedJob.status }}</span>
      </div>

      <div class="form-grid file-grid">
        <label class="is-wide">源文件路径<input v-model="fileForm.filePath" placeholder="/root/.../official.pdf" /></label>
        <label>文件名<input v-model="fileForm.fileName" placeholder="可空" /></label>
        <label>文件类型<input v-model="fileForm.fileType" /></label>
        <label class="is-wide">来源 URL<input v-model="fileForm.sourceUrl" placeholder="https://..." /></label>
      </div>
      <div class="action-row">
        <button class="admin-btn" :disabled="actionLoading === 'file'" @click="registerFile">登记源文件</button>
        <button class="admin-btn" :disabled="Boolean(actionLoading)" @click="runAction('staging-dry-run')">staging dry-run</button>
        <button class="admin-btn" :disabled="Boolean(actionLoading)" @click="runAction('quality-gate')">quality gate</button>
        <button class="admin-btn" :disabled="Boolean(actionLoading)" @click="runAction('generate-formal-sql')">生成 body-only SQL</button>
        <button class="admin-btn" :disabled="Boolean(actionLoading)" @click="runAction('rollback-plan')">rollback plan</button>
        <button class="admin-btn" :disabled="Boolean(actionLoading)" @click="runAction('post-check')">post-check</button>
      </div>

      <div class="detail-columns">
        <div>
          <h3>源文件</h3>
          <p v-if="!sortedFiles.length" class="muted">暂无源文件</p>
          <div v-for="file in sortedFiles" :key="String(file.id)" class="mini-row">
            <span>{{ file.file_name || file.fileName }}</span>
            <small>{{ shortPath(file.file_path || file.filePath) }}</small>
          </div>
        </div>
        <div>
          <h3>门禁</h3>
          <p v-if="!sortedGates.length" class="muted">暂无门禁</p>
          <div v-for="gate in sortedGates" :key="String(gate.id)" class="mini-row">
            <span>{{ gate.gate_name || gate.gateName }}</span>
            <b :class="String(gate.gate_status || gate.gateStatus).toLowerCase()">{{ gate.gate_status || gate.gateStatus }}</b>
          </div>
        </div>
        <div>
          <h3>产物</h3>
          <p v-if="!sortedArtifacts.length" class="muted">暂无产物</p>
          <div v-for="artifact in sortedArtifacts" :key="String(artifact.id)" class="mini-row">
            <span>{{ artifact.artifact_type || artifact.artifactType }}</span>
            <small>{{ shortPath(artifact.artifact_path || artifact.artifactPath) }}</small>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.import-jobs-page { display: flex; flex-direction: column; gap: 16px; }
.page-header, .detail-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-header h1, .detail-head h2 { margin: 0 0 6px; font-size: 22px; }
.page-header p, .detail-head p { margin: 0; color: #64748b; line-height: 1.5; }
.admin-panel { padding: 18px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
.guard-panel { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.guard-panel span, .status-pill { padding: 5px 8px; border-radius: 6px; background: #eff6ff; color: #1d4ed8; font-size: 12px; font-weight: 700; }
.import-grid { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.8fr); gap: 16px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; }
.form-grid label { display: flex; flex-direction: column; gap: 5px; color: #475569; font-size: 12px; }
.form-grid input, .form-grid select { min-height: 36px; padding: 7px 9px; border: 1px solid #dbe3ef; border-radius: 6px; }
.form-grid .is-wide { grid-column: 1 / -1; }
.admin-btn { min-height: 36px; padding: 0 12px; border: 1px solid #cbd5e1; border-radius: 6px; background: #fff; color: #0f172a; cursor: pointer; }
.admin-btn--primary { background: #0f172a; color: #fff; border-color: #0f172a; }
.admin-btn:disabled { opacity: .55; cursor: not-allowed; }
.job-row { display: flex; width: 100%; justify-content: space-between; gap: 10px; padding: 10px; margin-bottom: 8px; border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; text-align: left; cursor: pointer; }
.job-row.active { border-color: #2563eb; background: #eff6ff; }
.job-row b { color: #0f766e; font-size: 12px; }
.action-row { display: flex; flex-wrap: wrap; gap: 8px; margin: 8px 0 16px; }
.detail-columns { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.mini-row { display: grid; gap: 4px; padding: 8px; border: 1px solid #eef2f7; border-radius: 6px; margin-bottom: 8px; }
.mini-row span { font-weight: 700; color: #1e293b; }
.mini-row small { color: #64748b; overflow-wrap: anywhere; }
.mini-row b.pass { color: #047857; }
.mini-row b.fail { color: #b91c1c; }
.muted { color: #64748b; }
.admin-alert { padding: 10px 12px; border-radius: 6px; font-size: 13px; }
.admin-alert.is-ok { background: #ecfdf5; color: #047857; }
.admin-alert.is-error { background: #fef2f2; color: #b91c1c; }
@media (max-width: 900px) { .import-grid, .detail-columns, .form-grid { grid-template-columns: 1fr; } }
</style>
