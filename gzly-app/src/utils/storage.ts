/** 统一封装 uni storage，集中管理 App 端各类凭证/会话的本地持久化 */

function get(key: string): string {
  try {
    return (uni.getStorageSync(key) as string) || ''
  } catch {
    return ''
  }
}
function set(key: string, value: string) {
  try {
    uni.setStorageSync(key, value)
  } catch {
    /* ignore */
  }
}
function remove(key: string) {
  try {
    uni.removeStorageSync(key)
  } catch {
    /* ignore */
  }
}

/* 志愿安全码：按 planId 绑定 + 当前默认码 */
export function saveSafetyCode(planId: number, code: string) {
  if (planId && code) set(`gz_plan_safety_${planId}`, code)
}
export function getSafetyCode(planId: number): string {
  return get(`gz_plan_safety_${planId}`)
}
export function setCurrentSafetyCode(code: string) {
  if (code) set('gz_current_safety_code', code)
}
export function getCurrentSafetyCode(): string {
  return get('gz_current_safety_code')
}
export function setLastPlanId(id: number) {
  if (id) set('gz_last_plan_id', String(id))
}
export function getLastPlanId(): number {
  return Number(get('gz_last_plan_id') || 0)
}

/* 专业规划码 */
export function saveMajorPlanCode(id: number, code: string) {
  if (id && code) set(`gz_mp_code_${id}`, code)
}
export function getMajorPlanCode(id: number): string {
  return get(`gz_mp_code_${id}`)
}
export function setLastMajorPlanId(id: number) {
  if (id) set('gz_last_mp_id', String(id))
}
export function getLastMajorPlanId(): number {
  return Number(get('gz_last_mp_id') || 0)
}

/* 首启免责声明门禁：记录已同意的版本 */
export function getDisclaimerAcceptedVersion(): string {
  return get('gz_disclaimer_version')
}
export function setDisclaimerAccepted(version: string) {
  if (version) {
    set('gz_disclaimer_version', version)
    set('gz_disclaimer_time', String(Date.now()))
  }
}
export function getDisclaimerAcceptedTime(): number {
  return Number(get('gz_disclaimer_time') || 0)
}

/* 偏好省份：从省份专区跳转时预选（tab 页无法带 query，用本地存储传递） */
export function setPrefProvince(code: string) {
  if (code) set('gz_pref_province', code)
}
export function getPrefProvince(): string {
  return get('gz_pref_province')
}

/* 本地志愿方案历史（无需登录，按本机保存，用于「我的方案」） */
export interface PlanRecord {
  planId: number
  provinceName?: string
  totalScore?: number
  itemCount?: number
  createdAt: number
}
export function addPlanRecord(rec: PlanRecord) {
  try {
    const list = getPlanRecords().filter((r) => r.planId !== rec.planId)
    list.unshift(rec)
    uni.setStorageSync('gz_plan_records', list.slice(0, 30))
  } catch {
    /* ignore */
  }
}
export function getPlanRecords(): PlanRecord[] {
  try {
    const v = uni.getStorageSync('gz_plan_records')
    return Array.isArray(v) ? (v as PlanRecord[]) : []
  } catch {
    return []
  }
}

/* AI 问答活动会话 */
export interface AiQaActive {
  sessionUid: string
  code: string
}
export function setAiQaActive(active: AiQaActive) {
  try {
    uni.setStorageSync('gz_aiqa_active', active)
  } catch {
    /* ignore */
  }
}
export function getAiQaActive(): AiQaActive | null {
  try {
    const v = uni.getStorageSync('gz_aiqa_active') as AiQaActive
    return v && v.sessionUid ? v : null
  } catch {
    return null
  }
}
export function clearAiQaActive() {
  remove('gz_aiqa_active')
}
