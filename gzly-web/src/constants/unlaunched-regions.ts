/**
 * 未上线地区列表（前端 AI 志愿问答使用）。
 *
 * 显式排除已上线 8 省（GZ/SC/AH/HB/GX/HI/YN/HA）——它们走现有志愿推荐 / 分数线 /
 * 策略建议链路（见 src/constants/provinces.ts）。本列表只用于「未上线地区 AI 问答」，
 * 与后端 com.gzly.service.AiQaRegionRegistry 保持一致。
 *
 * 注意：河南 = HA（已上线，不在此列），陕西 = SN，湖南 = HN，湖北 = HB（已上线），河北 = HE。
 */
export interface UnlaunchedRegion {
  code: string
  name: string
}

/** 已上线地区，必须排除（与 provinces.ts 的 8 省一致）。 */
export const LAUNCHED_REGION_CODES = ['GZ', 'SC', 'HB', 'AH', 'GX', 'HI', 'YN', 'HA'] as const

export const UNLAUNCHED_REGIONS: UnlaunchedRegion[] = [
  { code: 'BJ', name: '北京' },
  { code: 'TJ', name: '天津' },
  { code: 'HE', name: '河北' },
  { code: 'SX', name: '山西' },
  { code: 'NM', name: '内蒙古' },
  { code: 'LN', name: '辽宁' },
  { code: 'JL', name: '吉林' },
  { code: 'HLJ', name: '黑龙江' },
  { code: 'SH', name: '上海' },
  { code: 'JS', name: '江苏' },
  { code: 'ZJ', name: '浙江' },
  { code: 'FJ', name: '福建' },
  { code: 'JX', name: '江西' },
  { code: 'SD', name: '山东' },
  { code: 'HN', name: '湖南' },
  { code: 'GD', name: '广东' },
  { code: 'CQ', name: '重庆' },
  { code: 'XZ', name: '西藏' },
  { code: 'SN', name: '陕西' },
  { code: 'GS', name: '甘肃' },
  { code: 'QH', name: '青海' },
  { code: 'NX', name: '宁夏' },
  { code: 'XJ', name: '新疆' },
]

export function isLaunchedRegion(code?: string | null): boolean {
  const normalized = String(code ?? '').trim().toUpperCase()
  return (LAUNCHED_REGION_CODES as readonly string[]).includes(normalized)
}

export function regionName(code?: string | null): string {
  const normalized = String(code ?? '').trim().toUpperCase()
  return UNLAUNCHED_REGIONS.find(r => r.code === normalized)?.name ?? ''
}

/** 常见选科（仅作为上下文标签，不参与正式志愿生成）。 */
export const SUBJECT_OPTIONS = ['物理', '历史', '化学', '生物', '政治', '地理', '技术']

/** 常见批次。 */
export const BATCH_OPTIONS = ['本科批', '专科（高职）批', '提前批']
