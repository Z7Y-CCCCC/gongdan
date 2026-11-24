import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import ProcessTaskSection from '../ProcessTaskSection.vue'
import ProcessCardSettingsModal from '../ProcessCardSettingsModal.vue'
import { setUiLocale } from '../../i18n'

const mocks = vi.hoisted(() => ({ fetchHomeProcessCards: vi.fn(), fetchProcesses: vi.fn(), fetchUserPreference: vi.fn(), setUserPreference: vi.fn() }))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...mocks }))
const wrappers: any[] = []
const processes = [1, 2, 3, 4, 5].map(id => ({ id, name: `工序${id}`, createdAt: '2026-11-24' }))
const card = (processId: number) => ({ processId, processName: `工序${processId}`, taskCount: 2, plannedQuantity: 10, goodQuantity: 3, defectQuantity: 1 })
const item = (processId: number, visible = true, color = '#123456') => ({ processId, visible, color })
function deferred() {
  let resolve!: (value?: any) => void
  let reject!: (reason?: any) => void
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
async function section() {
  const wrapper = shallowMount(ProcessTaskSection, { props: { token: 'token-one' } })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}

describe('persistent home process-card settings', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.fetchHomeProcessCards.mockResolvedValue({ items: [card(1), card(2)] })
    mocks.fetchProcesses.mockResolvedValue({ items: processes, total: processes.length })
    mocks.fetchUserPreference.mockResolvedValue({ value: JSON.stringify({ version: 1, items: [item(2), item(1, false), item(3)] }) })
    mocks.setUserPreference.mockResolvedValue({})
  })
  afterEach(() => { wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount()); setUiLocale('zh-CN') })

  it('switches static controls to English without translating process names or resetting preferences', async () => {
    const wrapper = await section()
    const before = (wrapper.vm as any).visibleCards
    setUiLocale('en-US')
    await flushPromises()
    expect(wrapper.get('h2').text()).toBe('Process tasks')
    expect(wrapper.find('button[aria-label="Settings"]').exists()).toBe(true)
    expect((wrapper.vm as any).visibleCards).toEqual(before)
    expect((wrapper.vm as any).visibleCards[0].processName).toBe('工序2')
    expect(mocks.setUserPreference).not.toHaveBeenCalled()
  })

  it('restores saved order/color/visibility and lists idle processes for configuration', async () => {
    const wrapper = await section()
    const vm = wrapper.vm as any
    expect(vm.visibleCards.map((entry: any) => entry.processId)).toEqual([2, 3])
    expect(vm.visibleCards[0].color).toBe('#123456')
    expect(vm.visibleCards[1].taskCount).toBe(0)
    expect(vm.modalSettings.map((entry: any) => entry.processId)).toEqual([2, 1, 3, 4, 5])
    expect(vm.modalSettings.find((entry: any) => entry.processId === 5).visible).toBe(false)
    expect(mocks.fetchUserPreference).toHaveBeenCalledWith('token-one', 'home_dashboard.process_cards')
    expect(mocks.setUserPreference).not.toHaveBeenCalled()
  })

  it('does not show zero task metrics before the actual aggregate request completes', async () => {
    const pending = deferred()
    mocks.fetchHomeProcessCards.mockReturnValueOnce(pending.promise)
    const wrapper = await section()
    expect((wrapper.vm as any).visibleCards).toEqual([])
    expect((wrapper.vm as any).loading).toBe(true)
    pending.resolve({ items: [card(2)] })
    await flushPromises()
    expect((wrapper.vm as any).visibleCards[0].taskCount).toBe(2)
  })

  it('loads the full process catalog and defaults to the first four without writing', async () => {
    mocks.fetchProcesses.mockResolvedValueOnce({ items: processes.slice(0, 3), total: 5 })
      .mockResolvedValueOnce({ items: processes.slice(3), total: 5 })
    mocks.fetchUserPreference.mockResolvedValue({ value: null })
    const wrapper = await section()
    const vm = wrapper.vm as any
    expect(vm.visibleCards.map((entry: any) => entry.processId)).toEqual([1, 2, 3, 4])
    expect(vm.modalSettings).toHaveLength(5)
    expect(mocks.fetchProcesses.mock.calls.map(call => call[1].page)).toEqual([1, 2])
    expect(mocks.setUserPreference).not.toHaveBeenCalled()
  })

  it('saves once through PUT, applies server-acknowledged settings and survives a new mount', async () => {
    const wrapper = await section()
    const vm = wrapper.vm as any
    await vm.openSettings()
    const pending = deferred()
    mocks.setUserPreference.mockReturnValueOnce(pending.promise)
    const chosen = [item(3, true, '#abcdef'), item(2, false)]
    const first = vm.onSettingsSaved(chosen)
    const second = vm.onSettingsSaved(chosen)
    expect(mocks.setUserPreference).toHaveBeenCalledTimes(1)
    expect(vm.showSettings).toBe(true)
    expect(vm.visibleCards.map((entry: any) => entry.processId)).toEqual([2, 3])
    pending.resolve()
    await Promise.all([first, second])
    expect(vm.showSettings).toBe(false)
    expect(vm.visibleCards.map((entry: any) => entry.processId)).toEqual([3])
    const saved = mocks.setUserPreference.mock.calls[0][2]
    expect(JSON.parse(saved)).toEqual({ version: 1, items: chosen })
    mocks.fetchUserPreference.mockResolvedValue({ value: saved })
    const next = await section()
    expect((next.vm as any).visibleCards.map((entry: any) => entry.processId)).toEqual([3])
    expect((next.vm as any).visibleCards[0].color).toBe('#abcdef')
  })

  it('keeps the modal and draft available after save failure', async () => {
    const wrapper = await section()
    const vm = wrapper.vm as any
    await vm.openSettings()
    mocks.setUserPreference.mockRejectedValueOnce(new Error('Offline'))
    await vm.onSettingsSaved([item(1)])
    expect(vm.showSettings).toBe(true)
    expect(vm.savingSettings).toBe(false)
    expect(vm.settingsError).toContain('保存失败')
    expect(vm.visibleCards.map((entry: any) => entry.processId)).toEqual([2, 3])
    await vm.onSettingsSaved([item(1)])
    expect(vm.showSettings).toBe(false)
  })

  it('ignores an old account preference response after the token changes', async () => {
    const old = deferred()
    mocks.fetchUserPreference.mockReturnValueOnce(old.promise)
    const wrapper = await section()
    mocks.fetchUserPreference.mockResolvedValueOnce({ value: JSON.stringify({ version: 1, items: [item(4)] }) })
    await wrapper.setProps({ token: 'token-two' })
    await flushPromises()
    old.resolve({ value: JSON.stringify({ version: 1, items: [item(1)] }) })
    await flushPromises()
    expect((wrapper.vm as any).visibleCards.map((entry: any) => entry.processId)).toEqual([4])
  })

  it('does not close or reset the real modal before its parent acknowledges saving', async () => {
    const wrapper = mount(ProcessCardSettingsModal, { props: { token: 'test', visible: true,
      settings: [{ ...item(1), processName: '工序1' }] }, attachTo: document.body, global: { stubs: { teleport: true } } })
    wrappers.push(wrapper)
    const input = wrapper.get('input[type="color"]')
    await input.setValue('#abcdef')
    await wrapper.get('button[aria-label="保存"]').trigger('click')
    expect(wrapper.emitted('save')?.[0]).toEqual([[item(1, true, '#abcdef')]])
    expect(wrapper.emitted('update:visible')).toBeUndefined()
    await wrapper.setProps({ saving: true })
    expect(wrapper.get('button[aria-label="保存"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('input[type="color"]').attributes('disabled')).toBeDefined()
    await wrapper.setProps({ saving: false, error: '保存失败' })
    expect((wrapper.get('input[type="color"]').element as HTMLInputElement).value).toBe('#abcdef')
    expect(wrapper.get('[role="alert"]').text()).toBe('保存失败')
  })
})
