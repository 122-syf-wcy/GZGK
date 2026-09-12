import { describe, expect, it, vi } from 'vitest'
import { validateApiContract, volunteerPlanSchema } from '../api-contracts'

describe('volunteerPlanSchema', () => {
  it('接受合法方案并透传未知字段', () => {
    const result = volunteerPlanSchema.safeParse({
      id: 1,
      accessKey: 'abc',
      items: [{ index: 1, universityName: 'A', majorName: 'B', gradient: '稳', futureField: 'x' }],
      extra: true,
    })
    expect(result.success).toBe(true)
  })

  it('拒绝缺失关键字段的条目', () => {
    const result = volunteerPlanSchema.safeParse({
      id: 1,
      items: [{ index: 1 }],
    })
    expect(result.success).toBe(false)
  })
})

describe('validateApiContract', () => {
  it('契约不符时仅告警不抛错', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    expect(() => validateApiContract('/volunteer/plan', { id: 'not-a-number', items: [] })).not.toThrow()
    expect(warn).toHaveBeenCalled()
    warn.mockRestore()
  })

  it('未注册的 URL 静默跳过', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    validateApiContract('/announcement/current', { anything: true })
    expect(warn).not.toHaveBeenCalled()
    warn.mockRestore()
  })
})
