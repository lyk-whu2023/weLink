import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api'
import { wsService } from '@/utils/websocket'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  function setToken(newToken) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info) {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  async function login(loginForm) {
    const res = await authApi.login(loginForm)
    setToken(res.data.accessToken)
    setUserInfo(res.data.userInfo)
    wsService.connect(res.data.accessToken)
    return res.data
  }

  async function register(registerForm) {
    return await authApi.register(registerForm)
  }

  async function fetchUserInfo(userId) {
    const res = await authApi.getUserInfo(userId)
    return res.data
  }

  function logout() {
    wsService.disconnect()
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  return {
    token,
    userInfo,
    setToken,
    setUserInfo,
    login,
    register,
    fetchUserInfo,
    logout
  }
})
