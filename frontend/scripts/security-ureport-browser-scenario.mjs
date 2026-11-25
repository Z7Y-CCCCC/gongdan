// Setup for check-ureport-browser.ps1. The CLI pauses at native dialogs, so the
// PowerShell runner confirms/dismisses each dialog explicitly instead of hiding it.
// Only a blank unnamed scratch report in the isolated, authenticated H2 fixture.
async (page) => {
  const origin = new URL(page.url()).origin
  if (new URL(origin).hostname !== '127.0.0.2') throw Error('Use the isolated H2 fixture')
  const metadata = await (await page.request.get(`${origin}/__functional-fixture`)).json()
  if (metadata.fixture !== 'real-h2-api' || metadata.factoryCode !== 'FUNCTIONALTEST') throw Error('Wrong fixture')
  const iframe = page.locator('.ureport-frame'), native = page.frameLocator('.ureport-frame')
  await iframe.waitFor()
  const signature = await iframe.getAttribute('src')
  if (!signature || new URL(signature, origin).searchParams.has('_u')) throw Error('Never edit a saved template')
  await native.locator('#__save_btn').waitFor({ state: 'attached' })
  await native.locator('table.htCore tbody td:visible').first().dblclick()
  const draft = 'Native fixture draft 2026-11-25'
  const editor = native.locator('textarea.form-control:visible').first()
  await editor.fill(draft); await editor.press('Tab')
  const dirty = !(await native.locator('#__save_btn').getAttribute('class') || '').split(/\s+/).includes('disabled')
  const cell = (await native.locator('table.htCore tbody td').first().innerText()).replace(/\u00a0/g, ' ')
  if (!dirty || cell !== draft) throw Error('Native input did not reach the actual grid/unsaved signal')
  await page.screenshot({ path: 'output/playwright/management-security-real-2026-11-25/native-unsaved.png', animations: 'disabled' })
  return { fixture: 'real-h2-api', origin, signature, draft, dirty, cell, templateFile: null }
}
