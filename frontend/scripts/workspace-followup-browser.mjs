// Playwright CLI run-code; run only against ui-browser-fixture.mjs on loopback.
async page => {
  const { hostname, origin } = new URL(page.url())
  if (hostname !== '127.0.0.2' || (await (await page.request.get(origin + '/api/fixture-state')).json()).fixture !== 'management-ui') throw Error('Use the isolated management fixture.')
  const checks = [], errors = []
  const listener = error => errors.push(error.message)
  page.on('pageerror', listener)
  const check = (condition, description) => { if (!condition) throw Error(description); checks.push(description) }
  const button = (name, target = page) => target.getByRole('button', { name, exact: true })
  const focused = locator => locator.evaluate(element => element === document.activeElement)
  const ready = async () => {
    await page.locator('.admin-shell').waitFor()
    await page.locator('.workspace-loading-overlay').waitFor({ state: 'hidden' })
  }
  const saveImage = name => page.screenshot({ path: `output/playwright/uiux-followup/${name}.png`, animations: 'disabled' })

  try {
    await page.setViewportSize({ width: 1280, height: 820 })
    await page.goto(origin)
    if (await button('登录').isVisible()) {
      await page.getByLabel('工厂代码', { exact: true }).fill('UIFIXTURE')
      await page.getByLabel('用户名', { exact: true }).fill('admin')
      await page.getByLabel('密码', { exact: true }).fill('fixture-27')
      await button('登录').click()
    }
    await ready()
    const sidebar = page.locator('.sidebar')
    await button('高级运营', sidebar).click()
    const gantt = button('排产甘特图', sidebar)
    await gantt.click()
    check(await gantt.getAttribute('aria-current') === 'page', 'Sidebar Gantt points to the actual selected section.')
    const panel = page.getByRole('tabpanel', { name: '高级运营', exact: true })
    check(await button('排产甘特图', panel).getAttribute('aria-pressed') === 'true', 'The content section is Gantt, not SOP.')
    const search = button('搜索菜单')
    await search.click()
    const query = page.getByPlaceholder('搜索菜单')
    await query.fill('现场事件')
    await query.press('Enter')
    check(await button('现场事件', sidebar).getAttribute('aria-current') === 'page', 'Search enters the specific field-event section.')
    await button('工资绩效', panel).click()
    check(await button('工资绩效', sidebar).getAttribute('aria-current') === 'page', 'In-page switch also updates the sidebar.')
    await saveImage('advanced-section')

    const advancedTab = page.getByRole('tab', { name: '高级运营', exact: true })
    await advancedTab.focus()
    await advancedTab.press('Shift+F10')
    const menu = page.getByRole('menu', { name: '标签页操作', exact: true })
    const closeTab = menu.getByRole('menuitem', { name: '关闭', exact: true })
    check(await focused(closeTab), 'Keyboard context menu moves focus to its first enabled action.')
    await page.keyboard.press('ArrowDown')
    check(await focused(menu.getByRole('menuitem', { name: '固定', exact: true })), 'Arrow keys reach tab actions.')
    await page.keyboard.press('Escape')
    await menu.waitFor({ state: 'hidden' })
    check(await focused(advancedTab), 'Escape restores the workspace tab focus.')

    const zone = button('设置时区')
    await zone.click()
    const timezone = page.getByRole('dialog', { name: '设置时区', exact: true })
    await timezone.waitFor()
    check(await timezone.evaluate(element => element.contains(document.activeElement)), 'When opening time zone, focus enters the modal.')
    await page.keyboard.press('Control+k')
    check(await timezone.evaluate(element => element.contains(document.activeElement)) && !await page.getByPlaceholder('搜索菜单').isVisible(), 'The workspace search shortcut cannot steal focus from the modal.')
    await button('确认', timezone).focus()
    await page.keyboard.press('Tab')
    check(await focused(button('关闭', timezone)), 'Time-zone dialog traps tabbing.')
    await page.keyboard.press('Escape')
    await timezone.waitFor({ state: 'hidden' })
    check(await focused(zone), 'Escape from time zone restores the opener.')

    const notification = button('通知')
    await notification.click()
    check(await notification.getAttribute('aria-expanded') === 'true', 'Notification popover exposes its open state.')
    await button('查看全部通知').focus()
    await page.keyboard.press('Escape')
    check(await notification.getAttribute('aria-expanded') === 'false' && await focused(notification), 'Notification Escape closes it from a child action.')
    await notification.click()
    await button('查看全部通知').click()
    const range = page.getByRole('group', { name: '通知范围' })
    await range.waitFor()
    await button('已归档', range).click()
    check(await button('已归档', range).getAttribute('aria-pressed') === 'true' && await page.getByRole('searchbox', { name: '搜索通知标题或内容' }).isVisible(), 'Notification scope and search have accessible names and selection.')

    await page.goto(`${origin}/?workspace=production-workorder`)
    await ready()
    const template = button('打印模板')
    await template.click()
    const editor = page.getByRole('dialog', { name: '编辑打印模板', exact: true })
    const name = editor.getByRole('textbox', { name: '模板名称' })
    await name.fill('验收标签')
    await page.evaluate(() => {
      const save = Storage.prototype.setItem
      Storage.prototype.setItem = function(key, value) {
        if (key === 'print_templates_data') throw new DOMException('Test quota error', 'QuotaExceededError')
        return save.call(this, key, value)
      }
      window.restorePrintStorage = () => { Storage.prototype.setItem = save }
    })
    await button('保存', editor).click()
    await editor.getByRole('alert').getByText('打印模板保存失败，请重试。', { exact: true }).waitFor()
    check(await name.inputValue() === '验收标签' && !await page.getByText('保存成功', { exact: true }).isVisible(), 'Storage failure keeps the draft and never claims success.')
    await saveImage('print-template-save-failure')
    await page.evaluate(() => window.restorePrintStorage())
    await button('保存', editor).click()
    await editor.waitFor({ state: 'hidden' })
    await template.click()
    check(await name.inputValue() === '验收标签', 'After a successful retry the stored template reopens correctly.')
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.setViewportSize({ width: 640, height: 780 })
    check(await editor.evaluate(element => { const b = element.getBoundingClientRect(); return b.left >= 0 && b.right <= innerWidth && getComputedStyle(element).transitionDuration === '0s' }), 'The print editor stays within a narrow viewport with motion reduced.')
    await page.keyboard.press('Escape')
    await editor.waitFor({ state: 'hidden' })
    check(await focused(template), 'Closing the unchanged template returns focus.')
    check(!errors.length, `No uncaught browser error: ${errors.join('; ')}`)
    return { passed: checks.length, checks, fixtureOnly: true }
  } finally {
    page.off('pageerror', listener)
    await page.emulateMedia({ reducedMotion: 'no-preference' })
  }
}
