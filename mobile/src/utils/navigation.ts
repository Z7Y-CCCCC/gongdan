const tabPages = new Set([
  '/pages/home/index', '/pages/work-orders/index', '/pages/report-list/index',
  '/pages/tasks/index', '/pages/board/index'
])
const pages = new Set([...tabPages, '/pages/work-order-detail/index', '/pages/report-form/index', '/pages/field-events/index'])
let pendingNavigation: { key: string } | undefined

export function safeDecode(value: string) {
  try { return decodeURIComponent(value) } catch { return value }
}

export function loginRedirect(value: unknown): string {
  let route = typeof value === 'string' ? value : ''
  // Routers may pass the outer query value encoded or already decoded. Stop
  // decoding once the route is recognizable so its own query stays intact.
  for (let i = 0; i < 2 && !route.startsWith('/') && !route.startsWith('#/'); i += 1) {
    const decoded = safeDecode(route)
    if (decoded === route) break
    route = decoded
  }
  if (route.startsWith('#')) route = route.slice(1)
  return pages.has(route.split('?')[0]) ? route : ''
}

export function navigatePage(url: string, replace = false) {
  const isTab = tabPages.has(url.split('?')[0])
  const target = isTab ? url.split('?')[0] : url
  const mode = isTab ? 'switchTab' : replace ? 'reLaunch' : 'navigateTo'
  const key = `${mode}:${target}`
  // App launch and the first page can both request the same login redirect
  // before the uni page registry is ready. Queue that transition only once.
  if (pendingNavigation?.key === key) return
  const navigation = { key }
  pendingNavigation = navigation
  const options = {
    url: target,
    fail(error: any) {
      if (pendingNavigation !== navigation) return
      uni.showToast({ title: error?.errMsg || '页面跳转失败，请重试', icon: 'none' })
    },
    complete() {
      if (pendingNavigation === navigation) pendingNavigation = undefined
    }
  }
  if (isTab) uni.switchTab(options)
  else if (replace) uni.reLaunch(options)
  else uni.navigateTo(options)
}

export function goAfterLogin(redirect: unknown) {
  navigatePage(loginRedirect(redirect) || '/pages/home/index', true)
}
