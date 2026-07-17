/** 已上线 8 省（与 gzly-web constants/provinces.ts 口径一致） */
export type ProvinceCode = 'GZ' | 'SC' | 'HB' | 'AH' | 'GX' | 'HI' | 'YN' | 'HA'

export interface ProvinceBrief {
  code: ProvinceCode
  name: string
  /** full = 完整志愿生成；query-only = 规则查询 + 历史估算 */
  workspaceMode: 'full' | 'query-only'
}

export const DEFAULT_PROVINCE_CODE: ProvinceCode = 'GZ'

export const PROVINCES: ProvinceBrief[] = [
  { code: 'GZ', name: '贵州', workspaceMode: 'full' },
  { code: 'SC', name: '四川', workspaceMode: 'full' },
  { code: 'AH', name: '安徽', workspaceMode: 'full' },
  { code: 'HB', name: '湖北', workspaceMode: 'query-only' },
  { code: 'GX', name: '广西', workspaceMode: 'query-only' },
  { code: 'HI', name: '海南', workspaceMode: 'query-only' },
  { code: 'YN', name: '云南', workspaceMode: 'query-only' },
  { code: 'HA', name: '河南', workspaceMode: 'query-only' },
]

export function normalizeProvinceCode(code?: unknown): ProvinceCode {
  const value = Array.isArray(code) ? code[0] : code
  const normalized = String(value ?? '').trim().toUpperCase()
  return PROVINCES.some((p) => p.code === normalized)
    ? (normalized as ProvinceCode)
    : DEFAULT_PROVINCE_CODE
}

export function getProvinceName(code?: unknown): string {
  const c = normalizeProvinceCode(code)
  return PROVINCES.find((p) => p.code === c)?.name ?? '贵州'
}
