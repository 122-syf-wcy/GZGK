import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getBatchSupportByProvince, hasBatchSupportEndpoint } from '../api/volunteer'

vi.mock('../api/request', () => ({
  default: {
    get: vi.fn((url: string) => Promise.resolve({ data: { code: 0, message: 'ok', data: { url } } })),
  },
}))

describe('volunteer batch-support province dispatch', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uses real backend endpoints for every configured province', async () => {
    const http = (await import('../api/request')).default

    for (const code of ['GZ', 'SC', 'AH', 'HB', 'GX', 'HI', 'YN', 'HA', 'CQ', 'GS', 'XJ'] as const) {
      await getBatchSupportByProvince(code)
    }

    expect(http.get).toHaveBeenNthCalledWith(1, '/volunteer/gz/batch-support')
    expect(http.get).toHaveBeenNthCalledWith(2, '/volunteer/sc/batch-support')
    expect(http.get).toHaveBeenNthCalledWith(3, '/volunteer/ah/batch-support')
    expect(http.get).toHaveBeenNthCalledWith(4, '/volunteer/hb/batch-support')
    expect(http.get).toHaveBeenNthCalledWith(5, '/volunteer/gx/batch-support')
    expect(http.get).toHaveBeenNthCalledWith(6, '/volunteer/hi/batch-support')
    expect(http.get).toHaveBeenNthCalledWith(7, '/volunteer/yn/batch-support')
    expect(http.get).toHaveBeenNthCalledWith(8, '/volunteer/ha/batch-support')
    expect(http.get).toHaveBeenNthCalledWith(9, '/volunteer/cq/batch-support')
    expect(http.get).toHaveBeenNthCalledWith(10, '/volunteer/gs/batch-support')
    expect(http.get).toHaveBeenNthCalledWith(11, '/volunteer/xj/batch-support')
  })

  it('exposes endpoint support separately from open workspace state', () => {
    expect(hasBatchSupportEndpoint('GZ')).toBe(true)
    expect(hasBatchSupportEndpoint('SC')).toBe(true)
    expect(hasBatchSupportEndpoint('AH')).toBe(true)
    expect(hasBatchSupportEndpoint('HB')).toBe(true)
    expect(hasBatchSupportEndpoint('GX')).toBe(true)
    expect(hasBatchSupportEndpoint('HI')).toBe(true)
    expect(hasBatchSupportEndpoint('YN')).toBe(true)
    expect(hasBatchSupportEndpoint('HA')).toBe(true)
    expect(hasBatchSupportEndpoint('CQ')).toBe(true)
    expect(hasBatchSupportEndpoint('GS')).toBe(true)
    expect(hasBatchSupportEndpoint('XJ')).toBe(true)
    expect(hasBatchSupportEndpoint('UNKNOWN')).toBe(false)
  })

  it('does not fall back unknown province codes to GZ batch support', async () => {
    const http = (await import('../api/request')).default

    await expect(getBatchSupportByProvince('UNKNOWN')).rejects.toThrow('未知省份暂未配置批次支持接口')
    expect(http.get).not.toHaveBeenCalled()
  })
})
