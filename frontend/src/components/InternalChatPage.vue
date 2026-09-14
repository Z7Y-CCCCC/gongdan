<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  fetchInternalChatContacts,
  fetchInternalChatMessages,
  markInternalChatRead,
  sendInternalChatMessage,
  type InternalChatContactInfo,
  type InternalChatMessageInfo,
  type UserInfo
} from '../api'
import UiIcon from './UiIcon.vue'
import { useLatestRequest } from '../composables/useLatestRequest'

const props = defineProps<{
  token: string
  currentUser: UserInfo | null
}>()

const contacts = ref<InternalChatContactInfo[]>([])
const messages = ref<InternalChatMessageInfo[]>([])
const activePeerId = ref<number | null>(null)
const searchQuery = ref('')
const composer = ref('')
const loadingContacts = ref(false)
const loadingMessages = ref(false)
const sending = ref(false)
const errorMessage = ref('')
const railView = ref<'messages' | 'contacts'>('messages')
const showPresence = ref(false)
const contactsSearch = ref<HTMLInputElement | null>(null)
const messageList = ref<HTMLElement | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null
let contactsRequest: Promise<void> | null = null
let messagesRequest: { peerId: number; promise: Promise<void> } | null = null
const beginMessagesLoad = useLatestRequest()
let disposed = false

const activePeer = computed(() => contacts.value.find(contact => contact.id === activePeerId.value) || null)
const filteredContacts = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  if (!keyword) return contacts.value
  return contacts.value.filter(contact => [contact.name, contact.username, contact.roleLabel, contact.departmentName]
    .filter(Boolean)
    .some(value => String(value).toLowerCase().includes(keyword)))
})
const displayContacts = computed(() => {
  if (railView.value === 'messages') return filteredContacts.value
  return [...filteredContacts.value].sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
})

function initials(name: string) {
  return name.trim().slice(0, 1) || '?'
}

function formatTime(value?: string, withDate = false) {
  if (!value) return ''
  const date = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return value
  if (withDate) {
    return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function parseDate(value?: string) {
  if (!value) return null
  const date = new Date(value.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

function formatElapsed(value?: string) {
  const date = parseDate(value)
  if (!date) return '暂无活动记录'
  const seconds = Math.max(0, Math.floor((Date.now() - date.getTime()) / 1000))
  if (seconds < 10) return '刚刚活跃'
  if (seconds < 60) return `${seconds}秒前活跃`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}分钟前活跃`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前活跃`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前活跃`
  return formatTime(value, true)
}

function presenceLabel(contact: InternalChatContactInfo) {
  if (contact.online) {
    const deviceCount = contact.onlineDeviceCount || contact.onlineDevices?.length || 0
    return deviceCount > 1 ? `在线 · ${deviceCount} 台设备` : '在线'
  }
  return contact.lastSeenAt ? `离线 · ${formatElapsed(contact.lastSeenAt)}` : '暂无登录记录'
}

function presenceTitle(contact: InternalChatContactInfo) {
  if (contact.online) return `${presenceLabel(contact)}，点击查看设备详情`
  if (contact.lastSeenAt) return `${presenceLabel(contact)}，最近登录 ${formatTime(contact.lastLoginAt, true) || '暂无记录'}`
  return '暂无登录记录'
}

function isMine(message: InternalChatMessageInfo) {
  return message.senderId === props.currentUser?.id
}

async function scrollToLatest() {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}

async function loadContacts() {
  if (!props.token || contactsRequest) return
  contactsRequest = (async () => {
    loadingContacts.value = contacts.value.length === 0
    try {
      const result = await fetchInternalChatContacts(props.token)
      if (disposed) return
      contacts.value = result
      // Keep the user's current selection, not the selection captured before this request.
      if (!contacts.value.some(contact => contact.id === activePeerId.value)) {
        activePeerId.value = contacts.value[0]?.id ?? null
        messages.value = []
      }
      errorMessage.value = ''
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '联系人加载失败'
    } finally {
      loadingContacts.value = false
      contactsRequest = null
    }
  })()
  await contactsRequest
}

async function loadMessages(peerId = activePeerId.value, quiet = false) {
  if (!props.token || !peerId || disposed || peerId !== activePeerId.value) return
  if (messagesRequest?.peerId === peerId) return messagesRequest.promise
  const isLatest = beginMessagesLoad()
  const isCurrent = () => isLatest() && peerId === activePeerId.value && !disposed
  const promise = (async () => {
    if (!quiet) loadingMessages.value = true
    try {
      const result = await fetchInternalChatMessages(props.token, peerId)
      if (!isCurrent()) return
      messages.value = result
      await markInternalChatRead(props.token, peerId)
      if (!isCurrent()) return
      const contact = contacts.value.find(item => item.id === peerId)
      if (contact) contact.unreadCount = 0
      await scrollToLatest()
      errorMessage.value = ''
    } catch (error) {
      if (isCurrent()) errorMessage.value = error instanceof Error ? error.message : '消息加载失败'
    } finally {
      if (isLatest()) {
        loadingMessages.value = false
        messagesRequest = null
      }
    }
  })()
  messagesRequest = { peerId, promise }
  await promise
}

function selectContact(contact: InternalChatContactInfo) {
  if (activePeerId.value === contact.id) return
  activePeerId.value = contact.id
  messages.value = []
  showPresence.value = false
}

function openMessages() {
  railView.value = 'messages'
}

async function openContacts() {
  railView.value = 'contacts'
  await nextTick()
  contactsSearch.value?.focus()
}

function togglePresence() {
  showPresence.value = !showPresence.value
}

async function sendMessage() {
  const draft = composer.value
  const content = draft.trim()
  const peerId = activePeerId.value
  if (!content || !peerId || sending.value) return
  sending.value = true
  try {
    await sendInternalChatMessage(props.token, { receiverId: peerId, content })
    if (disposed) return
    if (activePeerId.value === peerId) {
      if (composer.value === draft) composer.value = ''
      await loadMessages(peerId)
    }
    await loadContacts()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '消息发送失败'
  } finally {
    sending.value = false
  }
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (!event.shiftKey) {
    event.preventDefault()
    void sendMessage()
  }
}

function startPolling() {
  if (disposed) return
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(() => {
    void loadContacts()
    if (activePeerId.value) void loadMessages(activePeerId.value, true)
  }, 8000)
}

watch(activePeerId, peerId => {
  if (peerId) void loadMessages(peerId)
})

onMounted(async () => {
  await loadContacts()
  startPolling()
})

onBeforeUnmount(() => {
  disposed = true
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <section class="internal-chat-page">
    <aside class="internal-chat-rail" aria-label="沟通导航">
      <button class="internal-chat-rail-btn" :class="{ active: railView === 'messages' }" type="button" title="消息" :aria-current="railView === 'messages' ? 'page' : undefined" @click="openMessages">
        <UiIcon name="message" :size="19" />
      </button>
      <button class="internal-chat-rail-btn" :class="{ active: railView === 'contacts' }" type="button" title="联系人" :aria-current="railView === 'contacts' ? 'page' : undefined" @click="openContacts">
        <UiIcon name="user" :size="19" />
      </button>
    </aside>

    <aside class="internal-chat-sidebar">
      <header class="internal-chat-sidebar-head">
        <div>
          <p class="internal-chat-kicker">WORKSPACE</p>
          <h1>消息</h1>
        </div>
        <span class="internal-chat-online-dot" title="内部沟通在线"></span>
      </header>

      <label class="internal-chat-search">
        <UiIcon name="search" :size="15" />
        <input ref="contactsSearch" v-model="searchQuery" type="search" placeholder="搜索联系人" aria-label="搜索联系人" />
      </label>

      <div class="internal-chat-list-heading">
        <span>{{ railView === 'contacts' ? '联系人' : '最近会话' }}</span>
        <span v-if="contacts.length">{{ contacts.length }}</span>
      </div>

      <div class="internal-chat-contact-list">
        <div v-if="loadingContacts" class="internal-chat-state">联系人加载中...</div>
        <div v-else-if="errorMessage && contacts.length === 0" class="internal-chat-state error">{{ errorMessage }}</div>
        <div v-else-if="filteredContacts.length === 0" class="internal-chat-state">暂无可沟通的联系人</div>
        <button
          v-for="contact in displayContacts"
          :key="contact.id"
          class="internal-chat-contact"
          :class="{ active: activePeerId === contact.id }"
          type="button"
          @click="selectContact(contact)"
        >
          <span class="internal-chat-avatar">{{ initials(contact.name) }}</span>
          <span class="internal-chat-contact-body">
            <span class="internal-chat-contact-title">
              <strong>{{ contact.name }}</strong>
              <time v-if="contact.lastMessageAt">{{ formatTime(contact.lastMessageAt) }}</time>
            </span>
            <span class="internal-chat-contact-sub">
              <span class="internal-chat-presence-dot" :class="{ online: contact.online }"></span>
              {{ contact.departmentName || contact.roleLabel }} · {{ contact.online ? '在线' : contact.lastSeenAt ? `离线 ${formatElapsed(contact.lastSeenAt)}` : '离线' }}
            </span>
            <span class="internal-chat-contact-preview">{{ contact.lastMessage || '开始一段新的沟通' }}</span>
          </span>
          <span v-if="contact.unreadCount" class="internal-chat-unread">{{ contact.unreadCount > 99 ? '99+' : contact.unreadCount }}</span>
        </button>
      </div>
    </aside>

    <main class="internal-chat-main">
      <header v-if="activePeer" class="internal-chat-main-head">
        <div class="internal-chat-peer-title">
          <span class="internal-chat-avatar large">{{ initials(activePeer.name) }}</span>
          <div>
            <h2>{{ activePeer.name }}</h2>
            <p>{{ activePeer.departmentName || activePeer.roleLabel }} · {{ activePeer.username }}</p>
          </div>
        </div>
        <div class="internal-chat-peer-status-wrap">
          <button class="internal-chat-peer-status" :class="{ online: activePeer.online }" type="button" :title="presenceTitle(activePeer)" @click="togglePresence">
            <span class="internal-chat-presence-dot" :class="{ online: activePeer.online }"></span>
            {{ presenceLabel(activePeer) }}
            <UiIcon name="chevronUp" :size="13" :class="{ rotated: !showPresence }" />
          </button>
          <div v-if="showPresence" class="internal-chat-presence-popover" @click.stop>
            <div class="internal-chat-presence-popover-head">
              <strong>{{ activePeer.online ? '在线设备' : '最近活动' }}</strong>
              <span>{{ presenceLabel(activePeer) }}</span>
            </div>
            <div v-if="activePeer.onlineDevices?.length" class="internal-chat-presence-list">
              <div v-for="device in activePeer.onlineDevices" :key="device.sessionId" class="internal-chat-presence-device">
                <span class="internal-chat-presence-device-icon"><UiIcon name="user" :size="15" /></span>
                <div class="internal-chat-presence-device-main">
                  <strong>{{ device.deviceLabel }}</strong>
                  <span>{{ device.operatingSystem }} · {{ device.browser }}</span>
                  <span>{{ device.loginLocation }} · {{ device.ipAddress }}</span>
                </div>
                <time>{{ formatElapsed(device.lastActiveAt) }}</time>
              </div>
            </div>
            <div v-else class="internal-chat-presence-offline">
              <p>最近活动：{{ formatTime(activePeer.lastSeenAt, true) || '暂无记录' }}</p>
              <p>最近登录：{{ formatTime(activePeer.lastLoginAt, true) || '暂无记录' }}</p>
              <span>{{ activePeer.lastSeenAt ? formatElapsed(activePeer.lastSeenAt) : '该联系人还没有登录记录' }}</span>
            </div>
          </div>
        </div>
      </header>

      <div v-if="activePeer" ref="messageList" class="internal-chat-messages">
        <div v-if="loadingMessages" class="internal-chat-state">消息加载中...</div>
        <div v-else-if="messages.length === 0" class="internal-chat-empty">
          <span class="internal-chat-empty-icon"><UiIcon name="message" :size="26" /></span>
          <strong>开始沟通</strong>
          <p>向 {{ activePeer.name }} 发送第一条消息</p>
        </div>
        <template v-else>
          <div v-for="message in messages" :key="message.id" class="internal-chat-message-row" :class="{ mine: isMine(message) }">
            <span v-if="!isMine(message)" class="internal-chat-avatar small">{{ initials(message.senderName) }}</span>
            <div class="internal-chat-message-stack">
              <span class="internal-chat-message-time">{{ formatTime(message.createdAt, true) }}</span>
              <div class="internal-chat-bubble">{{ message.content }}</div>
              <span v-if="isMine(message)" class="internal-chat-message-read-state" :class="{ read: message.read }">
                {{ message.read ? '已读' : '未读' }}
              </span>
            </div>
          </div>
        </template>
      </div>
      <div v-else class="internal-chat-empty full">
        <span class="internal-chat-empty-icon"><UiIcon name="message" :size="26" /></span>
        <strong>选择联系人开始沟通</strong>
        <p>内部消息会按工厂隔离保存，并在这里持续同步</p>
      </div>

      <footer v-if="activePeer" class="internal-chat-composer">
        <textarea
          v-model="composer"
          rows="3"
          maxlength="2000"
          placeholder="输入消息，按 Enter 发送，Shift + Enter 换行"
          @keydown.enter="handleComposerKeydown"
        ></textarea>
        <div class="internal-chat-composer-foot">
          <span>{{ composer.length }}/2000</span>
          <button class="internal-chat-send" type="button" :disabled="sending || !composer.trim()" @click="sendMessage">
            <UiIcon name="send" :size="15" />
            {{ sending ? '发送中...' : '发送' }}
          </button>
        </div>
      </footer>
    </main>
  </section>
</template>

<style scoped>
.internal-chat-page {
  min-height: calc(100vh - 94px);
  height: calc(100vh - 94px);
  display: grid;
  grid-template-columns: 58px 312px minmax(0, 1fr);
  overflow: hidden;
  color: var(--text-primary);
  background: var(--page-bg);
}

.internal-chat-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 18px 0;
  color: var(--sidebar-muted);
  background: var(--sidebar);
}

.internal-chat-rail-btn {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 8px;
  color: var(--sidebar-muted);
  background: transparent;
  cursor: pointer;
  transition: color .16s ease, background .16s ease, transform .16s ease;
}

.internal-chat-rail-btn:hover { color: var(--text-primary); background: var(--surface-hover); transform: translateY(-1px); }
.internal-chat-rail-btn.active { color: var(--text-primary); background: var(--sidebar-active); box-shadow: inset 2px 0 var(--accent); }

.internal-chat-sidebar {
  min-width: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-default);
  background: var(--surface);
}

.internal-chat-sidebar-head,
.internal-chat-main-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 22px 18px;
}

.internal-chat-kicker {
  margin: 0 0 5px;
  color: var(--accent-dark);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.5px;
}

.internal-chat-sidebar-head h1,
.internal-chat-main-head h2 { margin: 0; font-weight: 700; color: var(--text-primary); }
.internal-chat-sidebar-head h1 { font-size: 22px; }
.internal-chat-online-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 0 4px var(--status-success-bg); }

.internal-chat-search {
  height: 36px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 16px 20px;
  padding: 0 11px;
  border: 1px solid var(--border-default);
  border-radius: 5px;
  color: var(--text-muted);
  background: var(--surface-subtle);
}

.internal-chat-search:focus-within { border-color: #5bc9a7; box-shadow: 0 0 0 2px rgba(25, 167, 127, .1); }
.internal-chat-search input { width: 100%; min-width: 0; border: 0; outline: 0; color: var(--text-primary); background: transparent; font-size: 12px; }
.internal-chat-list-heading { display: flex; justify-content: space-between; padding: 0 20px 8px; color: var(--text-muted); font-size: 11px; }
.internal-chat-contact-list { min-height: 0; flex: 1; overflow: auto; padding: 0 8px 12px; }

.internal-chat-contact {
  width: 100%; min-height: 76px; display: flex; align-items: center; gap: 10px;
  padding: 10px 11px; border: 0; border-radius: 6px; color: inherit; background: transparent; text-align: left; cursor: pointer;
}
.internal-chat-contact:hover { background: var(--surface-hover); }
.internal-chat-contact.active { background: var(--sidebar-active); box-shadow: inset 3px 0 var(--accent); }

.internal-chat-avatar {
  width: 36px; height: 36px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 50%;
  color: var(--accent-dark); background: var(--status-success-bg); font-size: 14px; font-weight: 700;
}
.internal-chat-avatar.large { width: 42px; height: 42px; font-size: 16px; }
.internal-chat-avatar.small { width: 30px; height: 30px; align-self: flex-end; font-size: 12px; }
.internal-chat-contact-body { min-width: 0; flex: 1; display: grid; gap: 3px; }
.internal-chat-contact-title { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.internal-chat-contact-title strong { overflow: hidden; color: var(--text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.internal-chat-contact-title time { color: var(--text-muted); font-size: 10px; white-space: nowrap; }
.internal-chat-contact-sub, .internal-chat-contact-preview { overflow: hidden; color: var(--text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.internal-chat-contact-sub { display: flex; align-items: center; gap: 4px; }
.internal-chat-contact-preview { color: var(--text-secondary); }
.internal-chat-unread { min-width: 17px; height: 17px; padding: 0 4px; border-radius: 9px; color: #fff; background: #e45145; font-size: 10px; line-height: 17px; text-align: center; }

.internal-chat-main { min-width: 0; display: flex; flex-direction: column; background: var(--page-bg); }
.internal-chat-main-head { min-height: 82px; border-bottom: 1px solid var(--border-default); background: var(--surface); }
.internal-chat-peer-title { display: flex; align-items: center; gap: 12px; }
.internal-chat-main-head h2 { font-size: 16px; }
.internal-chat-main-head p { margin: 4px 0 0; color: var(--text-muted); font-size: 11px; }
.internal-chat-peer-status-wrap { position: relative; display: flex; align-items: center; }
.internal-chat-peer-status { display: inline-flex; align-items: center; gap: 6px; padding: 7px 8px; border: 0; border-radius: 5px; color: var(--text-muted); background: transparent; font: inherit; font-size: 11px; cursor: pointer; }
.internal-chat-peer-status:hover { color: var(--text-primary); background: var(--surface-hover); }
.internal-chat-peer-status .ui-icon { transition: transform .16s ease; }
.internal-chat-peer-status .ui-icon.rotated { transform: rotate(180deg); }
.internal-chat-presence-dot { width: 6px; height: 6px; flex: 0 0 auto; border-radius: 50%; background: var(--text-muted); }
.internal-chat-presence-dot.online { background: var(--accent); box-shadow: 0 0 0 3px var(--status-success-bg); }

.internal-chat-presence-popover { position: absolute; top: calc(100% + 8px); right: 0; z-index: 5; width: min(360px, calc(100vw - 32px)); overflow: hidden; border: 1px solid var(--border-default); border-radius: 6px; color: var(--text-primary); background: var(--surface); box-shadow: 0 16px 36px rgba(20, 40, 32, .16); }
.internal-chat-presence-popover-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 16px; border-bottom: 1px solid var(--border-default); }
.internal-chat-presence-popover-head strong { font-size: 13px; }
.internal-chat-presence-popover-head span { color: var(--text-muted); font-size: 11px; }
.internal-chat-presence-list { padding: 4px 0; }
.internal-chat-presence-device { display: flex; align-items: flex-start; gap: 9px; padding: 11px 16px; border-bottom: 1px solid var(--border-default); }
.internal-chat-presence-device:last-child { border-bottom: 0; }
.internal-chat-presence-device-icon { width: 28px; height: 28px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 50%; color: var(--accent-dark); background: var(--status-success-bg); }
.internal-chat-presence-device-main { min-width: 0; display: grid; flex: 1; gap: 3px; }
.internal-chat-presence-device-main strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.internal-chat-presence-device-main span, .internal-chat-presence-device time { color: var(--text-muted); font-size: 10px; line-height: 1.4; }
.internal-chat-presence-device time { flex: 0 0 auto; white-space: nowrap; }
.internal-chat-presence-offline { display: grid; gap: 6px; padding: 14px 16px 16px; color: var(--text-muted); font-size: 11px; }
.internal-chat-presence-offline p { margin: 0; }
.internal-chat-presence-offline span { color: var(--text-secondary); }

.internal-chat-messages { min-height: 0; flex: 1; overflow: auto; padding: 28px clamp(20px, 5vw, 76px); }
.internal-chat-message-row { display: flex; align-items: flex-end; gap: 9px; margin: 0 0 18px; }
.internal-chat-message-row.mine { justify-content: flex-end; }
.internal-chat-message-stack { max-width: min(680px, 78%); display: flex; flex-direction: column; gap: 5px; }
.internal-chat-message-row.mine .internal-chat-message-stack { align-items: flex-end; }
.internal-chat-message-time { color: var(--text-muted); font-size: 10px; }
.internal-chat-bubble { padding: 10px 13px; border: 1px solid var(--border-default); border-radius: 4px 12px 12px 12px; color: var(--text-primary); background: var(--surface-raised); font-size: 13px; line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; box-shadow: 0 2px 8px rgba(32, 60, 48, .04); }
.mine .internal-chat-bubble { border-color: #1aab83; border-radius: 12px 4px 12px 12px; color: #fff; background: #1aab83; }
.internal-chat-message-read-state { color: var(--text-muted); font-size: 10px; line-height: 1; }
.internal-chat-message-read-state.read { color: var(--accent-dark); }

.internal-chat-empty { min-height: 220px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--text-muted); text-align: center; }
.internal-chat-empty.full { flex: 1; }
.internal-chat-empty-icon { width: 58px; height: 58px; display: grid; place-items: center; margin-bottom: 12px; border-radius: 50%; color: var(--accent-dark); background: var(--status-success-bg); }
.internal-chat-empty strong { color: var(--text-secondary); font-size: 14px; }
.internal-chat-empty p { margin: 6px 0 0; font-size: 12px; }
.internal-chat-state { padding: 28px 12px; color: var(--text-muted); font-size: 12px; text-align: center; }
.internal-chat-state.error { color: #d6534d; }

.internal-chat-composer { padding: 14px 24px 18px; border-top: 1px solid var(--border-default); background: var(--surface); }
.internal-chat-composer textarea { width: 100%; min-height: 62px; resize: vertical; border: 0; outline: 0; color: #32413c; background: transparent; font: inherit; font-size: 13px; line-height: 1.6; }
.internal-chat-composer textarea::placeholder { color: var(--text-muted); }
.internal-chat-composer-foot { display: flex; align-items: center; justify-content: space-between; color: var(--text-muted); font-size: 10px; }
.internal-chat-send { min-width: 78px; height: 32px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; border: 0; border-radius: 4px; color: #fff; background: #1aab83; font-size: 12px; cursor: pointer; }
.internal-chat-send:hover { background: #138f6c; }
.internal-chat-send:disabled { opacity: .5; cursor: not-allowed; }

@media (max-width: 820px) {
  .internal-chat-page { grid-template-columns: 50px 250px minmax(0, 1fr); }
  .internal-chat-main-head { padding-left: 16px; padding-right: 16px; }
  .internal-chat-peer-status { max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .internal-chat-messages { padding-left: 18px; padding-right: 18px; }
}
</style>
