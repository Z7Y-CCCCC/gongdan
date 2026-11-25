import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import DepartmentManagementPage from '../DepartmentManagementPage.vue'

const api = vi.hoisted(() => ({ fetchDepartments: vi.fn(), fetchUsers: vi.fn() }))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))
let wrapper: VueWrapper<any> | undefined
beforeEach(() => {
  vi.resetAllMocks()
  api.fetchDepartments.mockResolvedValue({ items: [{ id: 1, code: 'D1', name: '部门', createdAt: '2026-11-25' }], total: 1 })
  api.fetchUsers.mockResolvedValue([{ id: 7, username: 'fixture', name: '成员', departmentId: 1, role: 'ADMIN', enabled: true }])
})
afterEach(() => { wrapper?.unmount(); wrapper = undefined; document.body.innerHTML = '' })
async function open() {
  wrapper = mount(DepartmentManagementPage, { props: { token: 'fixture-one', departments: [], departmentTotal: 0 },
    attachTo: document.body, global: { stubs: { UiIcon: true, TableConfigButton: true } } })
  await flushPromises()
  const trigger = wrapper.get('tbody button[aria-label="编辑"]')
  ;(trigger.element as HTMLElement).focus(); await trigger.trigger('click'); await flushPromises()
  return { trigger, modal: wrapper.get('.modal') }
}
describe('department dialog keyboard ownership beyond checkbox containment', () => {
  it('announces the actual dialog and moves focus into it instead of leaving it on a hidden row', async () => {
    const { modal } = await open()
    expect(modal.attributes('role')).toBe('dialog')
    expect(modal.attributes('aria-modal')).toBe('true')
    const label = document.getElementById(modal.attributes('aria-labelledby')!)
    expect(label?.textContent).toBe('编辑部门')
    expect(modal.element.contains(document.activeElement)).toBe(true)
  })
  it('loops Tab and Shift+Tab within the full dialog and restores the opener on Escape', async () => {
    const { trigger, modal } = await open()
    const close = modal.get('button[aria-label="关闭"]'), save = modal.get('button[type="submit"]')
    ;(save.element as HTMLElement).focus(); await save.trigger('keydown', { key: 'Tab' })
    expect(document.activeElement).toBe(close.element)
    await close.trigger('keydown', { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(save.element)
    await save.trigger('keydown', { key: 'Escape' }); await flushPromises()
    expect(wrapper!.find('.modal').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger.element)
  })
  it('closes the member disclosure first, then lets the next Escape close the parent from its trigger', async () => {
    const { modal } = await open()
    const trigger = modal.get('button[aria-label="部门人员"]')
    await trigger.trigger('click'); await flushPromises()
    const member = modal.get('input[type="checkbox"]')
    ;(member.element as HTMLElement).focus(); await member.trigger('keydown', { key: 'Escape' }); await flushPromises()
    expect(wrapper!.find('.modal').exists()).toBe(true)
    expect(wrapper!.find('.department-member-list').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger.element)
    await trigger.trigger('keydown', { key: 'Escape' }); await flushPromises()
    expect(wrapper!.find('.modal').exists()).toBe(false)
  })
})
