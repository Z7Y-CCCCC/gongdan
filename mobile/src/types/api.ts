export type ReportStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface UserInfo {
  id: number
  username: string
  name: string
  role: 'ADMIN' | 'PRODUCTION'
  roleLabel: string
}

export interface MobileAuthResult {
  bindRequired: boolean
  openidTicket?: string
  token?: string
  user?: UserInfo
}

export interface WorkOrderTask {
  processName: string
  processCode?: string
  sequenceNo: number
  plannedQuantity: number
  completedQuantity?: number
  defectQuantity?: number
  approvedQuantity?: number
  lastReportTime?: string
  reportPermission?: string
  enabled?: boolean
}

export interface WorkOrderInfo {
  id: number
  code: string
  productId: number
  productCode: string
  productName: string
  productSpec?: string
  plannedQuantity: number
  goodQuantity: number
  defectQuantity: number
  status: string
  priority: string
  routeName?: string
  progressPercent: number
  planStart?: string
  planEnd?: string
  remark?: string
  tasks: WorkOrderTask[]
  createdAt?: string
}

export interface ListResponse<T> {
  items: T[]
  total: number
}

export interface ReportImage {
  id: number
  originalFileName: string
  accessPath: string
  fileSize: number
  contentType: string
  previewPath?: string
}

export interface WorkOrderReport {
  id: number
  workOrderId: number
  workOrderCode: string
  productName?: string
  productCode?: string
  productSpec?: string
  processSequenceNo: number
  processName: string
  reporterName: string
  reportQuantity: number
  goodQuantity: number
  defectQuantity: number
  defectItemName?: string
  reportDurationMinutes: number
  startTime?: string
  endTime?: string
  materialName?: string
  remark?: string
  status: ReportStatus
  approvalRemark?: string
  createdAt: string
  images: ReportImage[]
}
