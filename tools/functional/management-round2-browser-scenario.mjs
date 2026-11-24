async (page) => {
  // Run from the repository root in its root-owned Playwright fixture page.
  // No login or session replacement; two declared GET 503s are fault injections.
  const initialUrl = new URL(page.url());
  const port = Number(initialUrl.port);
  if (initialUrl.protocol !== 'http:' || initialUrl.hostname !== '127.0.0.2'
    || initialUrl.username || initialUrl.password || !/^\d+$/.test(initialUrl.port)
    || !Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error('Expected an HTTP 127.0.0.2 fixture page with an explicit valid port and no userinfo.');
  }
  const origin = initialUrl.origin;
  const artifacts = 'output/playwright/management-functional';
  const unique = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`.toUpperCase();
  const departmentCode = `R2-UI-${unique}`;
  const departmentName = `Round2部门验收-${unique}`;
  const editedDepartmentName = `${departmentName}-已编辑`;
  const groupName = `Round2分页部门-${unique}`;
  const username = `r2_${unique.toLowerCase().replace(/-/g, '_')}`;
  const userName = `Round2用户-${unique}`;
  const csvName = `round2-users-${unique}.csv`;
  const checks = [], injectedFaults = [], errors = [];
  const ownedDepartments = new Map();
  const ownedRoutes = [];
  let token, actor, systemDepartment, failed;
  const pageError = (error) => errors.push(`Browser pageerror: ${error.message}`);
  page.on('pageerror', pageError);

  function check(condition, message) {
    if (!condition) throw new Error(message);
    checks.push(message);
  }
  function result() {
    return { fixtureOnly: true, passed: checks.length, checks: [...checks],
      injectedFaults: [...injectedFaults], errors: [...errors] };
  }
  async function remember() {
    await page.evaluate((value) => { window.__managementRound2Result = value; }, result());
  }
  async function screenshot(name) {
    await page.screenshot({ path: `${artifacts}/round2-${name}.png`, fullPage: true, animations: 'disabled' });
  }
  async function fixtureProbe() {
    if (new URL(page.url()).origin !== origin) throw new Error('Expected the same root-owned loopback fixture origin.');
    const response = await page.request.get(`${origin}/__functional-fixture`);
    if (!response.ok()) throw new Error(`Fixture probe failed (${response.status()}).`);
    const metadata = await response.json();
    if (metadata.fixture !== 'real-h2-api' || metadata.factoryCode !== 'FUNCTIONALTEST') {
      throw new Error('Refusing writes outside the isolated real-h2-api FUNCTIONALTEST fixture.');
    }
    return metadata;
  }
  async function api(path, method = 'GET', data) {
    if (!token) throw new Error('No existing fixture session token.');
    const response = await page.request.fetch(`${origin}/api${path}`, {
      method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      ...(data === undefined ? {} : { data })
    });
    if (!response.ok()) throw new Error(`Fixture API ${method} ${path}: HTTP ${response.status()}.`);
    const text = await response.text();
    return text ? JSON.parse(text) : null;
  }
  function matchesResponse(response, pathname, method, query = {}) {
    const url = new URL(response.url());
    return url.origin === origin && url.pathname === pathname && response.request().method() === method
      && Object.entries(query).every(([key, value]) => url.searchParams.get(key) === String(value));
  }
  async function responseAfter(action, pathname, method = 'GET', query = {}) {
    const responsePromise = page.waitForResponse(
      (response) => matchesResponse(response, pathname, method, query), { timeout: 30000 })
      .then((response) => ({ response }), (error) => ({ error }));
    await action();
    const outcome = await responsePromise;
    if (outcome.error) throw outcome.error;
    return outcome.response;
  }
  function departmentsPanel() {
    return page.locator('section.content:visible').filter({ has: page.locator('#department-list-table') }).first();
  }
  function usersPanel() {
    return page.locator('section.content:visible').filter({ has: page.locator('#user-list-table') }).first();
  }
  function departmentRow(code) {
    return page.locator('#department-list-table tbody tr').filter({
      has: page.getByRole('cell', { name: code, exact: true })
    });
  }
  async function openSettingsScreen(name, table) {
    const sidebar = page.getByRole('complementary');
    const expand = sidebar.getByRole('button', { name: /^(Expand sidebar|展开侧边栏)$/ });
    if (await expand.isVisible()) await expand.click();
    const destination = sidebar.getByRole('button', { name, exact: true });
    if (!await destination.isVisible()) {
      await sidebar.getByRole('button', { name: /^(Settings|系统配置)$/ }).click();
    }
    await destination.click();
    await page.locator(table).waitFor({ state: 'visible' });
  }
  async function openDepartments() {
    await openSettingsScreen(/^(Departments|部门)$/, '#department-list-table');
    await page.waitForFunction(() => {
      const table = document.querySelector('#department-list-table');
      return table && table.closest('.table-container').getAttribute('aria-busy') !== 'true';
    });
  }
  async function openUsers() {
    await openSettingsScreen(/^(Users|用户)$/, '#user-list-table');
  }
  async function queryDepartments(name = '', code = '') {
    const panel = departmentsPanel();
    await panel.locator('.filters input').nth(0).fill(name);
    await panel.locator('.filters input').nth(1).fill(code);
    const query = { page: 1, size: 10, ...(name ? { name } : {}), ...(code ? { code } : {}) };
    const response = await responseAfter(() => panel.getByRole('button', { name: /^(Search|查询)$/ }).click(),
      '/api/departments', 'GET', query);
    if (response.status() !== 200) throw new Error(`Department search returned ${response.status()}.`);
    const data = await response.json();
    await page.waitForFunction(() => {
      const table = document.querySelector('#department-list-table');
      return table && table.closest('.table-container').getAttribute('aria-busy') !== 'true';
    });
    return data;
  }
  async function readyDepartmentModal() {
    const modal = page.locator('.modal-backdrop .modal').filter({ has: page.locator('.department-fields') });
    await modal.waitFor({ state: 'visible' });
    await page.waitForFunction(() => {
      const trigger = document.querySelector('.modal-backdrop .multiselect-trigger');
      return trigger && !trigger.disabled;
    });
    return modal;
  }
  async function visibleDepartmentCodes() {
    return (await page.locator('#department-list-table tbody tr:has(.action-cell) td:nth-child(2)').allTextContents())
      .map((text) => text.trim());
  }
  async function importLogResponseAfter(action) {
    return responseAfter(action, '/api/system-logs/imports', 'GET', { module: 'USER', page: 1, size: 20 });
  }

  try {
    await fixtureProbe();
    token = await page.evaluate(() => localStorage.getItem('heihu_token'));
    if (!token) throw new Error('The existing page has no heihu_token; do not log in from this scenario.');
    const storedUser = await page.evaluate(() => JSON.parse(localStorage.getItem('heihu_user') || 'null'));
    if (await page.evaluate(() => localStorage.getItem('heihu_locale')) !== 'en-US') {
      throw new Error('The root-owned page must already be in English; this scenario does not replace locale preferences.');
    }
    const initialUsers = await api('/users');
    const initialDepartments = await api('/departments?page=1&size=200');
    systemDepartment = initialDepartments.items.find((item) => item.code === 'SYS-CONFIG');
    actor = initialUsers.find((item) => item.id === storedUser?.id);
    if (!actor || actor.role !== 'ADMIN' || !systemDepartment || actor.departmentId !== systemDepartment.id) {
      throw new Error('Cannot confirm the existing administrator remains assigned to SYS-CONFIG.');
    }
    check(true, 'Verified the real H2 fixture and reused the existing SYS-CONFIG administrator session without logging in.');

    await openDepartments();
    await departmentsPanel().getByRole('button', { name: /^(Create department|新建部门)$/ }).click();
    let modal = await readyDepartmentModal();
    const trigger = modal.locator('.multiselect-trigger');
    check((await trigger.innerText()).trim() === 'Select department members',
      'English department empty-member fallback is exactly Select department members.');
    await trigger.click();
    const actorOption = page.locator('.multiselect-option').filter({
      hasText: `${actor.name} (${actor.username})`
    });
    await actorOption.waitFor({ state: 'visible' });
    check((await actorOption.locator('span').innerText()).trim() === `${actor.name} (${actor.username})`,
      'Department member choices preserve the administrator business name and account rather than translating them.');
    await trigger.press('Escape');
    await modal.locator('.department-fields input').nth(0).fill(departmentName);
    await modal.locator('.department-fields input').nth(1).fill(departmentCode);
    const createdResponse = await responseAfter(() => modal.getByRole('button', { name: /^Save$/ }).click(),
      '/api/departments', 'POST');
    if (createdResponse.status() !== 200) throw new Error('Actual UI department creation did not succeed.');
    const created = await createdResponse.json();
    ownedDepartments.set(created.id, departmentCode);
    await modal.waitFor({ state: 'hidden' });
    let searched = await queryDepartments(departmentName, departmentCode);
    await departmentRow(departmentCode).waitFor({ state: 'visible' });
    check(searched.total === 1 && searched.items[0].id === created.id,
      'Actual department Create and Search returned the newly persisted record, not a fabricated UI row.');
    check((await departmentRow(departmentCode).locator('td').nth(0).innerText()).trim() === departmentName,
      'English department table preserves the Chinese business department name.');
    await screenshot('department-created');

    await page.reload({ waitUntil: 'domcontentloaded' });
    await openDepartments();
    searched = await queryDepartments(departmentName, departmentCode);
    await departmentRow(departmentCode).waitFor({ state: 'visible' });
    check(searched.total === 1 && searched.items[0].id === created.id,
      'Department creation survives a full document reload and a fresh real API search.');
    await departmentRow(departmentCode).getByRole('button', { name: /^Edit$/ }).click();
    modal = await readyDepartmentModal();
    check(await modal.locator('.department-fields input').nth(0).inputValue() === departmentName,
      'Actual department Edit loads the saved business name before modifying it.');
    await modal.locator('.department-fields input').nth(0).fill(editedDepartmentName);
    const editedResponse = await responseAfter(() => modal.getByRole('button', { name: /^Save$/ }).click(),
      `/api/departments/${created.id}`, 'PUT');
    if (editedResponse.status() !== 200) throw new Error('Actual UI department edit did not succeed.');
    await modal.waitFor({ state: 'hidden' });
    await page.reload({ waitUntil: 'domcontentloaded' });
    await openDepartments();
    searched = await queryDepartments(editedDepartmentName, departmentCode);
    await departmentRow(departmentCode).waitFor({ state: 'visible' });
    check(searched.total === 1 && searched.items[0].name === editedDepartmentName,
      'Actual department edit is persisted and read back after another full document reload.');
    await screenshot('department-edited');
    await departmentRow(departmentCode).getByRole('button', { name: /^Delete$/ }).click();
    const confirmation = page.locator('.delete-confirm-dialog');
    await confirmation.waitFor({ state: 'visible' });
    check((await confirmation.locator('.delete-confirm-dialog__message').innerText()).includes(editedDepartmentName),
      'Actual dangerous confirmation names the owned department, not SYS-CONFIG.');
    const deletedResponse = await responseAfter(() => confirmation.locator('button.btn--danger').click(),
      `/api/departments/${created.id}`, 'DELETE');
    if (![200, 204].includes(deletedResponse.status())) throw new Error('Actual UI department deletion did not succeed.');
    await departmentRow(departmentCode).waitFor({ state: 'hidden' });
    const deleted = await api(`/departments?code=${encodeURIComponent(departmentCode)}&page=1&size=10`);
    check(deleted.total === 0, 'Actual department Delete is confirmed absent by a separate real API read.');
    ownedDepartments.delete(created.id);
    await remember();

    const seeded = [];
    for (let index = 1; index <= 13; index++) {
      const code = `R2-P-${unique}-${String(index).padStart(2, '0')}`;
      const record = await api('/departments', 'POST', { name: `${groupName}-${index}`, code, userIds: [] });
      ownedDepartments.set(record.id, code);
      seeded.push(record);
    }
    searched = await queryDepartments(groupName);
    await departmentRow(seeded[12].code).waitFor({ state: 'visible' });
    const firstCodes = await visibleDepartmentCodes();
    check(searched.total === 13 && searched.items.length === 10 && firstCodes.length === 10,
      'Department name search displays 10 actual first-page rows out of 13 uniquely owned fixture departments.');
    await departmentsPanel().locator('.filters input').nth(0).fill(`Unsubmitted-${unique}`);
    const secondResponse = await responseAfter(() => departmentsPanel().getByRole('button', { name: /^Next page$/ }).click(),
      '/api/departments', 'GET', { name: groupName, page: 2, size: 10 });
    if (secondResponse.status() !== 200) throw new Error('Real department second-page request failed.');
    const secondPage = await secondResponse.json();
    await page.waitForFunction(() => {
      const table = document.querySelector('#department-list-table');
      return table && table.closest('section.content').querySelector('.page-num').textContent.trim() === '2'
        && table.closest('.table-container').getAttribute('aria-busy') !== 'true';
    });
    const secondCodes = await visibleDepartmentCodes();
    check(secondPage.total === 13 && secondPage.items.length === 3 && secondCodes.length === 3
      && !secondCodes.some((code) => firstCodes.includes(code)),
      'Department Next page loads the remaining three persisted records without duplicating page one.');
    check(new URL(secondResponse.url()).searchParams.get('name') === groupName
      && await departmentsPanel().locator('.filters input').nth(0).inputValue() === `Unsubmitted-${unique}`,
      'Unsubmitted department filter text does not contaminate the applied query used by pagination.');
    await screenshot('department-page-two');

    const target = seeded[0];
    const departmentFault = 'Injected department GET 503 (fixture only)';
    const departmentRoute = `${origin}/api/departments?*`;
    let departmentFaultUsed = false;
    const departmentRouteHandler = async (route) => {
      const request = route.request(), url = new URL(request.url());
      if (!departmentFaultUsed && request.method() === 'GET' && url.searchParams.get('name') === groupName
        && url.searchParams.get('code') === target.code && url.searchParams.get('page') === '1') {
        departmentFaultUsed = true;
        injectedFaults.push({ endpoint: '/api/departments', method: 'GET', status: 503, injected: true,
          query: { name: groupName, code: target.code, page: 1, size: 10 } });
        await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: departmentFault }) });
      } else await route.continue();
    };
    await page.route(departmentRoute, departmentRouteHandler);
    ownedRoutes.push([departmentRoute, departmentRouteHandler]);
    await departmentsPanel().locator('.filters input').nth(0).fill(groupName);
    await departmentsPanel().locator('.filters input').nth(1).fill(target.code);
    const failedSearch = await responseAfter(() => departmentsPanel().getByRole('button', { name: /^Search$/ }).click(),
      '/api/departments', 'GET', { name: groupName, code: target.code, page: 1, size: 10 });
    const departmentAlert = departmentsPanel().getByRole('alert').filter({ hasText: departmentFault });
    await departmentAlert.waitFor({ state: 'visible' });
    check(failedSearch.status() === 503 && departmentFaultUsed
      && JSON.stringify(await visibleDepartmentCodes()) === JSON.stringify(secondCodes)
      && (await departmentsPanel().locator('.page-num').innerText()).trim() === '2',
      'Explicitly injected department GET 503 preserves the last successful second page and page number.');
    check(await departmentsPanel().getByRole('button', { name: /^Next page$/ }).isDisabled(),
      'Department pagination is blocked while the failed search awaits Retry.');
    await screenshot('department-injected-503');
    const retried = await responseAfter(() => departmentAlert.getByRole('button', { name: /^Retry$/ }).click(),
      '/api/departments', 'GET', { name: groupName, code: target.code, page: 1, size: 10 });
    const retryData = await retried.json();
    await departmentRow(target.code).waitFor({ state: 'visible' });
    await departmentAlert.waitFor({ state: 'hidden' });
    check(retried.status() === 200 && retryData.total === 1 && retryData.items[0].id === target.id,
      'Department Retry repeats the failed exact query and displays its real persisted match.');
    await screenshot('department-retry');
    await remember();

    await openUsers();
    const csv = `账号,姓名,角色,手机号,部门,密码,虚拟用户,状态\r\n${username},${userName},PRODUCTION,,,FixtureOnlyPass123,否,启用\r\n`;
    const importResponsePromise = page.waitForResponse((response) => matchesResponse(response, '/api/users/import', 'POST'))
      .then((response) => ({ response }), (error) => ({ error }));
    const logSavePromise = page.waitForResponse((response) => matchesResponse(response, '/api/system-logs/imports', 'POST'))
      .then((response) => ({ response }), (error) => ({ error }));
    // Directly target the observed hidden input; do not open a native file chooser.
    await usersPanel().locator('input[type="file"][accept=".csv"]').setInputFiles({
      name: csvName, mimeType: 'text/csv', buffer: Buffer.from(csv, 'utf8')
    });
    const importOutcome = await importResponsePromise;
    if (importOutcome.error) throw importOutcome.error;
    const importResponse = importOutcome.response;
    const imported = await importResponse.json();
    check(importResponse.status() === 200 && imported.total === 1 && imported.success === 1 && imported.fail === 0,
      'Real hidden CSV input executes the frontend import handler and backend saves exactly one new PRODUCTION user.');
    const logOutcome = await logSavePromise;
    if (logOutcome.error) throw logOutcome.error;
    const logSaved = logOutcome.response;
    check(logSaved.status() === 200,
      'The actual CSV import handler saves its USER import history through the real system-logs API.');
    await usersPanel().locator('.filters input').nth(1).fill(username);
    const importedRow = page.locator('#user-list-table tbody tr').filter({
      has: page.getByRole('cell', { name: username, exact: true })
    });
    await importedRow.waitFor({ state: 'visible' });
    check((await importedRow.locator('td').nth(0).innerText()).trim() === userName,
      'English user table preserves the imported Chinese business name.');
    const savedUser = (await api('/users')).find((item) => item.username === username);
    check(savedUser && savedUser.name === userName && savedUser.role === 'PRODUCTION' && savedUser.departmentId == null,
      'Imported user is independently read back from real persistence with the intended role and no changed administrator membership.');
    await screenshot('user-import');
    await page.reload({ waitUntil: 'domcontentloaded' });
    await openUsers();
    await usersPanel().locator('.filters input').nth(1).fill(username);
    await importedRow.waitFor({ state: 'visible' });
    check((await importedRow.locator('td').nth(0).innerText()).trim() === userName,
      'The imported user remains visible after a full document reload.');
    const logResponse = await importLogResponseAfter(() => usersPanel().getByRole('button', { name: /^(Import logs?|导入日志)$/ }).click());
    const logModal = page.locator('.modal-backdrop .modal').filter({
      has: page.getByRole('heading', { name: /^(Import logs?|导入日志)$/ })
    });
    const historyRow = logModal.locator('.import-history-table tbody tr').filter({ hasText: csvName });
    await historyRow.waitFor({ state: 'visible' });
    check(logResponse.status() === 200 && await logModal.locator('.import-result').count() === 0,
      'After reload, the actual Import log modal reads persisted server history rather than the previous in-memory import result.');
    check((await historyRow.locator('td').nth(2).innerText()).trim() === '1'
      && (await historyRow.locator('td').nth(3).innerText()).trim() === '1'
      && (await historyRow.locator('td').nth(4).innerText()).trim() === '0',
      'Persisted import-history row shows the exact file, one total, one success and zero failures.');
    await screenshot('user-import-log-persisted');
    await logModal.locator('.modal-header button.close-btn').click();

    const logFault = 'Injected USER import-history GET 503 (fixture only)';
    const logRoute = `${origin}/api/system-logs/imports?*`;
    let logFaultUsed = false;
    const logRouteHandler = async (route) => {
      const request = route.request(), url = new URL(request.url());
      if (!logFaultUsed && request.method() === 'GET' && url.searchParams.get('module') === 'USER'
        && url.searchParams.get('page') === '1' && url.searchParams.get('size') === '20') {
        logFaultUsed = true;
        injectedFaults.push({ endpoint: '/api/system-logs/imports', method: 'GET', status: 503, injected: true,
          query: { module: 'USER', page: 1, size: 20 } });
        await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: logFault }) });
      } else await route.continue();
    };
    await page.route(logRoute, logRouteHandler);
    ownedRoutes.push([logRoute, logRouteHandler]);
    const failedLogs = await importLogResponseAfter(() => usersPanel().getByRole('button', { name: /^(Import logs?|导入日志)$/ }).click());
    const logAlert = logModal.getByRole('alert').filter({ hasText: logFault });
    await logAlert.waitFor({ state: 'visible' });
    check(failedLogs.status() === 503 && logFaultUsed && await historyRow.isVisible(),
      'Explicitly injected USER import-history GET 503 keeps the last persisted history row available.');
    await screenshot('user-import-log-injected-503');
    const retriedLogs = await importLogResponseAfter(() => logAlert.getByRole('button', { name: /^Retry$/ }).click());
    await logAlert.waitFor({ state: 'hidden' });
    await historyRow.waitFor({ state: 'visible' });
    check(retriedLogs.status() === 200,
      'Import-history Retry requests USER page one with size twenty and restores real server history.');
    await screenshot('user-import-log-retry');
    await logModal.locator('.modal-header button.close-btn').click();

    await openDepartments();
    await departmentsPanel().getByRole('button', { name: /^Create department$/ }).click();
    modal = await readyDepartmentModal();
    await modal.locator('.multiselect-trigger').click();
    const importedOption = page.locator('.multiselect-option').filter({ hasText: `${userName} (${username})` });
    await importedOption.waitFor({ state: 'visible' });
    await importedOption.locator('input[type="checkbox"]').check();
    check((await modal.locator('.multiselect-trigger').innerText()).trim() === userName,
      'Selected department-member text preserves the imported business name; only the empty fallback is translated.');
    await modal.locator('.multiselect-trigger').press('Escape');
    await modal.getByRole('button', { name: /^Cancel$/ }).click();

    const finalUsers = await api('/users');
    const finalActor = finalUsers.find((item) => item.id === actor.id);
    const finalSystem = (await api('/departments?code=SYS-CONFIG&page=1&size=10')).items.find((item) => item.id === systemDepartment.id);
    check(finalActor && finalActor.departmentId === actor.departmentId && finalActor.username === actor.username
      && finalSystem && finalSystem.name === systemDepartment.name
      && await page.evaluate(() => localStorage.getItem('heihu_token')) === token,
      'All real UI checks preserve the original administrator token, account and SYS-CONFIG department.');
    check(injectedFaults.length === 2 && errors.length === 0,
      'Exactly two declared GET faults were injected, with no browser runtime errors.');
  } catch (error) {
    failed = error;
    errors.push(error instanceof Error ? error.message : String(error));
    await screenshot('failed').catch(() => {});
  } finally {
    for (const [pattern, handler] of ownedRoutes) {
      await page.unroute(pattern, handler).catch((error) => errors.push(`Owned route cleanup: ${error.message}`));
    }
    if (token && ownedDepartments.size) {
      try {
        await fixtureProbe();
        for (const [id, code] of ownedDepartments) {
          if (id === systemDepartment?.id || !code.startsWith(`R2-P-${unique}-`) && code !== departmentCode) {
            throw new Error('Refusing cleanup of a department not owned by this scenario.');
          }
          const matches = await api(`/departments?code=${encodeURIComponent(code)}&page=1&size=10`);
          const record = matches.items.find((item) => item.id === id && item.code === code);
          if (record) await api(`/departments/${id}`, 'DELETE');
        }
        const remaining = await api(`/departments?name=${encodeURIComponent(groupName)}&page=1&size=20`);
        if (remaining.total !== 0) throw new Error('Owned pagination fixture departments were not fully removed.');
        ownedDepartments.clear();
        if (!failed && errors.length === 0) {
          await queryDepartments('', 'SYS-CONFIG');
          await departmentRow('SYS-CONFIG').waitFor({ state: 'visible' });
          check((await departmentRow('SYS-CONFIG').locator('td').nth(0).innerText()).trim() === systemDepartment.name,
            'Owned pagination departments are cleaned up and the original SYS-CONFIG business name remains intact in English.');
          await screenshot('cleanup-complete');
        }
      } catch (error) {
        errors.push(`Owned fixture department cleanup: ${error.message}`);
      }
    }
    page.off('pageerror', pageError);
    await remember().catch(() => {});
  }
  if (failed || errors.length) throw new Error(JSON.stringify(result()));
  return result();
}
