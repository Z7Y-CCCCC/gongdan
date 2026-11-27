// Production H5 + isolated mobile fixture (--seed-orders).
// Simulate a committed POST whose response is lost before reaching the app.
async (page) => {
  const { hostname, origin } = new URL(page.url())
  const fixture = async () => (await (await page.request.get(`${origin}/api/fixture-state`)).json())
  if (hostname !== '127.0.0.2' || (await fixture()).fixture !== 'mobile-ui') throw new Error('Use the isolated mobile fixture')
  const button = name => page.getByRole('button', { name, exact: true })
  const feedback = page.locator('.report-submit-feedback')
  const checks = []
  const check = (ok, description) => { if (!ok) throw new Error(description); checks.push(description) }
  const screenshot = name => page.screenshot({ path: `output/playwright/mobile-stage3/${name}.png`, animations: 'disabled' })
  const reportPath = '/api/mobile/reports'
  const writingRoute = '**' + reportPath
  await page.setViewportSize({ width: 320, height: 650 })
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await page.goto(`${origin}/#/pages/work-orders/index`)
  const login = button('登录')
  await login.or(button('QA-042，查看工单')).first().waitFor()
  if (await login.isVisible()) {
    await page.getByPlaceholder('请输入工厂代码').fill('QA')
    await page.getByPlaceholder('请输入工号/手机号').fill('worker')
    await page.getByPlaceholder('请输入登录密码').fill('FixturePassword')
    await login.click()
  }
  await button('QA-042，查看工单').click()
  await button('装配报工').click()
  const formUrl = page.url()
  const quantity = page.getByLabel('报工数', { exact: true })
  const initial = await fixture()
  await quantity.fill('7')
  await page.route(writingRoute, async route => {
    const response = await route.fetch()
    if (!response.ok()) throw new Error('The isolated fixture should commit the write')
    await route.abort('failed')
  }, { times: 1 })
  await button('提交').click()
  await feedback.getByText('暂未确认提交结果', { exact: true }).waitFor()
  check((await fixture()).writeCount === initial.writeCount + 1, 'The isolated server created one record before the lost response.')
  check(await quantity.inputValue() === '7' && await quantity.isDisabled() && await button('重新提交').count() === 0,
    'Uncertain outcome retains draft and blocks a blind retry.')
  check(await button('查看报工记录').evaluate(el => { const r = el.getBoundingClientRect(); return r.height >= 44 && r.bottom <= innerHeight }), 'Review CTA stays visible at 320px.')
  await screenshot('report-uncertain')
  await button('查看报工记录').click()
  await page.getByRole('region', { name: '待核对报工范围' }).waitFor()
  await page.getByText('QA-042', { exact: true }).first().waitFor()
  check((await fixture()).requests.some(entry => entry.method === 'GET' && entry.path === reportPath && new URLSearchParams(entry.query).get('workOrderId') === '42'),
    'Verification opens the exact work order, without a pending-only assumption.')
  await screenshot('report-review-scope')
  await page.goto(formUrl)
  // The uni-app H5 router can restore the parent of a previous navigateTo
  // entry after switchTab. Follow the production report entry if it did.
  const workOrderEntry = button('装配报工')
  await feedback.or(workOrderEntry).first().waitFor()
  if (await workOrderEntry.isVisible()) await workOrderEntry.click()
  await feedback.getByText('暂未确认提交结果', { exact: true }).waitFor()
  await button('已找到记录').click()
  await feedback.getByText('记录已核对', { exact: true }).waitFor()
  check((await fixture()).writeCount === initial.writeCount + 1, 'Manual confirmation does not send another POST.')
  await screenshot('report-review-resolved')
  return { passed: checks.length, checks }
}
