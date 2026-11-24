export function missingRequiredCustomField<T extends { id: number; name: string; required?: boolean }>(
  fields: T[], values: Array<{ fieldId?: number; fieldName?: string; value: unknown }>,
  previousValues?: Array<{ fieldId?: number; fieldName?: string; value: unknown }>
): T | undefined {
  return fields.find(field => {
    if (!field.required) return false
    const find = (items: typeof values) => items.find(item => item.fieldId === field.id || !item.fieldId && item.fieldName === field.name)?.value
    const empty = (value: unknown) => value === null || value === undefined || typeof value === 'string' && !value.trim()
      || Array.isArray(value) && !value.some(item => item !== null && item !== undefined && String(item).trim())
    if (!empty(find(values))) return false
    // Legacy orders may already lack a required value; do not block unrelated edits.
    return !previousValues || !empty(find(previousValues))
  })
}
