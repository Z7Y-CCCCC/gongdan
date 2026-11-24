import { describe, expect, it } from 'vitest'
import { bomMaterialQuantity, formatBomDecimal, normalizeBomQuantity, normalizeBomScrapRate } from '../bomDecimal'

describe('exact BOM decimals', () => {
  it.each([
    ['99999999999999.9999', '99999999999999.9999'], ['1e-4', '0.0001'], ['1.234500', '1.2345'],
    ['2.5e+3', '2500'], ['.125', '0.125'], ['+001.2500', '1.25'], [0.125, '0.125']
  ])('normalizes %s without rounding', (value, expected) => {
    expect(normalizeBomQuantity(value)).toBe(expected)
  })

  it.each(['9007199254740993', '100000000000000', '99999999999999.99999', '1e1000000',
    '1e-5', '1.23451', '1e-1000000', 'NaN', 'Infinity', '1,000', '0', '-1', ''])('rejects invalid or unrepresentable quantity %s', value => {
    expect(() => normalizeBomQuantity(value)).toThrow()
  })

  it.each([['100.0000', '100'], ['1.234500', '1.2345'], ['0', '0'], [1.5, '1.5']])('normalizes scrap %s exactly', (value, expected) => {
    expect(normalizeBomScrapRate(value)).toBe(expected)
  })
  it.each(['100.0001', '-0.1', '0.00001', 'NaN'])('rejects invalid scrap %s', value => {
    expect(() => normalizeBomScrapRate(value)).toThrow()
  })
  it('formats scientific notation as a plain decimal without Number()', () => {
    expect(formatBomDecimal('1.2345e+3')).toBe('1234.5')
    expect(formatBomDecimal('99999999999999.9999')).toBe('99999999999999.9999')
  })
  it('computes exact ceilings including sub-unit output and near-equal large numbers', () => {
    expect(bomMaterialQuantity('1', 5, '0.5')).toBe(10)
    expect(bomMaterialQuantity('99999999999999.9999', 1, '99999999999999.9998')).toBe(2)
    expect(bomMaterialQuantity(0.1, 3, 0.2)).toBe(2)
  })
  it('rejects material quantities outside the Java Integer range instead of clamping or rounding', () => {
    expect(bomMaterialQuantity('2147483647', 1, '1')).toBe(2147483647)
    expect(() => bomMaterialQuantity('2147483648', 1, '1')).toThrow('超过2147483647')
  })
})
