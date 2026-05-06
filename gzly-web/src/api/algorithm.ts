import request from './request'

/** 历史数据机会指数计算，仅供辅助参考 */
export function calcProbability(studentRank: number, schoolId: string, majorName: string, subjectType: string) {
  return request.get('/algorithm/probability', { params: { studentRank, schoolId, majorName, subjectType } })
}

/** 风险评估 */
export function assessRisk(schoolId: string, majorName: string, subjectType: string) {
  return request.get('/algorithm/risk', { params: { schoolId, majorName, subjectType } })
}

/** 分数线预测 */
export function predictScore(schoolId: string, majorName: string, subjectType: string) {
  return request.get('/algorithm/predict', { params: { schoolId, majorName, subjectType } })
}

/** 协同过滤推荐 */
export function recommendSimilar(schoolId: string, majorName: string, subjectType: string, studentRank: number, topN = 10) {
  return request.get('/algorithm/recommend', { params: { schoolId, majorName, subjectType, studentRank, topN } })
}

/** 综合分析 (机会指数+风险+预测 一次返回) */
export function comprehensiveAnalysis(studentRank: number, schoolId: string, majorName: string, subjectType: string) {
  return request.get('/algorithm/analysis', { params: { studentRank, schoolId, majorName, subjectType } })
}

/** 批量机会指数 */
export function batchProbability(studentRank: number, subjectType: string, targets: Array<{ schoolId: string; majorName: string }>) {
  return request.post('/algorithm/probability/batch', { studentRank, subjectType, targets })
}

/** 批量推荐 */
export function batchRecommend(selected: Array<{ schoolId: string; majorName: string }>, subjectType: string, studentRank: number, topN = 10) {
  return request.post('/algorithm/recommend/batch', { selected, subjectType, studentRank, topN })
}

/** 官方一分一段表位次区间，仅用于校验手填位次 */
export function estimateRank(score: number, subjectType: string) {
  return request.get('/algorithm/estimate-rank', { params: { score, subjectType } })
}
