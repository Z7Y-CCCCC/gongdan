import type { WorkOrderInfo, WorkOrderReportInfo, WorkOrderTaskPayload } from '../api'

export type DashboardProcessStep = {
  name: string
  progress: number
}

export type DashboardWorkOrderRow = {
  code: string
  product: string
  qty: number
  customer: string
  urgent: boolean
  progress: number
  steps: DashboardProcessStep[]
}

export type DashboardTaskProgressRow = {
  process: string
  taskCount: number
  planned: number
  good: number
  defect: number
  remaining: number
}

export type WorkshopDashboardMetrics = {
  inProcessOrderCount: number
  planCount: number
  inProcessTaskPlanCount: number
  inProcessTaskCount: number
  delayedOrders: number
  delayedTasks: number
  todayDone: number
  todayTasks: number
  todayOutput: number
  todayDefect: number
  defectRate: number
}

function quantity(value: number | undefined) {
  return Math.max(0, Number(value || 0))
}

function localDateKey(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function timestampDateKey(value?: string) {
  return value ? value.slice(0, 10) : ''
}

function isCompletedStatus(status?: string) {
  return ['COMPLETED', '已完成'].includes(status || '')
}

function isClosedStatus(status?: string) {
  return isCompletedStatus(status) || ['CANCELED', 'CANCELLED', '已取消'].includes(status || '')
}

function taskCompletedQuantity(task: WorkOrderTaskPayload) {
  return quantity(task.completedQuantity) + quantity(task.defectQuantity)
}

function taskProgress(task: WorkOrderTaskPayload) {
  const planned = quantity(task.plannedQuantity)
  if (planned === 0) return 0
  return Math.min(100, Math.round(taskCompletedQuantity(task) / planned * 100))
}

function workOrderProgress(workOrder: WorkOrderInfo) {
  if (Number.isFinite(workOrder.progressPercent)) {
    return Math.max(0, Math.min(100, Math.round(workOrder.progressPercent)))
  }
  const planned = quantity(workOrder.plannedQuantity)
  if (planned === 0) return 0
  return Math.min(100, Math.round((quantity(workOrder.goodQuantity) + quantity(workOrder.defectQuantity)) / planned * 100))
}

export function buildWorkshopDashboard(
  workOrders: WorkOrderInfo[],
  reports: WorkOrderReportInfo[],
  now = new Date(),
  customerNamesByOrderCode = new Map<string, string>()
) {
  const today = localDateKey(now)
  const activeOrders = workOrders.filter(order => !isClosedStatus(order.status))
  const approvedReports = reports.filter(report => report.status === 'APPROVED')
  const todayReports = approvedReports.filter(report => timestampDateKey(report.approvedAt || report.createdAt) === today)

  const tableData: DashboardWorkOrderRow[] = workOrders.map(order => ({
    code: order.code,
    product: order.productName || '未知产品',
    qty: quantity(order.plannedQuantity),
    customer: (order.relatedOrderCode ? customerNamesByOrderCode.get(order.relatedOrderCode) : undefined) || order.customerName || '-',
    urgent: order.priority === 'URGENT',
    progress: workOrderProgress(order),
    steps: (order.tasks || [])
      .filter(task => task.enabled !== false)
      .sort((a, b) => a.sequenceNo - b.sequenceNo)
      .map(task => ({ name: task.processName || '未命名工序', progress: taskProgress(task) }))
  }))

  const processMap = new Map<string, DashboardTaskProgressRow>()
  let inProcessTaskCount = 0
  let inProcessTaskPlanCount = 0
  let delayedTasks = 0

  activeOrders.forEach(order => {
    ;(order.tasks || []).filter(task => task.enabled !== false).forEach(task => {
      const planned = quantity(task.plannedQuantity)
      const good = quantity(task.completedQuantity)
      const defect = quantity(task.defectQuantity)
      const remaining = Math.max(0, planned - good - defect)
      const processName = task.processName || '未命名工序'
      const row = processMap.get(processName) || {
        process: processName,
        taskCount: 0,
        planned: 0,
        good: 0,
        defect: 0,
        remaining: 0
      }

      row.taskCount += 1
      row.planned += planned
      row.good += good
      row.defect += defect
      row.remaining += remaining
      processMap.set(processName, row)

      if (remaining > 0) {
        inProcessTaskCount += 1
        inProcessTaskPlanCount += planned
        if (task.planDeadline && new Date(task.planDeadline).getTime() < now.getTime()) delayedTasks += 1
      }
    })
  })

  const totalGood = approvedReports.reduce((sum, report) => sum + quantity(report.goodQuantity), 0)
  const totalDefect = approvedReports.reduce((sum, report) => sum + quantity(report.defectQuantity), 0)
  const todayTaskKeys = new Set(todayReports.map(report => `${report.workOrderId}:${report.processSequenceNo}`))

  const metrics: WorkshopDashboardMetrics = {
    inProcessOrderCount: activeOrders.length,
    planCount: activeOrders.reduce((sum, order) => sum + quantity(order.plannedQuantity), 0),
    inProcessTaskPlanCount,
    inProcessTaskCount,
    delayedOrders: activeOrders.filter(order => Boolean(order.planEnd) && new Date(order.planEnd as string).getTime() < now.getTime()).length,
    delayedTasks,
    todayDone: workOrders.filter(order => isCompletedStatus(order.status) && timestampDateKey(order.updatedAt) === today).length,
    todayTasks: todayTaskKeys.size,
    todayOutput: todayReports.reduce((sum, report) => sum + quantity(report.goodQuantity), 0),
    todayDefect: todayReports.reduce((sum, report) => sum + quantity(report.defectQuantity), 0),
    defectRate: totalGood + totalDefect > 0 ? Number((totalDefect / (totalGood + totalDefect) * 100).toFixed(2)) : 0
  }

  return {
    metrics,
    tableData,
    taskProgressData: Array.from(processMap.values())
  }
}
