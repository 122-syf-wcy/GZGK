/**
 * 全站免责声明 / 风险提示统一文案。
 *
 * 文案专业、中性，不使用“甩锅”语气；不暴露 FULL_RECOMMEND / PRE_OFFICIAL_DATA / QUERY_ONLY 等内部 code。
 * 各页面复用本文件，避免重复粘贴大段文字。
 */

/** 完整免责声明（用于弹层 / 可展开详情）。 */
export const FULL_DISCLAIMER =
  '本系统提供的志愿方案、分数线查询、院校信息、AI 解读及问答内容，均基于历史公开数据、用户输入信息、'
  + '系统规则和 AI 分析生成，仅供高考志愿填报参考，不构成正式填报意见或录取承诺。'
  + '2026 年官方招生计划、投档线、选科要求及录取规则，请以各省教育考试院、高校招生网及官方志愿填报系统发布内容为准。'
  + '因政策调整、数据更新、用户输入误差、AI 理解偏差等原因，系统结果可能存在不完整、不准确或滞后情况，'
  + '请务必结合官方资料、学校招生章程和人工复核后再做最终决策。'

/** 短版提示（通用一行）。 */
export const SHORT_DISCLAIMER = '结果仅供参考，不代表官方录取结论；请以考试院和高校官方信息为准。'

/** 首页轻量提示。 */
export const HOME_NOTICE = '当前为官方数据待发布阶段，系统结果仅供参考，请以考试院和高校官方信息为准。'

/** 志愿结果页顶部短提示。 */
export const RESULT_NOTICE = '当前方案为历史数据估算结果，仅供参考。'

/** AI 解读 / 回复下方小字。 */
export const AI_ANALYSIS_NOTICE = 'AI 解读可能存在偏差，请结合官方资料和人工复核。'

/** 未上线地区 AI 问答回复下方小字。 */
export const AI_QA_REPLY_NOTICE = 'AI 回答仅供参考；若未返回来源，请以考试院和高校官方信息为准。'

/** 未上线地区 AI 问答创建会话前确认文案。 */
export const AI_QA_CONFIRM_NOTICE = '当前地区暂未接入完整志愿推荐，本功能仅提供 AI 问答和方向参考，不生成正式志愿表。'

/** 分数线查询提示。 */
export const SCORE_LINE_NOTICE = '历史分数线仅供参考，不等同于当年录取线。'

/** 院校查询提示。 */
export const UNIVERSITY_NOTICE = '院校、专业、学费、选科要求等信息可能随年份调整，请以高校招生章程为准。'

/** 页脚链接文案。 */
export const FOOTER_DISCLAIMER_LABEL = '免责声明｜结果仅供参考，请以官方信息为准'

/** 志愿生成前风险确认卡片要点。 */
export const VOLUNTEER_RISK_POINTS = [
  '当前结果基于历史数据和系统估算，不代表正式录取结果。',
  '2026 年官方数据发布后，方案与位次参考可能发生变化。',
  '分数、位次、选科等信息填写错误会直接影响结果，请认真核对。',
  '最终志愿请结合官方招生计划、学校招生章程和人工复核后确定。',
]

/** 志愿生成前确认勾选文案。 */
export const VOLUNTEER_CONFIRM_LABEL = '我已知晓并同意：系统结果仅供参考，最终以官方信息为准。'

/**
 * 全站强制入口门槛版本号。
 * 升级免责声明文案时改这个值即可让所有用户重新阅读并同意。
 */
export const SITE_DISCLAIMER_VERSION = '2026-06-13-v1'

/** 入口门槛标题与按钮文案。 */
export const SITE_GATE_TITLE = '免责声明与使用须知'
export const SITE_GATE_INTRO = '在使用本系统前，请完整阅读以下免责声明。需滚动到底部并勾选确认后，方可进入使用。'
export const SITE_GATE_AGREE_LABEL = '我已逐条阅读并同意上述《免责声明》全部内容'
export const SITE_GATE_AGREE_BTN = '同意并进入'
export const SITE_GATE_REJECT_BTN = '不同意并退出'
export const SITE_GATE_SCROLL_HINT = '请将免责声明阅读至底部后，再勾选同意'
export const SITE_GATE_REJECTED_TITLE = '您已选择不同意'
export const SITE_GATE_REJECTED_DESC = '未同意《免责声明》将无法使用本系统。如需使用，请重新阅读并同意。'
export const SITE_GATE_REREAD_BTN = '重新阅读免责声明'

/** 本机确认状态 localStorage key。 */
export const ACCEPT_KEYS = {
  volunteer: 'volunteer_disclaimer_accepted',
  aiQa: 'ai_qa_disclaimer_accepted',
  site: 'site_disclaimer_accepted',
} as const

export function isDisclaimerAccepted(key: string): boolean {
  try {
    return localStorage.getItem(key) === '1'
  } catch {
    return false
  }
}

export function setDisclaimerAccepted(key: string, accepted: boolean): void {
  try {
    if (accepted) localStorage.setItem(key, '1')
    else localStorage.removeItem(key)
  } catch {
    // ignore storage errors
  }
}
