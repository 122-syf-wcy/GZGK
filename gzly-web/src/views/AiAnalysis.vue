<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useVolunteerStore } from '@/stores/volunteer'
import { useAuthStore } from '@/stores/auth'
import { fetchMyPlanDetail, fetchMyPlanSkillsHistory } from '@/api/myPlans'
import {
  chatZxfSkill,
  createAiAnalysisTicket,
  exportPlanLongImage,
  fetchVolunteerPlan,
  generateAiAnalysis,
  getAiAnalysisUrl,
  type AiAnalysisResponse,
  type ZxfSkillChatMessage,
} from '@/api/volunteer'
import AiAnalysisHeader from '@/components/ai/AiAnalysisHeader.vue'
import AiAnalysisSidebar from '@/components/ai/AiAnalysisSidebar.vue'
import ConclusionSection from '@/components/ai/ConclusionSection.vue'
import DiagnosisSection from '@/components/ai/DiagnosisSection.vue'
import ActionStepsSection from '@/components/ai/ActionStepsSection.vue'
import SkillsServiceCard from '@/components/ai/SkillsServiceCard.vue'
import SkillsChatBox from '@/components/ai/SkillsChatBox.vue'
import FloatingExportBar from '@/components/ai/FloatingExportBar.vue'
import AiAnalysisMetaBadge from '@/components/ai/AiAnalysisMetaBadge.vue'
import VolunteerListPanel from '@/components/ai/VolunteerListPanel.vue'
import { buildAiProfileSummary, formDataFromPlan } from '@/utils/volunteer-plan'
import { renderMarkdown } from '@/utils/markdown'
import { AI_GENERATED_NOTICE } from '@/constants/compliance'
import type { VolunteerPlan } from '@/types'
import {
  AlertTriangle,
  Bot,
  ChevronDown,
  ShieldCheck,
} from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const volunteerStore = useVolunteerStore()
const authStore = useAuthStore()

const content = ref('')
const isStreaming = ref(false)
const isDone = ref(false)
const contentRef = ref<HTMLDivElement | null>(null)
const activeSection = ref('headline')
const scrollProgress = ref(0)
const collapsedSections = ref<Set<string>>(new Set())
const skillInput = ref('')
const skillLoading = ref(false)
const skillError = ref('')
const skillMessages = ref<ZxfSkillChatMessage[]>([])
const skillSourceOpen = ref(false)
const skillSourceChunks = ref<Array<Record<string, unknown>>>([])
const skillReferencedVolunteers = ref<Array<Record<string, unknown>>>([])
const aiMeta = ref<{
  modelVersion?: string
  fallback?: boolean
  fallbackReason?: string
  issues?: string[]
} | null>(null)
let eventSource: EventSource | null = null

const modeProfile = computed(() => buildAiProfileSummary(volunteerStore.formData))
const markdownHtml = computed(() => renderMarkdown(content.value))

function chanceOf(item: { chanceScore?: number }) {
  return item.chanceScore || 0
}

const keepList = computed(() =>
  [...volunteerStore.planItems]
    .sort((a, b) => ((b.matchScore || 0) + chanceOf(b)) - ((a.matchScore || 0) + chanceOf(a)))
    .slice(0, 5),
)

const riskList = computed(() =>
  [...volunteerStore.planItems]
    .sort((a, b) => {
      const aRisk = (a.riskColor === 'red' ? 100 : a.riskColor === 'yellow' ? 60 : 20) + (40 - chanceOf(a))
      const bRisk = (b.riskColor === 'red' ? 100 : b.riskColor === 'yellow' ? 60 : 20) + (40 - chanceOf(b))
      return bRisk - aRisk
    })
    .slice(0, 3),
)

const overviewMetrics = computed(() => {
  const items = volunteerStore.planItems
  const total = items.length || 1
  const byGradient = {
    冲: items.filter(item => item.gradient === '冲').length,
    稳: items.filter(item => item.gradient === '稳').length,
    保: items.filter(item => item.gradient === '保').length,
    垫: items.filter(item => item.gradient === '垫').length,
  }
  return [
    { label: '梯度分布', value: `冲${byGradient.冲} / 稳${byGradient.稳} / 保${byGradient.保} / 垫${byGradient.垫}` },
    { label: '专业级数据', value: `${items.filter(item => item.dataSourceType === '专业级').length} 个` },
    { label: '高匹配项', value: `${items.filter(item => (item.matchScore || 0) >= 60).length} 个` },
    { label: '高风险项', value: `${items.filter(item => item.riskColor === 'red').length} 个` },
  ]
})

type ReportSection = {
  id: string
  title: string
  html: string
  preview: string
}

const headlineHighlights = computed(() => {
  const keep = keepList.value[0]
  const risk = riskList.value[0]
  const highlightItems: Array<{ title: string; value: string; note: string; tone?: 'warn' | 'normal' }> = []
  if (keep) {
    highlightItems.push({
      title: '当前最值得保留',
      value: `${keep.universityName} · ${keep.majorName}`,
      note: keep.recommendReason || '专业级数据与当前偏好相对更贴近，适合作为前排保留项。',
    })
  }
  if (risk) {
    highlightItems.push({
      title: '当前最大风险点',
      value: `${risk.universityName} · ${risk.majorName}`,
      note: risk.riskReason || '建议优先核对章程、位次波动和专业限制。',
      tone: 'warn',
    })
  }
  return highlightItems
})

function normalizeHeadingLine(raw: string) {
  const normalized = raw
    .replace(/^(\s*)[-*](?=\*\*)/, '$1- ')
    .replace(/^(\s*)[-*]\*(?!\*)\s*/, '$1- ')
  const line = normalized.trim()
  if (/^\*\*[^*]+\*\*$/.test(line)) {
    return `## ${line.replace(/^\*\*|\*\*$/g, '').trim()}`
  }
  if (/^【[^】]+】$/.test(line)) {
    return `## ${line.replace(/^【|】$/g, '').trim()}`
  }
  return normalized
}

function cleanPreviewText(text: string) {
  return text
    .replace(/^\s*[-*]\s*/gm, '')
    .replace(/\*/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

const reportSections = computed<ReportSection[]>(() => {
  const source = content.value.trim()
  if (!source) return []

  const lines = source
    .replace(/\r\n/g, '\n')
    .split('\n')
    .map(normalizeHeadingLine)

  const sections: Array<{ title: string; lines: string[]; preview: string }> = []
  let currentTitle = ''
  let currentLines: string[] = []

  const pushSection = () => {
    if (!currentTitle) return
    const body = currentLines.join('\n').trim()
    const preview = cleanPreviewText(body).slice(0, 110)
    sections.push({ title: currentTitle, lines: currentLines.slice(), preview })
  }

  for (const raw of lines) {
    const line = raw.trim()
    if (/^##\s*/.test(line)) {
      pushSection()
      currentTitle = line.replace(/^##\s*/, '').trim()
      currentLines = []
      continue
    }
    currentLines.push(raw)
  }
  pushSection()

  if (!sections.length) {
    return [{
      id: 'report-section-1',
      title: '完整诊断',
      html: renderMarkdown(source),
      preview: cleanPreviewText(source).slice(0, 110),
    }]
  }

  return sections.map((section, index) => ({
    id: `report-section-${index + 1}`,
    title: section.title,
    html: renderMarkdown(section.lines.join('\n')),
    preview: section.preview,
  }))
})

const reportAnchors = computed(() => [
  { id: 'report-overview', label: '关键结论' },
  ...reportSections.value.map(section => ({ id: section.id, label: section.title })),
  { id: 'appendix', label: '附录补充' },
  { id: 'volunteer-panel', label: '完整志愿清单' },
  { id: 'zxf-skills', label: 'skills 服务' },
])

const skillSuggestions = computed(() => [
  '按就业优先，帮我筛掉最不值得保留的冲档项',
  '这些志愿里哪些更值得保专业，哪些更值得保学校',
  '结合扩招指数和院校招生指数，重排前15个志愿',
])

const executiveSummary = computed(() => {
  const firstSection = reportSections.value[0]
  if (firstSection?.preview) return firstSection.preview
  return 'AI 会先给出一句话结论，再展开诊断、重排建议和执行清单。'
})

const actionList = computed(() => {
  const actions: Array<{ title: string; desc: string; tone?: 'warn' | 'normal' }> = []
  const firstRisk = riskList.value[0]
  const firstKeep = keepList.value[0]
  if (firstKeep) {
    actions.push({
      title: '优先保留主锚点',
      desc: `${firstKeep.universityName} · ${firstKeep.majorName} 适合作为当前方案里的核心保留项，优先放在更靠前的位置。`,
    })
  }
  if (firstRisk) {
    actions.push({
      title: '先处理最高风险项',
      desc: `${firstRisk.universityName} · ${firstRisk.majorName} 需要优先核对章程、位次波动和专业限制，再决定是否保留。`,
      tone: 'warn',
    })
  }
  actions.push({
    title: '按梯度重新排位',
    desc: '先把“稳”和“保”的核心项排顺，再决定哪些“冲”项值得留在前段，避免整体顺序失真。',
  })
  actions.push({
    title: '优先看专业级数据',
    desc: '如果两项接近，优先保留专业级且高可信的志愿，再把院校级回退数据放到后段做备选。',
  })
  return actions
})

async function startStream() {
  if (!volunteerStore.planId || !volunteerStore.planSafetyCode) {
    content.value = '缺少方案信息，暂时无法生成 AI 深度解读。'
    isDone.value = true
    isStreaming.value = false
    return
  }

  if (eventSource) {
    eventSource.close()
    eventSource = null
  }

  content.value = ''
  isStreaming.value = true
  isDone.value = false

  try {
    const res = await generateAiAnalysis(volunteerStore.planId, volunteerStore.planSafetyCode, true)
    const data = res.data.data
    content.value = analysisToMarkdown(data)
    aiMeta.value = {
      modelVersion: data.aiModelVersion,
      fallback: data.aiFallbackUsed === true,
      fallbackReason: data.aiFallbackReason,
      issues: data.dataIssues || [],
    }
    isStreaming.value = false
    isDone.value = true
    volunteerStore.setAiContent(content.value)
    return
  } catch {
    // 结构化接口不可用时保留旧 SSE 兼容链路。
    aiMeta.value = null
  }

  let url = ''
  try {
    const ticketRes = await createAiAnalysisTicket(volunteerStore.planId, volunteerStore.planSafetyCode, modeProfile.value)
    url = getAiAnalysisUrl(ticketRes.data.data.ticket)
  } catch (error: any) {
    content.value = error?.message || 'AI 分析凭证获取失败，请稍后重试。'
    isStreaming.value = false
    isDone.value = true
    return
  }

  eventSource = new EventSource(url)

  eventSource.onmessage = (event) => {
    const data = event.data
    if (data === '[DONE]') {
      isStreaming.value = false
      isDone.value = true
      volunteerStore.setAiContent(content.value)
      eventSource?.close()
      return
    }
    if (data.startsWith('[ERROR]')) {
      content.value += `\n\n${data}`
      isStreaming.value = false
      isDone.value = true
      eventSource?.close()
      return
    }
    content.value += data
    nextTick(() => {
      if (contentRef.value) {
        contentRef.value.scrollTop = contentRef.value.scrollHeight
      }
    })
  }

  eventSource.onerror = () => {
    if (isStreaming.value && !content.value) {
      content.value = 'AI 分析服务暂时不可用，请稍后重试。'
    }
    isStreaming.value = false
    isDone.value = true
    eventSource?.close()
  }
}

function analysisToMarkdown(analysis: AiAnalysisResponse) {
  const lines: string[] = []
  lines.push(`> ${AI_GENERATED_NOTICE}`)
  lines.push('')
  lines.push('## 一句话总判断')
  lines.push(analysis.conclusion || '当前方案已完成基础诊断，请继续复核风险项和官方章程。')
  lines.push('')
  if (analysis.diagnosisSections?.length) {
    for (const section of analysis.diagnosisSections) {
      lines.push(`## ${section.title}`)
      lines.push(section.content)
      lines.push('')
    }
  }
  if (analysis.topKeepDirections?.length) {
    lines.push('## 最值得保留的 5 个方向')
    analysis.topKeepDirections.forEach(item => lines.push(`- ${item}`))
    lines.push('')
  }
  if (analysis.topRiskPoints?.length) {
    lines.push('## 最需要警惕的 3 个风险点')
    analysis.topRiskPoints.forEach(item => lines.push(`- ${item}`))
    lines.push('')
  }
  if (analysis.actionSteps?.length) {
    lines.push('## 下一步执行清单')
    analysis.actionSteps.forEach(item => lines.push(`- **${item.title}**：${item.content}`))
    lines.push('')
  }
  if (analysis.reorderAdvice?.length) {
    lines.push('## 排列式重排建议')
    analysis.reorderAdvice.forEach(item => lines.push(`- ${item}`))
    lines.push('')
  }
  lines.push('## 免责声明')
  lines.push(analysis.disclaimer || volunteerStore.referenceProbabilityNotice)
  return lines.join('\n')
}

onMounted(async () => {
  if (!volunteerStore.planItems.length) {
    await restorePlan()
  }
  if (!volunteerStore.planItems.length) {
    router.push('/volunteer')
    return
  }
  if (volunteerStore.aiContent) {
    content.value = volunteerStore.aiContent
    isDone.value = true
  } else {
    await startStream()
  }
  // 登录态用户：跨设备恢复本方案的 skills 历史问答（B3）
  await restoreSkillsHistory()
  window.addEventListener('scroll', onPageScroll, { passive: true })
  nextTick(() => updateActiveSection())
})

async function restoreSkillsHistory() {
  if (!authStore.isAuthenticated || !volunteerStore.planId) return
  if (skillMessages.value.length) return
  try {
    const res = await fetchMyPlanSkillsHistory(volunteerStore.planId)
    const items = res.data.data || []
    if (!items.length) return
    const restored: ZxfSkillChatMessage[] = []
    for (const it of items) {
      if (it.question) restored.push({ role: 'user', content: it.question })
      if (it.answer) restored.push({ role: 'assistant', content: it.answer })
    }
    if (restored.length) {
      skillMessages.value = restored
    }
  } catch {
    // 历史拉取失败不影响主流程
  }
}

async function restorePlan() {
  const saved = volunteerStore.getSavedPlanMeta()
  const planId = Number(route.query.planId || saved?.planId)
  if (!planId) return
  let safetyCode = String(route.query.safetyCode || route.query.accessKey || saved?.safetyCode || saved?.accessKey || '')
  if (!safetyCode && authStore.isAuthenticated) {
    try {
      const detailRes = await fetchMyPlanDetail(planId)
      const detail = detailRes.data.data as VolunteerPlan
      if (detail.items?.length) {
        volunteerStore.setPlanFromResponse(detail)
        volunteerStore.setFormData(formDataFromPlan(detail))
        return
      }
      safetyCode = detail.safetyCode || detail.accessKey || ''
    } catch {
      safetyCode = ''
    }
  }
  if (!safetyCode) return
  try {
    const res = await fetchVolunteerPlan(planId, safetyCode)
    const plan = res.data.data
    volunteerStore.setPlanFromResponse(plan)
    volunteerStore.setFormData(formDataFromPlan(plan))
    if (route.query.safetyCode || route.query.accessKey) {
      router.replace({ path: route.path, query: { ...route.query, safetyCode: undefined, accessKey: undefined } })
    }
  } catch {
    volunteerStore.clearPlan()
  }
}

onUnmounted(() => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  window.removeEventListener('scroll', onPageScroll)
})

function scrollToSection(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function isSectionCollapsed(id: string) {
  return collapsedSections.value.has(id)
}

function toggleSection(id: string) {
  const next = new Set(collapsedSections.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  collapsedSections.value = next
}

function getSectionBadge(section: ReportSection) {
  const text = `${section.title} ${section.preview}`
  if (/风险|警惕|限制|退档|不匹配|冲/.test(text)) return '风险复核'
  if (/排序|重排|梯度|顺序/.test(text)) return '排序重点'
  if (/保留|建议|执行|下一步/.test(text)) return '执行建议'
  return '重点阅读'
}

function updateActiveSection() {
  let current = 'headline'
  for (const anchor of reportAnchors.value) {
    const el = document.getElementById(anchor.id)
    if (!el) continue
    const rect = el.getBoundingClientRect()
    if (rect.top <= 180) {
      current = anchor.id
    }
  }
  activeSection.value = current

  const doc = document.documentElement
  const maxScroll = Math.max(doc.scrollHeight - window.innerHeight, 1)
  scrollProgress.value = Math.min(Math.max(window.scrollY / maxScroll, 0), 1)
}

function onPageScroll() {
  updateActiveSection()
}

watch(content, () => {
  nextTick(() => updateActiveSection())
})

function fillSkillSuggestion(text: string) {
  skillInput.value = text
}

function formatSkillError(error: unknown) {
  const message = error instanceof Error ? error.message : ''
  if (/timeout|超时|时间较长/i.test(message)) {
    return 'AI 咨询还在处理但已超过等待时间，请稍后重试；系统已保留你的问题。'
  }
  return message || '咨询失败，请稍后再试。'
}

async function sendSkillMessage() {
  const message = skillInput.value.trim()
  if (!message || skillLoading.value) return
  if (!volunteerStore.planId || !volunteerStore.planSafetyCode) {
    skillError.value = '缺少当前志愿方案，暂时无法咨询。'
    return
  }

  skillError.value = ''
  skillInput.value = ''
  skillMessages.value = [...skillMessages.value, { role: 'user', content: message }]
  skillLoading.value = true

  try {
    const res = await chatZxfSkill({
      planId: volunteerStore.planId,
      safetyCode: volunteerStore.planSafetyCode,
      message,
      aiReport: content.value,
      messages: skillMessages.value.slice(-8),
    })
    const reply = res.data.data.answer || res.data.data.reply || '暂时没有生成有效回复，请稍后再试。'
    skillSourceChunks.value = res.data.data.sourceChunks || []
    skillReferencedVolunteers.value = res.data.data.referencedVolunteers || []
    skillMessages.value = [...skillMessages.value, { role: 'assistant', content: reply }]
  } catch (error: unknown) {
    skillError.value = formatSkillError(error)
    skillMessages.value = skillMessages.value.filter(item => !(item.role === 'user' && item.content === message))
    skillInput.value = message
  } finally {
    skillLoading.value = false
  }
}

async function exportLongImage() {
  if (!volunteerStore.planId || !volunteerStore.planSafetyCode) {
    router.push('/poster')
    return
  }
  try {
    const res = await exportPlanLongImage(volunteerStore.planId, volunteerStore.planSafetyCode)
    downloadBlob(res.data, 'gzly-volunteer-plan.png')
  } catch {
    router.push('/poster')
  }
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="ai-page">
    <AiAnalysisHeader :progress="scrollProgress" @back="router.back()" />

    <div class="ai-warning">
      <div class="ai-warning-inner">
        <AlertTriangle :size="14" />
        <span>AI 结论只作决策辅助，请以招生章程、专业目录和官方投档规则为准。</span>
      </div>
    </div>

    <div class="ai-layout ai-layout--report">
      <AiAnalysisSidebar
        :anchors="reportAnchors"
        :active-section="activeSection"
        :progress="scrollProgress"
        @scroll-to="scrollToSection"
      />

      <div class="report-main">
        <section class="report-hero">
          <div class="report-hero__eyebrow">
            <ShieldCheck :size="16" />
            志愿方案复盘报告
          </div>
          <div class="report-hero__content">
            <div>
              <h2>先抓结论，再拆风险</h2>
              <p>把分析内容整理成可阅读的诊断报告，重点看保留项、风险项、梯度顺序和官方规则核验。</p>
            </div>
            <div class="report-hero__badges">
              <span>官方章程优先</span>
              <span>专业级数据优先</span>
              <span>风险先处理</span>
            </div>
          </div>
        </section>

        <section class="ai-disclaimer-card">
          <strong>机会指数口径与免责声明</strong>
          <p>{{ AI_GENERATED_NOTICE }}</p>
          <p>{{ volunteerStore.referenceProbabilityNotice }}</p>
        </section>

        <AiAnalysisMetaBadge
          v-if="aiMeta"
          :model-version="aiMeta.modelVersion"
          :fallback="aiMeta.fallback"
          :fallback-reason="aiMeta.fallbackReason"
          :issues="aiMeta.issues"
        />

        <section
          v-if="volunteerStore.manualReviewItems && volunteerStore.manualReviewItems.length"
          class="ai-manual-review"
        >
          <header>
            <h3>强制人工复核清单</h3>
            <span>{{ volunteerStore.manualReviewItems.length }} 条</span>
          </header>
          <ul>
            <li v-for="entry in volunteerStore.manualReviewItems" :key="`ai-mr-${entry.index}`">
              <div class="ai-manual-review__title">
                <span class="ai-manual-review__index">{{ entry.index }}</span>
                {{ entry.universityName }} · {{ entry.majorName }}
                <span class="ai-manual-review__gradient">{{ entry.gradient }}</span>
              </div>
              <ul class="ai-manual-review__reasons">
                <li v-for="(reason, ri) in entry.reasons" :key="`ai-mr-${entry.index}-r-${ri}`">{{ reason }}</li>
              </ul>
            </li>
          </ul>
        </section>

        <ConclusionSection :summary="executiveSummary" :metrics="overviewMetrics" />

        <section class="headline-strip headline-strip--compact">
          <div
            v-for="item in headlineHighlights.slice(0, 2)"
            :key="item.title"
            class="headline-card"
            :class="{ 'headline-card--warn': item.tone === 'warn' }"
          >
            <span class="headline-card__label">{{ item.title }}</span>
            <strong class="headline-card__value">{{ item.value }}</strong>
            <p class="headline-card__note">{{ item.note }}</p>
          </div>
        </section>

        <DiagnosisSection :streaming="isStreaming">
            <div class="ai-bubble gz-card">
              <div class="ai-bubble__title">逐条诊断正文</div>
              <div class="report-sections">
                <section
                  v-for="(section, index) in reportSections"
                  :id="section.id"
                  :key="section.id"
                  class="report-section"
                >
                  <div class="report-section__head">
                    <span class="report-section__index">{{ String(index + 1).padStart(2, '0') }}</span>
                    <div class="report-section__copy">
                      <div class="report-section__title-row">
                        <h3 class="report-section__title">{{ section.title }}</h3>
                        <span class="report-section__badge">{{ getSectionBadge(section) }}</span>
                      </div>
                      <p v-if="section.preview" class="report-section__preview">{{ section.preview }}</p>
                    </div>
                    <button
                      class="report-section__toggle"
                      :aria-expanded="!isSectionCollapsed(section.id)"
                      @click="toggleSection(section.id)"
                    >
                      <ChevronDown :size="16" :class="{ rotated: isSectionCollapsed(section.id) }" />
                      {{ isSectionCollapsed(section.id) ? '展开' : '收起' }}
                    </button>
                  </div>
                  <div
                    v-show="!isSectionCollapsed(section.id)"
                    class="markdown-body report-section__body"
                    v-html="section.html"
                  ></div>
                </section>
              </div>
              <span v-if="isStreaming" class="typewriter-cursor"></span>
            </div>
        </DiagnosisSection>

        <section id="appendix" class="digest-grid digest-grid--after">
          <div class="digest-card">
          <h3>最值得保留的 5 个方向</h3>
          <div class="digest-list">
            <div v-for="item in keepList" :key="item.index" class="digest-item">
              <strong>{{ item.universityName }}</strong>
              <span>{{ item.majorName }}</span>
            </div>
          </div>
          </div>
          <div id="risk-list" class="digest-card digest-card--warn">
          <h3>最需要警惕的 3 个风险点</h3>
          <div class="digest-list">
            <div v-for="item in riskList" :key="item.index" class="digest-item">
              <strong>{{ item.universityName }}</strong>
              <span>{{ item.riskReason || '建议重点复核位次波动、招生章程和专业要求。' }}</span>
            </div>
          </div>
          </div>
        </section>

        <ActionStepsSection :actions="actionList" />

        <VolunteerListPanel
          :items="volunteerStore.planItems"
          :plan-id="volunteerStore.planId"
          :safety-code="volunteerStore.planSafetyCode"
        />

        <SkillsServiceCard @show-sources="skillSourceOpen = true">
          <SkillsChatBox
            v-model="skillInput"
            :suggestions="skillSuggestions"
            :messages="skillMessages"
            :loading="skillLoading"
            :error="skillError"
            :render="renderMarkdown"
            @pick="fillSkillSuggestion"
            @send="sendSkillMessage"
          />
        </SkillsServiceCard>
      </div>
    </div>

    <div v-if="skillSourceOpen" class="source-modal" role="dialog" aria-modal="true">
      <div class="source-modal__panel gz-card">
        <header>
          <h3>来源与上下文</h3>
          <button type="button" @click="skillSourceOpen = false">关闭</button>
        </header>
        <section>
          <strong>当前方案引用志愿</strong>
          <p v-if="!skillReferencedVolunteers.length">暂无引用记录，提问后会展示相关志愿。</p>
          <ul v-else>
            <li v-for="(item, index) in skillReferencedVolunteers" :key="`rv-${index}`">
              {{ item.index }} · {{ item.universityName }} · {{ item.majorName }} · 机会指数 {{ item.chanceScore }}
            </li>
          </ul>
        </section>
        <section>
          <strong>skills 检索片段</strong>
          <p v-if="!skillSourceChunks.length">当前回答未命中可展示片段。</p>
          <ul v-else>
            <li v-for="(chunk, index) in skillSourceChunks" :key="`sc-${index}`">
              {{ String(chunk.text || '').slice(0, 180) }}
            </li>
          </ul>
        </section>
      </div>
    </div>

    <FloatingExportBar :streaming="isStreaming" @export-image="exportLongImage" @reanalyze="startStream" />
  </div>
</template>

<style scoped>
.ai-page {
  min-height: 100dvh;
  padding-bottom: 92px;
  background: #f4f0e8;
  color: #1f2933;
}

.page-header {
  position: sticky;
  top: 0;
  z-index: var(--gz-z-sticky);
  background: rgba(250, 248, 242, 0.94);
  border-bottom: 1px solid rgba(31, 41, 51, 0.1);
  backdrop-filter: blur(10px);
}

.page-header-inner {
  max-width: var(--gz-desktop-max);
  margin: 0 auto;
  padding: var(--gz-space-3) var(--gz-space-4);
  display: flex;
  align-items: center;
  gap: var(--gz-space-3);
}

.reading-progress {
  height: 2px;
  background: rgba(31, 41, 51, 0.08);
}

.reading-progress span {
  display: block;
  height: 100%;
  width: 0;
  background: #1f2933;
  transition: width 0.16s ease-out;
}

.back-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 999px;
  background: #ede7db;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #1f2933;
  box-shadow: inset 0 0 0 1px rgba(31, 41, 51, 0.08);
}

.page-header-title {
  font-size: 18px;
  font-weight: 800;
  color: #1f2933;
}

.ai-warning {
  background: #fbf7ed;
  border-bottom: 1px solid rgba(146, 64, 14, 0.16);
}

.ai-warning-inner {
  max-width: var(--gz-desktop-max);
  margin: 0 auto;
  padding: 8px var(--gz-space-4);
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--gz-warning-text);
}

.ai-layout {
  max-width: 1180px;
  margin: 0 auto;
  padding: 18px 16px 0;
}

.ai-layout--report {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.report-main {
  min-width: 0;
}

.report-nav {
  position: sticky;
  top: 92px;
  align-self: flex-start;
  max-height: calc(100dvh - 116px);
  overflow: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.1);
  box-shadow: 0 10px 28px rgba(31, 41, 51, 0.06);
}

.report-nav__title {
  font-size: 15px;
  font-weight: 900;
  color: #1f2933;
}

.report-nav__desc {
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.report-nav__progress {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 0 6px;
  padding: 8px 10px;
  border-radius: 12px;
  background: #f4f0e8;
  border: 1px solid rgba(31, 41, 51, 0.08);
}

.report-nav__progress span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.report-nav__progress strong {
  color: #1f2933;
  font-size: 14px;
  font-weight: 900;
}

.report-nav__item {
  position: relative;
  min-height: 34px;
  padding: 0 12px 0 18px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  text-align: left;
  line-height: 1.35;
  cursor: pointer;
  transition: all 0.18s ease;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.report-nav__item::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 50%;
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: #cbd5e1;
  transform: translateY(-50%);
}

.report-nav__item:hover {
  background: #f7f2e8;
  color: #1f2933;
}

.report-nav__item.active {
  background: #efe7d8;
  border-color: rgba(31, 41, 51, 0.12);
  color: #1f2933;
  box-shadow: none;
}

.report-nav__item.active::before {
  height: 18px;
  background: #1f2933;
}

.summary-grid,
.digest-grid,
.headline-strip {
  display: grid;
  gap: 10px;
}

.report-hero {
  position: relative;
  overflow: hidden;
  margin-bottom: 12px;
  padding: 20px 22px;
  border-radius: 14px;
  background: #1f2933;
  box-shadow: 0 14px 34px rgba(31, 41, 51, 0.16);
  color: #fffdf7;
}

.report-hero::after {
  content: '';
  position: absolute;
  right: -80px;
  bottom: -120px;
  width: 280px;
  height: 280px;
  border-radius: 999px;
  background: rgba(255, 253, 247, 0.04);
}

.report-hero__eyebrow {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(255, 253, 247, 0.08);
  border: 1px solid rgba(255, 253, 247, 0.18);
  color: #efe7d8;
  font-size: 12px;
  font-weight: 800;
}

.report-hero__content {
  position: relative;
  z-index: 1;
  margin-top: 12px;
  display: grid;
  gap: 12px;
}

.report-hero h2 {
  max-width: 680px;
  font-size: clamp(24px, 4vw, 36px);
  line-height: 1.12;
  font-weight: 950;
  letter-spacing: -0.04em;
}

.report-hero p {
  max-width: 700px;
  margin-top: 8px;
  color: #ded7c9;
  font-size: 14px;
  line-height: 1.65;
}

.report-hero__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.report-hero__badges span {
  padding: 7px 10px;
  border-radius: 999px;
  background: transparent;
  border: 1px solid rgba(255, 253, 247, 0.22);
  color: #fffdf7;
  font-size: 12px;
  font-weight: 800;
}

.summary-card,
.digest-card {
  padding: 16px;
  border-radius: 12px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.1);
  box-shadow: 0 8px 24px rgba(31, 41, 51, 0.05);
}

.summary-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 800;
  color: #1f2933;
}

.summary-card p,
.digest-item span {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.62;
  color: #475569;
}

.report-overview__meta {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.report-overview__chip {
  display: inline-flex;
  flex-direction: column;
  gap: 4px;
  min-width: 180px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f7f2e8;
  border: 1px solid rgba(31, 41, 51, 0.08);
}

.report-overview__chip strong {
  font-size: 12px;
  color: #64748b;
}

.report-overview__chip span {
  font-size: 14px;
  line-height: 1.55;
  color: #1f2933;
  font-weight: 700;
}

.digest-card h3 {
  font-size: 15px;
  font-weight: 800;
  color: #1f2933;
}

.digest-card--warn {
  background: #fffaf0;
}

.digest-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.digest-item {
  padding: 10px 12px;
  border-radius: 12px;
  background: #f7f2e8;
  border: 1px solid rgba(31, 41, 51, 0.08);
}

.digest-item strong {
  font-size: 14px;
  color: #1f2933;
}

.action-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 800;
  color: #1f2933;
}

.headline-card {
  position: relative;
  overflow: hidden;
  padding: 16px;
  border-radius: 12px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.1);
  box-shadow: 0 8px 24px rgba(31, 41, 51, 0.05);
}

.headline-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: #1f2933;
}

.headline-card--warn {
  background: #fffaf0;
}

.headline-card--warn::before {
  background: #92400e;
}

.headline-card__label {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.headline-card__value {
  display: block;
  margin-top: 6px;
  font-size: 18px;
  line-height: 1.35;
  font-weight: 900;
  color: #1f2933;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.headline-card__note {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.62;
  color: #475569;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.action-card {
  margin-top: 12px;
  padding: 16px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.1);
  box-shadow: 0 8px 24px rgba(31, 41, 51, 0.05);
}

.action-list {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}

.action-item {
  padding: 11px 12px;
  border-radius: 12px;
  background: #f7f2e8;
  border: 1px solid rgba(31, 41, 51, 0.08);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.action-item--warn {
  background: #fff7ed;
}

.action-item strong {
  font-size: 14px;
  color: #1f2933;
}

.action-item span {
  font-size: 13px;
  line-height: 1.62;
  color: #475569;
}

.zxf-skills-card {
  margin-top: 12px;
  padding: 18px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.1);
  box-shadow: 0 10px 28px rgba(31, 41, 51, 0.06);
}

.zxf-skills-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.zxf-skills-card__eyebrow {
  display: block;
  font-size: 12px;
  font-weight: 900;
  color: #5f4630;
}

.zxf-skills-card h3 {
  margin-top: 4px;
  font-size: 20px;
  line-height: 1.25;
  font-weight: 950;
  color: #1f2933;
}

.zxf-skills-card__head p,
.zxf-skills-card__note {
  margin-top: 7px;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.zxf-skills-card__source {
  min-height: 36px;
  padding: 0 11px;
  border-radius: 999px;
  border: 1px solid rgba(31, 41, 51, 0.12);
  background: #f7f2e8;
  color: #1f2933;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 900;
  text-decoration: none;
  white-space: nowrap;
}

.zxf-suggestions {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.zxf-suggestions button {
  min-height: 36px;
  padding: 7px 11px;
  border: 1px solid rgba(31, 41, 51, 0.1);
  border-radius: 999px;
  background: #f7f2e8;
  color: #374151;
  font-size: 12px;
  line-height: 1.35;
  font-weight: 800;
  cursor: pointer;
}

.zxf-chat-window {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 520px;
  overflow: auto;
  padding: 12px;
  border-radius: 14px;
  background: #f7f2e8;
  border: 1px solid rgba(31, 41, 51, 0.08);
}

.zxf-chat-empty,
.zxf-chat-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 12px;
  background: #fffdf7;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.zxf-chat-message {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 8px;
  align-items: flex-start;
}

.zxf-chat-message__role {
  min-height: 28px;
  padding: 5px 8px;
  border-radius: 999px;
  background: #fffdf7;
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
  text-align: center;
}

.zxf-chat-message__body {
  min-width: 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.08);
  box-shadow: 0 4px 14px rgba(31, 41, 51, 0.04);
}

.zxf-chat-message.is-user .zxf-chat-message__role {
  background: #1f2933;
  color: #fffdf7;
}

.zxf-chat-message.is-user .zxf-chat-message__body {
  background: #fffaf0;
}

.zxf-chat-form {
  margin-top: 12px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 48px;
  gap: 10px;
  align-items: stretch;
}

.zxf-chat-form textarea {
  width: 100%;
  min-height: 52px;
  max-height: 140px;
  resize: vertical;
  border: 1px solid rgba(31, 41, 51, 0.14);
  border-radius: 14px;
  padding: 11px 12px;
  background: #fffdf7;
  color: #1f2933;
  font-size: 14px;
  line-height: 1.55;
  outline: none;
}

.zxf-chat-form textarea:focus {
  border-color: rgba(31, 41, 51, 0.32);
  box-shadow: 0 0 0 3px rgba(31, 41, 51, 0.08);
}

.zxf-chat-form button {
  min-width: 48px;
  min-height: 52px;
  border: none;
  border-radius: 14px;
  background: #1f2933;
  color: #fffdf7;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.zxf-chat-form button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.zxf-chat-error {
  margin-top: 10px;
  padding: 9px 11px;
  border-radius: 12px;
  background: #fff7ed;
  border: 1px solid #fcd9b6;
  color: #92400e;
  font-size: 12px;
  line-height: 1.6;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.ai-content-area {
  margin-top: 12px;
}

.chat-container {
  max-width: none;
  margin: 0 auto;
}

.ai-avatar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.ai-avatar {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #1f2933;
  color: #fffdf7;
}

.ai-label {
  display: block;
  font-size: 14px;
  font-weight: 800;
  color: #1f2933;
}

.ai-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot--active {
  background: #10b981;
}

.status-dot--done {
  background: #1f2933;
}

.ai-bubble {
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.ai-bubble__title {
  margin-bottom: 12px;
  padding-left: 4px;
  font-size: 15px;
  font-weight: 900;
  color: #1f2933;
}

.report-sections {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.report-section {
  position: relative;
  overflow: hidden;
  padding: 16px;
  border-radius: 12px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.1);
  box-shadow: 0 10px 26px rgba(31, 41, 51, 0.06);
}

.report-section::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  background: #1f2933;
}

.report-section__head {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.report-section__index {
  min-width: 34px;
  height: 34px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 900;
  color: #fffdf7;
  background: #1f2933;
  box-shadow: none;
}

.report-section__copy {
  min-width: 0;
  flex: 1;
}

.report-section__title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.report-section__title {
  font-size: 17px;
  line-height: 1.35;
  font-weight: 900;
  color: #1f2933;
}

.report-section__badge {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: #efe7d8;
  color: #5f4630;
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

.report-section__toggle {
  min-width: 64px;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(31, 41, 51, 0.12);
  border-radius: 999px;
  background: #fffaf0;
  color: #1f2933;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.report-section__toggle svg {
  transition: transform 0.18s ease;
}

.report-section__toggle svg.rotated {
  transform: rotate(-90deg);
}

.report-section__preview {
  display: none;
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.55;
  color: #64748b;
}

.report-section__body {
  margin-top: 10px;
}

.markdown-body {
  font-size: 14px;
  line-height: 1.72;
  color: #2f3a44;
}

.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 16px 0 8px;
  color: #1f2933;
}

.markdown-body :deep(h2) {
  font-size: 24px;
  line-height: 1.25;
  font-weight: 900;
}

.markdown-body :deep(h3) {
  font-size: 18px;
  line-height: 1.35;
  font-weight: 800;
}

.markdown-body :deep(h4) {
  font-size: 15px;
  line-height: 1.45;
  font-weight: 800;
}

.markdown-body :deep(p) {
  margin: 0 0 9px;
}

.markdown-body :deep(ol),
.markdown-body :deep(ul) {
  margin: 0 0 10px;
  padding-left: 22px;
}

.markdown-body :deep(li) {
  margin: 6px 0;
  padding-left: 2px;
}

.markdown-body :deep(blockquote) {
  margin: 12px 0;
  padding: 10px 12px;
  border-left: 4px solid #5f4630;
  background: #f7f2e8;
  color: #4b5563;
  border-radius: 0 16px 16px 0;
}

.markdown-body :deep(strong) {
  color: #1f2933;
}

.markdown-body :deep(hr) {
  height: 1px;
  margin: 16px 0;
  border: 0;
  background: rgba(31, 41, 51, 0.16);
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  padding: 12px 16px calc(env(safe-area-inset-bottom, 0px) + 12px);
  background: rgba(255, 253, 247, 0.94);
  backdrop-filter: blur(12px);
  border-top: 1px solid rgba(31, 41, 51, 0.1);
}

.action-btn {
  min-height: 46px;
  border-radius: 14px;
  border: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 800;
}

.action-btn--primary {
  background: #1f2933;
  color: #fffdf7;
}

.action-btn--outline {
  background: #ede7db;
  color: #1f2933;
}

@media (min-width: 1024px) {
  .ai-layout--report {
    display: grid;
    grid-template-columns: 220px minmax(0, 1fr);
    align-items: start;
    gap: 16px;
  }

  .summary-grid,
  .digest-grid,
  .headline-strip {
    grid-template-columns: 1fr 1fr;
  }

  .summary-card--wide {
    grid-column: 1 / -1;
  }

  .headline-strip--compact {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .report-overview__chip {
    min-width: 0;
    flex: 1 1 180px;
  }

  .action-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .bottom-bar {
    left: auto;
    right: 24px;
    bottom: 24px;
    width: 340px;
    border-radius: 14px;
    box-shadow: 0 12px 28px rgba(31, 41, 51, 0.12);
  }
}

@media (min-width: 1180px) {
  .report-hero__content {
    grid-template-columns: minmax(0, 1fr) 320px;
    align-items: end;
  }
}

@media (max-width: 767px) {
  .ai-layout {
    padding: 12px 10px 0;
  }

  .report-nav {
    position: relative;
    top: auto;
    max-height: none;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 6px;
    padding: 12px;
  }

  .report-nav__title,
  .report-nav__desc,
  .report-nav__progress {
    grid-column: 1 / -1;
  }

  .report-nav__item {
    min-height: 36px;
    padding-right: 10px;
    white-space: normal;
  }

  .report-hero,
  .summary-card,
  .headline-card,
  .report-section,
  .digest-card,
  .action-card,
  .zxf-skills-card {
    border-radius: 12px;
  }

  .zxf-skills-card {
    padding: 14px;
  }

  .zxf-skills-card__head {
    flex-direction: column;
  }

  .zxf-skills-card__source {
    align-self: flex-start;
  }

  .zxf-chat-message {
    grid-template-columns: 1fr;
  }

  .zxf-chat-message__role {
    width: fit-content;
  }

  .report-section {
    padding: 14px;
  }

  .report-section__head {
    position: relative;
    flex-wrap: wrap;
    gap: 10px;
  }

  .report-section__copy {
    flex-basis: calc(100% - 42px);
  }

  .report-section__toggle {
    margin-left: 42px;
  }

  .report-section__index {
    min-width: 32px;
    height: 32px;
  }

  .report-section__title {
    font-size: 16px;
  }

  .markdown-body {
    font-size: 14px;
  }
}

.ai-disclaimer-card {
  margin: 12px 0 16px;
  padding: 14px 18px;
  border-radius: 16px;
  background: #fff7ed;
  border: 1px solid #fcd9b6;
  color: #92400e;
  font-size: 12px;
  line-height: 1.7;
}

.ai-disclaimer-card strong {
  display: block;
  margin-bottom: 4px;
  font-size: 13px;
  color: #b45309;
}

.ai-manual-review {
  padding: 16px 18px;
  border-radius: 16px;
  background: #fffaf3;
  border: 1px solid #fcd9b6;
  margin-bottom: 16px;
}

.ai-manual-review header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.ai-manual-review header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #b45309;
}

.ai-manual-review header span {
  font-size: 12px;
  font-weight: 700;
  color: #b45309;
  background: #fff;
  border: 1px solid #fcd9b6;
  padding: 3px 10px;
  border-radius: 999px;
}

.ai-manual-review ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-manual-review > ul > li {
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #fcd9b6;
  padding: 10px 12px;
}

.ai-manual-review__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #1f2937;
  font-size: 13px;
}

.ai-manual-review__index {
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  background: #b45309;
  color: #fff;
  border-radius: 999px;
  font-size: 12px;
}

.ai-manual-review__gradient {
  margin-left: auto;
  font-size: 11px;
  color: #b45309;
}

.ai-manual-review__reasons {
  margin: 6px 0 0;
  padding-left: 18px;
  color: #92400e;
  font-size: 12px;
  line-height: 1.7;
}
</style>
