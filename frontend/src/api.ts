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

  return response.json()
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
