import { del, get, post, put } from './request'
import type { PageResult } from '@/types'

export interface TenantRow {
  id: number
  code: string
  name: string
  region: string
  regionName?: string
  timezone?: string
  currency?: string
  contactName?: string
  contactPhone?: string
  expireDate?: string
  status: number
  statusLabel: string
  createdAt: string
}

export interface TenantForm {
  code?: string
  name?: string
  region?: string
  regionName?: string
  timezone?: string
  currency?: string
  contactName?: string
  contactPhone?: string
  expireDate?: string
  modules?: string[]
}

export interface ModuleRow {
  moduleKey: string
  name: string
  description?: string
  version?: string
  core?: boolean
  sortOrder?: number
  licensed: boolean
  expireDate?: string
}

export function pageTenants(params: { keyword?: string; pageNum?: number; pageSize?: number }) {
  return get<PageResult<TenantRow>>('/platform/tenants', params)
}

export function createTenant(data: TenantForm) {
  return post<TenantRow>('/platform/tenants', data)
}

export function updateTenant(id: number, data: TenantForm) {
  return put<TenantRow>(`/platform/tenants/${id}`, data)
}

export function changeTenantStatus(id: number, enabled: boolean) {
  return put<void>(`/platform/tenants/${id}/status`, null, { enabled })
}

export function getTenantConfig(id: number) {
  return get<Record<string, string>>(`/platform/tenants/${id}/config`)
}

export function updateTenantConfig(id: number, configs: Record<string, string>) {
  return put<void>(`/platform/tenants/${id}/config`, { configs })
}

/** 模块目录（产品价目表） */
export function fetchModuleCatalog() {
  return get<ModuleRow[]>('/platform/modules/catalog')
}

/** 某租户的模块授权明细 */
export function fetchTenantModules(tenantId: number) {
  return get<ModuleRow[]>(`/platform/modules/tenant/${tenantId}`)
}

export function grantModules(tenantId: number, data: { moduleKeys: string[]; expireDate?: string; remark?: string }) {
  return post<void>(`/platform/modules/tenant/${tenantId}/grant`, data)
}

export function revokeModule(tenantId: number, moduleKey: string) {
  return del<void>(`/platform/modules/tenant/${tenantId}/${moduleKey}`)
}

/** 当前租户信息与已购模块 */
export function fetchCurrentTenant() {
  return get<TenantRow>('/tenants/current')
}

export function fetchCurrentModules() {
  return get<string[]>('/tenants/current/modules')
}

export function fetchCurrentConfig() {
  return get<Record<string, string>>('/tenants/current/config')
}

export function updateCurrentConfig(configs: Record<string, string>) {
  return put<void>('/tenants/current/config', { configs })
}
