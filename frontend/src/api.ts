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
    throw new Error(message)
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
  token?: string
  user?: UserInfo
  factories?: FactoryOptionItem[]
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
