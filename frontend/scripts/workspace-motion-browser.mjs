// Playwright CLI scenario. Run only on ui-browser-fixture.mjs --overview-demo.
async (page) => {
  const { origin, hostname } = new URL(page.url())
  const response = await page.request.get(`${origin}/api/fixture-state`)
  const fixture = await response.json()
  if (hostname !== '127.0.0.2' || fixture.fixture !== 'management-ui' || !fixture.overviewDemo) throw Error('Use the isolated overview fixture.')
  const checks = [], errors = []
  page.on('pageerror', error => errors.push(error.message))
  const check = (result, key) => { if (!result) throw Error(key); checks.push(key) }
  const screenshot = name => page.screenshot({ path: `output/playwright/ui-motion/${name}.png`, animations: 'disabled' })
  const button = name => page.getByRole('button', { name, exact: true })
  const animationCount = name => page.evaluate(n => window.motionEvents.filter(event => event === n).length, name)
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.evaluate(() => { localStorage.clear(); sessionStorage.clear() })
  await page.addInitScript(() => {
    if (window.motionEvents) return
    window.motionEvents = []
    document.addEventListener('animationstart', event => window.motionEvents.push(event.animationName))
    document.addEventListener('transitionrun', event => {
      if (event.target.matches?.('.workspace-tab.active') && event.pseudoElement === '::after') window.motionEvents.push('active-tab-line')
    })
  })
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto(origin)
  const login = page.locator('.login-box')
  const username = login.getByRole('textbox', { name: '用户名', exact: true })
  const password = login.getByRole('textbox', { name: '密码', exact: true })
  await username.waitFor()
  const baseline = (await login.evaluate(el => getComputedStyle(el).animationName)) !== 'login-card-arrival'
  if (!baseline) await page.waitForFunction(() => window.motionEvents.includes('login-card-arrival'))
  await screenshot(baseline ? 'desktop-login-before' : 'desktop-login-after')
  if (!baseline) {
    check(await animationCount('login-card-arrival') === 1, 'A fresh sign-in card animates only once.')
    await login.getByRole('tab', { name: '手机号', exact: true }).click()
    await login.getByRole('tab', { name: '工厂账号', exact: true }).click()
  }
  await login.getByRole('textbox', { name: '工厂代码', exact: true }).fill('UIFIXTURE')
  await username.fill('admin')
  await password.fill('fixture-27')
  const signIn = '**/api/auth/login'
  if (!baseline) {
    await page.route(signIn, route => route.fulfill({ status: 401, json: { message: '工厂代码、用户名或密码不正确' } }), { times: 1 })
    await button('登录').click()
    await login.getByRole('alert').waitFor()
    check(await username.inputValue() === 'admin' && await password.inputValue() === 'fixture-27', 'Rejected sign-in preserves credentials for correction.')
    check(await animationCount('login-card-arrival') === 1 && await animationCount('workspace-sign-in-arrival') === 0, 'Changing mode and retrying does not replay the entrance.')
    await page.waitForTimeout(450)
  }
  await button('登录').click()
  await page.locator('.home-dashboard').waitFor()
  await page.locator('.workspace-loading-overlay').waitFor({ state: 'hidden' })
  if (!baseline) {
    await page.waitForFunction(() => window.motionEvents.includes('workspace-sign-in-arrival'))
    check(await animationCount('workspace-sign-in-arrival') === 1, 'Successful interactive login animates the ready workspace once.')
    check(await page.locator('main.admin-shell').evaluate(el => el === document.activeElement && !el.inert), 'Successful login puts focus on the ready workspace.')
  }
  await page.getByText('传动座', { exact: true }).first().waitFor()
  await page.waitForTimeout(480)
  await button('基础数据').click()
  const unit = page.locator('.sidebar').getByRole('button', { name: '单位', exact: true })
  await unit.click()
  const fields = button('字段配置')
  await fields.waitFor()
  const tab = page.getByRole('tab', { name: '单位', exact: false })
  await tab.waitFor()
  await screenshot(baseline ? 'desktop-workspace-before' : 'desktop-workspace-after')
  if (!baseline) {
    check(await unit.getAttribute('aria-current') === 'page' && await tab.getAttribute('aria-selected') === 'true', 'New selection updates sidebar and tab together.')
    check(await unit.evaluate(el => getComputedStyle(el, '::before').opacity === '1') && await tab.evaluate(el => getComputedStyle(el, '::after').opacity === '1'), 'The sidebar rail and tab underline are both visible.')
    check(await page.getByRole('tabpanel').getAttribute('aria-labelledby') === await tab.getAttribute('id'), 'The live panel is linked to its active tab.')
    await button('收起侧栏').click()
    const group = page.locator('.sidebar [aria-controls="workspace-nav-basedata"]')
    check(await group.getAttribute('aria-expanded') === 'false' && await group.evaluate(el => el.classList.contains('active-parent') && el.tabIndex === 0), 'The active group remains reachable with the sidebar collapsed.')
    await group.click()
    check(await group.getAttribute('aria-expanded') === 'true' && await unit.getAttribute('aria-current') === 'page', 'Choosing an icon expands its group without changing the current page.')
    await page.evaluate(() => {
      const stage = document.getElementById('workspace-page')
      const original = stage.animate.bind(stage)
      stage.animate = (...args) => {
        document.body.dataset.pageChanged = JSON.stringify(args[0])
        return original(...args)
      }
    })
    await tab.press('ArrowLeft')
    await page.getByRole('tab', { name: '主页', exact: true }).waitFor()
    check(await page.getByRole('tab', { name: '主页', exact: true }).evaluate(el => el === document.activeElement && el.getAttribute('aria-selected') === 'true'), 'Tab keyboard navigation moves both selection and focus.')
    await page.waitForFunction(() => Boolean(document.body.dataset.pageChanged))
    check(await page.locator('body').getAttribute('data-page-changed') === '[{"opacity":0.7},{"opacity":1}]', 'Page switch uses a short opacity transition without moving fixed overlays.')
    await tab.click()
    await fields.waitFor()
  }
  await fields.click()
  const dialog = page.getByRole('dialog', { name: '列设置', exact: true })
  await dialog.waitFor()
  await screenshot(baseline ? 'desktop-dialog-before' : 'desktop-dialog-after')
  if (baseline) return { baseline, errors }
  await page.waitForTimeout(350)
  await page.keyboard.press('Escape')
  await dialog.waitFor({ state: 'hidden' })
  check(await fields.evaluate(el => el === document.activeElement), 'Escape closes the field dialog and returns focus.')
  await button('切换深色主题').click()
  await page.setViewportSize({ width: 640, height: 850 })
  await fields.click()
  await dialog.waitFor()
  await screenshot('desktop-dialog-dark-narrow')
  check(await dialog.evaluate(el => { const b = el.getBoundingClientRect(); return b.width <= innerWidth && b.x >= 0 && b.right <= innerWidth }), 'The dark dialog fits within the narrow viewport.')
  await page.keyboard.press('Escape')
  await dialog.waitFor({ state: 'hidden' })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.evaluate(() => { delete document.body.dataset.pageChanged })
  await page.getByRole('tab', { name: '主页', exact: true }).click()
  await page.locator('.home-dashboard').waitFor()
  check(await page.locator('body').getAttribute('data-page-changed') === null, 'Reduced motion suppresses scripted workspace movement.')
  await tab.click()
  await fields.click()
  await dialog.waitFor()
  check(await dialog.evaluate(el => getComputedStyle(el).transitionDuration === '0s'), 'Reduced motion suppresses dialog motion.')
  check(errors.length === 0, `Browser page errors: ${errors.join('; ')}`)
  return { passed: checks.length, checks }
}
