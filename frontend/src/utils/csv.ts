/** Parse the complete CSV before any importer starts writing business records. */
export function parseCsvRows(input: string): string[][] {
  const text = input.replace(/^\uFEFF/, '')
  const rows: string[][] = []
  let row: string[] = []
  let field = ''
  let quoted = false
  let state: 'field' | 'quoted' | 'closed' = 'field'
  let line = 1
  const finishField = () => {
    row.push(quoted ? field : field.trim())
    field = ''
    quoted = false
    state = 'field'
  }
  const finishRow = () => {
    finishField()
    rows.push(row)
    row = []
  }
  for (let index = 0; index < text.length; index++) {
    const char = text[index]
    if (state === 'quoted') {
      if (char === '"') {
        if (text[index + 1] === '"') { field += '"'; index++ }
        else state = 'closed'
      } else {
        field += char
        if (char === '\n' || char === '\r' && text[index + 1] !== '\n') line++
      }
      continue
    }
    if (char === ',') { finishField(); continue }
    if (char === '\n' || char === '\r') {
      if (char === '\r' && text[index + 1] === '\n') index++
      finishRow()
      line++
      continue
    }
    if (state === 'closed') {
      if (/\s/.test(char)) continue
      throw new Error(`CSV 第 ${line} 行引号后必须是分隔符`)
    }
    if (char === '"') {
      if (field.trim()) throw new Error(`CSV 第 ${line} 行引号位置不正确`)
      field = ''
      quoted = true
      state = 'quoted'
    } else field += char
  }
  if (state === 'quoted') throw new Error(`CSV 第 ${line} 行引号未闭合`)
  if (field || row.length || state === 'closed') finishRow()
  return rows
}
