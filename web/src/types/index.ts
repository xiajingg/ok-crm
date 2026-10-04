/** 与后端约定的公共类型 */

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

export interface MenuNode {
  code: string
  name: string
  path?: string
  component?: string
  icon?: string
  moduleKey?: string
  sortOrder?: number
  children?: MenuNode[]
}

export interface TenantBrief {
  id: number
  code: string
  name: string
  region: string
  regionName?: string
  timezone?: string
  currency?: string
  status?: number
}

export interface UserInfo {
  id: number
  username: string
  realName?: string
  platformAdmin: boolean
}

export interface TenantSummary {
  id: number
  code: string
  name: string
  region: string
  regionName?: string
  timezone?: string
  currency?: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expireSeconds: number
  user: UserInfo
  tenant: TenantSummary | null
  permissions: string[]
  modules: string[]
}

export interface ProfileResponse {
  user: UserInfo
  tenant: TenantSummary | null
  permissions: string[]
  modules: string[]
}
