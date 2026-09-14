import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import App from '../App.vue'
import { useAuth } from '../composables/useAuth'

const mocks = vi.hoisted(() => ({
  fetchCurrentFactory: vi.fn(), fetchUsers: vi.fn(), fetchDepartments: vi.fn(), fetchUnits: vi.fn(),
  fetchDefects: vi.fn(), fetchProcesses: vi.fn(), fetchProcessRoutes: vi.fn(),
  fetchNotificationUnreadCount: vi.fn(), fetchPluginRuntimeManifest: vi.fn()
}))
vi.mock('../api', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api')>(), ...mocks
}))

const user = {
  id: 1, username: 'admin', name: '管理员', role: 'ADMIN' as const, roleLabel: '管理员',
  virtualUser: false, enabled: true, createdAt: '2026-09-01'
}

// These tests exercise the shell session, not its lazy page implementations.
// Stub by registration key so async imports cannot outlive the test environment.
function mountShell() {
  return shallowMount(App, {
    global: { stubs: { InternalChatPage: true, HomeDashboardPage: true } }
  })
}

describe('App uses the same authenticated session as API consumers', () => {
  let wrapper: VueWrapper<any> | undefined
  const session = useAuth()
  beforeEach(() => {
    vi.resetAllMocks()
    session.logout()
    localStorage.clear()
    window.location.hash = ''
    mocks.fetchCurrentFactory.mockResolvedValue({ id: 2, code: 'FACTORY2', name: '第二工厂', enabled: true })
    mocks.fetchUsers.mockResolvedValue([])
    for (const name of ['fetchDepartments', 'fetchUnits', 'fetchDefects', 'fetchProcesses', 'fetchProcessRoutes'] as const) {
      mocks[name].mockResolvedValue({ items: [], total: 0 })
    }
    mocks.fetchNotificationUnreadCount.mockResolvedValue(0)
    mocks.fetchPluginRuntimeManifest.mockResolvedValue([])
  })
  afterEach(async () => {
    wrapper?.unmount()
    wrapper = undefined
    session.logout()
    window.location.hash = ''
    await flushPromises()
    await vi.dynamicImportSettled()
  })

  it('immediately returns the shell to login when the shared session expires', async () => {
    window.location.hash = '#/message-center'
    session.persistSession({ token: 'token-one', user, isSuperAdmin: false })
    wrapper = mountShell()
    expect(wrapper.find('.admin-shell').exists()).toBe(true)
    ;(wrapper.vm as any).showCreateModal = true
    ;(wrapper.vm as any).successUserPassword = 'temporary-password'
    session.logout()
    await flushPromises()
    expect(wrapper.find('.login-page').exists()).toBe(true)
    expect(wrapper.find('.admin-shell').exists()).toBe(false)
    expect((wrapper.vm as any).showCreateModal).toBe(false)
    expect((wrapper.vm as any).successUserPassword).toBe('')
  })

  it('clears the shared composable when logout is initiated from the shell', async () => {
    window.location.hash = '#/message-center'
    session.persistSession({ token: 'token-one', user, isSuperAdmin: false })
    wrapper = mountShell()
    ;(wrapper.vm as any).logout()
    await flushPromises()
    expect(session.token.value).toBe('')
    expect(session.currentUser.value).toBeNull()
  })

  it('restores the actual factory after a single-factory phone login', async () => {
    session.setFactoryCode('OLD_FACTORY')
    wrapper = mountShell()
    await (wrapper.vm as any).handlePhoneLoginResult({ token: 'phone-token', user })
    expect(mocks.fetchCurrentFactory).toHaveBeenCalledWith('phone-token')
    expect(session.currentFactoryCode.value).toBe('FACTORY2')
    expect(localStorage.getItem('heihu_factory_code')).toBe('FACTORY2')
  })

  it('does not block a production account behind admin-only bootstrap requests', async () => {
    session.persistSession({ token: 'worker-token', user: { ...user, role: 'PRODUCTION' }, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    expect(mocks.fetchUsers).not.toHaveBeenCalled()
    expect((wrapper.vm as any).workspaceLoadingVisible).toBe(false)
  })
})
