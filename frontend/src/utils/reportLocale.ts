import type { UiLocale } from '../i18n'

/** Preserve native _u/paging parameters; locale is always one of two fixed values. */
export function reportLocaleUrl(path: string, locale: UiLocale, timestamp: number) {
  const url = new URL(path, window.location.href)
  // Native UReport reads raw location.search and decodeURI, not URLSearchParams.
  // Reserializing every parameter would turn %20 into + and corrupt filenames.
  const parameters = url.search.slice(1).split('&').filter(part => part && !/^(?:ui_locale|_t)(?:=|$)/.test(part))
  parameters.push(`ui_locale=${locale === 'en-US' ? 'en-US' : 'zh-CN'}`, `_t=${timestamp}`)
  url.search = parameters.join('&')
  return url.origin === window.location.origin ? `${url.pathname}${url.search}${url.hash}` : url.href
}

/** Refresh the selected native template instead of silently opening the default. */
export function currentReportPath(frame: HTMLIFrameElement | null, initialPath: string) {
  try {
    const initial = new URL(initialPath, window.location.href)
    const current = new URL(frame?.contentWindow?.location.href || '', window.location.href)
    if (current.origin === initial.origin && current.pathname === initial.pathname) return current.href
  } catch {}
  return ''
}

/** UReport's native save control is enabled while the current report is dirty. */
export function reportHasUnsavedChanges(frame: HTMLIFrameElement | null) {
  if (!frame) return false
  try {
    const save = frame.contentDocument?.getElementById('__save_btn')
    // An unavailable native control is not evidence that a loaded draft is clean.
    return !save || !save.classList.contains('disabled')
  } catch {
    return true
  }
}
