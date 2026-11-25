// Manual production-H5 audit, excluded from the *.test.mjs unit runner.
// Use only browser-fixture.mjs --seed-orders --seed-rejected --seed-reports --audit-ui.
// Run from mobile/ with a fresh playwright-cli -s=mobile-security-ui browser.
async page => {
  // This dedicated audit browser owns all its synthetic fault routes. Clear
  // handlers left by an interrupted earlier run before installing fresh ones.
  await page.unrouteAll({ behavior: 'wait' })
  const origin = new URL(page.url()).origin
  if (new URL(origin).hostname !== '127.0.0.2') throw Error('Isolated mobile fixture only')
  const probe = await page.request.get(origin + '/api/fixture-state')
  const fixture = await probe.json()
  if (!probe.ok() || !Array.isArray(fixture.requests) || !Number.isInteger(fixture.writeCount)) throw Error('Not the synthetic mobile fixture')
  const final = await page.evaluate(() => window.__mobileAuditFinal === true)
  const phase = final ? 'final' : 'baseline'
  const requestedPrefix = await page.evaluate(() => window.__mobileAuditEvidencePrefix)
  if (requestedPrefix !== undefined && (typeof requestedPrefix !== 'string' || !/^[a-z0-9-]{1,64}$/.test(requestedPrefix))) {
    throw Error('Invalid local evidence prefix')
  }
  // Repeated release checks must not overwrite a previous build's screenshots.
  const evidencePrefix = requestedPrefix || phase
  const screens = [], interactions = [], errors = [], checks = [], outboundAttempts = []
  const verify = (name, passed) => checks.push({ name, passed: Boolean(passed) })
  await page.route('**/*', async route => {
    const target = new URL(route.request().url())
    if (['http:', 'https:'].includes(target.protocol) && target.origin !== origin) {
      outboundAttempts.push(target.origin)
      await route.abort()
    } else await route.continue()
  })
  page.on('pageerror', error => errors.push(error.message))
  const nav = url => page.evaluate(url => new Promise((resolve, reject) => window.uni.navigateTo({ url, success: resolve, fail: e => reject(Error(e.errMsg)) })), url)
  const button = name => page.getByRole('button', { name, exact: true }).last()
  const setColorScheme = async colorScheme => {
    await page.emulateMedia({ colorScheme })
    await page.waitForFunction(dark => window.matchMedia('(prefers-color-scheme: dark)').matches === dark, colorScheme === 'dark')
    // CDP media emulation can precede the next style/layout frame. Wait for
    // actual rendering before measuring; screenshots must describe that state.
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
    await page.evaluate(() => Promise.allSettled(document.getAnimations().filter(animation => Number.isFinite(animation.effect?.getComputedTiming().endTime)).map(animation => animation.finished)))
  }
  const closeInfo = async () => {
    if (await button('关闭').count()) await button('关闭').click()
    else await page.getByText('关闭', { exact: true }).last().click()
  }
  const capture = async name => {
    const metrics = await page.evaluate(() => {
      const visible = el => { const box = el.getBoundingClientRect(); return box.width > 0 && box.height > 0 && getComputedStyle(el).visibility !== 'hidden' && box.right > 0 && box.left < window.innerWidth }
      const root = [...document.querySelectorAll('.container')].filter(visible).at(-1)
      const inputs = [...document.querySelectorAll('input,textarea')].filter(visible).map(el => ({
        kind: el.tagName.toLowerCase(), type: el.getAttribute('type'),
        label: el.getAttribute('aria-label') || (el.getAttribute('aria-labelledby') || '').split(' ').map(id => document.getElementById(id)?.textContent || '').join(' ').trim() || [...el.labels || []].map(label => label.textContent).join(' ').trim(),
        placeholder: el.getAttribute('placeholder'), required: el.getAttribute('aria-required')
      }))
      const buttons = [...document.querySelectorAll('button,[role="button"],[role="tab"]')].filter(visible)
      const small = buttons.map(el => { const box = el.getBoundingClientRect(); return { name: el.getAttribute('aria-label') || el.textContent.trim(), width: box.width, height: box.height } }).filter(item => item.width < 43.9 || item.height < 43.9)
      const rgb = value => (value.match(/[\d.]+/g) || []).slice(0, 3).map(Number)
      const luminance = values => values.map(value => value / 255).map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4).reduce((sum, value, index) => sum + value * [.2126, .7152, .0722][index], 0)
      const iconContrasts = buttons.filter(el => el.matches('.icon-action:not([disabled])')).flatMap(el => {
        const icon = el.querySelector('.uni-icons')
        if (!icon) return []
        const foreground = getComputedStyle(icon).color, background = getComputedStyle(el).backgroundColor
        const values = [luminance(rgb(foreground)), luminance(rgb(background))].sort((a, b) => a - b)
        return [{ name: el.getAttribute('aria-label'), foreground, background, ratio: (values[1] + .05) / (values[0] + .05) }]
      })
      const lowContrastIcons = iconContrasts.filter(icon => icon.ratio < 3)
      const horizontallyScrollable = el => {
        for (let parent = el.parentElement; parent && parent !== document.body; parent = parent.parentElement) {
          if (['auto', 'scroll'].includes(getComputedStyle(parent).overflowX) && parent.scrollWidth > parent.clientWidth) return true
        }
        return false
      }
      const clippedControls = [...(root?.querySelectorAll('button,input,textarea') || [])].map(el => ({ el, box: el.getBoundingClientRect() }))
        .filter(({ el, box }) => box.width && box.height && (box.left < -1 || box.right > window.innerWidth + 1) && !horizontallyScrollable(el))
        .map(({ el, box }) => ({ name: el.getAttribute('aria-label') || el.textContent.trim(), left: box.left, right: box.right }))
      return { mainCount: [...document.querySelectorAll('[role="main"]')].filter(visible).length,
        headings: [...document.querySelectorAll('[role="heading"],h1,h2')].filter(el => visible(el) || el.classList.contains('sr-only') && root?.contains(el)).map(el => ({ text: el.getAttribute('aria-label') || el.textContent.trim(), level: el.getAttribute('aria-level') || el.tagName })),
        inputs, smallTargets: small, clippedControls, iconContrasts, lowContrastIcons, viewport: window.innerWidth, documentWidth: document.documentElement.scrollWidth,
        bodyWidth: document.body.scrollWidth, rootBackground: root ? getComputedStyle(root).backgroundColor : null,
        unexpectedMarkup: Boolean(document.querySelector('[data-audit-literal]')) }
    })
    const screenshot = `output/playwright/security-ui/${evidencePrefix}-${name}.png`
    await page.screenshot({ path: screenshot, animations: 'disabled' })
    screens.push({ name, screenshot, ...metrics })
    if (final) {
      verify(name + ': single main landmark', metrics.mainCount === 1)
      verify(name + ': heading', metrics.headings.length > 0)
      verify(name + ': durable input names', metrics.inputs.every(input => input.label))
      verify(name + ': touch targets', metrics.smallTargets.length === 0)
      verify(name + ': no clipped controls', metrics.clippedControls.length === 0)
      verify(name + ': enabled shared icons retain non-text contrast', metrics.lowContrastIcons.length === 0)
      verify(name + ': literal business text remains text', !metrics.unexpectedMarkup)
      if (name.endsWith('-dark')) verify(name + ': dark surface', !['rgb(246, 247, 247)', 'rgb(243, 245, 245)', 'rgb(255, 255, 255)'].includes(metrics.rootBackground))
    }
    await page.evaluate(result => { window.__mobileSecurityUiAuditResult = result }, { phase, complete: false, screens, interactions, pageerrors: errors })
    if (final && name.endsWith('-light')) {
      await setColorScheme('dark')
      await capture(name.replace('-light', '-dark'))
      await setColorScheme('light')
      await page.setViewportSize({ width: 390, height: 844 })
      await capture(name.replace('-light', '-wide'))
      await page.setViewportSize({ width: 320, height: 700 })
    }
  }
  const focusIn = dialog => dialog.evaluate(el => el.contains(document.activeElement))
  const focusLoop = async (name, dialog, returnTo) => {
    await dialog.waitFor({ state: 'visible' })
    const initiallyInside = await focusIn(dialog)
    const staysInside = []
    for (let i = 0; i < 7; i++) { await page.keyboard.press('Tab'); staysInside.push(await focusIn(dialog)) }
    const reverseInside = []
    for (let i = 0; i < 7; i++) { await page.keyboard.press('Shift+Tab'); reverseInside.push(await focusIn(dialog)) }
    await page.keyboard.press('Escape')
    const escapeClosed = !await dialog.isVisible()
    const focusReturned = returnTo ? await returnTo.evaluate(el => new Promise(resolve => requestAnimationFrame(() => resolve(document.activeElement === el)))) : null
    interactions.push({ name, initiallyInside, tabTrapped: staysInside.every(Boolean), shiftTabTrapped: reverseInside.every(Boolean), escapeClosed, focusReturned })
    if (final) {
      verify(name + ': initial focus, Tab/Shift+Tab containment and Escape', initiallyInside && staysInside.every(Boolean) && reverseInside.every(Boolean) && escapeClosed)
      if (returnTo) verify(name + ': returns focus to the invoking control', focusReturned)
    }
  }
  await page.setViewportSize({ width: 320, height: 700 })
  await page.goto(origin + '/mobile/#/pages/bind/index?manual=1')
  // A prior failed native API modal lives outside #app. A genuine reload gives
  // each audit run a clean document rather than hiding or force-clicking it.
  await page.reload()
  await button('登录').waitFor()
  await capture('bind-light')
  if (!final) { await setColorScheme('dark'); await capture('bind-dark'); await setColorScheme('light') }
  await page.getByPlaceholder('请输入工厂代码').fill('QA')
  await page.getByPlaceholder('请输入工号/手机号').fill('worker')
  await page.getByPlaceholder('请输入登录密码').fill('FixturePassword')
  let rejectLogin = true
  const loginFault = async route => {
    if (rejectLogin) { rejectLogin = false; await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: '登录暂时不可用，请重试' }) }) }
    else await route.fallback()
  }
  await page.route('**/api/mobile/auth/password-login', loginFault)
  await button('登录').click()
  await page.getByText('登录暂时不可用，请重试', { exact: true }).last().waitFor()
  const loginRetained = await page.getByPlaceholder('请输入工号/手机号').inputValue() === 'worker'
  const loginDialog = page.getByRole('dialog').last()
  const namedLoginDialog = await loginDialog.count() > 0 && await loginDialog.isVisible()
  interactions.push({ name: 'failed-password-login', retained: loginRetained, namedDialog: namedLoginDialog })
  if (final) verify('Login failure retains input and has a named dialog', loginRetained && namedLoginDialog)
  await capture('bind-failure')
  await closeInfo()
  await page.unroute('**/api/mobile/auth/password-login', loginFault)
  await button('登录').click()
  await button('刷新报工统计').waitFor()
  await capture('home-light')
  const activeTab = page.getByRole('tab', { selected: true }).last()
  await activeTab.focus()
  const beforeArrow = await activeTab.textContent()
  await page.keyboard.press('ArrowRight')
  const afterArrow = await page.evaluate(() => document.activeElement?.textContent?.trim())
  interactions.push({ name: 'main-navigation-arrow', beforeArrow: beforeArrow.trim(), afterArrow, moved: beforeArrow.trim() !== afterArrow })
  if (final) verify('Main-navigation arrow moves focus', beforeArrow.trim() !== afterArrow)
  await button('扫码报工').click()
  const scan = page.getByRole('dialog', { name: '扫码工单', exact: true })
  await scan.waitFor()
  await capture('scan-dialog')
  await focusLoop('scan-focus-loop', scan, button('扫码报工'))
  if (await scan.isVisible()) await button('关闭扫码').click()

  await page.getByRole('tab', { name: '看板', exact: true }).click()
  await button('刷新看板').waitFor()
  await capture('board-light')
  if (!final) { await setColorScheme('dark'); await capture('board-dark') }
  await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' })
  await setColorScheme('light')
  interactions.push({ name: 'reduced-motion', duration: await button('刷新看板').evaluate(el => getComputedStyle(el).transitionDuration) })
  if (final) verify('Reduced motion suppresses the repeated control transition', await button('刷新看板').evaluate(el => getComputedStyle(el).transitionDuration.split(',').every(value => parseFloat(value) <= 0.001)))
  await page.getByRole('tab', { name: '任务', exact: true }).click()
  await page.locator('button.task-card').first().waitFor()
  await capture('tasks-light')
  const beforeTasks = await page.locator('button.task-card').count()
  let faulted = false
  const pageFault = async route => {
    if (!faulted && new URL(route.request().url()).searchParams.get('page') === '2') { faulted = true; await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: '下一页暂时不可用' }) }) }
    else await route.fallback()
  }
  await page.route('**/api/mobile/work-orders?*', pageFault)
  await button('加载更多工单').click()
  await page.getByText('下一页暂时不可用', { exact: true }).waitFor()
  interactions.push({ name: 'task-page-failure', previousRowsRetained: await page.locator('button.task-card').count() === beforeTasks })
  if (final) verify('Failed next-page request retains the previous task rows', await page.locator('button.task-card').count() === beforeTasks)
  await capture('tasks-failure')
  await page.unroute('**/api/mobile/work-orders?*', pageFault)
  await page.getByRole('button', { name: /^重试/ }).last().click()
  await page.waitForFunction(before => document.querySelectorAll('button.task-card').length > before, beforeTasks)

  await page.getByRole('tab', { name: '工单', exact: true }).click()
  await page.locator('button.order-card').first().waitFor()
  await capture('work-orders-light')
  if (final) {
    const statusTab = page.locator('.status-tab-row').getByRole('tab').first()
    await statusTab.focus()
    const before = await statusTab.textContent()
    await page.keyboard.press('ArrowRight')
    const focused = await page.evaluate(() => document.activeElement?.textContent?.trim())
    verify('Status-tab arrows move focus without implicit selection', focused !== before.trim() && await statusTab.getAttribute('aria-selected') === 'true')
  }
  await button('展开工单搜索').click()
  await page.getByPlaceholder('工单号 / 产品').fill('后页专用零件')
  await button('搜索工单').click()
  await button('QA-001，查看工单').waitFor()
  interactions.push({ name: 'unloaded-order-search', foundOne: await page.locator('button.order-card').count() === 1 })
  if (final) verify('Server search reaches one order outside the initially loaded page', await page.locator('button.order-card').count() === 1)
  await capture('work-orders-search')
  await button('清空并收起搜索').click()
  await nav('/pages/work-order-detail/index?id=42&securityUiAudit=1')
  await button('查看工单备注').waitFor()
  await capture('work-order-detail-light')
  await button('查看工单备注').click()
  await page.getByText('工单备注', { exact: true }).waitFor()
  const infoDialog = page.getByRole('dialog').last()
  interactions.push({ name: 'order-note-dialog', namedDialog: await infoDialog.count() > 0 && await infoDialog.isVisible() })
  if (final) verify('Order note has a named dialog', await infoDialog.count() > 0 && await infoDialog.isVisible())
  await capture('order-note-dialog')
  await closeInfo()
  if (final) {
    await button('查看工单优先级').click()
    const priority = page.getByRole('dialog', { name: '优先级', exact: true })
    await capture('priority-dialog')
    await focusLoop('priority-dialog-focus', priority, button('查看工单优先级'))
    if (await priority.isVisible()) await closeInfo()
    await page.getByRole('button', { name: /详情$/ }).first().click()
    const stepInfo = page.getByRole('dialog').last()
    await capture('step-detail-dialog')
    await focusLoop('step-detail-dialog-focus', stepInfo, page.getByRole('button', { name: /详情$/ }).first())
    if (await stepInfo.isVisible()) await closeInfo()
  }
  await nav('/pages/report-form/index?orderId=42&processNo=1&securityUiAudit=1')
  await button('选择生产人员').waitFor()
  await page.waitForFunction(() => !document.querySelector('button.reporter-select')?.disabled)
  await capture('report-form-light')
  await button('选择生产人员').click()
  const reporter = page.getByRole('dialog', { name: '选择生产人员', exact: true })
  await reporter.waitFor()
  await capture('reporter-dialog')
  await focusLoop('reporter-focus-loop', reporter, button('选择生产人员'))
  if (await reporter.isVisible()) await button('关闭人员选择').click()
  await page.getByLabel('报工数', { exact: true }).last().fill('3')
  await page.getByLabel('不良数', { exact: true }).last().fill('0')
  await page.getByLabel('良品数', { exact: true }).last().fill('3')
  await page.getByLabel('报工备注', { exact: true }).last().fill('保留失败草稿')
  const reportFault = route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: '报工未保存，请重试' }) })
  await page.route('**/api/mobile/reports', reportFault)
  await button('提交').click()
  await page.getByText('报工未保存，请重试', { exact: true }).last().waitFor()
  interactions.push({ name: 'report-failed-save', remarkRetained: await page.getByLabel('报工备注', { exact: true }).last().inputValue() === '保留失败草稿' })
  if (final) verify('Failed report submission retains the entered remark', await page.getByLabel('报工备注', { exact: true }).last().inputValue() === '保留失败草稿')
  await capture('report-form-failure')
  await page.unroute('**/api/mobile/reports', reportFault)

  await page.evaluate(() => window.uni.switchTab({ url: '/pages/report-list/index' }))
  await button('刷新报工记录').waitFor()
  await page.locator('button.photo-button').first().waitFor()
  await capture('report-list-light')
  const imageDelay = async route => { await new Promise(resolve => setTimeout(resolve, 600)); await route.fallback() }
  await page.route('**/api/mobile/reports/images/*/file', imageDelay)
  const photos = page.getByRole('button', { name: '查看报工照片', exact: true })
  await photos.nth(0).click()
  await photos.nth(1).click()
  await page.waitForTimeout(850)
  const gallery = () => page.getByRole('dialog', { name: '报工照片预览', exact: true })
  interactions.push({ name: 'concurrent-photo-previews', visibleGalleryCount: await gallery().count() })
  if (final) verify('Concurrent photos own one gallery', await gallery().count() === 1)
  await capture('photo-gallery-concurrent')
  if (final) await focusLoop('photo-gallery-focus', gallery().last(), photos.nth(1))
  while (await gallery().count()) await button('关闭照片预览').click()
  await photos.nth(0).click()
  await nav('/pages/field-events/index?securityUiAudit=1')
  await button('上报').waitFor()
  await page.waitForTimeout(850)
  interactions.push({ name: 'hidden-photo-preview', lateGalleryCount: await gallery().count() })
  if (final) verify('Hidden owner cannot open a late gallery', await gallery().count() === 0)
  await capture('photo-gallery-hidden-owner')
  while (await gallery().count()) await button('关闭照片预览').click()
  await page.unroute('**/api/mobile/reports/images/*/file', imageDelay)
  await capture('field-events-light')
  await page.getByPlaceholder('例如：A 线贴标机停机').fill('保留现场事件')
  const eventFault = route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: '现场事件未保存，请重试' }) })
  await page.route('**/api/mobile/field-events', eventFault)
  await button('上报').click()
  await page.getByText('现场事件未保存，请重试', { exact: true }).last().waitFor()
  interactions.push({ name: 'event-failed-save', titleRetained: await page.getByPlaceholder('例如：A 线贴标机停机').inputValue() === '保留现场事件' })
  if (final) verify('Failed field-event submission retains the entered title', await page.getByPlaceholder('例如：A 线贴标机停机').inputValue() === '保留现场事件')
  await capture('field-events-failure')
  await page.unroute('**/api/mobile/field-events', eventFault)
  if (final) {
    await page.evaluate(() => window.uni.switchTab({ url: '/pages/home/index' }))
    await button('退出登录').waitFor()
    await button('退出登录').click()
    const logout = page.getByRole('dialog', { name: '退出登录', exact: true })
    await capture('logout-dialog')
    await focusLoop('logout-dialog-focus', logout, button('退出登录'))
    verify('Cancel logout preserves the local session', await page.evaluate(() => Boolean(window.uni.getStorageSync('token'))))
    const beforeLogout = await (await page.request.get(origin + '/api/fixture-state')).json()
    let failedLogoutRequests = 0
    const logoutFault = async route => {
      failedLogoutRequests++
      await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: '退出暂时不可用' }) })
    }
    await page.route(origin + '/api/auth/logout', logoutFault)
    await button('退出登录').click()
    await button('退出').click()
    await page.getByText(/退出未完成，登录仍保留/).last().waitFor()
    verify('A failed server logout preserves the client session', await page.evaluate(() => Boolean(window.uni.getStorageSync('token'))))
    verify('Failed logout issued only one request', failedLogoutRequests === 1)
    await capture('logout-failure')
    await page.unroute(origin + '/api/auth/logout', logoutFault)
    await button('重试退出').click()
    await button('登录').waitFor()
    verify('Confirmed logout clears local session', await page.evaluate(() => !window.uni.getStorageSync('token') && !window.uni.getStorageSync('user')))
    const afterLogout = await (await page.request.get(origin + '/api/fixture-state')).json()
    verify('Explicit retry reached the fixture logout endpoint once', afterLogout.logoutCount === beforeLogout.logoutCount + 1)
    await capture('logout-complete')
    verify('No application pageerrors', errors.length === 0)
    verify('No outbound origins contacted', outboundAttempts.length === 0)
  }
  const result = { phase, evidencePrefix, complete: true, evidenceType: 'real production H5 / synthetic API; long and literal business text is isolated test data',
    passed: checks.filter(check => check.passed).length, failed: checks.filter(check => !check.passed), checks,
    screens, interactions, pageerrors: errors, outboundAttempts }
  await page.evaluate(result => { window.__mobileSecurityUiAuditResult = result }, result)
  return result
}
