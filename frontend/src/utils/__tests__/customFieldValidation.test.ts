import { describe, expect, it } from 'vitest'
import { missingRequiredCustomField } from '../customFieldValidation'
const field = { id: 1, name: '批次', required: true }
describe('custom field required validation', () => {
  it.each([undefined, null, '', '  ', [], ['', ' ']])('rejects an empty required value: %j', value => {
    expect(missingRequiredCustomField([field], [{ fieldId: 1, value }])).toBe(field)
  })
  it.each([0, false, '批次1', ['批次1']])('accepts a populated value without treating zero or false as empty: %j', value => {
    expect(missingRequiredCustomField([field], [{ fieldId: 1, value }])).toBeUndefined()
  })
  it('supports a legacy name-only value but does not match a foreign field ID by name', () => {
    expect(missingRequiredCustomField([field], [{ fieldName: '批次', value: 'legacy' }])).toBeUndefined()
    expect(missingRequiredCustomField([field], [{ fieldId: 9, fieldName: '批次', value: 'foreign' }])).toBe(field)
  })
  it('allows an already-missing legacy order value but does not allow clearing a populated required field', () => {
    expect(missingRequiredCustomField([field], [{ fieldId: 1, value: '' }], [])).toBeUndefined()
    expect(missingRequiredCustomField([field], [{ fieldId: 1, value: '' }], [{ fieldId: 1, value: 'old' }])).toBe(field)
  })
})
