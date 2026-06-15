import { describe, expect, it } from 'vitest'
import { toOptionalInteger } from '../utils/numberInput'

describe('toOptionalInteger', () => {
  it('accepts numeric values returned by Vue number inputs', () => {
    expect(toOptionalInteger(500)).toBe(500)
    expect(toOptionalInteger(57000)).toBe(57000)
    expect(toOptionalInteger(499.6)).toBe(500)
  })

  it('accepts string values and trims whitespace', () => {
    expect(toOptionalInteger(' 500 ')).toBe(500)
    expect(toOptionalInteger('57000')).toBe(57000)
  })

  it('returns null for blank, missing, or invalid values', () => {
    expect(toOptionalInteger('')).toBeNull()
    expect(toOptionalInteger('   ')).toBeNull()
    expect(toOptionalInteger(null)).toBeNull()
    expect(toOptionalInteger(undefined)).toBeNull()
    expect(toOptionalInteger('abc')).toBeNull()
    expect(toOptionalInteger(Number.NaN)).toBeNull()
  })
})
