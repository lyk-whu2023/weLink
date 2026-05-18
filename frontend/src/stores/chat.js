import { defineStore } from 'pinia'
import { ref } from 'vue'
import { messageApi } from '@/api'
import { useUserStore } from '@/stores/user'
import dayjs from 'dayjs'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref([])
  const currentConversation = ref(null)
  const messages = ref([])
  const unreadCount = ref(0)
  const userStore = useUserStore()

  function addMessage(message) {
    const msg = {
      ...message,
      timestamp: message.createdAt || message.timestamp || dayjs().format('YYYY-MM-DD HH:mm:ss')
    }
    
    // 检查消息是否已存在，避免重复
    const exists = messages.value.some(m => 
      (m.msgId && msg.msgId && m.msgId === msg.msgId) ||
      (!m.msgId && !msg.msgId && m.content === msg.content && m.timestamp === msg.timestamp)
    )
    if (exists) return
    
    // 更新会话列表
    const conv = conversations.value.find(c => 
      (c.type === 'private' && (c.userId === msg.fromUserId || c.userId === msg.toUserId)) ||
      (c.type === 'group' && c.groupId === msg.groupId)
    )
    
    if (conv) {
      conv.lastMessage = msg.content
      conv.lastTime = msg.timestamp
      
      // 如果不是当前活跃会话，增加未读数
      if (currentConversation.value) {
        const isCurrentPrivate = currentConversation.value.type === 'private' && 
          ((msg.fromUserId === currentConversation.value.userId) || 
           (msg.toUserId === currentConversation.value.userId))
        const isCurrentGroup = currentConversation.value.type === 'group' && 
          msg.groupId === currentConversation.value.groupId
        
        if (!isCurrentPrivate && !isCurrentGroup) {
          conv.unread = (conv.unread || 0) + 1
        }
      } else {
        conv.unread = (conv.unread || 0) + 1
      }
    }
    
    // 如果是当前会话的消息，添加到消息列表
    if (currentConversation.value) {
      const isCurrentPrivate = currentConversation.value.type === 'private' && 
        ((msg.fromUserId === currentConversation.value.userId) || 
         (msg.toUserId === currentConversation.value.userId))
      const isCurrentGroup = currentConversation.value.type === 'group' && 
        msg.groupId === currentConversation.value.groupId
      
      if (isCurrentPrivate || isCurrentGroup) {
        messages.value.push(msg)
      }
    }
  }

  function setCurrentConversation(conversation) {
    currentConversation.value = conversation
    
    const conv = conversations.value.find(c => 
      (c.type === 'private' && c.userId === conversation.userId) ||
      (c.type === 'group' && c.groupId === conversation.groupId)
    )
    
    if (conv) {
      conv.unread = 0
    }
  }

  async function initConversations(friends, groups) {
    conversations.value = []
    
    if (friends) {
      friends.forEach(friend => {
        conversations.value.push({
          id: `private_${friend.id}`,
          type: 'private',
          userId: friend.id,
          name: friend.nickname || friend.username,
          avatar: friend.avatar,
          lastMessage: '',
          lastTime: '',
          unread: 0,
          online: friend.online
        })
      })
    }
    
    if (groups) {
      groups.forEach(group => {
        conversations.value.push({
          id: `group_${group.id}`,
          type: 'group',
          groupId: group.id,
          name: group.groupName,
          avatar: group.avatar,
          lastMessage: '',
          lastTime: '',
          unread: 0
        })
      })
    }
  }

  async function loadPrivateHistory(userId, page = 1, pageSize = 50) {
    const res = await messageApi.getPrivateHistory({
      userId: userStore.userInfo.id,
      targetId: userId,
      pageNum: page,
      pageSize
    })
    messages.value = res.data.records || []
    return res.data
  }

  async function loadGroupHistory(groupId, page = 1, pageSize = 50) {
    const res = await messageApi.getGroupHistory({
      groupId,
      pageNum: page,
      pageSize
    })
    messages.value = res.data.records || []
    return res.data
  }

  async function loadOfflineMessages() {
    const res = await messageApi.getOfflineMessages()
    const offlineMessages = res.data || []
    offlineMessages.forEach(msg => {
      const conv = conversations.value.find(c => 
        (c.type === 'private' && c.userId === msg.fromUserId) ||
        (c.type === 'group' && c.groupId === msg.groupId)
      )
      
      if (conv) {
        conv.lastMessage = msg.content
        conv.lastTime = msg.createdAt || msg.timestamp
        conv.unread = (conv.unread || 0) + 1
      }
    })
    return offlineMessages
  }

  async function markAsRead(msgId) {
    return await messageApi.markAsRead(msgId)
  }

  function clearMessages() {
    messages.value = []
  }

  return {
    conversations,
    currentConversation,
    messages,
    unreadCount,
    addMessage,
    setCurrentConversation,
    initConversations,
    loadPrivateHistory,
    loadGroupHistory,
    loadOfflineMessages,
    markAsRead,
    clearMessages
  }
})
