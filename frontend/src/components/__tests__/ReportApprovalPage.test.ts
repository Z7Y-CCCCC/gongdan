import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ReportApprovalPage from '../ReportApprovalPage.vue'

const { fetchReports, approveReport, rejectReport, fetchReportImageBlob } = vi.hoisted(() => ({
  fetchReports: vi.fn(), approveReport: vi.fn(), rejectReport: vi.fn(), fetchReportImageBlob: vi.fn()
}))
vi.mock('../../api', async (load) => ({
  ...await load<typeof import('../../api')>(),
  fetchReports, approveReport, rejectReport, fetchReportImageBlob
}))

const pending = { id: 1, workOrderCode: 'WO-1', processName: '装配', status: 'PENDING', images: [] }
const mountPage = () => mount(ReportApprovalPage, {
  props: { token: 'test-token' },
  global: { stubs: { TableConfigButton: true, UiIcon: true } }
})

beforeEach(() => {
  vi.resetAllMocks()
  fetchReports.mockResolvedValue({ items: [pending], total: 1 })
})
afterEach(() => vi.restoreAllMocks())

describe('approval feedback', () => {
  it('requires a reason, prevents a second write, and removes the resolved item from pending', async () => {
    let resolveDecision!: (value: unknown) => void
    rejectReport.mockImplementation(() => new Promise(resolve => { resolveDecision = resolve }))
    const page = mountPage()
    await flushPromises()
    await page.get('.action-link.danger').trigger('click')
    const confirm = page.get('.batch-modal .primary-btn')
    expect(confirm.attributes('disabled')).toBeDefined()
    await page.get('textarea').setValue('   ')
    expect(confirm.attributes('disabled')).toBeDefined()
    await page.get('textarea').setValue('  请核对数量  ')
    await confirm.trigger('click')
    await confirm.trigger('click')
    expect(rejectReport).toHaveBeenCalledExactlyOnceWith('test-token', 1, '请核对数量')
    const rejected = { ...pending, status: 'REJECTED', approvalRemark: '请核对数量' }
    fetchReports.mockResolvedValue({ items: [rejected], total: 1 })
    resolveDecision(rejected)
    await flushPromises()
    expect(page.find('.batch-modal').exists()).toBe(false)
    expect(page.get('.report-detail-modal').text()).toContain('请核对数量')
    expect(page.find('.action-link.danger').exists()).toBe(false)
    page.unmount()
  })

  it('does not wait for an attachment to refresh the result or render an attachment after closing', async () => {
    let resolveImage!: (value: Blob) => void
    fetchReportImageBlob.mockImplementation(() => new Promise(resolve => { resolveImage = resolve }))
    approveReport.mockResolvedValue({ ...pending, status: 'APPROVED', images: [{ id: 8 }] })
    const createUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:picture')
    const page = mountPage()
    await flushPromises()
    fetchReports.mockResolvedValue({ items: [], total: 0 })
    await page.get('.action-cell button:nth-child(2)').trigger('click')
    await flushPromises()
    expect(fetchReports).toHaveBeenCalledTimes(2)
    await page.get('.report-detail-modal .close-btn').trigger('click')
    resolveImage(new Blob())
    await flushPromises()
    expect(createUrl).not.toHaveBeenCalled()
    expect(page.find('.report-detail-modal').exists()).toBe(false)
    page.unmount()
  })
})
