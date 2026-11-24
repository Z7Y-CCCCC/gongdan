import { describe, expect, it } from 'vitest'
import { parseCsvRows } from '../csv'

describe('complete strict CSV parsing', () => {
  it('round-trips commas, escaped quotes, CRLF and significant quoted whitespace', () => {
    const values = ['a,b', 'x"y', 'first\r\nsecond', '  keep spaces  ', '']
    const csv = values.map(value => `"${value.replace(/"/g, '""')}"`).join(',')
    expect(parseCsvRows(csv)).toEqual([values])
  })
  it('accepts a UTF-8 BOM, unquoted whitespace, trailing empty fields and final CRLF', () => {
    expect(parseCsvRows('\uFEFF名称,备注\r\n 件 ,\r\n')).toEqual([['名称', '备注'], ['件', '']])
  })
  it.each(['名称,备注\n正常,第一行\n"未闭合,后面的行', '名称,备注\n正常,第一行\nna"me,x', '名称,备注\n正常,第一行\n"name"suffix,x'])
    ('rejects an entire malformed input instead of yielding an earlier writable prefix: %s', input => {
      expect(() => parseCsvRows(input)).toThrow(/CSV 第 .* 行/)
    })
  it('does not create a phantom row after a final newline, and preserves a quoted empty field', () => {
    expect(parseCsvRows('')).toEqual([])
    expect(parseCsvRows('""')).toEqual([['']])
    expect(parseCsvRows('a,b\n')).toEqual([['a', 'b']])
  })
})
