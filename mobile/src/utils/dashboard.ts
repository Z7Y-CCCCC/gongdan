import type { MobileDashboard } from '../types/api'

const metrics = [
  'workOrderTotal', 'totalPlanned', 'totalGood', 'activeOrderCount', 'reportTotal',
  'pendingReportCount', 'todayReportCount', 'totalDefect', 'todayGood', 'todayDefect', 'todayPending'
] as const

function requireCount(value: unknown) {
  if (typeof value !== 'number' || !Number.isFinite(value) || !Number.isInteger(value) || value < 0) {
    throw new Error('统计数据返回格式异常，请重试')
  }
  if (!Number.isSafeInteger(value)) throw new Error('统计数值超出安全范围，请联系管理员')
}

export function validateMobileDashboard(value: unknown): MobileDashboard {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('统计数据返回格式异常，请重试')
  const result = value as MobileDashboard
  metrics.forEach(key => requireCount(result[key]))
  if (!Array.isArray(result.recentOrders) || !Array.isArray(result.rankings)
      || result.recentOrders.length > 5 || result.rankings.length > 5
      || result.recentOrders.some(item => !item || !Number.isSafeInteger(item.id) || item.id <= 0)) {
    throw new Error('统计数据返回格式异常，请重试')
  }
  for (const item of result.rankings) {
    if (!item || typeof item.name !== 'string') throw new Error('统计数据返回格式异常，请重试')
    requireCount(item.value)
  }
  return result
}
