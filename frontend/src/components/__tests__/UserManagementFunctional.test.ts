import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import UserManagementPage from '../UserManagementPage.vue'
import type { UserInfo } from '../../api'
import { setUiLocale } from '../../i18n'

const api = vi.hoisted(() => ({ createUser: vi.fn(), updateUser: vi.fn(), updateUserPermission: vi.fn(), resetUserPassword: vi.fn(),
  unbindUserPhone: vi.fn(), updateUserStatus: vi.fn(), importUsersCsv: vi.fn(), exportUsersCsv: vi.fn(), fetchUsersExportBlob: vi.fn(),
  createSystemImportLog: vi.fn(), fetchSystemImportLogs: vi.fn() }))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))
const user = (id = 1, name = '用户一'): UserInfo => ({ id, name, username: `user${id}`, phone: '13800000000',
  role: 'ADMIN', roleLabel: '管理员', enabled: true, virtualUser: false, createdAt: '2026-11-24T10:00:00' })
const log = (fileName = '历史用户.csv') => ({ id: 1, fileName, totalCount: 5, successCount: 4, failCount: 1,
  errors: '第5行：账号已存在', createdAt: '2026-11-24T10:00:00' })
function deferred<T = any>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const wrappers: VueWrapper<any>[] = []
async function page(users = [user()]) {
  const wrapper = shallowMount(UserManagementPage, { props: { token: 'fixture-one', users, departments: [], currentUser: users[0] } })
  wrappers.push(wrapper); await flushPromises()
  return wrapper
}
function importInput() {
  const target = document.createElement('input')
  const file = new File(['username,name\nnew,新用户'], '用户导入.csv', { type: 'text/csv' })
  Object.defineProperty(target, 'files', { value: [file] })
  return { target, file, event: { target } as unknown as Event }
}
beforeEach(() => {
  vi.resetAllMocks()
  localStorage.removeItem('heihu_user')
  for (const method of ['createUser', 'updateUser', 'updateUserPermission', 'unbindUserPhone', 'updateUserStatus'] as const) {
    api[method].mockResolvedValue(user(1, '已更新用户'))
  }
  api.resetUserPassword.mockResolvedValue({ temporaryPassword: 'new-password', message: '重置成功' })
  api.fetchSystemImportLogs.mockResolvedValue({ items: [log()], total: 1 })
  api.createSystemImportLog.mockResolvedValue({})
  api.importUsersCsv.mockResolvedValue({ total: 1, success: 1, fail: 0, errors: [] })
  api.exportUsersCsv.mockResolvedValue(undefined)
  api.fetchUsersExportBlob.mockResolvedValue({ text: async () => '账号,姓名\nuser1,用户一' } as Blob)
})
afterEach(() => { wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount()); vi.restoreAllMocks() })

const operations = [
  { label: '创建用户', api: api.createUser, prepare: (vm: any) => {
    vm.openCreateUser(); Object.assign(vm.createForm, { name: ' 新用户 ', username: ' new ', password: 'exact-password' })
    return () => vm.handleCreateUser()
  } },
  { label: '编辑用户', api: api.updateUser, prepare: (vm: any) => {
    vm.openEditUser(user()); vm.editForm.name = ' 新姓名 '; return () => vm.handleUpdateUser()
  } },
  { label: '修改权限', api: api.updateUserPermission, prepare: (vm: any) => {
    vm.openPermissionConfig(user()); vm.permissionRole = 'PRODUCTION'; return () => vm.handleUpdatePermission()
  } },
  { label: '重置密码', api: api.resetUserPassword, prepare: (vm: any) => {
    vm.openResetPassword(user()); Object.assign(vm.resetPasswordForm, { password: 'new-password', confirmPassword: 'new-password' })
    return () => vm.handleResetPassword()
  } },
  { label: '解绑手机号', api: api.unbindUserPhone, prepare: (vm: any) => () => vm.handleUnbindPhone(user()) },
  { label: '停用用户', api: api.updateUserStatus, prepare: (vm: any) => () => vm.handleToggleEnabled(user(), false, '停用') },
]

describe.each(operations)('$label single-write and account isolation', operation => {
  it('sends one pending write, retains drafts after failure, then permits retry', async () => {
    const wrapper = await page(); const vm = wrapper.vm as any
    const execute = operation.prepare(vm)
    const pending = deferred()
    operation.api.mockReturnValueOnce(pending.promise)
    const first = execute(); const second = execute()
    expect(operation.api).toHaveBeenCalledTimes(1)
    expect(operation.api.mock.calls[0][0]).toBe('fixture-one')
    expect(vm.loading).toBe(true)
    pending.reject(new Error('写入暂时失败'))
    await Promise.all([first, second]); await flushPromises()
    expect(vm.loading).toBe(false)
    expect(wrapper.emitted('error')).toContainEqual(['写入暂时失败'])
    if (operation.label === '创建用户') expect(vm.createForm.password).toBe('exact-password')
    if (operation.label === '编辑用户') expect(vm.editForm.name).toBe(' 新姓名 ')
    if (operation.label === '修改权限') expect(vm.permissionRole).toBe('PRODUCTION')
    if (operation.label === '重置密码') expect(vm.resetPasswordForm.password).toBe('new-password')
    await execute(); await flushPromises()
    expect(operation.api).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('notice') || wrapper.emitted('reload-users')).toBeDefined()
  })

  it('does not apply an old response to a new session or persist the previous user', async () => {
    const wrapper = await page(); const vm = wrapper.vm as any
    const execute = operation.prepare(vm)
    const pending = deferred()
    operation.api.mockReturnValueOnce(pending.promise)
    const first = execute()
    const nextUser = user(1, '当前会话用户')
    localStorage.setItem('heihu_user', JSON.stringify(nextUser))
    await wrapper.setProps({ token: 'fixture-two', users: [nextUser], currentUser: nextUser })
    pending.resolve(user(1, '旧会话响应'))
    await first; await flushPromises()
    expect(JSON.parse(localStorage.getItem('heihu_user')!).name).toBe('当前会话用户')
    expect(wrapper.emitted('update-current-user')).toBeUndefined()
    expect(wrapper.emitted('reload-users')).toBeUndefined()
    expect(wrapper.emitted('notice')).toBeUndefined()
    expect(vm.loading).toBe(false)
    expect(vm.successUser).toBeNull()
    expect(vm.showCreateModal || vm.showEditModal || vm.showPermissionModal || vm.showResetPasswordModal).toBe(false)
  })
})

describe('user dialogs, confirmations and import history', () => {
  it('locks mutable controls and uses the exact password sent when displaying newly created account details', async () => {
    const wrapper = await page(); const vm = wrapper.vm as any
    await wrapper.get('button[aria-label="新增用户"]').trigger('click')
    await wrapper.get('input[placeholder="请输入姓名"]').setValue('新用户')
    await wrapper.get('input[placeholder="请输入账号"]').setValue('new')
    await wrapper.get('input[placeholder="请输入密码"]').setValue('sent-password')
    const pending = deferred(); api.createUser.mockReturnValueOnce(pending.promise)
    await Promise.all([wrapper.get('form').trigger('submit'), wrapper.get('form').trigger('submit')])
    expect(api.createUser).toHaveBeenCalledTimes(1)
    expect(wrapper.get('input[placeholder="请输入密码"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.modal button[aria-label="关闭"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button[type="submit"]').text()).toBe('保存中...')
    vm.createForm.password = 'programmatic-change'
    vm.openEditUser(user(2))
    expect(vm.editingUser).toBeNull()
    pending.resolve(user(2, '新用户')); await flushPromises()
    expect(vm.successUserPassword).toBe('sent-password')
    expect(vm.createForm.password).toBe('')
  })

  it('syncs a successful edit only to the matching current user and retains the API payload snapshot', async () => {
    const wrapper = await page(); const vm = wrapper.vm as any
    vm.openEditUser(user()); vm.editForm.name = ' 新姓名 '
    await vm.handleUpdateUser(); await flushPromises()
    expect(api.updateUser).toHaveBeenCalledExactlyOnceWith('fixture-one', 1, expect.objectContaining({ name: '新姓名', username: 'user1' }))
    expect(wrapper.emitted('update-current-user')).toContainEqual([user(1, '已更新用户')])
    expect(JSON.parse(localStorage.getItem('heihu_user')!).name).toBe('已更新用户')
  })

  it.each(['解绑', '停用'])('does not run a deferred %s confirmation with a different session token', async action => {
    const wrapper = await page(); const vm = wrapper.vm as any
    if (action === '解绑') vm.confirmUnbindPhone(user())
    else vm.confirmToggleEnabled(user())
    const confirmation = wrapper.emitted('confirm')!.slice(-1)[0]![0] as { action: () => Promise<void> }
    await wrapper.setProps({ token: 'fixture-two', currentUser: user(2), users: [user(2)] })
    await confirmation.action(); await flushPromises()
    expect(api.unbindUserPhone).not.toHaveBeenCalled()
    expect(api.updateUserStatus).not.toHaveBeenCalled()
  })

  it('does not let a prior token failure release the loading lock of a newer operation', async () => {
    const wrapper = await page(); const vm = wrapper.vm as any
    vm.openEditUser(user()); const old = deferred(); api.updateUser.mockReturnValueOnce(old.promise)
    const first = vm.handleUpdateUser()
    await wrapper.setProps({ token: 'fixture-two' })
    vm.openEditUser(user(2)); const current = deferred(); api.updateUser.mockReturnValueOnce(current.promise)
    const second = vm.handleUpdateUser()
    old.reject(new Error('旧会话错误')); await first; await flushPromises()
    expect(vm.loading).toBe(true)
    expect(wrapper.emitted('error')).toBeUndefined()
    current.resolve(user(2)); await second; await flushPromises()
    expect(vm.loading).toBe(false)
  })

  it('imports once, records an actual result with the captured token and opens persisted history', async () => {
    const wrapper = await page(); const vm = wrapper.vm as any
    const input = importInput(); const pending = deferred(); api.importUsersCsv.mockReturnValueOnce(pending.promise)
    const first = vm.handleImport(input.event); const second = vm.handleImport(input.event)
    expect(api.importUsersCsv).toHaveBeenCalledExactlyOnceWith('fixture-one', input.file)
    expect(vm.importing).toBe(true)
    await flushPromises()
    expect(wrapper.get('input[type="file"]').attributes('disabled')).toBeDefined()
    pending.resolve({ total: 5, success: 4, fail: 1, errors: ['第5行：账号已存在'] })
    await Promise.all([first, second]); await flushPromises()
    expect(api.createSystemImportLog).toHaveBeenCalledExactlyOnceWith('fixture-one', expect.objectContaining({
      module: 'USER', fileName: input.file.name, totalCount: 5, successCount: 4, failCount: 1, status: '部分成功'
    }))
    expect(wrapper.emitted('reload-users')).toHaveLength(1)
    expect(wrapper.emitted('notice')).toContainEqual(['导入完成：共 5 条，成功 4 条，失败 1 条'])
    await wrapper.get('button[aria-label="导入日志"]').trigger('click'); await flushPromises()
    expect(wrapper.get('.import-history-table').text()).toContain('历史用户.csv')
    expect(wrapper.get('.import-history-table').text()).toContain('第5行：账号已存在')
    expect(wrapper.get('.import-result').text()).toContain('总计：5 条')
  })

  it('does not record or display an old import result in the new session', async () => {
    const wrapper = await page(); const vm = wrapper.vm as any
    const pending = deferred(); api.importUsersCsv.mockReturnValueOnce(pending.promise)
    const first = vm.handleImport(importInput().event)
    await wrapper.setProps({ token: 'fixture-two' })
    pending.resolve({ total: 1, success: 1, fail: 0, errors: [] }); await first; await flushPromises()
    expect(api.createSystemImportLog).not.toHaveBeenCalled()
    expect(api.fetchSystemImportLogs).not.toHaveBeenCalled()
    expect(vm.importResult).toBeNull()
    expect(wrapper.emitted('notice')).toBeUndefined()
    expect(vm.importing).toBe(false)
  })

  it('does not issue an old-history request after a session change during import-log saving', async () => {
    const wrapper = await page(); const vm = wrapper.vm as any
    const pending = deferred(); api.createSystemImportLog.mockReturnValueOnce(pending.promise)
    const first = vm.handleImport(importInput().event); await flushPromises()
    expect(api.createSystemImportLog).toHaveBeenCalledTimes(1)
    await wrapper.setProps({ token: 'fixture-two' })
    pending.resolve({}); await first; await flushPromises()
    expect(api.fetchSystemImportLogs).not.toHaveBeenCalled()
    expect(vm.importResult).toBeNull()
  })

  it('reports completed import separately from logging failure, without retrying the data write', async () => {
    const wrapper = await page(); const vm = wrapper.vm as any
    api.createSystemImportLog.mockRejectedValueOnce(new Error('日志服务不可用'))
    await vm.handleImport(importInput().event); await flushPromises()
    expect(api.importUsersCsv).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('notice')).toContainEqual(['导入完成：共 1 条，全部成功'])
    expect(wrapper.emitted('error')).toContainEqual(['导入已完成，但日志保存失败'])
    expect(vm.importResult.success).toBe(1)
  })

  it('loads history from the visible entry, keeps prior records on failure, and retries without a false empty state', async () => {
    const wrapper = await page()
    const pending = deferred(); api.fetchSystemImportLogs.mockReturnValueOnce(pending.promise)
    await wrapper.get('button[aria-label="导入日志"]').trigger('click')
    expect(wrapper.get('.modal [role="status"]').text()).toBe('加载中...')
    expect(wrapper.get('.modal').text()).not.toContain('暂无导入日志')
    pending.resolve({ items: [log()], total: 1 }); await flushPromises()
    api.fetchSystemImportLogs.mockRejectedValueOnce(new Error('历史读取失败'))
    await (wrapper.vm as any).refreshImportLogs(); await flushPromises()
    expect(wrapper.get('.import-history-table').text()).toContain('历史用户.csv')
    expect(wrapper.get('.modal [role="alert"]').text()).toContain('历史读取失败')
    api.fetchSystemImportLogs.mockResolvedValueOnce({ items: [log('重试记录.csv')], total: 1 })
    await wrapper.get('.modal [role="alert"] button').trigger('click'); await flushPromises()
    expect(wrapper.get('.import-history-table').text()).toContain('重试记录.csv')
  })

  it('ignores stale history and clears credential drafts when the token changes', async () => {
    const wrapper = await page(); const vm = wrapper.vm as any
    const pending = deferred(); api.fetchSystemImportLogs.mockReturnValueOnce(pending.promise)
    vm.openImportLog(); vm.createForm.password = 'secret-draft'; vm.resetPasswordForm.password = 'secret-reset'
    await wrapper.setProps({ token: 'fixture-two' })
    pending.resolve({ items: [log('旧会话.csv')], total: 1 }); await flushPromises()
    expect(vm.importLogs).toEqual([])
    expect(vm.createForm.password).toBe('')
    expect(vm.resetPasswordForm.password).toBe('')
    expect(vm.showImportLogModal).toBe(false)
  })

  it('paginates persisted history and retries the same failed page instead of truncating to an unmarked first 200 records', async () => {
    const wrapper = await page()
    api.fetchSystemImportLogs.mockResolvedValueOnce({ items: [log()], total: 201 })
    await wrapper.get('button[aria-label="导入日志"]').trigger('click'); await flushPromises()
    expect(api.fetchSystemImportLogs).toHaveBeenLastCalledWith('fixture-one', 'USER', 1, 20)
    expect(wrapper.get('.import-history-pagination').text()).toContain('201')
    expect(wrapper.get('.import-history-pagination .page-num').text()).toBe('1 / 11')
    api.fetchSystemImportLogs.mockRejectedValueOnce(new Error('第二页历史读取失败'))
    await wrapper.get('.import-history-pagination button[aria-label="下一页"]').trigger('click'); await flushPromises()
    expect(wrapper.get('.import-history-pagination .page-num').text()).toBe('1 / 11')
    expect(wrapper.get('.import-history-table').text()).toContain('历史用户.csv')
    api.fetchSystemImportLogs.mockResolvedValueOnce({ items: [log('第二页.csv')], total: 201 })
    await wrapper.get('.modal [role="alert"] button').trigger('click'); await flushPromises()
    expect(api.fetchSystemImportLogs).toHaveBeenLastCalledWith('fixture-one', 'USER', 2, 20)
    expect(wrapper.get('.import-history-pagination .page-num').text()).toBe('2 / 11')
    expect(wrapper.get('.import-history-table').text()).toContain('第二页.csv')
  })

  it('prunes hidden selections and resets pagination when page size changes', async () => {
    const wrapper = await page(Array.from({ length: 25 }, (_, i) => user(i + 1, `用户${i + 1}`)))
    const vm = wrapper.vm as any
    vm.selectedUserIds = Array.from({ length: 25 }, (_, i) => 100 + i)
    expect(vm.isAllSelected).toBe(false)
    vm.isAllSelected = true
    vm.filterForm.name = '用户25'; await flushPromises()
    expect(vm.selectedUserIds).toEqual([25])
    vm.filterForm.name = ''; await flushPromises()
    vm.currentPage = 3; await flushPromises()
    expect(wrapper.get('tbody').text()).toContain('用户25')
    await wrapper.get('.size-select').setValue('50'); await flushPromises()
    expect(wrapper.get('.page-num').text()).toBe('1')
    expect(wrapper.get('tbody').text()).toContain('用户1')
  })

  it('prevents duplicate exports and shows localized saving feedback', async () => {
    const wrapper = await page(); const vm = wrapper.vm as any
    const pending = deferred(); api.exportUsersCsv.mockReturnValueOnce(pending.promise)
    const first = vm.handleExport(); const second = vm.handleExport()
    expect(api.exportUsersCsv).toHaveBeenCalledTimes(1)
    pending.resolve(undefined); await Promise.all([first, second]); await flushPromises()
    setUiLocale('en-US'); vm.openEditUser(user())
    const saving = deferred(); api.updateUser.mockReturnValueOnce(saving.promise)
    const write = vm.handleUpdateUser(); await flushPromises()
    expect(wrapper.get('button[type="submit"]').text()).toBe('Saving...')
    saving.resolve(user()); await write
  })
})
