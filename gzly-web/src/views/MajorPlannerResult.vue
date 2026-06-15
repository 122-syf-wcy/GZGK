<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { ArrowLeft, Copy, RefreshCw, RotateCcw, ShieldAlert, Sparkles } from 'lucide-vue-next'
import {
  fetchMajorPlannerResult,
  generateMajorPlannerAiAnalysis,
  restoreMajorPlanner,
  type MajorPlannerView,
} from '@/api/majorPlanner'
import { loadMajorPlannerSession, saveMajorPlannerCode, saveMajorPlannerSession } from '@/utils/majorPlannerSession'
import { renderMarkdown } from '@/utils/markdown'

defineOptions({ name: 'MajorPlannerResult' })

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const aiLoading = ref(false)
const errorMsg = ref('')
const result = ref<MajorPlannerView | null>(null)
const planCode = ref('')
const restoreCode = ref('')
const restorePlanNo = ref('')

const resultId = computed(() => Number(route.query.id || 0))
const topMajors = computed(() => result.value?.result?.topMajors || [])
const radar = computed(() => result.value?.result?.radar || [])
const notRecommended = computed(() => result.value?.result?.notRecommended || [])
const profile = computed(() => result.value?.result?.profile)
const aiContent = computed(() => result.value?.aiSummary || '')

onMounted(loadResult)

async function loadResult(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  const id = resultId.value
  if (!id) {
    loading.value = false
    errorMsg.value = '缺少规划结果 id，请重新生成或使用规划码找回。'
    return
  }
  const cached = loadMajorPlannerSession(id)
  if (cached.result) {
    result.value = cached.result
  }
  if (cached.planCode) {
    planCode.value = cached.planCode
    try {
      const fresh = await fetchMajorPlannerResult(id, cached.planCode)
      result.value = fresh
      saveMajorPlannerSession(fresh, cached.planCode)
    } catch (error: unknown) {
      if (!result.value) errorMsg.value = error instanceof Error ? error.message : '读取规划结果失败'
    }
  } else if (!result.value) {
    errorMsg.value = '为保护隐私，请输入规划码找回该结果。'
  }
  loading.value = false
}

async function copyPlanCode(): Promise<void> {
  const code = result.value?.planCode || planCode.value
  if (!code) {
    showToast('当前页面没有完整规划码，请使用你保存的规划码')
    return
  }
  try {
    await navigator.clipboard.writeText(code)
    showSuccessToast('规划码已复制')
  } catch {
    showToast('复制失败，请手动记录规划码')
  }
}

async function restore(): Promise<void> {
  const code = restoreCode.value.trim()
  if (!code) {
    showToast('请输入规划码')
    return
  }
  loading.value = true
  try {
    const restored = await restoreMajorPlanner({ planNo: restorePlanNo.value.trim(), planCode: code })
    result.value = restored
    planCode.value = code
    saveMajorPlannerSession(restored, code)
    if (restored.id !== resultId.value) {
      router.replace({ path: '/major-planner/result', query: { id: restored.id } })
    }
    errorMsg.value = ''
  } catch (error: unknown) {
    showToast(error instanceof Error ? error.message : '找回失败，请检查规划码')
  } finally {
    loading.value = false
  }
}

async function runAi(forceRefresh = false): Promise<void> {
  if (!result.value?.id) return
  const code = planCode.value || result.value.planCode
  if (!code) {
    showToast('请先输入规划码，再生成 AI 深度解读')
    return
  }
  aiLoading.value = true
  try {
    const analysis = await generateMajorPlannerAiAnalysis(result.value.id, code, forceRefresh)
    result.value.aiSummary = analysis.content
    result.value.aiGenerated = analysis.generated
    result.value.aiFallbackReason = analysis.fallbackReason
    saveMajorPlannerCode(result.value.id, code)
    saveMajorPlannerSession(result.value, code)
    showSuccessToast(analysis.fallbackUsed ? 'AI 暂不可用，已展示规则兜底总结' : 'AI 解读已生成')
  } catch (error: unknown) {
    showToast(error instanceof Error ? error.message : 'AI 解读失败，请稍后重试')
  } finally {
    aiLoading.value = false
  }
}

function md(content: string): string {
  return renderMarkdown(content || '', { autoSectionHeadings: true })
}
</script>

<template>
  <div class="planner-result-page">
    <header class="result-topbar">
      <button type="button" class="ghost-link" @click="router.push('/major-planner')">
        <ArrowLeft :size="16" />
        重新规划
      </button>
      <button type="button" class="ghost-link" @click="router.push('/')">返回首页</button>
    </header>

    <main class="result-shell">
      <section v-if="loading" class="status-card">正在读取专业规划结果...</section>
      <section v-else-if="errorMsg" class="restore-panel">
        <h1>找回专业规划</h1>
        <p>{{ errorMsg }}</p>
        <div class="restore-panel__fields">
          <input v-model="restorePlanNo" type="text" autocomplete="off" placeholder="规划编号（可选）" />
          <input v-model="restoreCode" type="password" autocomplete="new-password" placeholder="规划码" />
          <button type="button" @click="restore">
            <RotateCcw :size="15" />
            找回结果
          </button>
        </div>
      </section>

      <template v-else-if="result">
        <section class="summary-grid">
          <div class="summary-card summary-card--main">
            <span class="kicker">Major Planner</span>
            <h1>专业选择规划结果</h1>
            <p>{{ result.result.ruleSummary }}</p>
            <div class="code-box">
              <div>
                <span>规划编号</span>
                <strong>{{ result.planNo }}</strong>
              </div>
              <div>
                <span>规划码</span>
                <strong>{{ result.planCode || result.planCodeMasked }}</strong>
              </div>
              <button type="button" @click="copyPlanCode">
                <Copy :size="15" />
                复制规划码
              </button>
            </div>
          </div>
          <div class="summary-card">
            <h2>个人画像</h2>
            <dl class="profile-list">
              <div><dt>选科/科类</dt><dd>{{ profile?.subjectCategory || '--' }}</dd></div>
              <div><dt>分数/位次</dt><dd>{{ result.score || '--' }} / {{ result.rank || '--' }}</dd></div>
              <div><dt>推荐主线</dt><dd>{{ profile?.mainLine || '--' }}</dd></div>
              <div><dt>备选方向</dt><dd>{{ profile?.backupLine || '--' }}</dd></div>
            </dl>
          </div>
        </section>

        <section class="notice-card">
          <ShieldAlert :size="18" />
          <p>{{ result.disclaimer }}</p>
        </section>

        <section class="content-grid">
          <div class="main-column">
            <section class="panel">
              <div class="panel-head">
                <div>
                  <span class="kicker">Radar</span>
                  <h2>专业大类匹配雷达</h2>
                </div>
              </div>
              <div class="radar-list">
                <div v-for="item in radar" :key="item.category" class="radar-row">
                  <span>{{ item.category }}</span>
                  <div class="radar-track"><i :style="{ width: `${item.score}%` }" /></div>
                  <strong>{{ item.score }}%</strong>
                </div>
              </div>
            </section>

            <section class="panel">
              <div class="panel-head">
                <div>
                  <span class="kicker">Top 10</span>
                  <h2>推荐专业方向</h2>
                </div>
              </div>
              <div class="major-list">
                <article v-for="(major, index) in topMajors" :key="major.majorName" class="major-card">
                  <div class="major-card__top">
                    <span class="major-card__rank">{{ index + 1 }}</span>
                    <div>
                      <h3>{{ major.category }}</h3>
                      <p>{{ major.majorName }}</p>
                    </div>
                    <strong>{{ major.matchScore }}%</strong>
                  </div>
                  <div class="major-card__body">
                    <div><b>适合原因</b><span>{{ major.reasons.join('；') }}</span></div>
                    <div><b>主要学习内容</b><span>{{ major.learningContent }}</span></div>
                    <div><b>适合人群</b><span>{{ major.suitableFor }}</span></div>
                    <div><b>就业方向</b><span>{{ major.employmentDirections }}</span></div>
                    <div><b>考研/考公</b><span>{{ major.postgraduateAndCivil }}</span></div>
                    <div><b>风险提醒</b><span>{{ major.risks.join('；') }}</span></div>
                    <div><b>报考建议</b><span>{{ major.advice }}</span></div>
                    <div><b>选科要求提示</b><span>{{ major.subjectRequirement }}</span></div>
                  </div>
                </article>
              </div>
            </section>

            <section class="panel">
              <div class="panel-head">
                <div>
                  <span class="kicker">Caution</span>
                  <h2>不建议优先选择的方向</h2>
                </div>
              </div>
              <div class="caution-list">
                <article v-for="item in notRecommended" :key="item.direction">
                  <h3>{{ item.direction }}</h3>
                  <p>{{ item.reason }}</p>
                  <span>{{ item.advice }}</span>
                </article>
              </div>
            </section>
          </div>

          <aside class="side-column">
            <section class="panel ai-panel">
              <div class="panel-head">
                <div>
                  <span class="kicker">AI</span>
                  <h2>AI 深度解读</h2>
                </div>
              </div>
              <p class="ai-panel__hint">AI 只解释当前专业规划结果，不生成正式志愿表，不承诺录取或就业。</p>
              <div class="ai-actions">
                <button type="button" :disabled="aiLoading" @click="runAi(false)">
                  <Sparkles :size="16" />
                  {{ aiLoading ? '生成中' : (aiContent ? '重新查看/补全' : '生成 AI 解读') }}
                </button>
                <button type="button" :disabled="aiLoading" @click="runAi(true)">
                  <RefreshCw :size="15" />
                  刷新解读
                </button>
              </div>
              <div v-if="aiContent" class="markdown-body" v-html="md(aiContent)" />
              <div v-else class="empty-ai">点击上方按钮生成“优势画像、推荐主线、备选方向、避坑清单和下一步查询方法”。</div>
            </section>
          </aside>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.planner-result-page {
  min-height: 100dvh;
  background: #f6f7f4;
  color: #172033;
}

.result-topbar {
  width: min(100% - 28px, 1180px);
  margin: 0 auto;
  padding: 18px 0 0;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.ghost-link {
  border: 0;
  background: transparent;
  color: #146c6f;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 850;
}

.result-shell {
  width: min(100% - 28px, 1180px);
  margin: 18px auto 56px;
}

.status-card,
.restore-panel,
.summary-card,
.notice-card,
.panel {
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 8px;
  background: #fffdfa;
  box-shadow: 0 12px 34px rgba(23, 32, 51, 0.08);
}

.status-card,
.restore-panel {
  padding: 24px;
}

.restore-panel p {
  color: #607086;
}

.restore-panel__fields {
  display: grid;
  grid-template-columns: 1fr 1fr 120px;
  gap: 10px;
}

.restore-panel input {
  height: 42px;
  border: 1px solid #d9e0ea;
  border-radius: 8px;
  padding: 0 12px;
}

.restore-panel button,
.code-box button,
.ai-actions button {
  min-height: 42px;
  border: 0;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-weight: 850;
  background: #146c6f;
  color: #fff;
}

.summary-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.65fr);
  gap: 14px;
}

.summary-card {
  padding: 22px;
}

.summary-card h1,
.summary-card h2,
.panel h2 {
  margin: 4px 0 0;
}

.summary-card--main p {
  color: #536172;
  line-height: 1.8;
}

.kicker {
  color: #146c6f;
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.code-box {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 10px;
  align-items: center;
  margin-top: 16px;
}

.code-box div {
  min-width: 0;
  padding: 12px;
  border-radius: 8px;
  background: #f1f5f7;
}

.code-box span,
.profile-list dt {
  display: block;
  color: #6c7a89;
  font-size: 12px;
  font-weight: 800;
}

.code-box strong {
  display: block;
  margin-top: 4px;
  word-break: break-all;
}

.profile-list {
  display: grid;
  gap: 12px;
  margin: 14px 0 0;
}

.profile-list div {
  display: grid;
  gap: 4px;
}

.profile-list dd {
  margin: 0;
  font-weight: 850;
}

.notice-card {
  display: flex;
  gap: 12px;
  margin-top: 14px;
  padding: 14px 16px;
  color: #5b4b35;
  line-height: 1.7;
}

.notice-card p {
  margin: 0;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 14px;
  margin-top: 14px;
  align-items: start;
}

.main-column,
.side-column {
  display: grid;
  gap: 14px;
}

.side-column {
  position: sticky;
  top: 16px;
}

.panel {
  padding: 20px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.radar-list {
  display: grid;
  gap: 12px;
}

.radar-row {
  display: grid;
  grid-template-columns: 120px 1fr 48px;
  gap: 10px;
  align-items: center;
  font-weight: 850;
}

.radar-track {
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf1f5;
}

.radar-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #146c6f, #c07a2a);
}

.major-list {
  display: grid;
  gap: 12px;
}

.major-card {
  border: 1px solid #e1e7ef;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.major-card__top {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 56px;
  gap: 12px;
  align-items: center;
  padding: 14px;
  background: #f7faf9;
}

.major-card__rank {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #146c6f;
  color: #fff;
  font-weight: 900;
}

.major-card h3,
.major-card p {
  margin: 0;
}

.major-card p {
  margin-top: 4px;
  color: #607086;
}

.major-card__body {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 14px;
}

.major-card__body div {
  display: grid;
  gap: 5px;
}

.major-card__body b {
  color: #26364a;
}

.major-card__body span,
.caution-list p,
.caution-list span,
.ai-panel__hint,
.empty-ai {
  color: #607086;
  line-height: 1.7;
}

.caution-list {
  display: grid;
  gap: 10px;
}

.caution-list article {
  padding: 14px;
  border-radius: 8px;
  background: #fff7ec;
  border: 1px solid #f3ddbd;
}

.caution-list h3,
.caution-list p {
  margin: 0;
}

.caution-list p {
  margin-top: 6px;
}

.ai-actions {
  display: grid;
  grid-template-columns: 1fr 118px;
  gap: 10px;
  margin: 14px 0;
}

.ai-actions button:nth-child(2) {
  background: #edf1f5;
  color: #334155;
}

.markdown-body {
  max-height: 58vh;
  overflow: auto;
  padding-right: 4px;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 16px 0 8px;
  color: #172033;
}

.markdown-body :deep(p),
.markdown-body :deep(li) {
  line-height: 1.8;
  color: #405064;
}

.markdown-body :deep(blockquote) {
  margin: 12px 0;
  padding: 10px 12px;
  border-left: 3px solid #146c6f;
  background: #f1f8f7;
  color: #39505c;
}

button:disabled {
  opacity: 0.6;
}

@media (max-width: 920px) {
  .summary-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .side-column {
    position: static;
  }

  .markdown-body {
    max-height: none;
  }
}

@media (max-width: 640px) {
  .result-shell,
  .result-topbar {
    width: min(100% - 20px, 1180px);
  }

  .summary-card,
  .panel,
  .restore-panel {
    padding: 16px;
  }

  .code-box,
  .restore-panel__fields,
  .major-card__body,
  .ai-actions {
    grid-template-columns: 1fr;
  }

  .radar-row {
    grid-template-columns: 86px 1fr 44px;
    font-size: 13px;
  }
}
</style>
