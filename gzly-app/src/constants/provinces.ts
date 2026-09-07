/**
 * 省份文案。
 *
 * 这里**只放文案**。志愿数、批次名、志愿单位、能否生成等结构化参数一律从
 * `GET /volunteer/{code}/batch-support` 取（见 APP_UNIAPP_PLAN.md 的 D4）。
 *
 * 原因：gzly-web 把这些参数硬编码在前端，已经和后端漂移——广西后端 40 前端 45、
 * 云南后端 40 前端 45、河南后端 48 前端 45。再抄一份就是第三个事实源。
 */

import type { ProvinceCode } from '@/types'

export interface ProvinceText {
  code: ProvinceCode
  name: string
  shortName: string
  /** 省级招生考试机构全称，展示官方来源时用 */
  officialSourceName: string
}

export const PROVINCE_TEXTS: Record<ProvinceCode, ProvinceText> = {
  GZ: { code: 'GZ', name: '贵州省', shortName: '贵州', officialSourceName: '贵州省招生考试院' },
  SC: { code: 'SC', name: '四川省', shortName: '四川', officialSourceName: '四川省教育考试院' },
  HB: { code: 'HB', name: '湖北省', shortName: '湖北', officialSourceName: '湖北省教育考试院' },
  AH: { code: 'AH', name: '安徽省', shortName: '安徽', officialSourceName: '安徽省教育招生考试院' },
  GX: { code: 'GX', name: '广西壮族自治区', shortName: '广西', officialSourceName: '广西壮族自治区招生考试院' },
  HI: { code: 'HI', name: '海南省', shortName: '海南', officialSourceName: '海南省考试局' },
  YN: { code: 'YN', name: '云南省', shortName: '云南', officialSourceName: '云南省招生考试院' },
  HA: { code: 'HA', name: '河南省', shortName: '河南', officialSourceName: '河南省教育考试院' },
}

export const PROVINCE_LIST: ProvinceText[] = [
  PROVINCE_TEXTS.GZ,
  PROVINCE_TEXTS.SC,
  PROVINCE_TEXTS.HB,
  PROVINCE_TEXTS.AH,
  PROVINCE_TEXTS.GX,
  PROVINCE_TEXTS.HI,
  PROVINCE_TEXTS.YN,
  PROVINCE_TEXTS.HA,
]

export const DEFAULT_PROVINCE_CODE: ProvinceCode = 'GZ'

export function normalizeProvinceCode(value?: string): ProvinceCode {
  const upper = (value || '').toUpperCase()
  return (upper in PROVINCE_TEXTS ? upper : DEFAULT_PROVINCE_CODE) as ProvinceCode
}

export function getProvinceText(code?: string): ProvinceText {
  return PROVINCE_TEXTS[normalizeProvinceCode(code)]
}

/** 就绪度对用户的说法。不美化，缺数据就说缺数据。 */
export const READINESS_LABEL: Record<string, string> = {
  LOCKED: '数据未导入',
  QUERY_ONLY: '仅开放查询',
  ESTIMATE: '历史估算',
  FULL: '官方数据完整',
}
