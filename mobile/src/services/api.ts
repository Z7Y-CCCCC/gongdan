import type { ListResponse, MobileAuthResult, ReportImage, ReportStatus, WorkOrderInfo, WorkOrderReport } from '../types/api'

export const API_BASE = 'http://localhost:8080/api'

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
    uni.reLaunch({ url: '/pages/bind/index' })
    return false
  }
  return true
}

export function request<T>(path: string, options: UniApp.RequestOptions = {}): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${API_BASE}${path}`,
      method: options.method || 'GET',
      data: options.data,
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
}) {
  return request<WorkOrderReport>('/mobile/reports', {
    method: 'POST',
    data: payload
  })
}

export function fetchReports(status: ReportStatus, period = 'today') {
  return request<ListResponse<WorkOrderReport>>(`/mobile/reports?status=${status}&period=${period}&size=500`)
}
