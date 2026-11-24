/** Exact DECIMAL(18,4) handling. Never convert a BOM decimal through binary floating point. */
type DecimalValue = string | number
const SCALE = 10000n
const MAX_QUANTITY = 999999999999999999n
const MAX_MATERIAL_QUANTITY = 2147483647n

function units(value: DecimalValue, label: string, allowZero: boolean, max = MAX_QUANTITY): bigint {
  const source = String(value).trim()
  const match = /^([+-]?)(?:(\d+)(?:\.(\d*))?|\.(\d+))(?:[eE]([+-]?\d+))?$/.exec(source)
  if (!match) throw new Error(`${label}必须为有效数字`)
  const fraction = match[3] ?? match[4] ?? ''
  const coefficient = `${match[2] ?? ''}${fraction}`.replace(/^0+/, '')
  const rangeMessage = label === '损耗率' ? '损耗率必须在0到100之间' : `${label}必须大于0`
  if (!coefficient) {
    if (allowZero) return 0n
    throw new Error(rangeMessage)
  }
  if (match[1] === '-') throw new Error(rangeMessage)
  const trailingZeros = coefficient.length - coefficient.replace(/0+$/, '').length
  const digits = coefficient.slice(0, coefficient.length - trailingZeros)
  const exponent = BigInt(match[5] ?? '0')
  const decimalScale = BigInt(fraction.length - trailingZeros) - exponent
  // Reject excess integer digits before allocating powers of ten, even for huge exponents.
  if (BigInt(digits.length) - decimalScale > 14n) {
    throw new Error(label === '损耗率' ? '损耗率必须在0到100之间' : `${label}不能超过99999999999999.9999`)
  }
  if (decimalScale > 4n) throw new Error(`${label}最多支持4位小数`)
  const result = BigInt(digits) * (10n ** (4n - decimalScale))
  if (result > max) {
    throw new Error(label === '损耗率' ? '损耗率必须在0到100之间' : `${label}不能超过99999999999999.9999`)
  }
  return result
}

function plain(value: bigint): string {
  const text = value.toString().padStart(5, '0')
  const fraction = text.slice(-4).replace(/0+$/, '')
  return `${text.slice(0, -4)}${fraction ? `.${fraction}` : ''}`
}

export function normalizeBomQuantity(value: DecimalValue, label = '产出数量'): string {
  return plain(units(value, label, false))
}

export function normalizeBomScrapRate(value: DecimalValue): string {
  return plain(units(value, '损耗率', true, 100n * SCALE))
}

export function formatBomDecimal(value: DecimalValue): string {
  try { return plain(units(value, '数量', true)) }
  catch { return String(value) }
}

/** Existing material rows are Java Integer quantities; ceil the exact ratio before conversion. */
export function bomMaterialQuantity(quantity: DecimalValue, plannedQuantity: number, outputQuantity: DecimalValue): number {
  if (!Number.isInteger(plannedQuantity) || plannedQuantity <= 0 || plannedQuantity > Number(MAX_MATERIAL_QUANTITY)) {
    throw new Error('计划数量必须为1到2147483647之间的整数')
  }
  const numerator = units(quantity, '子件用量', false) * BigInt(plannedQuantity)
  const denominator = units(outputQuantity, '产出数量', false)
  const required = (numerator + denominator - 1n) / denominator
  if (required > MAX_MATERIAL_QUANTITY) throw new Error('BOM换算用料数量超过2147483647，请调整计划数量或BOM')
  // This conversion is exact: required has already been bounded to the Java Integer range.
  return Number(required)
}
