export type OptionalNumericInput = string | number | null | undefined

export function toOptionalInteger(value: OptionalNumericInput): number | null {
  if (value == null) {
    return null
  }

  if (typeof value === 'number') {
    return Number.isFinite(value) ? Math.round(value) : null
  }

  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }

  const num = Number(trimmed)
  return Number.isFinite(num) ? Math.round(num) : null
}
