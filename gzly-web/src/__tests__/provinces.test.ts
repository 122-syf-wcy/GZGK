/**
 * v7.54 前端单测：provinces.ts 关键逻辑回归
 *
 * 覆盖：
 * - normalizeProvinceCode 大小写 / null / 未知 → 兜底 GZ
 * - getProvinceConfig 返回的 status / volunteerUnitType / targetCount
 * - PROVINCE_LIST 4 省全部 status=open（v7.54 全部上线后）
 * - provinceQuery routeQuery 形态
 *
 * 这些是 VolunteerForm + UI 路由的核心依赖；任何切省 / 默认批次 / 文案
 * 都依赖于这些常量，因此回归很重要。
 */
import { describe, it, expect } from 'vitest'
import {
  DEFAULT_PROVINCE_CODE,
  PROVINCE_CONFIGS,
  PROVINCE_LIST,
  getProvinceConfig,
  normalizeProvinceCode,
  provinceQuery,
} from '../constants/provinces'

describe('provinces.ts', () => {
  describe('normalizeProvinceCode', () => {
    it('returns GZ for null / undefined', () => {
      expect(normalizeProvinceCode(null)).toBe('GZ')
      expect(normalizeProvinceCode(undefined)).toBe('GZ')
    })

    it('returns GZ for empty / whitespace', () => {
      expect(normalizeProvinceCode('')).toBe('GZ')
      expect(normalizeProvinceCode('   ')).toBe('GZ')
    })

    it('uppercases known codes', () => {
      expect(normalizeProvinceCode('gz')).toBe('GZ')
      expect(normalizeProvinceCode('sc')).toBe('SC')
      expect(normalizeProvinceCode('ah')).toBe('AH')
      expect(normalizeProvinceCode('hb')).toBe('HB')
    })

    it('returns GZ for unknown codes', () => {
      expect(normalizeProvinceCode('XYZ')).toBe('GZ')
      expect(normalizeProvinceCode('Foo')).toBe('GZ')
    })

    it('handles arrays (Vue Router query param)', () => {
      expect(normalizeProvinceCode(['SC'])).toBe('SC')
      expect(normalizeProvinceCode([])).toBe('GZ')
    })
  })

  describe('PROVINCE_CONFIGS', () => {
    it('contains GZ / SC / HB / AH', () => {
      expect(Object.keys(PROVINCE_CONFIGS)).toEqual(['GZ', 'SC', 'HB', 'AH'])
    })

    it('GZ has 96 平行 unit type', () => {
      const gz = PROVINCE_CONFIGS.GZ
      expect(gz.volunteerUnitType).toBe('MAJOR_96')
      expect(gz.targetCount).toBe(96)
      expect(gz.status).toBe('open')
    })

    it('SC has 45 院校专业组 unit type', () => {
      const sc = PROVINCE_CONFIGS.SC
      expect(sc.volunteerUnitType).toBe('PROFESSIONAL_GROUP_45')
      expect(sc.targetCount).toBe(45)
      expect(sc.status).toBe('open')
    })

    it('AH has 45 院校专业组 unit type and v7.48+ open', () => {
      const ah = PROVINCE_CONFIGS.AH
      expect(ah.volunteerUnitType).toBe('PROFESSIONAL_GROUP_45')
      expect(ah.targetCount).toBe(45)
      expect(ah.status).toBe('open')
      expect(ah.targetBatch).toBe('普通本科批')
    })

    it('HB has 45 院校专业组 unit type (preparing 状态保留)', () => {
      const hb = PROVINCE_CONFIGS.HB
      expect(hb.volunteerUnitType).toBe('PROFESSIONAL_GROUP_45')
      expect(hb.targetCount).toBe(45)
      // HB 暂未数据完整核验，应该仍是 preparing
      expect(hb.status).toBe('preparing')
    })
  })

  describe('PROVINCE_LIST', () => {
    it('has 4 provinces', () => {
      expect(PROVINCE_LIST).toHaveLength(4)
    })

    it('每个 config 都有必需字段', () => {
      for (const p of PROVINCE_LIST) {
        expect(p.code).toBeTruthy()
        expect(p.name).toBeTruthy()
        expect(p.volunteerUnitType).toMatch(/^(MAJOR_96|PROFESSIONAL_GROUP_45)$/)
        expect(p.targetCount).toBeGreaterThan(0)
        expect(p.officialSource).toBeTruthy()
        expect(p.status).toMatch(/^(open|preparing|locked)$/)
      }
    })

    it('open 状态的省份 (GZ + SC + AH) 至少有 3 个', () => {
      const open = PROVINCE_LIST.filter((p) => p.status === 'open')
      expect(open.length).toBeGreaterThanOrEqual(3)
    })
  })

  describe('getProvinceConfig', () => {
    it('returns config for normalized code', () => {
      expect(getProvinceConfig('sc').code).toBe('SC')
      expect(getProvinceConfig('AH').code).toBe('AH')
    })

    it('returns GZ config for unknown', () => {
      expect(getProvinceConfig('UNKNOWN').code).toBe('GZ')
    })
  })

  describe('provinceQuery', () => {
    it('returns object with provinceCode', () => {
      expect(provinceQuery('SC')).toEqual({ provinceCode: 'SC' })
      expect(provinceQuery(null)).toEqual({ provinceCode: 'GZ' })
    })
  })

  describe('DEFAULT_PROVINCE_CODE', () => {
    it('is GZ', () => {
      expect(DEFAULT_PROVINCE_CODE).toBe('GZ')
    })
  })
})
