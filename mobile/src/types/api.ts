export type ReportStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface UserInfo {
  id: number
  factoryId?: number
  username: string
  name: string
  role: 'ADMIN' | 'PRODUCTION'
  roleLabel: string
  departmentName?: string
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
  assignee?: string
  enabled?: boolean
}

export interface WorkOrderInfo {
  id: number
  code: string
  productId: number
  productCode: string
  productName: string
  productSpec?: string
  unitName?: string | null
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
  truncated?: boolean
}

export interface MobileDashboard {
  workOrderTotal: number
  totalPlanned: number
  totalGood: number
  activeOrderCount: number
  reportTotal: number
  pendingReportCount: number
  todayReportCount: number
  totalDefect: number
  todayGood: number
  todayDefect: number
  todayPending: number
  recentOrders: WorkOrderInfo[]
  rankings: Array<{ name: string; value: number }>
}

export interface FieldEventRecord {
  id: number
  moduleName: 'FIELD_EVENT'
  businessKey?: string
  title: string
  status: string
  payloadJson?: string
  createdById?: number
  createdByName?: string
  createdAt?: string
  updatedAt?: string
}

export interface FieldEventPayload {
  moduleName?: 'FIELD_EVENT'
  businessKey?: string
  title: string
  status?: string
  payloadJson?: string
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
  reporterId?: number
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
  approverName?: string
  approvedAt?: string
  approvalRemark?: string
  createdAt: string
  images: ReportImage[]
}
