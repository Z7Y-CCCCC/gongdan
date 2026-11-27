import { describe, expect, it } from 'vitest'
import { buildWorkspaceUrl, parseWorkspaceKey, readWorkspaceRoute } from '../workspaceRoute'

describe('workspace new-window routes', () => {
  it.each(['production-workorder', 'basedata-bom', 'custom-customer', 'inventory-balance', 'sysconfig-external-data-sources', 'monitor-cacheList', 'notifications', 'plugin-quality-management'])('restores %s without changing origin', key => {
    const url = buildWorkspaceUrl('https://example.invalid/app/?existing=1#/old', key)
    expect(new URL(url).origin).toBe('https://example.invalid')
    expect(new URL(url).pathname).toBe('/app/')
    expect(new URL(url).searchParams.get('existing')).toBe('1')
    expect(new URL(url).hash).toBe('')
    expect(readWorkspaceRoute(url)).toEqual(parseWorkspaceKey(key))
  })
  it.each(['https://evil.invalid', '../sysconfig-user', 'production-unknown', 'plugin-', 'plugin-a/../../other', 'custom-not-supported', 'a'.repeat(129)])('rejects invalid route %s', key => {
    expect(parseWorkspaceKey(key)).toBeNull()
    expect(() => buildWorkspaceUrl('https://example.invalid/', key)).toThrow()
  })
  it('reopens an exact advanced section and rejects an unknown one', () => {
    const url = buildWorkspaceUrl('https://example.invalid/', 'advanced-field_event')
    expect(readWorkspaceRoute(url)).toEqual({ main: 'advanced', sub: 'field_event' })
    expect(parseWorkspaceKey('advanced-unknown')).toBeNull()
  })
})
