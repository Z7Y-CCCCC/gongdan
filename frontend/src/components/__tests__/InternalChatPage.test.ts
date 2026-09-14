import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils'
import InternalChatPage from '../InternalChatPage.vue'

const mocks = vi.hoisted(() => ({
  fetchInternalChatContacts: vi.fn(), fetchInternalChatMessages: vi.fn(),
  markInternalChatRead: vi.fn(), sendInternalChatMessage: vi.fn()
}))
vi.mock('../../api', () => mocks)

const contacts = [
  { id: 1, name: '甲', username: 'a', roleLabel: '管理员', unreadCount: 1 },
  { id: 2, name: '乙', username: 'b', roleLabel: '管理员', unreadCount: 1 }
]

function deferred<T>() {
  let resolve!: (result: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}

describe('internal chat request isolation', () => {
  let wrapper: VueWrapper<any> | undefined
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.fetchInternalChatContacts.mockResolvedValue(contacts.map(contact => ({ ...contact })))
    mocks.fetchInternalChatMessages.mockResolvedValue([])
    mocks.markInternalChatRead.mockResolvedValue(undefined)
    mocks.sendInternalChatMessage.mockResolvedValue({ id: 1 })
  })
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.useRealTimers()
  })

  function create() {
    wrapper = shallowMount(InternalChatPage, { props: { token: 'test-token', currentUser: null } })
    return wrapper.vm as any
  }

  it('loads the new conversation while the old one is pending and never displays old-peer messages', async () => {
    const first = deferred<any[]>()
    const second = deferred<any[]>()
    mocks.fetchInternalChatMessages.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const vm = create()
    await flushPromises()
    expect(mocks.fetchInternalChatMessages).toHaveBeenCalledWith('test-token', 1)
    vm.selectContact(contacts[1])
    await flushPromises()
    expect(mocks.fetchInternalChatMessages).toHaveBeenCalledWith('test-token', 2)
    second.resolve([{ id: 2, senderId: 2, senderName: '乙', receiverId: 9, content: '乙的消息', read: false, createdAt: '2026-09-11 12:00:00' }])
    await flushPromises()
    first.resolve([{ id: 1, senderId: 1, senderName: '甲', receiverId: 9, content: '甲的消息', read: false, createdAt: '2026-09-11 12:00:00' }])
    await flushPromises()
    expect(vm.activePeerId).toBe(2)
    expect(vm.messages.map((message: any) => message.content)).toEqual(['乙的消息'])
    expect(mocks.markInternalChatRead).toHaveBeenCalledTimes(1)
    expect(mocks.markInternalChatRead).toHaveBeenCalledWith('test-token', 2)
  })

  it('does not restore an earlier selection when a contacts refresh finishes', async () => {
    const vm = create()
    await flushPromises()
    const pending = deferred<any[]>()
    mocks.fetchInternalChatContacts.mockReturnValueOnce(pending.promise)
    const refresh = vm.loadContacts()
    vm.selectContact(contacts[1])
    pending.resolve(contacts)
    await refresh
    await flushPromises()
    expect(vm.activePeerId).toBe(2)
  })

  it('preserves a new draft after switching contacts while a previous send is pending', async () => {
    const vm = create()
    await flushPromises()
    const pending = deferred<any>()
    mocks.sendInternalChatMessage.mockReturnValueOnce(pending.promise)
    vm.composer = '给甲的消息'
    const send = vm.sendMessage()
    vm.selectContact(contacts[1])
    vm.composer = '给乙的新草稿'
    pending.resolve({ id: 1 })
    await send
    expect(mocks.sendInternalChatMessage).toHaveBeenCalledWith('test-token', { receiverId: 1, content: '给甲的消息' })
    expect(vm.composer).toBe('给乙的新草稿')
  })

  it('does not start a polling interval after being unmounted during initialization', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    const pending = deferred<any[]>()
    mocks.fetchInternalChatContacts.mockReturnValueOnce(pending.promise)
    create()
    wrapper!.unmount()
    wrapper = undefined
    pending.resolve(contacts)
    await flushPromises()
    expect(vi.getTimerCount()).toBe(0)
  })
})
