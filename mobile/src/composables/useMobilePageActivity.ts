import { onBeforeUnmount, onMounted } from 'vue'
import { onHide } from '@dcloudio/uni-app'

function currentPage() {
  try {
    const pages = getCurrentPages()
    return pages[pages.length - 1]
  } catch { return undefined }
}

function currentHash() {
  return typeof window !== 'undefined' ? window.location?.hash || '' : ''
}

// navigateTo can hide a live page without unmounting it. Native camera/album
// overlays can also hide it, but keep the uni page and H5 route unchanged.
export function useMobilePageActivity() {
  let disposed = false
  let hideEpoch = 0
  let routeEpoch = 0
  const routeChanged = () => { routeEpoch++ }
  onHide(() => { hideEpoch++ })
  onMounted(() => {
    if (typeof window !== 'undefined') window.addEventListener('hashchange', routeChanged)
  })
  onBeforeUnmount(() => {
    disposed = true
    if (typeof window !== 'undefined') window.removeEventListener('hashchange', routeChanged)
  })

  function capture(nativeOverlay = false) {
    const page = currentPage(), hash = currentHash()
    const startedHide = hideEpoch, startedRoute = routeEpoch
    return () => !disposed && startedRoute === routeEpoch
      && (nativeOverlay || startedHide === hideEpoch)
      && page === currentPage() && hash === currentHash()
  }

  return { capture }
}
