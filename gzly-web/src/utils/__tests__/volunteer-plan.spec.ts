import { describe, expect, it } from 'vitest'
import {
  buildPlanModeItems,
  cloneGradientRanges,
  GRADIENT_RANGE_PRESETS,
  groupGradientCount,
  presetGradientRanges,
  summarizePlan,
} from '../volunteer-plan'
import type { VolunteerItem } from '@/types'

function item(index: number, gradient: VolunteerItem['gradient'], chanceScore = 60): VolunteerItem {
  return {
    index,
    universityName: `测试大学${index}`,
    majorName: '计算机科学与技术',
    province: '贵州',
    city: '贵阳',
    tags: [],
    gradient,
    historyMinScore: 500,
    historyMinRank: 30000,
    referenceYear: 2025,
    resubjectRequirement: '不限',
    chanceScore,
  }
}

describe('presetGradientRanges', () => {
  it('返回预设的深拷贝，修改不污染源', () => {
    const ranges = presetGradientRanges('均衡型')
    ranges.chong.rankOffsetMin = -99999
    expect(GRADIENT_RANGE_PRESETS.均衡型.chong.rankOffsetMin).toBe(-10000)
  })

  it('未知模式回退均衡型', () => {
    const ranges = presetGradientRanges('不存在的模式' as never)
    expect(ranges).toEqual(GRADIENT_RANGE_PRESETS.均衡型)
  })
})

describe('cloneGradientRanges', () => {
  it('逐档深拷贝', () => {
    const source = presetGradientRanges('保守型')
    const cloned = cloneGradientRanges(source)
    expect(cloned).toEqual(source)
    expect(cloned.bao).not.toBe(source.bao)
  })
})

describe('summarizePlan', () => {
  it('统计均值与数量', () => {
    const summary = summarizePlan([item(1, '冲', 40), item(2, '稳', 80)])
    expect(summary.total).toBe(2)
    expect(summary.avgChance).toBe(60)
  })

  it('空列表不除零', () => {
    const summary = summarizePlan([])
    expect(summary.total).toBe(0)
    expect(summary.avgChance).toBe(0)
  })
})

describe('buildPlanModeItems', () => {
  it('均衡型保持原顺序', () => {
    const items = [item(2, '稳'), item(1, '冲')]
    const result = buildPlanModeItems(items, '均衡型')
    expect(result.map(i => i.index)).toEqual([1, 2])
  })

  it('非均衡型重排后 index 连续', () => {
    const items = [item(1, '冲'), item(2, '稳'), item(3, '保'), item(4, '垫')]
    const result = buildPlanModeItems(items, '保守型')
    expect(result).toHaveLength(4)
    expect(result.map(i => i.index)).toEqual([1, 2, 3, 4])
  })
})

describe('groupGradientCount', () => {
  it('按冲稳保垫分组计数', () => {
    const counts = groupGradientCount([item(1, '冲'), item(2, '稳'), item(3, '稳')])
    expect(counts.find(c => c.key === '稳')?.count).toBe(2)
    expect(counts.find(c => c.key === '垫')?.count).toBe(0)
  })
})
