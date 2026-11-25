import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, KeepAlive } from 'vue'
import { flushPromises, mount, shallowMount, type VueWrapper } from '@vue/test-utils'
import BaseDataPage from '../BaseDataPage.vue'
import { setUiLocale } from '../../i18n'

const api = vi.hoisted(() => Object.fromEntries([
  'fetchUnits', 'createUnit', 'deleteUnit', 'fetchDefects', 'createDefect', 'deleteDefect',
  'fetchProcesses', 'createProcess', 'deleteProcess', 'fetchProcessRoutes', 'createProcessRoute',
  'deleteProcessRoute', 'fetchDepartments', 'fetchSystemImportLogs', 'createSystemImportLog'
].map(name => [name, vi.fn()])))
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), ...api }))

const readers: TestReader[] = [], wrappers: VueWrapper<any>[] = []
let autoRead = true
class TestReader {
  static LOADING = 1
  readyState = 0
  text = ''
  error: Error | null = null
  onload: ((event: any) => unknown) | null = null
  onerror: (() => unknown) | null = null
  onabort: (() => unknown) | null = null
  finished: Promise<unknown> = Promise.resolve()
  abort = vi.fn(() => { this.readyState = 2; this.onabort?.() })
  readAsText(file: File & { fixtureText: string }) {
    this.text = file.fixtureText; this.readyState = 1; readers.push(this)
    if (autoRead) queueMicrotask(() => this.deliver())
  }
  deliver() {
    this.readyState = 2
    this.finished = Promise.resolve(this.onload?.({ target: { result: this.text } }))
    return this.finished
  }
}
function deferred<T = any>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(yes => { resolve = yes })
  return { promise, resolve }
}
type Scenario = {
  kind: string; method: string; create: string; header: string; rows: string[]
  verify: (payload: any) => void
}
const scenarios: Scenario[] = [
  { kind: 'unit', method: 'handleImportUnits', create: 'createUnit', header: '单位名称,备注',
    rows: ['"=原单位","含,逗号与""引号""\r\n第二行"', '第二单位,原备注'],
    verify: payload => expect(payload).toEqual({ name: '=原单位', remark: '含,逗号与"引号"\r\n第二行' }) },
  { kind: 'defect', method: 'handleImportDefects', create: 'createDefect', header: '不良品项编号,不良品项名称',
    rows: ['001,"=原不良项\n原文"', '002,第二不良项'],
    verify: payload => expect(payload).toEqual({ code: '001', name: '=原不良项\n原文' }) },
  { kind: 'process', method: 'handleImportProcesses', create: 'createProcess',
    header: '工序编号,工序名称,报工权限,报工数配比,任务打卡,要求',
    rows: ['001,=原工序,"all,dept-7",2,开启,"原要求\r\n下一行"', '002,第二工序,all,1,关闭,原文'],
    verify: payload => expect(payload).toMatchObject({ code: '001', name: '=原工序',
      reportPermission: ['all', 'dept-7'], reportRatio: 2, taskPunchIn: true, requirements: '原要求\r\n下一行' }) },
  { kind: 'route', method: 'handleImportRoutes', create: 'createProcessRoute', header: '工艺路线编号,工艺路线名称,工序列表',
    rows: ['001,=原路线,装配', '002,第二路线,装配'],
    verify: payload => expect(payload).toMatchObject({ code: '001', name: '=原路线',
      steps: [{ type: 'process', processId: 7, reportRatio: 1, remark: '' }] }) }
]
type Change = 'session' | 'deactivate' | 'unmount'
type PageHandle = Pick<VueWrapper<any>, 'setProps' | 'unmount'>
async function page(kind: string, cached = false) {
  if (!cached) {
    const wrapper = shallowMount(BaseDataPage, { props: { token: 'fixture-one', activeBasedataMenu: kind, users: [] } })
    wrappers.push(wrapper); await flushPromises()
    return { wrapper, host: wrapper }
  }
  const Other = defineComponent({ render: () => h('p', 'Other') })
  const Host = defineComponent({ props: { show: Boolean }, render() {
    return h(KeepAlive, null, { default: () => this.show
      ? h(BaseDataPage, { token: 'fixture-one', activeBasedataMenu: kind, users: [] }) : h(Other) })
  } })
  const host = mount(Host, { props: { show: true }, global: { stubs: {
    UiIcon: true, TableConfigButton: true, ColumnSettingsPanel: true, DeleteConfirmDialog: true, SystemLogsDialog: true
  } } })
  wrappers.push(host); await flushPromises()
  return { wrapper: host.getComponent(BaseDataPage), host }
}
async function changePage(change: Change, wrapper: PageHandle, host: PageHandle) {
  if (change === 'session') await wrapper.setProps({ token: 'fixture-two' })
  else if (change === 'deactivate') await host.setProps({ show: false })
  else host.unmount()
  await flushPromises()
}
async function choose(vm: any, scenario: Scenario, csv = [scenario.header, ...scenario.rows].join('\n')) {
  const file = Object.assign(new File([csv], 'fixture.csv', { type: 'text/csv' }), { fixtureText: csv })
  await vm[scenario.method]({ target: { files: [file] } })
  await flushPromises()
  return readers[readers.length - 1]!
}
beforeEach(() => {
  vi.resetAllMocks(); readers.length = 0; autoRead = true
  setUiLocale('zh-CN')
  for (const [name, mock] of Object.entries(api)) {
    if (name.startsWith('fetch')) mock.mockResolvedValue({ items: [], total: 0 })
    else mock.mockImplementation(async (_token, payload) => ({ id: 1, ...payload }))
  }
  api.fetchProcesses.mockResolvedValue({ items: [{ id: 7, code: 'P7', name: '装配', collectData: [] }], total: 1 })
  vi.stubGlobal('FileReader', TestReader)
})
afterEach(() => {
  wrappers.splice(0).reverse().forEach(wrapper => wrapper.unmount())
  vi.restoreAllMocks(); vi.unstubAllGlobals()
})

describe.each(scenarios)('$kind captured import lifecycle', scenario => {
  it.each(['session', 'menu'])('keeps a still-open picker bound to its original %s before a late file change', async change => {
    const { wrapper } = await page(scenario.kind), vm = wrapper.vm as any
    vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => {})
    const trigger = `trigger${scenario.kind[0].toUpperCase()}${scenario.kind.slice(1)}Import`
    vm[trigger]()
    if (change === 'session') await wrapper.setProps({ token: 'fixture-two' })
    else await wrapper.setProps({ activeBasedataMenu: scenario.kind === 'unit' ? 'defect' : 'unit' })
    await choose(vm, scenario)
    if (readers.length) await readers[readers.length - 1]!.finished
    expect(api[scenario.create]).not.toHaveBeenCalled()
    expect(api.createSystemImportLog).not.toHaveBeenCalled()
    expect(vm.baseDataOperationBusy).toBe(false)
  })

  it('stops old import rows after a same-instance menu change while a row write is pending', async () => {
    const { wrapper } = await page(scenario.kind), vm = wrapper.vm as any
    const pending = deferred(); api[scenario.create].mockReturnValueOnce(pending.promise)
    const reader = await choose(vm, scenario)
    expect(api[scenario.create]).toHaveBeenCalledTimes(1)
    await wrapper.setProps({ activeBasedataMenu: scenario.kind === 'unit' ? 'defect' : 'unit' })
    pending.resolve({ id: 1 }); await reader.finished; await flushPromises()
    expect(api[scenario.create]).toHaveBeenCalledTimes(1)
    expect(api.createSystemImportLog).not.toHaveBeenCalled()
    expect(vm.baseDataOperationBusy).toBe(false)
  })

  it('accepts an explicit new picker after the previous account picker became invalid', async () => {
    const { wrapper } = await page(scenario.kind), vm = wrapper.vm as any
    vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => {})
    const trigger = `trigger${scenario.kind[0].toUpperCase()}${scenario.kind.slice(1)}Import`
    vm[trigger]()
    await wrapper.setProps({ token: 'fixture-two' }); await flushPromises()
    vm[trigger]()
    const reader = await choose(vm, scenario)
    await reader.finished
    expect(api[scenario.create]).toHaveBeenCalledTimes(2)
    expect(api[scenario.create].mock.calls.every(call => call[0] === 'fixture-two')).toBe(true)
    expect(vm.baseDataOperationBusy).toBe(false)
  })

  it('does not revive an old import when a same-instance menu leaves and returns before its response', async () => {
    const { wrapper } = await page(scenario.kind), vm = wrapper.vm as any
    const pending = deferred(); api[scenario.create].mockReturnValueOnce(pending.promise)
    const reader = await choose(vm, scenario)
    await wrapper.setProps({ activeBasedataMenu: scenario.kind === 'unit' ? 'defect' : 'unit' })
    await wrapper.setProps({ activeBasedataMenu: scenario.kind })
    pending.resolve({ id: 1 }); await reader.finished; await flushPromises()
    expect(api[scenario.create]).toHaveBeenCalledTimes(1)
    expect(api.createSystemImportLog).not.toHaveBeenCalled()
    expect(vm.baseDataOperationBusy).toBe(false)
  })

  it('preserves quoted business text and machine-value mappings while keeping every write on the captured account', async () => {
    const { wrapper } = await page(scenario.kind), vm = wrapper.vm as any
    const reader = await choose(vm, scenario)
    await reader.finished; await flushPromises()
    expect(api[scenario.create]).toHaveBeenCalledTimes(2)
    expect(api[scenario.create].mock.calls.every(call => call[0] === 'fixture-one')).toBe(true)
    scenario.verify(api[scenario.create].mock.calls[0][1])
    expect(api.createSystemImportLog).toHaveBeenCalledWith('fixture-one', expect.objectContaining({
      totalCount: 2, successCount: 2, failCount: 0, status: '成功'
    }))
    expect(vm[`${scenario.kind}Importing`]).toBe(false)
    expect(vm.baseDataOperationBusy).toBe(false)
  })

  it('validates an invalid final CSV row before sending even its valid prefix and releases the read lock', async () => {
    const { wrapper } = await page(scenario.kind), vm = wrapper.vm as any
    const reader = await choose(vm, scenario, `${scenario.header}\n${scenario.rows[0]}\n"unterminated`)
    await reader.finished
    expect(api[scenario.create]).not.toHaveBeenCalled()
    expect(api.createSystemImportLog).not.toHaveBeenCalled()
    expect([...(wrapper.emitted('error') || []), ...(wrapper.emitted('notice') || [])].flat().join(' ')).toContain('引号未闭合')
    expect(vm[`${scenario.kind}Importing`]).toBe(false)
    expect(vm.baseDataOperationBusy).toBe(false)
  })

  it.each<Change>(['session', 'deactivate', 'unmount'])('stops subsequent rows, completion notices and log writes after %s changes', async change => {
    const { wrapper, host } = await page(scenario.kind, change === 'deactivate'), vm = wrapper.vm as any
    const pending = deferred(); api[scenario.create].mockReturnValueOnce(pending.promise)
    const reader = await choose(vm, scenario)
    expect(api[scenario.create]).toHaveBeenCalledTimes(1)
    await changePage(change, wrapper, host)
    pending.resolve({ id: 1 }); await reader.finished; await flushPromises()
    expect(api[scenario.create]).toHaveBeenCalledTimes(1)
    expect(api[scenario.create].mock.calls[0][0]).toBe('fixture-one')
    expect(api.createSystemImportLog).not.toHaveBeenCalled()
    expect((wrapper.emitted('notice') || []).flat().join(' ')).not.toContain('导入完成')
    expect(vm[`${scenario.kind}Importing`]).toBe(false)
    expect(vm.baseDataOperationBusy).toBe(false)
  })

  it.each<Change>(['session', 'deactivate', 'unmount'])('aborts pending FileReader work and rejects even an already captured callback after %s changes', async change => {
    const { wrapper, host } = await page(scenario.kind, change === 'deactivate'), vm = wrapper.vm as any
    autoRead = false
    const reader = await choose(vm, scenario), oldCallback = reader.onload
    expect(vm[`${scenario.kind}Importing`]).toBe(true)
    await changePage(change, wrapper, host)
    expect(reader.abort).toHaveBeenCalledTimes(1)
    expect(reader.onload).toBeNull()
    await oldCallback?.({ target: { result: reader.text } }); await flushPromises()
    expect(api[scenario.create]).not.toHaveBeenCalled()
    expect(api.createSystemImportLog).not.toHaveBeenCalled()
    expect(vm[`${scenario.kind}Importing`]).toBe(false)
  })

  it('allows one file read while pending and permits retry after an honest read error', async () => {
    const { wrapper } = await page(scenario.kind), vm = wrapper.vm as any
    autoRead = false
    const reader = await choose(vm, scenario)
    await choose(vm, scenario)
    expect(readers).toHaveLength(1)
    const upload = wrapper.findAll('button[aria-label="导入"]').find(button => button.isVisible())!
    expect(upload.attributes('disabled')).toBeDefined()
    reader.error = new Error('文件读取失败'); reader.onerror?.(); await flushPromises()
    expect(vm.baseDataOperationBusy).toBe(false)
    expect((wrapper.emitted('error') || []).flat().join(' ')).toContain('文件读取失败')
    await choose(vm, scenario)
    expect(readers).toHaveLength(2)
  })

  it('does not refresh old import logs or announce completion when account changes during log persistence', async () => {
    const { wrapper } = await page(scenario.kind), vm = wrapper.vm as any
    const pendingLog = deferred(); api.createSystemImportLog.mockReturnValueOnce(pendingLog.promise)
    const reader = await choose(vm, scenario)
    expect(api.createSystemImportLog).toHaveBeenCalledTimes(1)
    await wrapper.setProps({ token: 'fixture-two' }); await flushPromises()
    const readsAfterChange = api.fetchSystemImportLogs.mock.calls.length
    pendingLog.resolve({ id: 1 }); await reader.finished; await flushPromises()
    expect(api.fetchSystemImportLogs).toHaveBeenCalledTimes(readsAfterChange)
    expect((wrapper.emitted('notice') || []).flat().join(' ')).not.toContain('导入完成')
    expect(vm.baseDataOperationBusy).toBe(false)
  })
})

describe('base-data confirmations and lookups stay with their captured page', () => {
  const deletes = [
    { kind: 'unit', method: 'handleDeleteUnit', api: 'deleteUnit' },
    { kind: 'defect', method: 'handleDeleteDefect', api: 'deleteDefect' },
    { kind: 'process', method: 'handleDeleteProcess', api: 'deleteProcess' }
  ]
  describe.each(deletes)('$kind destructive confirmation', deletion => {
    it.each<Change>(['session', 'deactivate', 'unmount'])('cancels a still-pending confirmation on %s changes', async change => {
      const { wrapper, host } = await page(deletion.kind, change === 'deactivate'), vm = wrapper.vm as any
      const pending = vm[deletion.method]({ id: 7, name: '原对象' })
      expect(vm.deleteVisible).toBe(true)
      await changePage(change, wrapper, host)
      vm.deleteHandleConfirm(); await pending; await flushPromises()
      expect(api[deletion.api]).not.toHaveBeenCalled()
      expect(vm.deleteVisible).toBe(false)
    })

    it('captures the original target and sends one write while confirmation is processing', async () => {
      const { wrapper } = await page(deletion.kind), vm = wrapper.vm as any
      const record = { id: 7, name: '原对象' }, pendingWrite = deferred()
      api[deletion.api].mockReturnValueOnce(pendingWrite.promise)
      const pending = vm[deletion.method](record)
      record.id = 99
      vm.deleteHandleConfirm(); await flushPromises()
      await vm[deletion.method]({ id: 8, name: '另一对象' })
      expect(api[deletion.api]).toHaveBeenCalledExactlyOnceWith('fixture-one', 7)
      expect(vm.deleteLoading).toBe(true)
      pendingWrite.resolve(undefined); await pending
      expect(vm.deleteLoading).toBe(false)
      expect(vm.deleteVisible).toBe(false)
    })

    it.each<Change>(['session', 'deactivate', 'unmount'])('does not rewrite newer rows after a confirmed delete finishes late on %s changes', async change => {
      const { wrapper, host } = await page(deletion.kind, change === 'deactivate'), vm = wrapper.vm as any
      const pendingWrite = deferred(); api[deletion.api].mockReturnValueOnce(pendingWrite.promise)
      const pending = vm[deletion.method]({ id: 7, name: '原对象' })
      vm.deleteHandleConfirm(); await flushPromises()
      expect(api[deletion.api]).toHaveBeenCalledExactlyOnceWith('fixture-one', 7)
      await changePage(change, wrapper, host)
      const state = deletion.kind === 'unit' ? 'units' : deletion.kind === 'defect' ? 'defects' : 'processes'
      vm[state] = [{ id: 7, name: '较新页面数据' }]
      pendingWrite.resolve(undefined); await pending; await flushPromises()
      expect(vm[state]).toEqual([{ id: 7, name: '较新页面数据' }])
      expect(vm.deleteLoading).toBe(false)
      expect(vm.deleteVisible).toBe(false)
    })
  })

  it.each<Change>(['session', 'deactivate', 'unmount'])('makes an external route-confirm callback inert after %s changes', async change => {
    const { wrapper, host } = await page('route', change === 'deactivate'), vm = wrapper.vm as any
    vm.confirmDeleteRoute({ id: 7, name: '原路线' })
    const payload = wrapper.emitted('confirm')![0][0] as { action: () => Promise<void> }
    await changePage(change, wrapper, host)
    await payload.action()
    expect(api.deleteProcessRoute).not.toHaveBeenCalled()
  })

  it('keeps delayed route-import process references bound to the original lookup snapshot', async () => {
    const { wrapper } = await page('route'), vm = wrapper.vm as any
    autoRead = false
    const scenario = scenarios.find(item => item.kind === 'route')!
    const reader = await choose(vm, scenario)
    vm.processes = [{ id: 99, code: 'NEW', name: '装配' }]
    await reader.deliver(); await flushPromises()
    expect(api.createProcessRoute.mock.calls[0][1].steps[0].processId).toBe(7)
  })
})
