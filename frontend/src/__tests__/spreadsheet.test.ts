import { beforeEach, describe, expect, it, vi } from 'vitest'
import ExcelJS from 'exceljs'
import { createXlsxBuffer, downloadXlsx } from '../utils/spreadsheet'
import { parseCsvRows } from '../utils/csv'

const save = vi.hoisted(() => vi.fn())
vi.mock('file-saver', () => ({ saveAs: save }))
beforeEach(() => save.mockReset())

describe('safe spreadsheet output with intact business values', () => {
  it('roundtrips formula-like strings as text without formulas or hyperlinks', async () => {
    const text = ['=1+1', '+SUM(A1)', '-text', '@value', "'original", '  保存  ', 'http://example.invalid/', 'line\nnext',
      '_x0041_', '_x00ff_', '_x005F_x0041_', 'first\r\nsecond\rthird', '\t保存\t', '业务😀文本']
    const bytes = await createXlsxBuffer('业务数据', [['名称'], ...text.map(value => [value])])
    const workbook = new ExcelJS.Workbook()
    await workbook.xlsx.load(bytes as any)
    text.forEach((value, index) => {
      const cell = workbook.worksheets[0]!.getCell(index + 2, 1)
      expect(cell.type).toBe(ExcelJS.ValueType.String)
      expect(cell.value).toBe(value)
      expect(cell.formula).toBeUndefined()
      expect(cell.hyperlink).toBeUndefined()
    })
  })

  it('keeps actual negative numbers numeric and long numbers lossless', async () => {
    const bytes = await createXlsxBuffer('数量', [['数量'], [-3], [1.25], [9007199254740991]])
    const workbook = new ExcelJS.Workbook(); await workbook.xlsx.load(bytes as any)
    expect(workbook.worksheets[0]!.getCell('A2').value).toBe(-3)
    expect(workbook.worksheets[0]!.getCell('A3').value).toBe(1.25)
    expect(workbook.worksheets[0]!.getCell('A4').value).toBe('9007199254740991')
  })

  it('rejects formula objects and unrepresentable text instead of modifying original data', () => {
    expect(() => createXlsxBuffer('表', [[{ formula: '1+1' } as any]])).toThrow()
    expect(() => createXlsxBuffer('表', [['invalid\u0000text']])).toThrow()
    expect(() => createXlsxBuffer('表', [[Infinity]])).toThrow()
    expect(() => createXlsxBuffer('表', [['invalid\u007Ftext']])).toThrow(/CSV/)
    expect(() => createXlsxBuffer('表', [['invalid\uFFFEtext']])).toThrow(/CSV/)
    expect(() => createXlsxBuffer('表', [['invalid\uD800text']])).toThrow(/Unicode/)
    expect(() => createXlsxBuffer('表', [['invalid\uDC00text']])).toThrow(/Unicode/)
  })

  it('rejects Excel format limits before a download instead of silently truncating', () => {
    expect(() => createXlsxBuffer('表', [['x'.repeat(32_768)]])).toThrow(/32767/)
    expect(() => createXlsxBuffer('表', [new Array(16_385).fill('')])).toThrow(/列数/)
    expect(() => createXlsxBuffer('表', new Array(1_048_577))).toThrow(/行数/)
    expect(save).not.toHaveBeenCalled()
  })

  it('captures a lossless immutable snapshot before the asynchronous Excel import', async () => {
    const rows = [['名称'], ['=original']]
    const pending = createXlsxBuffer('表', rows)
    rows[1]![0] = 'changed'
    rows.push(['late row'])
    const workbook = new ExcelJS.Workbook(); await workbook.xlsx.load(await pending as any)
    expect(workbook.worksheets[0]!.getCell('A2').value).toBe('=original')
    expect(workbook.worksheets[0]!.rowCount).toBe(2)
  })

  it('does not generate or download a file for an already expired caller', async () => {
    expect(await downloadXlsx('业务', '表', [[{ formula: '1+1' } as any]], () => false)).toBe(false)
    expect(save).not.toHaveBeenCalled()
  })

  it('suppresses a late download after its session/page changes', async () => {
    let current = true
    const pending = downloadXlsx('export', '表', [['名称'], ['保存']], () => current)
    current = false
    expect(await pending).toBe(false)
    expect(save).not.toHaveBeenCalled()
  })

  it('downloads exactly one actual XLSX when the caller remains current', async () => {
    expect(await downloadXlsx('业务', '表', [['名称'], ['=1+1']])).toBe(true)
    expect(save).toHaveBeenCalledTimes(1)
    expect(save.mock.calls[0]![0].type).toBe('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
    expect(save.mock.calls[0]![1]).toBe('业务.xlsx')
  })

  it('preserves whitespace for export parsing without changing import defaults', () => {
    const csv = '账号,姓名\n  account  ,  姓名  \n'
    expect(parseCsvRows(csv)[1]).toEqual(['account', '姓名'])
    expect(parseCsvRows(csv, { preserveWhitespace: true })[1]).toEqual(['  account  ', '  姓名  '])
  })
})
