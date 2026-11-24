// switchTab does not carry query parameters. Hand off only to the same session.
let pendingReviewToken = ''

export function showPendingReportsOnReturn() {
  pendingReviewToken = uni.getStorageSync('token') || ''
}

export function consumePendingReportsOnReturn() {
  const shouldShowPending = Boolean(pendingReviewToken && pendingReviewToken === uni.getStorageSync('token'))
  pendingReviewToken = ''
  return shouldShowPending
}
