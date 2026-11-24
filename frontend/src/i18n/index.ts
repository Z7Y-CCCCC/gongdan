import { ref, toDisplayString, type App } from 'vue'
import shell from './locales/en-shell.json'
import pages from './locales/en-pages.json'
import support from './locales/en-support.json'
import errors from './locales/en-errors.json'
import { formatUiDate, uiTimezone, setUiTimezone } from './date'
export { formatUiDate, uiTimezone, setUiTimezone }

export type UiLocale = 'zh-CN' | 'en-US'
const stored = typeof localStorage === 'undefined' ? null : localStorage.getItem('heihu_locale')
export const uiLocale = ref<UiLocale>(stored === 'en-US' ? 'en-US' : 'zh-CN')
export const englishMessages: Record<string, string> = Object.fromEntries(Object.entries({ ...support, ...pages, ...shell, ...errors }).map(([key, value]) => [key.replace(/\s+/g, ' ').trim(), value]))
const escapeRegex = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const patterns = Object.entries(englishMessages).filter(([key]) => /\{\d+\}/.test(key)).map(([source, translated]) => {
  const indexes: number[] = []
  const regex = source.split(/(\{\d+\})/).map(part => /^\{\d+\}$/.test(part)
    ? (indexes.push(Number(part.slice(1, -1))), '([\\s\\S]+?)') : escapeRegex(part)).join('')
  return { regex: new RegExp(`^${regex}$`), translated, indexes }
})

export function setUiLocale(value: string) {
  if (value !== 'zh-CN' && value !== 'en-US') return false
  uiLocale.value = value
  if (typeof localStorage !== 'undefined') localStorage.setItem('heihu_locale', value)
  if (typeof document !== 'undefined') document.documentElement.lang = value
  if (typeof document !== 'undefined') document.title = value === 'en-US' ? 'Work Order Hub' : '小工单'
  return true
}

function translateKnown(text: string): string | null {
  const exact = englishMessages[text.replace(/\s+/g, ' ').trim()]
  if (exact) return exact
  for (const pattern of patterns) {
    const matches = pattern.regex.exec(text.trim())
    if (!matches) continue
    const captured: Record<number, string> = {}
    pattern.indexes.forEach((index, position) => { captured[index] = matches[position + 1] })
    return pattern.translated.replace(/\{(\d+)\}/g, (whole, index) => captured[index] ?? whole)
  }
  return null
}

export function t(text: string, values?: unknown[]): string
export function t<T>(text: T, values?: unknown[]): T
export function t(text: unknown, values?: unknown[]): unknown {
  if (typeof text !== 'string') return text
  if (!values && uiLocale.value === 'zh-CN') return text
  const source = text.replace(/\s+/g, ' ').trim()
  let translated = uiLocale.value === 'en-US' ? englishMessages[source] || text : text
  if (!values && uiLocale.value === 'en-US') {
    let compound = false
    if (!englishMessages[source] && text.includes('；')) {
      const parts = text.split('；').map(part => translateKnown(part))
      if (parts.every(part => part !== null)) { translated = parts.join('; '); compound = true }
    }
    if (!compound) translated = translateKnown(text) || text
  }
  return values ? translated.replace(/\{(\d+)\}/g, (whole, index) => Number(index) < values.length ? toDisplayString(values[Number(index)]) : whole) : translated
}

export const i18nPlugin = {
  install(app: App) {
    app.config.globalProperties.$t = t
    app.config.globalProperties.$date = formatUiDate
    document.documentElement.lang = uiLocale.value
    document.title = uiLocale.value === 'en-US' ? 'Work Order Hub' : '小工单'
    const synchronize = (event: StorageEvent) => {
      if (event.key === 'heihu_timezone') { setUiTimezone(event.newValue || 'Asia/Shanghai'); return }
      if (event.key !== 'heihu_locale') return
      uiLocale.value = event.newValue === 'en-US' ? 'en-US' : 'zh-CN'
      document.documentElement.lang = uiLocale.value
      document.title = uiLocale.value === 'en-US' ? 'Work Order Hub' : '小工单'
    }
    window.addEventListener('storage', synchronize)
    app.onUnmount(() => window.removeEventListener('storage', synchronize))
  }
}
