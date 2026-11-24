import { t } from '../i18n'

export function escapePrintHtml(value: unknown) {
  return String(value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

/** Only call this with fixed UI labels, never user-owned names or values. */
export function printLabel(text: string, values?: unknown[]) {
  return escapePrintHtml(t(text, values))
}

/** A printed spreadsheet tab is UI; sanitize its name, never the cell values. */
export function printSheetName(label: string, code: string, used: Set<string>) {
  const base = `${label}${code ? '-' + code : ''}`.replace(/[\\/*?:\[\]]/g, '_').replace(/^'+|'+$/g, '').slice(0, 31).replace(/'+$/g, '') || 'Sheet'
  let name = base
  let index = 2
  while (used.has(name.toLowerCase())) {
    const suffix = `-${index++}`
    name = base.slice(0, 31 - suffix.length) + suffix
  }
  used.add(name.toLowerCase())
  return name
}
