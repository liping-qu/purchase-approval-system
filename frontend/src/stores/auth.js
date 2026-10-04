import { computed, reactive } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../api'

export const useAuthStore = defineStore('auth', () => {
  const user = reactive(JSON.parse(localStorage.getItem('procurement_user') || 'null') || {})
  const token = computed(() => localStorage.getItem('procurement_token') || '')
  const isAuthenticated = computed(() => Boolean(token.value))

  async function login(payload) {
    const result = await api.login(payload)
    const body = result?.data || result || {}
    const nextToken = body.token || body.accessToken || body.jwt || 'mock-token'
    const nextUser = body.user || body.profile || { username: payload.username, nickname: payload.username, roles: ['EMPLOYEE'] }
    localStorage.setItem('procurement_token', nextToken)
    localStorage.setItem('procurement_user', JSON.stringify(nextUser))
    Object.assign(user, nextUser)
    return nextUser
  }
  function logout() { localStorage.removeItem('procurement_token'); localStorage.removeItem('procurement_user'); Object.keys(user).forEach((key) => delete user[key]) }
  return { user, token, isAuthenticated, login, logout }
})
