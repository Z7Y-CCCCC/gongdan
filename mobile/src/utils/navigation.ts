const tabPages = new Set([
  '/pages/home/index', '/pages/work-orders/index', '/pages/report-list/index',
  '/pages/tasks/index', '/pages/board/index'
])
const pages = new Set([...tabPages, '/pages/work-order-detail/index', '/pages/report-form/index', '/pages/field-events/index'])

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
  if (typeof window !== 'undefined' && window.location) {
    window.location.hash = `#${url}`
  } else if (tabPages.has(url.split('?')[0])) {
    uni.switchTab({ url: url.split('?')[0] })
  } else if (replace) {
    uni.reLaunch({ url })
  } else {
    uni.navigateTo({ url })
  }
}

export function goAfterLogin(redirect: unknown) {
  navigatePage(loginRedirect(redirect) || '/pages/home/index', true)
}
