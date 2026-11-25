import { saveAs } from 'file-saver'

export type SpreadsheetCell = string | number | boolean | null | undefined
export type SpreadsheetRows = ReadonlyArray<ReadonlyArray<SpreadsheetCell>>
const MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
const MAX_ROWS = 1_048_576
const MAX_COLUMNS = 16_384
const MAX_TEXT_LENGTH = 32_767

function validateText(value: string) {
  if (value.length > MAX_TEXT_LENGTH) {
    throw new Error('Excel 单元格最多支持 32767 个字符，请选择 CSV 备份以保留原始数据')
  }
  // ExcelJS strips some control characters, and malformed Unicode cannot survive XML encoding.
  if (/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F\uFFFE\uFFFF]/.test(value)) {
    throw new Error('Excel 不支持该控制字符，请选择 CSV 备份以保留原始数据')
  }
  for (let index = 0; index < value.length; index++) {
    const code = value.charCodeAt(index)
    if (code >= 0xD800 && code <= 0xDBFF) {
      const next = value.charCodeAt(++index)
      if (!(next >= 0xDC00 && next <= 0xDFFF)) throw new Error('文本包含不完整的 Unicode 字符，请选择 CSV 备份')
    } else if (code >= 0xDC00 && code <= 0xDFFF) {
      throw new Error('文本包含不完整的 Unicode 字符，请选择 CSV 备份')
    }
  }
  return value
}

function encodeText(value: string) {
  // OOXML treats _xNNNN_ as an escape, and XML normalizes raw CR to LF.
  // Escape the literal prefix first, then encode CR, so the decoded business value stays identical.
  return value.replace(/_(?=x[0-9a-fA-F]{4}_)/g, '_x005F_').replace(/\r/g, '_x000D_')
}

/**
 * Prepare one raw business value for ExcelJS's XLSX serializer.
 * Apply exactly once per master cell immediately before serialization; the
 * returned text is OOXML-escaped, not an idempotent display/business value.
 */
export function prepareXlsxCell(value: unknown): SpreadsheetCell {
  if (value == null || typeof value === 'boolean') return value
  if (typeof value === 'string') return encodeText(validateText(value))
  // Never accept formula, hyperlink, rich-text or other ExcelJS value objects.
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new Error('Excel 导出只支持文本、有限数字和布尔值')
  }
  // Excel retains only 15 significant numeric digits. Preserve larger values as text.
  const digits = String(value).split(/[eE]/)[0]!.replace(/[^0-9]/g, '').replace(/^0+/, '')
  return digits.length > 15 || Math.abs(value) >= 1e15 ? String(value) : value
}

function snapshotRows(rows: SpreadsheetRows): SpreadsheetCell[][] {
  if (rows.length > MAX_ROWS) throw new Error('数据超出 Excel 行数限制，请选择 CSV 备份')
  return rows.map(row => {
    if (row.length > MAX_COLUMNS) throw new Error('数据超出 Excel 列数限制，请选择 CSV 备份')
    return row.map(prepareXlsxCell)
  })
}

async function encode(snapshot: SpreadsheetCell[][], sheetName: string): Promise<Uint8Array<ArrayBuffer>> {
  const module = await import('exceljs')
  const ExcelJS = module.default || module
  const workbook = new ExcelJS.Workbook()
  const name = sheetName.replace(/[\[\]*?:/\\\u0000-\u001F\u007F]/g, '_').replace(/^'+|'+$/g, '').trim().slice(0, 31) || 'Sheet1'
  const sheet = workbook.addWorksheet(name)
  snapshot.forEach(row => sheet.addRow(row))
  if (snapshot.length) {
    sheet.views = [{ state: 'frozen', ySplit: 1 }]
    sheet.getRow(1).font = { bold: true }
    const count = snapshot.reduce((maximum, row) => Math.max(maximum, row.length), 0)
    for (let index = 1; index <= count; index++) {
      sheet.getColumn(index).width = Math.min(48, Math.max(14,
        ...snapshot.slice(0, 200).map(row => String(row[index - 1] ?? '').length + 2)))
    }
  }
  const bytes = await workbook.xlsx.writeBuffer()
  return new Uint8Array(bytes)
}

/** First row is the header; strings remain strings, even when beginning with =, +, -, or @. */
export function createXlsxBuffer(sheetName: string, rows: SpreadsheetRows): Promise<Uint8Array<ArrayBuffer>> {
  // Capture and validate before any async import so later edits cannot change the export snapshot.
  return encode(snapshotRows(rows), sheetName)
}

/** Returns false when a departed page/session suppresses a late generated download. */
export async function downloadXlsx(
  fileName: string, sheetName: string, rows: SpreadsheetRows,
  shouldDownload: () => boolean = () => true
): Promise<boolean> {
  if (!shouldDownload()) return false
  const bytes = await createXlsxBuffer(sheetName, rows)
  if (!shouldDownload()) return false
  const safeName = fileName.replace(/[<>:"/\\|?*\u0000-\u001F]/g, '_') || 'export'
  saveAs(new Blob([bytes], { type: MIME }), /\.xlsx$/i.test(safeName) ? safeName : `${safeName}.xlsx`)
  return true
}
