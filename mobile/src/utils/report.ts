const MAX_INTEGER = 2147483647

export function formatDateTime(date: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

export function updateDateTime(value: string, part: 'date' | 'time', next: string) {
  const [date, time] = (value || formatDateTime(new Date())).split(' ')
  return part === 'date' ? `${next} ${time}` : `${date} ${next}:00`
}

function validDateTime(value: string) {
  if (!/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(value)) return NaN
  const parsed = new Date(value.replace(' ', 'T'))
  if (!Number.isFinite(parsed.getTime()) || formatDateTime(parsed).slice(0, 16) !== value.slice(0, 16)) return NaN
  return parsed.getTime()
}

export function validateReportValues(form: {
  reportQuantity: string; goodQuantity: string; defectQuantity: string;
  startTime: string; endTime: string;
}, hours: string, minutes: string) {
  const report = Number(form.reportQuantity)
  const good = Number(form.goodQuantity)
  const defect = Number(form.defectQuantity)
  if (![report, good, defect].every(value => Number.isInteger(value) && value >= 0 && value <= MAX_INTEGER) || report <= 0) {
    return '报工数须为正整数，良品数和不良数须为非负整数'
  }
  if (good + defect !== report) return '良品数与不良数之和必须等于报工数'
  const start = validDateTime(form.startTime)
  const end = validDateTime(form.endTime)
  if (!Number.isFinite(start) || !Number.isFinite(end)) return '请选择有效的开始和结束时间'
  if (end < start) return '结束时间不能早于开始时间'
  const h = Number(hours)
  const m = Number(minutes)
  if (!Number.isInteger(h) || h < 0 || !Number.isInteger(m) || m < 0 || m > 59 || h * 60 + m > MAX_INTEGER) {
    return '报工时长须为非负整数，分钟须在0到59之间'
  }
  return ''
}
