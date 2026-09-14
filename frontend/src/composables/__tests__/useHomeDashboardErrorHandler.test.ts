import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { ApiError } from '../../api'
import { useHomeDashboardErrorHandler } from '../useHomeDashboardErrorHandler'
import { useAuth } from '../useAuth'

describe('home dashboard error handling', () => {
  it('propagates a child section 403 to the dashboard-level forbidden state', () => {
    let parent!: ReturnType<typeof useHomeDashboardErrorHandler>
    let child!: ReturnType<typeof useHomeDashboardErrorHandler>
    const Child = defineComponent({ setup() { child = useHomeDashboardErrorHandler(); return () => h('div') } })
    const Parent = defineComponent({ setup() { parent = useHomeDashboardErrorHandler(); return () => h(Child) } })
    const wrapper = mount(Parent)
    child.handleApiError(new ApiError('无权限', 403))
    expect(parent.forbidden.value).toBe(true)
    expect(parent.forbiddenMessage.value).toBe('无权限访问主页')
    wrapper.unmount()
  })

  it('does not unconditionally clear a newer session in response to a consumed 401', () => {
    const session = useAuth()
    session.token.value = 'new-session'
    const handler = useHomeDashboardErrorHandler()
    expect(handler.handleApiError(new ApiError('旧请求已过期', 401))).toBe(true)
    expect(session.token.value).toBe('new-session')
    session.logout()
  })
})
