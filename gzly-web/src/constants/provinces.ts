export type ProvinceCode = 'GZ' | 'SC' | 'HB' | 'AH'

export type ProvinceFeatureStatus = 'open' | 'preparing' | 'locked'

export interface ProvinceConfig {
  code: ProvinceCode
  name: string
  shortName: string
  volunteerUnit: string
  volunteerUnitType: 'MAJOR_96' | 'PROFESSIONAL_GROUP_45'
  targetCount: number
  targetBatch: string
  officialSource: string
  sourceType: string
  status: ProvinceFeatureStatus
  statusLabel: string
  statusTone: 'open' | 'preparing'
  routeQuery: { provinceCode: ProvinceCode }
  heroTitle: string
  heroDescription: string
  dataStatusTitle: string
  dataStatusDescription: string
  volunteerCta: string
  volunteerLockTitle: string
  volunteerLockDescription: string
  scoreLineDescription: string
  universityContextHint: string
  specialAdmissionsHint: string
  footerReminder: string
}

export const DEFAULT_PROVINCE_CODE: ProvinceCode = 'GZ'

export const PROVINCE_CONFIGS: Record<ProvinceCode, ProvinceConfig> = {
  GZ: {
    code: 'GZ',
    name: '贵州',
    shortName: '贵州',
    volunteerUnit: '专业（类）+ 院校',
    volunteerUnitType: 'MAJOR_96',
    targetCount: 96,
    targetBatch: '普通本科批',
    officialSource: '贵州省招生考试院',
    sourceType: '考试院官方数据',
    status: 'open',
    statusLabel: '已开放',
    statusTone: 'open',
    routeQuery: { provinceCode: 'GZ' },
    heroTitle: '贵州普通本科批志愿工作台',
    heroDescription: '围绕贵州新高考“3+1+2”和 96 个“专业（类）+ 院校”志愿，提供院校查询、历年分数线、智能草稿和官方复核入口。',
    dataStatusTitle: '贵州数据已开放',
    dataStatusDescription: '已补齐贵州 2024/2025 普通类历史、物理专业分数据与院校级投档线；2024 艺术/体育历史数据可查询，2025 艺体专业分以考试院后续公开源为准。',
    volunteerCta: '生成 96 个志愿草稿',
    volunteerLockTitle: '贵州智能生成已开放',
    volunteerLockDescription: '生成前仍需阅读风险告知，结果只作为公益辅助参考。',
    scoreLineDescription: '查看贵州院校级投档线和历年位次来源。',
    universityContextHint: '当前处于贵州专区，院校详情请结合贵州招生计划、章程和专业目录复核。',
    specialAdmissionsHint: '优先展示贵州 2026 特殊类型招生政策线索。',
    footerReminder: '数据与建议仅供参考，请以贵州省招生考试院和高校官方材料为准。',
  },
  SC: {
    code: 'SC',
    name: '四川',
    shortName: '四川',
    volunteerUnit: '院校专业组',
    volunteerUnitType: 'PROFESSIONAL_GROUP_45',
    targetCount: 45,
    targetBatch: '普通本科批B段',
    officialSource: '四川省教育考试院',
    sourceType: '考试院官方数据 + 历史回退',
    status: 'open',
    statusLabel: '已开放',
    statusTone: 'open',
    routeQuery: { provinceCode: 'SC' },
    heroTitle: '四川 2026 新高考志愿工作台',
    heroDescription: '围绕四川新高考"3+1+2"和 45 个院校专业组志愿，提供院校查询、历年分数线、智能草稿（普通本科批 B 段）、提前批 / 艺术 / 体育 / 专项规则与官方复核入口。',
    dataStatusTitle: '四川主流程已开放（普通本科批 B 段试推荐）',
    dataStatusDescription: '主流程批次（本科批 B 段、高职专科批）按 45 个院校专业组生成草稿；2026 官方数据 6 月底发布后会自动升级为完整推荐。提前批 / 艺术 / 体育 / 专项已开放批次入口和规则说明，等综合分公式与专项资格审核就位后再开放生成。',
    volunteerCta: '生成 45 个院校专业组草稿',
    volunteerLockTitle: '四川智能生成已开放',
    volunteerLockDescription: '生成前仍需阅读风险告知，结果只作为公益辅助参考；最终以四川省教育考试院和高校官方材料为准。',
    scoreLineDescription: '查看四川院校专业组调档线、专业级录取线与历年位次来源。',
    universityContextHint: '当前处于四川专区，院校库为全国库；请按四川招生专业组、调档线和官方目录二次复核。',
    specialAdmissionsHint: '展示四川提前批 A / 高校专项 / 高水平运动队 / 国家专项 / 地方专项 / 区域均衡 / 少数民族预科 / 艺术 / 体育等 16 类特殊批次的规则与资格条件线索。',
    footerReminder: '四川专区数据持续补齐中，最终请以四川省教育考试院和高校官方材料为准。',
  },
  HB: {
    code: 'HB',
    name: '湖北',
    shortName: '湖北',
    volunteerUnit: '院校专业组',
    volunteerUnitType: 'PROFESSIONAL_GROUP_45',
    targetCount: 45,
    targetBatch: '本科普通批',
    officialSource: '湖北省教育考试院',
    sourceType: '数据准备中',
    status: 'preparing',
    statusLabel: '数据准备中',
    statusTone: 'preparing',
    routeQuery: { provinceCode: 'HB' },
    heroTitle: '湖北本科普通批工作台',
    heroDescription: '湖北本科普通批按 45 个院校专业组志愿设计；当前先接入规则、数据状态和官方来源，完整生成需等待官方数据导入并核验。',
    dataStatusTitle: '湖北专区准备中',
    dataStatusDescription: '当前按湖北 2025 院校专业组规则建模，不用贵州 96 专业志愿规则替代。',
    volunteerCta: '查看湖北表单状态',
    volunteerLockTitle: '数据未完整核验，生成能力暂不开放',
    volunteerLockDescription: '湖北 45 个院校专业组生成需要官方一分一段、本科普通批专业组计划和调档线全部导入并核验。',
    scoreLineDescription: '仅展示已导入且可核验的湖北院校专业组调档线；无数据时显示准备中。',
    universityContextHint: '当前处于湖北专区，请按湖北招生专业组、调档线和官方目录二次复核。',
    specialAdmissionsHint: '湖北特殊类型招生资料未完整核验，暂作为官方入口提醒。',
    footerReminder: '湖北专区数据准备中，最终请以湖北省教育考试院和高校官方材料为准。',
  },
  AH: {
    code: 'AH',
    name: '安徽',
    shortName: '安徽',
    volunteerUnit: '院校专业组',
    volunteerUnitType: 'PROFESSIONAL_GROUP_45',
    targetCount: 45,
    targetBatch: '普通本科批',
    officialSource: '安徽省教育招生考试院',
    sourceType: '考试院政策 + 院校专业组历史数据',
    status: 'open',
    statusLabel: '已开放',
    statusTone: 'open',
    routeQuery: { provinceCode: 'AH' },
    heroTitle: '安徽 2026 新高考志愿工作台',
    heroDescription: '围绕安徽新高考"3+1+2"和 45 个院校专业组志愿，提供院校查询、历年分数线、智能草稿（普通本科批 / 高职专科批）、提前批 / 艺术 / 体育 / 专项规则与官方复核入口。',
    dataStatusTitle: '安徽主流程已开放（普通本科批试推荐）',
    dataStatusDescription: '主流程批次（普通本科批、高职专科批）按 45 个院校专业组生成草稿；2026 官方数据 6 月底发布后会自动升级为完整推荐。提前批 / 艺术 / 体育 / 专项已开放批次入口和规则说明，等综合分公式与专项资格审核就位后再开放生成。',
    volunteerCta: '生成 45 个院校专业组草稿',
    volunteerLockTitle: '安徽智能生成已开放',
    volunteerLockDescription: '生成前仍需阅读风险告知，结果只作为公益辅助参考；最终以安徽省教育招生考试院和高校官方材料为准。',
    scoreLineDescription: '查看安徽院校专业组调档线、专业级录取线与历年位次来源。',
    universityContextHint: '当前处于安徽专区，院校库为全国库；请按安徽招生专业组、调档线和官方目录二次复核。',
    specialAdmissionsHint: '展示安徽提前批 / 国家专项 / 地方专项 / 高校专项 / 艺术 / 体育 / 高水平运动队 / 西藏定向 等 14 类批次的规则与资格条件线索。',
    footerReminder: '安徽专区数据持续补齐中，最终请以安徽省教育招生考试院和高校官方材料为准。',
  },
}

export const PROVINCE_LIST = Object.values(PROVINCE_CONFIGS)

export function normalizeProvinceCode(code?: unknown): ProvinceCode {
  const value = Array.isArray(code) ? code[0] : code
  const normalized = String(value ?? '').trim().toUpperCase()
  return normalized in PROVINCE_CONFIGS ? normalized as ProvinceCode : DEFAULT_PROVINCE_CODE
}

export function getProvinceConfig(code?: unknown): ProvinceConfig {
  return PROVINCE_CONFIGS[normalizeProvinceCode(code)]
}

export function provinceQuery(code?: unknown): { provinceCode: ProvinceCode } {
  return { provinceCode: normalizeProvinceCode(code) }
}
