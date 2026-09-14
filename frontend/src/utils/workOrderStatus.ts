export function normalizeWorkOrderStatus(status: string) {
  if (status === 'IN_PROGRESS') return 'EXECUTING'
  if (status === 'CANCELLED') return 'CANCELED'
  return status
}
