import { defineComponent, h, KeepAlive } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import PluginCenterPage from '../PluginCenterPage.vue'
import FactorySettingsPage from '../FactorySettingsPage.vue'
import UReportPage from '../UReportPage.vue'
import { setUiLocale } from '../../i18n'
const mocks = vi.hoisted(() => Object.fromEntries(['fetchPlugins', 'createPlugin', 'registerPluginVersion', 'enablePlugin',
  'refreshPluginHealth', 'fetchCurrentFactory', 'updateCurrentFactory', 'establishUReportSession'].map(name => [name, vi.fn()])))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...mocks }))
const wrappers: any[] = []
function deferred() {
  let resolve!: (value?: any) => void
  let reject!: (reason?: any) => void
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const plugin = (pluginKey = 'fixture-plugin') => ({ pluginKey, name: pluginKey, description: '', configJson: '{}',
  runtimeMode: 'IFRAME', factoryStatus: 'DISABLED', healthStatus: 'UP', versions: [{ id: 1, version: '1.0', status: 'READY' }],
  currentStableVersion: '1.0', manifestJson: '{}' })
async function page(component: any) {
  const wrapper = shallowMount(component, { props: { token: 'token-one' } })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}
beforeEach(() => {
  vi.resetAllMocks()
  mocks.fetchPlugins.mockResolvedValue([plugin()])
  mocks.enablePlugin.mockResolvedValue({})
  mocks.refreshPluginHealth.mockResolvedValue({})
  mocks.fetchCurrentFactory.mockResolvedValue({ id: 1, name: '工厂1', code: 'F1', enabled: true })
  mocks.updateCurrentFactory.mockResolvedValue({ id: 1, name: '新工厂', code: 'F1', enabled: true })
  mocks.establishUReportSession.mockResolvedValue({ path: '/ureport/designer' })
})
afterEach(() => { wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount()); vi.useRealTimers(); vi.restoreAllMocks() })

describe('plugin, factory and report functional lifecycles', () => {
  it('locks duplicate plugin actions and retains dirty configuration after a health refresh', async () => {
    const wrapper = await page(PluginCenterPage)
    const vm = wrapper.vm as any
    vm.configJson = '{"draft":true}'
    const pending = deferred()
    mocks.enablePlugin.mockReturnValueOnce(pending.promise)
    const action = () => mocks.enablePlugin('token-one', 'fixture-plugin', '1.0')
    const first = vm.runAction(action, '插件已启用')
    const second = vm.runAction(action, '插件已启用')
    expect(mocks.enablePlugin).toHaveBeenCalledTimes(1)
    pending.resolve()
    await Promise.all([first, second])
    expect(vm.configJson).toBe('{"draft":true}')
    vm.refreshHealthSelected()
    await flushPromises()
    expect(vm.configJson).toBe('{"draft":true}')
  })

  it('ignores an old account plugin catalog after token change', async () => {
    const old = deferred()
    mocks.fetchPlugins.mockReturnValueOnce(old.promise)
    const wrapper = await page(PluginCenterPage)
    mocks.fetchPlugins.mockResolvedValueOnce([plugin('current')])
    await wrapper.setProps({ token: 'token-two' })
    await flushPromises()
    old.resolve([plugin('old')])
    await flushPromises()
    expect((wrapper.vm as any).selectedKey).toBe('current')
  })

  it('refreshes a KeepAlive plugin page while preserving its dirty configuration', async () => {
    const Other = defineComponent({ setup: () => () => h('p', 'Other') })
    const Host = defineComponent({ props: { show: Boolean }, setup: props => () => h(KeepAlive, null, {
      default: () => props.show ? h(PluginCenterPage, { token: 'token-one' }) : h(Other)
    }) })
    const wrapper = mount(Host, { props: { show: true }, global: { stubs: { UiIcon: true, TableConfigButton: true, DeleteConfirmDialog: true } } })
    wrappers.push(wrapper)
    await flushPromises()
    const component = wrapper.getComponent(PluginCenterPage)
    ;(component.vm as any).configJson = '{"draft":true}'
    const before = mocks.fetchPlugins.mock.calls.length
    await wrapper.setProps({ show: false })
    await wrapper.setProps({ show: true })
    await flushPromises()
    expect(mocks.fetchPlugins.mock.calls.length).toBe(before + 1)
    expect((wrapper.getComponent(PluginCenterPage).vm as any).configJson).toBe('{"draft":true}')
  })

  it('does not duplicate factory saves and keeps the name draft after a failure', async () => {
    const wrapper = await page(FactorySettingsPage)
    const vm = wrapper.vm as any
    vm.form.name = '新工厂'
    const pending = deferred()
    mocks.updateCurrentFactory.mockReturnValueOnce(pending.promise)
    const first = vm.saveFactory()
    const second = vm.saveFactory()
    expect(mocks.updateCurrentFactory).toHaveBeenCalledTimes(1)
    pending.reject(new Error('保存失败'))
    await Promise.all([first, second])
    expect(vm.form.name).toBe('新工厂')
    expect(vm.saving).toBe(false)
    expect(wrapper.emitted('error')).toContainEqual(['保存失败'])
  })

  it('does not overwrite a newer factory session with an old load response', async () => {
    const old = deferred()
    mocks.fetchCurrentFactory.mockReturnValueOnce(old.promise)
    const wrapper = await page(FactorySettingsPage)
    mocks.fetchCurrentFactory.mockResolvedValueOnce({ id: 2, name: '当前工厂', code: 'F2', enabled: true })
    await wrapper.setProps({ token: 'token-two' })
    await flushPromises()
    old.resolve({ id: 1, name: '旧工厂' })
    await flushPromises()
    expect((wrapper.vm as any).factory.id).toBe(2)
  })

  it('never claims the factory is disabled when its first load fails', async () => {
    mocks.fetchCurrentFactory.mockRejectedValueOnce(new Error('读取失败'))
    const wrapper = await page(FactorySettingsPage)
    expect(wrapper.get('[role="alert"]').text()).toBe('读取失败')
    expect(wrapper.find('.factory-summary').exists()).toBe(false)
  })

  it('ignores a previous report-session response after the token changes', async () => {
    const old = deferred()
    mocks.establishUReportSession.mockReturnValueOnce(old.promise)
    const wrapper = await page(UReportPage)
    mocks.establishUReportSession.mockResolvedValueOnce({ path: '/ureport/current' })
    await wrapper.setProps({ token: 'token-two' })
    await flushPromises()
    old.resolve({ path: '/ureport/old' })
    await flushPromises()
    expect((wrapper.vm as any).iframeSrc).toContain('/ureport/current')
    expect((wrapper.vm as any).iframeSrc).not.toContain('/ureport/old')
  })

  it('unblocks report refresh and exposes a retry when the iframe never loads', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const wrapper = await page(UReportPage)
    expect((wrapper.vm as any).frameLoading).toBe(true)
    await vi.advanceTimersByTimeAsync(20001)
    expect((wrapper.vm as any).frameLoading).toBe(false)
    expect((wrapper.vm as any).iframeSrc).toBe('')
    expect(wrapper.text()).toContain('加载失败')
    expect(wrapper.find('button[aria-label="重新加载"]').exists()).toBe(true)
  })

  it('keeps a native report draft open on language changes until explicit discard is confirmed', async () => {
    const wrapper = await page(UReportPage)
    const vm = wrapper.vm as any
    vm.onFrameLoaded()
    const original = vm.iframeSrc
    const doc = document.implementation.createHTMLDocument('Report')
    doc.body.innerHTML = '<button id="__save_btn"></button><textarea>Unsaved report</textarea>'
    const frame = wrapper.get('iframe').element
    Object.defineProperty(frame, 'contentDocument', { configurable: true, value: doc })
    Object.defineProperty(frame, 'contentWindow', { configurable: true, value: { location: { href: `${window.location.origin}/ureport/designer?_u=file%3ATEMPLATE` } } })
    setUiLocale('en-US')
    await flushPromises()
    expect(mocks.establishUReportSession).toHaveBeenCalledTimes(1)
    expect(vm.iframeSrc).toBe(original)
    expect(wrapper.text()).toContain('Apply language')
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    vm.requestReload()
    await flushPromises()
    expect(confirm).toHaveBeenCalledWith('Reloading will discard unsaved report changes. Continue?')
    expect(mocks.establishUReportSession).toHaveBeenCalledTimes(1)
    expect(vm.iframeSrc).toBe(original)
    expect(doc.querySelector('textarea')!.value).toBe('Unsaved report')
    confirm.mockReturnValue(true)
    vm.requestReload()
    await flushPromises()
    expect(mocks.establishUReportSession).toHaveBeenCalledTimes(2)
    const url = new URL(vm.iframeSrc, window.location.href)
    expect(url.searchParams.get('ui_locale')).toBe('en-US')
    expect(url.searchParams.get('_u')).toBe('file:TEMPLATE')
  })

  it('applies language to a clean native report without a discard prompt', async () => {
    const wrapper = await page(UReportPage)
    const vm = wrapper.vm as any
    vm.onFrameLoaded()
    const doc = document.implementation.createHTMLDocument('Report')
    doc.body.innerHTML = '<button id="__save_btn" class="disabled"></button>'
    const frame = wrapper.get('iframe').element
    Object.defineProperty(frame, 'contentDocument', { configurable: true, value: doc })
    Object.defineProperty(frame, 'contentWindow', { configurable: true, value: { location: { href: `${window.location.origin}/ureport/designer` } } })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    setUiLocale('en-US')
    await flushPromises()
    vm.requestReload()
    await flushPromises()
    expect(confirm).not.toHaveBeenCalled()
    expect(mocks.establishUReportSession).toHaveBeenCalledTimes(2)
    expect(vm.iframeSrc).toContain('ui_locale=en-US')
  })
})
