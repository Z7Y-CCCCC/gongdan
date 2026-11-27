export const advancedWorkspaceSections = [
  { key: 'SOP', label: 'SOP 文档库', icon: 'file' },
  { key: 'MESSAGE', label: '微信/企微推送', icon: 'message' },
  { key: 'QUALITY', label: '质检返工', icon: 'check' },
  { key: 'EQUIPMENT', label: '设备/工位', icon: 'settings' },
  { key: 'GANTT', label: '排产甘特图', icon: 'calendar' },
  { key: 'PAYROLL', label: '工资绩效', icon: 'activity' },
  { key: 'FIELD_EVENT', label: '现场事件', icon: 'warning' }
] as const

export type AdvancedWorkspaceSection = typeof advancedWorkspaceSections[number]['key']
export function isAdvancedWorkspaceSection(value: unknown): value is AdvancedWorkspaceSection {
  return advancedWorkspaceSections.some(section => section.key === value)
}
