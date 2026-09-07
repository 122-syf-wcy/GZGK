/**
 * 运行时配置。
 *
 * H5 端可以靠相对路径 + 反向代理，App 端没有代理，必须使用绝对域名。
 */

/**
 * 开发期可用 VITE_API_BASE 覆盖，指向本地 mock：
 *   $env:VITE_API_BASE="http://127.0.0.1:8092/api"; npm run dev:h5
 * 不设则走本机后端默认端口。
 */
const DEV_BASE_URL = import.meta.env.VITE_API_BASE || 'http://127.0.0.1:8082/api'
const PROD_BASE_URL = 'https://gzly.dongsiwei.com/api'

export const API_BASE_URL = import.meta.env.PROD ? PROD_BASE_URL : DEV_BASE_URL

/** 常规接口超时。后端普通查询远快于此，超过说明链路有问题。 */
export const DEFAULT_TIMEOUT = 15000

/** AI 解读与导出接口。后端 SSE emitter 超时为 120s，AI 调用自身 80s，留出余量。 */
export const LONG_TASK_TIMEOUT = 120000

/** 志愿生成。后端无固定上限，实测受候选量影响较大。 */
export const GENERATE_TIMEOUT = 60000

/** 本地存储键。集中声明，避免散落各处拼字符串。 */
export const STORAGE_KEYS = {
  /** 最近方案列表：{planId, safetyCode, accessKey, ...摘要}[]，最多 5 条 */
  planArchive: 'gzly_plan_archive',
  /** 当前方案凭证：{planId, safetyCode, accessKey} */
  currentPlan: 'gzly_current_plan',
  /** 每条志愿的本地草稿标记，实际键为 `${draftStatus}:${planId}` */
  draftStatus: 'gzly_draft_status',
  /** 首启隐私与免责双确认 */
  privacyAgreed: 'gzly_privacy_agreed_v1',
  /** 表单草稿：分数、选科、偏好，重进不丢 */
  formDraft: 'gzly_form_draft',
  /** 登录会话：{token, userId, email, nickname} */
  session: 'gzly_session',
  /** 新手引导 */
  onboardingDone: 'gzly_onboarding_done_v1',
} as const

/** 本地最多保留几份方案。无账号模型下这是用户唯一的方案存档。 */
export const MAX_ARCHIVED_PLANS = 5
