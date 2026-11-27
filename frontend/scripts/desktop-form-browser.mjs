// Playwright CLI run-code. Run against ui-browser-fixture.mjs on an isolated loopback port.
async page => {
  const { origin, hostname } = new URL(page.url())
  if (hostname !== '127.0.0.2') throw Error('Use the isolated management UI fixture.')
  const fixture = await (await page.request.get(origin + '/api/fixture-state')).json()
  if (fixture.fixture !== 'management-ui') throw Error('Use the isolated management UI fixture.')

  const errors = []
  const onError = error => errors.push(error.message)
  page.on('pageerror', onError)
  const checks = []
  const check = (value, label) => {
    if (!value) throw Error(label)
    checks.push(label)
  }
  const button = (name, root = page) => root.getByRole('button', { name, exact: true })
  const dialog = name => page.getByRole('dialog', { name, exact: true })
  const textbox = (name, root = page) => root.getByRole('textbox', { name, exact: true })
  const screenshot = name => page.screenshot({ path: 'output/playwright/business-ui/forms/' + name + '.png', animations: 'disabled' })
  const focused = element => element.evaluate(node => node === document.activeElement)
  await page.setViewportSize({ width: 1280, height: 800 })
  if (await textbox('工厂代码').isVisible()) {
    await textbox('工厂代码').fill('UIFIXTURE')
    await textbox('用户名').fill('admin')
    await textbox('密码').fill('fixture-27')
    await button('登录').click()
  }
  await page.locator('.workspace-loading-overlay').waitFor({ state: 'hidden' })
  const simulateSaveFailure = async (target, submit, form, draft, message) => {
    const requests = []
    let finish
    const pause = new Promise(resolve => { finish = resolve })
    await page.route(target, async route => {
      if (route.request().method() !== 'POST') return route.fallback()
      requests.push(route.request().postDataJSON())
      await pause
      await route.fulfill({ status: 503, json: { message } })
    })
    try {
      const value = await draft.inputValue()
      await submit.click()
      await form.getByRole('status').getByText('保存中...', { exact: true }).waitFor()
      check(await form.getAttribute('aria-busy') === 'true' && await button('保存中...', form).isDisabled(), message + ': visible pending state locks save')
      await page.keyboard.press('Escape')
      check(await form.isVisible(), message + ': Escape cannot leave while saving')
      await form.evaluate(node => {
        const form = node.querySelector('form')
        if (form) form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
        else node.querySelector('.modal-footer button.primary-btn').dispatchEvent(new MouseEvent('click', { bubbles: true }))
      })
      finish()
      await form.getByRole('alert').getByText(message, { exact: true }).waitFor()
      check(requests.length === 1 && await draft.inputValue() === value, message + ': no duplicate write; error remains in the form with its draft')
    } finally {
      finish()
      await page.unroute(target)
    }
  }
  try {
    await button('工单列表').click()
    const createOrder = button('创建工单')
    await createOrder.click()
    const order = dialog('创建工单')
    const closeOrder = button('关闭', order)
    const saveOrder = button('保存工单', order)
    const code = textbox('工单编号', order)
    await closeOrder.waitFor()
    await page.waitForFunction(() => document.activeElement?.matches('.workorder-modal[role="dialog"] button.close-btn'))
    check(await focused(closeOrder), 'Create work order receives initial focus')
    await page.keyboard.press('Shift+Tab')
    check(await focused(saveOrder), 'Shift+Tab wraps to save instead of the underlying table')
    await page.keyboard.press('Tab')
    check(await focused(closeOrder), 'Tab wraps back to close')
    await button('生产任务', order).click()
    const addTask = button('添加生产任务', order)
    await addTask.click()
    await dialog('选择生产任务').waitFor()
    await page.keyboard.press('Escape')
    await dialog('选择生产任务').waitFor({ state: 'hidden' })
    check(await order.isVisible() && await focused(addTask), 'Escape closes only the task picker and restores its trigger')
    await button('基本信息', order).click()
    await code.fill('UI-QA-NOT-WRITTEN')
    await order.getByRole('combobox', { name: '产品信息', exact: true }).selectOption('1')
    await order.getByRole('spinbutton', { name: '计划数', exact: true }).fill('5')
    await simulateSaveFailure('**/api/work-orders', saveOrder, order, code, '夹具：工单未保存')
    await screenshot('work-order-save-error')
    await closeOrder.click()
    const confirmation = dialog('放弃未保存的修改？')
    await confirmation.waitFor()
    await screenshot('work-order-discard')
    await page.keyboard.press('Escape')
    await confirmation.waitFor({ state: 'hidden' })
    check(await code.inputValue() === 'UI-QA-NOT-WRITTEN' && await focused(closeOrder), 'Declining discard preserves the form and returns focus')
    await button('取消', order).click()
    await button('放弃修改', confirmation).click()
    await order.waitFor({ state: 'hidden' })
    check(await focused(createOrder), 'Confirming discard returns focus to the work-order opener')
    await createOrder.click()
    check(await code.inputValue() === '', 'A fresh form does not inherit a discarded value')
    await page.keyboard.press('Escape')
    await order.waitFor({ state: 'hidden' })
    check(await focused(createOrder), 'An unchanged form closes immediately with Escape')

    await button('基础数据').click()
    await page.locator('.sidebar').getByRole('button', { name: '单位', exact: true }).click()
    const createUnit = button('创建单位')
    await createUnit.click()
    const unit = dialog('创建单位')
    const name = unit.getByPlaceholder('请输入单位名称')
    await name.waitFor()
    check(await focused(name), 'Unit dialog focuses the name input')
    await name.fill('UI-QA-UNIT-NOT-WRITTEN')
    await simulateSaveFailure('**/api/units', button('保存单位', unit), unit, name, '夹具：单位未保存')
    await screenshot('unit-save-error')
    await page.keyboard.press('Escape')
    await button('放弃修改', confirmation).click()
    await unit.waitFor({ state: 'hidden' })
    check(await focused(createUnit), 'Unit discard returns focus to its opener')
    check(errors.length === 0, 'No browser script errors: ' + errors.join('; '))
    return { checks: checks.length, passed: checks }
  } finally {
    page.removeListener('pageerror', onError)
  }
}
