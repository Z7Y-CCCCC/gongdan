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
  fetchWorkOrders: vi.fn(), fetchReports: vi.fn(), fetchSalesOrders: vi.fn(), logoutSession: vi.fn()
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
    global: { stubs: { KeepAlive: false, InternalChatPage: true, HomeDashboardPage: true, UReportPage: true,
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
    mocks.logoutSession.mockResolvedValue(undefined)
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

  async function dirtyReportShell() {
    session.persistSession({ token: 'admin-token', user, isSuperAdmin: true })
    wrapper = mountShell()
    await flushPromises()
    const vm = wrapper.vm as any
    vm.activeMainMenu = 'analytics'
    await flushPromises()
    let dirty = true
    const confirm = vi.fn((_reason: string) => false)
    vm.workspaceLeaveGuards.register('analytics', { isDirty: () => dirty, confirmLeave: confirm })
    return { vm, confirm, clean: () => { dirty = false } }
  }

  it('keeps normal production reports available but denies every ordinary-admin designer entry', async () => {
    session.persistSession({ token: 'admin-token', user, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    const vm = wrapper.vm as any
    expect(wrapper.findAll('a.sub-item').some(node => node.text() === '高级报表')).toBe(false)
    vm.activeMainMenu = 'analytics'
    expect(vm.activeMainMenu).toBe('home')
    vm.jumpFromQuickApp('analytics')
    expect(vm.activeMainMenu).toBe('home')
    expect(vm.workspaceTabs.some((tab: any) => tab.id === 'analytics')).toBe(false)
    expect(vm.actionMessage).toBe('高级报表仅平台超级管理员可用')
    vm.activeMainMenu = 'report'
    expect(vm.activeMainMenu).toBe('report')
  })

  it('unmounts a designer after platform permission disappears without a discard veto', async () => {
    const { vm, confirm } = await dirtyReportShell()
    expect(vm.activeMainMenu).toBe('analytics')
    session.isSuperAdmin.value = false
    expect(vm.activeMainMenu).toBe('home')
    expect(vm.workspaceLeaveGuards.hasUnsavedChanges()).toBe(false)
    expect(confirm).not.toHaveBeenCalled()
  })

  it('blocks both direct sidebar departure and workspace/quick-app navigation before deactivation', async () => {
    const { vm, confirm } = await dirtyReportShell()
    const home = wrapper!.findAll('a.nav-item').find(node => node.text() === '主页')!
    await home.trigger('click')
    expect(vm.activeMainMenu).toBe('analytics')
    vm.activateWorkspaceTab(vm.workspaceTabs[0])
    expect(vm.activeWorkspaceTabId).toBe('analytics')
    const config = vm.activeConfigMenu
    vm.jumpFromQuickApp('sysconfig', 'user')
    expect(vm.activeMainMenu).toBe('analytics')
    expect(vm.activeConfigMenu).toBe(config)
    expect(confirm.mock.calls.map(call => call[0])).toEqual(['navigate', 'navigate', 'navigate'])
  })

  it.each(['single', 'all', 'right', 'others'])('does not remove dirty tabs on cancelled %s closure', async mode => {
    const { vm, confirm } = await dirtyReportShell()
    const ids = vm.workspaceTabs.map((tab: any) => tab.id)
    if (mode === 'single') vm.closeWorkspaceTab('analytics')
    if (mode === 'all') vm.closeAllTabs()
    if (mode === 'right') { vm.tabContextMenu.tabId = 'home'; vm.closeTabsRight() }
    if (mode === 'others') { vm.tabContextMenu.tabId = 'home'; vm.closeOtherTabs() }
    expect(vm.workspaceTabs.map((tab: any) => tab.id)).toEqual(ids)
    expect(vm.activeMainMenu).toBe('analytics')
    expect(vm.activeWorkspaceTabId).toBe('analytics')
    expect(confirm).toHaveBeenCalledExactlyOnceWith('close')
  })

  it('accepts an explicit close once without a second navigation prompt', async () => {
    const { vm, confirm } = await dirtyReportShell()
    confirm.mockReturnValue(true)
    vm.closeWorkspaceTab('analytics')
    expect(confirm).toHaveBeenCalledExactlyOnceWith('close')
    expect(vm.activeMainMenu).toBe('home')
    expect(vm.workspaceTabs.some((tab: any) => tab.id === 'analytics')).toBe(false)
  })

  it('preserves a cancelled logout, but forced session invalidation clears state without prompting', async () => {
    const { vm, confirm } = await dirtyReportShell()
    vm.logout()
    expect(session.token.value).toBe('admin-token')
    expect(vm.activeMainMenu).toBe('analytics')
    expect(confirm).toHaveBeenCalledExactlyOnceWith('logout')
    session.logout()
    expect(vm.activeMainMenu).toBe('home')
    expect(vm.workspaceLeaveGuards.hasUnsavedChanges()).toBe(false)
    expect(confirm).toHaveBeenCalledTimes(1)
  })

  it('guards browser refresh/close only while a real draft is dirty', async () => {
    const { clean, confirm } = await dirtyReportShell()
    const dirtyEvent = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(dirtyEvent)
    expect(dirtyEvent.defaultPrevented).toBe(true)
    clean()
    const cleanEvent = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(cleanEvent)
    expect(cleanEvent.defaultPrevented).toBe(false)
    expect(confirm).not.toHaveBeenCalled()
  })

  it('sends one server logout and clears the local session only after it is confirmed', async () => {
    session.persistSession({ token: 'admin-token', user, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    let resolve!: () => void
    mocks.logoutSession.mockReturnValueOnce(new Promise<void>(done => { resolve = done }))
    const vm = wrapper.vm as any
    const first = vm.logout(), duplicate = vm.logout()
    expect(mocks.logoutSession).toHaveBeenCalledTimes(1)
    expect(mocks.logoutSession.mock.calls[0][0]).toBe('admin-token')
    expect(session.token.value).toBe('admin-token')
    expect(vm.signingOut).toBe(true)
    resolve()
    await Promise.all([first, duplicate])
    expect(session.token.value).toBe('')
    expect(vm.signingOut).toBe(false)
  })

  it('retains an uncertain server session with visible retry, and succeeds on retry', async () => {
    session.persistSession({ token: 'admin-token', user, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    mocks.logoutSession.mockRejectedValueOnce(new Error('network unavailable'))
    const vm = wrapper.vm as any
    await vm.logout()
    await flushPromises()
    expect(session.token.value).toBe('admin-token')
    expect(vm.signingOut).toBe(false)
    expect(wrapper.get('.signout-error[role="alert"]').text()).toContain('服务器退出未确认')
    expect(wrapper.text()).toContain('重试退出')
    expect(wrapper.text()).toContain('仅退出本机')
    await vm.logout()
    expect(mocks.logoutSession).toHaveBeenCalledTimes(2)
    expect(session.token.value).toBe('')
  })

  it('cannot clear a replacement login when an old logout response arrives', async () => {
    session.persistSession({ token: 'admin-token', user, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    let resolve!: () => void
    mocks.logoutSession.mockReturnValueOnce(new Promise<void>(done => { resolve = done }))
    const vm = wrapper.vm as any
    const old = vm.logout()
    session.persistSession({ token: 'new-token', user, isSuperAdmin: false })
    resolve()
    await old
    expect(session.token.value).toBe('new-token')
    expect(vm.signOutError).toBe('')
    expect(vm.signingOut).toBe(false)
  })

  it('labels local-only logout honestly and requires its separate risk acknowledgement', async () => {
    session.persistSession({ token: 'admin-token', user, isSuperAdmin: false })
    wrapper = mountShell()
    await flushPromises()
    mocks.logoutSession.mockRejectedValueOnce(new Error('offline'))
    const vm = wrapper.vm as any
    await vm.logout()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    vm.logoutLocally()
    expect(session.token.value).toBe('admin-token')
    expect(confirm).toHaveBeenCalledWith('仅退出本机不会撤销服务器会话，仍然退出？')
    confirm.mockReturnValue(true)
    vm.logoutLocally()
    expect(session.token.value).toBe('')
    expect(vm.actionMessage).toBe('已退出本机，服务器会话可能仍有效')
    confirm.mockRestore()
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
