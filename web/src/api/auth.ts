import { get, post } from './request'
import type { LoginResponse, MenuNode, ProfileResponse, TenantBrief } from '@/types'

/** 登录页的租户下拉列表（免登录） */
export function fetchLoginTenants() {
  return get<TenantBrief[]>('/auth/tenants')
}

export function login(data: { tenantCode: string; username: string; password: string }) {
  return post<LoginResponse>('/auth/login', data)
}

export function platformLogin(data: { username: string; password: string }) {
  return post<LoginResponse>('/auth/platform-login', data)
}

export function fetchProfile() {
  return get<ProfileResponse>('/auth/me')
}

/** 动态菜单：后端已按「已购模块 ∩ 岗位权限」过滤 */
export function fetchMenus() {
  return get<MenuNode[]>('/auth/menus')
}
