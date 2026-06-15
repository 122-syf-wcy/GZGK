import type { MajorPlannerView } from '@/api/majorPlanner'

const RESULT_PREFIX = 'gz_major_planner_result_'
const CODE_PREFIX = 'gz_major_planner_code_'

export function saveMajorPlannerSession(result: MajorPlannerView, planCode?: string): void {
  if (!result?.id) return
  try {
    sessionStorage.setItem(`${RESULT_PREFIX}${result.id}`, JSON.stringify(result))
    if (planCode) {
      sessionStorage.setItem(`${CODE_PREFIX}${result.id}`, planCode)
    }
  } catch {
    // sessionStorage may be disabled on some mobile browsers.
  }
}

export function loadMajorPlannerSession(id: number): { result: MajorPlannerView | null; planCode: string } {
  let result: MajorPlannerView | null = null
  let planCode = ''
  try {
    const raw = sessionStorage.getItem(`${RESULT_PREFIX}${id}`)
    if (raw) result = JSON.parse(raw) as MajorPlannerView
    planCode = sessionStorage.getItem(`${CODE_PREFIX}${id}`) || ''
  } catch {
    result = null
    planCode = ''
  }
  return { result, planCode }
}

export function saveMajorPlannerCode(id: number, planCode: string): void {
  if (!id || !planCode) return
  try {
    sessionStorage.setItem(`${CODE_PREFIX}${id}`, planCode)
  } catch {
    // ignore
  }
}
