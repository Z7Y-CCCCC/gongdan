import type { FieldEventPayload, FieldEventRecord, ListResponse, MobileAuthResult, ReportImage, ReportStatus, WorkOrderInfo, WorkOrderReport } from '../types/api'

// H5 can use /api through a reverse proxy. Local static demos use the backend port directly.
// Mini Program builds should set VITE_API_BASE_URL to HTTPS.
const configuredApiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
function defaultApiBase() {
  if (typeof window !== 'undefined' && window.location && ['127.0.0.1', 'localhost'].includes(window.location.hostname)) {
    return 'http://127.0.0.1:18080/api'
  }
  return '/api'
}
export const API_BASE = configuredApiBase || defaultApiBase()

function token() {
  return uni.getStorageSync('token') || ''
}

export function saveSession(result: MobileAuthResult) {
  if (result.token) uni.setStorageSync('token', result.token)
  if (result.user) uni.setStorageSync('user', result.user)
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
    const url = redirect ? `/pages/bind/index?redirect=${encodeURIComponent(redirect)}` : '/pages/bind/index'
    if (typeof window !== 'undefined' && window.location) {
      window.location.hash = `#${url}`
      return false
    }
    setTimeout(() => {
      uni.reLaunch({
        url,
        fail: (err) => {
          console.error('[ensureLogin] reLaunch failed, trying navigateTo...', err)
          uni.navigateTo({
            url,
            fail: (err2) => {
              console.error('[ensureLogin] navigateTo also failed. Fallback to location.href...', err2)
              if (typeof window !== 'undefined' && window.location) {
                window.location.hash = `#/pages/bind/index?redirect=${encodeURIComponent(redirect)}`
              }
            }
          })
        }
      })
    }, 100)
    return false
  }
  return true
}

export function request<T>(path: string, options: { method?: 'GET' | 'POST' | 'PUT' | 'DELETE'; data?: any; header?: Record<string, string> } = {}): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${API_BASE}${path}`,
      method: options.method || 'GET',
      data: options.data,
      timeout: 10000,
      header: {
        'Content-Type': 'application/json',
        ...(token() ? { Authorization: `Bearer ${token()}` } : {}),
        ...(options.header || {})
      },
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data as T)
          return
        }
        // token 失效或被挤下线
        if (res.statusCode === 401) {
          clearSession()
          const data = res.data as any
          const msg = data?.message || '登录已失效，请重新登录'
          uni.showToast({ title: msg, icon: 'none', duration: 2000 })
          setTimeout(() => {
            uni.reLaunch({ url: '/pages/bind/index' })
          }, 1500)
          reject(new Error(msg))
          return
        }
        const data = res.data as any
        reject(new Error(data?.message || '请求失败'))
      },
      fail: () => reject(new Error('网络请求失败'))
    })
  })
}

export function wechatLogin(payload: { code?: string; mockOpenid?: string }) {
  return request<MobileAuthResult>('/mobile/auth/wechat-login', {
    method: 'POST',
    data: payload
  })
}

export function bindAccount(payload: { openidTicket: string; factoryCode: string; username: string; password: string }) {
  return request<MobileAuthResult>('/mobile/auth/bind', {
    method: 'POST',
    data: payload
  })
}

export function fetchWorkOrders() {
  return request<ListResponse<WorkOrderInfo>>('/mobile/work-orders?size=500')
}

export function fetchWorkOrder(id: number | string) {
  return request<WorkOrderInfo>(`/mobile/work-orders/${id}`)
}

export function fetchWorkOrderByCode(code: string) {
  return request<WorkOrderInfo>(`/mobile/work-orders/by-code/${encodeURIComponent(code)}`)
}

export function fetchWorkOrderReporters(workOrderId: number | string) {
  return request<Array<{ id: number; name: string; username: string }>>(`/mobile/work-orders/${workOrderId}/reporters`)
}

export function uploadReportImage(filePath: string): Promise<ReportImage> {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${API_BASE}/mobile/reports/images`,
      filePath,
      name: 'file',
      header: {
        Authorization: `Bearer ${token()}`
      },
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try {
            resolve(JSON.parse(res.data) as ReportImage)
          } catch {
            reject(new Error('图片上传结果解析失败'))
          }
          return
        }
        reject(new Error('图片上传失败'))
      },
      fail: () => reject(new Error('图片上传失败'))
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

export function fetchReports(status?: string, period = '') {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  if (period) params.set('period', period)
  params.set('size', '500')
  return request<ListResponse<WorkOrderReport>>(`/mobile/reports?${params.toString()}`)
}

export function fetchFieldEvents() {
  return request<ListResponse<FieldEventRecord>>('/mobile/field-events?size=50')
}

export function createFieldEvent(payload: FieldEventPayload) {
  return request<FieldEventRecord>('/mobile/field-events', {
    method: 'POST',
    data: payload
  })
}
