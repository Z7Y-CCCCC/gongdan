import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import App from '../App.vue'
import { useAuth } from '../composables/useAuth'
import { setUiLocale } from '../i18n'

const mocks = vi.hoisted(() => ({
  fetchCurrentFactory: vi.fn(), fetchUsers: vi.fn(), fetchDepartments: vi.fn(), fetchUnits: vi.fn(),
  fetchDefects: vi.fn(), fetchProcesses: vi.fn(), fetchProcessRoutes: vi.fn(),
  fetchNotificationUnreadCount: vi.fn(), fetchPluginRuntimeManifest: vi.fn(), fetchNotifications: vi.fn(),
  markNotificationRead: vi.fn(), markAllNotificationsRead: vi.fn(),
  fetchWorkOrders: vi.fn(), fetchReports: vi.fn(), fetchSalesOrders: vi.fn()
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
    global: { stubs: { KeepAlive: false, InternalChatPage: true, HomeDashboardPage: true,
      WorkOrderPage: { name: 'WorkOrderPage', props: ['token', 'focusTarget'], emits: ['clear-focus'], template: '<section />' }
    } }
  })
}

describe('App uses the same authenticated session as API consumers', () => {
  let wrapper: VueWrapper<any> | undefined
  const session = useAuth()
  beforeEach(() => {
    vi.resetAllMocks()
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))
    session.logout()
    localStorage.clear()
    window.location.hash = ''
    mocks.fetchCurrentFactory.mockResolvedValue({ id: 2, code: 'FACTORY2', name: '第二工厂', enabled: true })
    mocks.fetchUsers.mockResolvedValue([])
    for (const name of ['fetchDepartments', 'fetchUnits', 'fetchDefects', 'fetchProcesses', 'fetchProcessRoutes'] as const) {
      mocks[name].mockResolvedValue({ items: [], total: 0 })
    }
    mocks.fetchNotificationUnreadCount.mockResolvedValue(0)
    mocks.fetchNotifications.mockResolvedValue({ items: [], total: 0, unreadCount: 0 })
    mocks.markNotificationRead.mockResolvedValue({})
    mocks.markAllNotificationsRead.mockResolvedValue({})
    mocks.fetchPluginRuntimeManifest.mockResolvedValue([])
    for (const name of ['fetchWorkOrders', 'fetchReports', 'fetchSalesOrders'] as const) {
      mocks[name].mockResolvedValue({ items: [], total: 0 })
    }
  })
  afterEach(async () => {
    wrapper?.unmount()
    wrapper = undefined
    session.logout()
    window.location.hash = ''
    await flushPromises()
    await vi.dynamicImportSettled()
    vi.unstubAllGlobals()
  })

  it('names workspace tabs consistently with the actual screen', async () => {
    session.persistSession({ token: 'admin-token', user, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.workspaceTabs[0].label).toBe('主页')
    vm.openNotificationCenter()
    await flushPromises()
    expect(vm.workspaceTabs.find((tab: any) => tab.id === 'notifications')?.label).toBe('通知中心')
    vm.activeMainMenu = 'production'
    vm.activeProductionMenu = 'workbench'
    await flushPromises()
    expect(vm.workspaceTabs.find((tab: any) => tab.id === 'production-workbench')?.label).toBe('报工工作台')
  })

  it('exposes the account menu through a native keyboard-operable trigger', async () => {
    session.persistSession({ token: 'admin-token', user, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    const trigger = wrapper.get('button[aria-label="账户菜单"]')
    expect(trigger.attributes('aria-expanded')).toBe('false')
    await trigger.trigger('click')
    expect(trigger.attributes('aria-expanded')).toBe('true')
    await trigger.trigger('keydown', { key: 'Escape' })
    expect(trigger.attributes('aria-expanded')).toBe('false')
  })

  it('opens the context-menu target in an isolated window, not just the active homepage', async () => {
    session.persistSession({ token: 'admin-token', user, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    const vm = wrapper.vm as any
    vm.jumpFromQuickApp('basedata', 'bom')
    await flushPromises()
    vm.jumpFromQuickApp('home')
    await flushPromises()
    vm.tabContextMenu.tabId = 'basedata-bom'
    const opened = { opener: window, location: { href: '' } }
    const open = vi.spyOn(window, 'open').mockReturnValue(opened as unknown as Window)
    vm.openCurrentTabInNewWindow()
    expect(open).toHaveBeenCalledWith('about:blank', '_blank')
    expect(opened.opener).toBeNull()
    expect(new URL(opened.location.href).searchParams.get('workspace')).toBe('basedata-bom')
    open.mockRestore()
  })

  it('removes shell shortcut handlers when the app is unmounted', async () => {
    session.persistSession({ token: 'admin-token', user, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    const vm = wrapper.vm as any
    wrapper.unmount()
    wrapper = undefined
    const event = new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(false)
    expect(vm.showWorkspaceSearch).toBe(false)
  })

  it('persists a real language switch and translates the login shell', async () => {
    wrapper = mountShell()
    const vm = wrapper.vm as any
    vm.selectLanguage('en-US')
    await flushPromises()
    expect(localStorage.getItem('heihu_locale')).toBe('en-US')
    expect(document.documentElement.lang).toBe('en-US')
    expect(wrapper.text()).toContain('Sign in')
    expect(wrapper.text()).not.toContain('请输入密码')
    vm.selectLanguage('zh-CN')
    await flushPromises()
    expect(wrapper.text()).toContain('登录')
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

  it('routes the home event to the work-order page with its exact target and clears it on logout', async () => {
    session.persistSession({ token: 'admin-token', user, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    wrapper.getComponent({ name: 'HomeDashboardPage' }).vm.$emit('view-work-order', 5001)
    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.activeMainMenu).toBe('production')
    expect(vm.activeProductionMenu).toBe('workorder')
    expect(wrapper.getComponent({ name: 'WorkOrderPage' }).props()).toMatchObject({ token: 'admin-token', focusTarget: { id: 5001 } })
    session.logout()
    await flushPromises()
    expect(vm.workOrderFocusTarget).toBeNull()
  })

  it('clears a closed target and creates a fresh lookup when the same home order is opened again', async () => {
    session.persistSession({ token: 'admin-token', user, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    const vm = wrapper.vm as any
    vm.viewHomeWorkOrder(42)
    await flushPromises()
    const first = vm.workOrderFocusTarget
    wrapper.getComponent({ name: 'WorkOrderPage' }).vm.$emit('clear-focus')
    await flushPromises()
    expect(vm.workOrderFocusTarget).toBeNull()
    vm.activeMainMenu = 'home'
    vm.viewHomeWorkOrder(42)
    await flushPromises()
    expect(vm.workOrderFocusTarget).toEqual({ id: 42 })
    expect(vm.workOrderFocusTarget).not.toBe(first)
    vm.activeMainMenu = 'home'
    await flushPromises()
    expect(vm.workOrderFocusTarget).toBeNull()
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

  it('does not restore another account notification count after logout', async () => {
    session.persistSession({ token: 'worker-token', user: { ...user, role: 'PRODUCTION' }, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    let resolve!: (count: number) => void
    mocks.fetchNotificationUnreadCount.mockReturnValue(new Promise<number>(yes => { resolve = yes }))
    const load = (wrapper.vm as any).loadNotificationCount()
    session.logout()
    await flushPromises()
    resolve(9)
    await load
    expect((wrapper.vm as any).notificationUnreadCount).toBe(0)
  })

  it('ignores a superseded dropdown response', async () => {
    session.persistSession({ token: 'worker-token', user: { ...user, role: 'PRODUCTION' }, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    let resolve!: (value: any) => void
    mocks.fetchNotifications.mockReturnValueOnce(new Promise(yes => { resolve = yes }))
      .mockResolvedValueOnce({ items: [{ id: 2 }], unreadCount: 2 })
    const vm = wrapper.vm as any
    const old = vm.loadNotifications()
    await vm.loadNotifications()
    resolve({ items: [{ id: 1 }], unreadCount: 1 })
    await old
    expect(vm.notificationItems).toEqual([{ id: 2 }])
    expect(vm.notificationUnreadCount).toBe(2)
  })

  it('sends one notification read while navigation is pending and refreshes counts', async () => {
    session.persistSession({ token: 'worker-token', user: { ...user, role: 'PRODUCTION' }, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    let resolve!: (value: any) => void
    mocks.markNotificationRead.mockReturnValue(new Promise(yes => { resolve = yes }))
    mocks.fetchNotificationUnreadCount.mockResolvedValue(4)
    const vm = wrapper.vm as any
    vm.notificationUnreadCount = 8
    const item = { id: 1, unread: true, state: 'UNREAD', targetMain: 'home' }
    const first = vm.openNotification(item)
    const second = vm.openNotification(item)
    expect(mocks.markNotificationRead).toHaveBeenCalledTimes(1)
    resolve({})
    await Promise.all([first, second])
    expect(vm.notificationUnreadCount).toBe(4)
  })

  it('uses scoped system translations in the notification preview and restores original Chinese on language changes', async () => {
    const system = { id: 1, title: '报工被驳回', eventType: 'REPORT_REJECTED', content: '保存 / 删除 / 审批人 编辑 / 客户原文',
      unread: true, priority: 'HIGH', state: 'UNREAD', createdAt: '2026-11-24T10:00:00' }
    const custom = { ...system, id: 2, title: '保存', eventType: 'CUSTOM_MESSAGE' }
    session.persistSession({ token: 'worker-token', user: { ...user, role: 'PRODUCTION' }, isSuperAdmin: false })
    mocks.fetchNotifications.mockResolvedValue({ items: [system, custom], total: 2, unreadCount: 2 })
    setUiLocale('en-US')
    wrapper = mountShell()
    await flushPromises()
    const vm = wrapper.vm as any
    await vm.toggleNotifications()
    await flushPromises()
    expect(wrapper.findAll('.notification-content strong').map(node => node.text())).toEqual(['Report rejected', '保存'])
    expect(wrapper.findAll('.notification-content > span').map(node => node.text())).toEqual([
      '保存 / 删除 / Reviewed by 编辑 / 客户原文', custom.content
    ])
    setUiLocale('zh-CN')
    await flushPromises()
    expect(wrapper.findAll('.notification-content strong').map(node => node.text())).toEqual(['报工被驳回', '保存'])
    expect(wrapper.findAll('.notification-content > span').map(node => node.text())).toEqual([system.content, custom.content])
    expect(vm.notificationItems).toEqual([system, custom])
  })
})
