<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { uploadMedia, saveMedia, listMedia, submitContentEdit, deleteMedia, getMyContentEdits } from '@/api/alumni'
import request from '@/api/request'
import { showToast, showSuccessToast } from 'vant'
import { sanitizeHttpUrl } from '@/utils/markdown'
import { fetchQaList, alumniReplyQa, fetchAlumniQaPending, fetchAlumniQaHistory, alumniReviewQa, updateAlumniQaNote, editAndResubmitAlumniReply } from '@/api/qa'
import {
  ArrowLeft,
  Upload,
  Image,
  FileText,
  Newspaper,
  Link2,
  Plus,
  X,
  Eye,
  Clock,
  CheckCircle,
  XCircle,
  LogOut,
  LayoutGrid,
  Pencil,
  Save,
  Trash2,
  MessageCircle,
  Check,
  Send,
} from 'lucide-vue-next'

const router = useRouter()
const admin = ref<any>(null)
const schoolName = ref('')
const schoolMeta = ref<any>(null)
const banners = ref<any[]>([])
const photos = ref<any[]>([])
const defaultPhotos = ref<string[]>([])
const articles = ref<any[]>([])
const files = ref<any[]>([])
const submittedEdits = ref<any[]>([])
const uploading = ref(false)
const activeTab = ref<'photos' | 'news' | 'files' | 'edit' | 'qa'>('photos')

// QA state
const qaThreads = ref<any[]>([])
const qaPending = ref<any[]>([])
const qaHistory = ref<any[]>([])
const qaReplyingTo = ref<number | null>(null)
const qaReplyContent = ref('')
const qaSubmitting = ref(false)
const qaRejectDialogVisible = ref(false)
const qaRejectReason = ref('')
const qaRejectTargetId = ref<number | null>(null)
const qaNoteDialogVisible = ref(false)
const qaNoteTargetId = ref<number | null>(null)
const qaNoteText = ref('')
const qaEditReplyId = ref<number | null>(null)
const qaEditReplyContent = ref('')
const qaStatusFilter = ref<'all' | 'pending' | 'rejected' | 'approved'>('all')

const showArticleDialog = ref(false)
const articleForm = ref({ title: '', content: '', link: '' })

const editFields = ref([
  { key: 'content', label: '学校简介', value: '', editing: false, type: 'textarea' },
  { key: 'address', label: '学校地址', value: '', editing: false, type: 'input' },
  { key: 'phone', label: '联系电话', value: '', editing: false, type: 'input' },
  { key: 'email', label: '联系邮箱', value: '', editing: false, type: 'input' },
  { key: 'schoolSite', label: '官方网站', value: '', editing: false, type: 'input' },
])
const savingField = ref('')

onMounted(async () => {
  const raw = localStorage.getItem('alumni_admin')
  const token = localStorage.getItem('alumni_token')
  if (!raw || !token) { router.push('/alumni/login'); return }
  try { admin.value = JSON.parse(raw) } catch { router.push('/alumni/login'); return }
  loadAllMedia()
  if (admin.value?.schoolId) {
    loadDefaultPhotos(admin.value.schoolId)
    loadMyEdits()
    try {
      const res = await request.get('/university/by-school-id', { params: { schoolId: admin.value.schoolId } })
      schoolMeta.value = res.data?.data || null
      schoolName.value = schoolMeta.value?.name || admin.value.schoolId
    } catch {
      schoolName.value = admin.value.schoolId
    }
  }
})

async function loadAllMedia() {
  if (!admin.value?.schoolId) return
  try {
    const res = await listMedia(admin.value.schoolId)
    const all = res.data?.data || []
    banners.value = all.filter((m: any) => m.mediaType === 4)
    photos.value = all.filter((m: any) => m.mediaType === 1)
    articles.value = all.filter((m: any) => m.mediaType === 2)
    files.value = all.filter((m: any) => m.mediaType === 3)
  } catch { /* */ }
}

async function loadMyEdits() {
  try {
    const res = await getMyContentEdits()
    submittedEdits.value = res.data?.data || []
  } catch {
    submittedEdits.value = []
  }
}

async function loadDefaultPhotos(schoolId: string) {
  try {
    const res = await fetch('/school_photos.json', { cache: 'no-store' })
    if (!res.ok) throw new Error('load failed')
    const allPhotos = await res.json() as Record<string, string[]>
    defaultPhotos.value = Array.isArray(allPhotos[schoolId]) ? allPhotos[schoolId] : []
  } catch {
    defaultPhotos.value = []
  }
}

const MAX_FILE_SIZE = 5 * 1024 * 1024

async function onPhotoUpload(e: Event, type: 'banner' | 'campus' = 'campus') {
  const input = e.target as HTMLInputElement
  if (!input.files?.length || !admin.value) return
  for (const file of input.files) {
    if (file.size > MAX_FILE_SIZE) {
      showToast(`${file.name} 超过5MB限制`)
      input.value = ''
      return
    }
  }
  uploading.value = true
  try {
    for (const file of input.files) {
      const res = await uploadMedia(file)
      if (res.data?.data) {
        await saveMedia({
          schoolId: admin.value.schoolId,
          mediaType: type === 'banner' ? 4 : 1,
          url: res.data.data,
          caption: file.name.replace(/\.\w+$/, ''),
        })
      }
    }
    showSuccessToast('上传成功，等待审核')
    loadAllMedia()
  } catch {
    showToast('上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function onFileUpload(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length || !admin.value) return
  for (const file of input.files) {
    if (file.size > 10 * 1024 * 1024) {
      showToast(`${file.name} 超过10MB限制`)
      input.value = ''
      return
    }
  }
  uploading.value = true
  try {
    for (const file of input.files) {
      const res = await uploadMedia(file)
      if (res.data?.data) {
        await saveMedia({
          schoolId: admin.value.schoolId,
          mediaType: 3,
          url: res.data.data,
          caption: file.name,
        })
      }
    }
    showSuccessToast('文件上传成功，等待审核')
    loadAllMedia()
  } catch {
    showToast('上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function submitArticle() {
  if (!articleForm.value.title.trim()) {
    showToast('请输入标题')
    return
  }
  if (!articleForm.value.content.trim() && !articleForm.value.link.trim()) {
    showToast('请输入内容或链接')
    return
  }
  uploading.value = true
  try {
    const caption = JSON.stringify({
      title: articleForm.value.title,
      content: articleForm.value.content,
      link: articleForm.value.link,
    })
    await saveMedia({
      schoolId: admin.value.schoolId,
      mediaType: 2,
      url: articleForm.value.link || '',
      caption,
    })
    showSuccessToast('资讯发布成功，等待审核')
    showArticleDialog.value = false
    articleForm.value = { title: '', content: '', link: '' }
    loadAllMedia()
  } catch {
    showToast('发布失败')
  } finally {
    uploading.value = false
  }
}

async function handleDelete(id: number, type: string) {
  try {
    await deleteMedia(id)
    showSuccessToast('删除成功')
    loadAllMedia()
  } catch {
    showToast('删除失败')
  }
}

async function saveField(field: typeof editFields.value[0]) {
  if (!field.value.trim()) {
    showToast('内容不能为空')
    return
  }
  savingField.value = field.key
  try {
    await submitContentEdit({
      schoolId: admin.value.schoolId,
      fieldName: field.key,
      newValue: field.value,
    })
    showSuccessToast('已提交，等待审核')
    field.editing = false
    field.value = ''
    loadMyEdits()
  } catch {
    showToast('提交失败')
  } finally {
    savingField.value = ''
  }
}

function parseCaption(caption: string) {
  try { return JSON.parse(caption) } catch { return { title: caption, content: '', link: '' } }
}

function statusLabel(s: number) {
  return { 0: '待AI审核', 1: '已发布', 2: '已退回', 3: '待人工审核' }[s] || '未知'
}
function statusIcon(s: number) {
  return { 0: Clock, 1: CheckCircle, 2: XCircle, 3: Eye }[s] || Clock
}
function statusColor(s: number) {
  return { 0: '#f59e0b', 1: '#10b981', 2: '#ef4444', 3: '#2563eb' }[s] || '#94a3b8'
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

function editFieldLabel(fieldName: string) {
  return editFields.value.find(field => field.key === fieldName)?.label || fieldName
}

const tabCounts = computed(() => ({
  photos: photos.value.length,
  news: articles.value.length,
  files: files.value.length,
  edit: submittedEdits.value.length,
  qa: qaPending.value.length,
}))

const currentHeroPreview = computed(() => {
  return banners.value[0]?.url || photos.value[0]?.url || defaultPhotos.value[0] || ''
})

const currentHeroSource = computed(() => {
  if (banners.value.length) return '已上传背景横幅'
  if (photos.value.length) return '已上传校园风光首图'
  if (defaultPhotos.value.length) return '系统抓取校园风光首图'
  return '暂无背景图'
})

const qaHealthSummary = computed(() => {
  const rejectedReplyCount = qaHistory.value.filter((item: any) => item.status === 2).length
  const approvedReplyCount = qaHistory.value.filter((item: any) => item.status === 1).length
  return {
    disabled: !!schoolMeta.value?.qaDisabled,
    disabledReason: schoolMeta.value?.qaDisabledReason || '',
    disabledUntil: schoolMeta.value?.qaDisabledUntil || '',
    pendingCount: qaPending.value.length,
    rejectedReplyCount,
    approvedReplyCount,
  }
})

const filteredQaPending = computed(() => {
  return qaStatusFilter.value === 'all' || qaStatusFilter.value === 'pending'
    ? qaPending.value
    : []
})

const filteredQaHistory = computed(() => {
  if (qaStatusFilter.value === 'all') return qaHistory.value
  if (qaStatusFilter.value === 'approved') return qaHistory.value.filter((item: any) => item.status === 1)
  if (qaStatusFilter.value === 'rejected') return qaHistory.value.filter((item: any) => item.status === 2)
  return qaHistory.value.filter((item: any) => item.status === 3)
})

function logout() {
  localStorage.removeItem('alumni_admin')
  localStorage.removeItem('alumni_token')
  router.push('/alumni/login')
}

// ── QA ──

async function loadQa() {
  if (!admin.value?.schoolId) return
  try {
    const [threadsRes, pendingRes, historyRes] = await Promise.all([
      fetchQaList(admin.value.schoolId, 1, 50),
      fetchAlumniQaPending(),
      fetchAlumniQaHistory(),
    ])
    qaThreads.value = threadsRes.data?.data || []
    qaPending.value = pendingRes.data?.data?.items || []
    qaHistory.value = historyRes.data?.data || []
  } catch { /* */ }
}

async function doAlumniReply(questionId: number) {
  if (qaReplyContent.value.trim().length < 2) { showToast('回答至少2个字'); return }
  qaSubmitting.value = true
  try {
    await alumniReplyQa({
      questionId,
      content: qaReplyContent.value,
      authorName: admin.value?.nickname || '校友管理员',
    })
    showSuccessToast('回答已提交，AI审核后将自动发布或进入校友复核')
    qaReplyContent.value = ''
    qaReplyingTo.value = null
    setTimeout(loadQa, 3000)
  } catch { showToast('提交失败') }
  qaSubmitting.value = false
}

async function handleQaDecision(id: number, status: number, reason = '') {
  try {
    await alumniReviewQa({ id, status, reason })
    showSuccessToast(status === 1 ? '已发布' : '已退回')
    qaRejectDialogVisible.value = false
    qaRejectReason.value = ''
    qaRejectTargetId.value = null
    await loadQa()
  } catch (error: any) {
    showToast(error.message || '操作失败')
  }
}

function openQaRejectDialog(id: number) {
  qaRejectTargetId.value = id
  qaRejectReason.value = ''
  qaRejectDialogVisible.value = true
}

async function saveQaNote() {
  if (!qaNoteTargetId.value) return
  if (!qaNoteText.value.trim()) {
    showToast('请输入退回说明')
    return
  }
  try {
    await updateAlumniQaNote(qaNoteTargetId.value, qaNoteText.value.trim())
    showSuccessToast('退回说明已更新')
    qaNoteDialogVisible.value = false
    await loadQa()
  } catch (error: any) {
    showToast(error.message || '保存失败')
  }
}

function openQaNoteDialog(item: any) {
  qaNoteTargetId.value = item.id
  qaNoteText.value = item.reviewNote || ''
  qaNoteDialogVisible.value = true
}

function openReplyEdit(item: any) {
  qaEditReplyId.value = item.id
  qaEditReplyContent.value = item.content || ''
}

async function submitReplyEdit() {
  if (!qaEditReplyId.value) return
  if (qaEditReplyContent.value.trim().length < 2) {
    showToast('回答内容至少2个字')
    return
  }
  try {
    await editAndResubmitAlumniReply(qaEditReplyId.value, qaEditReplyContent.value.trim())
    showSuccessToast('已重新提交，等待 AI 审核')
    qaEditReplyId.value = null
    qaEditReplyContent.value = ''
    await loadQa()
  } catch (error: any) {
    showToast(error.message || '提交失败')
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

function safeNewsLink(caption: string) {
  return sanitizeHttpUrl(parseCaption(caption).link)
}
</script>

<template>
  <div class="manage-page">
    <!-- Header -->
    <header class="manage-header">
      <div class="header-inner">
        <button class="header-back" @click="router.push('/')"><ArrowLeft :size="20" /></button>
        <div class="header-brand">
          <LayoutGrid :size="18" />
          <h1>院校内容管理</h1>
        </div>
        <button class="header-logout" @click="logout">
          <LogOut :size="16" />
          <span>退出</span>
        </button>
      </div>
    </header>

    <div v-if="admin" class="manage-container">
      <!-- Profile Card -->
      <div class="profile-card">
        <div class="profile-avatar">{{ admin.nickname?.charAt(0) || '?' }}</div>
        <div class="profile-info">
          <div class="profile-name">{{ admin.nickname }}</div>
          <div class="profile-meta">{{ schoolName || admin.schoolId }} · {{ admin.major || '校友维护员' }}</div>
        </div>
        <div class="profile-badge">
          <CheckCircle :size="14" />
          <span>已认证</span>
        </div>
      </div>

      <!-- Tabs -->
      <div class="tab-bar">
        <button
          v-for="tab in [
            { key: 'photos', label: '校园照片', icon: Image },
            { key: 'news', label: '资讯动态', icon: Newspaper },
            { key: 'files', label: '文件资料', icon: FileText },
            { key: 'edit', label: '信息编辑', icon: Pencil },
            { key: 'qa', label: '问答管理', icon: MessageCircle },
          ]"
          :key="tab.key"
          class="tab-item"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key as any; if (tab.key === 'qa') loadQa()"
        >
          <component :is="tab.icon" :size="16" />
          <span>{{ tab.label }}</span>
          <span class="tab-count">{{ tabCounts[tab.key as keyof typeof tabCounts] }}</span>
        </button>
      </div>

      <!-- Photos Tab -->
      <div v-if="activeTab === 'photos'" class="tab-content">
        <div class="hero-preview-card">
          <div class="hero-preview-head">
            <div>
              <h3 class="content-title">
                <Image :size="18" />
                当前详情页背景图
              </h3>
              <p class="hero-preview-desc">
                规则：优先使用你上传的背景横幅；如果未上传横幅，则默认使用校园风光第一张图片。
              </p>
            </div>
            <span class="hero-source-badge">{{ currentHeroSource }}</span>
          </div>
          <div v-if="currentHeroPreview" class="hero-preview-image-wrap">
            <img :src="currentHeroPreview" class="hero-preview-image" loading="lazy" />
          </div>
          <div v-else class="hero-preview-empty">暂无可用背景图</div>
        </div>

        <div class="content-toolbar">
          <h3 class="content-title">
            <Image :size="18" />
            背景横幅 ({{ banners.length }})
          </h3>
          <label class="action-btn action-btn--primary" :class="{ disabled: uploading }">
            <Upload :size="15" />
            上传横幅
            <input type="file" accept="image/*" hidden @change="(e: Event) => onPhotoUpload(e, 'banner')" :disabled="uploading" />
          </label>
        </div>
        <p style="font-size: 12px; color: #94a3b8; margin: 0 0 12px;">建议尺寸 1200x400px，宽高比 3:1，最大 5MB</p>
        <div class="banner-list">
          <div v-for="b in banners" :key="b.id" class="banner-card">
            <img :src="b.url" loading="lazy" style="width: 100%; height: 120px; object-fit: cover; border-radius: 8px;" />
            <div class="ai-log ai-log--compact" :class="parseAiLog(b.aiReviewResult).cls">
              <Eye :size="12" />
              <span class="ai-log-label">AI{{ parseAiLog(b.aiReviewResult).label }}</span>
              <span class="ai-log-reason">{{ parseAiLog(b.aiReviewResult).reason }}</span>
            </div>
            <div v-if="b.status === 2 && b.reviewNote" class="review-note">
              <strong>人工退回原因：</strong>{{ b.reviewNote }}
            </div>
            <div class="photo-footer">
              <span class="photo-caption">{{ b.caption || '横幅' }}</span>
              <div class="photo-footer-right">
                <span class="status-tag" :style="{ color: statusColor(b.status), borderColor: statusColor(b.status) }">
                  <component :is="statusIcon(b.status)" :size="11" />
                  {{ statusLabel(b.status) }}
                </span>
                <button class="delete-btn" @click="handleDelete(b.id, 'photo')" title="删除"><Trash2 :size="13" /></button>
              </div>
            </div>
          </div>
          <div v-if="banners.length === 0" style="padding: 20px; text-align: center; color: #94a3b8; font-size: 13px;">暂无横幅</div>
        </div>

        <div class="content-toolbar" style="margin-top: 24px;">
          <h3 class="content-title">
            <Image :size="18" />
            系统抓取默认校园图 ({{ defaultPhotos.length }})
          </h3>
        </div>
        <p style="font-size: 12px; color: #94a3b8; margin: 0 0 12px;">
          这些图片来自系统自动抓取，会回显到当前学校详情页；你可以上传新的背景横幅或校园风光来覆盖默认展示。
        </p>
        <div v-if="defaultPhotos.length" class="photo-grid">
          <div v-for="(url, idx) in defaultPhotos" :key="`${url}-${idx}`" class="photo-card photo-card--readonly">
            <div class="photo-img-wrap">
              <img :src="url" loading="lazy" />
            </div>
            <div class="photo-footer">
              <span class="photo-caption">系统默认图 {{ idx + 1 }}</span>
              <div class="photo-footer-right">
                <span class="status-tag" style="color: #2563eb; border-color: #bfdbfe;">
                  <Eye :size="11" />
                  默认
                </span>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-state empty-state--compact">
          <Image :size="32" />
          <p>暂无系统默认校园图</p>
          <span>如果系统后续抓到图片，会自动回显在这里。</span>
        </div>

        <div class="content-toolbar" style="margin-top: 24px;">
          <h3 class="content-title">
            <Image :size="18" />
            校园风光 ({{ photos.length }})
          </h3>
          <label class="action-btn action-btn--primary" :class="{ disabled: uploading }">
            <Upload :size="15" />
            {{ uploading ? '上传中...' : '上传风光' }}
            <input type="file" accept="image/*" multiple hidden @change="(e: Event) => onPhotoUpload(e, 'campus')" :disabled="uploading" />
          </label>
        </div>
        <p style="font-size: 12px; color: #94a3b8; margin: 0 0 12px;">建议正方形或 4:3 比例，详情页以九宫格展示，最大 5MB</p>

        <div class="photo-grid">
          <div v-for="p in photos" :key="p.id" class="photo-card">
            <div class="photo-img-wrap">
              <img :src="p.url" loading="lazy" />
              <div class="photo-overlay">
                <Eye :size="18" />
              </div>
            </div>
            <div class="ai-log ai-log--compact" :class="parseAiLog(p.aiReviewResult).cls">
              <Eye :size="12" />
              <span class="ai-log-label">AI{{ parseAiLog(p.aiReviewResult).label }}</span>
              <span class="ai-log-reason">{{ parseAiLog(p.aiReviewResult).reason }}</span>
            </div>
            <div v-if="p.status === 2 && p.reviewNote" class="review-note">
              <strong>人工退回原因：</strong>{{ p.reviewNote }}
            </div>
            <div class="photo-footer">
              <span class="photo-caption">{{ p.caption || '未命名' }}</span>
              <div class="photo-footer-right">
                <span class="status-tag" :style="{ color: statusColor(p.status), borderColor: statusColor(p.status) }">
                  <component :is="statusIcon(p.status)" :size="11" />
                  {{ statusLabel(p.status) }}
                </span>
                <button class="delete-btn" @click="handleDelete(p.id, 'photo')" title="删除"><Trash2 :size="13" /></button>
              </div>
            </div>
          </div>

          <label class="photo-add-card" :class="{ disabled: uploading }">
            <Plus :size="28" />
            <span>添加照片</span>
            <input type="file" accept="image/*" multiple hidden @change="onPhotoUpload" :disabled="uploading" />
          </label>
        </div>

        <div v-if="photos.length === 0" class="empty-state">
          <Image :size="40" />
          <p>还没有照片</p>
          <span>上传母校的美照，让更多人了解你的学校</span>
        </div>
      </div>

      <!-- News Tab -->
      <div v-if="activeTab === 'news'" class="tab-content">
        <div class="content-toolbar">
          <h3 class="content-title">
            <Newspaper :size="18" />
            资讯动态
          </h3>
          <button class="action-btn action-btn--primary" @click="showArticleDialog = true">
            <Plus :size="15" />
            发布资讯
          </button>
        </div>

        <div class="news-list">
          <div v-for="a in articles" :key="a.id" class="news-card">
            <div class="news-body">
              <h4 class="news-title">{{ parseCaption(a.caption).title }}</h4>
              <p v-if="parseCaption(a.caption).content" class="news-excerpt">
                {{ parseCaption(a.caption).content.slice(0, 100) }}{{ parseCaption(a.caption).content.length > 100 ? '...' : '' }}
              </p>
              <a v-if="safeNewsLink(a.caption)" :href="safeNewsLink(a.caption)" target="_blank" rel="noopener noreferrer" class="news-link">
                <Link2 :size="13" />
                查看原文
              </a>
            </div>
            <div class="ai-log ai-log--compact" :class="parseAiLog(a.aiReviewResult).cls">
              <Eye :size="12" />
              <span class="ai-log-label">AI{{ parseAiLog(a.aiReviewResult).label }}</span>
              <span class="ai-log-reason">{{ parseAiLog(a.aiReviewResult).reason }}</span>
            </div>
            <div v-if="a.status === 2 && a.reviewNote" class="review-note">
              <strong>人工退回原因：</strong>{{ a.reviewNote }}
            </div>
            <div class="news-footer">
              <span class="status-tag" :style="{ color: statusColor(a.status), borderColor: statusColor(a.status) }">
                <component :is="statusIcon(a.status)" :size="11" />
                {{ statusLabel(a.status) }}
              </span>
              <button class="delete-btn" @click="handleDelete(a.id, 'news')" title="删除"><Trash2 :size="13" /></button>
            </div>
          </div>
        </div>

        <div v-if="articles.length === 0" class="empty-state">
          <Newspaper :size="40" />
          <p>还没有资讯</p>
          <span>发布学校的最新动态、招生资讯等</span>
        </div>
      </div>

      <!-- Files Tab -->
      <div v-if="activeTab === 'files'" class="tab-content">
        <div class="content-toolbar">
          <h3 class="content-title">
            <FileText :size="18" />
            文件资料
          </h3>
          <label class="action-btn action-btn--primary" :class="{ disabled: uploading }">
            <Upload :size="15" />
            {{ uploading ? '上传中...' : '上传文件' }}
            <input type="file" multiple hidden @change="onFileUpload" :disabled="uploading" />
          </label>
        </div>

        <div class="file-list">
          <div v-for="f in files" :key="f.id" class="file-card">
            <div class="file-icon-wrap">
              <FileText :size="22" />
            </div>
            <div class="file-info">
              <span class="file-name">{{ f.caption || '未命名文件' }}</span>
              <a v-if="safeMediaUrl(f.url)" :href="safeMediaUrl(f.url)" target="_blank" rel="noopener noreferrer" class="file-download">下载</a>
              <span v-else class="file-download file-download--disabled">链接待核验</span>
              <div class="ai-log ai-log--compact" :class="parseAiLog(f.aiReviewResult).cls">
                <Eye :size="12" />
                <span class="ai-log-label">AI{{ parseAiLog(f.aiReviewResult).label }}</span>
                <span class="ai-log-reason">{{ parseAiLog(f.aiReviewResult).reason }}</span>
              </div>
              <div v-if="f.status === 2 && f.reviewNote" class="review-note review-note--inline">
                <strong>人工退回原因：</strong>{{ f.reviewNote }}
              </div>
            </div>
            <span class="status-tag" :style="{ color: statusColor(f.status), borderColor: statusColor(f.status) }">
              <component :is="statusIcon(f.status)" :size="11" />
              {{ statusLabel(f.status) }}
            </span>
            <button class="delete-btn" @click="handleDelete(f.id, 'file')" title="删除"><Trash2 :size="13" /></button>
          </div>
        </div>

        <div v-if="files.length === 0" class="empty-state">
          <FileText :size="40" />
          <p>还没有文件</p>
          <span>上传招生简章、专业介绍等资料</span>
        </div>
      </div>

      <!-- Edit Tab -->
      <div v-if="activeTab === 'edit'" class="tab-content">
        <div class="content-toolbar">
          <h3 class="content-title">
            <Pencil :size="18" />
            信息编辑
          </h3>
        </div>

        <div class="edit-info-hint">
          修改提交后会先经过 AI 一审，再交给系统管理员二审。
        </div>

        <div class="edit-fields">
          <div v-for="field in editFields" :key="field.key" class="edit-field-card">
            <div class="edit-field-header">
              <span class="edit-field-label">{{ field.label }}</span>
              <button
                v-if="!field.editing"
                class="edit-field-btn"
                @click="field.editing = true"
              >
                <Pencil :size="13" />
                编辑
              </button>
              <div v-else class="edit-field-actions">
                <button class="edit-field-btn edit-field-btn--cancel" @click="field.editing = false; field.value = ''">
                  取消
                </button>
                <button
                  class="edit-field-btn edit-field-btn--save"
                  :disabled="savingField === field.key"
                  @click="saveField(field)"
                >
                  <Save :size="13" />
                  {{ savingField === field.key ? '提交中...' : '提交' }}
                </button>
              </div>
            </div>
            <div v-if="field.editing" class="edit-field-input">
              <textarea
                v-if="field.type === 'textarea'"
                v-model="field.value"
                :placeholder="'输入新的' + field.label"
                rows="5"
                class="form-input form-textarea"
              ></textarea>
              <input
                v-else
                v-model="field.value"
                :placeholder="'输入新的' + field.label"
                class="form-input"
              />
            </div>
            <div v-else class="edit-field-hint">
              点击编辑按钮修改{{ field.label }}
            </div>
          </div>
        </div>

        <div class="content-toolbar" style="margin-top: 24px;">
          <h3 class="content-title">
            <Clock :size="18" />
            最近提交记录 ({{ submittedEdits.length }})
          </h3>
        </div>

        <div v-if="submittedEdits.length" class="edit-history-list">
          <div v-for="item in submittedEdits" :key="item.id" class="edit-history-card">
            <div class="edit-history-head">
              <span class="edit-field-label">{{ editFieldLabel(item.fieldName) }}</span>
              <span class="status-tag" :style="{ color: statusColor(item.status), borderColor: statusColor(item.status) }">
                <component :is="statusIcon(item.status)" :size="11" />
                {{ statusLabel(item.status) }}
              </span>
            </div>
            <div class="edit-history-value">{{ item.newValue }}</div>
            <div class="ai-log ai-log--compact" :class="parseAiLog(item.aiReviewResult).cls">
              <Eye :size="12" />
              <span class="ai-log-label">AI{{ parseAiLog(item.aiReviewResult).label }}</span>
              <span class="ai-log-reason">{{ parseAiLog(item.aiReviewResult).reason }}</span>
            </div>
            <div v-if="item.status === 2 && item.reviewNote" class="review-note">
              <strong>人工退回原因：</strong>{{ item.reviewNote }}
            </div>
          </div>
        </div>
        <div v-else class="empty-state empty-state--compact">
          <Clock :size="32" />
          <p>还没有提交记录</p>
          <span>你提交过的内容编辑、AI一审结果和人工审核状态会显示在这里。</span>
        </div>
      </div>

      <!-- QA Tab -->
      <div v-if="activeTab === 'qa'" class="tab-content">
        <div class="content-toolbar">
          <h3 class="content-title"><MessageCircle :size="18" /> 问答管理</h3>
        </div>

        <div class="edit-info-hint" :class="{ 'edit-info-hint--danger': qaHealthSummary.disabled }">
          <template v-if="qaHealthSummary.disabled">
            当前学校问答功能已关闭{{ qaHealthSummary.disabledReason ? `：${qaHealthSummary.disabledReason}` : '' }}
            <template v-if="qaHealthSummary.disabledUntil">（截止 {{ new Date(qaHealthSummary.disabledUntil).toLocaleString('zh-CN') }}）</template>
          </template>
          <template v-else>
            你负责本校问答自治：AI 高置信内容会自动通过/退回；低置信内容由你复核。当前待复核 {{ qaHealthSummary.pendingCount }} 条，已退回回复 {{ qaHealthSummary.rejectedReplyCount }} 条。
          </template>
        </div>

        <div class="sub-filter-bar sub-filter-bar--panel">
          <div class="sub-stats">
            <span class="sub-stat-chip">待复核 {{ qaHealthSummary.pendingCount }}</span>
            <span class="sub-stat-chip">已退回 {{ qaHealthSummary.rejectedReplyCount }}</span>
            <span class="sub-stat-chip">已通过 {{ qaHealthSummary.approvedReplyCount }}</span>
          </div>
          <div class="status-tabs status-tabs--segment">
            <button :class="{ active: qaStatusFilter === 'all' }" @click="qaStatusFilter = 'all'">全部</button>
            <button :class="{ active: qaStatusFilter === 'pending' }" @click="qaStatusFilter = 'pending'">待复核</button>
            <button :class="{ active: qaStatusFilter === 'rejected' }" @click="qaStatusFilter = 'rejected'">已退回</button>
            <button :class="{ active: qaStatusFilter === 'approved' }" @click="qaStatusFilter = 'approved'">已通过</button>
          </div>
        </div>

        <!-- Pending Review -->
        <div v-if="filteredQaPending.length" class="qa-section">
          <h4 class="qa-subtitle">待校友复核 ({{ filteredQaPending.length }})</h4>
          <div v-for="item in filteredQaPending" :key="item.id" class="qa-review-card">
            <div class="qa-review-meta">
              <span class="qa-badge" :class="item.parentId ? 'qa-badge--ans' : 'qa-badge--q'">{{ item.parentId ? '回答' : '提问' }}</span>
              <span>{{ item.authorName }}</span>
              <span class="qa-review-time">{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</span>
            </div>
            <p class="qa-review-content">{{ item.content }}</p>
            <div class="ai-log ai-log--compact" :class="parseAiLog(item.aiReviewResult).cls">
              <Eye :size="12" />
              <span class="ai-log-label">AI{{ parseAiLog(item.aiReviewResult).label }}</span>
              <span class="ai-log-reason">{{ parseAiLog(item.aiReviewResult).reason }}</span>
            </div>
            <div class="qa-review-actions">
              <button class="qa-btn qa-btn--pass" @click="handleQaDecision(item.id, 1)">
                <Check :size="14" /> 通过发布
              </button>
              <button class="qa-btn qa-btn--reject" @click="openQaRejectDialog(item.id)">
                <X :size="14" /> 退回
              </button>
            </div>
          </div>
        </div>

        <!-- All Threads -->
        <div v-if="qaStatusFilter === 'all'" class="qa-section">
          <h4 class="qa-subtitle">已有问答 ({{ qaThreads.length }})</h4>
          <div v-if="qaThreads.length === 0" class="qa-empty">暂无问答</div>
          <div v-for="t in qaThreads" :key="t.question.id" class="qa-thread-card">
            <div class="qa-thread-q">
              <strong>{{ t.question.authorName }}：</strong>{{ t.question.content }}
            </div>
            <div v-for="a in t.answers" :key="a.id" class="qa-thread-a">
              <strong>{{ a.authorName }}：</strong>{{ a.content }}
            </div>
            <div class="qa-thread-actions">
              <button class="qa-btn qa-btn--reply" @click="qaReplyingTo = qaReplyingTo === t.question.id ? null : t.question.id">
                <Send :size="14" /> 回复
              </button>
            </div>
            <div v-if="qaReplyingTo === t.question.id" class="qa-reply-box">
              <textarea v-model="qaReplyContent" placeholder="输入回复内容..." class="form-input form-textarea" rows="3" maxlength="1000"></textarea>
              <div class="qa-reply-btns">
                <button class="qa-btn" @click="qaReplyingTo = null">取消</button>
                <button class="qa-btn qa-btn--pass" :disabled="qaSubmitting" @click="doAlumniReply(t.question.id)">
                  <Send :size="14" /> {{ qaSubmitting ? '提交中...' : '提交回复' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="qa-section">
          <h4 class="qa-subtitle">我的回复记录 ({{ filteredQaHistory.length }})</h4>
          <div v-if="filteredQaHistory.length === 0" class="qa-empty">暂无匹配的回复记录</div>
          <div v-for="item in filteredQaHistory" :key="`history-${item.id}`" class="qa-review-card">
            <div class="qa-review-meta">
              <span class="qa-badge qa-badge--ans">回复</span>
              <span>{{ item.authorName }}</span>
              <span class="status-tag" :style="{ color: statusColor(item.status), borderColor: statusColor(item.status) }">
                <component :is="statusIcon(item.status)" :size="11" />
                {{ statusLabel(item.status) }}
              </span>
              <span class="qa-review-time">{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</span>
            </div>
            <p class="qa-review-content">{{ item.content }}</p>
            <div class="ai-log ai-log--compact" :class="parseAiLog(item.aiReviewResult).cls">
              <Eye :size="12" />
              <span class="ai-log-label">AI{{ parseAiLog(item.aiReviewResult).label }}</span>
              <span class="ai-log-reason">{{ parseAiLog(item.aiReviewResult).reason }}</span>
            </div>
            <div v-if="item.status === 2 && item.reviewNote" class="review-note">
              <strong>人工退回原因：</strong>{{ item.reviewNote }}
            </div>
            <div v-if="item.status === 2 || item.status === 3" class="qa-review-actions" style="margin-top: 10px;">
              <button class="qa-btn qa-btn--reply" @click="openQaNoteDialog(item)">
                <MessageCircle :size="14" /> 修改退回说明
              </button>
              <button v-if="item.authorType === 'alumni'" class="qa-btn qa-btn--pass" @click="openReplyEdit(item)">
                <Pencil :size="14" /> 编辑回复后重提
              </button>
            </div>
            <div v-if="qaEditReplyId === item.id" class="qa-reply-box">
              <textarea v-model="qaEditReplyContent" placeholder="修改后的回复内容..." class="form-input form-textarea" rows="3" maxlength="1000"></textarea>
              <div class="qa-reply-btns">
                <button class="qa-btn" @click="qaEditReplyId = null; qaEditReplyContent = ''">取消</button>
                <button class="qa-btn qa-btn--pass" @click="submitReplyEdit">
                  <Send :size="14" /> 重新提交审核
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Article Dialog -->
    <van-overlay :show="showArticleDialog" @click="showArticleDialog = false">
      <div class="dialog-wrap" @click.stop>
        <div class="dialog-card">
          <div class="dialog-header">
            <h3>发布资讯</h3>
            <button class="dialog-close" @click="showArticleDialog = false"><X :size="18" /></button>
          </div>
          <div class="dialog-body">
            <div class="form-field">
              <label>标题 <span class="required">*</span></label>
              <input v-model="articleForm.title" placeholder="输入资讯标题" class="form-input" />
            </div>
            <div class="form-field">
              <label>内容</label>
              <textarea v-model="articleForm.content" placeholder="输入资讯内容（可选）" rows="5" class="form-input form-textarea"></textarea>
            </div>
            <div class="form-field">
              <label>链接</label>
              <input v-model="articleForm.link" placeholder="外部链接地址（可选）" class="form-input" />
            </div>
          </div>
          <div class="dialog-footer">
            <button class="action-btn action-btn--ghost" @click="showArticleDialog = false">取消</button>
            <button class="action-btn action-btn--primary" :disabled="uploading" @click="submitArticle">
              {{ uploading ? '发布中...' : '发布' }}
            </button>
          </div>
        </div>
      </div>
    </van-overlay>

    <van-overlay :show="qaRejectDialogVisible" @click="qaRejectDialogVisible = false">
      <div class="dialog-wrap" @click.stop>
        <div class="dialog-card">
          <div class="dialog-header">
            <h3>退回问答</h3>
            <button class="dialog-close" @click="qaRejectDialogVisible = false"><X :size="18" /></button>
          </div>
          <div class="dialog-body">
            <div class="form-field">
              <label>退回说明 <span class="required">*</span></label>
              <textarea v-model="qaRejectReason" placeholder="例如：内容较笼统，请补充更具体的信息后重新提交" rows="4" class="form-input form-textarea"></textarea>
            </div>
          </div>
          <div class="dialog-footer">
            <button class="action-btn action-btn--ghost" @click="qaRejectDialogVisible = false">取消</button>
            <button class="action-btn action-btn--primary" @click="qaRejectTargetId && handleQaDecision(qaRejectTargetId, 2, qaRejectReason)">确认退回</button>
          </div>
        </div>
      </div>
    </van-overlay>

    <van-overlay :show="qaNoteDialogVisible" @click="qaNoteDialogVisible = false">
      <div class="dialog-wrap" @click.stop>
        <div class="dialog-card">
          <div class="dialog-header">
            <h3>修改退回说明</h3>
            <button class="dialog-close" @click="qaNoteDialogVisible = false"><X :size="18" /></button>
          </div>
          <div class="dialog-body">
            <div class="form-field">
              <label>退回说明 <span class="required">*</span></label>
              <textarea v-model="qaNoteText" placeholder="输入新的退回说明" rows="4" class="form-input form-textarea"></textarea>
            </div>
          </div>
          <div class="dialog-footer">
            <button class="action-btn action-btn--ghost" @click="qaNoteDialogVisible = false">取消</button>
            <button class="action-btn action-btn--primary" @click="saveQaNote">保存</button>
          </div>
        </div>
      </div>
    </van-overlay>
  </div>
</template>

<style scoped>
.manage-page {
  min-height: 100dvh;
  background: var(--gz-bg, #f8fafc);
}

/* ---- Header ---- */
.manage-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: linear-gradient(135deg, #1e3a8a, #2563eb);
  color: #fff;
  box-shadow: 0 2px 20px rgba(30, 58, 138, 0.3);
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 1100px;
  margin: 0 auto;
  padding: 14px 16px;
}

.header-back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(8px);
  color: #fff;
  cursor: pointer;
  transition: background 0.2s;
}

.header-back:hover {
  background: rgba(255, 255, 255, 0.25);
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.header-brand h1 {
  font-size: 17px;
  font-weight: 700;
}

.header-logout {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 7px 14px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.9);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.header-logout:hover {
  background: rgba(255, 255, 255, 0.2);
}

/* ---- Container ---- */
.manage-container {
  max-width: 1100px;
  margin: 0 auto;
  padding: 20px 16px;
}

.tab-content {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

/* ---- Profile Card ---- */
.profile-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.04);
  margin-bottom: 20px;
}

.profile-avatar {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 800;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.profile-name {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.profile-meta {
  font-size: 13px;
  color: #64748b;
  margin-top: 3px;
}

.profile-badge {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border-radius: 20px;
  background: #ecfdf5;
  color: #059669;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

/* ---- Tabs ---- */
.tab-bar {
  display: flex;
  gap: 6px;
  padding: 4px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  margin-bottom: 20px;
  overflow-x: auto;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
  justify-content: center;
  padding: 12px 16px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.tab-item:hover {
  background: #f1f5f9;
  color: #334155;
}

.tab-item.active {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
}

.tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.06);
  font-size: 11px;
  font-weight: 700;
}

.tab-item.active .tab-count {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
}

/* ---- Content Toolbar ---- */
.content-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.content-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}

.hero-preview-card {
  padding: 18px 20px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  margin-bottom: 20px;
}

.hero-preview-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;
}

.hero-preview-desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.hero-source-badge {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.hero-preview-image-wrap {
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.hero-preview-image {
  width: 100%;
  height: 220px;
  object-fit: cover;
  display: block;
}

.hero-preview-empty {
  padding: 24px;
  border-radius: 12px;
  border: 1px dashed #cbd5e1;
  background: #f8fafc;
  color: #94a3b8;
  text-align: center;
  font-size: 13px;
}

/* ---- Action Buttons ---- */
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 18px;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn--primary {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: #fff;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.25);
}

.action-btn--primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.35);
}

.action-btn--primary:active {
  transform: scale(0.97);
}

.action-btn--ghost {
  background: #f1f5f9;
  color: #475569;
}

.action-btn--ghost:hover {
  background: #e2e8f0;
}

.action-btn.disabled {
  opacity: 0.5;
  pointer-events: none;
}

/* ---- Photo Grid ---- */
.photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
}

.photo-card {
  border-radius: 14px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s, box-shadow 0.2s;
}

.photo-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.photo-card--readonly:hover {
  transform: none;
}

.photo-img-wrap {
  position: relative;
  aspect-ratio: 4/3;
  overflow: hidden;
}

.photo-img-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}

.photo-card:hover .photo-img-wrap img {
  transform: scale(1.05);
}

.photo-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.3);
  color: #fff;
  opacity: 0;
  transition: opacity 0.2s;
}

.photo-card:hover .photo-overlay {
  opacity: 1;
}

.photo-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  gap: 8px;
}

.photo-caption {
  font-size: 12px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.photo-add-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  aspect-ratio: 4/3;
  border: 2px dashed #cbd5e1;
  border-radius: 14px;
  color: #94a3b8;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.photo-add-card:hover {
  border-color: #2563eb;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.04);
}

.photo-add-card.disabled {
  opacity: 0.5;
  pointer-events: none;
}

/* ---- Status Tag ---- */
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  border: 1px solid;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 600;
  flex-shrink: 0;
}

/* ---- News List ---- */
.news-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.news-card {
  padding: 20px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s;
}

.news-card:hover {
  transform: translateY(-1px);
}

.news-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 6px;
}

.news-excerpt {
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
  margin-bottom: 8px;
}

.news-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #2563eb;
  text-decoration: none;
  font-weight: 500;
}

.news-link:hover {
  text-decoration: underline;
}

.news-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

/* ---- File List ---- */
.file-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.file-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
  transition: transform 0.2s;
}

.file-card:hover {
  transform: translateY(-1px);
}

.file-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #eff6ff;
  color: #2563eb;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-download {
  font-size: 12px;
  color: #2563eb;
  text-decoration: none;
}

.file-download:hover {
  text-decoration: underline;
}

.ai-log {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 9px 12px;
  border-radius: 10px;
  background: #f8fafc;
  color: #64748b;
  border: 1px solid #e2e8f0;
}

.ai-log--compact {
  margin: 10px 0 0;
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
  background: #fef2f2;
  border-color: #fecaca;
  color: #991b1b;
}

.ai-error {
  background: #fffbeb;
  border-color: #fde68a;
  color: #92400e;
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

.review-note--inline {
  margin-top: 8px;
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
  color: #64748b;
}

.sub-stat-chip {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  font-weight: 600;
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
  color: #64748b;
  cursor: pointer;
  transition: all 0.18s;
}

.status-tabs button.active {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.22);
}

.status-tabs button:hover:not(.active) {
  background: #f8fafc;
  color: #334155;
}

.status-tabs--segment {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  background: #f8fafc;
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
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: #fff;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.22);
}

/* ---- Empty State ---- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 60px 20px;
  color: #94a3b8;
}

.empty-state p {
  font-size: 16px;
  font-weight: 600;
  color: #64748b;
}

.empty-state span {
  font-size: 13px;
}

.empty-state--compact {
  padding: 28px 16px;
}

/* ---- Dialog ---- */
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
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  overflow: hidden;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #f1f5f9;
}

.dialog-header h3 {
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}

.dialog-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: #f1f5f9;
  color: #64748b;
  cursor: pointer;
}

.dialog-close:hover {
  background: #e2e8f0;
}

.dialog-body {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-field label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 6px;
}

.required {
  color: #ef4444;
}

.form-input {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  color: #0f172a;
  background: #f8fafc;
  transition: border-color 0.2s, box-shadow 0.2s;
  outline: none;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
  background: #fff;
}

.form-textarea {
  resize: vertical;
  min-height: 100px;
  font-family: inherit;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid #f1f5f9;
}

/* ---- Photo Footer Right ---- */
.photo-footer-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ---- Delete Button ---- */
.delete-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.delete-btn:hover {
  background: #fef2f2;
  color: #ef4444;
}

/* ---- Edit Fields ---- */
.edit-info-hint {
  padding: 10px 14px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 10px;
  font-size: 13px;
  color: #92400e;
  margin-bottom: 16px;
}

.edit-info-hint--danger {
  background: #fef2f2;
  border-color: #fecaca;
  color: #991b1b;
}

.edit-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.edit-field-card {
  padding: 18px 20px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.edit-field-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.edit-field-label {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.edit-field-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  border: 1.5px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.edit-field-btn:hover {
  background: #eff6ff;
  border-color: #2563eb;
}

.edit-field-actions {
  display: flex;
  gap: 8px;
}

.edit-field-btn--cancel {
  color: #64748b;
}

.edit-field-btn--save {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}

.edit-field-btn--save:hover {
  background: #1d4ed8;
}

.edit-field-btn--save:disabled {
  opacity: 0.6;
}

.edit-field-input {
  margin-top: 4px;
}

.edit-field-hint {
  font-size: 13px;
  color: #94a3b8;
}

.edit-history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.edit-history-card {
  padding: 16px 18px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.edit-history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.edit-history-value {
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
  margin-bottom: 10px;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 767px) {
  .hero-preview-head {
    flex-direction: column;
  }

  .hero-preview-image {
    height: 160px;
  }
}

/* ---- Desktop ---- */
@media (min-width: 768px) {
  .header-inner {
    padding: 16px 32px;
  }

  .header-brand h1 {
    font-size: 20px;
  }

  .manage-container {
    padding: 28px 32px;
  }

  .profile-card {
    padding: 28px 32px;
    border-radius: 24px;
  }

  .tab-content {
    padding: 24px;
    border-radius: 24px;
  }

  .photo-grid {
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 18px;
  }

  .tab-bar {
    max-width: 760px;
  }
}

@media (min-width: 1024px) {
  .manage-container {
    max-width: 1320px;
    padding: 32px 40px;
  }

  .photo-grid {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  }

  .news-list {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
  }

  .edit-fields {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
  }

  .edit-history-list {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
  }

  .file-list {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .banner-list {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 14px;
  }

  .hero-preview-card {
    padding: 22px 24px;
  }

  .hero-preview-image {
    height: 280px;
  }

  .sub-filter-bar--panel {
    padding: 16px 18px;
  }

  .qa-review-actions {
    flex-wrap: wrap;
  }

  .dialog-card {
    max-width: 600px;
  }
}

@media (min-width: 1400px) {
  .manage-container {
    max-width: 1440px;
    padding: 36px 48px;
  }

  .photo-grid {
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  }

  .banner-list,
  .news-list,
  .file-list,
  .edit-fields,
  .edit-history-list {
    gap: 18px;
  }
}

/* ── QA Tab ── */
.qa-section { margin-bottom: 20px; }
.qa-subtitle { font-size: 14px; font-weight: 700; color: #374151; margin-bottom: 10px; }
.qa-empty { text-align: center; padding: 24px; color: #9ca3af; font-size: 14px; }

.qa-review-card, .qa-thread-card {
  background: #fff; border: 1px solid #f3f4f6; border-radius: 10px; padding: 12px 14px; margin-bottom: 10px;
}
.qa-review-meta { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #6b7280; margin-bottom: 6px; }
.qa-review-time { margin-left: auto; font-size: 12px; color: #9ca3af; }
.qa-review-content { font-size: 14px; line-height: 1.6; color: #1f2937; margin: 0 0 8px; }
.qa-review-actions { display: flex; gap: 8px; }
.qa-review-note { font-size: 12px; color: #64748b; }

.qa-badge { padding: 1px 6px; border-radius: 4px; font-size: 10px; font-weight: 600; }
.qa-badge--q { background: #eff6ff; color: #2563eb; }
.qa-badge--ans { background: #f0fdf4; color: #16a34a; }

.qa-btn {
  display: flex; align-items: center; gap: 4px; padding: 5px 12px;
  border: 1px solid #e5e7eb; border-radius: 6px; background: #fff;
  font-size: 12px; cursor: pointer; transition: all 0.15s;
}
.qa-btn--pass { color: #16a34a; border-color: #bbf7d0; }
.qa-btn--pass:hover { background: #f0fdf4; }
.qa-btn--reject { color: #ef4444; border-color: #fecaca; }
.qa-btn--reject:hover { background: #fef2f2; }
.qa-btn--reply { color: #2563eb; border-color: #bfdbfe; }
.qa-btn--reply:hover { background: #eff6ff; }

.qa-thread-q { font-size: 14px; color: #1f2937; margin-bottom: 6px; }
.qa-thread-a { font-size: 13px; color: #6b7280; margin-left: 16px; margin-bottom: 4px; padding-left: 8px; border-left: 2px solid #e5e7eb; }
.qa-thread-actions { margin-top: 8px; }
.qa-reply-box { margin-top: 8px; display: flex; flex-direction: column; gap: 8px; }
.qa-reply-btns { display: flex; justify-content: flex-end; gap: 8px; }
</style>
