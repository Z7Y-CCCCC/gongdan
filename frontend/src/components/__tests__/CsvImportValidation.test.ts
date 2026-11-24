import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import BaseDataPage from '../BaseDataPage.vue'
import ProductDefinitionPage from '../ProductDefinitionPage.vue'

const mocks = vi.hoisted(() => Object.fromEntries([
  'fetchUnits', 'fetchDefects', 'fetchProcesses', 'fetchProcessRoutes', 'fetchDepartments', 'fetchProducts', 'fetchProductCategories',
  'createUnit', 'createDefect', 'createProcess', 'createProcessRoute', 'createProduct', 'createSystemImportLog'
].map(name => [name, vi.fn()])))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...mocks }))
let wrapper: any
beforeEach(() => {
  vi.resetAllMocks()
  for (const [name, mock] of Object.entries(mocks)) {
    if (name.startsWith('fetch')) mock.mockResolvedValue({ items: [], total: 0 })
    else mock.mockResolvedValue({})
  }
  mocks.fetchProductCategories.mockResolvedValue([])
  vi.stubGlobal('FileReader', class {
    onload?: (event: any) => unknown
    readAsText() { void this.onload?.({ target: { result: '名称,备注\n可写前缀,ok\n"不完整记录,缺少结束引号' } }) }
  })
})
afterEach(() => { wrapper?.unmount(); vi.unstubAllGlobals() })

describe('CSV import rejects malformed files before writes', () => {
  it.each([
    ['单位', 'handleImportUnits'], ['不良品项', 'handleImportDefects'], ['工序', 'handleImportProcesses'], ['工艺路线', 'handleImportRoutes']
  ])('%s does not import a valid prefix of an unfinished CSV', async (_label, method) => {
    wrapper = shallowMount(BaseDataPage, { props: { token: 'fixture-token', users: [], activeBasedataMenu: 'unit' } })
    await flushPromises()
    await (wrapper.vm as any)[method]({ target: { files: [{ name: 'malformed.csv' }] } })
    await flushPromises()
    for (const name of ['createUnit', 'createDefect', 'createProcess', 'createProcessRoute', 'createSystemImportLog']) {
      expect(mocks[name]).not.toHaveBeenCalled()
    }
    expect(wrapper.emitted('error')?.flat().join('')).toContain('引号未闭合')
  })
  it('products do not import a valid prefix of an unfinished CSV', async () => {
    wrapper = shallowMount(ProductDefinitionPage, { props: { token: 'fixture-token' } })
    await flushPromises()
    await (wrapper.vm as any).handleImportProducts({ target: { files: [{ name: 'malformed.csv' }] } })
    await flushPromises()
    expect(mocks.createProduct).not.toHaveBeenCalled()
    expect(mocks.createSystemImportLog).not.toHaveBeenCalled()
    expect(wrapper.emitted('notice')?.flat().join('')).toContain('引号未闭合')
  })
})
