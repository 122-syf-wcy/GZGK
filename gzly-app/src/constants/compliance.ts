import complianceTerms from '../../../gzly-server/src/main/resources/compliance-terms.json'

/**
 * 合规常量。
 *
 * 这一份必须与 `gzly-web/src/constants/compliance.ts` 和后端
 * `com.gzly.common.ComplianceConstants` 保持同源。视觉可以两端分叉，文案不行。
 *
 * DISCLAIMER_VERSION 是后端强校验字段：生成请求必须携带且精确匹配，否则
 * 返回 HTTP 200 + code=-1。后端升版而客户端没跟，会导致存量 App 全量生成失败，
 * 且失败形态很容易被误判成成功。
 */

export const DISCLAIMER_VERSION = '2026-04-27-v1'
export const DISCLAIMER_UPDATED_AT = '2026年4月27日'

export const REFERENCE_PROBABILITY_NOTICE =
  '本系统输出为「机会指数」「风险等级」「数据参考度」与梯度建议，基于历史投档位次分布、官方一分一段表、招生计划变化和模型计算结果整理得出，'
  + '仅供辅助参考，不构成任何录取承诺。最终结果以各省级招生考试机构、各高校招生章程、'
  + '官方招生专业目录、当年招生计划和正式投档录取结果为准。'

export const AI_GENERATED_NOTICE = '本内容由 AI/系统基于结构化志愿数据生成，仅供参考。'

export const DISCLAIMER_CONFIRM_TEXT =
  '我已阅读并确认：本系统仅作公益辅助，最终以官方信息和本人决策为准'

/**
 * 客户端本地文案的违禁词自检。
 *
 * 词表为单一事实源：`gzly-server/src/main/resources/compliance-terms.json`，
 * 由后端 AiService 启动期加载、web/app 构建脚本（check-compliance.mjs）与
 * 两端 constants 共读同一份文件，禁止在此再维护硬编码词条（只增不删）。
 * 服务端会清洗自己产出的内容，但 App 本地写死的空状态、按钮、推送、应用商店描述
 * 不经过服务端，必须自检。
 */
export const BANNED_TERMS_HARD: readonly string[] = complianceTerms.hard

export const BANNED_TERMS_SOFT: readonly string[] = complianceTerms.soft

/** 正确说法对照，供 UI 文案参考 */
export const APPROVED_WORDING = {
  chance: '机会指数',
  risk: '风险等级',
  confidence: '数据参考度',
  gradient: '梯度参考',
} as const

export type ComplianceSection = {
  title: string
  paragraphs: string[]
}

/** 与 gzly-web 的 COMPLIANCE_SECTIONS 同源，措辞不得分叉。 */
export const COMPLIANCE_SECTIONS: ComplianceSection[] = [
  {
    title: '一、公益性质与非官方声明',
    paragraphs: [
      '本系统为面向高考考生的公益辅助工具，完全免费，不设置收费、打赏或商业广告入口。',
      '本系统不是任何省级招生考试机构、教育部阳光高考平台或高校的官方网站，也不代表任何官方招生机构作出录取判断。',
    ],
  },
  {
    title: '二、数据来源与时效提示',
    paragraphs: [
      '系统优先整理各省级招生考试机构、教育部阳光高考平台、高校招生网和高校招生章程等官方公开信息，并在结果中尽量展示参考年份、数据来源和官方核验入口。',
      '招生计划、专业目录、选科要求、学费标准和录取规则可能随年份、批次和院校政策变化。即使数据来自官方公开渠道，也可能存在更新滞后、名称差异、解析遗漏或人工录入误差。',
    ],
  },
  {
    title: '三、AI 与算法内容标识',
    paragraphs: [
      '本内容由 AI/系统基于结构化志愿数据生成，仅供参考。系统只基于你填写的分数、官方位次、选科、偏好和已整理的数据生成参考草稿，不会读取或替你提交正式志愿。',
      'AI 解读只能解释系统已经生成的结构化结果和人工复核清单，不得编造政策、学校、专业、分数线、招生计划或录取结论。',
    ],
  },
  {
    title: '四、禁止承诺与梯度口径',
    paragraphs: [
      '本系统不会提供任何内部渠道、确定性结果、绝对安全或结果承诺类宣传。',
      '「冲、稳、保、垫」仅是常见的志愿梯度整理思路，用来帮助你排序和复核志愿草稿，不代表任何确定性录取结果。',
    ],
  },
  {
    title: '五、个人信息与安全边界',
    paragraphs: [
      '系统只收集生成志愿草稿所必要的信息，包括高考总分、官方位次、选科、偏好设置以及用于安全限流和排障的必要访问记录。',
      '请不要在本系统填写或上传身份证号、准考证号、志愿填报系统账号密码、短信验证码、银行卡信息等高敏感信息。',
    ],
  },
  {
    title: '六、必须人工复核',
    paragraphs: [
      '正式填报前，请逐条核对省级招生考试机构发布的招生计划、官方一分一段表、目标高校招生章程、招生专业目录、体检和单科成绩要求。',
      '历史类选科要求、军警公安、医学、师范、专项计划、艺术体育、中外合作、高学费等情形必须人工复核，系统提示不能替代官方材料和专业指导。',
    ],
  },
  {
    title: '七、合理责任边界',
    paragraphs: [
      '系统会持续改进数据来源、算法说明、风险提示和安全保护，但无法覆盖当年政策调整、招生计划变化、同分排序、专业热度波动、大小年等全部因素。',
      '你应结合官方资料、学校招生办、老师或具备资质的专业人士建议独立决策。本告知用于说明系统能力边界和使用风险，不构成正式法律意见。',
    ],
  },
]

/** 开发期自检：命中即在控制台报错，避免违禁词进包 */
export function assertCompliantText(text: string, where: string): void {
  if (import.meta.env.PROD) return
  const hit = BANNED_TERMS_HARD.find(term => text.includes(term))
  if (hit) {
    console.error(`[合规] ${where} 命中违禁词「${hit}」，请改用「${APPROVED_WORDING.chance}」等合规表述`)
  }
}
