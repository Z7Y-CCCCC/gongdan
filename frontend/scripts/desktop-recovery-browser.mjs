// Playwright CLI scenario. Run only against the isolated management UI fixture.
async (page) => {
  const { origin, hostname } = new URL(page.url())
  const fixture = await (await page.request.get(`${origin}/api/fixture-state`)).json()
  if (hostname !== '127.0.0.2' || fixture.fixture !== 'management-ui') throw Error('Use the isolated management fixture.')
  const checks = [], pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  const check = (result, label) => { if (!result) throw Error(label); checks.push(label) }
  const button = name => page.getByRole('button', { name, exact: true })
  const warning = page.locator('.page-load-feedback [role="alert"]')
  const screenshot = file => page.screenshot({ path: `output/playwright/desktop-recovery/${file}.png`, animations: 'disabled' })
  await page.addInitScript(() => {
    const write = Storage.prototype.setItem
    Storage.prototype.setItem = function (key, value) {
      if (key === 'inbound_print_template' && window.failPrintTemplateStorage) {
        throw new DOMException('Isolated QA storage error', 'QuotaExceededError')
      }
      return write.call(this, key, value)
    }
  })
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto(origin)
  await page.getByRole('textbox', { name: '工厂代码', exact: true }).fill('UIFIXTURE')
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill('admin')
  await page.locator('input[autocomplete="current-password"]').fill('fixture-27')
  await button('登录').click()
  await page.locator('.home-dashboard').waitFor()
  await page.locator('.workspace-loading-overlay').waitFor({ state: 'hidden' })

  const inventoryRequests = []
  let inventoryFailure = true
  await page.route('**/api/inventory-details?**', route => {
    const receiptCode = new URL(route.request().url()).searchParams.get('receiptCode')
    inventoryRequests.push(receiptCode)
    if (inventoryFailure) return route.fulfill({ status: 503, json: { message: '夹具：库存明细暂不可用' } })
    const record = { receiptCode: receiptCode || 'FIXTURE-OLD', productCode: 'P-1', productName: '示例零件',
      receiptType: '普通入库', sourceType: 'IN', operationTime: '2026-11-27T08:00:00', receiptQty: 1, changeQty: 1 }
    return route.fulfill({ json: { items: [record], total: 1 } })
  })
  await button('库存管理').click()
  await page.locator('#workspace-nav-inventory').getByRole('button', { name: '库存收发明细', exact: true }).click()
  await warning.waitFor()
  check(await page.locator('.inventory-detail-data-table').count() === 0, 'Failed first read does not appear to be an empty inventory.')
  check(await button('导出Excel').isDisabled() && await page.getByRole('combobox', { name: '每页条数' }).isEnabled(), 'Failed inventory locks exports but keeps controls usable.')
  inventoryFailure = false
  await button('重试').click()
  await page.getByText('FIXTURE-OLD', { exact: true }).waitFor()
  const filter = page.getByRole('textbox', { name: '单据编号', exact: true })
  await filter.fill('FIXTURE-NEW')
  inventoryFailure = true
  await button('查询库存明细').click()
  await warning.waitFor()
  check(await page.getByText('FIXTURE-OLD', { exact: true }).count() === 0 && await filter.inputValue() === 'FIXTURE-NEW', 'A failed filter never shows the old rows under new criteria.')
  await screenshot('01-inventory-filter-error')
  inventoryFailure = false
  await button('重试').click()
  await page.getByText('FIXTURE-NEW', { exact: true }).waitFor()
  check(inventoryRequests.join('|') === '||FIXTURE-NEW|FIXTURE-NEW', 'Inline retry resends the filter that failed.')

  let reportFailure = true
  await page.route('**/api/reports?**', route => reportFailure
    ? route.fulfill({ status: 503, json: { message: '夹具：报表暂不可用' } }) : route.continue())
  await button('报表').click()
  await page.locator('#workspace-nav-report').getByRole('button', { name: '生产报表', exact: true }).click()
  await warning.waitFor()
  check(await page.locator('.report-center-page .metric-grid').count() === 0, 'First report failure does not show default zero metrics.')
  reportFailure = false
  await button('重试').click()
  await page.locator('.report-center-page .metric-grid').waitFor()
  reportFailure = true
  await button('刷新生产报表').click()
  await warning.getByText('显示上次成功加载的数据。').waitFor()
  check(await page.locator('.report-center-page .metric-grid').isVisible(), 'An unsuccessful refresh keeps a clearly labeled previous report snapshot.')
  await button('切换深色主题').click()
  await page.setViewportSize({ width: 640, height: 850 })
  await screenshot('02-report-retained-data-dark')

  await page.setViewportSize({ width: 1280, height: 900 })
  await page.locator('#workspace-nav-inventory').getByRole('button', { name: '入库单', exact: true }).click()
  const opener = button('编辑打印模板')
  await opener.click()
  const dialog = page.getByRole('dialog', { name: '编辑入库打印模板' })
  await dialog.waitFor()
  await dialog.getByRole('textbox', { name: '模板名称' }).fill('隔离夹具草稿')
  await page.evaluate(() => { window.failPrintTemplateStorage = true })
  await dialog.getByRole('button', { name: '保存' }).click()
  await dialog.getByRole('alert').waitFor()
  check(await dialog.getByRole('textbox', { name: '模板名称' }).inputValue() === '隔离夹具草稿' && await page.evaluate(() => localStorage.getItem('inbound_print_template') === null), 'Failed local persistence keeps the print template and unsaved edits visible.')
  await screenshot('03-inbound-print-save-error')
  await page.evaluate(() => { window.failPrintTemplateStorage = false })
  await dialog.getByRole('button', { name: '保存' }).click()
  await dialog.waitFor({ state: 'hidden' })
  check(await opener.evaluate(el => el === document.activeElement), 'Successful print-template save returns keyboard focus.')
  await opener.click()
  check(await dialog.getByRole('textbox', { name: '模板名称' }).inputValue() === '隔离夹具草稿', 'Reopening reads the confirmed stored template.')
  await page.keyboard.press('Escape')
  await dialog.waitFor({ state: 'hidden' })
  check(await opener.evaluate(el => el === document.activeElement), 'Escape closes the print template and returns keyboard focus.')
  check(pageErrors.length === 0, `Browser errors: ${pageErrors.join('; ')}`)
  return { passed: checks.length, checks }
}
