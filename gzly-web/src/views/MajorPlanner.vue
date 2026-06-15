<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { ArrowLeft, ArrowRight, Compass, RotateCcw, ShieldAlert, Sparkles } from 'lucide-vue-next'
import { evaluateMajorPlanner, restoreMajorPlanner, type MajorPlannerEvaluateRequest } from '@/api/majorPlanner'
import { PROVINCE_LIST } from '@/constants/provinces'
import { saveMajorPlannerSession } from '@/utils/majorPlannerSession'

defineOptions({ name: 'MajorPlanner' })

const router = useRouter()
const step = ref(0)
const submitting = ref(false)
const restoring = ref(false)
const restoreCode = ref('')
const restorePlanNo = ref('')

const steps = ['基础信息', '学科优势', '兴趣方向', '职业期待', '避雷偏好']
const subjectOptions = ['语文', '数学', '英语', '物理', '化学', '生物', '政治', '历史', '地理', '信息技术']
const subjectCategoryOptions = ['物理 + 化学 + 生物', '物理 + 化学 + 政治', '物理 + 化学 + 地理', '物理 + 生物 + 地理', '历史 + 政治 + 地理', '历史 + 政治 + 生物', '文科', '理科', '3+3 综合选科']
const interestOptions = ['技术', '医学', '教育', '财经', '法律', '语言', '艺术', '管理', '农业', '公安', '师范', '研究型']
const personalityOptions = ['喜欢和人打交道', '喜欢独立研究', '喜欢动手实践', '喜欢稳定工作', '喜欢创新挑战']
const careerOptions = ['高薪', '稳定', '考公', '考研', '就业快', '体制内', '出国', '创业']
const budgetOptions = ['低预算', '均衡预算', '不限制']

const form = ref<MajorPlannerEvaluateRequest>({
  provinceCode: '',
  subjectCategory: '',
  score: null,
  rank: null,
  likedSubjects: [],
  dislikedSubjects: [],
  interestDirections: [],
  personalityTraits: [],
  careerExpectations: [],
  acceptMedicine: null,
  acceptTeacher: null,
  acceptAgriculture: null,
  acceptSinoForeign: null,
  acceptPrivate: null,
  familyBudget: '均衡预算',
  cityPreferences: [],
  avoidDirections: [],
})

const progress = computed(() => Math.round(((step.value + 1) / steps.length) * 100))
const canSubmit = computed(() => !!form.value.subjectCategory && (
  form.value.likedSubjects.length > 0
  || form.value.interestDirections.length > 0
  || form.value.careerExpectations.length > 0
))

function toggle(list: string[], value: string, max = 8): void {
  const index = list.indexOf(value)
  if (index >= 0) {
    list.splice(index, 1)
    return
  }
  if (list.length >= max) {
    showToast(`最多选择 ${max} 项`)
    return
  }
  list.push(value)
}

function setBoolean(key: 'acceptMedicine' | 'acceptTeacher' | 'acceptAgriculture' | 'acceptSinoForeign' | 'acceptPrivate', value: boolean): void {
  form.value[key] = form.value[key] === value ? null : value
}

function parseNumber(value: unknown): number | null {
  const text = String(value ?? '').trim()
  if (!text) return null
  const num = Number(text)
  return Number.isFinite(num) ? Math.round(num) : null
}

function nextStep(): void {
  if (step.value === 0 && !form.value.subjectCategory) {
    showToast('请选择选科/科类')
    return
  }
  if (step.value < steps.length - 1) step.value += 1
}

function prevStep(): void {
  if (step.value > 0) step.value -= 1
}

async function submit(): Promise<void> {
  if (!canSubmit.value) {
    showToast('请至少填写选科，并选择学科优势、兴趣方向或职业期待中的一项')
    return
  }
  submitting.value = true
  try {
    const payload: MajorPlannerEvaluateRequest = {
      ...form.value,
      score: parseNumber(form.value.score),
      rank: parseNumber(form.value.rank),
      cityPreferences: form.value.cityPreferences.map(item => item.trim()).filter(Boolean),
      avoidDirections: form.value.avoidDirections.map(item => item.trim()).filter(Boolean),
    }
    const result = await evaluateMajorPlanner(payload)
    saveMajorPlannerSession(result, result.planCode)
    router.push({ path: '/major-planner/result', query: { id: result.id } })
  } catch (error: unknown) {
    showToast(error instanceof Error ? error.message : '生成规划失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

async function restore(): Promise<void> {
  const code = restoreCode.value.trim()
  if (!code) {
    showToast('请输入规划码')
    return
  }
  restoring.value = true
  try {
    const result = await restoreMajorPlanner({ planNo: restorePlanNo.value.trim(), planCode: code })
    saveMajorPlannerSession(result, code)
    router.push({ path: '/major-planner/result', query: { id: result.id } })
  } catch (error: unknown) {
    showToast(error instanceof Error ? error.message : '找回失败，请检查规划码')
  } finally {
    restoring.value = false
  }
}
</script>

<template>
  <div class="planner-page">
    <section class="planner-hero">
      <button class="ghost-link" type="button" @click="router.push('/')">
        <ArrowLeft :size="16" />
        返回首页
      </button>
      <div class="planner-hero__content">
        <span class="planner-hero__badge">
          <Compass :size="16" />
          公益专业方向参考
        </span>
        <h1>专业选择规划</h1>
        <p>从兴趣、学科优势和职业期待出发，找到更适合优先了解的专业方向。</p>
      </div>
    </section>

    <main class="planner-shell">
      <section class="notice-card">
        <ShieldAlert :size="18" />
        <p>本功能基于用户填写信息、专业特征规则和 AI 分析生成专业方向建议，仅供参考，不构成最终报考意见。请结合院校招生章程、选科要求、个人兴趣和家庭情况综合判断。</p>
      </section>

      <section class="restore-card">
        <div>
          <h2>已有规划码？</h2>
          <p>输入规划码即可找回历史规划结果；完整规划码只在创建时展示一次。</p>
        </div>
        <div class="restore-card__fields">
          <input v-model="restorePlanNo" type="text" autocomplete="off" placeholder="规划编号（可选）" />
          <input v-model="restoreCode" type="password" autocomplete="new-password" placeholder="规划码" />
          <button type="button" :disabled="restoring" @click="restore">
            <RotateCcw :size="15" />
            {{ restoring ? '找回中' : '找回' }}
          </button>
        </div>
      </section>

      <section class="planner-card">
        <div class="step-head">
          <div>
            <span>步骤 {{ step + 1 }} / {{ steps.length }}</span>
            <h2>{{ steps[step] }}</h2>
          </div>
          <strong>{{ progress }}%</strong>
        </div>
        <div class="progress-track"><span :style="{ width: `${progress}%` }" /></div>

        <div v-if="step === 0" class="step-panel">
          <label class="field">
            <span>所在省份（可选）</span>
            <select v-model="form.provinceCode">
              <option value="">暂不选择</option>
              <option v-for="province in PROVINCE_LIST" :key="province.code" :value="province.code">
                {{ province.name }}
              </option>
            </select>
          </label>
          <div class="field">
            <span>选科/科类</span>
            <div class="choice-grid">
              <button
                v-for="item in subjectCategoryOptions"
                :key="item"
                type="button"
                :class="{ active: form.subjectCategory === item }"
                @click="form.subjectCategory = item"
              >
                {{ item }}
              </button>
            </div>
          </div>
          <div class="field-pair">
            <label class="field">
              <span>分数（可选）</span>
              <input v-model="form.score" inputmode="numeric" type="number" placeholder="例如 580" />
            </label>
            <label class="field">
              <span>位次（可选）</span>
              <input v-model="form.rank" inputmode="numeric" type="number" placeholder="例如 18000" />
            </label>
          </div>
        </div>

        <div v-else-if="step === 1" class="step-panel">
          <div class="field">
            <span>喜欢的学科</span>
            <div class="choice-grid choice-grid--compact">
              <button v-for="item in subjectOptions" :key="`like-${item}`" type="button" :class="{ active: form.likedSubjects.includes(item) }" @click="toggle(form.likedSubjects, item)">
                {{ item }}
              </button>
            </div>
          </div>
          <div class="field">
            <span>不喜欢的学科</span>
            <div class="choice-grid choice-grid--compact">
              <button v-for="item in subjectOptions" :key="`dislike-${item}`" type="button" :class="{ active: form.dislikedSubjects.includes(item) }" @click="toggle(form.dislikedSubjects, item)">
                {{ item }}
              </button>
            </div>
          </div>
        </div>

        <div v-else-if="step === 2" class="step-panel">
          <div class="field">
            <span>兴趣方向</span>
            <div class="choice-grid choice-grid--compact">
              <button v-for="item in interestOptions" :key="item" type="button" :class="{ active: form.interestDirections.includes(item) }" @click="toggle(form.interestDirections, item)">
                {{ item }}
              </button>
            </div>
          </div>
          <div class="field">
            <span>性格倾向</span>
            <div class="choice-grid">
              <button v-for="item in personalityOptions" :key="item" type="button" :class="{ active: form.personalityTraits.includes(item) }" @click="toggle(form.personalityTraits, item, 5)">
                {{ item }}
              </button>
            </div>
          </div>
        </div>

        <div v-else-if="step === 3" class="step-panel">
          <div class="field">
            <span>职业期待</span>
            <div class="choice-grid choice-grid--compact">
              <button v-for="item in careerOptions" :key="item" type="button" :class="{ active: form.careerExpectations.includes(item) }" @click="toggle(form.careerExpectations, item)">
                {{ item }}
              </button>
            </div>
          </div>
          <label class="field">
            <span>家庭预算</span>
            <select v-model="form.familyBudget">
              <option v-for="item in budgetOptions" :key="item" :value="item">{{ item }}</option>
            </select>
          </label>
          <label class="field">
            <span>城市偏好（用逗号分隔，可选）</span>
            <input
              :value="form.cityPreferences.join('，')"
              type="text"
              placeholder="例如 贵阳，成都，重庆"
              @input="form.cityPreferences = ($event.target as HTMLInputElement).value.split(/[，,]/)"
            />
          </label>
        </div>

        <div v-else class="step-panel">
          <div class="field">
            <span>接受度偏好</span>
            <div class="boolean-grid">
              <button type="button" :class="{ active: form.acceptMedicine === true }" @click="setBoolean('acceptMedicine', true)">接受医学</button>
              <button type="button" :class="{ active: form.acceptMedicine === false }" @click="setBoolean('acceptMedicine', false)">不接受医学</button>
              <button type="button" :class="{ active: form.acceptTeacher === true }" @click="setBoolean('acceptTeacher', true)">接受师范</button>
              <button type="button" :class="{ active: form.acceptTeacher === false }" @click="setBoolean('acceptTeacher', false)">不接受师范</button>
              <button type="button" :class="{ active: form.acceptAgriculture === true }" @click="setBoolean('acceptAgriculture', true)">接受农林</button>
              <button type="button" :class="{ active: form.acceptAgriculture === false }" @click="setBoolean('acceptAgriculture', false)">不接受农林</button>
              <button type="button" :class="{ active: form.acceptSinoForeign === true }" @click="setBoolean('acceptSinoForeign', true)">接受中外合作</button>
              <button type="button" :class="{ active: form.acceptPrivate === true }" @click="setBoolean('acceptPrivate', true)">接受民办</button>
            </div>
          </div>
          <label class="field">
            <span>想避开的专业方向（用逗号分隔，可选）</span>
            <input
              :value="form.avoidDirections.join('，')"
              type="text"
              placeholder="例如 医学，农林，艺术"
              @input="form.avoidDirections = ($event.target as HTMLInputElement).value.split(/[，,]/)"
            />
          </label>
        </div>

        <div class="step-actions">
          <button class="secondary-btn" type="button" :disabled="step === 0 || submitting" @click="prevStep">上一步</button>
          <button v-if="step < steps.length - 1" class="primary-btn" type="button" @click="nextStep">
            下一步
            <ArrowRight :size="16" />
          </button>
          <button v-else class="primary-btn" type="button" :disabled="submitting" @click="submit">
            <Sparkles :size="16" />
            {{ submitting ? '生成中' : '生成专业规划' }}
          </button>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.planner-page {
  min-height: 100dvh;
  background: #f6f7f4;
  color: #172033;
}

.planner-hero {
  padding: 28px 20px 44px;
  color: #fff;
  background: linear-gradient(135deg, #17324d 0%, #1d6f72 52%, #7a5d2f 100%);
}

.planner-hero__content {
  width: min(100%, 980px);
  margin: 22px auto 0;
}

.planner-hero__badge,
.ghost-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.ghost-link {
  border: 0;
  background: transparent;
  color: rgba(255, 255, 255, 0.9);
  font-weight: 800;
}

.planner-hero__badge {
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.24);
  font-size: 13px;
  font-weight: 800;
}

.planner-hero h1 {
  margin: 18px 0 10px;
  font-size: clamp(32px, 8vw, 56px);
  line-height: 1.08;
}

.planner-hero p {
  max-width: 660px;
  margin: 0;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.8;
}

.planner-shell {
  width: min(100% - 28px, 1080px);
  margin: -24px auto 48px;
}

.notice-card,
.restore-card,
.planner-card {
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 8px;
  background: #fffdfa;
  box-shadow: 0 12px 34px rgba(23, 32, 51, 0.08);
}

.notice-card {
  display: flex;
  gap: 12px;
  padding: 16px;
  color: #5b4b35;
  line-height: 1.7;
}

.notice-card p {
  margin: 0;
}

.restore-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: end;
  margin-top: 14px;
  padding: 18px;
}

.restore-card h2,
.restore-card p {
  margin: 0;
}

.restore-card h2 {
  font-size: 18px;
}

.restore-card p {
  margin-top: 6px;
  color: #607086;
  line-height: 1.6;
}

.restore-card__fields {
  display: grid;
  grid-template-columns: 170px 170px 92px;
  gap: 10px;
}

.restore-card input,
.field input,
.field select {
  width: 100%;
  height: 42px;
  border: 1px solid #d9e0ea;
  border-radius: 8px;
  background: #fff;
  padding: 0 12px;
  color: #172033;
}

.restore-card button,
.primary-btn,
.secondary-btn {
  height: 42px;
  border-radius: 8px;
  border: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-weight: 850;
}

.restore-card button,
.primary-btn {
  background: #146c6f;
  color: #fff;
}

.planner-card {
  margin-top: 14px;
  padding: 22px;
}

.step-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.step-head span {
  color: #6c7a89;
  font-size: 13px;
  font-weight: 800;
}

.step-head h2 {
  margin: 4px 0 0;
  font-size: 24px;
}

.progress-track {
  height: 8px;
  margin: 18px 0 22px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf1f5;
}

.progress-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #146c6f, #c07a2a);
}

.step-panel {
  display: grid;
  gap: 18px;
}

.field,
.field-pair {
  display: grid;
  gap: 8px;
}

.field > span {
  font-weight: 850;
}

.field-pair {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.choice-grid,
.boolean-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.choice-grid--compact {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.choice-grid button,
.boolean-grid button {
  min-height: 42px;
  padding: 8px 10px;
  border: 1px solid #d9e0ea;
  border-radius: 8px;
  background: #fff;
  color: #334155;
  font-weight: 800;
}

.choice-grid button.active,
.boolean-grid button.active {
  color: #0d4f52;
  border-color: rgba(20, 108, 111, 0.45);
  background: #e9f6f4;
}

.step-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 22px;
}

.secondary-btn {
  min-width: 96px;
  background: #edf1f5;
  color: #334155;
}

.primary-btn {
  min-width: 150px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

@media (max-width: 720px) {
  .planner-hero {
    padding: 22px 16px 38px;
  }

  .planner-shell {
    width: min(100% - 20px, 1080px);
    margin-top: -18px;
  }

  .restore-card,
  .planner-card {
    padding: 16px;
  }

  .restore-card,
  .field-pair {
    grid-template-columns: 1fr;
  }

  .restore-card__fields {
    grid-template-columns: 1fr;
  }

  .choice-grid,
  .choice-grid--compact,
  .boolean-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .step-actions {
    position: sticky;
    bottom: 0;
    z-index: 2;
    margin: 20px -16px -16px;
    padding: 12px 16px;
    background: rgba(255, 253, 250, 0.96);
    border-top: 1px solid #e4e9f0;
  }

  .primary-btn,
  .secondary-btn {
    flex: 1;
    min-width: 0;
  }
}
</style>
