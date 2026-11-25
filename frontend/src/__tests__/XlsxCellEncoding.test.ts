import { describe, expect, it, vi } from 'vitest'
import ExcelJS from 'exceljs'
import { createXlsxBuffer, prepareXlsxCell } from '../utils/spreadsheet'

vi.mock('file-saver', () => ({ saveAs: vi.fn() }))

type Layout = 'default export' | 'merged print layout'

async function roundtrip(layout: Layout, values: Array<string | number | boolean | null | undefined>) {
  let bytes: Uint8Array | ArrayBuffer
  if (layout === 'default export') {
    bytes = await createXlsxBuffer('业务', [['名称'], ...values.map(value => [value])])
  } else {
    const workbook = new ExcelJS.Workbook()
    const sheet = workbook.addWorksheet('打印')
    sheet.mergeCells('A1:C1')
    sheet.getCell('A1').value = '名称'
    values.forEach((value, index) => {
      sheet.mergeCells(index + 2, 1, index + 2, 3)
      sheet.getCell(index + 2, 1).value = value ?? null
      sheet.getCell(index + 2, 1).alignment = { wrapText: true }
    })
    const masters = new Set<ExcelJS.Cell>()
    sheet.eachRow(row => row.eachCell(cell => {
      // A merged slave aliases the same master: preparing it again double-escapes literals.
      const master = cell.master
      if (masters.has(master)) return
      masters.add(master)
      master.value = prepareXlsxCell(master.value) ?? null
    }))
    bytes = await workbook.xlsx.writeBuffer()
  }
  const reopened = new ExcelJS.Workbook()
  await reopened.xlsx.load(bytes as any)
  return reopened.worksheets[0]!
}

describe.each<Layout>(['default export', 'merged print layout'])('%s real XLSX cell encoding', layout => {
  it('preserves literal formulas, OOXML escapes, CR/LF, tabs and complete Unicode as text', async () => {
    const values = ['=1+1', '+SUM(A1)', '-business', '@business', "'original", '  原文  ',
      'https://example.invalid/', '_x0041_', '_x00ff_', '_x005F_x0041_',
      '_x005F__x0041__x005F_x000D_', '_x0041__x0041_', '_x000D_\r_x000A_',
      'first\r\nsecond\rthird\nfourth', '\t原文\t', '业务😀𠮷文本']
    const sheet = await roundtrip(layout, values)
    values.forEach((value, index) => {
      const cell = sheet.getCell(index + 2, 1)
      expect(cell.value).toBe(value)
      expect(cell.type).toBe(ExcelJS.ValueType.String)
      expect(cell.formula).toBeUndefined()
      expect(cell.hyperlink).toBeUndefined()
      if (layout === 'merged print layout') expect(sheet.getCell(index + 2, 3).value).toBe(value)
    })
  })

  it('preserves the maximum text length including a complete surrogate pair at the boundary', async () => {
    const values = ['a'.repeat(32_767), 'a'.repeat(32_765) + '😀']
    const sheet = await roundtrip(layout, values)
    values.forEach((value, index) => expect(sheet.getCell(index + 2, 1).value).toBe(value))
  })

  it('keeps ordinary numeric/boolean values typed but converts large or high-precision numbers to text', async () => {
    const values = [-3, 1.25, true, false, 9007199254740991, 1e15, -1e18, 1.234567890123456, 1e100]
    const sheet = await roundtrip(layout, values)
    expect(sheet.getCell('A2').value).toBe(-3)
    expect(sheet.getCell('A2').type).toBe(ExcelJS.ValueType.Number)
    expect(sheet.getCell('A3').value).toBe(1.25)
    expect(sheet.getCell('A4').value).toBe(true)
    expect(sheet.getCell('A5').value).toBe(false)
    values.slice(4).forEach((value, index) => {
      const cell = sheet.getCell(index + 6, 1)
      expect(cell.value).toBe(String(value))
      expect(cell.type).toBe(ExcelJS.ValueType.String)
    })
  })
})

describe('raw XLSX cell validation', () => {
  it.each([
    { formula: '1+1', result: 2 }, { sharedFormula: 'A1', result: 2 },
    { text: 'link', hyperlink: 'https://example.invalid/' },
    { richText: [{ text: 'business' }] }, new Date(0), ['business'], {},
    new String('business'), new Number(1), 1n, Symbol('business'), () => 'business',
    NaN, Infinity, -Infinity
  ])('rejects non-primitive or non-finite value %# without coercion', value => {
    expect(() => prepareXlsxCell(value)).toThrow(/只支持/)
    expect(() => createXlsxBuffer('业务', [[value as any]])).toThrow(/只支持/)
  })

  it.each(['\u0000', '\u0008', '\u000B', '\u000C', '\u000E', '\u001F', '\u007F', '\uFFFE', '\uFFFF'])
    ('rejects unsupported control/noncharacter %j rather than silently removing it', value => {
      expect(() => prepareXlsxCell(`left${value}right`)).toThrow(/CSV/)
    })

  it.each(['\uD800', '\uDC00', '\uD800text', '\uD800\uD800'])
    ('rejects malformed Unicode %j rather than replacing it', value => {
      expect(() => prepareXlsxCell(value)).toThrow(/Unicode/)
    })

  it('rejects text over the Excel limit before any asynchronous serializer starts', () => {
    expect(() => prepareXlsxCell('a'.repeat(32_768))).toThrow(/32767/)
    expect(() => prepareXlsxCell('a'.repeat(32_766) + '😀')).toThrow(/32767/)
    expect(() => createXlsxBuffer('业务', [['a'.repeat(32_768)]])).toThrow(/32767/)
  })

  it('preserves empty primitive values and encodes only once per raw cell', () => {
    expect(prepareXlsxCell(null)).toBeNull()
    expect(prepareXlsxCell(undefined)).toBeUndefined()
    expect(prepareXlsxCell('')).toBe('')
    expect(prepareXlsxCell('_x0041_\r')).toBe('_x005F_x0041__x000D_')
  })
})
