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
const messageList = ref<HTMLElement | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null
let contactsRequest: Promise<void> | null = null
let messagesRequest: Promise<void> | null = null

const activePeer = computed(() => contacts.value.find(contact => contact.id === activePeerId.value) || null)
const filteredContacts = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  if (!keyword) return contacts.value
  return contacts.value.filter(contact => [contact.name, contact.username, contact.roleLabel, contact.departmentName]
    .filter(Boolean)
    .some(value => String(value).toLowerCase().includes(keyword)))
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
      const previousPeerId = activePeerId.value
      contacts.value = await fetchInternalChatContacts(props.token)
      if (previousPeerId && contacts.value.some(contact => contact.id === previousPeerId)) {
        activePeerId.value = previousPeerId
      } else if (!activePeerId.value && contacts.value.length > 0) {
        activePeerId.value = contacts.value[0].id
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
  if (!props.token || !peerId || messagesRequest) return
  messagesRequest = (async () => {
    if (!quiet) loadingMessages.value = true
    try {
      messages.value = await fetchInternalChatMessages(props.token, peerId)
      await markInternalChatRead(props.token, peerId)
      const contact = contacts.value.find(item => item.id === peerId)
      if (contact) contact.unreadCount = 0
      await scrollToLatest()
      errorMessage.value = ''
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '消息加载失败'
    } finally {
      loadingMessages.value = false
      messagesRequest = null
    }
  })()
  await messagesRequest
}

function selectContact(contact: InternalChatContactInfo) {
  if (activePeerId.value === contact.id) return
  activePeerId.value = contact.id
  messages.value = []
}

async function sendMessage() {
  const content = composer.value.trim()
  if (!content || !activePeerId.value || sending.value) return
  sending.value = true
  try {
    await sendInternalChatMessage(props.token, { receiverId: activePeerId.value, content })
    composer.value = ''
    await loadMessages(activePeerId.value)
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
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <section class="internal-chat-page">
    <aside class="internal-chat-rail" aria-label="沟通导航">
      <div class="internal-chat-brand">工</div>
      <div class="internal-chat-rail-btn active" title="消息" aria-current="page"><UiIcon name="message" :size="19" /></div>
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
        <input v-model="searchQuery" type="search" placeholder="搜索联系人" aria-label="搜索联系人" />
      </label>

      <div class="internal-chat-list-heading">
        <span>最近会话</span>
        <span v-if="contacts.length">{{ contacts.length }}</span>
      </div>

      <div class="internal-chat-contact-list">
        <div v-if="loadingContacts" class="internal-chat-state">联系人加载中...</div>
        <div v-else-if="errorMessage && contacts.length === 0" class="internal-chat-state error">{{ errorMessage }}</div>
        <div v-else-if="filteredContacts.length === 0" class="internal-chat-state">暂无可沟通的联系人</div>
        <button
          v-for="contact in filteredContacts"
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
            <span class="internal-chat-contact-sub">{{ contact.departmentName || contact.roleLabel }}</span>
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
        <div class="internal-chat-peer-status"><span></span> 内部成员</div>
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

.internal-chat-brand {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  margin-bottom: 16px;
  border-radius: 8px;
  color: #fff;
  background: #18a77d;
  font-size: 16px;
  font-weight: 700;
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
  cursor: default;
}

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
.internal-chat-contact-preview { color: var(--text-secondary); }
.internal-chat-unread { min-width: 17px; height: 17px; padding: 0 4px; border-radius: 9px; color: #fff; background: #e45145; font-size: 10px; line-height: 17px; text-align: center; }

.internal-chat-main { min-width: 0; display: flex; flex-direction: column; background: var(--page-bg); }
.internal-chat-main-head { min-height: 82px; border-bottom: 1px solid var(--border-default); background: var(--surface); }
.internal-chat-peer-title { display: flex; align-items: center; gap: 12px; }
.internal-chat-main-head h2 { font-size: 16px; }
.internal-chat-main-head p { margin: 4px 0 0; color: var(--text-muted); font-size: 11px; }
.internal-chat-peer-status { color: var(--text-muted); font-size: 11px; }
.internal-chat-peer-status span { display: inline-block; width: 6px; height: 6px; margin-right: 5px; border-radius: 50%; background: #1ab686; }

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
  .internal-chat-peer-status { display: none; }
  .internal-chat-messages { padding-left: 18px; padding-right: 18px; }
}
</style>
