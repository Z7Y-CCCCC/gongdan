// Manual production-H5 QA; deliberately excluded from the unit-test runner.
// Start browser-fixture.mjs --seed-orders --seed-rejected, open its loopback
// URL at #/pages/board/index in a fresh playwright-cli session, then run-code
// --filename mobile/test/ui-browser-scenario.mjs from the repository root.
// Create output/playwright/mobile-ui first. Never run this against real data.
async (page) => {
  if (new URL(page.url()).hostname !== '127.0.0.2') throw new Error('Use only the isolated mobile fixture')
  const fixtureProbe = await page.request.get(`${new URL(page.url()).origin}/api/fixture-state`)
  const fixtureState = await fixtureProbe.json()
  if (!fixtureProbe.ok() || !Array.isArray(fixtureState.requests) || !Number.isInteger(fixtureState.fieldWriteCount)) throw new Error('Not a supported isolated fixture')
  await page.setViewportSize({ width: 390, height: 844 })
  const shots = []
  const capture = async name => {
    const filename = `output/playwright/mobile-ui/${name}.png`
    await page.screenshot({ path: filename, animations: 'disabled' })
    shots.push(filename)
  }
  const barBounds = async expected => {
    const result = await page.evaluate(() => {
      const bars = [...document.querySelectorAll('.mobile-tabbar')].filter(element => element.getBoundingClientRect().height > 0)
      const bounds = bars[0]?.getBoundingClientRect()
      return { count: bars.length, bottom: bounds?.bottom, height: window.innerHeight }
    })
    if (result.count !== expected || expected && Math.abs(result.bottom - result.height) > 1) throw new Error(JSON.stringify(result))
  }
  const iconTargets = async () => {
    const bad = await page.locator('button.icon-action').evaluateAll(buttons => buttons.filter(button => {
      const box = button.getBoundingClientRect()
      return box.height > 0 && (!button.getAttribute('aria-label') || box.width < 43.9 || box.height < 43.9)
    }).map(button => ({ name: button.getAttribute('aria-label'), width: button.getBoundingClientRect().width, height: button.getBoundingClientRect().height })))
    if (bad.length) throw new Error(`Invalid icon actions: ${JSON.stringify(bad)}`)
  }
  if (page.url().includes('/pages/bind/index')) {
    await capture('login')
    await page.getByPlaceholder('请输入工厂代码').fill('QA')
    await page.getByPlaceholder('请输入工号/手机号').fill('worker')
    await page.getByPlaceholder('请输入登录密码').fill('FixturePassword')
    await page.getByRole('button', { name: '登录', exact: true }).click()
  }
  await page.getByRole('button', { name: '刷新看板', exact: true }).waitFor()
  await page.getByText('最近 5 / 共 42 工单 · 指标全量', { exact: true }).waitFor()
  await barBounds(1)
  await iconTargets()
  await capture('board')

  await page.getByRole('tab', { name: '任务', exact: true }).click()
  await page.getByText(/已查\s*20\s*\/\s*41/).waitFor()
  await iconTargets()
  await capture('tasks')
  await page.getByRole('button', { name: '加载更多工单', exact: true }).click()
  await page.getByText(/已查\s*40\s*\/\s*41/).waitFor()
  await capture('tasks-more')

  await page.getByRole('tab', { name: '工单', exact: true }).click()
  await page.locator('button.order-card').first().waitFor()
  await capture('orders')
  await page.getByRole('button', { name: '展开工单搜索', exact: true }).click()
  await page.getByPlaceholder('工单号 / 产品').fill('后页专用零件')
  await page.getByRole('button', { name: '搜索工单', exact: true }).click()
  await page.getByRole('button', { name: 'QA-001，查看工单', exact: true }).waitFor()
  await capture('orders-search')
  await page.getByRole('button', { name: '清空并收起搜索', exact: true }).click()
  await page.getByRole('button', { name: 'QA-042，查看工单', exact: true }).click()
  await page.getByRole('button', { name: '返回工单列表', exact: true }).waitFor()
  await barBounds(0)
  await capture('order-detail')
  await page.getByRole('button', { name: '装配报工', exact: true }).click()
  await page.getByRole('button', { name: '关闭报工', exact: true }).waitFor()
  await page.getByLabel('报工数', { exact: true }).fill('5')
  await page.getByLabel('不良数', { exact: true }).fill('1')
  await page.getByLabel('不良原因', { exact: true }).fill('测试次品')
  await page.getByRole('button', { name: '选择生产人员', exact: true }).click()
  await page.getByRole('dialog', { name: '选择生产人员', exact: true }).waitFor()
  await page.keyboard.press('Escape')
  await page.getByRole('dialog', { name: '选择生产人员', exact: true }).waitFor({ state: 'hidden' })
  await page.getByRole('button', { name: '选择生产人员', exact: true }).click()
  await capture('report-picker')
  await page.getByRole('button', { name: '关闭人员选择', exact: true }).click()
  await iconTargets()
  await capture('report-form')
  await page.getByRole('button', { name: '提交', exact: true }).click()
  await page.getByRole('button', { name: '刷新报工记录', exact: true }).waitFor()
  await page.getByText('待审批', { exact: true }).first().waitFor()
  await capture('reports-pending')
  await page.getByRole('button', { name: '驳回', exact: true }).click()
  await page.getByText('请核对实际良品数', { exact: true }).waitFor()
  await capture('reports-rejected')

  await page.getByRole('tab', { name: '报工', exact: true }).click()
  await page.getByRole('button', { name: '刷新报工统计', exact: true }).waitFor()
  await capture('home')
  await page.getByRole('button', { name: '现场事件', exact: true }).click()
  const stateUrl = `${new URL(page.url()).origin}/api/fixture-state`
  const before = await (await page.request.get(stateUrl)).json()
  const eventTitle = `现场事件 ${before.fieldWriteCount + 1}`
  await page.getByPlaceholder('例如：A 线贴标机停机').fill(eventTitle)
  await page.getByRole('button', { name: '上报', exact: true }).click()
  await page.getByText(eventTitle, { exact: true }).waitFor()
  const after = await (await page.request.get(stateUrl)).json()
  if (after.fieldWriteCount !== before.fieldWriteCount + 1) throw new Error('Field event was not written exactly once')
  await capture('field-events')
  await page.getByRole('button', { name: '返回工作台', exact: true }).click()
  await page.getByRole('tab', { name: '看板', exact: true }).click()
  await page.getByText('最近 5 / 共 42 工单 · 指标全量', { exact: true }).waitFor()
  await barBounds(1)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  const motion = await page.locator('button.icon-action').first().evaluate(button => getComputedStyle(button).transitionDuration)
  if (motion.split(',').some(value => parseFloat(value) > 0.001)) throw new Error(`Reduced motion ignored: ${motion}`)
  return { shots, motion, viewport: page.viewportSize() }
}
