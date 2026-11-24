import { afterEach, describe, expect, it } from 'vitest'
import { formatUiDate, setUiTimezone } from '../date'
afterEach(() => setUiTimezone('Asia/Shanghai'))
describe('display time zones without changing source dates', () => {
  it('treats offset-free backend timestamps as factory Shanghai time, not the browser zone', () => {
    setUiTimezone('Asia/Shanghai')
    expect(formatUiDate('2026-11-24T09:30:00')).toBe('2026-11-24 09:30')
    setUiTimezone('America/New_York')
    expect(formatUiDate('2026-11-24T09:30:00')).toBe('2026-11-23 20:30')
  })
  it('honors explicit offsets and leaves calendar-only dates untouched', () => {
    setUiTimezone('Asia/Shanghai')
    expect(formatUiDate('2026-11-24T01:30:00Z', true)).toBe('2026-11-24 09:30:00')
    expect(formatUiDate('2026-11-24')).toBe('2026-11-24')
    expect(formatUiDate(null)).toBe('-')
  })
  it('rejects invalid zones and does not lose invalid source information', () => {
    expect(setUiTimezone('NOT_A_ZONE')).toBe(false)
    expect(formatUiDate('Invalid date')).toBe('Invalid date')
  })
})
