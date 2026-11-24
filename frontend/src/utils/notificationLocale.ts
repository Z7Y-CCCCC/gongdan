import type { NotificationInfo } from '../api'
import { formatUiDate, t, uiLocale } from '../i18n'

type NotificationText = Pick<NotificationInfo, 'eventType' | 'title' | 'content'>

// These are the only system events emitted by NotificationService. A matching
// event type alone is not a license to translate an arbitrary authored title.
const systemTitles = new Map([
  ['REPORT_PENDING_APPROVAL', '待审批报工'],
  ['REPORT_SUBMITTED', '今日报工'],
  ['REPORT_APPROVED', '报工已通过'],
  ['REPORT_REJECTED', '报工被驳回'],
  ['WORK_ORDER_OVERDUE', '逾期工单']
])

function isSystemNotification(item: NotificationText) {
  return systemTitles.get(item.eventType) === item.title
}

function systemMarker(content: string, marker: string): [string, string] | null {
  const index = content.indexOf(marker)
  // Names and remarks may themselves contain these words. An ambiguous flat
  // historical string must remain verbatim rather than guessing field borders.
  if (index < 0 || index !== content.lastIndexOf(marker)) return null
  const prefix = content.slice(0, index)
  const suffix = content.slice(index + marker.length)
  return prefix.includes(' / ') && suffix.length ? [prefix, suffix] : null
}

export function notificationHeading(item: NotificationText): string {
  return isSystemNotification(item) ? t(item.title) : item.title
}

export function notificationBody(item: NotificationText): string {
  const content = item.content
  if (uiLocale.value !== 'en-US' || !isSystemNotification(item) || typeof content !== 'string') return content

  if (item.eventType === 'REPORT_SUBMITTED') {
    const fields = /^([\s\S]+) 提交 (-?\d+) 良品 \/ (-?\d+) 不良$/.exec(content)
    if (fields && fields[0] === content) {
      return t('{0} 提交 {1} 良品 / {2} 不良', [fields[1], fields[2], fields[3]])
    }
  }

  if (item.eventType === 'REPORT_APPROVED' || item.eventType === 'REPORT_REJECTED') {
    const fields = systemMarker(content, ' / 审批人 ')
    if (fields) return t('{0} / 审批人 {1}', fields)
  }

  if (item.eventType === 'WORK_ORDER_OVERDUE') {
    const fields = systemMarker(content, ' / 计划完工 ')
    const date = fields && /^(?:-|\d{4}-\d{2}-\d{2} \d{2}:\d{2})$/.exec(fields[1])
    if (fields && date && date[0] === fields[1]) {
      return t('{0} / 计划完工 {1}', [fields[0], formatUiDate(fields[1])])
    }
  }

  // Pending approval contains only authored order/process/reporter fields.
  // Unknown formats and future event types are deliberately not passed to t().
  return content
}
