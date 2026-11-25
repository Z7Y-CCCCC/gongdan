import { compileStyle, parse } from '@vue/compiler-sfc'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, shallowMount, type VueWrapper } from '@vue/test-utils'
import DepartmentManagementPage from '../DepartmentManagementPage.vue'
import UserManagementPage from '../UserManagementPage.vue'
import BaseDataPage from '../BaseDataPage.vue'
import LiveBroadcastSection from '../LiveBroadcastSection.vue'
import PerformanceSection from '../PerformanceSection.vue'
import pluginRuntimeSource from '../PluginRuntimePage.vue?raw'
import { setUiLocale, t } from '../../i18n'

const api = vi.hoisted(() => ({ fetchDepartments: vi.fn(), fetchUsers: vi.fn(), fetchUsersExportBlob: vi.fn(), resetUserPassword: vi.fn(),
  fetchProcesses: vi.fn(), fetchUnits: vi.fn(), fetchDefects: vi.fn(), fetchProcessRoutes: vi.fn(),
  fetchHomeRecentReports: vi.fn(), fetchHomePerformance: vi.fn() }))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))
const wrappers: VueWrapper<any>[] = []
const user = { id: 11, username: 'fixture', name: '保存', departmentId: 1, departmentName: '保存', role: 'ADMIN' as const,
  roleLabel: '管理员', enabled: true, virtualUser: false, createdAt: '2026-11-24T10:00:00' }
async function page(component: any, props: Record<string, unknown> = {}, full = false) {
  const wrapper = (full ? mount : shallowMount)(component, { props: { token: 'fixture-one', ...props },
    attachTo: document.body, global: { stubs: { TableConfigButton: true, UiIcon: true } } })
  wrappers.push(wrapper); await flushPromises(); return wrapper
}
beforeEach(() => {
  vi.resetAllMocks()
  api.fetchDepartments.mockResolvedValue({ items: [{ id: 1, name: '保存', code: 'D1', createdAt: '2026-11-24', createdBy: 'fixture' }], total: 1 })
  api.fetchUsers.mockResolvedValue([user])
  api.resetUserPassword.mockResolvedValue({ message: '重置成功', temporaryPassword: 'fixture-reset-secret' })
  api.fetchProcesses.mockResolvedValue({ items: [{ id: 1, name: '工序', code: 'P1', reportPermission: ['dept-1'], reportRatio: 1, taskPunchIn: false, enabled: true, createdAt: '2026-11-24' }], total: 1 })
  api.fetchUnits.mockResolvedValue({ items: [], total: 0 }); api.fetchDefects.mockResolvedValue({ items: [], total: 0 })
  api.fetchProcessRoutes.mockResolvedValue({ items: [], total: 0 })
  api.fetchHomeRecentReports.mockResolvedValue({ items: [] })
  api.fetchHomePerformance.mockResolvedValue({ plannedQuantity: 10, completedQuantity: 5, workOrderCount: 2,
    defectRatePercent: 0, averageCycleDays: 1, overdueUnfinishedCount: 0 })
})
afterEach(() => { wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount()); vi.useRealTimers() })

describe('page details missed by route-opening checks', () => {
  it('keeps department-member checkboxes inside the dialog focus scope instead of a body portal', async () => {
    const wrapper = await page(DepartmentManagementPage, { departments: [], departmentTotal: 0 }, true)
    await wrapper.get('tbody button[aria-label="编辑"]').trigger('click'); await flushPromises()
    await wrapper.get('button[aria-label="部门人员"]').trigger('click'); await flushPromises()
    const choice = wrapper.get('.modal input[type="checkbox"]')
    expect(wrapper.get('.modal').element.contains(choice.element)).toBe(true)
    expect(wrapper.get('button[aria-label="部门人员"]').attributes('aria-controls')).toBeTruthy()
    await choice.setValue(false)
    expect((wrapper.vm as any).departmentForm.userIds).toEqual([])
  })
  it('never copies the submitted reset password into a global success notice', async () => {
    const wrapper = await page(UserManagementPage, { users: [user], departments: [], currentUser: user })
    const vm = wrapper.vm as any
    vm.openResetPassword(user); vm.resetPasswordForm.password = 'fixture-reset-secret'; vm.resetPasswordForm.confirmPassword = 'fixture-reset-secret'
    await vm.handleResetPassword(); await flushPromises()
    expect(JSON.stringify(wrapper.emitted('notice'))).not.toContain('fixture-reset-secret')
    expect(vm.resetPasswordForm.password).toBe('')
    expect(api.resetUserPassword).toHaveBeenCalledExactlyOnceWith('fixture-one', 11, 'fixture-reset-secret')
  })
  it('keeps actual department/employee names that collide with UI vocabulary unchanged in permission tags', async () => {
    setUiLocale('en-US')
    const wrapper = await page(BaseDataPage, { activeBasedataMenu: 'process', users: [user] })
    expect(wrapper.get('.perm-cell-tag').text()).toBe('保存')
  })
  it('localizes the Everyone permission fallback without translating actual selected employee names', async () => {
    setUiLocale('en-US')
    const wrapper = await page(BaseDataPage, { activeBasedataMenu: 'process', users: [user] })
    const vm = wrapper.vm as any
    vm.openCreateProcess(); await flushPromises()
    expect(wrapper.get('.perm-tag').text()).toContain(t('所有人'))
    vm.processForm.reportPermission = ['emp-11']; await flushPromises()
    expect(wrapper.get('.perm-tag').text()).toContain('保存')
  })
  it('compiles plugin runtime dark-theme rules against the actual root instead of an impossible scoped root', () => {
    const style = parse(pluginRuntimeSource).descriptor.styles[0]
    const result = compileStyle({ source: style.content, filename: 'PluginRuntimePage.vue', id: 'data-v-audit', scoped: true })
    expect(result.errors).toEqual([])
    expect(result.code).not.toMatch(/:root\.dark\[data-v-/)
    expect(result.code).toMatch(/(?:\.dark|:root\.dark)\s+\.plugin-runtime/)
  })
  it('makes initial production-feed failure visible and retryable instead of a false no-broadcast state', async () => {
    api.fetchHomeRecentReports.mockRejectedValueOnce(new Error('生产动态读取失败'))
    const wrapper = await page(LiveBroadcastSection, { active: true })
    expect(wrapper.get('[role="alert"]').text()).toContain('生产动态读取失败')
    expect(wrapper.find('.live-broadcast__empty').exists()).toBe(false)
    await wrapper.get('[role="alert"] button').trigger('click'); await flushPromises()
    expect(wrapper.find('.live-broadcast__empty').exists()).toBe(true)
  })
  it('exposes a meaningful KPI load error and a retry while displaying unknown values, not zeros', async () => {
    api.fetchHomePerformance.mockRejectedValueOnce(new Error('绩效读取失败'))
    const wrapper = await page(PerformanceSection)
    expect(wrapper.get('[role="alert"]').text()).toContain('绩效读取失败')
    expect(wrapper.findAll('.kpi-value').map(node => node.text())).toEqual(Array(6).fill('--'))
    await wrapper.get('[role="alert"] button').trigger('click'); await flushPromises()
    expect(wrapper.findAll('.kpi-value')[0].text()).toBe('10')
  })
})
