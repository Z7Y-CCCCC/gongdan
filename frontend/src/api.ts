export type Role = 'ADMIN' | 'PRODUCTION'

export interface UserInfo {
  id: number
  username: string
  phone?: string
  name: string
  role: Role
  roleLabel: string
  departmentId?: number
  departmentName?: string
  virtualUser: boolean
  enabled: boolean
  mobileBound?: boolean
  createdAt: string
}

export interface DepartmentInfo {
  id: number
  code: string
  name: string
  userNames?: string
  createdBy: string
  createdAt: string
}

export interface DepartmentListResponse {
  items: DepartmentInfo[]
  total: number
}

export interface CreateDepartmentPayload {
  code: string
  name: string
  userIds?: number[]
}

export interface UpdateDepartmentPayload {
  code: string
  name: string
  userIds?: number[]
}

export interface LoginPayload {
  factoryCode: string
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  user: UserInfo
  isSuperAdmin: boolean
}

export interface CreateUserPayload {
  username: string
  phone?: string
  name: string
  password: string
  role: Role
  departmentId?: number
  virtualUser: boolean
}

export interface UpdateUserPayload {
  username: string
  phone?: string
  name: string
  role: Role
  departmentId?: number
  virtualUser: boolean
}

export interface ResetPasswordResponse {
  message: string
  temporaryPassword: string
}

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'

export class ApiError extends Error {
  status: number
  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    }
  })

  if (!response.ok) {
    let message = '请求失败，请稍后再试'
    try {
      const body = await response.json()
      message = body.message || message
    } catch {
      message = response.statusText || message
    }
    throw new ApiError(message, response.status)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  if (!text) {
    return undefined as T
  }

  return JSON.parse(text) as T
}

export function login(payload: LoginPayload) {
  return request<LoginResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function fetchUsers(token: string) {
  return request<UserInfo[]>('/users', {
    headers: {
      Authorization: `Bearer ${token}`
    }
  })
}

export function createUser(token: string, payload: CreateUserPayload) {
  return request<UserInfo>('/users', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(payload)
  })
}

export function updateUser(token: string, id: number, payload: UpdateUserPayload) {
  return request<UserInfo>(`/users/${id}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(payload)
  })
}

export function resetUserPassword(token: string, id: number, password?: string) {
  const options: RequestInit = {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${token}`
    }
  }
  if (password) {
    options.body = JSON.stringify({ password })
  }
  return request<ResetPasswordResponse>(`/users/${id}/password/reset`, options)
}

export function unbindUserPhone(token: string, id: number) {
  return request<UserInfo>(`/users/${id}/phone/unbind`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${token}`
    }
  })
}

export function updateUserStatus(token: string, id: number, enabled: boolean) {
  return request<UserInfo>(`/users/${id}/status`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify({ enabled })
  })
}

export function updateUserPermission(token: string, id: number, role: Role) {
  return request<UserInfo>(`/users/${id}/permission`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify({ role })
  })
}

export function exportUsersUrl(token: string): string {
  return `${API_BASE}/users/export?_t=${Date.now()}`
}

export async function exportUsersCsv(token: string): Promise<void> {
  const response = await fetch(`${API_BASE}/users/export`, {
    headers: {
      Authorization: `Bearer ${token}`
    }
  })
  if (!response.ok) {
    let message = '导出失败'
    try {
      const body = await response.json()
      message = body.message || message
    } catch {
      message = response.statusText || message
    }
    throw new Error(message)
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `用户列表_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

export interface ImportResult {
  total: number
  success: number
  fail: number
  errors: string[]
}

export async function importUsersCsv(token: string, file: File): Promise<ImportResult> {
  const formData = new FormData()
  formData.append('file', file)

  const response = await fetch(`${API_BASE}/users/import`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: formData
  })

  if (!response.ok) {
    let message = '导入失败'
    try {
      const body = await response.json()
      message = body.message || message
    } catch {
      message = response.statusText || message
    }
    throw new Error(message)
  }

  return response.json()
}

export async function fetchDepartments(token: string, params: { code?: string; name?: string; page?: number; size?: number }) {
  const query = new URLSearchParams()
  if (params.code) query.append('code', params.code)
  if (params.name) query.append('name', params.name)
  if (params.page) query.append('page', String(params.page))
  if (params.size) query.append('size', String(params.size))

  return request<DepartmentListResponse>(`/departments?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function createDepartment(token: string, payload: CreateDepartmentPayload) {
  return request<DepartmentInfo>('/departments', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function updateDepartment(token: string, id: number, payload: UpdateDepartmentPayload) {
  return request<DepartmentInfo>(`/departments/${id}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function deleteDepartment(token: string, id: number) {
  return request<void>(`/departments/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  })
}

// ---- 单位 ----
export interface UnitInfo {
  id: number
  name: string
  remark?: string
  createdAt: string
}
export interface UnitListResponse {
  items: UnitInfo[]
  total: number
}
export interface UnitPayload {
  name: string
  remark?: string
}

export async function fetchUnits(token: string, params: { name?: string; page?: number; size?: number } = {}) {
  const query = new URLSearchParams()
  if (params.name) query.append('name', params.name)
  if (params.page) query.append('page', String(params.page))
  if (params.size) query.append('size', String(params.size))
  return request<UnitListResponse>(`/units?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function createUnit(token: string, payload: UnitPayload) {
  return request<UnitInfo>('/units', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function updateUnit(token: string, id: number, payload: UnitPayload) {
  return request<UnitInfo>(`/units/${id}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function deleteUnit(token: string, id: number) {
  return request<void>(`/units/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  })
}

// ---- 不良品项 ----
export interface DefectInfo {
  id: number
  code: string
  name: string
  createdAt: string
}
export interface DefectListResponse {
  items: DefectInfo[]
  total: number
}
export interface DefectPayload {
  code?: string
  name: string
}

export async function fetchDefects(token: string, params: { code?: string; name?: string; page?: number; size?: number } = {}) {
  const query = new URLSearchParams()
  if (params.code) query.append('code', params.code)
  if (params.name) query.append('name', params.name)
  if (params.page) query.append('page', String(params.page))
  if (params.size) query.append('size', String(params.size))
  return request<DefectListResponse>(`/defect-items?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function createDefect(token: string, payload: DefectPayload) {
  return request<DefectInfo>('/defect-items', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function updateDefect(token: string, id: number, payload: DefectPayload) {
  return request<DefectInfo>(`/defect-items/${id}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function deleteDefect(token: string, id: number) {
  return request<void>(`/defect-items/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  })
}

// ===== 工序 API =====
export interface CollectDataItem { type: string; name: string }
export interface ProcessPayload {
  code?: string
  name: string
  reportPermission?: string[]
  reportRatio?: number
  defectItemIds?: number[]
  taskPunchIn?: boolean
  goodQuantity?: string
  machineName?: string
  feedCount?: string
  correspondingMachine?: string
  requirements?: string
  collectData?: CollectDataItem[]
}
export interface ProcessInfo {
  id: number
  code: string
  name: string
  reportPermission: string[]
  reportRatio: number
  defectItemIds: number[]
  taskPunchIn: boolean
  goodQuantity: string
  machineName: string
  feedCount: string
  correspondingMachine: string
  requirements: string
  collectData: CollectDataItem[]
  creator: string
  createdAt: string
}
export interface ProcessListResponse { items: ProcessInfo[]; total: number }

export async function fetchProcesses(token: string, params: { code?: string; name?: string; page?: number; size?: number } = {}) {
  const query = new URLSearchParams()
  if (params.code) query.append('code', params.code)
  if (params.name) query.append('name', params.name)
  if (params.page) query.append('page', String(params.page))
  if (params.size) query.append('size', String(params.size))
  return request<ProcessListResponse>(`/processes?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function createProcess(token: string, payload: ProcessPayload) {
  return request<ProcessInfo>('/processes', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function updateProcess(token: string, id: number, payload: ProcessPayload) {
  return request<ProcessInfo>(`/processes/${id}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function deleteProcess(token: string, id: number) {
  return request<void>(`/processes/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  })
}

// ===== 工艺路线 API =====
export interface RouteStepPayload {
  type: 'process' | 'route'
  processId: number | null
  reportRatio: number
  remark: string
}
export interface ProcessRoutePayload {
  code?: string
  name: string
  steps: RouteStepPayload[]
}
export interface RouteStepInfo {
  type: string
  processId: number | null
  reportRatio: number
  remark: string
}
export interface ProcessRouteInfo {
  id: number
  code: string
  name: string
  steps: RouteStepInfo[]
  creator: string
  createdAt: string
}
export interface ProcessRouteListResponse { items: ProcessRouteInfo[]; total: number }

export async function fetchProcessRoutes(token: string, params: { code?: string; name?: string; page?: number; size?: number } = {}) {
  const query = new URLSearchParams()
  if (params.code) query.append('code', params.code)
  if (params.name) query.append('name', params.name)
  if (params.page) query.append('page', String(params.page))
  if (params.size) query.append('size', String(params.size))
  return request<ProcessRouteListResponse>(`/process-routes?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function createProcessRoute(token: string, payload: ProcessRoutePayload) {
  return request<ProcessRouteInfo>('/process-routes', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function updateProcessRoute(token: string, id: number, payload: ProcessRoutePayload) {
  return request<ProcessRouteInfo>(`/process-routes/${id}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function deleteProcessRoute(token: string, id: number) {
  return request<void>(`/process-routes/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  })
}

// ===== 产品定义 API =====
export interface ProductPayload {
  code?: string
  name: string
  unitId: number
  spec?: string
  attribute?: string
  routeId?: number | null
  supplierName?: string
  maxStock?: string
  minStock?: string
  safetyStock?: string
  inventoryQty?: number
  costPrice?: string
  salePrice?: string
  keyPart?: boolean
  manufactureMode?: string
  quantity?: string
  fixedWeight?: string
  customerName?: string
  productType?: string
}

export interface ProductInfo {
  id: number
  code: string
  name: string
  unitId: number
  unitName: string
  spec: string
  attribute: string
  routeId?: number | null
  routeName?: string
  supplierName?: string
  maxStock?: string
  minStock?: string
  safetyStock?: string
  inventoryQty: number
  costPrice?: string
  salePrice?: string
  keyPart: boolean
  manufactureMode?: string
  quantity?: string
  fixedWeight?: string
  customerName?: string
  productType?: string
  creator?: string
  createdAt: string
}

export interface ProductListResponse {
  items: ProductInfo[]
  total: number
}

export interface ProductOperationLogInfo {
  id: number
  actionType: string
  actionLabel: string
  operatorName: string
  productCode?: string
  productName?: string
  detail?: string
  createdAt: string
  message: string
}

export interface ProductOperationLogListResponse {
  items: ProductOperationLogInfo[]
  total: number
}

export interface ProductOperationLogPayload {
  actionType: string
  productCode?: string
  productName?: string
  detail?: string
}

export async function fetchProducts(
  token: string,
  params: { code?: string; name?: string; spec?: string; page?: number; size?: number } = {}
) {
  const query = new URLSearchParams()
  if (params.code) query.append('code', params.code)
  if (params.name) query.append('name', params.name)
  if (params.spec) query.append('spec', params.spec)
  if (params.page) query.append('page', String(params.page))
  if (params.size) query.append('size', String(params.size))
  return request<ProductListResponse>(`/products?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function createProduct(token: string, payload: ProductPayload) {
  return request<ProductInfo>('/products', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function fetchProductOperationLogs(
  token: string,
  params: { page?: number; size?: number } = {}
) {
  const query = new URLSearchParams()
  if (params.page) query.append('page', String(params.page))
  if (params.size) query.append('size', String(params.size))
  return request<ProductOperationLogListResponse>(`/products/operation-logs?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function createProductOperationLog(token: string, payload: ProductOperationLogPayload) {
  return request<void>('/products/operation-logs', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function updateProduct(token: string, id: number, payload: ProductPayload) {
  return request<ProductInfo>(`/products/${id}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function deleteProduct(token: string, id: number) {
  return request<void>(`/products/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  })
}

// ===== 自定义字段 API =====
export interface CustomFieldOption {
  label: string
  value: string
  color?: string | null
  defaultSelected?: boolean
}

export interface CustomFieldPayload {
  scope?: string
  name: string
  fieldType: string
  options?: CustomFieldOption[]
  defaultValues?: string[]
  placeholder?: string
  displayMode?: string
  required?: boolean
  allowUserOptions?: boolean
  helperText?: string
  description?: string
  sortOrder?: number
}

export interface CustomFieldInfo {
  id: number
  scope: string
  name: string
  fieldType: string
  options: CustomFieldOption[]
  defaultValues: string[]
  placeholder?: string
  displayMode?: string
  required: boolean
  allowUserOptions: boolean
  helperText?: string
  description?: string
  sortOrder: number
  creator?: string
  createdAt?: string
  updatedAt?: string
}

export interface CustomFieldListResponse {
  items: CustomFieldInfo[]
  total: number
}

export async function fetchCustomFields(token: string, scope = 'workorder') {
  const query = new URLSearchParams()
  query.append('scope', scope)
  return request<CustomFieldListResponse>(`/custom-fields?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function createCustomField(token: string, payload: CustomFieldPayload) {
  return request<CustomFieldInfo>('/custom-fields', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function updateCustomField(token: string, id: number, payload: CustomFieldPayload) {
  return request<CustomFieldInfo>(`/custom-fields/${id}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function deleteCustomField(token: string, id: number) {
  return request<void>(`/custom-fields/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  })
}

// ===== 工单 API =====
export interface WorkOrderCustomValue {
  fieldId?: number
  fieldName: string
  fieldType: string
  value: string | string[] | number | null
}

export interface WorkOrderTaskPayload {
  processName: string
  processCode?: string
  sequenceNo: number
  plannedQuantity: number
  completedQuantity?: number
  defectQuantity?: number
  approvedQuantity?: number
  unapprovedQuantity?: number
  lastReportTime?: string
  planDeadline?: string
  reportPermission?: string
  assignee?: string
  enabled?: boolean
}

export interface WorkOrderMaterialPayload {
  materialCode?: string
  materialName: string
  materialSpec?: string
  quantity: number
}

export interface WorkOrderPayload {
  code?: string
  productId: number
  plannedQuantity: number
  categoryId?: number
  status?: string
  priority?: string
  planStart?: string
  planEnd?: string
  relatedOrderCode?: string
  remark?: string
  goodQuantity?: number
  defectQuantity?: number
  defectRate?: string
  customValues?: WorkOrderCustomValue[]
  tasks?: WorkOrderTaskPayload[]
  materials?: WorkOrderMaterialPayload[]
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
  defectRate?: string
  categoryId?: number
  categoryName?: string
  status: string
  priority: string
  routeName?: string
  progressPercent: number
  planStart?: string
  planEnd?: string
  relatedOrderCode?: string
  remark?: string
  customValues: WorkOrderCustomValue[]
  tasks: WorkOrderTaskPayload[]
  materials: WorkOrderMaterialPayload[]
  creator?: string
  createdAt?: string
  updatedAt?: string
  planCount?: number
  customerName?: string
}

export interface WorkOrderListResponse {
  items: WorkOrderInfo[]
  total: number
}

export async function fetchWorkOrders(
  token: string,
  params: { code?: string; productName?: string; page?: number; size?: number } = {}
) {
  const query = new URLSearchParams()
  if (params.code) query.append('code', params.code)
  if (params.productName) query.append('productName', params.productName)
  if (params.page) query.append('page', String(params.page))
  if (params.size) query.append('size', String(params.size))
  return request<WorkOrderListResponse>(`/work-orders?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function createWorkOrder(token: string, payload: WorkOrderPayload) {
  return request<WorkOrderInfo>('/work-orders', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function updateWorkOrder(token: string, id: number, payload: WorkOrderPayload) {
  return request<WorkOrderInfo>(`/work-orders/${id}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function deleteWorkOrder(token: string, id: number) {
  return request<void>(`/work-orders/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  })
}

// ===== 工单分类 API =====
export interface WorkOrderCategoryInfo {
  id: number
  name: string
  creatorId?: number
  creatorName?: string
  createdAt: string
}

export async function fetchWorkOrderCategories(token: string) {
  return request<WorkOrderCategoryInfo[]>('/work-orders/categories', {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function createWorkOrderCategory(token: string, payload: { name: string }) {
  return request<WorkOrderCategoryInfo>('/work-orders/categories', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function deleteWorkOrderCategory(token: string, id: number) {
  return request<void>(`/work-orders/categories/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  })
}

// ===== 用户偏好 API =====
export interface UserPreferenceInfo {
  key: string
  value: string
  updatedAt: string
}

export async function fetchUserPreference(token: string, key: string) {
  return request<UserPreferenceInfo>(`/user/preferences/${key}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function setUserPreference(token: string, key: string, value: string) {
  return request<UserPreferenceInfo>(`/user/preferences/${key}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({ value })
  })
}

// ===== 报工审批 API =====
export type ReportStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface WorkOrderReportImageInfo {
  id: number
  originalFileName: string
  accessPath: string
  fileSize: number
  contentType: string
  createdAt: string
}

export interface WorkOrderReportInfo {
  id: number
  workOrderId: number
  workOrderCode: string
  productName?: string
  productCode?: string
  productSpec?: string
  processSequenceNo: number
  processName: string
  reporterId: number
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
  approverId?: number
  approverName?: string
  approvedAt?: string
  approvalRemark?: string
  createdAt: string
  updatedAt: string
  images: WorkOrderReportImageInfo[]
}

export interface WorkOrderReportListResponse {
  items: WorkOrderReportInfo[]
  total: number
}

export async function fetchReports(
  token: string,
  params: { status?: ReportStatus | ''; workOrderCode?: string; reporterName?: string; period?: string; page?: number; size?: number } = {}
) {
  const query = new URLSearchParams()
  if (params.status) query.append('status', params.status)
  if (params.workOrderCode) query.append('workOrderCode', params.workOrderCode)
  if (params.reporterName) query.append('reporterName', params.reporterName)
  if (params.period) query.append('period', params.period)
  if (params.page) query.append('page', String(params.page))
  if (params.size) query.append('size', String(params.size))
  return request<WorkOrderReportListResponse>(`/reports?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function fetchReport(token: string, id: number) {
  return request<WorkOrderReportInfo>(`/reports/${id}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function approveReport(token: string, id: number, remark = '') {
  return request<WorkOrderReportInfo>(`/reports/${id}/approve`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({ remark })
  })
}

export async function rejectReport(token: string, id: number, remark = '') {
  return request<WorkOrderReportInfo>(`/reports/${id}/reject`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({ remark })
  })
}

// ===== 手机端 API 共用类型 =====
export interface MobileAuthResult {
  bindRequired: boolean
  openidTicket?: string
  token?: string
  user?: UserInfo
}

export async function mobileWechatLogin(payload: { code?: string; mockOpenid?: string }) {
  return request<MobileAuthResult>('/mobile/auth/wechat-login', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export async function mobileBind(payload: { openidTicket: string; factoryCode: string; username: string; password: string }) {
  return request<MobileAuthResult>('/mobile/auth/bind', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

// ===== 手机号登录 API =====
export interface PhonePasswordLoginPayload {
  phone: string
  password: string
}

export interface PhoneSmsLoginPayload {
  phone: string
  code: string
}

export interface SmsSendPayload {
  phone: string
}

export interface SelectFactoryPayload {
  phone: string
  factoryId: number
  loginType: 'password' | 'sms'
  credential: string
}

export interface FactoryOptionItem {
  factoryId: number
  factoryCode: string
  factoryName: string
}

export interface PhoneLoginResult {
  selectRequired: boolean
  bindingRequired?: boolean
  phone?: string
  token?: string
  user?: UserInfo
  factories?: FactoryOptionItem[]
}

export interface BindFactoryPayload {
  phone: string
  factoryCode: string
  authorizationCode: string
}

export interface BindFactoryResponse {
  token: string
  user: UserInfo
  defaultPassword: string
}

export function phonePasswordLogin(payload: PhonePasswordLoginPayload) {
  return request<PhoneLoginResult>('/auth/phone/password-login', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function phoneSmsLogin(payload: PhoneSmsLoginPayload) {
  return request<PhoneLoginResult>('/auth/phone/sms-login', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function sendSmsCode(payload: SmsSendPayload) {
  return request<void>('/auth/phone/sms/send', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function selectFactory(payload: SelectFactoryPayload) {
  return request<PhoneLoginResult>('/auth/phone/select-factory', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function bindFactory(payload: BindFactoryPayload) {
  return request<BindFactoryResponse>('/auth/phone/bind-factory', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}


// ===== 主页仪表盘 API =====
export type HomeRange = 'today' | 'yesterday' | 'this_month'
export type WorkOrderStatusTab =
  | 'not_started' | 'in_progress' | 'due_within_3_days'
  | 'overdue_unfinished' | 'overdue_finished_this_month'
  | 'finished_today' | 'finished_this_month'

export interface ProcessCardItem {
  processId: number
  processName: string
  taskCount: number
  plannedQuantity: number
  goodQuantity: number
  defectQuantity: number
}
export interface ProcessCardListResponse { items: ProcessCardItem[] }

export interface WorkOrderProgressItem {
  id: number
  code: string
  productCode: string
  productName: string
  productSpec: string
  status: string
  statusLabel: string
}
export interface WorkOrderProgressListResponse { items: WorkOrderProgressItem[] }

export interface PerformanceResponse {
  plannedQuantity: number
  completedQuantity: number
  workOrderCount: number
  defectRatePercent: number
  averageCycleDays: number
  overdueUnfinishedCount: number
}

export interface RecentReportItem {
  id: number
  workOrderId: number
  workOrderCode: string
  reporterName: string
  processName: string
  goodQuantity: number
  defectQuantity: number
  createdAt: string
}
export interface RecentReportListResponse { items: RecentReportItem[] }

export function fetchHomeProcessCards(token: string, range: HomeRange, includeNotStarted: boolean) {
  const query = new URLSearchParams()
  query.append('range', range)
  query.append('includeNotStarted', String(includeNotStarted))
  return request<ProcessCardListResponse>(`/home-dashboard/process-cards?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function fetchHomeWorkOrders(token: string, tab: WorkOrderStatusTab) {
  const query = new URLSearchParams()
  query.append('status', tab)
  return request<WorkOrderProgressListResponse>(`/home-dashboard/work-orders?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function fetchHomePerformance(token: string, range: HomeRange) {
  const query = new URLSearchParams()
  query.append('range', range)
  return request<PerformanceResponse>(`/home-dashboard/performance?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function fetchHomeRecentReports(token: string, params: { since?: string; limit?: number } = {}) {
  const query = new URLSearchParams()
  if (params.since) query.append('since', params.since)
  if (params.limit) query.append('limit', String(params.limit))
  return request<RecentReportListResponse>(`/home-dashboard/reports/recent?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

// ===== 入库单 API =====
export interface InboundReceiptItemInfo {
  id: number
  productId: number
  productCode: string
  productName: string
  productSpec?: string
  productAttribute?: string
  unitName?: string
  minStock?: string
  maxStock?: string
  safetyStock?: string
  inventoryQty: number
  quantity: number
  customValues?: WorkOrderCustomValue[]
}

export interface InboundReceiptInfo {
  id: number
  code: string
  inboundType: string
  inboundTime: string
  remark?: string
  creator?: string
  createdAt: string
  items: InboundReceiptItemInfo[]
  customValues?: WorkOrderCustomValue[]
}

export interface InboundReceiptListResponse {
  items: InboundReceiptInfo[]
  total: number
}

export interface InboundReceiptItemPayload {
  productId: number
  quantity: number
  customValues?: WorkOrderCustomValue[]
}

export interface InboundReceiptPayload {
  code?: string
  inboundType: string
  inboundTime: string
  remark?: string
  items: InboundReceiptItemPayload[]
  customValues?: WorkOrderCustomValue[]
}

export async function fetchInboundReceipts(
  token: string,
  params: { code?: string; inboundType?: string; startTime?: string; endTime?: string; page?: number; size?: number } = {}
) {
  const query = new URLSearchParams()
  if (params.code) query.append('code', params.code)
  if (params.inboundType) query.append('inboundType', params.inboundType)
  if (params.startTime) query.append('startTime', params.startTime)
  if (params.endTime) query.append('endTime', params.endTime)
  if (params.page) query.append('page', String(params.page))
  if (params.size) query.append('size', String(params.size))
  return request<InboundReceiptListResponse>(`/inbound-receipts?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function fetchInboundReceipt(token: string, id: number) {
  return request<InboundReceiptInfo>(`/inbound-receipts/${id}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function createInboundReceipt(token: string, payload: InboundReceiptPayload) {
  return request<InboundReceiptInfo>('/inbound-receipts', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function deleteInboundReceipt(token: string, id: number) {
  return request<void>(`/inbound-receipts/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  })
}

// ===== 出库单 API =====
export interface OutboundReceiptItemInfo {
  id: number
  productId: number
  productCode: string
  productName: string
  productSpec?: string
  productAttribute?: string
  unitName?: string
  minStock?: string
  maxStock?: string
  safetyStock?: string
  inventoryQty: number
  quantity: number
  customValues?: WorkOrderCustomValue[]
}

export interface OutboundReceiptInfo {
  id: number
  code: string
  outboundType: string
  outboundTime: string
  remark?: string
  creator?: string
  createdAt: string
  items: OutboundReceiptItemInfo[]
  customValues?: WorkOrderCustomValue[]
}

export interface OutboundReceiptListResponse {
  items: OutboundReceiptInfo[]
  total: number
}

export interface OutboundReceiptItemPayload {
  productId: number
  quantity: number
  customValues?: WorkOrderCustomValue[]
}

export interface OutboundReceiptPayload {
  code?: string
  outboundType: string
  outboundTime: string
  remark?: string
  items: OutboundReceiptItemPayload[]
  customValues?: WorkOrderCustomValue[]
}

export async function fetchOutboundReceipts(
  token: string,
  params: { code?: string; outboundType?: string; startTime?: string; endTime?: string; page?: number; size?: number } = {}
) {
  const query = new URLSearchParams()
  if (params.code) query.append('code', params.code)
  if (params.outboundType) query.append('outboundType', params.outboundType)
  if (params.startTime) query.append('startTime', params.startTime)
  if (params.endTime) query.append('endTime', params.endTime)
  if (params.page) query.append('page', String(params.page))
  if (params.size) query.append('size', String(params.size))
  return request<OutboundReceiptListResponse>(`/outbound-receipts?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function fetchOutboundReceipt(token: string, id: number) {
  return request<OutboundReceiptInfo>(`/outbound-receipts/${id}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function createOutboundReceipt(token: string, payload: OutboundReceiptPayload) {
  return request<OutboundReceiptInfo>('/outbound-receipts', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export async function deleteOutboundReceipt(token: string, id: number) {
  return request<void>(`/outbound-receipts/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  })
}

// ===== 库存收发明细 API =====
export interface InventoryDetailInfo {
  sourceType: string
  receiptCode: string
  receiptType: string
  operationTime: string
  remark?: string
  productCode: string
  productName: string
  productSpec?: string
  productAttribute?: string
  unitName?: string
  receiptQty: number
  changeQty: number
}

export interface InventoryDetailListResponse {
  items: InventoryDetailInfo[]
  total: number
}

export async function fetchInventoryDetails(
  token: string,
  params: {
    receiptCode?: string
    receiptType?: string
    sourceType?: string
    remark?: string
    productCode?: string
    productName?: string
    productSpec?: string
    productAttribute?: string
    startTime?: string
    endTime?: string
    page?: number
    size?: number
  } = {}
) {
  const query = new URLSearchParams()
  if (params.receiptCode) query.append('receiptCode', params.receiptCode)
  if (params.receiptType) query.append('receiptType', params.receiptType)
  if (params.sourceType) query.append('sourceType', params.sourceType)
  if (params.remark) query.append('remark', params.remark)
  if (params.productCode) query.append('productCode', params.productCode)
  if (params.productName) query.append('productName', params.productName)
  if (params.productSpec) query.append('productSpec', params.productSpec)
  if (params.productAttribute) query.append('productAttribute', params.productAttribute)
  if (params.startTime) query.append('startTime', params.startTime)
  if (params.endTime) query.append('endTime', params.endTime)
  if (params.page) query.append('page', String(params.page))
  if (params.size) query.append('size', String(params.size))
  return request<InventoryDetailListResponse>(`/inventory-details?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

// ===== 超级管理员管理 API =====

// --- 工厂管理 ---
export interface CreateFactoryPayload {
  factoryCode: string
  factoryName: string
}

export interface FactoryListItem {
  factoryCode: string
  factoryName: string
  enabled: boolean
  createdAt: string
}

export function createFactory(token: string, payload: CreateFactoryPayload) {
  return request<FactoryListItem>('/super-admin/factories', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export function fetchFactories(token: string) {
  return request<FactoryListItem[]>('/super-admin/factories', {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function disableFactory(token: string, code: string) {
  return request<void>(`/super-admin/factories/${code}/disable`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function enableFactory(token: string, code: string) {
  return request<void>(`/super-admin/factories/${code}/enable`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` }
  })
}

// --- 工厂管理员账号 ---
export interface CreateFactoryAdminPayload {
  factoryCode: string
  username: string
  password?: string
}

export interface FactoryAdminResponse {
  factoryCode: string
  username: string
  password: string
  enabled: boolean
  createdAt: string
}

export interface FactoryAdminListItem {
  id: number
  factoryCode: string
  username: string
  enabled: boolean
  createdAt: string
}

export function createFactoryAdmin(token: string, payload: CreateFactoryAdminPayload) {
  return request<FactoryAdminResponse>('/super-admin/factory-admins', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export function fetchFactoryAdmins(token: string) {
  return request<FactoryAdminListItem[]>('/super-admin/factory-admins', {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function disableFactoryAdmin(token: string, id: number) {
  return request<void>(`/super-admin/factory-admins/${id}/disable`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function enableFactoryAdmin(token: string, id: number) {
  return request<void>(`/super-admin/factory-admins/${id}/enable`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` }
  })
}

// --- 授权码管理 ---
export interface GenerateAuthCodePayload {
  factoryCode: string
  expirationMinutes?: number
}

export interface AuthCodeResponse {
  code: string
  expiresAt: string
}

export interface AuthCodeListItem {
  id: number
  code: string
  factoryCode: string
  status: 'active' | 'expired' | 'consumed' | 'disabled'
  createdAt: string
  expiresAt: string
}

export function generateAuthCode(token: string, payload: GenerateAuthCodePayload) {
  return request<AuthCodeResponse>('/super-admin/auth-codes', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export function fetchAuthCodes(token: string) {
  return request<AuthCodeListItem[]>('/super-admin/auth-codes', {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function disableAuthCode(token: string, id: number) {
  return request<void>(`/super-admin/auth-codes/${id}/disable`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` }
  })
}

// --- 超级管理员账号 ---
export interface CreateSuperAdminPayload {
  username: string
  password: string
}

export function createSuperAdmin(token: string, payload: CreateSuperAdminPayload) {
  return request<void>('/super-admin/admins', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  })
}

export function fetchSuperAdmins(token: string) {
  return request<FactoryAdminListItem[]>('/super-admin/super-admins', {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function resetSuperAdminUserPassword(token: string, userId: number, password?: string) {
  const body: Record<string, string> = {}
  if (password) body.password = password
  return request<{ password: string }>(`/super-admin/users/${userId}/reset-password`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(body)
  })
}

export function deleteFactoryAdmin(token: string, id: number) {
  return request<void>(`/super-admin/factory-admins/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function deleteAuthCode(token: string, id: number) {
  return request<void>(`/super-admin/auth-codes/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function deleteSuperAdmin(token: string, id: number) {
  return request<void>(`/super-admin/super-admins/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` }
  })
}
