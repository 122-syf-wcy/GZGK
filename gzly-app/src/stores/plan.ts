/**
 * 志愿方案状态。
 *
 * 无账号模型下，`safetyCode` + `accessKey` 是方案的全部所有权凭证，
 * 因此这里承担三层兜底：内存 → 本地存档 → 服务端回捞。
 * 三层缺一层，用户切页或杀进程后就会丢方案。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { MAX_ARCHIVED_PLANS, STORAGE_KEYS } from '@/constants/config'
import { DISCLAIMER_VERSION } from '@/constants/compliance'
import { fetchPlan } from '@/api/volunteer'
import type { ArchivedPlan, VolunteerFormData, VolunteerPlan } from '@/types'

function defaultForm(): VolunteerFormData {
  return {
    provinceCode: 'GZ',
    totalScore: 0,
    provinceRank: 0,
    firstSubject: '物理',
    resubjects: [],
    preferredMajors: [],
    preferredRegions: [],
    strategyMode: '均衡型',
    decisionPriority: '专业优先',
    careerGoal: '就业优先',
    tuitionBudget: '均衡预算',
    acceptPrivate: true,
    acceptSinoForeign: false,
    agreedDisclaimer: false,
    disclaimerVersion: DISCLAIMER_VERSION,
  }
}

function readArchive(): ArchivedPlan[] {
  try {
    const raw = uni.getStorageSync(STORAGE_KEYS.planArchive)
    return Array.isArray(raw) ? raw : []
  } catch {
    return []
  }
}

/** 冷启动时恢复表单草稿。免责确认不跨会话继承，每次提交都要重新走告知流程。 */
function hydrateForm(): VolunteerFormData {
  try {
    const saved = uni.getStorageSync(STORAGE_KEYS.formDraft)
    if (saved && typeof saved === 'object' && saved.totalScore) {
      return { ...defaultForm(), ...saved, agreedDisclaimer: false }
    }
  } catch {
    // 读失败就当没有草稿
  }
  return defaultForm()
}

export const usePlanStore = defineStore('plan', () => {
  const form = ref<VolunteerFormData>(hydrateForm())
  const plan = ref<VolunteerPlan | null>(null)
  const aiContent = ref('')
  const generating = ref(false)
  const archive = ref<ArchivedPlan[]>(readArchive())

  function setForm(partial: Partial<VolunteerFormData>) {
    form.value = { ...form.value, ...partial }
    try {
      uni.setStorageSync(STORAGE_KEYS.formDraft, form.value)
    } catch {
      // 草稿写失败不阻断主流程
    }
  }

  function resetForm() {
    form.value = defaultForm()
    try {
      uni.removeStorageSync(STORAGE_KEYS.formDraft)
    } catch {
      // ignore
    }
  }

  function persistArchive() {
    try {
      uni.setStorageSync(STORAGE_KEYS.planArchive, archive.value)
    } catch {
      // 存储写失败不阻断主流程，用户仍可在本次会话内使用方案
    }
  }

  function setPlan(next: VolunteerPlan) {
    plan.value = next
    aiContent.value = ''

    const entry: ArchivedPlan = {
      planId: next.id,
      safetyCode: next.safetyCode,
      accessKey: next.accessKey,
      provinceCode: next.provinceCode,
      provinceName: next.provinceName,
      totalScore: next.totalScore,
      provinceRank: next.provinceRank,
      itemCount: next.items.length,
      createdAt: next.createdAt,
      savedAt: Date.now(),
    }
    archive.value = [entry, ...archive.value.filter(a => a.planId !== next.id)].slice(0, MAX_ARCHIVED_PLANS)
    persistArchive()

    try {
      uni.setStorageSync(STORAGE_KEYS.currentPlan, {
        planId: next.id,
        safetyCode: next.safetyCode,
        accessKey: next.accessKey,
      })
    } catch {
      // 同上
    }
  }

  function currentCredentials(): { planId: number; safetyCode: string; accessKey: string } | null {
    if (plan.value) {
      return { planId: plan.value.id, safetyCode: plan.value.safetyCode, accessKey: plan.value.accessKey }
    }
    try {
      const saved = uni.getStorageSync(STORAGE_KEYS.currentPlan)
      if (saved?.planId && saved?.accessKey) return saved
    } catch {
      // ignore
    }
    return null
  }

  /** 第三层兜底：内存没有就用本地凭证回服务端捞。 */
  async function restore(planId?: number): Promise<boolean> {
    if (plan.value && (!planId || plan.value.id === planId)) return true
    const cred = planId
      ? archive.value.find(a => a.planId === planId) || currentCredentials()
      : currentCredentials()
    if (!cred) return false
    try {
      const restored = await fetchPlan(cred.planId, cred.safetyCode, cred.accessKey)
      plan.value = restored
      return true
    } catch {
      return false
    }
  }

  function clear() {
    plan.value = null
    aiContent.value = ''
    try {
      uni.removeStorageSync(STORAGE_KEYS.currentPlan)
    } catch {
      // ignore
    }
  }

  function removeArchived(planId: number) {
    archive.value = archive.value.filter(a => a.planId !== planId)
    persistArchive()
  }

  return {
    form,
    plan,
    aiContent,
    generating,
    archive,
    setForm,
    resetForm,
    setPlan,
    restore,
    clear,
    removeArchived,
    currentCredentials,
  }
})
