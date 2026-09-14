import { afterEach, describe, expect, it, vi } from 'vitest'
import { shallowMount, type VueWrapper } from '@vue/test-utils'
import PluginRuntimePage from '../PluginRuntimePage.vue'

const proxyRequest = vi.hoisted(() => vi.fn())
vi.mock('../../api', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api')>(), proxyPluginRequest: proxyRequest
}))

describe('plugin iframe isolation', () => {
  let wrapper: VueWrapper<any> | undefined
  afterEach(() => {
    wrapper?.unmount()
    proxyRequest.mockReset()
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
})
