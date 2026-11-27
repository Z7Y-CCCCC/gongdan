// Run through playwright-cli run-code --filename after signing in to the isolated
// functional-browser-fixture.mjs. Never run against production business data.
async (page) => {
  const origin = new URL(page.url()).origin
  if (new URL(origin).hostname !== '127.0.0.2') throw new Error('Use the loopback functional fixture')
  const metadata = await (await page.request.get(`${origin}/__functional-fixture`)).json()
  if (metadata.fixture !== 'real-h2-api' || metadata.factoryCode !== 'FUNCTIONALTEST') throw new Error('Wrong fixture')
  const checks = []
  const pages = []
  const errors = []
  const serverErrors = []
  const onError = error => errors.push(error.message)
  const onResponse = response => { if (response.status() >= 500) serverErrors.push(`${response.status()} ${response.url()}`) }
  page.on('pageerror', onError)
  page.on('response', onResponse)
  const check = (ok, label) => { if (!ok) throw new Error(label); checks.push(label) }
  const ready = async () => {
    await page.locator('.workspace-loading-overlay').waitFor({ state: 'hidden' })
    await page.locator('.page-motion-stage').waitFor({ state: 'attached' })
  }
  try {
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto(`${origin}/?workspace=basedata-unit`, { waitUntil: 'networkidle' })
    await ready()
    check(await page.getByRole('button', { name: 'Create unit', exact: true }).isVisible(), 'English workspace URL restored Units')
    check(await page.getByRole('button', { name: 'Close Units', exact: true }).count() === 1, 'Nested tab accessibility label is English')
    const name = `Browser verification ${Date.now()}`
    await page.getByRole('button', { name: 'Create unit', exact: true }).click()
    let modal = page.locator('.modal-backdrop .modal').filter({ has: page.getByRole('heading', { name: 'Create unit', exact: true }) })
    await modal.getByRole('textbox', { name: '*Name', exact: true }).fill(name)
    await modal.getByRole('textbox', { name: 'Notes', exact: true }).fill('保存')
    await modal.getByRole('button', { name: 'Save unit', exact: true }).click()
    await modal.waitFor({ state: 'hidden' })
    await page.getByRole('row').filter({ hasText: name }).waitFor()
    await page.reload({ waitUntil: 'networkidle' })
    await ready()
    let row = page.getByRole('row').filter({ hasText: name })
    await row.waitFor()
    check((await row.innerText()).includes('保存'), 'Real create persists across reload without translating business notes')
    await row.getByRole('button', { name: 'Edit', exact: true }).click()
    modal = page.locator('.modal-backdrop .modal').filter({ has: page.getByRole('heading', { name: 'Edit unit', exact: true }) })
    await modal.getByRole('textbox', { name: '*Name', exact: true }).fill(`${name} edited`)
    await modal.getByRole('button', { name: 'Save unit', exact: true }).click()
    await modal.waitFor({ state: 'hidden' })
    row = page.getByRole('row').filter({ hasText: `${name} edited` })
    await row.waitFor()
    check(await row.count() === 1, 'Real edit updates the persisted row')
    await row.getByRole('button', { name: 'Delete', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: 'Confirm deletion', exact: true })
    await dialog.getByRole('button', { name: 'Confirm deletion', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
    await row.waitFor({ state: 'hidden' })
    check(await row.count() === 0, 'Real delete removes the persisted row')
    const routes = [
      'home', 'dashboard', 'notifications', 'advanced', 'report',
      'production-sales', 'production-workorder', 'production-task', 'production-reporting', 'production-workbench',
      'inventory-inbound', 'inventory-outbound', 'inventory-detail', 'inventory-balance', 'inventory-summary',
      'basedata-product', 'basedata-bom', 'basedata-unit', 'basedata-defect', 'basedata-process', 'basedata-route', 'basedata-warehouse', 'basedata-supplier', 'basedata-customer',
      'custom-product', 'custom-bom', 'custom-workorder', 'custom-reporting', 'custom-process', 'custom-customer', 'custom-receipt', 'custom-receipt_detail',
      'sysconfig-department', 'sysconfig-user', 'sysconfig-factory', 'sysconfig-plugin-center', 'sysconfig-external-data-sources',
      'monitor-online', 'monitor-cache', 'monitor-cacheList'
    ]
    for (const route of routes) {
      await page.goto(`${origin}/?workspace=${route}`, { waitUntil: 'networkidle' })
      await ready()
      const content = await page.locator(route === 'dashboard' ? '.dashboard-fullscreen' : '.page-motion-stage').innerText()
      check(content.trim().length > 0, `${route}: rendered the actual workspace`)
      const untranslatedControls = await page.locator('button, h1, h2, h3, th').evaluateAll(elements => elements.filter(el => el.getClientRects().length).map(el => {
        const label = el.getAttribute('aria-label') || ''
        // The top-bar user name is user data, not translatable interface copy.
        return el.matches('.user-profile-trigger') ? label : `${el.textContent?.trim()} ${label}`
      }).filter(text => /\p{Script=Han}/u.test(text)))
      pages.push({ route, untranslatedControls })
      if (['home', 'production-workorder', 'sysconfig-factory', 'monitor-cache'].includes(route)) {
        await page.screenshot({ path: `output/playwright/management-functional/${route}-en.png`, animations: 'disabled' })
      }
    }
    const untranslated = pages.filter(item => item.untranslatedControls.length > 0)
    if (untranslated.length) throw Error(`Untranslated controls: ${JSON.stringify(untranslated)}`)
    checks.push('Seeded page controls and system notification text are fully English')
    await page.goto(`${origin}/?workspace=basedata-unit`, { waitUntil: 'networkidle' })
    await ready()
    await page.getByRole('button', { name: 'Home', exact: true }).click()
    const unitTab = page.getByRole('tab').filter({ hasText: 'Units' })
    await unitTab.click({ button: 'right' })
    const popupPromise = page.waitForEvent('popup')
    await page.getByRole('menuitem', { name: 'Open in a new window', exact: true }).click()
    const popup = await popupPromise
    try {
      await popup.waitForURL(url => url.searchParams.get('workspace') === 'basedata-unit')
      await popup.getByRole('button', { name: 'Create unit', exact: true }).waitFor()
      check(await popup.evaluate(() => window.opener === null), 'Context-menu target opens in a real isolated window')
    } finally { await popup.close() }
    await page.goto(`${origin}/?workspace=home`, { waitUntil: 'networkidle' })
    await ready()
    check(!/\d(?:\.\d+)?天/.test(await page.locator('.page-motion-stage').innerText()), 'Computed production cycle uses English duration')
    await page.getByRole('button', { name: 'Switch to dark theme', exact: true }).click()
    check(await page.getByRole('button', { name: 'Switch to light theme', exact: true }).isVisible(), 'Theme toggle has truthful state')
    const accountContrast = await page.locator('.user-profile .username').evaluate(element => {
      const color = getComputedStyle(element).color.match(/\d+(?:\.\d+)?/g).slice(0, 3).map(Number)
      const background = getComputedStyle(element.closest('header')).backgroundColor.match(/\d+(?:\.\d+)?/g).slice(0, 3).map(Number)
      const luminance = rgb => rgb.map(value => value / 255).map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4).reduce((total, value, index) => total + value * [0.2126, 0.7152, 0.0722][index], 0)
      return (Math.max(luminance(color), luminance(background)) + 0.05) / (Math.min(luminance(color), luminance(background)) + 0.05)
    })
    check(accountContrast >= 4.5, 'Actual dark-theme account text has sufficient contrast')
    const account = page.getByRole('button', { name: 'Account menu', exact: true })
    await account.focus()
    await page.keyboard.press('Enter')
    check(await page.getByRole('button', { name: 'Sign out', exact: true }).isVisible(), 'Account menu opens from the keyboard')
    await page.keyboard.press('Escape')
    check(await account.getAttribute('aria-expanded') === 'false', 'Account menu closes with Escape')
    await page.screenshot({ path: 'output/playwright/management-functional/home-dark-en.png', animations: 'disabled' })
    await page.getByRole('button', { name: 'Switch to light theme', exact: true }).click()
    await page.getByRole('button', { name: 'Language', exact: true }).click()
    await page.getByRole('button', { name: 'Simplified Chinese', exact: true }).click()
    check(await page.getByRole('button', { name: '主页', exact: true }).isVisible(), 'Switch back to Chinese without reload')
    await page.getByRole('button', { name: '语言', exact: true }).click()
    await page.getByRole('button', { name: 'English', exact: true }).click()
    check(await page.getByRole('button', { name: 'Home', exact: true }).isVisible(), 'Switch to English again without reload')
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.getByRole('button', { name: 'Time zone', exact: true }).click()
    const timezone = page.getByRole('dialog', { name: 'Time zone', exact: true })
    await timezone.getByRole('radio', { name: 'America/New_York (GMT-5)', exact: true }).check()
    await timezone.getByRole('button', { name: 'Confirm', exact: true }).click()
    await timezone.waitFor({ state: 'hidden' })
    check(await page.evaluate(() => localStorage.getItem('heihu_timezone')) === 'America/New_York', 'Display time zone persists')
    await page.getByRole('button', { name: 'Time zone', exact: true }).click()
    await timezone.getByRole('radio', { name: 'Asia/Shanghai (GMT+8)', exact: true }).check()
    await timezone.getByRole('button', { name: 'Confirm', exact: true }).click()
    await timezone.waitFor({ state: 'hidden' })
    check(errors.length === 0, `No uncaught browser error: ${errors.join('; ')}`)
    check(serverErrors.length === 0, `No server 5xx: ${serverErrors.join('; ')}`)
    const loginContext = await page.context().browser().newContext({ viewport: { width: 390, height: 844 } })
    try {
      const login = await loginContext.newPage()
      await login.goto(origin, { waitUntil: 'networkidle' })
      await login.getByRole('button', { name: 'Switch to English', exact: true }).click()
      await login.getByRole('textbox', { name: 'Factory code', exact: true }).waitFor()
      check(await login.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'English login fits a narrow screen without horizontal overflow')
      await login.screenshot({ path: 'output/playwright/management-functional/login-narrow-en.png' })
    } finally { await loginContext.close() }
    return { passed: checks.length, checkedRoutes: pages.length, checks, pages, errors, serverErrors, fixtureOnly: true }
  } finally {
    page.off('pageerror', onError)
    page.off('response', onResponse)
    await page.emulateMedia({ reducedMotion: 'no-preference' })
  }
}
