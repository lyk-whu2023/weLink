import api from '@/utils/request'

export const authApi = {
  register(data) {
    return api.post('/auth/register', data)
  },

  login(data) {
    return api.post('/auth/login', data)
  },

  getUserInfo(userId) {
    return api.get(`/user/${userId}`)
  },

  searchUserByUsername(username) {
    return api.get('/auth/user/search', { params: { username } })
  }
}

export const friendApi = {
  sendRequest(friendId) {
    return api.post(`/friend/apply/${friendId}`)
  },

  sendRequestByUsername(username) {
    return api.post('/friend/apply/username', null, { params: { username } })
  },

  acceptRequest(friendId) {
    return api.post(`/friend/accept/${friendId}`)
  },

  rejectRequest(friendId) {
    return api.post(`/friend/reject/${friendId}`)
  },

  getList() {
    return api.get('/friend/list')
  },

  getPendingRequests() {
    return api.get('/friend/requests/pending')
  },

  deleteFriend(friendId) {
    return api.delete(`/friend/${friendId}`)
  }
}

export const groupApi = {
  create(data) {
    return api.post('/group', data)
  },

  join(groupId) {
    return api.post(`/group/join/${groupId}`)
  },

  joinByName(groupName) {
    return api.post('/group/join/by-name', null, { params: { groupName } })
  },

  getList() {
    return api.get('/group/list')
  },

  getMembers(groupId) {
    return api.get(`/group/${groupId}/members`)
  },

  inviteMembers(groupId, memberIds) {
    return api.post(`/group/${groupId}/invite`, memberIds)
  },

  kickMember(groupId, targetId) {
    return api.delete(`/group/${groupId}/kick/${targetId}`)
  },

  quitGroup(groupId) {
    return api.delete(`/group/${groupId}/quit`)
  }
}

export const messageApi = {
  getConversationSummaries() {
    return api.get('/message/conversations')
  },

  getPrivateHistory(params) {
    return api.get('/message/history/private', { params })
  },

  getGroupHistory(params) {
    return api.get('/message/history/group', { params })
  },

  getOfflineMessages() {
    return api.get('/message/offline')
  },

  markAsRead(msgId) {
    return api.post(`/message/read/${msgId}`)
  },

  markConversationAsRead(params) {
    return api.post('/message/read/conversation', null, { params })
  }
}

export const fileApi = {
  upload(file) {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/file/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  }
}
