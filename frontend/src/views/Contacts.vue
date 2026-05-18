<template>
  <div class="contacts-container">
    <div class="contacts-sidebar">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="好友" name="friends" />
        <el-tab-pane label="群组" name="groups" />
        <el-tab-pane name="requests">
          <template #label>
            <span>
              好友申请
              <el-badge v-if="contactStore.friendRequests.length > 0" :value="contactStore.friendRequests.length" :max="99" />
            </span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <div class="contacts-actions">
        <el-button v-if="activeTab === 'friends'" type="primary" @click="showAddFriendDialog = true">
          <el-icon><Plus /></el-icon>
          添加好友
        </el-button>
        <template v-else-if="activeTab === 'groups'">
          <el-button type="primary" @click="showCreateGroupDialog = true">
            <el-icon><Plus /></el-icon>
            创建群组
          </el-button>
          <el-button @click="showJoinGroupDialog = true">
            <el-icon><UserFilled /></el-icon>
            加入群组
          </el-button>
        </template>
      </div>

      <div class="contacts-list">
        <div v-if="activeTab === 'friends'">
          <div
            v-for="friend in contactStore.friends"
            :key="friend.id"
            class="contact-item"
            @click="startChatWithFriend(friend)"
          >
            <el-avatar :size="40" :src="friend.avatar">
              {{ friend.username?.charAt(0)?.toUpperCase() }}
            </el-avatar>
            <div class="contact-info">
              <div class="contact-name">{{ friend.nickname || friend.username }}</div>
              <div class="contact-status" :class="{ online: friend.online }">
                {{ friend.online ? '在线' : '离线' }}
              </div>
            </div>
          </div>
          <el-empty v-if="contactStore.friends.length === 0" description="暂无好友" />
        </div>

        <div v-else-if="activeTab === 'groups'">
          <div
            v-for="group in contactStore.groups"
            :key="group.id"
            class="contact-item"
            @click="startChatWithGroup(group)"
          >
            <el-avatar :size="40" :src="group.avatar">
              {{ group.groupName?.charAt(0)?.toUpperCase() }}
            </el-avatar>
            <div class="contact-info">
              <div class="contact-name">{{ group.groupName }}</div>
              <div class="contact-status">{{ group.memberCount }} 人</div>
            </div>
          </div>
          <el-empty v-if="contactStore.groups.length === 0" description="暂无群组" />
        </div>

        <div v-else-if="activeTab === 'requests'">
          <div
            v-for="request in contactStore.friendRequests"
            :key="request.id"
            class="contact-item contact-request-item"
          >
            <el-avatar :size="40" :src="request.avatar">
              {{ request.username?.charAt(0)?.toUpperCase() }}
            </el-avatar>
            <div class="contact-info">
              <div class="contact-name">{{ request.nickname || request.username }}</div>
              <div class="contact-status">请求添加你为好友</div>
            </div>
            <div class="request-actions">
              <el-button type="primary" size="small" @click="handleAcceptRequest(request)">接受</el-button>
              <el-button size="small" @click="handleRejectRequest(request)">拒绝</el-button>
            </div>
          </div>
          <el-empty v-if="contactStore.friendRequests.length === 0" description="暂无好友申请" />
        </div>
      </div>
    </div>

    <div class="contacts-content">
      <el-empty description="选择联系人开始聊天" />
    </div>

    <el-dialog v-model="showAddFriendDialog" title="添加好友" width="400px">
      <el-form :model="addFriendForm">
        <el-form-item label="用户名">
          <el-input v-model="addFriendForm.username" placeholder="请输入用户名" @keyup.enter="handleSearchUser">
            <template #append>
              <el-button @click="handleSearchUser">搜索</el-button>
            </template>
          </el-input>
        </el-form-item>
        <div v-if="searchedUser" class="search-result">
          <el-avatar :size="40" :src="searchedUser.avatar">
            {{ searchedUser.username?.charAt(0)?.toUpperCase() }}
          </el-avatar>
          <div class="search-result-info">
            <div class="search-result-name">{{ searchedUser.nickname || searchedUser.username }}</div>
            <div class="search-result-username">@{{ searchedUser.username }}</div>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="showAddFriendDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddFriend" :disabled="!searchedUser">发送申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCreateGroupDialog" title="创建群组" width="500px">
      <el-form :model="createGroupForm">
        <el-form-item label="群组名称">
          <el-input v-model="createGroupForm.groupName" placeholder="请输入群组名称" />
        </el-form-item>
        <el-form-item label="群公告">
          <el-input
            v-model="createGroupForm.notice"
            type="textarea"
            :rows="3"
            placeholder="请输入群公告（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateGroupDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateGroup">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showJoinGroupDialog" title="加入群组" width="400px">
      <el-form :model="joinGroupForm">
        <el-form-item label="群组名称">
          <el-input v-model="joinGroupForm.groupName" placeholder="请输入群组名称" @keyup.enter="handleJoinGroup">
            <template #append>
              <el-button @click="handleJoinGroup">加入</el-button>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useContactStore } from '@/stores/contact'
import { useChatStore } from '@/stores/chat'
import { authApi } from '@/api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const contactStore = useContactStore()
const chatStore = useChatStore()

const activeTab = ref('friends')
const showAddFriendDialog = ref(false)
const showCreateGroupDialog = ref(false)
const showJoinGroupDialog = ref(false)
const searchedUser = ref(null)

const addFriendForm = reactive({
  username: ''
})

const createGroupForm = reactive({
  groupName: '',
  notice: ''
})

const joinGroupForm = reactive({
  groupName: ''
})

onMounted(async () => {
  await contactStore.fetchFriends()
  await contactStore.fetchGroups()
  await contactStore.fetchPendingRequests()
})

const startChatWithFriend = (friend) => {
  const conversation = {
    id: `private_${friend.id}`,
    type: 'private',
    userId: friend.id,
    name: friend.nickname || friend.username,
    avatar: friend.avatar,
    lastMessage: '',
    lastTime: '',
    unread: 0
  }
  
  chatStore.setCurrentConversation(conversation)
  
  if (!chatStore.conversations.find(c => c.id === conversation.id)) {
    chatStore.conversations.unshift(conversation)
  }
  
  router.push('/chat')
}

const startChatWithGroup = (group) => {
  const conversation = {
    id: `group_${group.id}`,
    type: 'group',
    groupId: group.id,
    name: group.groupName,
    avatar: group.avatar,
    lastMessage: '',
    lastTime: '',
    unread: 0
  }
  
  chatStore.setCurrentConversation(conversation)
  
  if (!chatStore.conversations.find(c => c.id === conversation.id)) {
    chatStore.conversations.unshift(conversation)
  }
  
  router.push('/chat')
}

const handleSearchUser = async () => {
  if (!addFriendForm.username) {
    ElMessage.warning('请输入用户名')
    return
  }
  
  try {
    const res = await authApi.searchUserByUsername(addFriendForm.username)
    searchedUser.value = res.data
  } catch (error) {
    searchedUser.value = null
    ElMessage.error(error.response?.data?.message || '用户不存在')
  }
}

const handleAddFriend = async () => {
  if (!searchedUser.value) {
    ElMessage.warning('请先搜索用户')
    return
  }
  
  try {
    await contactStore.sendFriendRequest(searchedUser.value.id)
    ElMessage.success('好友申请已发送')
    showAddFriendDialog.value = false
    addFriendForm.username = ''
    searchedUser.value = null
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '发送好友申请失败')
  }
}

const handleAcceptRequest = async (user) => {
  try {
    await contactStore.acceptFriendRequest(user.id)
    ElMessage.success('已接受好友申请')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '接受好友申请失败')
  }
}

const handleRejectRequest = async (user) => {
  try {
    await contactStore.rejectFriendRequest(user.id)
    ElMessage.success('已拒绝好友申请')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '拒绝好友申请失败')
  }
}

const handleCreateGroup = async () => {
  if (!createGroupForm.groupName) {
    ElMessage.warning('请输入群组名称')
    return
  }
  
  try {
    await contactStore.createGroup(createGroupForm)
    ElMessage.success('群组创建成功')
    showCreateGroupDialog.value = false
    createGroupForm.groupName = ''
    createGroupForm.notice = ''
    await contactStore.fetchGroups()
  } catch (error) {
    console.error('Failed to create group:', error)
  }
}

const handleJoinGroup = async () => {
  if (!joinGroupForm.groupName) {
    ElMessage.warning('请输入群组名称')
    return
  }
  
  try {
    await contactStore.joinGroupByName(joinGroupForm.groupName)
    ElMessage.success('加入群组成功')
    showJoinGroupDialog.value = false
    joinGroupForm.groupName = ''
    await contactStore.fetchGroups()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加入群组失败')
  }
}
</script>

<style scoped>
.contacts-container {
  display: flex;
  height: 100%;
  background-color: white;
}

.contacts-sidebar {
  width: 300px;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
}

.contacts-actions {
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
}

.contacts-actions .el-button {
  width: 100%;
}

.contacts-list {
  flex: 1;
  overflow-y: auto;
}

.contact-item {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.contact-item:hover {
  background-color: #f5f5f5;
}

.contact-info {
  margin-left: 12px;
  flex: 1;
}

.contact-name {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.contact-status {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.contact-status.online {
  color: #67c23a;
}

.search-result {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 8px;
  margin-top: 8px;
}

.search-result-info {
  flex: 1;
}

.search-result-name {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.search-result-username {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.request-actions {
  display: flex;
  gap: 8px;
}

.contact-request-item {
  padding: 16px 20px;
}

.contacts-content {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>
