import type { FieldEventPayload, FieldEventRecord, ListResponse, MobileAuthResult, MobileDashboard, ReportImage, WorkOrderInfo, WorkOrderReport, UserInfo } from '../types/api'
import { loginRedirect, navigatePage } from '../utils/navigation'
import { validateMobileDashboard } from '../utils/dashboard'

// H5 can use /api through a reverse proxy. Local static demos use the backend port directly.
// Mini Program builds should set VITE_API_BASE_URL to HTTPS.
const configuredApiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
function defaultApiBase() {
  if (typeof window !== 'undefined' && window.location && ['127.0.0.1', 'localhost'].includes(window.location.hostname)) {
    return 'http://127.0.0.1:8080/api'
  }
  return '/api'
}
export const API_BASE = configuredApiBase || defaultApiBase()

function token() {
  return uni.getStorageSync('token') || ''
}

let expiryRedirectTimer: ReturnType<typeof setTimeout> | undefined

export function saveSession(result: Pick<MobileAuthResult, 'token' | 'user'>) {
  if (typeof result.token !== 'string' || !result.token || result.token !== result.token.trim() || /[\r\n]/.test(result.token)
    || !result.user || !Number.isSafeInteger(result.user.id) || result.user.id <= 0) throw new Error('登录响应不完整，请重试')
  if (expiryRedirectTimer) clearTimeout(expiryRedirectTimer)
  expiryRedirectTimer = undefined
  try {
    uni.setStorageSync('token', result.token)
    uni.setStorageSync('user', result.user)
  } catch {
    // Do not persist a new bearer token alongside the previous account if the
    // second synchronous storage write fails. The existing error path retries.
    for (const key of ['token', 'user']) { try { uni.removeStorageSync(key) } catch { /* storage may be unavailable */ } }
    throw new Error('登录状态保存失败，请检查可用存储空间后重试')
  }
}

export function clearSession() {
  uni.removeStorageSync('token')
  uni.removeStorageSync('user')
}

export function ensureLogin() {
  if (!token()) {
    let redirect = ''
    if (typeof window !== 'undefined' && window.location) {
      redirect = window.location.hash || ''
    }
    try {
      const pages = getCurrentPages()
      if (!redirect && pages.length > 0) {
        const currentPage = pages[pages.length - 1]
        const path = '/' + currentPage.route
        // @ts-ignore
        const options = currentPage.$page?.options || {}
        const query = Object.keys(options).map(k => `${k}=${encodeURIComponent(options[k])}`).join('&')
        redirect = path + (query ? `?${query}` : '')
      }
    } catch (e) {}
    if (redirect.replace(/^#/, '').split('?')[0] === '/pages/bind/index') return false
    redirect = loginRedirect(redirect)
    const url = redirect ? `/pages/bind/index?redirect=${encodeURIComponent(redirect)}` : '/pages/bind/index'
    navigatePage(url, true)
    return false
  }
  return true
}

function responseError(status: number, data: any, requestToken: string, fallback: string) {
  const message = typeof data?.message === 'string' ? data.message
    : status === 401 ? '登录已失效，请重新登录' : fallback
  if (status === 401 && requestToken && token() === requestToken) {
    clearSession()
    uni.showToast({ title: message, icon: 'none', duration: 2000 })
    if (!expiryRedirectTimer) {
      expiryRedirectTimer = setTimeout(() => {
        expiryRedirectTimer = undefined
        if (!token()) ensureLogin()
      }, 1500)
    }
  }
  return Object.assign(new Error(message), { status })
}

export function request<T>(path: string, options: { method?: 'GET' | 'POST' | 'PUT' | 'DELETE'; data?: any; header?: Record<string, string>; auth?: boolean } = {}): Promise<T> {
  const requestToken = options.auth === false ? '' : token()
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${API_BASE}${path}`,
      method: options.method || 'GET',
      data: options.data,
      // 免费内网穿透的首次 TLS/隧道握手可能超过 10 秒，给移动端留出完整登录时间。
      timeout: 30000,
      header: {
        'Content-Type': 'application/json',
        ...(requestToken ? { Authorization: `Bearer ${requestToken}` } : {}),
        ...(options.header || {})
      },
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          if (requestToken && token() !== requestToken) {
            reject(new Error('登录状态已变更，请刷新'))
            return
          }
          resolve(res.data as T)
          return
        }
        reject(responseError(res.statusCode, res.data, requestToken, '请求失败'))
      },
      fail: () => reject(new Error('网络请求失败'))
    })
  })
}

export function wechatLogin(payload: { code?: string; mockOpenid?: string }) {
  return request<MobileAuthResult>('/mobile/auth/wechat-login', {
    method: 'POST',
    data: payload,
    auth: false
  })
}

export function bindAccount(payload: { openidTicket: string; factoryCode: string; username: string; password: string }) {
  return request<MobileAuthResult>('/mobile/auth/bind', {
    method: 'POST',
    data: payload,
    auth: false
  })
}

export function passwordLogin(payload: { factoryCode: string; username: string; password: string }) {
  return request<{ token: string; user: UserInfo }>('/mobile/auth/password-login', { method: 'POST', data: payload, auth: false })
}

export function logoutSession() {
  return request<void>('/auth/logout', { method: 'POST' })
}

function queryString(params: Record<string, string | number | boolean | undefined>) {
  return Object.entries(params).filter(([, value]) => value !== undefined && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`).join('&')
}

async function fetchAllPages<T extends { id: number }>(path: string, params: Record<string, string | number | undefined> = {}): Promise<ListResponse<T>> {
  const size = 100
  const maxItems = 5000
  const sessionToken = token()
  const first = await request<ListResponse<T>>(`${path}?${queryString({ ...params, page: 1, size })}`)
  const items = [...(first.items || [])]
  const ids = new Set(items.map(item => item.id))
  const total = Math.max(items.length, Number(first.total) || 0)
  for (let page = 2; page <= Math.min(Math.ceil(total / size), maxItems / size); page += 1) {
    if (token() !== sessionToken) throw new Error('登录状态已变更，请刷新')
    const next = await request<ListResponse<T>>(`${path}?${queryString({ ...params, page, size })}`)
    if (!next.items?.length) break
    for (const item of next.items) {
      if (!ids.has(item.id) && items.length < maxItems) {
        ids.add(item.id)
        items.push(item)
      }
    }
  }
  if (token() !== sessionToken) throw new Error('登录状态已变更，请刷新')
  return { items, total, truncated: items.length < total }
}

export function fetchWorkOrders() {
  return fetchAllPages<WorkOrderInfo>('/mobile/work-orders')
}

export function fetchWorkOrderPage(filters: { page: number; size: number; keyword?: string; status?: string; reportableOnly?: boolean }) {
  return request<ListResponse<WorkOrderInfo>>(`/mobile/work-orders?${queryString(filters)}`)
}

export async function fetchDashboard(day: string): Promise<MobileDashboard> {
  const result = await request<MobileDashboard>(`/mobile/dashboard?${queryString({ day })}`)
  return validateMobileDashboard(result)
}

function positiveResourceId(value: number | string, label: string) {
  const number = typeof value === 'number' ? value : typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : NaN
  if (!Number.isSafeInteger(number) || number <= 0) throw new Error(`${label}无效`)
  return number
}

export async function fetchWorkOrder(id: number | string) {
  // Deep-link text is untrusted. Never let a slash/dot segment change the
  // authenticated endpoint before the server gets a chance to authorize it.
  return request<WorkOrderInfo>(`/mobile/work-orders/${positiveResourceId(id, '工单编号')}`)
}

export function fetchWorkOrderByCode(code: string) {
  return request<WorkOrderInfo>(`/mobile/work-orders/by-code/${encodeURIComponent(code)}`)
}

export async function fetchWorkOrderReporters(workOrderId: number | string, processSequenceNo: number) {
  const id = positiveResourceId(workOrderId, '工单编号')
  const sequence = positiveResourceId(processSequenceNo, '工序编号')
  return request<Array<{ id: number; name: string; username: string; departmentName?: string }>>(`/mobile/work-orders/${id}/reporters?processSequenceNo=${sequence}`)
}

export function uploadReportImage(filePath: string): Promise<ReportImage> {
  const requestToken = token()
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${API_BASE}/mobile/reports/images`,
      filePath,
      name: 'file',
      timeout: 30000,
      header: {
        ...(requestToken ? { Authorization: `Bearer ${requestToken}` } : {})
      },
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          if (requestToken && token() !== requestToken) {
            reject(new Error('登录状态已变更，请重新上传'))
            return
          }
          try {
            const image = JSON.parse(res.data) as ReportImage
            if (!image || !Number.isSafeInteger(image.id) || image.id <= 0) throw new Error('无效照片记录')
            resolve(image)
          } catch {
            reject(new Error('图片上传结果解析失败'))
          }
          return
        }
        let data: unknown
        try { data = JSON.parse(res.data) } catch { data = undefined }
        reject(responseError(res.statusCode, data, requestToken, '图片上传失败'))
      },
      fail: () => reject(new Error('图片上传失败'))
    })
  })
}

export function downloadReportImage(imageId: number): Promise<string> {
  try { positiveResourceId(imageId, '照片编号') }
  catch (error) { return Promise.reject(error) }
  const requestToken = token()
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: `${API_BASE}/mobile/reports/images/${imageId}/file`,
      timeout: 30000,
      header: requestToken ? { Authorization: `Bearer ${requestToken}` } : {},
      success: (res) => {
        if (res.statusCode === 200 && token() === requestToken && typeof res.tempFilePath === 'string' && res.tempFilePath) resolve(res.tempFilePath)
        else {
          // H5 creates a Blob URL before this callback. Discarding an old
          // session/error download must release it; native paths are untouched.
          if (typeof res.tempFilePath === 'string' && res.tempFilePath.startsWith('blob:')
            && typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(res.tempFilePath)
          reject(responseError(res.statusCode, undefined, requestToken, '图片暂时无法加载，请重试'))
        }
      },
      fail: () => reject(new Error('图片暂时无法加载，请重试'))
    })
  })
}

export function createReport(payload: {
  workOrderId: number
  processSequenceNo: number
  processName: string
  reportQuantity: number
  goodQuantity: number
  defectQuantity: number
  defectItemName?: string
  reportDurationMinutes: number
  startTime?: string
  endTime?: string
  materialName?: string
  remark?: string
  imageIds: number[]
  reporterUserId?: number
}) {
  return request<WorkOrderReport>('/mobile/reports', {
    method: 'POST',
    data: payload
  })
}

export function fetchReports(status?: string, period = '', options: { includeSubmitted?: boolean; workOrderId?: number } = {}) {
  return fetchAllPages<WorkOrderReport>('/mobile/reports', {
    status, period, workOrderId: options.workOrderId,
    includeSubmitted: options.includeSubmitted ? 'true' : undefined
  })
}

export function fetchFieldEvents() {
  return request<ListResponse<FieldEventRecord>>('/mobile/field-events?size=50')
}

export function fetchReportPage(filters: { page: number; size: number; status?: string; period?: string; includeSubmitted?: boolean; workOrderId?: number }) {
  return request<ListResponse<WorkOrderReport>>(`/mobile/reports?${queryString(filters)}`)
}

export function createFieldEvent(payload: FieldEventPayload) {
  return request<FieldEventRecord>('/mobile/field-events', {
    method: 'POST',
    data: payload
  })
}
