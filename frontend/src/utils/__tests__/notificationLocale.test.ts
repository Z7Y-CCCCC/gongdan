import { afterEach, describe, expect, it } from 'vitest'
import { setUiLocale, setUiTimezone } from '../../i18n'
import { notificationBody, notificationHeading } from '../notificationLocale'

const titles = {
  REPORT_PENDING_APPROVAL: '待审批报工', REPORT_SUBMITTED: '今日报工', REPORT_APPROVED: '报工已通过',
  REPORT_REJECTED: '报工被驳回', WORK_ORDER_OVERDUE: '逾期工单'
}
function notification(eventType: keyof typeof titles, content = '保存') {
  return { eventType, title: titles[eventType], content }
}
afterEach(() => { setUiLocale('zh-CN'); setUiTimezone('Asia/Shanghai') })

describe('notification system text is localized without translating authored payloads', () => {
  it.each([
    ['REPORT_PENDING_APPROVAL', 'Reports awaiting approval'], ['REPORT_SUBMITTED', "Today's reports"],
    ['REPORT_APPROVED', 'Report approved'], ['REPORT_REJECTED', 'Report rejected'], ['WORK_ORDER_OVERDUE', 'Overdue work orders']
  ] as const)('translates only the exact %s system title', (type, expected) => {
    setUiLocale('en-US')
    expect(notificationHeading(notification(type))).toBe(expected)
    expect(notificationHeading({ ...notification(type), title: '保存' })).toBe('保存')
  })

  it('keeps unknown events untouched even when their text matches known UI translations or system patterns', () => {
    setUiLocale('en-US')
    const item = { eventType: 'CUSTOM_MESSAGE', title: '今日报工', content: '保存 提交 2 良品 / 1 不良' }
    expect(notificationHeading(item)).toBe(item.title)
    expect(notificationBody(item)).toBe(item.content)
    expect(notificationHeading({ ...item, title: '保存' })).toBe('保存')
  })

  it('preserves complete pending-approval contents because all three fields are authored', () => {
    setUiLocale('en-US')
    const item = notification('REPORT_PENDING_APPROVAL', '保存 / 删除 / 编辑')
    expect(notificationBody(item)).toBe(item.content)
  })

  it('translates submitted quantity labels but never the reporter name or numeric strings', () => {
    setUiLocale('en-US')
    expect(notificationBody(notification('REPORT_SUBMITTED', '保存 提交 2 良品 / 1 不良')))
      .toBe('保存 submitted 2 good / 1 defective')
    expect(notificationBody(notification('REPORT_SUBMITTED', '客户原文  A\nB 提交 0 良品 / -1 不良')))
      .toBe('客户原文  A\nB submitted 0 good / -1 defective')
  })

  it('keeps delimiter-like words inside a submitted reporter name unchanged', () => {
    setUiLocale('en-US')
    expect(notificationBody(notification('REPORT_SUBMITTED', '保存 提交 3 良品 / 2 不良 提交 2 良品 / 1 不良')))
      .toBe('保存 提交 3 良品 / 2 不良 submitted 2 good / 1 defective')
  })

  it.each(['REPORT_APPROVED', 'REPORT_REJECTED'] as const)('translates only the %s reviewer label and preserves the entire remark', type => {
    setUiLocale('en-US')
    const item = notification(type, '保存 / 删除 / 审批人 编辑 / 良品不足，客户原文  A\nB / 继续生产')
    expect(notificationBody(item)).toBe('保存 / 删除 / Reviewed by 编辑 / 良品不足，客户原文  A\nB / 继续生产')
  })

  it('does not guess reviewer field boundaries when a name or remark repeats the system marker', () => {
    setUiLocale('en-US')
    const item = notification('REPORT_REJECTED', '保存 / 删除 / 审批人 编辑 / 审批人 保存')
    expect(notificationBody(item)).toBe(item.content)
  })

  it('translates overdue metadata without changing order or product names and respects the selected timezone', () => {
    setUiLocale('en-US')
    const item = notification('WORK_ORDER_OVERDUE', '保存 / 删除 / 计划完工 2026-11-24 12:00')
    expect(notificationBody(item)).toBe('保存 / 删除 / Planned completion 2026-11-24 12:00')
    setUiTimezone('America/New_York')
    expect(notificationBody(item)).toBe('保存 / 删除 / Planned completion 2026-11-23 23:00')
    expect(notificationBody(notification('WORK_ORDER_OVERDUE', '保存 / 删除 / 计划完工 -')))
      .toBe('保存 / 删除 / Planned completion -')
  })

  it.each([
    ['REPORT_SUBMITTED', '保存 提交 2.5 良品 / 1 不良'], ['REPORT_SUBMITTED', '保存 提交 2 良品 / 1 不良\n'],
    ['REPORT_SUBMITTED', '客户自由内容'], ['REPORT_APPROVED', '审批人 保存'],
    ['REPORT_REJECTED', '保存 / 删除 / 审批人 '], ['WORK_ORDER_OVERDUE', '保存 / 删除 / 计划完工 客户自由日期'],
    ['WORK_ORDER_OVERDUE', '保存 / 删除 / 计划完工 2026-11-24 12:00\n'],
    ['WORK_ORDER_OVERDUE', '保存 / 计划完工 字段 / 删除 / 计划完工 2026-11-24 12:00']
  ] as const)('leaves unrecognized %s content verbatim (%s)', (type, content) => {
    setUiLocale('en-US')
    expect(notificationBody(notification(type, content))).toBe(content)
  })

  it('requires the original system title as well as its event type before translating body text', () => {
    setUiLocale('en-US')
    const item = { ...notification('REPORT_SUBMITTED', '保存 提交 2 良品 / 1 不良'), title: '客户原文' }
    expect(notificationBody(item)).toBe(item.content)
  })

  it('switches back to exact Chinese source strings without mutating notification data', () => {
    const item = Object.freeze(notification('REPORT_SUBMITTED', '客户原文  A\nB 提交 2 良品 / 1 不良'))
    setUiLocale('en-US')
    expect(notificationBody(item)).toContain(' submitted ')
    setUiLocale('zh-CN')
    expect(notificationBody(item)).toBe(item.content)
    expect(notificationHeading(item)).toBe(item.title)
  })
})
