<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import {
  getPendingApplications,
  reviewApplication,
  getPendingMedia,
  reviewMedia,
  reviewMediaBatch,
  getPendingEdits,
  reviewContentEdit,
  reviewContentEditBatch,
} from '@/api/alumni'
import request from '@/api/request'
import { showSuccessToast, showToast } from 'vant'
import { sanitizeHttpUrl } from '@/utils/markdown'
import {
  UserCheck,
  UserX,
  Image,
  Check,
  X,
  Clock,
  Users,
  FileImage,
  FileText,
  AlertCircle,
  Filter,
  ChevronDown,
  Mail,
  Phone,
  GraduationCap,
  Calendar,
  MessageSquare,
} from 'lucide-vue-next'

const tab = ref<'apps' | 'media' | 'edits'>('apps')
const apps = ref<any[]>([])
const medias = ref<any[]>([])
const edits = ref<any[]>([])
const loading = ref(false)
const statusFilter = ref(0)
const mediaStatusFilter = ref<number>(3)
const editStatusFilter = ref<number>(3)
const selectedMediaIds = ref<number[]>([])
const selectedEditIds = ref<number[]>([])
const rejectDialogVisible = ref(false)
const rejectReason = ref('')
const rejectTarget = ref<'media' | 'edits'>('media')
const rejectTargetIds = ref<number[]>([])
const batchSubmitting = ref(false)

onMounted(() => {
  loadAll()
})

async function loadAll() {
  loading.value = true
  await Promise.all([loadApps(), loadMedia(), loadEdits()])
  loading.value = false
}

async function loadApps() {
  try {
    const res = await getPendingApplications(statusFilter.value)
    if (res.data?.data) apps.value = res.data.data
  } catch { /* */ }
}

async function loadMedia() {
  try {
    const res = await getPendingMedia(1, 50, mediaStatusFilter.value === 0 ? undefined : mediaStatusFilter.value)
    if (res.data?.data) medias.value = res.data.data
    selectedMediaIds.value = []
  } catch { /* */ }
}

async function loadEdits() {
  try {
    const res = await getPendingEdits(editStatusFilter.value, 1, 50)
    if (res.data?.data) edits.value = res.data.data
    selectedEditIds.value = []
  } catch { /* */ }
}

async function handleApp(id: number, approved: boolean) {
  try {
    await reviewApplication(id, approved)
    showSuccessToast(approved ? '已通过' : '已拒绝')
    apps.value = apps.value.filter(a => a.id !== id)
  } catch { showToast('操作失败') }
}

function openMediaUrl(m: any) {
  const url = safeMediaUrl(m.url)
  if (!url) {
    showToast('链接格式异常，已阻止打开')
    return
  }
  if (m.mediaType === 1 || m.mediaType === 4) {
    window.open(url, '_blank', 'noopener,noreferrer')
  } else if (m.mediaType === 3 && m.url) {
    window.open(url, '_blank', 'noopener,noreferrer')
  }
}

function safeUploadUrl(url?: string | null) {
  const value = String(url || '').trim()
  if (!value || /[\u0000-\u001F\u007F]/.test(value)) return ''
  return value.startsWith('/uploads/') && !value.includes('..') ? value : ''
}

function safeMediaUrl(url?: string | null) {
  return safeUploadUrl(url) || sanitizeHttpUrl(url)
}

async function handleMedia(id: number, approved: boolean, reason = '') {
  try {
    await reviewMedia(id, approved, reason)
    showSuccessToast(approved ? '已发布' : '已退回')
    medias.value = medias.value.filter(m => m.id !== id)
  } catch { showToast('操作失败') }
}

async function handleEdit(id: number, approved: boolean, reason = '') {
  try {
    await reviewContentEdit(id, approved, reason)
    showSuccessToast(approved ? '已通过' : '已退回')
    edits.value = edits.value.filter(e => e.id !== id)
  } catch { showToast('操作失败') }
}

function parseAiLog(raw: string | null | undefined): { label: string; reason: string; cls: string } {
  if (!raw) return { label: '待审核', reason: 'AI 一审尚未完成', cls: 'ai-none' }
  try {
    const obj = JSON.parse(raw)
    if (obj.pass === true) return { label: '通过', reason: obj.reason || '未说明', cls: 'ai-pass' }
    if (obj.pass === false) return { label: '风险', reason: obj.reason || '未说明', cls: 'ai-reject' }
    if (obj.mode === 'error' || obj.pass === null || obj.error) {
      return { label: '异常', reason: obj.reason || obj.error || '审核流程异常', cls: 'ai-error' }
    }
    return { label: '原始', reason: raw, cls: 'ai-none' }
  } catch {
    return { label: '原始', reason: raw, cls: 'ai-none' }
  }
}

const schoolNames = ref<Record<string, string>>({})

async function resolveSchoolName(schoolId: string) {
  if (!schoolId || schoolNames.value[schoolId]) return
  try {
    const res = await request.get('/university/by-school-id', { params: { schoolId } })
    schoolNames.value[schoolId] = res.data?.data?.name || schoolId
  } catch {
    schoolNames.value[schoolId] = schoolId
  }
}

function getSchoolName(schoolId: string): string {
  if (!schoolId) return '-'
  if (!schoolNames.value[schoolId]) {
    resolveSchoolName(schoolId)
    return schoolId
  }
  return schoolNames.value[schoolId]
}

const stats = computed(() => ({
  pendingApps: apps.value.length,
  pendingMedia: medias.value.length,
  pendingEdits: edits.value.length,
  total: apps.value.length + medias.value.length + edits.value.length,
}))

const mediaSummary = computed(() => ({
  pending: medias.value.filter((item: any) => item.status === 3).length,
  approved: medias.value.filter((item: any) => item.status === 1).length,
  rejected: medias.value.filter((item: any) => item.status === 2).length,
}))

const editSummary = computed(() => ({
  pending: edits.value.filter((item: any) => item.status === 3).length,
  approved: edits.value.filter((item: any) => item.status === 1).length,
  rejected: edits.value.filter((item: any) => item.status === 2).length,
}))

const selectableMedia = computed(() => medias.value.filter((item: any) => item.status === 3))
const selectableEdits = computed(() => edits.value.filter((item: any) => item.status === 3))
const allMediaSelected = computed(() => selectableMedia.value.length > 0 && selectableMedia.value.every((item: any) => selectedMediaIds.value.includes(item.id)))
const allEditsSelected = computed(() => selectableEdits.value.length > 0 && selectableEdits.value.every((item: any) => selectedEditIds.value.includes(item.id)))

function toggleSelection(target: 'media' | 'edits', id: number) {
  const source = target === 'media' ? selectedMediaIds : selectedEditIds
  if (source.value.includes(id)) {
    source.value = source.value.filter(item => item !== id)
    return
  }
  source.value = [...source.value, id]
}

function toggleSelectAll(target: 'media' | 'edits') {
  if (target === 'media') {
    selectedMediaIds.value = allMediaSelected.value ? [] : selectableMedia.value.map((item: any) => item.id)
    return
  }
  selectedEditIds.value = allEditsSelected.value ? [] : selectableEdits.value.map((item: any) => item.id)
}

async function doBatchApprove(target: 'media' | 'edits') {
  const ids = target === 'media' ? selectedMediaIds.value : selectedEditIds.value
  if (!ids.length) {
    showToast('请先勾选待审核内容')
    return
  }
  batchSubmitting.value = true
  try {
    if (target === 'media') {
      const res = await reviewMediaBatch(ids, true)
      showSuccessToast(`已发布 ${res.data?.data?.processedCount || ids.length} 条`)
      await loadMedia()
    } else {
      const res = await reviewContentEditBatch(ids, true)
      showSuccessToast(`已采纳 ${res.data?.data?.processedCount || ids.length} 条`)
      await loadEdits()
    }
  } catch (error: any) {
    showToast(error.message || '批量操作失败')
  } finally {
    batchSubmitting.value = false
  }
}

function openRejectDialog(target: 'media' | 'edits', ids: number[]) {
  if (!ids.length) {
    showToast('请先勾选待审核内容')
    return
  }
  rejectTarget.value = target
  rejectTargetIds.value = ids
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

async function confirmReject() {
  if (!rejectReason.value.trim()) {
    showToast('请输入统一退回原因')
    return
  }
  batchSubmitting.value = true
  try {
    if (rejectTarget.value === 'media') {
      if (rejectTargetIds.value.length === 1) {
        await handleMedia(rejectTargetIds.value[0], false, rejectReason.value.trim())
      } else {
        const res = await reviewMediaBatch(rejectTargetIds.value, false, rejectReason.value.trim())
        showSuccessToast(`已退回 ${res.data?.data?.processedCount || rejectTargetIds.value.length} 条`)
        await loadMedia()
      }
    } else {
      if (rejectTargetIds.value.length === 1) {
        await handleEdit(rejectTargetIds.value[0], false, rejectReason.value.trim())
      } else {
        const res = await reviewContentEditBatch(rejectTargetIds.value, false, rejectReason.value.trim())
        showSuccessToast(`已退回 ${res.data?.data?.processedCount || rejectTargetIds.value.length} 条`)
        await loadEdits()
      }
    }
    rejectDialogVisible.value = false
  } catch (error: any) {
    showToast(error.message || '退回失败')
  } finally {
    batchSubmitting.value = false
  }
}
</script>

<template>
  <div class="review-page">
    <div class="review-inner">
      <!-- Page Header -->
      <div class="review-header">
        <div>
          <h1 class="review-title">校友审核</h1>
          <p class="review-desc">管理校友申请、媒体内容和信息编辑</p>
        </div>
        <button class="refresh-btn" @click="loadAll" :disabled="loading">
          <svg :class="{ spinning: loading }" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-6.22-8.56"/><path d="M21 3v9h-9"/></svg>
          刷新
        </button>
      </div>

      <!-- Stats Cards -->
      <div class="stats-row">
        <div class="mini-stat" :class="{ highlight: stats.pendingApps > 0 }">
          <div class="mini-stat-icon mini-stat-icon--blue"><Users :size="18" /></div>
          <div class="mini-stat-body">
            <span class="mini-stat-val">{{ stats.pendingApps }}</span>
            <span class="mini-stat-label">待审申请</span>
          </div>
        </div>
        <div class="mini-stat" :class="{ highlight: stats.pendingMedia > 0 }">
          <div class="mini-stat-icon mini-stat-icon--purple"><FileImage :size="18" /></div>
          <div class="mini-stat-body">
            <span class="mini-stat-val">{{ stats.pendingMedia }}</span>
            <span class="mini-stat-label">待审媒体</span>
          </div>
        </div>
        <div class="mini-stat" :class="{ highlight: stats.pendingEdits > 0 }">
          <div class="mini-stat-icon mini-stat-icon--green"><FileText :size="18" /></div>
          <div class="mini-stat-body">
            <span class="mini-stat-val">{{ stats.pendingEdits }}</span>
            <span class="mini-stat-label">待审编辑</span>
          </div>
        </div>
        <div class="mini-stat">
          <div class="mini-stat-icon mini-stat-icon--amber"><AlertCircle :size="18" /></div>
          <div class="mini-stat-body">
            <span class="mini-stat-val">{{ stats.total }}</span>
            <span class="mini-stat-label">总待处理</span>
          </div>
        </div>
      </div>

      <!-- Tab Bar -->
      <div class="review-tabs-wrap">
        <div class="review-tabs">
          <button
            v-for="t in [
              { key: 'apps', label: '校友申请', icon: Users, count: stats.pendingApps },
              { key: 'media', label: '媒体审核', icon: FileImage, count: stats.pendingMedia },
              { key: 'edits', label: '内容编辑', icon: FileText, count: stats.pendingEdits },
            ]"
            :key="t.key"
            class="review-tab"
            :class="{ active: tab === t.key }"
            @click="tab = t.key as any"
          >
            <component :is="t.icon" :size="16" />
            <span>{{ t.label }}</span>
            <span v-if="t.count > 0" class="tab-badge">{{ t.count }}</span>
          </button>
        </div>
      </div>

      <!-- Apps Tab -->
      <div v-if="tab === 'apps'" class="tab-panel">
        <div v-if="apps.length === 0" class="empty-panel">
          <UserCheck :size="40" />
          <p>暂无待审核的校友申请</p>
        </div>

        <div class="app-grid">
          <div v-for="a in apps" :key="a.id" class="app-card">
            <div class="app-header">
              <div class="app-avatar">{{ a.nickname?.charAt(0) || '?' }}</div>
              <div class="app-identity">
                <div class="app-name">{{ a.nickname }}</div>
                <div class="app-meta-row">
                  <span class="app-meta-item"><GraduationCap :size="12" /> {{ getSchoolName(a.schoolId) }}</span>
                  <span v-if="a.graduationYear" class="app-meta-item"><Calendar :size="12" /> {{ a.graduationYear }}届</span>
                </div>
              </div>
            </div>

            <div class="app-details">
              <div v-if="a.major" class="detail-row">
                <span class="detail-label">专业</span>
                <span class="detail-val">{{ a.major }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label"><Phone :size="12" /> 手机</span>
                <span class="detail-val">{{ a.phone }}</span>
              </div>
              <div v-if="a.email" class="detail-row">
                <span class="detail-label"><Mail :size="12" /> 邮箱</span>
                <span class="detail-val">{{ a.email }}</span>
              </div>
            </div>

            <p v-if="a.bio" class="app-bio">
              <MessageSquare :size="12" />
              {{ a.bio }}
            </p>

            <div class="app-actions">
              <button class="review-btn review-btn--reject" @click="handleApp(a.id, false)">
                <UserX :size="15" />
                拒绝
              </button>
              <button class="review-btn review-btn--approve" @click="handleApp(a.id, true)">
                <UserCheck :size="15" />
                通过
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Media Tab -->
      <div v-if="tab === 'media'" class="tab-panel">
        <div class="sub-filter-bar sub-filter-bar--panel">
          <div class="sub-stats">
            <span class="sub-stat-chip">待人工审核 {{ mediaSummary.pending }}</span>
            <span class="sub-stat-chip">已通过 {{ mediaSummary.approved }}</span>
            <span class="sub-stat-chip">已退回 {{ mediaSummary.rejected }}</span>
          </div>
          <div class="status-tabs status-tabs--segment">
            <button :class="{ active: mediaStatusFilter === 0 }" @click="mediaStatusFilter = 0; loadMedia()">全部</button>
            <button :class="{ active: mediaStatusFilter === 3 }" @click="mediaStatusFilter = 3; loadMedia()">待人工审核</button>
            <button :class="{ active: mediaStatusFilter === 2 }" @click="mediaStatusFilter = 2; loadMedia()">已退回</button>
            <button :class="{ active: mediaStatusFilter === 1 }" @click="mediaStatusFilter = 1; loadMedia()">已通过</button>
          </div>
        </div>

        <div v-if="selectableMedia.length" class="batch-toolbar">
          <div class="batch-toolbar-left">
            <label class="batch-checkbox">
              <input type="checkbox" :checked="allMediaSelected" @change="toggleSelectAll('media')" />
              <span>当前页全选</span>
            </label>
            <button class="batch-link" @click="selectedMediaIds = []">取消全选</button>
            <span class="batch-count">已选 {{ selectedMediaIds.length }} 条</span>
          </div>
          <div class="batch-toolbar-actions">
            <button class="review-btn review-btn--approve" :disabled="!selectedMediaIds.length || batchSubmitting" @click="doBatchApprove('media')">
              <Check :size="15" /> 批量一键同意发布
            </button>
            <button class="review-btn review-btn--reject" :disabled="!selectedMediaIds.length || batchSubmitting" @click="openRejectDialog('media', selectedMediaIds)">
              <X :size="15" /> 批量退回
            </button>
          </div>
        </div>

        <div v-if="medias.length === 0" class="empty-panel">
          <Image :size="40" />
          <p>暂无待审核的媒体内容</p>
        </div>

        <div class="media-grid">
          <div v-for="m in medias" :key="m.id" class="media-card">
            <div class="media-preview" :style="{ cursor: (m.mediaType === 3 || m.mediaType === 1 || m.mediaType === 4) ? 'pointer' : 'default' }" @click="openMediaUrl(m)">
              <img v-if="m.mediaType === 1 || m.mediaType === 4" :src="m.url" loading="lazy" />
              <div v-else style="display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: #97999e; gap: 4px;">
                <FileText :size="32" />
                <span style="font-size: 11px;">{{ m.mediaType === 2 ? '资讯' : '文件（点击查看）' }}</span>
              </div>
            </div>
            <div class="media-info">
              <div class="card-check-row" v-if="m.status === 3">
                <label class="batch-checkbox">
                  <input type="checkbox" :checked="selectedMediaIds.includes(m.id)" @change="toggleSelection('media', m.id)" />
                  <span>加入批量审核</span>
                </label>
              </div>
              <div class="media-meta">
                <span class="media-school"><GraduationCap :size="12" /> {{ getSchoolName(m.schoolId) }}</span>
                <span class="media-caption">{{ m.caption || '无说明' }}</span>
              </div>
              <div class="ai-log" :class="parseAiLog(m.aiReviewResult).cls">
                <AlertCircle :size="13" />
                <span class="ai-log-label">AI{{ parseAiLog(m.aiReviewResult).label }}</span>
                <span class="ai-log-reason">{{ parseAiLog(m.aiReviewResult).reason }}</span>
              </div>
              <div v-if="m.status === 2 && m.reviewNote" class="review-note">
                <strong>人工退回原因：</strong>{{ m.reviewNote }}
              </div>
              <div class="media-actions">
                <button v-if="m.status === 3" class="review-btn review-btn--reject" @click="openRejectDialog('media', [m.id])">
                  <X :size="16" />
                  退回
                </button>
                <button v-if="m.status === 3" class="review-btn review-btn--approve" @click="handleMedia(m.id, true)">
                  <Check :size="16" />
                  一键同意发布
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Edits Tab -->
      <div v-if="tab === 'edits'" class="tab-panel">
        <div class="sub-filter-bar sub-filter-bar--panel">
          <div class="sub-stats">
            <span class="sub-stat-chip">待人工审核 {{ editSummary.pending }}</span>
            <span class="sub-stat-chip">已采纳 {{ editSummary.approved }}</span>
            <span class="sub-stat-chip">已退回 {{ editSummary.rejected }}</span>
          </div>
          <div class="status-tabs status-tabs--segment">
            <button :class="{ active: editStatusFilter === 0 }" @click="editStatusFilter = 0; loadEdits()">全部</button>
            <button :class="{ active: editStatusFilter === 3 }" @click="editStatusFilter = 3; loadEdits()">待人工审核</button>
            <button :class="{ active: editStatusFilter === 2 }" @click="editStatusFilter = 2; loadEdits()">已退回</button>
            <button :class="{ active: editStatusFilter === 1 }" @click="editStatusFilter = 1; loadEdits()">已采纳</button>
          </div>
        </div>

        <div v-if="selectableEdits.length" class="batch-toolbar">
          <div class="batch-toolbar-left">
            <label class="batch-checkbox">
              <input type="checkbox" :checked="allEditsSelected" @change="toggleSelectAll('edits')" />
              <span>当前页全选</span>
            </label>
            <button class="batch-link" @click="selectedEditIds = []">取消全选</button>
            <span class="batch-count">已选 {{ selectedEditIds.length }} 条</span>
          </div>
          <div class="batch-toolbar-actions">
            <button class="review-btn review-btn--approve" :disabled="!selectedEditIds.length || batchSubmitting" @click="doBatchApprove('edits')">
              <Check :size="15" /> 批量一键同意采纳
            </button>
            <button class="review-btn review-btn--reject" :disabled="!selectedEditIds.length || batchSubmitting" @click="openRejectDialog('edits', selectedEditIds)">
              <X :size="15" /> 批量退回
            </button>
          </div>
        </div>

        <div v-if="edits.length === 0" class="empty-panel">
          <FileText :size="40" />
          <p>暂无待审核的内容编辑</p>
        </div>

        <div class="edit-list">
          <div v-for="e in edits" :key="e.id" class="edit-card">
            <div class="edit-header">
              <label v-if="e.status === 3" class="batch-checkbox">
                <input type="checkbox" :checked="selectedEditIds.includes(e.id)" @change="toggleSelection('edits', e.id)" />
                <span>加入批量审核</span>
              </label>
              <span class="edit-field">{{ e.fieldName }}</span>
              <span class="edit-school"><GraduationCap :size="12" /> {{ getSchoolName(e.schoolId) }}</span>
            </div>
            <div class="edit-diff">
              <div v-if="e.oldValue" class="diff-row diff-row--old">
                <span class="diff-label">原内容</span>
                <span class="diff-val">{{ e.oldValue }}</span>
              </div>
              <div class="diff-row diff-row--new">
                <span class="diff-label">新内容</span>
                <span class="diff-val">{{ e.newValue }}</span>
              </div>
            </div>
            <div class="ai-log" :class="parseAiLog(e.aiReviewResult).cls">
              <AlertCircle :size="13" />
              <span class="ai-log-label">AI{{ parseAiLog(e.aiReviewResult).label }}</span>
              <span class="ai-log-reason">{{ parseAiLog(e.aiReviewResult).reason }}</span>
            </div>
            <div v-if="e.status === 2 && e.reviewNote" class="review-note">
              <strong>人工退回原因：</strong>{{ e.reviewNote }}
            </div>
            <div class="edit-actions">
              <button v-if="e.status === 3" class="review-btn review-btn--reject" @click="openRejectDialog('edits', [e.id])">
                <X :size="14" /> 退回
              </button>
              <button v-if="e.status === 3" class="review-btn review-btn--approve" @click="handleEdit(e.id, true)">
                <Check :size="14" /> 一键同意采纳
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <van-overlay :show="rejectDialogVisible" @click="rejectDialogVisible = false">
      <div class="dialog-wrap" @click.stop>
        <div class="dialog-card">
          <div class="dialog-header">
            <h3>统一退回原因</h3>
            <button class="dialog-close" @click="rejectDialogVisible = false"><X :size="18" /></button>
          </div>
          <div class="dialog-body">
            <p class="dialog-desc">你正在退回 {{ rejectTargetIds.length }} 条{{ rejectTarget === 'media' ? '媒体内容' : '编辑记录' }}，请输入统一退回原因。</p>
            <textarea v-model="rejectReason" class="dialog-textarea" rows="4" placeholder="例如：内容信息不足，请补充真实来源后重新提交"></textarea>
          </div>
          <div class="dialog-footer">
            <button class="dialog-btn dialog-btn--ghost" @click="rejectDialogVisible = false">取消</button>
            <button class="dialog-btn dialog-btn--danger" :disabled="batchSubmitting" @click="confirmReject">
              {{ batchSubmitting ? '提交中...' : '确认退回' }}
            </button>
          </div>
        </div>
      </div>
    </van-overlay>
  </div>
</template>

<style scoped>
.review-page {
  min-height: 100%;
}

.review-inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 16px;
}

/* ---- Header ---- */
.review-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
}

.review-title {
  font-size: 24px;
  font-weight: 800;
  color: var(--gz-text-primary, #17181c);
  margin-bottom: 4px;
}

.review-desc {
  font-size: 14px;
  color: var(--gz-text-tertiary, #97999e);
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1.5px solid rgba(0, 0, 0, 0.08);
  border-radius: 10px;
  background: #fff;
  color: var(--gz-text-secondary, #6a6c72);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.refresh-btn:hover {
  border-color: var(--gz-primary, #17181c);
  color: var(--gz-primary, #17181c);
}

.refresh-btn:disabled {
  opacity: 0.5;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.spinning {
  animation: spin 1s linear infinite;
}

/* ---- Stats ---- */
.stats-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.mini-stat {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  border: 1.5px solid transparent;
  transition: border-color 0.2s;
}

.mini-stat.highlight {
  border-color: rgba(23, 24, 28, 0.15);
}

.mini-stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.mini-stat-icon--blue { background: linear-gradient(135deg, #17181c, #2b2d33); }
.mini-stat-icon--purple { background: linear-gradient(135deg, #7c3aed, #a855f7); }
.mini-stat-icon--green { background: linear-gradient(135deg, #2f7d5d, #2f7d5d); }
.mini-stat-icon--amber { background: linear-gradient(135deg, #a5793a, #b98a2f); }

.mini-stat-body {
  display: flex;
  flex-direction: column;
}

.mini-stat-val {
  font-size: 24px;
  font-weight: 800;
  color: #17181c;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.mini-stat-label {
  font-size: 12px;
  color: #97999e;
  font-weight: 500;
}

/* ---- Tabs ---- */
.review-tabs-wrap {
  margin-bottom: 20px;
  width: min(100%, 760px);
}

.review-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;
  width: 100%;
  padding: 4px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(23, 24, 28, 0.06);
  overflow: hidden;
}

.review-tab {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 0;
  width: 100%;
  padding: 12px 16px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: #6a6c72;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.review-tab :deep(svg),
.review-tab svg {
  flex-shrink: 0;
}

.review-tab:hover {
  background: #f2f2ef;
  color: #383a40;
}

.review-tab.active {
  background: linear-gradient(135deg, #17181c, #2b2d33);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(23, 24, 28, 0.25);
}

.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: rgba(192, 72, 72, 0.1);
  color: #c04848;
  font-size: 11px;
  font-weight: 700;
}

.review-tab.active .tab-badge {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
}

.status-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.status-tabs button {
  padding: 7px 14px;
  border: 1px solid #dbe2ea;
  border-radius: 999px;
  background: #fff;
  font-size: 12px;
  font-weight: 600;
  color: #6a6c72;
  cursor: pointer;
  transition: all 0.18s;
}

.status-tabs button.active {
  background: linear-gradient(135deg, #17181c, #2b2d33);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 6px 16px rgba(23, 24, 28, 0.22);
}

.status-tabs button:hover:not(.active) {
  background: #fafaf8;
  color: #383a40;
}

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

.sub-filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.sub-filter-bar--panel {
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e5e7eb;
  border-radius: 14px;
}

.sub-stats {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #6a6c72;
}

.sub-stat-chip {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: #fafaf8;
  border: 1px solid #e3e2de;
  font-weight: 600;
}

.batch-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  margin-bottom: 16px;
}

.batch-toolbar-left,
.batch-toolbar-actions,
.batch-checkbox,
.card-check-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.batch-link {
  border: none;
  background: transparent;
  color: #17181c;
  cursor: pointer;
  font-size: 12px;
}

.batch-count {
  font-size: 12px;
  color: #6a6c72;
}

/* ---- Panel ---- */
.tab-panel {
  min-height: 200px;
}

.empty-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 80px 20px;
  color: #97999e;
}

.empty-panel p {
  font-size: 15px;
  font-weight: 500;
  color: #6a6c72;
}

/* ---- App Cards ---- */
.app-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}

.app-card {
  padding: 24px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s;
}

.app-card:hover {
  transform: translateY(-1px);
}

.app-header {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 16px;
}

.app-avatar {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, #17181c, #7c3aed);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
  flex-shrink: 0;
}

.app-identity {
  flex: 1;
  min-width: 0;
}

.app-name {
  font-size: 17px;
  font-weight: 700;
  color: #17181c;
}

.app-meta-row {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}

.app-meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #6a6c72;
}

.app-details {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: #fafaf8;
  border-radius: 8px;
  font-size: 13px;
}

.detail-label {
  display: flex;
  align-items: center;
  gap: 3px;
  color: #97999e;
  font-weight: 500;
}

.detail-val {
  color: #383a40;
  font-weight: 500;
}

.app-bio {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 13px;
  color: #6a6c72;
  line-height: 1.6;
  padding: 12px 14px;
  background: #fafaf8;
  border-radius: 10px;
  margin-bottom: 16px;
  border-left: 3px solid #17181c;
}

.app-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

/* ---- Review Buttons ---- */
.review-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 9px 18px;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.review-btn--approve {
  background: linear-gradient(135deg, #2f7d5d, #2f7d5d);
  color: #fff;
  box-shadow: 0 2px 8px rgba(47, 125, 93, 0.25);
}

.review-btn--approve:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(47, 125, 93, 0.35);
}

.review-btn--reject {
  background: #f7efef;
  color: #c04848;
  border: 1px solid #e3cbcb;
}

.review-btn--reject:hover {
  background: #fee2e2;
}

/* ---- Media Grid ---- */
.media-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.media-card {
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s;
}

.media-card:hover {
  transform: translateY(-2px);
}

.media-preview {
  aspect-ratio: 4/3;
  overflow: hidden;
}

.media-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}

.media-card:hover .media-preview img {
  transform: scale(1.05);
}

.media-info {
  padding: 12px 14px;
}

.media-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
}

.media-school {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  color: #17181c;
}

.media-caption {
  font-size: 12px;
  color: #6a6c72;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 12px;
  flex-wrap: wrap;
}

.ai-log {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fafaf8;
  color: #6a6c72;
  border: 1px solid #e3e2de;
  font-size: 12px;
}

.ai-log-label {
  font-weight: 700;
  flex-shrink: 0;
}

.ai-log-reason {
  line-height: 1.5;
  word-break: break-word;
}

.ai-pass {
  background: #f0fdf4;
  border-color: #bbf7d0;
  color: #166534;
}

.ai-reject {
  background: #f7efef;
  border-color: #e3cbcb;
  color: #8f3030;
}

.ai-error {
  background: #faf7ef;
  border-color: #e6dcbd;
  color: #7c5f33;
}

.review-note {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff7ed;
  border: 1px solid #fdba74;
  color: #9a3412;
  font-size: 12px;
  line-height: 1.6;
}

.icon-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.icon-btn--approve {
  background: #eef2ee;
  color: #2f7d5d;
}

.icon-btn--approve:hover {
  background: #2f7d5d;
  color: #fff;
}

.icon-btn--reject {
  background: #f7efef;
  color: #c04848;
}

.icon-btn--reject:hover {
  background: #c04848;
  color: #fff;
}

/* ---- Edit List ---- */
.edit-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.edit-card {
  padding: 20px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.edit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.edit-field {
  font-size: 15px;
  font-weight: 700;
  color: #17181c;
}

.edit-school {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #17181c;
  font-weight: 500;
}

.edit-diff {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.diff-row {
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
}

.diff-row--old {
  background: #f7efef;
  border-left: 3px solid #c04848;
}

.diff-row--new {
  background: #eef2ee;
  border-left: 3px solid #2f7d5d;
}

.diff-label {
  font-size: 11px;
  font-weight: 600;
  color: #97999e;
  margin-bottom: 4px;
  display: block;
}

.diff-val {
  color: #383a40;
  line-height: 1.5;
}

.edit-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.dialog-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 20px;
}

.dialog-card {
  width: 100%;
  max-width: 520px;
  background: #fff;
  border-radius: 18px;
  overflow: hidden;
}

.dialog-header,
.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #f2f2ef;
}

.dialog-footer {
  border-bottom: none;
  border-top: 1px solid #f2f2ef;
  justify-content: flex-end;
  gap: 10px;
}

.dialog-close {
  border: none;
  background: #f2f2ef;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.dialog-body {
  padding: 18px 20px;
}

.dialog-desc {
  font-size: 13px;
  line-height: 1.6;
  color: #6a6c72;
  margin-bottom: 12px;
}

.dialog-textarea {
  width: 100%;
  min-height: 110px;
  box-sizing: border-box;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 10px 12px;
  resize: vertical;
  font: inherit;
}

.dialog-btn {
  padding: 8px 16px;
  border-radius: 8px;
  border: none;
  cursor: pointer;
}

.dialog-btn--ghost {
  background: #f2f2ef;
  color: #4b4d54;
}

.dialog-btn--danger {
  background: #c04848;
  color: #fff;
}

/* ---- Desktop ---- */
@media (min-width: 768px) {
  .review-inner {
    padding: 32px;
  }

  .stats-row {
    grid-template-columns: repeat(4, 1fr);
  }

  .review-tabs {
    max-width: 500px;
  }

  .app-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .media-grid {
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  }
}

@media (min-width: 1200px) {
  .app-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 767px) {
  .batch-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
