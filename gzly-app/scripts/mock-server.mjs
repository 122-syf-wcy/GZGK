/**
 * 本地 mock 后端。
 *
 * 用途：UI 开发不必启动 MySQL + Redis + Spring Boot 全套。
 *
 * 用法：
 *   node scripts/mock-server.mjs
 *   $env:VITE_API_BASE="http://127.0.0.1:8092/api"; npm run dev:h5
 *
 * 端口默认 8092（避开真实后端的 8082），可用 MOCK_PORT 覆盖。
 *
 * 约束：响应形态必须贴着真实后端——Result 包装、业务失败用 HTTP 200 + code=-1、
 * null 字段直接省略（后端配了 non_null）。如果 mock 比真接口"干净"，
 * 就会掩盖归一化层的缺陷，失去意义。
 */
import http from 'node:http'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'

const PORT = Number(process.env.MOCK_PORT || 8092)
const ROOT = process.cwd()

/** 处理器内的局部变量 path（URL 路径）会遮蔽 node:path 模块，这里在模块级封装 */
function filePath(rel) {
  return path.join(ROOT, rel)
}

// ---------------------------------------------------------------------------
// 省份与批次
// ---------------------------------------------------------------------------

const PROVINCES = {
  gz: { name: '贵州', batch: '普通本科批', count: 96, mode: '专业（类）+ 院校', unit: 'MAJOR_96', level: 'ESTIMATE' },
  sc: { name: '四川', batch: '普通本科批B段', count: 45, mode: '院校专业组', unit: 'PROFESSIONAL_GROUP_45', level: 'QUERY_ONLY' },
  hb: { name: '湖北', batch: '本科普通批', count: 45, mode: '院校专业组', unit: 'PROFESSIONAL_GROUP_45', level: 'QUERY_ONLY' },
  ah: { name: '安徽', batch: '普通本科批次', count: 45, mode: '院校专业组', unit: 'PROFESSIONAL_GROUP_45', level: 'LOCKED' },
  gx: { name: '广西', batch: '本科普通批', count: 40, mode: '院校专业组', unit: 'PROFESSIONAL_GROUP_45', level: 'LOCKED' },
  hi: { name: '海南', batch: '本科普通批', count: 30, mode: '院校专业组', unit: 'PROFESSIONAL_GROUP_45', level: 'LOCKED' },
  yn: { name: '云南', batch: '本科批', count: 40, mode: '院校专业组', unit: 'PROFESSIONAL_GROUP_45', level: 'LOCKED' },
  ha: { name: '河南', batch: '普通本科批', count: 48, mode: '院校专业组', unit: 'PROFESSIONAL_GROUP_45', level: 'LOCKED' },
}

const MISSING_REASON = {
  QUERY_ONLY: '该地区院校专业组投档线尚未完整核验，暂只开放政策与分数线查询。',
  LOCKED: '该地区官方一分一段表尚未导入，暂不开放智能生成。',
}

function batchSupport(code) {
  const p = PROVINCES[code]
  if (!p) return null
  const ready = p.level === 'ESTIMATE' || p.level === 'FULL'
  return {
    provinceCode: code.toUpperCase(),
    provinceName: p.name,
    year: 2026,
    targetYear: 2026,
    latestOfficialDataYear: 2025,
    dataSourceYears: [2024, 2025],
    recommendationPhase: 'PRE_OFFICIAL_DATA',
    officialDataReady: false,
    readinessLevel: p.level,
    items: [
      {
        batchCode: 'NORMAL_UNDERGRADUATE',
        batchName: p.batch,
        candidateType: '普通类',
        supportLevel: ready ? 'ESTIMATE_RECOMMEND' : 'QUERY_ONLY',
        targetCount: p.count,
        maxVolunteerCount: p.count,
        volunteerMode: p.mode,
        policyStatus: 'published',
        officialSourceTitle: `${p.name}省2026年普通高校招生工作规定`,
        generatorReady: ready,
        ...(ready ? {} : { supportReason: MISSING_REASON[p.level], missingData: ['official_score_rank'] }),
      },
      {
        batchCode: 'EARLY_A_B',
        batchName: '本科提前批',
        candidateType: '普通类',
        supportLevel: 'QUERY_ONLY',
        targetCount: 0,
        volunteerMode: '院校顺序志愿',
        policyStatus: 'published',
        generatorReady: false,
        supportReason: '非普通类本科批暂只提供政策查询，不进入智能生成。',
      },
    ],
    summary: { [ready ? 'ESTIMATE_RECOMMEND' : 'QUERY_ONLY']: 1, QUERY_ONLY: 1 },
    warnings: ready ? ['当前使用 2024/2025 历史数据估算，官方数据发布后会更新。'] : [],
  }
}

// ---------------------------------------------------------------------------
// 院校与专业素材
// ---------------------------------------------------------------------------

const SCHOOLS = [
  ['贵州大学', '贵阳', ['211', '双一流']],
  ['贵州师范大学', '贵阳', ['省重点']],
  ['贵州医科大学', '贵阳', ['省重点']],
  ['贵州财经大学', '贵阳', []],
  ['遵义医科大学', '遵义', []],
  ['云南大学', '昆明', ['211', '双一流']],
  ['重庆邮电大学', '重庆', []],
  ['西南大学', '重庆', ['211', '双一流']],
  ['四川农业大学', '雅安', ['211']],
  ['成都信息工程大学', '成都', []],
  ['湘潭大学', '湘潭', ['双一流']],
  ['长沙理工大学', '长沙', []],
  ['广西大学', '南宁', ['211', '双一流']],
  ['桂林电子科技大学', '桂林', []],
  ['南昌大学', '南昌', ['211', '双一流']],
  ['江西财经大学', '南昌', []],
  ['福州大学', '福州', ['211', '双一流']],
  ['华侨大学', '泉州', []],
  ['郑州大学', '郑州', ['211', '双一流']],
  ['河南大学', '开封', ['双一流']],
  ['武汉科技大学', '武汉', []],
  ['湖北大学', '武汉', []],
  ['安徽大学', '合肥', ['211', '双一流']],
  ['合肥工业大学', '合肥', ['211', '双一流']],
  ['海南大学', '海口', ['211', '双一流']],
  ['西北师范大学', '兰州', []],
  ['青岛大学', '青岛', []],
  ['扬州大学', '扬州', []],
  ['宁波大学', '宁波', ['双一流']],
  ['浙江工业大学', '杭州', []],
  ['上海海事大学', '上海', []],
  ['天津工业大学', '天津', []],
]

const MAJORS = [
  '计算机科学与技术', '软件工程', '人工智能', '数据科学与大数据技术', '电子信息工程',
  '通信工程', '自动化', '电气工程及其自动化', '机械设计制造及其自动化', '土木工程',
  '临床医学', '口腔医学', '预防医学', '护理学', '药学',
  '汉语言文学', '英语', '新闻学', '法学', '社会工作',
  '金融学', '会计学', '经济学', '国际经济与贸易', '工商管理',
  '数学与应用数学', '物理学', '化学', '生物科学', '统计学',
  '教育学', '学前教育', '小学教育', '心理学', '体育教育',
  '环境工程', '食品科学与工程', '农学', '园艺', '动物医学',
]

const GRADIENTS = [
  { key: '冲', count: 14, chanceFrom: 52, chanceTo: 26 },
  { key: '稳', count: 43, chanceFrom: 84, chanceTo: 56 },
  { key: '保', count: 29, chanceFrom: 94, chanceTo: 86 },
  { key: '垫', count: 10, chanceFrom: 99, chanceTo: 95 },
]

function riskOf(chance) {
  if (chance >= 85) return ['低风险', 'green']
  if (chance >= 55) return ['中风险', 'yellow']
  return ['高风险', 'red']
}

function levelOf(chance) {
  if (chance >= 95) return '兜底参考'
  if (chance >= 85) return '稳妥参考'
  if (chance >= 55) return '适中'
  return '冲刺参考'
}

/** 生成一份贵州 96 条方案。数据分布贴着真实链路：梯度 14/43/29/10，机会指数随梯度递增。 */
function buildPlan(req) {
  const totalScore = Number(req.totalScore || req.score || 520)
  const provinceRank = Number(req.provinceRank || req.rank || 39000)
  const items = []
  let index = 0

  GRADIENTS.forEach((g) => {
    for (let i = 0; i < g.count; i++) {
      index += 1
      const s = SCHOOLS[(index * 7) % SCHOOLS.length]
      const major = MAJORS[(index * 11) % MAJORS.length]
      const ratio = g.count === 1 ? 0 : i / (g.count - 1)
      const chance = Math.round(g.chanceFrom + (g.chanceTo - g.chanceFrom) * ratio)
      const [riskLevel, riskColor] = riskOf(chance)
      const minRank = Math.round(provinceRank * (1 + (chance - 60) / 130))
      const needsReview = index % 9 === 0
      const majorLevel = index % 7 !== 0

      items.push({
        index,
        provinceCode: 'GZ',
        volunteerUnitType: 'MAJOR_96',
        volunteerUnitLabel: '专业（类）+ 院校',
        universityName: s[0],
        schoolId: `S${1000 + ((index * 13) % 900)}`,
        majorName: major,
        province: s[1] === '重庆' || s[1] === '上海' || s[1] === '天津' ? s[1] : `${s[1]}所在省`,
        city: s[1],
        tags: s[2],
        gradient: g.key,
        historyMinScore: Math.max(380, totalScore - Math.round((chance - 60) / 2.2)),
        historyMinRank: minRank,
        referenceYear: 2025,
        resubjectRequirement: index % 3 === 0 ? '化学' : index % 3 === 1 ? '不限' : '化学或生物',
        subjectRequirementSource: index % 5 === 0 ? 'missing' : 'official_requirement',
        latestPlanCount: 12 + ((index * 3) % 60),
        planTrend: index % 4 === 0 ? '扩招' : index % 4 === 1 ? '基本稳定' : index % 4 === 2 ? '缩招' : '单年计划',
        planExpansionIndex: 88 + ((index * 5) % 30),
        schoolEnrollmentIndex: 40 + ((index * 7) % 55),
        precisionScore: 55 + ((index * 3) % 40),
        precisionLabel: index % 3 === 0 ? '依据充分' : '依据一般',
        rankGap: minRank - provinceRank,
        recommendationScore: 60 + ((index * 5) % 38),
        chanceScore: chance,
        chanceLevel: levelOf(chance),
        calibratedProbability: Math.max(2, Math.min(99, chance + ((index % 5) - 2))),
        dataConfidenceScore: majorLevel ? 72 + ((index * 3) % 22) : 48 + ((index * 3) % 16),
        confidenceLabel: majorLevel ? (index % 4 === 0 ? '中可信' : '高可信') : '需复核',
        dataSourceType: majorLevel ? '专业级' : '院校级',
        riskLevel,
        riskColor,
        trend: index % 3 === 0 ? '上升' : index % 3 === 1 ? '稳定' : '下降',
        matchScore: 30 + ((index * 9) % 70),
        matchTag: index % 6 === 0 ? '双匹配' : index % 3 === 0 ? '专业匹配' : '无',
        schoolNature: index % 11 === 0 ? '民办' : '公办',
        referenceFitLevel: chance >= 80 ? '较高' : chance >= 55 ? '中等' : '偏低',
        recommendReason: `近三年该专业最低位次在 ${minRank - 1200} 至 ${minRank + 900} 之间波动，与你的位次 ${provinceRank} 形成${g.key === '冲' ? '冲刺' : g.key === '垫' ? '兜底' : '匹配'}关系。`,
        riskReason: needsReview
          ? '该专业选科要求未取得官方来源，必须人工核验招生专业目录。'
          : `参考年份为 2025，${index % 4 === 2 ? '当年计划较上年减少，需关注缩招影响。' : '计划数基本稳定。'}`,
        algorithmExplanation: `位次差 ${minRank - provinceRank}，数据来源为${majorLevel ? '专业级录取线' : '院校级投档线回退'}，机会指数经等渗校准。`,
        needsManualReview: needsReview,
        ...(needsReview ? { reviewFlags: ['missing_subject_requirement'] } : {}),
        legacySubjectFallback: index % 17 === 0,
        admissionBrochureUrl: 'https://zs.example.edu.cn/zhangcheng',
        majorCatalogUrl: 'https://zs.example.edu.cn/mulu',
        historyRecords: [2025, 2024].map(year => ({
          year,
          minScore: Math.max(380, totalScore - Math.round((chance - 60) / 2.2) - (2025 - year) * 3),
          minRank: minRank + (2025 - year) * 800,
          planCount: 12 + ((index * 3) % 60) - (2025 - year) * 2,
          subjectType: '物理类',
          dataSourceType: majorLevel ? '专业级' : '院校级',
          confidenceLabel: majorLevel ? '高可信' : '需复核',
        })),
      })
    }
  })

  const manualReviewItems = items
    .filter(it => it.needsManualReview)
    .map(it => ({
      index: it.index,
      universityName: it.universityName,
      majorName: it.majorName,
      gradient: it.gradient,
      reasons: ['未取得官方选科要求，需核对招生专业目录', '请同时核对体检与单科成绩要求'],
      evidenceLinks: [it.admissionBrochureUrl, it.majorCatalogUrl],
      confidenceLabel: it.confidenceLabel,
    }))

  return {
    id: 20260812,
    provinceCode: 'GZ',
    provinceName: '贵州',
    volunteerUnitType: 'MAJOR_96',
    volunteerUnitLabel: '专业（类）+ 院校',
    targetBatch: '普通本科批',
    targetCount: 96,
    totalScore,
    provinceRank,
    firstSubject: req.firstSubject || '物理',
    resubjects: req.resubjects || ['化学', '生物'],
    preferredMajors: req.preferredMajors || [],
    preferredRegions: req.preferredRegions || [],
    strategyMode: req.strategyMode || '均衡型',
    decisionPriority: req.decisionPriority || '专业优先',
    careerGoal: req.careerGoal || '就业优先',
    tuitionBudget: req.tuitionBudget || '均衡预算',
    acceptPrivate: req.acceptPrivate ?? true,
    acceptSinoForeign: req.acceptSinoForeign ?? false,
    safetyCode: 'GZ8F2K5Q',
    accessKey: 'ak_mock_9f3c2b7a1d',
    createdAt: '2026-08-12 18:20:00',
    items,
    manualReviewItems,
    metrics: {
      totalCount: 96,
      chongCount: 14,
      wenCount: 43,
      baoCount: 29,
      dianCount: 10,
      manualReviewCount: manualReviewItems.length,
      officialRequirementCount: 96 - Math.floor(96 / 5),
      missingRequirementCount: Math.floor(96 / 5),
      lowConfidenceCount: Math.floor(96 / 7),
      legacyFallbackCount: Math.floor(96 / 17),
      avgPrecisionScore: 74,
      portfolioSafetyProbability: 99.6,
      portfolioSafetyLevel: '整表安全度较高',
      portfolioSafetyNote: '保与垫区共有 39 条高可信条目，组合兜底参考概率 99.6%。',
      safeTailCount: 39,
      targetCount: 96,
      provinceCode: 'GZ',
      volunteerUnitType: 'MAJOR_96',
      generationCostMs: 1840,
      strategyMode: req.strategyMode || '均衡型',
    },
    referenceProbabilityNotice:
      '本系统输出为「机会指数」「风险等级」「数据参考度」与梯度建议，基于历史投档位次分布、官方一分一段表、招生计划变化和模型计算结果整理得出，'
      + '仅供辅助参考，不构成任何录取承诺。最终结果以各省级招生考试机构、各高校招生章程、官方招生专业目录、当年招生计划和正式投档录取结果为准。',
    warnings: ['当前使用 2024/2025 历史数据估算，官方数据发布后会更新。'],
    advisorAdvice: {
      title: '张雪峰.skill 报考建议',
      positioning: `你的位次 ${provinceRank} 处于物理类中上区间，冲刺目标建议锁定省内 211 与邻省双一流。`,
      priorityAdvice: '当前设定为专业优先，冲区已优先安排计算机与电子信息类。',
      gradientAdvice: '梯度为 14/43/29/10，稳区占比较高，适合求稳。',
      cityAdvice: '省内院校生活成本低、招生计划多，建议保与垫区以省内为主。',
      majorAdvice: '医学类需注意体检与单科要求，师范类关注公费生协议。',
      riskChecklist: ['核对选科要求', '核对体检限制', '确认学费区间', '确认是否服从调剂（本批次不设）'],
      actionItems: ['逐条打开招生章程核验', '把待查项导出后线下核对'],
      sourceNote: '本建议基于 GitHub 公开项目的策略框架整理，不扮演张雪峰本人，不代表本人或任何机构官方意见，也不构成录取承诺。',
      sourceProjectName: 'Eric-Yibo-Shen/zhangxuefeng-skillset',
      sourceProjectUrl: 'https://github.com/Eric-Yibo-Shen/zhangxuefeng-skillset',
    },
  }
}

let cachedPlan = null

// ---------------------------------------------------------------------------
// 邮箱验证码认证（贴真实后端 EmailAuthService 契约）
// ---------------------------------------------------------------------------

const AUTH = {
  /** email -> code；mock 固定发 123456，控制台可见 */
  codes: new Map(),
  /** email -> user */
  users: new Map(),
  /** token -> userId */
  tokens: new Map(),
  /** planId -> userId（认领关系） */
  planOwners: new Map(),
  nextUserId: 1001,
}

const EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/

function maskNickname(email) {
  const prefix = email.slice(0, email.indexOf('@'))
  if (prefix.length <= 2) return `${prefix}同学`
  return `${prefix[0]}***${prefix[prefix.length - 1]}`
}

/** 从 Authorization 头解析用户；无效返回 null */
function authUser(req) {
  const auth = req.headers.authorization || ''
  if (!auth.startsWith('Bearer ')) return null
  const userId = AUTH.tokens.get(auth.slice(7))
  if (!userId) return null
  for (const user of AUTH.users.values()) {
    if (user.userId === userId) return user
  }
  return null
}

/** 与真实后端拦截器一致：401 裸写，body 无 data 字段 */
function send401(res) {
  res.writeHead(401)
  res.end(JSON.stringify({ code: 401, message: '未登录或 Token 已过期' }))
}

// ---------------------------------------------------------------------------
// 其他接口数据
// ---------------------------------------------------------------------------

function rankCheck(query) {
  const score = Number(query.get('totalScore') || 0)
  const first = query.get('firstSubject') || '物理'
  const base = Math.max(1, Math.round((750 - score) * 620))
  return {
    provinceCode: (query.get('provinceCode') || 'GZ').toUpperCase(),
    provinceName: PROVINCES[(query.get('provinceCode') || 'gz').toLowerCase()]?.name || '贵州',
    subjectType: first === '历史' ? '历史类' : '物理类',
    referenceYear: 2025,
    rankLow: base,
    rankHigh: base + 561,
    officialDataReady: true,
    sourceName: '贵州省招生考试院',
    sourcePageUrl: 'https://zsksy.guizhou.gov.cn/',
    parseMethod: 'official_pdf',
    note: `${score} 分在 2025 年${first === '历史' ? '历史' : '物理'}类对应的同分位次区间。`,
    reminder: '系统仅做参考校验，最终请以贵州省招生考试院公布的官方一分一段表与考生本人确认的位次为准。',
  }
}

function universityList(query) {
  const keyword = query.get('keyword') || ''
  const page = Number(query.get('page') || 1)
  const pageSize = Number(query.get('pageSize') || 20)
  const all = SCHOOLS.filter(s => !keyword || s[0].includes(keyword)).map((s, i) => ({
    id: 1000 + i,
    schoolId: `S${1000 + i}`,
    name: s[0],
    province: s[1],
    city: s[1],
    level: s[2].includes('211') ? '本科' : '本科',
    typeName: i % 3 === 0 ? '综合类' : i % 3 === 1 ? '理工类' : '师范类',
    tags: s[2],
    f985: s[2].includes('985') ? 1 : 0,
    f211: s[2].includes('211') ? 1 : 0,
    natureName: i % 11 === 7 ? '民办' : '公办',
  }))
  const start = (page - 1) * pageSize
  return { total: all.length, page, pageSize, records: all.slice(start, start + pageSize) }
}

function scoreLineSchools(query) {
  const page = Number(query.get('page') || 1)
  const pageSize = Number(query.get('pageSize') || 20)
  const all = SCHOOLS.map((s, i) => ({
    schoolId: `S${1000 + i}`,
    universityName: s[0],
    city: s[1],
    tags: s[2],
    year: 2025,
    subjectType: query.get('subjectType') || '物理类',
    minScore: 610 - i * 4,
    minRank: 3200 + i * 1750,
    yearCount: 2,
  }))
  const start = (page - 1) * pageSize
  return { total: all.length, page, pageSize, records: all.slice(start, start + pageSize) }
}

function schoolHistory(query) {
  const id = query.get('schoolId') || 'S1000'
  const idx = Number(id.replace('S', '')) - 1000
  return [2025, 2024].map(year => ({
    schoolId: id,
    universityName: SCHOOLS[idx % SCHOOLS.length][0],
    year,
    subjectType: query.get('subjectType') || '物理类',
    minScore: 610 - idx * 4 - (2025 - year) * 5,
    minRank: 3200 + idx * 1750 + (2025 - year) * 900,
    batch: '本科批',
    rankSourceType: year === 2024 ? 'score_rank_converted' : 'original',
  }))
}

const SPECIAL_CATEGORIES = [
  { category: 'STRONG_BASE', categoryName: '强基计划', count: 3 },
  { category: 'ART', categoryName: '艺术类', count: 4 },
  { category: 'SPORT', categoryName: '体育类', count: 2 },
  { category: 'RURAL', categoryName: '专项计划', count: 3 },
]

function specialPolicies(query) {
  const category = query.get('category') || ''
  const all = SPECIAL_CATEGORIES.flatMap(c =>
    Array.from({ length: c.count }, (_, i) => ({
      id: `${c.category}-${i}`,
      year: 2026,
      category: c.category,
      categoryName: c.categoryName,
      title: `2026年${c.categoryName}招生实施办法（第 ${i + 1} 号）`,
      summary: `${c.categoryName}报名条件、考核方式与录取规则说明，须以省级招生考试机构原文为准。`,
      officialUrl: 'https://zsksy.guizhou.gov.cn/',
      sourceName: '贵州省招生考试院',
      applyStart: '2026-04-08',
      applyEnd: '2026-04-25',
      publishedAt: '2026-03-30 10:00:00',
    })),
  )
  return category ? all.filter(p => p.category === category) : all
}

const AI_REPORT = `## 整体定位

你的位次落在物理类中上区间，本次方案的梯度为冲 14 / 稳 43 / 保 29 / 垫 10，稳区占比偏高，属于求稳型结构。

## 冲区解读

冲区 14 条的机会指数集中在 26 到 52 之间，属于"够得着但不确定"的区间。这一段的价值在于争取更好的专业或城市，不应作为兜底依赖。

## 稳区解读

稳区是本方案的主体。43 条的机会指数在 56 到 84 之间，历史位次与你的位次形成较稳定的匹配关系。建议按专业意愿而非学校名气排序。

## 风险提示

方案中有若干条目未取得官方选科要求，已进入人工复核清单，必须逐条核对招生专业目录后再填报。医学、师范、专项类还需确认体检、单科成绩与协议条款。

## 下一步

先处理人工复核清单，再按专业意愿微调稳区顺序，最后确认保与垫区能够兜底。

本内容由 AI/系统基于结构化志愿数据生成，仅供参考。`

// ---------------------------------------------------------------------------
// 路由
// ---------------------------------------------------------------------------

function ok(data) {
  return { code: 0, message: 'success', data }
}

function fail(message) {
  return { code: -1, message }
}

function readBody(req) {
  return new Promise((resolve) => {
    let raw = ''
    req.on('data', (c) => { raw += c })
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {})
      } catch {
        resolve({})
      }
    })
  })
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`)
  const path = url.pathname.replace(/^\/api/, '')
  const q = url.searchParams

  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Headers', '*')
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS')
  res.setHeader('Content-Type', 'application/json; charset=utf-8')

  if (req.method === 'OPTIONS') {
    res.writeHead(204)
    res.end()
    return
  }

  // 模拟真实网络延迟，避免 UI 在瞬时返回下掩盖加载态问题
  await new Promise(r => setTimeout(r, 120))

  const send = (payload, status = 200) => {
    res.writeHead(status)
    res.end(JSON.stringify(payload))
  }

  let m

  if ((m = /^\/volunteer\/([a-z]{2})\/batch-support$/.exec(path))) {
    const data = batchSupport(m[1])
    return send(data ? ok(data) : fail('暂不支持该省份志愿生成'))
  }

  if (path === '/volunteer/rank-check') {
    if (!q.get('totalScore')) return send(fail('缺少必要参数：totalScore'), 400)
    return send(ok(rankCheck(q)))
  }

  if (path === '/volunteer/recommend' && req.method === 'POST') {
    const body = await readBody(req)
    if (body.agreedDisclaimer !== true || body.disclaimerVersion !== '2026-04-27-v1') {
      return send(fail('请先阅读并确认生成前风险告知'))
    }
    cachedPlan = buildPlan(body)
    return send(ok(cachedPlan))
  }

  if (path === '/volunteer/plan' && req.method === 'POST') {
    // 真实后端方案是落库的；mock 进程重启后按默认参数重建，模拟持久化
    if (!cachedPlan) cachedPlan = buildPlan({ totalScore: 545, provinceRank: 127661 })
    return send(ok(cachedPlan))
  }

  if (/^\/volunteer\/plans\/\d+\/ai-analysis$/.test(path)) {
    // 贴近真实链路的耗时，让客户端的阶段化思考态可见
    await new Promise(r => setTimeout(r, 3200))
    return send(ok({ content: AI_REPORT, generatedAt: '2026-08-12 18:25:00' }))
  }

  if (/^\/volunteer\/plans\/\d+\/skills\/ask$/.test(path)) {
    const body = await readBody(req)
    await new Promise(r => setTimeout(r, 1600))
    const q = String(body.question || '你的问题')
    const answer = [
      `关于「${q}」，结合这份方案说三点。`,
      '第一，先看数据可信度再看机会指数。同样是 70 分的机会指数，专业级数据支撑的条目和院校级回退的条目参考价值完全不同，后者应默认降一档看待。',
      '第二，稳区内部建议按专业意愿排序而不是按学校名气。平行志愿一轮投档定终身，排在前面的应该是"录了就去"的组合；名气分和就业方向冲突时，优先满足你在表单里选择的决策优先级。',
      '第三，任何进入人工复核清单的条目，在核对招生章程与专业目录之前都不要当作有效志愿计算。尤其注意体检、单科成绩与外语语种限制，这三类是每年退档的高发原因。',
      '本内容由 AI/系统基于结构化志愿数据生成，仅供参考。',
    ].join('\n\n')
    return send(ok({
      answer,
      sourceProjectName: 'Eric-Yibo-Shen/zhangxuefeng-skillset',
      sourceProjectUrl: 'https://github.com/Eric-Yibo-Shen/zhangxuefeng-skillset',
    }))
  }

  // 导出：返回二进制流，验证客户端 arraybuffer 接收与分平台保存链路。
  // mock 用现成图片顶替服务端渲染的长图；xlsx 的内容有效性由真实后端保证。
  if (/^\/volunteer\/plans\/\d+\/export-long-image$/.test(path) && req.method === 'POST') {
    try {
      let buf
      try {
        buf = await readFile(filePath('assets-raw/hero-sky.png'))
      } catch {
        buf = await readFile(filePath('src/static/img/hero-sky.webp'))
      }
      await new Promise(r => setTimeout(r, 800))
      res.setHeader('Content-Type', 'image/png')
      res.setHeader('Content-Disposition', "attachment; filename*=UTF-8''gzly-volunteer-plan.png")
      res.writeHead(200)
      res.end(buf)
    } catch (e) {
      send(fail('长图生成失败，请稍后重试'))
    }
    return
  }

  if (/^\/volunteer\/plans\/\d+\/export-excel$/.test(path) && req.method === 'POST') {
    await new Promise(r => setTimeout(r, 500))
    const csv = '\uFEFF序号,院校,专业,梯度,机会指数\n1,西南大学,口腔医学,冲,51\n2,南昌大学,经济学,冲,50\n'
    res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
    res.setHeader('Content-Disposition', "attachment; filename*=UTF-8''gzly-volunteer-plan.xlsx")
    res.writeHead(200)
    res.end(Buffer.from(csv, 'utf-8'))
    return
  }

  // ---- 邮箱验证码认证 ----
  if (path === '/auth/email/send-code' && req.method === 'POST') {
    const body = await readBody(req)
    const email = String(body.email || '').trim().toLowerCase()
    if (!EMAIL_RE.test(email)) return send(fail('邮箱格式不正确'))
    AUTH.codes.set(email, '123456')
    console.log(`[mock] 验证码已发送: ${email} -> 123456`)
    return send(ok({ cooldownSeconds: 60 }))
  }

  if (path === '/auth/email/login' && req.method === 'POST') {
    const body = await readBody(req)
    const email = String(body.email || '').trim().toLowerCase()
    const code = String(body.code || '').trim()
    if (!EMAIL_RE.test(email)) return send(fail('邮箱格式不正确'))
    if (!/^\d{6}$/.test(code)) return send(fail('验证码格式不正确'))
    if (AUTH.codes.get(email) !== code) return send(fail('验证码错误，请重新输入'))
    AUTH.codes.delete(email)

    let user = AUTH.users.get(email)
    const newUser = !user
    if (!user) {
      user = { userId: AUTH.nextUserId++, email, nickname: maskNickname(email), lastLoginAt: '2026-08-12 23:30:00' }
      AUTH.users.set(email, user)
    }
    const token = `mock_jwt_${user.userId}_${Date.now()}`
    AUTH.tokens.set(token, user.userId)
    return send(ok({ token, userId: user.userId, email, nickname: user.nickname, newUser }))
  }

  // ---- 登录态端点（/me/**，未带有效 token 一律 401 裸写） ----
  if (path === '/me' || path.startsWith('/me/')) {
    const user = authUser(req)
    if (!user) return send401(res)

    if (path === '/me') {
      return send(ok({ userId: user.userId, identifier: user.email, nickname: user.nickname, lastLoginAt: user.lastLoginAt }))
    }

    if (path === '/me/account/delete' && req.method === 'POST') {
      const body = await readBody(req)
      if (body.confirm !== true) return send(fail('请确认注销操作'))
      AUTH.users.delete(user.email)
      for (const [t, uid] of AUTH.tokens) {
        if (uid === user.userId) AUTH.tokens.delete(t)
      }
      for (const [pid, uid] of AUTH.planOwners) {
        if (uid === user.userId) AUTH.planOwners.delete(pid)
      }
      return send(ok({ deleted: true }))
    }

    if (path === '/me/plans' && req.method === 'GET') {
      const items = []
      if (cachedPlan && AUTH.planOwners.get(cachedPlan.id) === user.userId) {
        items.push({
          id: cachedPlan.id,
          provinceCode: cachedPlan.provinceCode,
          targetBatch: cachedPlan.targetBatch,
          totalScore: cachedPlan.totalScore,
          provinceRank: cachedPlan.provinceRank,
          firstSubject: cachedPlan.firstSubject,
          strategyMode: cachedPlan.strategyMode,
          itemCount: cachedPlan.items.length,
          hasAiAnalysis: false,
          createdAt: cachedPlan.createdAt,
        })
      }
      return send(ok({ items, total: items.length, page: 1, size: 20 }))
    }

    if ((m = /^\/me\/plans\/(\d+)$/.exec(path)) && req.method === 'GET') {
      const planId = Number(m[1])
      if (!cachedPlan || cachedPlan.id !== planId) return send(fail('方案不存在'))
      if (AUTH.planOwners.get(planId) !== user.userId) return send(fail('无权限访问该方案'))
      return send(ok(cachedPlan))
    }

    if (path === '/me/plans/claim' && req.method === 'POST') {
      const body = await readBody(req)
      const planId = Number(body.planId || 0)
      const key = String(body.safetyCode || body.accessKey || '')
      if (!planId) return send(fail('方案 id 不能为空'))
      if (!key) return send(fail('访问密钥不能为空'))
      if (!cachedPlan) cachedPlan = buildPlan({ totalScore: 545, provinceRank: 127661 })
      if (cachedPlan.id !== planId || (key !== cachedPlan.safetyCode && key !== cachedPlan.accessKey)) {
        return send(fail('访问密钥无效，无法绑定该方案'))
      }
      const owner = AUTH.planOwners.get(planId)
      if (owner && owner !== user.userId) return send(fail('该方案已被其他账号绑定，无法重复绑定'))
      const alreadyOwned = owner === user.userId
      AUTH.planOwners.set(planId, user.userId)
      return send(ok({ planId, claimed: !alreadyOwned, alreadyOwned }))
    }

    return send({ code: 404, message: '服务接口暂时不可用，请稍后重试。' }, 404)
  }

  if (path === '/university/list') return send(ok(universityList(q)))

  if ((m = /^\/university\/(\d+)$/.exec(path))) {
    const idx = Number(m[1]) - 1000
    const s = SCHOOLS[idx % SCHOOLS.length]
    return send(ok({
      id: Number(m[1]),
      schoolId: `S${m[1]}`,
      name: s[0],
      province: s[1],
      city: s[1],
      typeName: '综合类',
      natureName: '公办',
      tags: s[2],
      belong: '省教育厅',
      schoolSite: 'https://www.example.edu.cn',
      address: `${s[1]}市学院路 1 号`,
      content: `${s[0]}是一所以工学为主、多学科协调发展的高等学校，具体招生信息请以学校招生网公布的官方材料为准。`,
    }))
  }

  if (path === '/university/official-links') {
    return send(ok({
      schoolId: q.get('schoolId') || '',
      officialUrl: 'https://www.example.edu.cn',
      admissionSiteUrl: 'https://zs.example.edu.cn',
      admissionBrochureUrl: 'https://zs.example.edu.cn/zhangcheng',
      majorCatalogUrl: 'https://zs.example.edu.cn/mulu',
      tuitionInfoUrl: 'https://zs.example.edu.cn/shoufei',
    }))
  }

  if (path === '/score-line/schools') return send(ok(scoreLineSchools(q)))
  if (path === '/score-line/school-history') return send(ok(schoolHistory(q)))
  if (path === '/score-line/years') return send(ok([2025, 2024]))
  if (path === '/score-line/hot-majors') {
    return send(ok(MAJORS.slice(0, 10).map((name, i) => ({
      name, schoolCount: 120 - i * 7, avgScore: 580 - i * 6, avgRank: 12000 + i * 2400, heat: 100 - i * 6,
    }))))
  }

  if (path === '/special-admissions/categories') return send(ok(SPECIAL_CATEGORIES))
  if (path === '/special-admissions/policies') return send(ok(specialPolicies(q)))
  if (path === '/special-admissions/latest') return send(ok(specialPolicies(q).slice(0, 6)))

  if (path === '/announcement/current') {
    return send(ok({
      id: 1,
      title: '致即将奔赴高考的你们',
      content: '本系统为公益免费工具，所有数据与建议仅供参考，请以官方信息为准。祝顺利。',
      publishedAt: '2026-08-01 09:00:00',
    }))
  }

  if (path === '/site-stats/online') {
    return send(ok({ activeUsers: 37, windowSeconds: 300, totalViews: 128364, todayViews: 1842 }))
  }

  if (path === '/feedback' && req.method === 'POST') {
    const body = await readBody(req)
    if (!body.content || String(body.content).length < 10) return send(fail('反馈内容至少 10 个字'))
    return send(ok(null))
  }

  if (path === '/encouragement-messages') {
    if (req.method === 'POST') {
      const body = await readBody(req)
      const content = String(body.content || '')
      if (content.length < 4) return send(fail('留言至少 4 个字'))
      return send(ok({ id: Date.now(), nickname: body.nickname || '贵州考生', content, createdAt: '刚刚' }))
    }
    return send(ok([
      { id: 1, nickname: '高三老学长', content: '别慌，先把位次搞清楚，再谈冲稳保。', createdAt: '2 小时前' },
      { id: 2, nickname: '贵州考生', content: '愿所有努力都有回音。', createdAt: '5 小时前' },
      { id: 3, nickname: '匿名', content: '志愿也是一次认识自己的机会。', createdAt: '昨天' },
    ]))
  }

  send({ code: 404, message: '服务接口暂时不可用，请稍后重试。' }, 404)
})

server.listen(PORT, () => {
  console.log(`mock 后端已启动: http://127.0.0.1:${PORT}/api`)
})
