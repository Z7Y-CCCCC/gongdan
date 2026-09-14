import type { UserInfo, WorkOrderTask } from '../types/api'

// Display hint only: the reporters endpoint and report submission enforce the
// authoritative rule on the server, including changes since the page loaded.
export function canReportTask(task: WorkOrderTask, user?: UserInfo) {
  if (task.enabled === false || !user) return false
  const permission = (task.reportPermission || '').trim()
  if (!permission || permission === '所有人' || permission.toUpperCase() === 'ALL') return true
  const allowed = permission.split(',').map(value => value.trim()).filter(Boolean)
  const identities = [user.name, user.username, String(user.id), user.role, user.roleLabel, user.departmentName]
  return identities.some(value => Boolean(value) && allowed.includes(value!))
    || Boolean(task.assignee && [user.name, user.username].includes(task.assignee))
}
