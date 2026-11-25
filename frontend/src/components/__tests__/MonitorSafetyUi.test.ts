import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import CacheListPage from '../CacheListPage.vue'
import CacheMonitorPage from '../CacheMonitorPage.vue'
import OnlineUsersPage from '../OnlineUsersPage.vue'
import DeleteConfirmDialog from '../DeleteConfirmDialog.vue'

const api = vi.hoisted(() => ({ fetchCacheNames: vi.fn(), fetchCacheRecoveries: vi.fn(), fetchCacheKeys: vi.fn(),
  fetchCacheValue: vi.fn(), clearCacheName: vi.fn(), clearCacheKey: vi.fn(), clearAllCache: vi.fn(), restoreCacheRecovery: vi.fn(),
  fetchCacheMetrics: vi.fn(), fetchOnlineUsers: vi.fn(), forceLogoutOnlineUser: vi.fn() }))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))
const wrappers: VueWrapper<any>[] = []
function deferred<T = any>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const online = (id = 1, loginName = '当前用户') => ({ id, loginName, sessionId: `public-id-${id}`, departmentName: '制造',
  ipAddress: '127.0.0.1', loginLocation: '内网', operatingSystem: 'Windows', browser: 'Chromium', loginTime: '2026-11-24T10:00:00' })
const metrics = (keyCount = '12') => ({ basicInfo: { status: '正常', keyCount }, commandStats: [{ name: 'GET', count: 2 }],
  memoryInfo: { usedBytes: 1024, maxBytes: 2048, usedPercent: 50, usedHuman: '1KB', maxHuman: '2KB' } })
async function page(component: any) {
  const wrapper = shallowMount(component, { props: { token: 'fixture-one' } }); wrappers.push(wrapper)
  await flushPromises(); return wrapper
}
beforeEach(() => {
  vi.resetAllMocks()
  api.fetchCacheNames.mockResolvedValue([{ cacheName: 'cache-A', remark: 'first', keyCount: 2 }, { cacheName: 'cache-B', remark: 'second', keyCount: 1 }])
  api.fetchCacheRecoveries.mockResolvedValue([])
  api.fetchCacheKeys.mockResolvedValue(['key-one', 'key-two'])
  api.fetchCacheValue.mockResolvedValue({ cacheValue: 'current value' })
  api.clearAllCache.mockResolvedValue({ affected: 1, recoveryWindowMinutes: 5 })
  api.fetchCacheMetrics.mockResolvedValue(metrics())
  api.fetchOnlineUsers.mockResolvedValue({ items: [online()], total: 21 })
  api.forceLogoutOnlineUser.mockResolvedValue(undefined)
})
afterEach(() => { wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount()) })

describe('monitor data identity, truthful failure and destructive-action locks', () => {
  it('keeps cache keys bound to the latest selected cache instead of a slower earlier selection', async () => {
    const wrapper = await page(CacheListPage); const vm = wrapper.vm as any
    const old = deferred(); api.fetchCacheKeys.mockReturnValueOnce(old.promise)
    const first = vm.selectCache('cache-A')
    api.fetchCacheKeys.mockResolvedValueOnce(['cache-B-key'])
    await vm.selectCache('cache-B')
    old.resolve(['cache-A-key']); await first; await flushPromises()
    expect(vm.selectedCacheName).toBe('cache-B')
    expect(vm.cacheKeys).toEqual(['cache-B-key'])
  })

  it('keeps cache contents bound to the latest selected key', async () => {
    const wrapper = await page(CacheListPage); const vm = wrapper.vm as any
    await vm.selectCache('cache-A')
    const old = deferred(); api.fetchCacheValue.mockReturnValueOnce(old.promise)
    const first = vm.selectKey('key-one')
    api.fetchCacheValue.mockResolvedValueOnce({ cacheValue: 'second value' })
    await vm.selectKey('key-two')
    old.resolve({ cacheValue: 'first value' }); await first; await flushPromises()
    expect(vm.selectedCacheKey).toBe('key-two')
    expect(vm.cacheValue).toBe('second value')
  })

  it('shows a persistent retryable cache-list error instead of falsely claiming there are no caches', async () => {
    api.fetchCacheNames.mockRejectedValueOnce(new Error('缓存接口不可用'))
    const wrapper = await page(CacheListPage)
    expect(wrapper.get('[role="alert"]').text()).toContain('缓存接口不可用')
    expect(wrapper.text()).not.toContain('暂无缓存')
    await wrapper.get('[role="alert"] button').trigger('click'); await flushPromises()
    expect(wrapper.get('#cache-names-table').text()).toContain('cache-A')
  })

  it('provides keyboard-operable cache/key selection rather than only clickable table rows', async () => {
    const wrapper = await page(CacheListPage)
    const selected = wrapper.get('#cache-names-table tbody .cache-select-btn')
    expect(selected.element.tagName).toBe('BUTTON')
    await selected.trigger('click'); await flushPromises()
    expect(wrapper.find('#cache-keys-table tbody .cache-select-btn').exists()).toBe(true)
  })

  it('does not repeat a clear-all write while its first confirmed request is still pending', async () => {
    const wrapper = await page(CacheListPage); const vm = wrapper.vm as any
    const pending = deferred(); api.clearAllCache.mockReturnValueOnce(pending.promise)
    const first = vm.clearEverything(); vm.handleConfirm(); await flushPromises()
    const second = vm.clearEverything(); vm.handleConfirm(); await flushPromises()
    expect(api.clearAllCache).toHaveBeenCalledTimes(1)
    expect(vm.confirmLoading).toBe(true)
    pending.resolve({ affected: 1, recoveryWindowMinutes: 5 }); await Promise.all([first, second]); await flushPromises()
    expect(vm.confirmLoading).toBe(false)
  })

  it('does not replace the latest online-user query with a slower previous search', async () => {
    const wrapper = await page(OnlineUsersPage); const vm = wrapper.vm as any
    const old = deferred(); api.fetchOnlineUsers.mockReturnValueOnce(old.promise)
    vm.filters.loginName = '旧条件'; vm.search()
    api.fetchOnlineUsers.mockResolvedValueOnce({ items: [online(2, '新条件用户')], total: 1 })
    vm.filters.loginName = '新条件'; vm.search(); await flushPromises()
    old.resolve({ items: [online(3, '旧条件用户')], total: 99 }); await flushPromises()
    expect(wrapper.get('tbody').text()).toContain('新条件用户')
    expect(wrapper.get('tbody').text()).not.toContain('旧条件用户')
  })

  it('reloads a valid page after the last online-session page disappears', async () => {
    const wrapper = await page(OnlineUsersPage); const vm = wrapper.vm as any
    api.fetchOnlineUsers.mockResolvedValueOnce({ items: [], total: 20 })
      .mockResolvedValueOnce({ items: [online(20, '有效页用户')], total: 20 })
    vm.changePage(3); await flushPromises()
    expect(vm.page).toBe(2)
    expect(api.fetchOnlineUsers).toHaveBeenLastCalledWith('fixture-one', expect.objectContaining({ page: 2 }))
    expect(wrapper.get('tbody').text()).toContain('有效页用户')
  })

  it('locks a pending force-logout write and keeps its named confirmation busy', async () => {
    const wrapper = await page(OnlineUsersPage); const vm = wrapper.vm as any
    const pending = deferred(); api.forceLogoutOnlineUser.mockReturnValueOnce(pending.promise)
    const first = vm.forceLogout(online()); vm.handleConfirm(); await flushPromises()
    const second = vm.forceLogout(online()); vm.handleConfirm(); await flushPromises()
    expect(api.forceLogoutOnlineUser).toHaveBeenCalledTimes(1)
    expect(wrapper.getComponent(DeleteConfirmDialog).props('loading')).toBe(true)
    pending.resolve(undefined); await Promise.all([first, second]); await flushPromises()
  })

  it('does not display fake zero memory metrics or an indefinite loading status after cache monitoring fails', async () => {
    api.fetchCacheMetrics.mockRejectedValueOnce(new Error('监控接口不可用'))
    const wrapper = await page(CacheMonitorPage)
    expect(wrapper.get('[role="alert"]').text()).toContain('监控接口不可用')
    expect(wrapper.find('.monitor-metrics-grid').exists()).toBe(false)
    await wrapper.get('[role="alert"] button').trigger('click'); await flushPromises()
    expect(wrapper.find('.monitor-metrics-grid').exists()).toBe(true)
  })
})
