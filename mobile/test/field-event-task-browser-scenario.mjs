// Playwright CLI QA, production H5, isolated browser-fixture.mjs --seed-orders.
async (page) => {
  const { origin, hostname } = new URL(page.url())
  const state = async () => (await (await page.request.get(origin + '/api/fixture-state')).json())
  if (hostname !== '127.0.0.2' || (await state()).fixture !== 'mobile-ui') throw new Error('Use only the isolated fixture')
  const passed = [], errors = []
  const check = (result, name) => { if (!result) throw Error(name); passed.push(name) }
  const button = name => page.getByRole('button', { name, exact: true })
  const tab = name => page.getByRole('tab', { name, exact: true })
  const capture = name => page.screenshot({ path: 'output/playwright/mobile-ui-followup/' + name + '.png', animations: 'disabled' })
  page.on('pageerror', error => errors.push(error.message))
  await page.setViewportSize({ width: 390, height: 844 })
  const login = button('登录')
  const first = button('装配，QA-042，报工')
  await login.or(first).first().waitFor()
  if (await login.isVisible()) {
    await page.getByPlaceholder('请输入工厂代码').fill('QA')
    await page.getByPlaceholder('请输入工号/手机号').fill('worker')
    await page.getByPlaceholder('请输入登录密码').fill('FixturePassword')
    await login.click()
  }
  let permissions = 0
  const reporterApi = '**/api/mobile/work-orders/42/reporters?*'
  const permission = async (clicked, other, scope) => {
    let release
    const wait = new Promise(resolve => { release = resolve })
    await page.route(reporterApi, async route => {
      permissions++
      await wait
      await route.fulfill({ status: 503, json: { message: '示例：权限暂时无法确认' } })
    }, { times: 1 })
    const before = permissions
    await clicked.click()
    await page.getByText('确认权限中…', { exact: true }).waitFor()
    check(await clicked.isDisabled() && await clicked.getAttribute('aria-busy') === 'true', scope + ' reports a disabled, busy action')
    if (other) check(await other.isDisabled(), 'Task page locks other report actions while checking')
    check(permissions === before + 1, scope + ' issues one permission request')
    await capture(scope + '-pending')
    release()
    await page.getByRole('alert').getByText('示例：权限暂时无法确认', { exact: true }).waitFor()
    check(await clicked.isEnabled(), scope + ' reenables action and shows persistent failure')
  }
  await permission(first, button('装配，QA-041，报工'), 'task')
  await tab('工单').click()
  await button('QA-042，查看工单').click()
  await permission(button('装配报工'), null, 'detail')
  await button('返回工单列表').click()
  await tab('报工').click()
  await button('现场事件').click()
  const title = page.getByRole('textbox', { name: '事件标题' })
  await title.fill('现场事件网络核对')
  const before = (await state()).fieldWriteCount
  await page.route('**/api/mobile/field-events', async route => {
    if (route.request().method() !== 'POST') return route.continue()
    const response = await route.fetch()
    if (!response.ok()) throw Error('Isolated fixture POST must commit')
    await route.abort('failed')
  }, { times: 1 })
  await button('上报').click()
  await page.getByText('暂未确认上报结果', { exact: true }).waitFor()
  check((await state()).fieldWriteCount === before + 1 && await title.inputValue() === '现场事件网络核对' && await title.isDisabled(), 'Lost response keeps submitted draft locked')
  check(await button('重新上报').count() === 0 && await button('确认未上报，解除限制').count() === 0, 'Uncertain result forbids blind retry')
  await capture('field-event-unconfirmed')
  await button('返回工作台').click()
  await button('现场事件').click()
  await page.getByText('暂未确认上报结果', { exact: true }).waitFor()
  check(await title.inputValue() === '现场事件网络核对', 'Unresolved event persists across navigation')
  await button('核对最近上报').click()
  await page.getByText('记录已核对，不必重复上报', { exact: true }).waitFor()
  await page.getByText('已核对本次上报', { exact: true }).waitFor()
  check((await state()).fieldWriteCount === before + 1 && await title.inputValue() === '' && await title.isEnabled(), 'Exact record match clears draft without another write')
  await capture('field-event-resolved')
  if (errors.length) throw Error(errors.join('; '))
  return { passed: passed.length, checks: passed }
}
