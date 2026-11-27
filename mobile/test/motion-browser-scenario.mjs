// Run using playwright-cli run-code from the repository root on the isolated
// browser fixture (--seed-orders --mixed-recent); optional old H5 build via --dist.
async (page) => {
  const { origin, hostname } = new URL(page.url())
  const state = async () => (await (await page.request.get(`${origin}/api/fixture-state`)).json())
  if (hostname !== '127.0.0.2' || (await state()).fixture !== 'mobile-ui') throw Error('Use the isolated mobile fixture.')
  const path = 'output/playwright/ui-motion/'
  const checks = [], errors = []
  page.on('pageerror', error => errors.push(error.message))
  const check = (result, key) => { if (!result) throw Error(key); checks.push(key) }
  const screenshot = name => page.screenshot({ path: `${path}${name}.png`, animations: 'disabled' })
  const button = name => page.getByRole('button', { name, exact: true })
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${origin}/#/pages/board/index`)
  // Login may have redirected via hash history; hard-load the board so uni runs its entry hooks.
  await page.reload()
  const summary = page.getByText('最近 5 / 共 42 工单 · 指标全量', { exact: true })
  const factory = page.getByPlaceholder('请输入工厂代码')
  await summary.or(factory).first().waitFor()
  if (await factory.isVisible()) {
    await factory.fill('QA')
    await page.getByPlaceholder('请输入工号/手机号').fill('worker')
    await page.getByPlaceholder('请输入登录密码').fill('FixturePassword')
    await button('登录').click()
  }
  await summary.waitFor()
  const filters = page.getByRole('group', { name: '筛选最近工单' })
  const baseline = !await filters.isVisible()
  if (!baseline && await button('全部').getAttribute('aria-pressed') !== 'true') await button('全部').click()
  await screenshot(baseline ? 'mobile-board-before' : 'mobile-board-after')
  if (!baseline) {
    await button('其他').click()
    const rows = page.locator('.board-orders')
    await page.getByText('最近 5 条中符合 1 条', { exact: true }).waitFor()
    check(await summary.isVisible() && await rows.getByText('QA-041', { exact: true }).isVisible() && await rows.locator('.order-card').count() === 1, 'Filtering changes only the recent subset; the full aggregate stays visible.')
    check(await button('其他').getAttribute('aria-pressed') === 'true', 'The selected filter is named and announced.')
    await screenshot('mobile-board-filtered')
    const dashboardApi = '**/api/mobile/dashboard?*'
    await page.route(dashboardApi, route => route.fulfill({ status: 503, json: { message: '示例：看板暂时无法刷新' } }), { times: 1 })
    await button('刷新看板').click()
    await page.getByText('仍显示上次数据', { exact: true }).waitFor()
    check(await rows.locator('.order-card').count() === 1, 'Failed refresh preserves the selected filter and last data.')
    await button('重试看板').click()
    await page.getByText('仍显示上次数据', { exact: true }).waitFor({ state: 'hidden' })
    await button('执行中').click()
    await page.getByText('最近 5 条中符合 4 条', { exact: true }).waitFor()
    check(await rows.evaluate(el => getComputedStyle(el).animationName.includes('board-filter-change')), 'The recent-order results use scoped filter motion.')
  }

  await page.getByRole('tab', { name: '工单', exact: true }).click()
  await button('QA-042，查看工单').click()
  await button('装配报工').click()
  const quantity = page.getByLabel('报工数', { exact: true })
  await quantity.fill('5')
  const opener = button('选择生产人员')
  const sheet = page.getByRole('dialog', { name: '选择生产人员', exact: true })
  if (!baseline) {
    await page.evaluate(() => {
      document.addEventListener('transitionrun', event => {
        if (event.target.matches?.('.reporter-sheet') && event.propertyName === 'transform') document.body.dataset.sheetMoved = 'yes'
      })
    })
  }
  await opener.click()
  await sheet.waitFor()
  if (!baseline) {
    await page.waitForFunction(() => document.body.dataset.sheetMoved === 'yes')
    check(await button('关闭人员选择').evaluate(el => el === document.activeElement), 'True sheet translation starts and focus enters the sheet.')
  }
  await screenshot(baseline ? 'mobile-sheet-before' : 'mobile-sheet-after')
  await page.keyboard.press('Escape')
  await sheet.waitFor({ state: 'hidden' })
  if (baseline) return { baseline, errors }
  check(await opener.evaluate(el => el === document.activeElement), 'Escape restores focus to the report form.')

  const write = '**/api/mobile/reports'
  const feedback = page.locator('.report-submit-feedback')
  const before = await state()
  let rejected = 0
  await page.route(write, async route => {
    if (route.request().method() !== 'POST') return route.continue()
    rejected++
    await new Promise(resolve => setTimeout(resolve, 1500))
    await route.fulfill({ status: 422, json: { message: '示例：请核对填写数量' } })
  })
  await button('提交').click()
  await feedback.getByText('正在提交报工…', { exact: true }).waitFor()
  check(await button('提交中…').isDisabled() && await button('提交继续').isDisabled(), 'Pending submission locks both write buttons.')
  await screenshot('mobile-report-submitting')
  await feedback.getByText('提交失败', { exact: true }).waitFor()
  check(await quantity.inputValue() === '5' && (await state()).writeCount === before.writeCount && rejected === 1, 'Definite rejection retains the draft without creating a record.')
  await screenshot('mobile-report-failed')
  await page.unroute(write)
  await button('重新提交').click()
  await feedback.getByText('提交成功', { exact: true }).waitFor()
  check((await state()).writeCount === before.writeCount + 1, 'Retry creates exactly one record.')
  check(await button('查看报工记录').evaluate(el => { const box = el.getBoundingClientRect(); return box.height >= 44 && box.top >= 0 && box.bottom <= innerHeight }), 'Success action fits in the phone viewport without scrolling.')
  await screenshot('mobile-report-success')
  await button('查看报工记录').click()
  await button('刷新报工记录').waitFor()
  await page.getByText('待审批', { exact: true }).first().waitFor()
  check(true, 'Confirmed submission opens the pending records list.')

  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.getByRole('tab', { name: '看板', exact: true }).click()
  await button('其他').click()
  check(await page.locator('.board-orders').evaluate(el => getComputedStyle(el).animationName === 'none'), 'System reduced motion suppresses filter animation.')
  check(errors.length === 0, `Browser page errors: ${errors.join('; ')}`)
  return { passed: checks.length, checks }
}
