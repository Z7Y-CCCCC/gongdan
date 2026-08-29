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
      <button class="internal-chat-rail-btn active" type="button" title="消息"><UiIcon name="message" :size="19" /></button>
      <button class="internal-chat-rail-btn" type="button" title="联系人"><UiIcon name="user" :size="19" /></button>
      <span class="internal-chat-rail-spacer"></span>
      <button class="internal-chat-rail-btn" type="button" title="设置"><UiIcon name="settings" :size="19" /></button>
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
  color: #25332f;
  background: #f7f9f8;
}

.internal-chat-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 18px 0;
  color: #98a6a1;
  background: #18221f;
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
  color: #98a6a1;
  background: transparent;
  cursor: pointer;
}

.internal-chat-rail-btn:hover,
.internal-chat-rail-btn.active { color: #fff; background: #2a3934; }
.internal-chat-rail-btn.active { box-shadow: inset 2px 0 #1fd29d; }
.internal-chat-rail-spacer { flex: 1; }

.internal-chat-sidebar {
  min-width: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #dfe7e4;
  background: #fff;
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
  color: #19a77f;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.5px;
}

.internal-chat-sidebar-head h1,
.internal-chat-main-head h2 { margin: 0; font-weight: 700; }
.internal-chat-sidebar-head h1 { font-size: 22px; }
.internal-chat-online-dot { width: 8px; height: 8px; border-radius: 50%; background: #19b886; box-shadow: 0 0 0 4px #e1f7ef; }

.internal-chat-search {
  height: 36px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 16px 20px;
  padding: 0 11px;
  border: 1px solid #e0e7e4;
  border-radius: 5px;
  color: #8a9893;
  background: #f7f9f8;
}

.internal-chat-search:focus-within { border-color: #5bc9a7; box-shadow: 0 0 0 2px rgba(25, 167, 127, .1); }
.internal-chat-search input { width: 100%; min-width: 0; border: 0; outline: 0; color: #25332f; background: transparent; font-size: 12px; }
.internal-chat-list-heading { display: flex; justify-content: space-between; padding: 0 20px 8px; color: #8b9893; font-size: 11px; }
.internal-chat-contact-list { min-height: 0; flex: 1; overflow: auto; padding: 0 8px 12px; }

.internal-chat-contact {
  width: 100%; min-height: 76px; display: flex; align-items: center; gap: 10px;
  padding: 10px 11px; border: 0; border-radius: 6px; color: inherit; background: transparent; text-align: left; cursor: pointer;
}
.internal-chat-contact:hover { background: #f4faf7; }
.internal-chat-contact.active { background: #eaf8f3; box-shadow: inset 3px 0 #19aa83; }

.internal-chat-avatar {
  width: 36px; height: 36px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 50%;
  color: #147b60; background: #d8f2e8; font-size: 14px; font-weight: 700;
}
.internal-chat-avatar.large { width: 42px; height: 42px; font-size: 16px; }
.internal-chat-avatar.small { width: 30px; height: 30px; align-self: flex-end; font-size: 12px; }
.internal-chat-contact-body { min-width: 0; flex: 1; display: grid; gap: 3px; }
.internal-chat-contact-title { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.internal-chat-contact-title strong { overflow: hidden; color: #293934; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.internal-chat-contact-title time { color: #9aa6a2; font-size: 10px; white-space: nowrap; }
.internal-chat-contact-sub, .internal-chat-contact-preview { overflow: hidden; color: #87948f; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.internal-chat-contact-preview { color: #65736e; }
.internal-chat-unread { min-width: 17px; height: 17px; padding: 0 4px; border-radius: 9px; color: #fff; background: #e45145; font-size: 10px; line-height: 17px; text-align: center; }

.internal-chat-main { min-width: 0; display: flex; flex-direction: column; background: #f7f9f8; }
.internal-chat-main-head { min-height: 82px; border-bottom: 1px solid #dfe7e4; background: #fff; }
.internal-chat-peer-title { display: flex; align-items: center; gap: 12px; }
.internal-chat-main-head h2 { font-size: 16px; }
.internal-chat-main-head p { margin: 4px 0 0; color: #8a9893; font-size: 11px; }
.internal-chat-peer-status { color: #8a9893; font-size: 11px; }
.internal-chat-peer-status span { display: inline-block; width: 6px; height: 6px; margin-right: 5px; border-radius: 50%; background: #1ab686; }

.internal-chat-messages { min-height: 0; flex: 1; overflow: auto; padding: 28px clamp(20px, 5vw, 76px); }
.internal-chat-message-row { display: flex; align-items: flex-end; gap: 9px; margin: 0 0 18px; }
.internal-chat-message-row.mine { justify-content: flex-end; }
.internal-chat-message-stack { max-width: min(680px, 78%); display: flex; flex-direction: column; gap: 5px; }
.internal-chat-message-row.mine .internal-chat-message-stack { align-items: flex-end; }
.internal-chat-message-time { color: #9aa6a2; font-size: 10px; }
.internal-chat-bubble { padding: 10px 13px; border: 1px solid #e0e8e4; border-radius: 4px 12px 12px 12px; color: #32413c; background: #fff; font-size: 13px; line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; box-shadow: 0 2px 8px rgba(32, 60, 48, .04); }
.mine .internal-chat-bubble { border-color: #1aab83; border-radius: 12px 4px 12px 12px; color: #fff; background: #1aab83; }
.internal-chat-message-read-state { color: #a0aaa6; font-size: 10px; line-height: 1; }
.internal-chat-message-read-state.read { color: #18a77d; }

.internal-chat-empty { min-height: 220px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #8d9a95; text-align: center; }
.internal-chat-empty.full { flex: 1; }
.internal-chat-empty-icon { width: 58px; height: 58px; display: grid; place-items: center; margin-bottom: 12px; border-radius: 50%; color: #19a77f; background: #e3f6ef; }
.internal-chat-empty strong { color: #50615b; font-size: 14px; }
.internal-chat-empty p { margin: 6px 0 0; font-size: 12px; }
.internal-chat-state { padding: 28px 12px; color: #8d9a95; font-size: 12px; text-align: center; }
.internal-chat-state.error { color: #d6534d; }

.internal-chat-composer { padding: 14px 24px 18px; border-top: 1px solid #dfe7e4; background: #fff; }
.internal-chat-composer textarea { width: 100%; min-height: 62px; resize: vertical; border: 0; outline: 0; color: #32413c; background: transparent; font: inherit; font-size: 13px; line-height: 1.6; }
.internal-chat-composer textarea::placeholder { color: #a0aaa6; }
.internal-chat-composer-foot { display: flex; align-items: center; justify-content: space-between; color: #a0aaa6; font-size: 10px; }
.internal-chat-send { min-width: 78px; height: 32px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; border: 0; border-radius: 4px; color: #fff; background: #1aab83; font-size: 12px; cursor: pointer; }
.internal-chat-send:hover { background: #138f6c; }
.internal-chat-send:disabled { opacity: .5; cursor: not-allowed; }

:global(.admin-shell.dark-theme) .internal-chat-page { color: #d9e2e7; background: #11171b; }
:global(.admin-shell.dark-theme) .internal-chat-sidebar,
:global(.admin-shell.dark-theme) .internal-chat-main-head,
:global(.admin-shell.dark-theme) .internal-chat-composer { border-color: #303a44; background: #171d23; }
:global(.admin-shell.dark-theme) .internal-chat-sidebar-head h1,
:global(.admin-shell.dark-theme) .internal-chat-contact-title strong,
:global(.admin-shell.dark-theme) .internal-chat-main-head h2,
:global(.admin-shell.dark-theme) .internal-chat-empty strong { color: #e0e8ec; }
:global(.admin-shell.dark-theme) .internal-chat-search { border-color: #35414b; background: #222a32; }
:global(.admin-shell.dark-theme) .internal-chat-search input,
:global(.admin-shell.dark-theme) .internal-chat-bubble,
:global(.admin-shell.dark-theme) .internal-chat-composer textarea { color: #dce5e9; }
:global(.admin-shell.dark-theme) .internal-chat-contact:hover { background: #202a32; }
:global(.admin-shell.dark-theme) .internal-chat-contact.active { background: #203a34; }
:global(.admin-shell.dark-theme) .internal-chat-bubble { border-color: #35414b; background: #20272e; }

@media (max-width: 820px) {
  .internal-chat-page { grid-template-columns: 50px 250px minmax(0, 1fr); }
  .internal-chat-main-head { padding-left: 16px; padding-right: 16px; }
  .internal-chat-peer-status { display: none; }
  .internal-chat-messages { padding-left: 18px; padding-right: 18px; }
}
</style>
