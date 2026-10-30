import { computed, reactive } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../api'

/**
 * 登录态。
 *
 * <p>改造前这里读的是 {@code body.token} 和 {@code body.user}，而后端
 * {@code LoginResponse} 是扁平的 <code>{ token, userId, username, displayName, roles }</code>：
 * {@code body.user} 恒为 undefined，于是每次都退到兜底值
 * <code>{ nickname: 用户名, roles: ['EMPLOYEE'] }</code>——<b>不管登录的是管理员还是采购员，
 * 前端都当他是普通员工</b>。按角色显隐的按钮因此永远不出现，而且不发请求、不报错，
 * 从页面上完全看不出来。</p>
 *
 * <p>现在按后端真实结构解析；缺 token 直接抛错，不再用 'mock-token' 掩盖——
 * 带假 token 进主页换来的是后面每个接口 401，比登录当场失败更难排查。</p>
 */
export const useAuthStore = defineStore('auth', () => {
  const user = reactive(JSON.parse(localStorage.getItem('procurement_user') || 'null') || {})
  const token = computed(() => localStorage.getItem('procurement_token') || '')
  const isAuthenticated = computed(() => Boolean(token.value))
  const roles = computed(() => (Array.isArray(user.roles) ? user.roles : []))

  function persist(profile) {
    localStorage.setItem('procurement_user', JSON.stringify(profile))
    Object.assign(user, profile)
  }

  async function login(payload) {
    const body = (await api.login(payload)) || {}
    if (!body.token) throw new Error('登录响应缺少 token')
    localStorage.setItem('procurement_token', body.token)
    persist({
      id: body.userId,
      username: body.username,
      nickname: body.displayName || body.username,
      roles: body.roles || []
    })
    // 登录响应不含部门与权限位，补一次 /auth/me 拿全；
    // 失败不影响登录本身，只是页面上少显示部门名。
    try {
      const me = await api.me()
      if (me) persist({ ...user, ...me, roles: me.roles || user.roles, nickname: me.displayName || user.nickname })
    } catch (ignored) { /* 非关键信息，忽略 */ }
    return user
  }

  function logout() {
    localStorage.removeItem('procurement_token')
    localStorage.removeItem('procurement_user')
    Object.keys(user).forEach((key) => delete user[key])
  }

  return { user, token, roles, isAuthenticated, login, logout }
})
