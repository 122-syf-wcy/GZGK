export type ProvinceCode = 'GZ' | 'SC' | 'HB' | 'AH' | 'GX' | 'HI' | 'YN' | 'HA'

export type ProvinceWorkspaceMode = 'full' | 'query-only'

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
  workspaceMode: ProvinceWorkspaceMode
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
    workspaceMode: 'full',
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
    workspaceMode: 'full',
    routeQuery: { provinceCode: 'SC' },
    heroTitle: '四川 2026 新高考志愿工作台',
    heroDescription: '围绕四川新高考"3+1+2"和 45 个院校专业组志愿，提供院校查询、历年分数线、智能草稿（普通本科批 B 段）、提前批 / 艺术 / 体育 / 专项规则与官方复核入口。',
    dataStatusTitle: '四川主流程已开放（普通本科批 B 段试推荐）',
    dataStatusDescription: '主流程批次（本科批 B 段、高职专科批）按 45 个院校专业组生成草稿；2026 官方数据 6 月底发布后会自动升级为完整推荐。提前批 / 艺术 / 体育 / 专项已开放批次入口和规则说明，等综合分公式与专项资格审核就位后再开放生成。当前为院校专业组级（45 组）结果，组内专业明细（专业代码 / 计划 / 学费 / 学制 / 选科）待官方源补齐，不等同于贵州 96 条完整专业志愿。',
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
    sourceType: '规则查询 + 历史估算',
    status: 'open',
    statusLabel: '历史估算可用',
    statusTone: 'open',
    workspaceMode: 'query-only',
    routeQuery: { provinceCode: 'HB' },
    heroTitle: '湖北本科普通批工作台',
    heroDescription: '湖北专区按 45 个院校专业组进入工作台；当前开放规则查询、历史估算和官方来源复核，不把 PRE_OFFICIAL_DATA 当作不可进入。',
    dataStatusTitle: '湖北工作台已开放',
    dataStatusDescription: '当前按湖北口径保留规则查询和历史估算入口，正式推荐仍需等待更完整的官方数据窗口。当前为院校专业组级结果，组内专业明细待官方源补齐；缺 hbksw /《湖北招生考试》官方导出，未补齐前不展示完整专业清单。',
    volunteerCta: '进入湖北工作台',
    volunteerLockTitle: '湖北规则查询与历史估算',
    volunteerLockDescription: '当前可以继续查看批次说明、院校专业组和位次区间；完整推荐暂不开放。',
    scoreLineDescription: '仅展示已导入且可核验的湖北院校专业组调档线；没有可核验数据时保留查询态。',
    universityContextHint: '当前处于湖北专区，请按湖北招生专业组、调档线和官方目录二次复核。',
    specialAdmissionsHint: '湖北特殊类型招生资料保留官方入口和规则提醒，作为查询态入口继续开放。',
    footerReminder: '湖北专区仅供公益参考，请以湖北省教育考试院和高校官方材料为准。',
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
    workspaceMode: 'full',
    routeQuery: { provinceCode: 'AH' },
    heroTitle: '安徽 2026 新高考志愿工作台',
    heroDescription: '围绕安徽新高考"3+1+2"和 45 个院校专业组志愿，提供院校查询、历年分数线、智能草稿（普通本科批 / 高职专科批）、提前批 / 艺术 / 体育 / 专项规则与官方复核入口。',
    dataStatusTitle: '安徽主流程已开放（普通本科批试推荐）',
    dataStatusDescription: '主流程批次（普通本科批、高职专科批）按 45 个院校专业组生成草稿；2026 官方数据 6 月底发布后会自动升级为完整推荐。提前批 / 艺术 / 体育 / 专项已开放批次入口和规则说明，等综合分公式与专项资格审核就位后再开放生成。当前为院校专业组级（45 组）结果，组内专业明细（专业代码 / 计划 / 学费 / 学制 / 选科）待官方源补齐，不等同于贵州 96 条完整专业志愿。',
    volunteerCta: '生成 45 个院校专业组草稿',
    volunteerLockTitle: '安徽智能生成已开放',
    volunteerLockDescription: '生成前仍需阅读风险告知，结果只作为公益辅助参考；最终以安徽省教育招生考试院和高校官方材料为准。',
    scoreLineDescription: '查看安徽院校专业组调档线、专业级录取线与历年位次来源。',
    universityContextHint: '当前处于安徽专区，院校库为全国库；请按安徽招生专业组、调档线和官方目录二次复核。',
    specialAdmissionsHint: '展示安徽提前批 / 国家专项 / 地方专项 / 高校专项 / 艺术 / 体育 / 高水平运动队 / 西藏定向 等 14 类批次的规则与资格条件线索。',
    footerReminder: '安徽专区数据持续补齐中，最终请以安徽省教育招生考试院和高校官方材料为准。',
  },
  GX: {
    code: 'GX',
    name: '广西',
    shortName: '广西',
    volunteerUnit: '院校专业组',
    volunteerUnitType: 'PROFESSIONAL_GROUP_45',
    targetCount: 45,
    targetBatch: '普通本科批',
    officialSource: '广西壮族自治区招生考试院',
    sourceType: '规则公告 + 历史估算',
    status: 'open',
    statusLabel: '历史估算可用',
    statusTone: 'open',
    workspaceMode: 'query-only',
    routeQuery: { provinceCode: 'GX' },
    heroTitle: '广西普通本科批工作台',
    heroDescription: '广西专区按院校专业组口径开放规则查询和历史估算；当前不展示完整推荐，但会保持省份隔离和本省选科口径。',
    dataStatusTitle: '广西工作台已开放',
    dataStatusDescription: '当前以历史估算、规则查询和批次说明为主，正式推荐和更完整的官方计划待后续数据补齐。当前为 QUERY_ONLY 策略建议：缺广西普通批全量招生计划 / 专业组目录 / 选科要求（现有仅征兵、补录），暂不生成院校清单，避免给出不可核验的假清单。',
    volunteerCta: '进入广西工作台',
    volunteerLockTitle: '广西规则查询与历史估算',
    volunteerLockDescription: '当前可以继续查看批次说明、院校专业组和位次区间；完整推荐暂不开放。',
    scoreLineDescription: '查看广西院校专业组调档线、历年位次和规则说明。',
    universityContextHint: '当前处于广西专区，院校详情请按广西招生计划、章程和选科要求复核。',
    specialAdmissionsHint: '优先展示广西特殊类型招生公告、资格条件和时间节点线索。',
    footerReminder: '广西专区仅供公益参考，请以广西官方材料为准。',
  },
  HI: {
    code: 'HI',
    name: '海南',
    shortName: '海南',
    volunteerUnit: '院校专业组',
    volunteerUnitType: 'PROFESSIONAL_GROUP_45',
    targetCount: 45,
    targetBatch: '本科批',
    officialSource: '海南省考试局',
    sourceType: '报名公告 + 历史估算',
    status: 'open',
    statusLabel: '历史估算可用',
    statusTone: 'open',
    workspaceMode: 'query-only',
    routeQuery: { provinceCode: 'HI' },
    heroTitle: '海南本科批工作台',
    heroDescription: '海南专区按 3+3 选科匹配打开工作台；当前以 selectedSubjects 与 requiredSubjects 的规则查询、历史估算和官方材料复核为主。',
    dataStatusTitle: '海南工作台已开放',
    dataStatusDescription: '海南按 3+3 选科匹配进入查询态，不按物理/历史分轨；正式实施办法未核到前，先保留规则查询和历史估算。当前为 QUERY_ONLY 策略建议：缺海南 2025 生效 3+3 普通批计划 / 专业目录 / 选考科目，暂不生成院校清单；坚持综合改革口径，不出现物理 / 历史主分类。',
    volunteerCta: '进入海南工作台',
    volunteerLockTitle: '海南规则查询与历史估算',
    volunteerLockDescription: '当前可以继续查看选科匹配、批次说明和历史估算；完整推荐暂不开放。',
    scoreLineDescription: '查看海南院校专业组调档线、历年位次和选科匹配说明。',
    universityContextHint: '当前处于海南专区，院校详情请按海南选科要求、章程和招生计划复核。',
    specialAdmissionsHint: '优先展示海南报名、专项和艺体公告的官方入口与资格线索。',
    footerReminder: '海南专区仅供公益参考，请以海南省考试局和高校官方材料为准。',
  },
  YN: {
    code: 'YN',
    name: '云南',
    shortName: '云南',
    volunteerUnit: '院校专业组',
    volunteerUnitType: 'PROFESSIONAL_GROUP_45',
    targetCount: 45,
    targetBatch: '普通本科批',
    officialSource: '云南省招生考试院',
    sourceType: '规则公告 + 历史估算',
    status: 'open',
    statusLabel: '历史估算可用',
    statusTone: 'open',
    workspaceMode: 'query-only',
    routeQuery: { provinceCode: 'YN' },
    heroTitle: '云南普通本科批工作台',
    heroDescription: '云南专区按 3+1+2 新高考口径开放历史估算和规则查询；当前保持物理/历史隔离，不展示完整推荐。',
    dataStatusTitle: '云南工作台已开放',
    dataStatusDescription: '当前以历史估算、规则查询和本省口径说明为主，2026 更完整的数据窗口到位后再升级推荐能力。当前为 QUERY_ONLY 策略建议：缺云南 2025 官方一分一段 / 普通批计划 / 专业组目录，暂不生成院校清单；2025 新高考与 2024 旧文理分离，不混轨。',
    volunteerCta: '进入云南工作台',
    volunteerLockTitle: '云南规则查询与历史估算',
    volunteerLockDescription: '当前可以继续查看批次说明、院校专业组和位次区间；完整推荐暂不开放。',
    scoreLineDescription: '查看云南院校专业组调档线、历年位次和历史回退说明。',
    universityContextHint: '当前处于云南专区，院校详情请按云南招生计划、章程和选科要求复核。',
    specialAdmissionsHint: '优先展示云南特殊类型招生公告、资格条件和时间节点线索。',
    footerReminder: '云南专区仅供公益参考，请以云南官方材料为准。',
  },
  HA: {
    code: 'HA',
    name: '河南',
    shortName: '河南',
    volunteerUnit: '院校专业组',
    volunteerUnitType: 'PROFESSIONAL_GROUP_45',
    targetCount: 45,
    targetBatch: '普通本科批',
    officialSource: '河南省教育考试院',
    sourceType: '招生工作规定 + 历史估算',
    status: 'open',
    statusLabel: '历史估算可用',
    statusTone: 'open',
    workspaceMode: 'query-only',
    routeQuery: { provinceCode: 'HA' },
    heroTitle: '河南普通本科批工作台',
    heroDescription: '河南专区按位次优先和院校专业组口径开放规则查询、历史估算和省份隔离，当前不展示完整推荐。',
    dataStatusTitle: '河南工作台已开放',
    dataStatusDescription: '当前以保守位次、规则查询和历史估算为主，正式推荐待更完整的官方计划与分数线窗口补齐。当前为 QUERY_ONLY 策略建议：缺河南官方一分一段 / 普通批计划 / 专业组目录，暂不生成院校清单，避免给出不可核验的假清单。',
    volunteerCta: '进入河南工作台',
    volunteerLockTitle: '河南规则查询与历史估算',
    volunteerLockDescription: '当前可以继续查看批次说明、院校专业组和位次区间；完整推荐暂不开放。',
    scoreLineDescription: '查看河南院校专业组调档线、历年位次和保守位次说明。',
    universityContextHint: '当前处于河南专区，院校详情请按河南招生计划、章程和位次要求复核。',
    specialAdmissionsHint: '优先展示河南特殊类型招生公告、资格条件和时间节点线索。',
    footerReminder: '河南专区仅供公益参考，请以河南官方材料为准。',
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
