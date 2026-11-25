// Production dist with explicitly labelled synthetic API faults/data, never a business backend.
// Start ui-browser-fixture.mjs, then use playwright-cli run-code --filename from repository root.
async (page) => {
  const origin = new URL(page.url()).origin
  if (new URL(origin).hostname !== '127.0.0.2') throw Error('Use the isolated management UI fixture')
  const identity = await (await page.request.get(`${origin}/api/fixture-state`)).json()
  if (identity.fixture !== 'management-ui') throw Error('Wrong fixture')
  const output = 'output/playwright/management-security-2026-11-25'
  const checks = [], errors = [], outside = [], downloads = []
  const check = (ok, label) => { if (!ok) throw Error(label); checks.push(label) }
  const onError = error => errors.push(error.message)
  const onRequest = request => {
    if (/^https?:/.test(request.url()) && new URL(request.url()).origin !== origin) outside.push(request.url())
  }
  page.on('pageerror', onError); page.on('request', onRequest)
  const ready = async () => {
    await page.locator('.workspace-loading-overlay').waitFor({ state: 'hidden' })
    await page.locator('.page-motion-stage').waitFor()
  }
  const settle = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
  const go = async workspace => { await page.goto(`${origin}/?workspace=${workspace}`); await ready() }
  const capture = name => page.screenshot({ path: `${output}/${name}.png`, animations: 'disabled' })
  const reply = (route, data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
  let releaseKeys, releaseValue, releaseClear, releaseLogout, keysStarted, valueStarted, logoutStarted
  const didStartKeys = new Promise(resolve => { keysStarted = resolve })
  const didStartValue = new Promise(resolve => { valueStarted = resolve })
  const didStartLogout = new Promise(resolve => { logoutStarted = resolve })
  const heldKeys = new Promise(resolve => { releaseKeys = resolve })
  const heldValue = new Promise(resolve => { releaseValue = resolve })
  const heldClear = new Promise(resolve => { releaseClear = resolve })
  const heldLogout = new Promise(resolve => { releaseLogout = resolve })
  let oldKeysSeen = false, oldValueSeen = false, clears = 0, logoutCalls = 0, logoutCaptured = true
  let failMetrics = true, failFeed = true, failLogPageTwo = true
  const hostile = '<img src=x onerror="window.__fixtureAttack=1">'
  const originalName = '=1+1 _x0041_\r\n' + hostile + '😀'
  const longName = '保存-' + '较长的业务姓名'.repeat(16) + hostile
  const user = { id: 7, username: 'admin', name: longName, role: 'ADMIN', roleLabel: '管理员', enabled: true,
    factoryId: 2, factoryCode: 'UIFIXTURE', departmentId: 1, departmentName: '生产运营', virtualUser: false, createdAt: '2026-11-25 08:00:00' }
  const patterns = []
  const route = async (pattern, handler) => { patterns.push(pattern); await page.route(pattern, handler) }
  try {
    await page.setViewportSize({ width: 1280, height: 800 })
    await route('**/api/home-dashboard/performance*', r => failMetrics
      ? reply(r, { message: '绩效故障-本地注入' }, 503)
      : reply(r, { plannedQuantity: 360, completedQuantity: 80, workOrderCount: 3, defectRatePercent: 2.44, averageCycleDays: 1.5, overdueUnfinishedCount: 0 }))
    await route('**/api/home-dashboard/reports/recent?*', r => failFeed ? reply(r, { message: '动态故障-本地注入' }, 503) : reply(r, { items: [] }))
    await route(/\/api\/users(?:[/?].*)?$/, r => {
      const url = new URL(r.request().url())
      if (url.pathname === '/api/users/export') {
        const includeHash = url.searchParams.get('includePasswordHash') === 'true'
        const raw = `\uFEFF账号,姓名${includeHash ? ',密码哈希' : ''}\r\nadmin,"${originalName.replaceAll('"', '""')}"${includeHash ? ',fixture-hash-only' : ''}\r\n`
        return r.fulfill({ status: 200, contentType: 'text/csv; charset=utf-8', body: raw })
      }
      if (url.pathname === '/api/users') return reply(r, [user])
      return r.continue()
    })
    await route(/\/api\/monitor\/cache(?:[/?].*)?$/, async r => {
      const url = new URL(r.request().url())
      if (r.request().method() === 'DELETE') { clears++; await heldClear; return reply(r, { affected: 2, recoveryWindowMinutes: 5 }) }
      if (url.pathname.endsWith('/names')) return reply(r, [{ cacheName: 'cache-A', remark: '旧响应', keyCount: 2 }, { cacheName: 'cache-B', remark: '新响应', keyCount: 2 }])
      if (url.pathname.includes('/recover')) return reply(r, [])
      if (url.pathname.endsWith('/keys')) {
        if (url.searchParams.get('cacheName') === 'cache-A') { oldKeysSeen = true; keysStarted(); await heldKeys; return reply(r, ['late-A-key']) }
        return reply(r, ['key-one', 'key-two'])
      }
      if (url.pathname.endsWith('/value')) {
        if (url.searchParams.get('cacheKey') === 'key-one') { oldValueSeen = true; valueStarted(); await heldValue; return reply(r, { cacheValue: 'late-first-value' }) }
        return reply(r, { cacheValue: `当前第二个键 ${hostile}` })
      }
      return reply(r, { basicInfo: { status: '正常', keyCount: '2' }, commandStats: [{ name: 'GET', count: 4 }],
        memoryInfo: { usedBytes: 1024, maxBytes: 2048, usedPercent: 50, usedHuman: '1KB', maxHuman: '2KB' } })
    })
    await route('**/api/system-logs/imports?*', r => {
      const url = new URL(r.request().url()), pageNumber = Number(url.searchParams.get('page') || 1)
      if (pageNumber === 2 && failLogPageTwo) return reply(r, { message: '日志翻页故障-本地注入' }, 503)
      const start = pageNumber === 2 ? 16 : 1, length = pageNumber === 2 ? 10 : 15
      return reply(r, { total: 25, items: Array.from({ length }, (_, index) => ({ id: start + index,
        fileName: index === 0 ? `第${pageNumber}页-${hostile}.csv` : `fixture-${start + index}.csv`, createdAt: '2026-11-25 08:00:00',
        totalCount: 2, successCount: 1, failCount: 1, status: '部分成功', operatorName: longName, errors: hostile })) })
    })
    await route('**/api/auth/logout', async r => {
      logoutCalls++; logoutCaptured &&= r.request().headers().authorization === 'Bearer local-ui-fixture-only'
      if (logoutCalls === 1) return reply(r, { message: '退出故障-本地注入' }, 503)
      logoutStarted(); await heldLogout; return r.fulfill({ status: 204, body: '' })
    })

    await page.getByRole('textbox', { name: '工厂代码', exact: true }).fill('UIFIXTURE')
    await page.getByRole('textbox', { name: '用户名', exact: true }).fill('admin')
    await page.getByRole('textbox', { name: '密码', exact: true }).fill('fixture-only')
    await page.getByRole('button', { name: '登录', exact: true }).click(); await ready()
    check(await page.getByRole('button', { name: '高级报表', exact: true }).count() === 0, 'Ordinary admin has no native designer entry')
    await page.getByRole('alert').filter({ hasText: '绩效故障-本地注入' }).waitFor()
    await page.getByRole('alert').filter({ hasText: '动态故障-本地注入' }).waitFor()
    check((await page.locator('.kpi-value').allTextContents()).every(value => value.trim() === '--'), 'Failed KPI never claims zero statistics')
    await capture('home-retryable-failures')
    failMetrics = false; failFeed = false
    await page.getByRole('alert').filter({ hasText: '绩效故障-本地注入' }).getByRole('button', { name: '重试', exact: true }).click()
    await page.getByRole('alert').filter({ hasText: '动态故障-本地注入' }).getByRole('button', { name: '重试', exact: true }).click()
    await page.getByRole('alert').filter({ hasText: '绩效故障-本地注入' }).waitFor({ state: 'hidden' })
    await page.getByRole('alert').filter({ hasText: '动态故障-本地注入' }).waitFor({ state: 'hidden' })
    checks.push('Home KPI/feed failure can be retried from the page')

    await go('monitor-cacheList')
    await page.getByRole('button', { name: 'cache-A', exact: true }).click()
    await didStartKeys; check(oldKeysSeen, 'First cache request actually entered the delayed route')
    await page.getByRole('button', { name: 'cache-B', exact: true }).focus(); await page.keyboard.press('Enter')
    await page.getByRole('button', { name: 'key-two', exact: true }).waitFor()
    const lateKeysReply = page.waitForResponse(response => response.url().includes('/monitor/cache/keys?') && response.url().includes('cacheName=cache-A'))
    releaseKeys(); await (await lateKeysReply).finished(); await settle()
    check(await page.getByRole('button', { name: 'cache-B', exact: true }).getAttribute('aria-pressed') === 'true'
      && await page.getByRole('button', { name: 'late-A-key', exact: true }).count() === 0, 'Delayed cache A cannot overwrite keyboard-selected B')
    await page.getByRole('button', { name: 'key-one', exact: true }).click()
    await didStartValue
    check(oldValueSeen, 'First key request actually entered the delayed route')
    await page.getByRole('button', { name: 'key-two', exact: true }).click()
    const content = page.getByRole('textbox', { name: '缓存内容', exact: true })
    await page.waitForFunction(expected => document.querySelector('.cache-list-page textarea')?.value === expected, `当前第二个键 ${hostile}`)
    const lateValueReply = page.waitForResponse(response => response.url().includes('/monitor/cache/value?') && response.url().includes('cacheKey=key-one'))
    releaseValue(); await (await lateValueReply).finished(); await settle()
    check(await content.inputValue() === `当前第二个键 ${hostile}`, 'Delayed first key cannot overwrite the second key')
    check(await page.locator('.cache-list-page img').count() === 0, 'Cache content containing HTML renders only as text')
    await capture('cache-latest-context')
    await page.getByRole('button', { name: '清理全部', exact: true }).click()
    const clearDialog = page.getByRole('dialog', { name: '清理全部缓存', exact: true })
    await clearDialog.waitFor()
    check(/清理/.test(await clearDialog.locator('.delete-confirm-dialog__footer .btn--danger').innerText()), 'Dangerous confirmation names clearing rather than deleting')
    await clearDialog.locator('.delete-confirm-dialog__footer .btn--danger').click()
    await clearDialog.locator('[aria-busy="true"]').first().waitFor()
    check(await clearDialog.getByRole('button', { name: '取消', exact: true }).isDisabled(), 'Pending clear cannot be double-submitted or dismissed')
    await page.keyboard.press('Enter'); await page.keyboard.press('Escape')
    check(clears === 1 && await clearDialog.isVisible(), 'Repeated keyboard activation dispatches one clear request')
    await capture('clear-pending'); releaseClear(); await clearDialog.waitFor({ state: 'hidden' })

    await go('sysconfig-department')
    await page.locator('.page-motion-stage tbody').getByRole('button', { name: '编辑', exact: true }).first().click()
    const department = page.locator('.modal-backdrop .modal').filter({ has: page.getByRole('heading', { name: '编辑部门', exact: true }) })
    await department.waitFor()
    const memberTrigger = department.getByRole('button', { name: '部门人员', exact: true })
    await memberTrigger.click()
    const choice = department.getByRole('checkbox').first(); await choice.waitFor(); await choice.focus()
    check(await choice.evaluate(element => Boolean(element.closest('.modal'))), 'Member checkbox remains inside the parent dialog focus scope')
    await page.keyboard.press('Space')
    await page.keyboard.press('Escape')
    check(await memberTrigger.evaluate(element => element === document.activeElement), 'Member disclosure Escape returns focus without dismissing the draft')
    check(await department.isVisible(), 'Closing member disclosure preserves the department form')
    await page.setViewportSize({ width: 390, height: 844 }); await settle()
    const bounds = await department.boundingBox()
    check(bounds && bounds.x >= 0 && bounds.x + bounds.width <= 391, 'Long-name department dialog fits a 390px viewport')
    await capture('department-long-name-narrow')
    await page.keyboard.press('Escape'); await department.waitFor({ state: 'hidden' })
    await page.setViewportSize({ width: 1280, height: 800 })

    await go('basedata-product')
    await page.getByRole('button', { name: '导入日志', exact: true }).click()
    const logs = page.getByRole('dialog', { name: '产品导入日志', exact: true }); await logs.waitFor()
    await logs.getByText(`第1页-${hostile}.csv`, { exact: true }).waitFor()
    check(await logs.locator('img').count() === 0, 'Hostile filename/log details remain text, not an image or event handler')
    await logs.getByRole('button', { name: '下一页', exact: true }).click()
    await logs.getByRole('alert').filter({ hasText: '日志翻页故障-本地注入' }).waitFor()
    check((await logs.locator('.pagination-bar').innerText()).includes('1/2'), 'Failed next-page load keeps the previous successful page number')
    failLogPageTwo = false
    await logs.getByRole('button', { name: '重试', exact: true }).click()
    await logs.getByText(`第2页-${hostile}.csv`, { exact: true }).waitFor()
    check((await logs.locator('.pagination-bar').innerText()).includes('2/2'), 'Retry commits the intended page together with its records')
    await page.keyboard.press('Escape'); await logs.waitFor({ state: 'hidden' })
    await page.getByRole('button', { name: '切换深色主题', exact: true }).click(); await settle()
    await page.getByRole('button', { name: '导入日志', exact: true }).click(); await logs.waitFor()
    await logs.getByRole('button', { name: '下一页', exact: true }).click()
    await logs.getByText(`第2页-${hostile}.csv`, { exact: true }).waitFor()
    await page.setViewportSize({ width: 390, height: 844 }); await settle()
    const logBounds = await logs.boundingBox()
    check(logBounds && logBounds.x >= 0 && logBounds.x + logBounds.width <= 391, 'Dark narrow log dialog fits while its wide table scrolls internally')
    await capture('logs-dark-narrow')
    await page.keyboard.press('Escape'); await logs.waitFor({ state: 'hidden' })
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.getByRole('button', { name: '切换浅色主题', exact: true }).click(); await settle()

    await go('sysconfig-user')
    const downloadEvent = page.waitForEvent('download')
    await page.getByRole('button', { name: '导出Excel', exact: true }).click()
    const download = await downloadEvent
    check(/\.xlsx$/i.test(download.suggestedFilename()), 'Default user download is XLSX rather than unsafe CSV')
    const downloadPath = `${output}/users-business-columns.xlsx`
    await download.saveAs(downloadPath); downloads.push(downloadPath)
    check((await page.locator('.page-motion-stage').innerText()).includes('密码哈希'), 'Raw credential backup risk remains visible beside its separate action')
    await capture('users-safe-export')

    await page.emulateMedia({ reducedMotion: 'reduce' }); await settle()
    const motion = await page.getByRole('button', { name: '导出Excel', exact: true }).evaluate(element => getComputedStyle(element).transitionDuration)
    check(motion.split(',').every(value => parseFloat(value) === 0), 'Reduced-motion settings remove action transitions')
    await page.getByRole('button', { name: '账户菜单', exact: true }).click()
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
    await page.getByRole('alert').filter({ hasText: '退出故障-本地注入' }).waitFor()
    check(await page.getByRole('button', { name: '重试退出', exact: true }).isVisible()
      && await page.getByRole('button', { name: '仅退出本机', exact: true }).isVisible(), 'Failed remote logout leaves the session and an explicit retry/local-only choice')
    await capture('logout-retryable')
    const retryLogout = page.locator('.user-dropdown button[aria-busy]')
    await retryLogout.click()
    await didStartLogout
    check(await retryLogout.isDisabled(), 'Retrying logout locks repeat activation while the request is pending')
    await page.keyboard.press('Enter'); check(logoutCalls === 2 && logoutCaptured, 'Both logout attempts use the captured session and no duplicate write')
    releaseLogout(); await page.getByRole('textbox', { name: '工厂代码', exact: true }).waitFor()
    check(await page.evaluate(() => window.__fixtureAttack) === undefined, 'No injected markup executed')
    check(errors.length === 0, `No browser application error: ${errors.join('; ')}`)
    check(outside.length === 0, 'No request left the explicitly isolated origin')
    return { fixture: 'management-ui', passed: checks.length, checks, errors, outside, clears, logoutCalls, downloads,
      originalExportName: originalName, screenshots: output, scope: 'Production dist + simulated API/faults, not server authorization or a real native designer' }
  } finally {
    releaseKeys(); releaseValue(); releaseClear(); releaseLogout()
    for (const pattern of patterns) await page.unroute(pattern)
    page.removeListener('pageerror', onError); page.removeListener('request', onRequest)
  }
}
