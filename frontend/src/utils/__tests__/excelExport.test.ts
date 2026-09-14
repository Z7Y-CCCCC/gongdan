import ExcelJS from 'exceljs'
import { describe, expect, it } from 'vitest'

describe('Excel export dependency compatibility', () => {
  it('round-trips a Chinese worksheet with conditional formatting', async () => {
    const workbook = new ExcelJS.Workbook()
    const sheet = workbook.addWorksheet('工单报表')
    sheet.addRow(['工单', '数量'])
    sheet.addRow(['GD001', 12])
    sheet.addConditionalFormatting({
      ref: 'B2:B10',
      rules: [{
        type: 'dataBar',
        priority: 1,
        cfvo: [{ type: 'min' }, { type: 'max' }],
        gradient: true,
      }],
    })

    const data = await workbook.xlsx.writeBuffer()
    const restored = new ExcelJS.Workbook()
    await restored.xlsx.load(data)

    expect(restored.getWorksheet('工单报表')?.getCell('A2').value).toBe('GD001')
    expect(restored.getWorksheet('工单报表')?.getCell('B2').value).toBe(12)
  })
})
