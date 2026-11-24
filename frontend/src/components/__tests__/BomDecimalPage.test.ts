import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import BomPage from '../BomPage.vue'

const mocks = vi.hoisted(() => ({
  fetchBoms: vi.fn(), fetchProducts: vi.fn(), createBom: vi.fn(), updateBom: vi.fn(), deleteBom: vi.fn()
}))
vi.mock('../../api', () => mocks)

const bom = (outputQuantity: string | number, quantity: string | number, scrapRate: string | number = '0.1234') => ({
  id: 1, code: 'BOM-PRECISION', productId: 1, enabled: true, outputQuantity,
  items: [{ id: 2, productId: 2, quantity, scrapRate, productCode: 'P2', productName: '子件' }]
})

describe('BOM decimal form round trips', () => {
  let wrapper: VueWrapper<any>
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.fetchBoms.mockResolvedValue({ items: [], total: 0 })
    mocks.fetchProducts.mockResolvedValue({ items: [{ id: 1, code: 'P1', name: '成品' }, { id: 2, code: 'P2', name: '子件' }], total: 2 })
    mocks.updateBom.mockResolvedValue({})
  })
  afterEach(() => wrapper?.unmount())
  async function mountPage(item: ReturnType<typeof bom>) {
    mocks.fetchBoms.mockResolvedValue({ items: [item], total: 1 })
    wrapper = shallowMount(BomPage, { props: { token: 'test-token' } })
    await flushPromises()
    ;(wrapper.vm as any).openEdit(item)
    await flushPromises()
    return wrapper.vm as any
  }

  it('edits and submits the DECIMAL(18,4) upper bound exactly as strings', async () => {
    const vm = await mountPage(bom('99999999999999.9999', '99999999999999.9998'))
    expect(wrapper.get('tbody').text()).toContain('99999999999999.9999')
    expect((wrapper.get('[aria-label="产出数量"]').element as HTMLInputElement).value).toBe('99999999999999.9999')
    await wrapper.get('[aria-label="产出数量"]').setValue('99999999999999.9999')
    await wrapper.get('[aria-label="子件用量"]').setValue('99999999999999.9998')
    await vm.saveBom()
    expect(mocks.updateBom.mock.calls[0][2]).toMatchObject({
      outputQuantity: '99999999999999.9999', items: [{ quantity: '99999999999999.9998', scrapRate: '0.1234' }]
    })
  })

  it('accepts legacy ordinary numeric responses and writes exact decimal strings', async () => {
    const vm = await mountPage(bom(2.5, 0.125, 1.5))
    expect(vm.form.outputQuantity).toBe('2.5')
    expect(vm.form.items[0].quantity).toBe('0.125')
    await vm.saveBom()
    expect(mocks.updateBom.mock.calls[0][2]).toMatchObject({ outputQuantity: '2.5', items: [{ quantity: '0.125', scrapRate: '1.5' }] })
  })

  it('normalizes scientific notation entered in text fields without losing precision', async () => {
    const vm = await mountPage(bom('1', '1'))
    await wrapper.get('[aria-label="产出数量"]').setValue('1e-4')
    await wrapper.get('[aria-label="子件用量"]').setValue('1.2345e3')
    await vm.saveBom()
    expect(mocks.updateBom.mock.calls[0][2]).toMatchObject({ outputQuantity: '0.0001', items: [{ quantity: '1234.5' }] })
  })

  it.each(['1.00001', '9007199254740993', '1e-5'])('refuses %s rather than rounding or overflowing it', async value => {
    const vm = await mountPage(bom('1', '1'))
    await wrapper.get('[aria-label="产出数量"]').setValue(value)
    await vm.saveBom()
    expect(mocks.updateBom).not.toHaveBeenCalled()
    expect(vm.showModal).toBe(true)
    expect(wrapper.emitted('notice')).toBeDefined()
  })
})
