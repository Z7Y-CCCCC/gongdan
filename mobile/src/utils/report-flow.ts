import { mobileSessionKey } from './session-key'

type ReportReview = { workOrderId: number; processName: string; quantity: number; startedAt: string }
type ReportView = { status: 'PENDING' | ''; review?: ReportReview }

// switchTab does not carry query parameters. Hand off only to the same session.
let reportFilter: { session: string; view: ReportView } | undefined

export function showPendingReportsOnReturn() {
  reportFilter = { session: mobileSessionKey(), view: { status: 'PENDING' } }
}

export function showReportsForReview(review: ReportReview) {
  reportFilter = { session: mobileSessionKey(), view: { status: '', review } }
}

export function consumeReportFilterOnReturn() {
  const current = reportFilter
  reportFilter = undefined
  if (current?.session && current.session === mobileSessionKey()) return current.view
}
