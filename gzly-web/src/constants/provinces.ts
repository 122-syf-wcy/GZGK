export type ProvinceCode = 'GZ' | 'SC' | 'HB' | 'AH' | 'GX' | 'HI' | 'YN' | 'HA'

export type ProvinceFeatureStatus = 'open' | 'preparing' | 'locked'
export type ProvinceScorelineStatus = 'complete' | 'partial' | 'blocked' | 'gap'
export type ProvinceSubjectMode = '3+1+2' | '3+3' | '3+1+2_FIRST_YEAR'

export interface ProvinceConfig {
  code: ProvinceCode
  name: string
  shortName: string
  volunteerUnit: string
  volunteerUnitType: 'MAJOR_96' | 'PROFESSIONAL_GROUP_45'
  subjectMode: ProvinceSubjectMode
  targetCount: number
  targetBatch: string
  officialSource: string
  sourceType: string
  identityStrategySummary: string
  supportedIdentities: string[]
  batchSupportNote: string
  status: ProvinceFeatureStatus
  statusLabel: string
  statusTone: 'open' | 'preparing'
  scorelineStatus: ProvinceScorelineStatus
  scorelineStatusLabel: string
  scorelineStatusTone: 'green' | 'blue' | 'amber' | 'red' | 'slate'
  scorelineSummary: string
  scorelineAvailableTypes: string[]
  scorelineGapTypes: string[]
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
    subjectMode: '3+1+2',
    targetCount: 96,
    targetBatch: '普通本科批',
    officialSource: '贵州省招生考试院',
    sourceType: '考试院官方数据',
    identityStrategySummary: '普通类按专业（类）+院校，艺术类、体育类按综合成绩和专业（类）平行志愿独立建模。',
    supportedIdentities: ['普通类', '艺术类', '体育类'],
    batchSupportNote: '2026 官方政策已核查；普通本/专科当前仅开放历史估算草稿，艺术类、体育类仅展示批次策略。',
    status: 'open',
    statusLabel: '官方数据待发布',
    statusTone: 'open',
    scorelineStatus: 'partial',
    scorelineStatusLabel: '分数线部分可用',
    scorelineStatusTone: 'blue',
    scorelineSummary: '一分一段、院校投档线、专业录取最低分已有历史数据；省控线和艺体/专项结构化线仍待补官方源。',
    scorelineAvailableTypes: ['一分一段', '院校投档线', '专业录取最低分'],
    scorelineGapTypes: ['省控线', '专业组投档线不适用', '艺体综合分', '提前批/专项线'],
    routeQuery: { provinceCode: 'GZ' },
    heroTitle: '贵州普通本科批志愿工作台',
    heroDescription: '围绕贵州新高考“3+1+2”和 96 个“专业（类）+ 院校”志愿，提供院校查询、历年分数线、智能草稿和官方复核入口。',
    dataStatusTitle: '贵州历史估算开放',
    dataStatusDescription: '已接入贵州历史录取线、官方一分一段、选科要求和高校官方材料入口；2026 正式招生数据仍待完整导入核验。',
    volunteerCta: '进入 AI 志愿',
    volunteerLockTitle: '贵州历史估算已开放',
    volunteerLockDescription: '当前仍处于 PRE_OFFICIAL_DATA，生成前需阅读风险告知，结果只作为公益辅助参考。',
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
    subjectMode: '3+1+2_FIRST_YEAR',
    targetCount: 45,
    targetBatch: '普通本科批B段',
    officialSource: '四川省教育考试院',
    sourceType: '2025历史数据估算',
    identityStrategySummary: '普通类按物理/历史院校专业组位次，艺术类、体育类按综合成绩位次分开处理。',
    supportedIdentities: ['普通类', '艺术类', '体育类'],
    batchSupportNote: '2026 官网政策已核查；普通本科批B段当前开放 PRE 历史估算，体育/提前/专项等非普通批保持 QUERY_ONLY。',
    status: 'open',
    statusLabel: '官方数据待发布',
    statusTone: 'open',
    scorelineStatus: 'partial',
    scorelineStatusLabel: '分数线部分可用',
    scorelineStatusTone: 'blue',
    scorelineSummary: '2025 新高考一分一段和院校专业组线可查；专业录取最低分、艺体综合分和官方源证据仍待补强。',
    scorelineAvailableTypes: ['一分一段', '院校投档线', '专业组投档线'],
    scorelineGapTypes: ['省控线', '专业录取最低分', '艺体综合分', '官方源证据补强'],
    routeQuery: { provinceCode: 'SC' },
    heroTitle: '四川普通本科批B段工作台',
    heroDescription: '四川专区开放 PRE 阶段历史估算工作台；普通本科批B段按本省院校专业组口径生成草稿，非普通批只展示 QUERY_ONLY 和缺口说明。',
    dataStatusTitle: '四川历史估算开放',
    dataStatusDescription: '当前使用已核验 2025 历史数据估算，不用贵州 96 专业志愿规则套四川院校专业组；2026 完整数据生成仍待官方数据导入核验。',
    volunteerCta: '进入 AI 志愿',
    volunteerLockTitle: '四川历史估算开放',
    volunteerLockDescription: '四川普通本科批B段可进入历史估算；完整数据生成仍需2026官方计划、分数位次和模型门禁通过。',
    scoreLineDescription: '仅展示已导入且可核验的四川院校专业组调档线；无数据时显示准备中。',
    universityContextHint: '当前处于四川专区，院校库仍为全国库；请按四川招生专业组、调档线和官方目录二次复核。',
    specialAdmissionsHint: '四川特殊类型招生资料未完整核验，暂作为官方入口提醒，不开放结构化政策列表。',
    footerReminder: '四川专区当前为历史估算，最终请以四川省教育考试院和高校官方材料为准。',
  },
  HB: {
    code: 'HB',
    name: '湖北',
    shortName: '湖北',
    volunteerUnit: '院校专业组',
    volunteerUnitType: 'PROFESSIONAL_GROUP_45',
    subjectMode: '3+1+2',
    targetCount: 45,
    targetBatch: '本科普通批',
    officialSource: '湖北省教育考试院',
    sourceType: '2025历史数据估算',
    identityStrategySummary: '湖北普通类、艺术类、体育类、技能高考四条策略隔离，绝不回落到四川规则。',
    supportedIdentities: ['普通类', '艺术类', '体育类', '技能高考'],
    batchSupportNote: '2026 湖北官网图片正文已核查；本科普通批当前开放 PRE 历史估算，高职高专/提前/技能等批次保持 QUERY_ONLY。',
    status: 'open',
    statusLabel: '官方数据待发布',
    statusTone: 'open',
    scorelineStatus: 'partial',
    scorelineStatusLabel: '分数线部分可用',
    scorelineStatusTone: 'blue',
    scorelineSummary: '一分一段和院校专业组线可查；A00306 位次、专业录取线、艺体专项线仍需官方复核。',
    scorelineAvailableTypes: ['一分一段', '院校投档线', '专业组投档线'],
    scorelineGapTypes: ['省控线', '专业录取最低分', '艺体综合分', '专项/提前批结构化线', 'A00306 人工确认'],
    routeQuery: { provinceCode: 'HB' },
    heroTitle: '湖北本科普通批工作台',
    heroDescription: '湖北本科普通批按 45 个院校专业组志愿设计；当前开放 PRE 阶段历史估算，HB 请求和策略独立，不回落到四川或贵州。',
    dataStatusTitle: '湖北历史估算开放',
    dataStatusDescription: '当前按湖北 2025 院校专业组规则建模；清华 A00306 位次仍需人工确认，2026 完整数据生成仍待官方数据导入核验。',
    volunteerCta: '进入 AI 志愿',
    volunteerLockTitle: '湖北历史估算开放',
    volunteerLockDescription: '湖北本科普通批可进入历史估算；完整数据生成仍需2026官方计划、分数位次和 A00306 人工复核通过。',
    scoreLineDescription: '仅展示已导入且可核验的湖北院校专业组调档线；无数据时显示准备中。',
    universityContextHint: '当前处于湖北专区，请按湖北招生专业组、调档线和官方目录二次复核。',
    specialAdmissionsHint: '湖北特殊类型招生资料未完整核验，暂作为官方入口提醒。',
    footerReminder: '湖北专区当前为历史估算，最终请以湖北省教育考试院和高校官方材料为准。',
  },
  AH: {
    code: 'AH',
    name: '安徽',
    shortName: '安徽',
    volunteerUnit: '院校专业组',
    volunteerUnitType: 'PROFESSIONAL_GROUP_45',
    subjectMode: '3+1+2',
    targetCount: 45,
    targetBatch: '普通本科批次',
    officialSource: '安徽省教育招生考试院',
    sourceType: '2025历史数据估算',
    identityStrategySummary: '普通类按院校专业组位次，艺术类、体育类按综合分平行志愿独立建模。',
    supportedIdentities: ['普通类', '艺术类', '体育类'],
    batchSupportNote: '2026 官方通知已核查；普通本科批次当前开放 PRE 历史估算，高校专项/艺术/体育等批次保持 QUERY_ONLY。',
    status: 'open',
    statusLabel: '官方数据待发布',
    statusTone: 'open',
    scorelineStatus: 'partial',
    scorelineStatusLabel: '分数线部分可用',
    scorelineStatusTone: 'blue',
    scorelineSummary: '一分一段和院校专业组线可查；专业录取最低分、艺体综合分和官方域名证据仍待补强。',
    scorelineAvailableTypes: ['一分一段', '院校投档线', '专业组投档线'],
    scorelineGapTypes: ['省控线', '专业录取最低分', '艺体综合分', '官方域名证据补强'],
    routeQuery: { provinceCode: 'AH' },
    heroTitle: '安徽普通本科批次工作台',
    heroDescription: '安徽普通本科批次按 45 个院校专业组志愿设计；当前开放 PRE 阶段历史估算，非普通批展示 QUERY_ONLY 和数据缺口。',
    dataStatusTitle: '安徽历史估算开放',
    dataStatusDescription: '当前按安徽 2025 院校专业组规则建模，不用贵州 96 专业志愿规则替代；2026 完整数据生成仍待官方数据导入核验。',
    volunteerCta: '进入 AI 志愿',
    volunteerLockTitle: '安徽历史估算开放',
    volunteerLockDescription: '安徽普通本科批次可进入历史估算；完整数据生成仍需2026官方计划、分数位次和模型门禁通过。',
    scoreLineDescription: '仅展示已导入且可核验的安徽院校专业组调档线；无数据时显示准备中。',
    universityContextHint: '当前处于安徽专区，请按安徽招生专业组、调档线和官方目录二次复核。',
    specialAdmissionsHint: '安徽特殊类型招生资料未完整核验，暂作为官方入口提醒。',
    footerReminder: '安徽专区当前为历史估算，最终请以安徽省教育招生考试院和高校官方材料为准。',
  },
  GX: {
    code: 'GX',
    name: '广西',
    shortName: '广西',
    volunteerUnit: '院校专业组',
    volunteerUnitType: 'PROFESSIONAL_GROUP_45',
    subjectMode: '3+1+2',
    targetCount: 45,
    targetBatch: '普通本科批',
    officialSource: '广西壮族自治区招生考试院',
    sourceType: '2025历史数据估算',
    identityStrategySummary: '广西按 3+1+2 院校专业组口径处理普通类；艺术、体育、提前批和专项不套普通位次模型。',
    supportedIdentities: ['普通类', '艺术类', '体育类', '提前批/专项'],
    batchSupportNote: '2026 官方计划、一分一档、投档线和选科要求未齐前，仅展示历史估算能力和缺口说明。',
    status: 'open',
    statusLabel: '官方数据待发布',
    statusTone: 'open',
    scorelineStatus: 'partial',
    scorelineStatusLabel: '分数线部分可用',
    scorelineStatusTone: 'green',
    scorelineSummary: '已导入 2025 一分一段和部分高职高专普通批院校专业组投档线；本科主批、专业录取线和艺体专项仍待官方源补齐。',
    scorelineAvailableTypes: ['一分一段', '院校投档线', '专业组投档线'],
    scorelineGapTypes: ['省控线', '普通本科批投档线', '专业录取最低分', '艺体综合分', '提前批/专项线'],
    routeQuery: { provinceCode: 'GX' },
    heroTitle: '广西 3+1+2 工作台',
    heroDescription: '广西专区按院校专业组和物理/历史口径贯通工作台；当前分数线部分可查，AI 志愿仍处于历史估算阶段。',
    dataStatusTitle: '广西历史估算开放',
    dataStatusDescription: '当前只使用 2024/2025 历史窗口估算；2026 官方招生计划、投档线、一分一档和选科要求发布后再进入完整门禁。',
    volunteerCta: '进入 AI 志愿',
    volunteerLockTitle: '广西历史估算开放',
    volunteerLockDescription: '广西普通批可进入历史估算；完整能力仍需 2026 官方计划、分数位次、投档线和选科要求齐备。',
    scoreLineDescription: '广西分数线当前部分可用：一分一段和部分院校专业组线可查，其余类型展示官方源缺口。',
    universityContextHint: '当前处于广西专区，院校详情请结合广西院校专业组、选科要求和官方目录复核。',
    specialAdmissionsHint: '广西艺术、体育、提前批和专项入口开放，当前只提供政策查询和缺口说明。',
    footerReminder: '广西专区当前为历史估算，最终请以广西壮族自治区招生考试院和高校官方材料为准。',
  },
  HI: {
    code: 'HI',
    name: '海南',
    shortName: '海南',
    volunteerUnit: '院校专业组',
    volunteerUnitType: 'PROFESSIONAL_GROUP_45',
    subjectMode: '3+3',
    targetCount: 30,
    targetBatch: '本科普通批',
    officialSource: '海南省考试局',
    sourceType: '2025历史数据估算',
    identityStrategySummary: '海南按 3+3 综合改革口径处理；查询与推荐需按 selectedSubjects 匹配 requiredSubjects。',
    supportedIdentities: ['普通类', '艺术类', '体育类', '综合改革'],
    batchSupportNote: '海南本科普通批当前按 3+3 selectedSubjects 覆盖 requiredSubjects 口径展示历史估算能力；不按物理/历史分轨。',
    status: 'open',
    statusLabel: '官方数据待发布',
    statusTone: 'open',
    scorelineStatus: 'partial',
    scorelineStatusLabel: '分数线部分可用',
    scorelineStatusTone: 'green',
    scorelineSummary: '已导入部分 2025 院校专业组投档线；一分一段/标准分位次、省控线、专业录取线和艺体线仍待考试局原始源补齐。',
    scorelineAvailableTypes: ['院校投档线', '专业组投档线'],
    scorelineGapTypes: ['一分一段/标准分位次', '省控线', '专业录取最低分', '艺体综合分'],
    routeQuery: { provinceCode: 'HI' },
    heroTitle: '海南 3+3 综合改革工作台',
    heroDescription: '海南专区不展示物理/历史主筛选，按 3+3 选科组合和院校专业组口径贯通查询、分数线和 AI 志愿。',
    dataStatusTitle: '海南历史估算开放',
    dataStatusDescription: '当前只使用 2024/2025 历史窗口估算；海南按 selectedSubjects 匹配 requiredSubjects，不把物理/历史作为核心分类。',
    volunteerCta: '进入 AI 志愿',
    volunteerLockTitle: '海南历史估算开放',
    volunteerLockDescription: '海南普通批可进入历史估算；完整能力仍需 2026 官方计划、成绩分布、投档线和选科要求齐备。',
    scoreLineDescription: '海南分数线当前部分可用：院校专业组线可查；一分一段/标准分位次仍显示官方源缺口。',
    universityContextHint: '当前处于海南专区，院校详情请按 3+3 选科要求、专业组和考试局官方材料复核。',
    specialAdmissionsHint: '海南艺术、体育、专项入口开放，当前以政策查询、3+3 规则和数据缺口说明为主。',
    footerReminder: '海南专区当前为历史估算，最终请以海南省考试局和高校官方材料为准。',
  },
  YN: {
    code: 'YN',
    name: '云南',
    shortName: '云南',
    volunteerUnit: '院校专业组',
    volunteerUnitType: 'PROFESSIONAL_GROUP_45',
    subjectMode: '3+1+2_FIRST_YEAR',
    targetCount: 45,
    targetBatch: '普通本科批',
    officialSource: '云南省招生考试院',
    sourceType: '2025历史数据估算',
    identityStrategySummary: '云南 2025 首年 3+1+2，2024 旧文理只能作弱参考，不进入新高考主排序。',
    supportedIdentities: ['普通类', '艺术类', '体育类', '提前批/专项'],
    batchSupportNote: '云南 2025 首年新高考样本有限；官方图片源仍需 OCR/人工复核，当前仅展示历史估算能力和缺口。',
    status: 'open',
    statusLabel: '官方数据待发布',
    statusTone: 'open',
    scorelineStatus: 'blocked',
    scorelineStatusLabel: '缺官方源/OCR复核',
    scorelineStatusTone: 'amber',
    scorelineSummary: '官方页面和图片源已登记，但结构化表仍需 OCR 与人工复核，当前不展示真实分数线表格。',
    scorelineAvailableTypes: [],
    scorelineGapTypes: ['2025 一分一段', '省控线', '院校/专业组投档线', '专业录取最低分', '艺体综合分'],
    routeQuery: { provinceCode: 'YN' },
    heroTitle: '云南首年新高考工作台',
    heroDescription: '云南专区按 2025 首年 3+1+2 口径展示历史估算能力；旧文理只作弱参考，分数线等待 OCR/人工复核。',
    dataStatusTitle: '云南历史估算开放',
    dataStatusDescription: '当前只使用 2024/2025 历史窗口估算；2025 首年新高考样本不足，旧文理不得硬映射为物理/历史。',
    volunteerCta: '进入 AI 志愿',
    volunteerLockTitle: '云南历史估算开放',
    volunteerLockDescription: '云南普通批可进入历史估算；完整能力仍需 2026 官方计划、分数线、组级投档线和选科要求齐备。',
    scoreLineDescription: '云南分数线当前处于官方源/OCR 阻塞，页面展示缺口和后续需要的官方文件。',
    universityContextHint: '当前处于云南专区，院校详情请结合云南首年新高考专业组和官方目录复核。',
    specialAdmissionsHint: '云南艺术、体育、提前批和专项入口开放，当前以政策查询和 OCR 缺口说明为主。',
    footerReminder: '云南专区当前为历史估算，最终请以云南省招生考试院和高校官方材料为准。',
  },
  HA: {
    code: 'HA',
    name: '河南',
    shortName: '河南',
    volunteerUnit: '院校专业组',
    volunteerUnitType: 'PROFESSIONAL_GROUP_45',
    subjectMode: '3+1+2_FIRST_YEAR',
    targetCount: 45,
    targetBatch: '普通本科批',
    officialSource: '河南省教育考试院',
    sourceType: '2025历史数据估算',
    identityStrategySummary: '河南 2025 首年 3+1+2，大省同分密度和位次波动更高，冲稳保策略必须更保守。',
    supportedIdentities: ['普通类', '艺术类', '体育类', '提前批/专项'],
    batchSupportNote: '河南普通高考核心源仍受 haeea.cn 访问阻塞影响；当前只展示历史估算能力和官方源缺口。',
    status: 'open',
    statusLabel: '官方数据待发布',
    statusTone: 'open',
    scorelineStatus: 'blocked',
    scorelineStatusLabel: '官方源阻塞',
    scorelineStatusTone: 'amber',
    scorelineSummary: 'haeea.cn 普通高考核心源仍需人工上传或官方访问解除，当前不展示真实分数线表格。',
    scorelineAvailableTypes: [],
    scorelineGapTypes: ['2025 一分一段', '省控线', '院校/专业组投档线', '专业录取最低分', '艺体综合分'],
    routeQuery: { provinceCode: 'HA' },
    heroTitle: '河南首年新高考工作台',
    heroDescription: '河南专区按 2025 首年 3+1+2 和大省位次波动口径展示历史估算能力；分数线等待官方核心源补齐。',
    dataStatusTitle: '河南历史估算开放',
    dataStatusDescription: '当前只使用 2024/2025 历史窗口估算；首年新高考与同分密度风险较高，旧文理不得硬映射。',
    volunteerCta: '进入 AI 志愿',
    volunteerLockTitle: '河南历史估算开放',
    volunteerLockDescription: '河南普通批可进入历史估算；完整能力仍需 2026 官方计划、分数线、组级投档线和同分密度参数齐备。',
    scoreLineDescription: '河南分数线当前处于官方源阻塞，页面展示缺口和需要人工上传的官方文件清单。',
    universityContextHint: '当前处于河南专区，院校详情请结合河南首年新高考专业组、同分位次和官方目录复核。',
    specialAdmissionsHint: '河南艺术、体育、提前批和专项入口开放，当前以政策查询和官方源阻塞说明为主。',
    footerReminder: '河南专区当前为历史估算，最终请以河南省教育考试院和高校官方材料为准。',
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

export function isThreePlusThreeProvince(code?: unknown): boolean {
  return getProvinceConfig(code).subjectMode === '3+3'
}

export function isFirstYearNewGaokaoProvince(code?: unknown): boolean {
  return getProvinceConfig(code).subjectMode === '3+1+2_FIRST_YEAR'
}
