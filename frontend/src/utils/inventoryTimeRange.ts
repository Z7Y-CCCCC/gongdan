function formatDateTime(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function resolveInventoryTimeRange(
  filter: { timeRange: string; startTime: string; endTime: string },
  now = new Date()
): { startTime?: string; endTime?: string } {
  if (filter.timeRange === 'custom') {
    return {
      startTime: filter.startTime ? `${filter.startTime.slice(0, 10)} 00:00:00` : undefined,
      endTime: filter.endTime ? `${filter.endTime.slice(0, 10)} 23:59:59` : undefined
    }
  }
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  if (filter.timeRange === 'today') {
    const end = new Date(start)
    end.setHours(23, 59, 59, 0)
    return { startTime: formatDateTime(start), endTime: formatDateTime(end) }
  }
  if (filter.timeRange === 'week') {
    start.setDate(start.getDate() - (start.getDay() || 7) + 1)
    return { startTime: formatDateTime(start), endTime: formatDateTime(now) }
  }
  if (filter.timeRange === 'month') {
    start.setDate(1)
    return { startTime: formatDateTime(start), endTime: formatDateTime(now) }
  }
  return {}
}
