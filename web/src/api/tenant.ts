import { get, put } from './request'
import type { Id } from '@/types'

/**
 * 企业设置。
 *
 * 单企业私有化部署：一套系统只服务一个企业，因此这里只有「当前企业」，
 * 不再有开通、停用、分页管理这些多租户运营接口。
 */
export interface TenantRow {
  id: Id
  code: string
  name: string
  region: string
  regionName?: string
  timezone?: string
  currency?: string
  contactName?: string
  contactPhone?: string
  status: number
  statusLabel: string
  createdAt: string
}

/** 可修改的企业信息（企业编码不可改） */
export interface TenantForm {
  name?: string
  region?: string
  regionName?: string
  timezone?: string
  currency?: string
  contactName?: string
  contactPhone?: string
}

export function fetchCurrentTenant() {
  return get<TenantRow>('/tenants/current')
}

export function updateCurrentTenant(data: TenantForm) {
  return put<TenantRow>('/tenants/current', data)
}

/** 本部署已启用的模块 */
export function fetchCurrentModules() {
  return get<string[]>('/tenants/current/modules')
}

export function fetchCurrentConfig() {
  return get<Record<string, string>>('/tenants/current/config')
}

export function updateCurrentConfig(configs: Record<string, string>) {
  return put<void>('/tenants/current/config', { configs })
}
