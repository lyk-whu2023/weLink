<template>
  <div class="chat-container">
    <div class="conversation-list">
      <div class="conversation-header">
        <h3>消息</h3>
      </div>
      <div class="conversation-items">
        <div
          v-for="conv in chatStore.conversations"
          :key="conv.id"
          class="conversation-item"
          :class="{ active: isCurrentConversation(conv) }"
          @click="selectConversation(conv)"
        >
          <el-avatar :size="40" :src="conv.avatar">
            {{ conv.name?.charAt(0)?.toUpperCase() }}
          </el-avatar>
          <div class="conversation-info">
            <div class="conversation-top">
              <span class="conversation-name">{{ conv.name }}</span>
              <span class="conversation-time">{{ formatTime(conv.lastTime) }}</span>
            </div>
            <div class="conversation-bottom">
              <span class="last-message">{{ conv.lastMessage }}</span>
              <el-badge v-if="conv.unread" :value="conv.unread" :max="99" />
            </div>
          </div>
        </div>
        <el-empty v-if="chatStore.conversations.length === 0" description="暂无消息" />
      </div>
    </div>

    <div class="chat-window">
      <div v-if="chatStore.currentConversation" class="chat-content">
        <div class="chat-header">
          <h3>{{ chatStore.currentConversation.name }}</h3>
        </div>

        <div class="message-list" ref="messageListRef">
          <div
            v-for="msg in chatStore.messages"
            :key="msg.msgId || msg.id"
            class="message-item"
            :class="{ 'message-self': msg.fromUserId === userStore.userInfo?.id }"
          >
            <el-avatar :size="36" :src="msg.avatar">
              {{ msg.fromUsername?.charAt(0)?.toUpperCase() }}
            </el-avatar>
            <div class="message-content">
              <div class="message-info">
                <span class="message-sender">{{ msg.fromUsername }}</span>
                <span class="message-time">{{ msg.timestamp }}</span>
              </div>
              <div class="message-bubble">
                {{ msg.content }}
              </div>
            </div>
          </div>
        </div>

        <div class="message-input">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="3"
            placeholder="输入消息... (Ctrl+Enter 发送)"
            resize="none"
            @keydown.ctrl.enter="sendMessage"
          />
          <div class="input-actions">
            <el-button type="primary" @click="sendMessage">发送</el-button>
          </div>
        </div>
      </div>

      <el-empty v-else description="选择一个会话开始聊天" />
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { useContactStore } from '@/stores/contact'
import { wsService } from '@/utils/websocket'
import dayjs from 'dayjs'

const userStore = useUserStore()
const chatStore = useChatStore()
const contactStore = useContactStore()

const inputMessage = ref('')
const messageListRef = ref(null)

onMounted(async () => {
  await contactStore.fetchFriends()
  await contactStore.fetchGroups()
  
  chatStore.loadOfflineMessages()
})

const formatTime = (time) => {
  if (!time) return ''
  return dayjs(time).format('HH:mm')
}

const isCurrentConversation = (conv) => {
  if (!chatStore.currentConversation) return false
  return (
    (conv.type === 'private' && conv.userId === chatStore.currentConversation.userId) ||
    (conv.type === 'group' && conv.groupId === chatStore.currentConversation.groupId)
  )
}

const selectConversation = async (conv) => {
  chatStore.setCurrentConversation(conv)
  chatStore.clearMessages()
  
  if (conv.type === 'private') {
    await chatStore.loadPrivateHistory(conv.userId)
  } else if (conv.type === 'group') {
    await chatStore.loadGroupHistory(conv.groupId)
  }
  
  await scrollToBottom()
}

const sendMessage = () => {
  if (!inputMessage.value.trim() || !chatStore.currentConversation) return

  const conv = chatStore.currentConversation
  const message = {
    content: inputMessage.value.trim(),
    msgType: 1,
    fromUserId: userStore.userInfo.id,
    fromUsername: userStore.userInfo.username,
    timestamp: dayjs().format('YYYY-MM-DD HH:mm:ss')
  }

  if (conv.type === 'private') {
    message.toUserId = conv.userId
    wsService.sendMessage({
      toUserId: conv.userId,
      msgType: 1,
      content: message.content
    })
  } else if (conv.type === 'group') {
    message.groupId = conv.groupId
    wsService.sendMessage({
      groupId: conv.groupId,
      msgType: 1,
      content: message.content
    })
  }

  chatStore.addMessage(message)
  inputMessage.value = ''
  scrollToBottom()
}

const scrollToBottom = async () => {
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}
</script>

<style scoped>
.chat-container {
  display: flex;
  height: 100%;
  background-color: white;
}

.conversation-list {
  width: 300px;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
}

.conversation-header {
  padding: 20px;
  border-bottom: 1px solid #e8e8e8;
}

.conversation-header h3 {
  margin: 0;
  font-size: 18px;
  color: #333;
}

.conversation-items {
  flex: 1;
  overflow-y: auto;
}

.conversation-item {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.conversation-item:hover {
  background-color: #f5f5f5;
}

.conversation-item.active {
  background-color: #e6f7ff;
}

.conversation-info {
  flex: 1;
  margin-left: 12px;
  min-width: 0;
}

.conversation-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.conversation-name {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.conversation-time {
  font-size: 12px;
  color: #999;
}

.conversation-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.last-message {
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.chat-window {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-content {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-header {
  padding: 20px;
  border-bottom: 1px solid #e8e8e8;
}

.chat-header h3 {
  margin: 0;
  font-size: 18px;
  color: #333;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background-color: #f5f5f5;
}

.message-item {
  display: flex;
  margin-bottom: 16px;
  align-items: flex-start;
}

.message-self {
  flex-direction: row-reverse;
}

.message-content {
  margin: 0 12px;
  max-width: 60%;
}

.message-self .message-content {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.message-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.message-self .message-info {
  flex-direction: row-reverse;
}

.message-sender {
  font-size: 12px;
  color: #666;
}

.message-time {
  font-size: 11px;
  color: #999;
}

.message-bubble {
  padding: 10px 14px;
  background-color: white;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.5;
  word-wrap: break-word;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.message-self .message-bubble {
  background-color: #95ec69;
}

.message-input {
  padding: 20px;
  border-top: 1px solid #e8e8e8;
  background-color: white;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
