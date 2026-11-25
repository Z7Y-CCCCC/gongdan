import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import DeleteConfirmDialog from '../DeleteConfirmDialog.vue'
import { setUiLocale } from '../../i18n'

let wrapper: VueWrapper<any> | undefined
beforeEach(() => setUiLocale('zh-CN'))
afterEach(() => { wrapper?.unmount(); wrapper = undefined; document.body.innerHTML = ''; setUiLocale('zh-CN') })
const button = () => document.querySelector('.delete-confirm-dialog__footer button:last-child') as HTMLButtonElement

describe('shared confirmation actions remain truthful and described', () => {
  it('keeps the legacy deletion label and safe initial cancel focus', async () => {
    wrapper = mount(DeleteConfirmDialog, { attachTo: document.body, props: { visible: true } })
    await nextTick()
    expect(button().textContent?.trim()).toBe('删除')
    expect(button().getAttribute('aria-label')).toBe('确认删除')
    expect((document.activeElement as HTMLElement).textContent?.trim()).toBe('取消')
  })

  it('names a restore operation without a delete label or destructive tone', async () => {
    wrapper = mount(DeleteConfirmDialog, { attachTo: document.body, props: {
      visible: true, title: '恢复缓存', confirmLabel: '恢复缓存', confirmIcon: 'refresh', confirmTone: 'primary'
    } as any })
    await nextTick()
    expect(button().textContent?.trim()).toBe('恢复缓存')
    expect(button().getAttribute('aria-label')).toBe('恢复缓存')
    expect(button().classList.contains('btn--primary')).toBe(true)
    expect(button().classList.contains('btn--danger')).toBe(false)
  })

  it('links the complete risk text as the dialog description', async () => {
    const message = '目标业务名称-'.repeat(80) + '。该操作不可逆。'
    wrapper = mount(DeleteConfirmDialog, { attachTo: document.body, props: { visible: true, message } })
    await nextTick()
    const dialog = document.querySelector('[role="dialog"]')!
    const description = dialog.getAttribute('aria-describedby')
    expect(description).toBeTruthy()
    expect(document.getElementById(description!)?.textContent).toBe(message)
  })

  it('announces a busy operation and blocks reactivation', async () => {
    wrapper = mount(DeleteConfirmDialog, { attachTo: document.body, props: { visible: true, loading: true } })
    await nextTick()
    expect(button().getAttribute('aria-busy')).toBe('true')
    button().click()
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('keeps business text literal instead of interpreting markup', async () => {
    const message = '<em>业务数据</em>'
    wrapper = mount(DeleteConfirmDialog, { attachTo: document.body, props: { visible: true, message } })
    await nextTick()
    expect(document.querySelector('.delete-confirm-dialog__message')?.textContent).toBe(message)
    expect(document.querySelector('.delete-confirm-dialog__message em')).toBeNull()
  })
})
