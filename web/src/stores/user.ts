import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { LoginResponse, MenuNode, ProfileResponse, TenantSummary, UserInfo } from '@/types'
import { clearToken, getToken, setToken } from '@/utils/auth'

/**
 * 登录态与权限。
 *
 * <p>权限码与已购模块都来自后端：权限码控制按钮显隐，
 * 已购模块决定菜单分组是否出现。前端不自己推断，避免与后端不一致。</p>
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken())
  const user = ref<UserInfo | null>(null)
  const tenant = ref<TenantSummary | null>(null)
  const permissions = ref<string[]>([])
  const modules = ref<string[]>([])
  const menus = ref<MenuNode[]>([])
  /** 动态路由是否已注入，避免重复 addRoute */
  const routesReady = ref(false)

  const isPlatformAdmin = computed(() => user.value?.platformAdmin === true)
  const displayName = computed(() => user.value?.realName || user.value?.username || '未登录')

  function applyLogin(data: LoginResponse) {
    token.value = data.token
    setToken(data.token)
    user.value = data.user
    tenant.value = data.tenant
    permissions.value = data.permissions ?? []
    modules.value = data.modules ?? []
  }

  async function login(payload: { tenantCode: string; username: string; password: string }) {
    applyLogin(await authApi.login(payload))
  }

  async function platformLogin(payload: { username: string; password: string }) {
    applyLogin(await authApi.platformLogin(payload))
  }

  async function loadProfile() {
    const profile: ProfileResponse = await authApi.fetchProfile()
    user.value = profile.user
    tenant.value = profile.tenant
    permissions.value = profile.permissions ?? []
    modules.value = profile.modules ?? []
  }

  async function loadMenus() {
    menus.value = await authApi.fetchMenus()
  }

  /** 是否拥有某权限码（平台超管恒为 true） */
  function has(code: string): boolean {
    if (isPlatformAdmin.value) {
      return true
    }
    return permissions.value.includes(code)
  }

  /** 当前租户是否购买了某模块 */
  function hasModule(moduleKey: string): boolean {
    if (isPlatformAdmin.value) {
      return true
    }
    return modules.value.includes(moduleKey)
  }

  function logout() {
    token.value = ''
    user.value = null
    tenant.value = null
    permissions.value = []
    modules.value = []
    menus.value = []
    routesReady.value = false
    clearToken()
  }

  return {
    token,
    user,
    tenant,
    permissions,
    modules,
    menus,
    routesReady,
    isPlatformAdmin,
    displayName,
    login,
    platformLogin,
    loadProfile,
    loadMenus,
    has,
    hasModule,
    logout
  }
})
