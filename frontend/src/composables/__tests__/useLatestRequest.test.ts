import { effectScope } from 'vue'
import { describe, expect, it } from 'vitest'
import { useLatestRequest } from '../useLatestRequest'

describe('useLatestRequest', () => {
  it('invalidates earlier requests and all outstanding requests when the scope is disposed', () => {
    const scope = effectScope()
    const begin = scope.run(() => useLatestRequest())!
    const first = begin()
    expect(first()).toBe(true)
    const second = begin()
    expect(first()).toBe(false)
    expect(second()).toBe(true)
    scope.stop()
    expect(second()).toBe(false)
  })
})
