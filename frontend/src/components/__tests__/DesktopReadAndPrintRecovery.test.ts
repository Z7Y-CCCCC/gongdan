import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import InventoryDetailPage from '../InventoryDetailPage.vue'
import ReportCenterPage from '../ReportCenterPage.vue'
import InboundReceiptPage from '../InboundReceiptPage.vue'

const api = vi.hoisted(() => ({
  fetchInventoryDetails: vi.fn(), fetchWorkOrders: vi.fn(), fetchReports: vi.fn(),
  fetchInboundReceipts: vi.fn(), fetchCustomFields: vi.fn()
}))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))

const wrappers: VueWrapper<any>[] = []
function mountPage(component: any) {
  const wrapper = shallowMount(component, { attachTo: document.body, props: { token: 'factory-one' }, global: { stubs: { PageLoadFeedback: false } } })
  wrappers.push(wrapper)
  return wrapper
}
function pending<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}
function inventory(receiptCode: string, total = 1) {
  return { total, items: [{ receiptCode, receiptType: '销售出库', productName: '产品', changeQty: -1, receiptQty: 1, sourceType: 'OUT' }] }
}
const emptyPage = { items: [], total: 0 }

beforeEach(() => {
  vi.resetAllMocks()
  for (const method of Object.values(api)) method.mockResolvedValue(emptyPage)
  localStorage.clear()
})
afterEach(() => {
  wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount())
  vi.restoreAllMocks()
})

describe('inventory movement read recovery', () => {
  it('separates initial loading, failed request, and a confirmed empty result', async () => {
    api.fetchInventoryDetails.mockRejectedValueOnce(new Error('暂时无法连接'))
    const page = mountPage(InventoryDetailPage)
    expect(page.get('[role="status"]').text()).toContain('加载中')
    expect(page.find('.inventory-detail-data-table').exists()).toBe(false)
    await flushPromises()
    expect(page.get('[role="alert"]').text()).toContain('暂时无法连接')
    expect(page.find('.page-info').exists()).toBe(false)
    expect(page.get('button[aria-label="导出Excel"]').attributes('disabled')).toBeDefined()
    expect(page.get('select.page-size-select').attributes('disabled')).toBeUndefined()
    expect(page.text()).not.toContain('暂无库存收发明细数据')
    await page.get('.page-load-feedback button').trigger('click')
    await flushPromises()
    expect(page.find('[role="alert"]').exists()).toBe(false)
    expect(page.get('.inventory-detail-data-table').text()).toContain('暂无库存收发明细数据')
    expect(page.get('.page-info').text()).toContain('共0条结果')
  })

  it('does not display or export previous filter data after a failed query; retry uses the failed request', async () => {
    api.fetchInventoryDetails.mockResolvedValueOnce(inventory('ORIGINAL')).mockRejectedValueOnce(new Error('503'))
    const page = mountPage(InventoryDetailPage)
    await flushPromises()
    const code = page.get<HTMLInputElement>('input[id$="-receipt-code"]')
    await code.setValue('FAILED-FILTER')
    await page.get('button[aria-label="查询库存明细"]').trigger('click')
    await flushPromises()
    expect(code.element.value).toBe('FAILED-FILTER')
    expect(page.text()).not.toContain('ORIGINAL')
    expect(page.get('[role="alert"]').text()).toContain('503')
    expect(page.get('button[aria-label="CSV备份"]').attributes('disabled')).toBeDefined()
    await code.setValue('NOT-SUBMITTED')
    api.fetchInventoryDetails.mockResolvedValueOnce(inventory('RETRIED'))
    await page.get('.page-load-feedback button').trigger('click')
    await flushPromises()
    expect(api.fetchInventoryDetails.mock.lastCall?.[1]).toMatchObject({ receiptCode: 'FAILED-FILTER', page: 1, size: 10 })
    expect(page.get('.inventory-detail-data-table').text()).toContain('RETRIED')
    expect(code.element.value).toBe('NOT-SUBMITTED')
    expect(page.text()).toContain('筛选已修改，点击查询应用。')
  })

  it('preserves failed page and page size, hides the old page, and rejects a superseded response', async () => {
    api.fetchInventoryDetails.mockResolvedValueOnce(inventory('PAGE-ONE', 50)).mockRejectedValueOnce(new Error('分页暂不可用'))
    const page = mountPage(InventoryDetailPage)
    await flushPromises()
    await page.get('button[aria-label="下一页"]').trigger('click')
    await flushPromises()
    expect(page.get('.current-page').text()).toBe('2')
    expect(page.find('.page-info').exists()).toBe(false)
    expect(page.text()).not.toContain('PAGE-ONE')
    api.fetchInventoryDetails.mockResolvedValueOnce(inventory('PAGE-TWO', 50))
    await page.get('.page-load-feedback button').trigger('click')
    await flushPromises()
    expect(page.get('tbody tr td').text()).toBe('11')
    expect(api.fetchInventoryDetails.mock.lastCall?.[1]).toMatchObject({ page: 2, size: 10 })

    const older = pending<ReturnType<typeof inventory>>()
    api.fetchInventoryDetails.mockReturnValueOnce(older.promise).mockResolvedValueOnce(inventory('LATEST'))
    await page.get('select.page-size-select').setValue('20')
    await page.get('button[aria-label="查询库存明细"]').trigger('click')
    older.resolve(inventory('STALE'))
    await flushPromises()
    expect(page.text()).toContain('LATEST')
    expect(page.text()).not.toContain('STALE')
    expect(api.fetchInventoryDetails.mock.lastCall?.[1]).toMatchObject({ page: 1, size: 20 })
  })

  it('keeps a same-query result labeled on failure but never keeps another session’s results', async () => {
    api.fetchInventoryDetails.mockResolvedValueOnce(inventory('ORIGINAL')).mockRejectedValueOnce(new Error('读取失败'))
    const page = mountPage(InventoryDetailPage)
    await flushPromises()
    await page.get('button[aria-label="查询库存明细"]').trigger('click')
    await flushPromises()
    expect(page.text()).toContain('ORIGINAL')
    expect(page.get('[role="alert"]').text()).toContain('显示上次成功加载的数据。')
    const next = pending<ReturnType<typeof inventory>>()
    api.fetchInventoryDetails.mockReturnValueOnce(next.promise)
    await page.setProps({ token: 'factory-two' })
    expect(page.text()).not.toContain('ORIGINAL')
    next.resolve(inventory('NEW-SESSION'))
    await flushPromises()
    expect(page.text()).toContain('NEW-SESSION')
  })
})

describe('production report read recovery', () => {
  it('does not show zero metrics or empty tables on first failure and retries both datasets', async () => {
    api.fetchReports.mockRejectedValueOnce(new Error('报工读取失败'))
    const page = mountPage(ReportCenterPage)
    await flushPromises()
    expect(page.get('[role="alert"]').text()).toContain('报工读取失败')
    expect(page.find('.metric-grid').exists()).toBe(false)
    expect(page.find('#report-latest-table').exists()).toBe(false)
    await page.get('.page-load-feedback button').trigger('click')
    await flushPromises()
    expect(api.fetchWorkOrders).toHaveBeenCalledTimes(2)
    expect(api.fetchReports).toHaveBeenCalledTimes(2)
    expect(page.find('[role="alert"]').exists()).toBe(false)
    expect(page.get('.metric-card strong').text()).toBe('0')
    expect(page.get('#report-latest-table').text()).toContain('暂无报工数据')
  })

  it('marks a complete prior snapshot if either dataset fails during refresh', async () => {
    api.fetchWorkOrders.mockResolvedValueOnce({ items: [{ productName: '先前产品', goodQuantity: 3 }], total: 1 })
    const page = mountPage(ReportCenterPage)
    await flushPromises()
    api.fetchWorkOrders.mockResolvedValueOnce({ items: [{ productName: '半更新产品', goodQuantity: 9 }], total: 1 })
    api.fetchReports.mockRejectedValueOnce(new Error('刷新失败'))
    await page.get('button[aria-label="刷新生产报表"]').trigger('click')
    await flushPromises()
    expect(page.get('[role="alert"]').text()).toContain('显示上次成功加载的数据。')
    expect(page.text()).toContain('先前产品')
    expect(page.text()).not.toContain('半更新产品')
    await page.setProps({ token: 'another-factory' })
    expect(page.text()).not.toContain('先前产品')
    await flushPromises()
  })
})

describe('inbound print template storage recovery', () => {
  it('retains the edited template after failed storage, then reopens the successfully saved blank footer', async () => {
    const page = mountPage(InboundReceiptPage)
    await flushPromises()
    const opener = page.get<HTMLButtonElement>('button[aria-label="编辑打印模板"]')
    opener.element.focus()
    await opener.trigger('click')
    await flushPromises()
    let dialog = page.get('form[role="dialog"]')
    expect(dialog.get('h2').text()).toBe('编辑入库打印模板')
    expect(dialog.element.contains(document.activeElement)).toBe(true)
    await dialog.get('input[placeholder="请输入模板名称"]').setValue('保留草稿')
    await dialog.get('textarea').setValue('')
    const storage = vi.spyOn(Storage.prototype, 'setItem').mockImplementationOnce(() => { throw new DOMException('quota', 'QuotaExceededError') })
    await dialog.trigger('submit')
    expect(dialog.get('[role="alert"]').text()).toBe('入库打印模板保存失败，请重试。')
    expect(dialog.get<HTMLInputElement>('input[placeholder="请输入模板名称"]').element.value).toBe('保留草稿')
    expect(page.emitted('notice')).toBeUndefined()
    expect(localStorage.getItem('inbound_print_template')).toBeNull()
    await dialog.trigger('submit')
    await flushPromises()
    expect(storage).toHaveBeenCalledTimes(2)
    expect(page.find('form[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement).toBe(opener.element)
    expect(page.emitted('notice')).toEqual([['入库打印模板已保存']])

    await opener.trigger('click')
    dialog = page.get('form[role="dialog"]')
    expect(dialog.get<HTMLTextAreaElement>('textarea').element.value).toBe('')
    await dialog.get('input[placeholder="请输入模板名称"]').setValue('取消的草稿')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    await flushPromises()
    expect(document.activeElement).toBe(opener.element)
    await opener.trigger('click')
    expect(page.get<HTMLInputElement>('form[role="dialog"] input[placeholder="请输入模板名称"]').element.value).toBe('保留草稿')
  })
})
