import { ref } from 'vue'

const initial = localStorage.getItem('heihu_timezone') || 'Asia/Shanghai'
function validZone(value: string) { try { new Intl.DateTimeFormat('en', { timeZone: value }); return true } catch { return false } }
export const uiTimezone = ref(validZone(initial) ? initial : 'Asia/Shanghai')
export function setUiTimezone(value: string) {
  if (!validZone(value)) return false
  uiTimezone.value = value
  localStorage.setItem('heihu_timezone', value)
  return true
}

export function formatUiDate(value: unknown, seconds = false) {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) return value
  let input = value
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}/.test(value) && !/(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)) input = value.replace(' ', 'T') + '+08:00'
  const date = input instanceof Date ? input : new Date(input as string | number)
  if (Number.isNaN(date.getTime())) return String(value)
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: uiTimezone.value, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', ...(seconds ? { second: '2-digit' } : {}), hourCycle: 'h23' }).formatToParts(date)
  const get = (key: string) => parts.find(part => part.type === key)?.value || ''
  return `${get('year')}-${get('month')}-${get('day')} ${get('hour')}:${get('minute')}${seconds ? ':' + get('second') : ''}`
}
