<template>
  <div class="main-container">
    <div class="sidebar">
      <div class="sidebar-header">
        <div class="user-info">
          <el-avatar :size="40" :src="userStore.userInfo?.avatar">
            {{ userStore.userInfo?.username?.charAt(0)?.toUpperCase() }}
          </el-avatar>
          <span class="username">{{ userStore.userInfo?.username }}</span>
        </div>
      </div>

      <div class="sidebar-nav">
        <el-menu
          :default-active="activeMenu"
          class="nav-menu"
          @select="handleMenuSelect"
        >
          <el-menu-item index="/chat">
            <el-icon><ChatDotRound /></el-icon>
            <span>消息</span>
          </el-menu-item>
          <el-menu-item index="/contacts">
            <el-icon><UserFilled /></el-icon>
            <span>联系人</span>
          </el-menu-item>
        </el-menu>
      </div>

      <div class="sidebar-footer">
        <el-button text @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          <span>退出登录</span>
        </el-button>
      </div>
    </div>

    <div class="main-content">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useContactStore } from '@/stores/contact'
import { useChatStore } from '@/stores/chat'
import { wsService } from '@/utils/websocket'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const contactStore = useContactStore()
const chatStore = useChatStore()

const activeMenu = computed(() => route.path)
let wsMessageHandler = null

const handleMenuSelect = (index) => {
  router.push(index)
}

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}

const initWebSocket = () => {
  wsMessageHandler = (data) => {
    if (data.type === 'message') {
      chatStore.addMessage(data)
      
      if (chatStore.currentConversation) {
        const isCurrentPrivate = chatStore.currentConversation.type === 'private' && 
          ((data.fromUserId === chatStore.currentConversation.userId) || 
           (data.toUserId === chatStore.currentConversation.userId))
        const isCurrentGroup = chatStore.currentConversation.type === 'group' && 
          data.groupId === chatStore.currentConversation.groupId
        
        if (isCurrentPrivate || isCurrentGroup) {
          scrollToBottom()
        }
      }
    } else if (data.type === 'system') {
      if (data.action === 'online') {
        contactStore.updateFriendOnlineStatus(data.userId, true)
        chatStore.updateConversationOnlineStatus(data.userId, true)
      } else if (data.action === 'offline') {
        contactStore.updateFriendOnlineStatus(data.userId, false)
        chatStore.updateConversationOnlineStatus(data.userId, false)
      }
    }
  }
  wsService.onMessage(wsMessageHandler)
}

const scrollToBottom = () => {
  const messageList = document.querySelector('.message-list')
  if (messageList) {
    messageList.scrollTop = messageList.scrollHeight
  }
}

onMounted(async () => {
  if (userStore.token) {
    wsService.ensureConnected(userStore.token)
  }
  initWebSocket()
  if (userStore.token) {
    const [friends, groups] = await Promise.all([
      contactStore.fetchFriends(),
      contactStore.fetchGroups()
    ])
    await chatStore.initConversations(friends, groups)
    await chatStore.applyConversationSummaries()
  }
})

onBeforeUnmount(() => {
  if (wsMessageHandler) {
    wsService.offMessage(wsMessageHandler)
  }
})
</script>

<style scoped>
.main-container {
  display: flex;
  width: 100vw;
  height: 100vh;
  background-color: #f5f5f5;
}

.sidebar {
  width: 240px;
  background-color: #2c3e50;
  color: white;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.username {
  font-size: 16px;
  font-weight: 500;
}

.sidebar-nav {
  flex: 1;
  padding: 20px 0;
}

.nav-menu {
  background-color: transparent;
  border: none;
}

.nav-menu .el-menu-item {
  color: rgba(255, 255, 255, 0.8);
}

.nav-menu .el-menu-item:hover,
.nav-menu .el-menu-item.is-active {
  background-color: rgba(255, 255, 255, 0.1);
  color: white;
}

.sidebar-footer {
  padding: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.sidebar-footer .el-button {
  color: rgba(255, 255, 255, 0.8);
  width: 100%;
}

.sidebar-footer .el-button:hover {
  color: white;
}

.main-content {
  flex: 1;
  overflow: hidden;
}
</style>
