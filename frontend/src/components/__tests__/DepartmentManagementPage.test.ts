import { defineComponent, h, KeepAlive } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, shallowMount, type VueWrapper } from '@vue/test-utils'
import DepartmentManagementPage from '../DepartmentManagementPage.vue'
import { setUiLocale } from '../../i18n'

const api = vi.hoisted(() => ({ fetchDepartments: vi.fn(), fetchUsers: vi.fn(), createDepartment: vi.fn(),
  updateDepartment: vi.fn(), deleteDepartment: vi.fn() }))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))
const department = (id = 1, name = '制造一部') => ({ id, name, code: `DEPT-${id}`, createdAt: '2026-11-24T10:00:00', createdBy: '管理员' })
const user = (id = 11, departmentId = 1) => ({ id, departmentId, name: `人员${id}`, username: `user${id}`,
  role: 'PRODUCTION', roleLabel: '生产人员', enabled: true, virtualUser: false, createdAt: '' })
function deferred<T = any>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const wrappers: VueWrapper<any>[] = []
async function page() {
  const wrapper = shallowMount(DepartmentManagementPage, { props: { token: 'fixture-one', departments: [], departmentTotal: 0 } })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}
async function edit(wrapper: VueWrapper<any>) {
  await wrapper.get('tbody button[aria-label="编辑"]').trigger('click')
  await flushPromises()
}
beforeEach(() => {
  vi.resetAllMocks()
  api.fetchDepartments.mockResolvedValue({ items: [department()], total: 21 })
  api.fetchUsers.mockResolvedValue([user(), user(12, 2)])
  api.createDepartment.mockResolvedValue(department(2, '新部门'))
  api.updateDepartment.mockResolvedValue(department())
  api.deleteDepartment.mockResolvedValue(undefined)
})
afterEach(() => { wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount()) })

describe('department real list and safe membership workflows', () => {
  it('translates an empty member prompt while preserving actual member names across locale changes', async () => {
    setUiLocale('en-US')
    try {
      const wrapper = await page()
      await wrapper.get('button[aria-label="Create department"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('button[aria-label="Department members"]').text()).toBe('Select department members')
      setUiLocale('zh-CN')
      await flushPromises()
      expect(wrapper.get('button[aria-label="部门人员"]').text()).toBe('请选择部门人员')
      await wrapper.get('.modal button[aria-label="取消"]').trigger('click')
      api.fetchUsers.mockResolvedValueOnce([{ ...user(), name: '保存' }])
      await edit(wrapper)
      setUiLocale('en-US')
      await flushPromises()
      expect(wrapper.get('button[aria-label="Department members"]').text()).toBe('保存')
    } finally { setUiLocale('zh-CN') }
  })

  it('renders its actual API result, paginates with the applied filter, and resets submitted searches to page one', async () => {
    const wrapper = await page()
    expect(wrapper.get('tbody').text()).toContain('制造一部')
    expect(wrapper.emitted('reload-departments')).toBeUndefined()
    await wrapper.get('.filters input').setValue('尚未查询的草稿')
    api.fetchDepartments.mockResolvedValueOnce({ items: [department(2, '第二页')], total: 21 })
    await wrapper.get('button[aria-label="下一页"]').trigger('click')
    await flushPromises()
    expect(api.fetchDepartments).toHaveBeenLastCalledWith('fixture-one', { code: '', name: '', page: 2, size: 10 })
    expect(wrapper.get('.page-num').text()).toBe('2')
    await wrapper.get('button[aria-label="查询"]').trigger('click')
    await flushPromises()
    expect(api.fetchDepartments).toHaveBeenLastCalledWith('fixture-one', { code: '', name: '尚未查询的草稿', page: 1, size: 10 })
    expect(wrapper.get('.page-num').text()).toBe('1')
  })

  it('keeps old rows and page after failure, but retries the exact failed page-one query even if the input changes', async () => {
    const wrapper = await page()
    await wrapper.get('button[aria-label="下一页"]').trigger('click'); await flushPromises()
    await wrapper.get('.filters input').setValue('失败条件')
    api.fetchDepartments.mockRejectedValueOnce(new Error('部门查询暂时失败'))
    await wrapper.get('button[aria-label="查询"]').trigger('click'); await flushPromises()
    expect(wrapper.get('.page-num').text()).toBe('2')
    expect(wrapper.get('tbody').text()).toContain('制造一部')
    expect(wrapper.get('[role="alert"]').text()).toContain('部门查询暂时失败')
    await wrapper.get('.filters input').setValue('未提交的修改')
    await wrapper.get('[role="alert"] button').trigger('click'); await flushPromises()
    expect(api.fetchDepartments).toHaveBeenLastCalledWith('fixture-one', { code: '', name: '失败条件', page: 1, size: 10 })
    expect(wrapper.get('.page-num').text()).toBe('1')
  })

  it('ignores an older query that resolves after the newest query', async () => {
    const wrapper = await page()
    const old = deferred()
    api.fetchDepartments.mockReturnValueOnce(old.promise)
    await wrapper.get('.filters input').setValue('旧条件')
    await wrapper.get('button[aria-label="查询"]').trigger('click')
    api.fetchDepartments.mockResolvedValueOnce({ items: [department(3, '最新结果')], total: 1 })
    await wrapper.get('.filters input').setValue('新条件')
    await wrapper.get('button[aria-label="查询"]').trigger('click'); await flushPromises()
    old.resolve({ items: [department(4, '旧结果')], total: 500 }); await flushPromises()
    expect(wrapper.get('tbody').text()).toContain('最新结果')
    expect(wrapper.get('tbody').text()).not.toContain('旧结果')
    expect(wrapper.get('.page-total').text()).toContain('1')
  })

  it('does not clear department members when loading users fails and permits a safe retry', async () => {
    const wrapper = await page()
    api.fetchUsers.mockRejectedValueOnce(new Error('无法加载人员'))
    await edit(wrapper)
    expect(wrapper.get('.modal [role="alert"]').text()).toContain('无法加载人员')
    await wrapper.get('form').trigger('submit')
    expect(api.updateDepartment).not.toHaveBeenCalled()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await wrapper.get('.modal [role="alert"] button').trigger('click'); await flushPromises()
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(api.updateDepartment).toHaveBeenCalledExactlyOnceWith('fixture-one', 1, { name: '制造一部', code: 'DEPT-1', userIds: [11] })
  })

  it('does not reopen a canceled edit or overwrite the next modal when its users arrive late', async () => {
    const wrapper = await page()
    const old = deferred()
    api.fetchUsers.mockReturnValueOnce(old.promise)
    await wrapper.get('tbody button[aria-label="编辑"]').trigger('click')
    await wrapper.get('.modal button[aria-label="取消"]').trigger('click')
    await wrapper.get('button[aria-label="新建部门"]').trigger('click'); await flushPromises()
    old.resolve([user(99)]); await flushPromises()
    expect(wrapper.get('.modal h2').text()).toBe('新建部门')
    expect((wrapper.vm as any).departmentForm.userIds).toEqual([])
    expect((wrapper.vm as any).allUsersList.map((entry: any) => entry.id)).toEqual([11, 12])
  })

  it('locks fields and duplicate saves, preserves the draft on failure, and sends a string for an auto-generated code', async () => {
    const wrapper = await page()
    await wrapper.get('button[aria-label="新建部门"]').trigger('click'); await flushPromises()
    await wrapper.get('.modal input[placeholder="请输入部门名称"]').setValue(' 新部门 ')
    const pending = deferred()
    api.createDepartment.mockReturnValueOnce(pending.promise)
    await Promise.all([wrapper.get('form').trigger('submit'), wrapper.get('form').trigger('submit')])
    expect(api.createDepartment).toHaveBeenCalledExactlyOnceWith('fixture-one', { name: '新部门', code: '', userIds: [] })
    expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.modal button[aria-label="取消"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button[type="submit"]').text()).toBe('保存中...')
    pending.reject(new Error('保存暂时失败')); await flushPromises()
    expect((wrapper.get('.modal input[placeholder="请输入部门名称"]').element as HTMLInputElement).value).toBe(' 新部门 ')
    expect(wrapper.get('fieldset').attributes('disabled')).toBeUndefined()
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(wrapper.find('.modal').exists()).toBe(false)
    expect(wrapper.emitted('notice')).toContainEqual(['部门已创建'])
  })

  it('removes a successfully deleted row immediately even if reloading fails, without reviving an older pending list', async () => {
    const wrapper = await page()
    const old = deferred()
    api.fetchDepartments.mockReturnValueOnce(old.promise)
    ;(wrapper.vm as any).loadDepartments()
    await wrapper.get('tbody button[aria-label="删除"]').trigger('click')
    api.fetchDepartments.mockRejectedValueOnce(new Error('删除后刷新失败'))
    const confirmation = wrapper.emitted('confirm')!.slice(-1)[0]![0] as { action: () => Promise<void>; title: string }
    expect(confirmation.title).toBe('删除部门')
    await Promise.all([confirmation.action(), confirmation.action()]); await flushPromises()
    expect(api.deleteDepartment).toHaveBeenCalledExactlyOnceWith('fixture-one', 1)
    old.resolve({ items: [department()], total: 21 }); await flushPromises()
    expect(wrapper.get('tbody').text()).not.toContain('制造一部')
    expect(wrapper.get('[role="alert"]').text()).toContain('删除后刷新失败')
    expect(wrapper.get('.page-total').text()).toContain('20')
    expect(wrapper.emitted('notice')).toContainEqual(['部门已删除'])
  })

  it('moves back to the last valid page after deleting the final row', async () => {
    const wrapper = await page()
    api.fetchDepartments.mockResolvedValueOnce({ items: [department(21, '末页')], total: 21 })
    await wrapper.get('button[aria-label="下一页"]').trigger('click'); await flushPromises()
    await wrapper.get('button[aria-label="下一页"]').trigger('click'); await flushPromises()
    await wrapper.get('tbody button[aria-label="删除"]').trigger('click')
    api.fetchDepartments.mockResolvedValueOnce({ items: [], total: 20 }).mockResolvedValueOnce({ items: [department(20)], total: 20 })
    await (wrapper.emitted('confirm')!.slice(-1)[0]![0] as any).action(); await flushPromises()
    expect(wrapper.get('.page-num').text()).toBe('2')
    expect(api.fetchDepartments).toHaveBeenLastCalledWith('fixture-one', { code: '', name: '', page: 2, size: 10 })
  })

  it('isolates old list, member, save and deferred delete responses from a new session', async () => {
    const wrapper = await page()
    await wrapper.get('tbody button[aria-label="删除"]').trigger('click')
    const confirmation = (wrapper.emitted('confirm')!.slice(-1)[0]![0] as any).action
    await edit(wrapper)
    const pending = deferred()
    api.updateDepartment.mockReturnValueOnce(pending.promise)
    await wrapper.get('form').trigger('submit')
    api.fetchDepartments.mockResolvedValueOnce({ items: [department(2, '新会话部门')], total: 1 })
    await wrapper.setProps({ token: 'fixture-two' }); await flushPromises()
    pending.resolve(department()); await flushPromises()
    await confirmation()
    expect(api.deleteDepartment).not.toHaveBeenCalled()
    expect(wrapper.get('tbody').text()).toContain('新会话部门')
    expect(wrapper.find('.modal').exists()).toBe(false)
    expect(wrapper.emitted('reload-departments')).toBeUndefined()
    expect(wrapper.emitted('notice')).toBeUndefined()
  })

  it('refreshes a cached department page when it is reactivated', async () => {
    const Other = defineComponent({ setup: () => () => h('p', 'Other') })
    const Host = defineComponent({ props: { show: Boolean }, setup: props => () => h(KeepAlive, null, {
      default: () => props.show ? h(DepartmentManagementPage, { token: 'fixture-one', departments: [], departmentTotal: 0 }) : h(Other)
    }) })
    const wrapper = mount(Host, { props: { show: true }, global: { stubs: { UiIcon: true, TableConfigButton: true } } })
    wrappers.push(wrapper); await flushPromises()
    expect(api.fetchDepartments).toHaveBeenCalledTimes(1)
    await wrapper.setProps({ show: false })
    api.fetchDepartments.mockResolvedValueOnce({ items: [department(2, '其他页面添加的部门')], total: 1 })
    await wrapper.setProps({ show: true }); await flushPromises()
    expect(api.fetchDepartments).toHaveBeenCalledTimes(2)
    expect(wrapper.get('tbody').text()).toContain('其他页面添加的部门')
  })

  it('provides a keyboard button for department members and localized saving feedback', async () => {
    const wrapper = await page(); await edit(wrapper)
    expect(wrapper.get('button[aria-label="部门人员"]').attributes('aria-expanded')).toBe('false')
    const pending = deferred()
    api.updateDepartment.mockReturnValueOnce(pending.promise)
    setUiLocale('en-US')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('button[type="submit"]').text()).toBe('Saving...')
    pending.resolve(department()); await flushPromises()
  })
})
