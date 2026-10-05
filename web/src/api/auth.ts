import { get, post } from './request'
import type { LoginResponse, MenuNode, ProfileResponse } from '@/types'

/** 部署信息：登录页展示企业名称用（免登录） */
export function fetchDeploymentInfo() {
  return get<{ tenantName: string | null; tenantCode: string | null }>('/auth/deployment')
}

/** 登录。单企业私有化部署，只需账号密码，不需要选企业 */
export function login(data: { username: string; password: string }) {
  return post<LoginResponse>('/auth/login', data)
}

export function fetchProfile() {
  return get<ProfileResponse>('/auth/me')
}

/** 动态菜单：后端已按「已启用模块 ∩ 岗位权限」过滤 */
export function fetchMenus() {
  return get<MenuNode[]>('/auth/menus')
}
