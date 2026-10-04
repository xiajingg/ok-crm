import { get, post, put } from './request'
import type { PageResult } from '@/types'

/** 公海客户（后端返回的是 CustomerBrief） */
export interface PoolCustomerRow {
  id: number
  name: string
  industry?: string
  level?: string
  ownerId?: number | null
  ownerAssignedAt?: string
  lastFollowUpAt?: string
  enterPoolAt?: string
}

export interface PoolLogRow {
  id: number
  customerId: number
  customerName?: string
  /** CLAIM | ASSIGN | TRANSFER | RELEASE | RECYCLE */
  action: string
  actionLabel: string
  fromOwnerId?: number
  fromOwnerName?: string
  toOwnerId?: number
  toOwnerName?: string
  reason?: string
  operatorId?: number
  operatorName?: string
  createdAt: string
}

export interface PoolSettings {
  enabled: boolean
  days: number
  /** LAST_FOLLOWUP | CREATE_TIME */
  basis: string
  claimLimit: number
}

export function pagePoolCustomers(params: { keyword?: string; pageNum?: number; pageSize?: number }) {
  return get<PageResult<PoolCustomerRow>>('/pool/customers', params)
}

/** 员工主动领取 */
export function claimCustomer(customerId: number) {
  return post<void>(`/pool/customers/${customerId}/claim`)
}

/** 主管指派 */
export function assignCustomer(customerId: number, employeeId: number, remark?: string) {
  return post<void>('/pool/assign', { customerId, employeeId, remark })
}

export function releaseCustomerToPool(customerId: number, reason?: string) {
  const query = reason ? `?reason=${encodeURIComponent(reason)}` : ''
  return post<void>(`/pool/customers/${customerId}/release${query}`)
}

export function pagePoolLogs(params: { customerId?: number; pageNum?: number; pageSize?: number }) {
  return get<PageResult<PoolLogRow>>('/pool/logs', params)
}

export function getPoolSettings() {
  return get<PoolSettings>('/pool/settings')
}

export function updatePoolSettings(data: PoolSettings) {
  return put<void>('/pool/settings', data)
}

/** 手工触发本租户回收，用于验证规则 */
export function runRecycleNow() {
  return post<number>('/pool/recycle/run')
}
