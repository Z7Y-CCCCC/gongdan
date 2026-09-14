import { describe, expect, it } from 'vitest'
import { resolveInventoryTimeRange } from '../inventoryTimeRange'

describe('inventory time ranges', () => {
  const now = new Date(2026, 8, 13, 14, 15, 16) // Sunday, local wall-clock time
  const range = (timeRange: string) => ({ timeRange, startTime: '', endTime: '' })

  it('includes the entire end date of a custom range', () => {
    expect(resolveInventoryTimeRange({ timeRange: 'custom', startTime: '2026-09-01', endTime: '2026-09-11' }, now))
      .toEqual({ startTime: '2026-09-01 00:00:00', endTime: '2026-09-11 23:59:59' })
  })

  it('uses Monday consistently as the start of the week, including on Sunday', () => {
    expect(resolveInventoryTimeRange(range('week'), now))
      .toEqual({ startTime: '2026-09-07 00:00:00', endTime: '2026-09-13 14:15:16' })
  })

  it('resolves today at request time and ignores obsolete preset input values', () => {
    expect(resolveInventoryTimeRange({ timeRange: 'today', startTime: '2026-01-01', endTime: '2026-01-01' }, now))
      .toEqual({ startTime: '2026-09-13 00:00:00', endTime: '2026-09-13 23:59:59' })
  })

  it('does not apply old dates when the range filter is cleared', () => {
    expect(resolveInventoryTimeRange({ timeRange: '', startTime: '2026-09-01', endTime: '2026-09-11' }, now)).toEqual({})
  })
})
