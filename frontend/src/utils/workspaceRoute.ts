import { isAdvancedWorkspaceSection } from './advancedWorkspace'

export type WorkspaceRoute = { main: string; sub?: string }
const simple = new Set(['home', 'dashboard', 'advanced', 'report', 'analytics', 'notifications', 'super-admin'])
const groups: Record<string, string[]> = {
  production: ['sales', 'workorder', 'task', 'reporting', 'workbench'],
  inventory: ['inbound', 'outbound', 'detail', 'balance', 'summary'],
  basedata: ['product', 'bom', 'unit', 'defect', 'process', 'route', 'warehouse', 'supplier', 'customer'],
  custom: ['product', 'bom', 'workorder', 'reporting', 'process', 'customer', 'receipt', 'receipt_detail'],
  sysconfig: ['department', 'user', 'factory', 'plugin-center', 'external-data-sources'],
  monitor: ['online', 'cache', 'cacheList']
}

export function parseWorkspaceKey(key: string | null): WorkspaceRoute | null {
  if (!key || key.length > 128) return null
  if (simple.has(key)) return { main: key }
  if (key.startsWith('advanced-') && isAdvancedWorkspaceSection(key.slice(9).toUpperCase())) {
    return { main: 'advanced', sub: key.slice(9) }
  }
  for (const [main, choices] of Object.entries(groups)) {
    const sub = key.startsWith(`${main}-`) ? key.slice(main.length + 1) : ''
    if (choices.includes(sub)) return { main, sub }
  }
  const plugin = /^plugin-([a-zA-Z0-9][a-zA-Z0-9_.-]{0,63})$/.exec(key)
  return plugin ? { main: 'plugin', sub: plugin[1] } : null
}

export function readWorkspaceRoute(href: string): WorkspaceRoute | null {
  try { return parseWorkspaceKey(new URL(href).searchParams.get('workspace')) } catch { return null }
}

export function buildWorkspaceUrl(href: string, key: string) {
  if (!parseWorkspaceKey(key)) throw new Error('Invalid workspace route')
  const url = new URL(href)
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Unsupported workspace URL')
  url.hash = ''
  url.searchParams.set('workspace', key)
  return url.toString()
}
