import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import PluginRuntimePage from '../PluginRuntimePage.vue'
import { uiLocale } from '../../i18n'

const proxyRequest = vi.hoisted(() => vi.fn())
vi.mock('../../api', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api')>(), proxyPluginRequest: proxyRequest
}))

describe('plugin iframe isolation', () => {
  let wrapper: VueWrapper<any> | undefined
  afterEach(() => {
    wrapper?.unmount()
    proxyRequest.mockReset()
    uiLocale.value = 'zh-CN'
    vi.restoreAllMocks()
  })

  function create(frontendEntry: string) {
    wrapper = shallowMount(PluginRuntimePage, {
      props: { token: 'test-token', plugin: { pluginKey: 'demo', name: '演示', version: '1.0', manifest: { frontendEntry } } as any }
    })
    return wrapper
  }

  it.each(['/plugin/index.html', `${window.location.origin}/plugin/index.html`, 'javascript:alert(1)', 'data:text/html,test'])
    ('does not mount an unsafe iframe entry: %s', (entry) => {
      const page = create(entry)
      expect(page.find('iframe').exists()).toBe(false)
      expect(page.text()).toContain('插件页面地址不安全')
    })

  it('continues to allow a separate-origin HTTP(S) plugin', () => {
    const page = create('https://plugin.example.test/index.html')
    expect(page.find('iframe').attributes('src')).toBe('https://plugin.example.test/index.html')
  })

  it('refuses bridge messages from a same-origin plugin entry', async () => {
    const page = create('/plugin/index.html')
    const vm = page.vm as any
    vm.frameRef = { contentWindow: window }
    await vm.handlePluginMessage(new MessageEvent('message', {
      source: window, origin: window.location.origin,
      data: { type: 'plugin-api-request', pluginKey: 'demo', requestId: 'req', method: 'GET', path: '/api/plugin/demo/items' }
    }))
    expect(proxyRequest).not.toHaveBeenCalled()
  })

  function message(requestId = 'req') {
    return new MessageEvent('message', { source: window, origin: 'https://plugin.example.test',
      data: { type: 'plugin-api-request', pluginKey: 'demo', requestId, method: 'POST', path: '/api/plugin/demo/items', body: { quantity: 1 } } })
  }
  function deferred() {
    let resolve!: (value: any) => void
    let reject!: (reason: any) => void
    const promise = new Promise((yes, no) => { resolve = yes; reject = no })
    return { promise, resolve, reject }
  }

  it('sends locale in platform context without reloading the iframe', async () => {
    const page = create('https://plugin.example.test/index.html')
    const vm = page.vm as any
    vm.frameRef = { contentWindow: window }
    const post = vi.spyOn(window, 'postMessage').mockImplementation(() => {})
    const src = page.get('iframe').attributes('src')
    const generation = vm.frameGeneration
    uiLocale.value = 'en-US'
    await flushPromises()
    expect(post).toHaveBeenCalledWith(expect.objectContaining({ type: 'platform-context', locale: 'en-US', context: { locale: 'en-US' } }), 'https://plugin.example.test')
    expect(page.get('iframe').attributes('src')).toBe(src)
    expect(vm.frameGeneration).toBe(generation)
  })

  it('does not forward an old token response into the replacement frame', async () => {
    const page = create('https://plugin.example.test/index.html')
    const vm = page.vm as any
    vm.frameRef = { contentWindow: window }
    const post = vi.spyOn(window, 'postMessage').mockImplementation(() => {})
    const pending = deferred()
    proxyRequest.mockReturnValueOnce(pending.promise)
    const request = vm.handlePluginMessage(message())
    await page.setProps({ token: 'new-token' })
    pending.resolve({ private: 'old factory data' })
    await request
    expect(post.mock.calls.filter(call => call[0]?.type === 'plugin-api-response')).toEqual([])
  })

  it('does not forward an old plugin error into a different plugin context', async () => {
    const page = create('https://plugin.example.test/index.html')
    const vm = page.vm as any
    vm.frameRef = { contentWindow: window }
    const post = vi.spyOn(window, 'postMessage').mockImplementation(() => {})
    const pending = deferred()
    proxyRequest.mockReturnValueOnce(pending.promise)
    const request = vm.handlePluginMessage(message())
    await page.setProps({ plugin: { pluginKey: 'next', name: '下一个', version: '1.0', manifest: { frontendEntry: 'https://next.example.test/index.html' } } as any })
    pending.reject(new Error('old private failure'))
    await request
    expect(post.mock.calls.filter(call => call[0]?.type === 'plugin-api-response')).toEqual([])
  })

  it('does not duplicate a pending write when the plugin retries the same request ID', async () => {
    const page = create('https://plugin.example.test/index.html')
    const vm = page.vm as any
    vm.frameRef = { contentWindow: window }
    const post = vi.spyOn(window, 'postMessage').mockImplementation(() => {})
    const pending = deferred()
    proxyRequest.mockReturnValueOnce(pending.promise)
    const first = vm.handlePluginMessage(message('write-1'))
    const second = vm.handlePluginMessage(message('write-1'))
    expect(proxyRequest).toHaveBeenCalledTimes(1)
    pending.resolve({ id: 9 })
    await Promise.all([first, second])
    expect(post.mock.calls.filter(call => call[0]?.type === 'plugin-api-response')).toHaveLength(1)
  })
})
