/**
 * 核心接口的运行时契约校验（zod）。
 *
 * 目的：后端字段改名/类型变化时前端第一时间在控制台暴露，而不是深处的
 * `xxx.toLocaleString()` 静默炸掉。校验失败只告警不阻断（宽松模式），
 * 因此 schema 只锁"消费方真正依赖"的关键字段，其余字段透传。
 */
import { z } from 'zod'

/** 志愿方案条目：结果页渲染强依赖的字段。 */
export const volunteerItemSchema = z.object({
  index: z.number(),
  universityName: z.string(),
  majorName: z.string(),
  gradient: z.string(),
  historyMinScore: z.number().optional().nullable(),
  historyMinRank: z.number().optional().nullable(),
  referenceYear: z.number().optional().nullable(),
  chanceScore: z.number().optional().nullable(),
}).passthrough()

/** 志愿方案：恢复/生成链路的关键结构。 */
export const volunteerPlanSchema = z.object({
  id: z.number(),
  accessKey: z.string().optional().nullable(),
  items: z.array(volunteerItemSchema),
  metrics: z.record(z.string(), z.unknown()).optional().nullable(),
  warnings: z.array(z.string()).optional().nullable(),
}).passthrough()

/** 省份批次支持能力。 */
export const provinceBatchSupportSchema = z.object({
  provinceCode: z.string(),
  year: z.number(),
  recommendationPhase: z.string(),
  officialDataReady: z.boolean(),
  items: z.array(z.object({
    batchCode: z.string(),
    batchName: z.string(),
    supportLevel: z.string(),
  }).passthrough()),
}).passthrough()

/** 分数线查询响应。 */
export const scoreLineQuerySchema = z.object({
  provinceCode: z.string(),
  year: z.number(),
  scoreLineType: z.string(),
  dataStatus: z.string(),
  pageResult: z.object({
    total: z.number(),
    records: z.array(z.record(z.string(), z.unknown())).optional(),
    list: z.array(z.record(z.string(), z.unknown())).optional(),
  }).passthrough(),
}).passthrough()

type ContractRule = { pattern: RegExp; schema: z.ZodTypeAny; name: string }

/** URL → schema 映射；只覆盖最关键的三类响应。 */
const CONTRACT_RULES: ContractRule[] = [
  { pattern: /^\/volunteer\/plan$/, schema: volunteerPlanSchema, name: 'VolunteerPlan' },
  { pattern: /^\/volunteer\/(gz|sc|hb|ah|gx|hi|yn|ha)\/batch-support/, schema: provinceBatchSupportSchema, name: 'ProvinceBatchSupport' },
  { pattern: /^\/score-lines\/[^/]+\//, schema: scoreLineQuerySchema, name: 'ScoreLineQuery' },
]

/**
 * 对响应 data 做宽松校验；不匹配任何规则时静默跳过。
 * 校验失败仅 console.warn，绝不影响业务流程。
 */
export function validateApiContract(url: string, payload: unknown): void {
  const rule = CONTRACT_RULES.find(r => r.pattern.test(url))
  if (!rule || payload == null) return
  const result = rule.schema.safeParse(payload)
  if (!result.success) {
    console.warn(
      `[契约告警] ${rule.name} (${url}) 响应结构与前端预期不一致：`,
      result.error.issues.slice(0, 5),
    )
  }
}
