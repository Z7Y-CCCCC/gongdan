import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, KeepAlive, ref } from 'vue'
import ProductDefinitionPage from '../ProductDefinitionPage.vue'

const api = vi.hoisted(() => Object.fromEntries(['fetchProducts', 'fetchUnits', 'fetchProcessRoutes',
  'fetchProductCategories', 'fetchSystemImportLogs', 'fetchProductOperationLogs', 'createProduct',
  'updateProduct', 'deleteProduct', 'createSystemImportLog'].map(name => [name, vi.fn()])))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))
const wrappers: VueWrapper<any>[] = [], readers: TestReader[] = []
class TestReader {
  static LOADING = 1
  readyState = 0
  text = ''
  onload?: (event: any) => unknown
  onerror?: () => unknown
  error: Error | null = null
  abort = vi.fn(() => { this.readyState = 2 })
  readAsText(file: File & { fixtureText: string }) { this.text = file.fixtureText; this.readyState = 1; readers.push(this) }
  deliver() { this.readyState = 2; return Promise.resolve(this.onload?.({ target: { result: this.text } })) }
}
function deferred() {
  let resolve!: (value?: any) => void
  const promise = new Promise<any>(yes => { resolve = yes })
  return { promise, resolve }
}
const csv = '产品编号,产品名称,单位\nP1,原产品一,件\nP2,原产品二,件'
async function page(change = 'token') {
  const visible = ref(true)
  const Other = defineComponent({ setup: () => () => h('div') })
  const Host = defineComponent({ setup: () => () => h(KeepAlive, {}, {
    default: () => visible.value ? h(ProductDefinitionPage, { token: 'actor-A' }) : h(Other)
  }) })
  const wrapper = change === 'deactivate' ? shallowMount(Host, { global: { stubs: { ProductDefinitionPage: false, KeepAlive: false } } })
    : shallowMount(ProductDefinitionPage, { props: { token: 'actor-A' } })
  wrappers.push(wrapper); await flushPromises()
  const child = change === 'deactivate' ? wrapper.findComponent(ProductDefinitionPage) : wrapper
  const leave = async () => {
    if (change === 'token') await wrapper.setProps({ token: 'actor-B' })
    else if (change === 'deactivate') { visible.value = false; await flushPromises() }
    else wrapper.unmount()
  }
  return { child, vm: child.vm as any, leave }
}
async function choose(vm: any, text = csv) {
  const input = document.createElement('input')
  const file = Object.assign(new File([text], 'fixture.csv', { type: 'text/csv' }), { fixtureText: text })
  Object.defineProperty(input, 'files', { configurable: true, value: [file] })
  await vm.handleImportProducts({ target: input })
  return readers[readers.length - 1]
}
beforeEach(() => {
  vi.resetAllMocks(); readers.length = 0
  for (const [name, method] of Object.entries(api)) method.mockResolvedValue(name.startsWith('fetch') ? { items: [], total: 0 } : {})
  api.fetchUnits.mockResolvedValue({ items: [{ id: 1, name: '件' }], total: 1 })
  api.fetchProductCategories.mockResolvedValue([])
  vi.stubGlobal('FileReader', TestReader)
})
afterEach(() => { wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount()); vi.unstubAllGlobals() })

describe('product CSV/import/delete page and session boundaries', () => {
  it('safely disposes without a pending reader even when an adapter omits FileReader state constants', async () => {
    const { child } = await page()
    vi.stubGlobal('FileReader', class {})
    expect(() => child.unmount()).not.toThrow()
  })
  it.each(['token', 'deactivate', 'unmount'])('aborts an unread file and ignores its late load after %s', async change => {
    const { child, vm, leave } = await page(change), reader = await choose(vm)
    await leave(); await reader.deliver(); await flushPromises()
    expect(reader.abort).toHaveBeenCalledTimes(1)
    expect(api.createProduct).not.toHaveBeenCalled()
    expect(api.createSystemImportLog).not.toHaveBeenCalled()
    expect(child.emitted('notice')).toBeUndefined()
    expect(vm.productImporting).toBe(false)
  })
  it.each(['token', 'deactivate', 'unmount'])('does not continue rows or write a late import log after %s', async change => {
    const { child, vm, leave } = await page(change), pending = deferred(), reader = await choose(vm)
    api.createProduct.mockReturnValueOnce(pending.promise)
    const operation = reader.deliver(); await flushPromises()
    expect(api.createProduct).toHaveBeenCalledTimes(1)
    expect(api.createProduct.mock.calls[0][0]).toBe('actor-A')
    await leave(); pending.resolve(); await operation; await flushPromises()
    expect(api.createProduct).toHaveBeenCalledTimes(1)
    expect(api.createSystemImportLog).not.toHaveBeenCalled()
    expect(child.emitted('notice')).toBeUndefined()
    expect(vm.productImporting).toBe(false)
  })
  it.each(['token', 'deactivate', 'unmount'])('cancels a pending row deletion confirmation after %s', async change => {
    const { vm, leave } = await page(change)
    const operation = vm.removeProduct({ id: 1, name: '原产品' })
    await leave(); vm.handleConfirm(); await operation
    expect(api.deleteProduct).not.toHaveBeenCalled()
    expect(vm.visible).toBe(false)
  })
  it('locks the import before FileReader returns, not after the first write starts', async () => {
    const { vm } = await page()
    await choose(vm); await choose(vm)
    expect(readers).toHaveLength(1)
    expect(vm.productImporting).toBe(true)
  })
  it('does not write a valid prefix when the final CSV row is malformed', async () => {
    const { vm } = await page(), reader = await choose(vm, '产品名称,单位\n原产品,件\n"bad')
    await reader.deliver(); await flushPromises()
    expect(api.createProduct).not.toHaveBeenCalled()
    expect(api.createSystemImportLog).not.toHaveBeenCalled()
    expect(vm.productImporting).toBe(false)
  })
  it('keeps the original business text, captured unit mapping, and original token for every row', async () => {
    const { vm } = await page(), reader = await choose(vm, '产品编号,产品名称,单位\n=CODE,"+原文\r\n第二行",件')
    vm.units = [{ id: 99, name: '件' }]
    await reader.deliver(); await flushPromises()
    expect(api.createProduct).toHaveBeenCalledExactlyOnceWith('actor-A', expect.objectContaining({ code: '=CODE', name: '+原文\r\n第二行', unitId: 1 }))
    expect(api.createSystemImportLog.mock.calls[0][0]).toBe('actor-A')
  })
})
