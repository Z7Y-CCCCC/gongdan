import { describe, expect, it } from 'vitest'
import type { WorkOrderInfo, WorkOrderReportInfo } from '../../api'
import { buildWorkshopDashboard } from '../workshopDashboard'

function workOrder(overrides: Partial<WorkOrderInfo> = {}): WorkOrderInfo {
  return {
    id: 1,
    code: 'WO-001',
    productId: 1,
    productCode: 'P-001',
    productName: '装配件',
    plannedQuantity: 100,
    goodQuantity: 20,
    defectQuantity: 2,
    status: 'EXECUTING',
    priority: 'URGENT',
    progressPercent: 22,
    customerName: '示范客户',
    customValues: [],
    materials: [],
    tasks: [
      { processName: '下料', sequenceNo: 1, plannedQuantity: 100, completedQuantity: 20, defectQuantity: 2, planDeadline: '2026-07-21 12:00:00' },
      { processName: '检验', sequenceNo: 2, plannedQuantity: 100, completedQuantity: 100, defectQuantity: 0 }
    ],
    ...overrides
  }
}

function report(overrides: Partial<WorkOrderReportInfo> = {}): WorkOrderReportInfo {
  return {
    id: 1,
    workOrderId: 1,
    workOrderCode: 'WO-001',
    processSequenceNo: 1,
    processName: '下料',
    reporterId: 1,
    reporterName: '张师傅',
    reportQuantity: 12,
    goodQuantity: 10,
    defectQuantity: 2,
    reportDurationMinutes: 30,
    status: 'APPROVED',
    approvedAt: '2026-07-22 09:30:00',
    createdAt: '2026-07-22 09:00:00',
    updatedAt: '2026-07-22 09:30:00',
    images: [],
    ...overrides
  }
}

describe('buildWorkshopDashboard', () => {
  it('derives live order, task and process values without demo constants', () => {
    const result = buildWorkshopDashboard([workOrder()], [report()], new Date(2026, 6, 22, 10, 0, 0))

    expect(result.metrics).toMatchObject({
      inProcessOrderCount: 1,
      planCount: 100,
      inProcessTaskPlanCount: 100,
      inProcessTaskCount: 1,
      delayedOrders: 0,
      delayedTasks: 1,
      todayTasks: 1,
      todayOutput: 10,
      todayDefect: 2,
      defectRate: 16.67
    })
    expect(result.tableData[0].customer).toBe('示范客户')
    expect(result.tableData[0].steps).toEqual([
      { name: '下料', progress: 22 },
      { name: '检验', progress: 100 }
    ])
    expect(result.taskProgressData[0]).toMatchObject({ planned: 100, good: 20, defect: 2, remaining: 78 })
  })

  it('excludes closed orders and pending or rejected reports from production KPIs', () => {
    const completed = workOrder({ status: 'COMPLETED', updatedAt: '2026-07-22 08:00:00' })
    const pending = report({ id: 2, status: 'PENDING', goodQuantity: 999, defectQuantity: 999, approvedAt: undefined })
    const result = buildWorkshopDashboard([completed], [pending], new Date(2026, 6, 22, 10, 0, 0))

    expect(result.metrics.inProcessOrderCount).toBe(0)
    expect(result.metrics.inProcessTaskCount).toBe(0)
    expect(result.metrics.todayDone).toBe(1)
    expect(result.metrics.todayOutput).toBe(0)
    expect(result.metrics.defectRate).toBe(0)
  })

  it.each(['FINISHED', 'CLOSED', 'CANCELLED'])('does not treat the persisted terminal status %s as running', (status) => {
    const result = buildWorkshopDashboard([workOrder({ status })], [], new Date(2026, 6, 22))
    expect(result.metrics.inProcessOrderCount).toBe(0)
    expect(result.metrics.inProcessTaskCount).toBe(0)
  })
})
