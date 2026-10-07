import { del, get, post, put } from './request'
import type { PageResult, Id } from '@/types'

export interface CustomerRow {
  id: Id
  name: string
  industry?: string
  level?: string
  source?: string
  phone?: string
  address?: string
  ownerId?: Id | null
  ownerName?: string | null
  inPool: boolean
  ownerAssignedAt?: string
  lastFollowUpAt?: string
  enterPoolAt?: string
  poolReason?: string
  tags?: string
  remark?: string
  contactCount?: number
  createdAt: string
}

export interface CustomerForm {
  name?: string
  industry?: string
  level?: string
  source?: string
  phone?: string
  address?: string
  ownerId?: Id | null
  tags?: string
  remark?: string
}

export interface ContactRow {
  id: Id
  customerId: Id
  name: string
  position?: string
  phone?: string
  email?: string
  primaryContact?: boolean
  remark?: string
  createdAt: string
}

export interface FollowUpRow {
  id: Id
  customerId: Id
  employeeId?: Id
  employeeName?: string
  type?: string
  content: string
  followedAt?: string
  nextFollowUpAt?: string
  createdAt: string
}

export function pageCustomers(params: {
  keyword?: string
  level?: string
  ownerId?: Id
  poolOnly?: boolean
  pageNum?: number
  pageSize?: number
}) {
  return get<PageResult<CustomerRow>>('/customers', params)
}

export function getCustomer(id: Id) {
  return get<CustomerRow>(`/customers/${id}`)
}

export function createCustomer(data: CustomerForm) {
  return post<CustomerRow>('/customers', data)
}

export function updateCustomer(id: Id, data: CustomerForm) {
  return put<CustomerRow>(`/customers/${id}`, data)
}

export function deleteCustomer(id: Id) {
  return del<void>(`/customers/${id}`)
}

/** 分配/转移归属（会写入公海流转日志） */
export function transferCustomer(id: Id, ownerId: Id, remark?: string) {
  return put<void>(`/customers/${id}/transfer`, { ownerId, remark })
}

export function batchAssignCustomers(customerIds: Id[], ownerId: Id) {
  return post<number>('/customers/batch-assign', { customerIds, ownerId })
}

export function releaseCustomer(id: Id, reason?: string) {
  return put<void>(`/customers/${id}/release`, null, { reason })
}

// ---------------- 联系人 ----------------

export function listContacts(customerId: Id) {
  return get<ContactRow[]>(`/customers/${customerId}/contacts`)
}

export function createContact(customerId: Id, data: Partial<ContactRow>) {
  return post<ContactRow>(`/customers/${customerId}/contacts`, data)
}

export function updateContact(customerId: Id, contactId: Id, data: Partial<ContactRow>) {
  return put<ContactRow>(`/customers/${customerId}/contacts/${contactId}`, data)
}

export function deleteContact(customerId: Id, contactId: Id) {
  return del<void>(`/customers/${customerId}/contacts/${contactId}`)
}

// ---------------- 跟进记录 ----------------

export function pageFollowUps(customerId: Id, params: { pageNum?: number; pageSize?: number }) {
  return get<PageResult<FollowUpRow>>(`/customers/${customerId}/follow-ups`, params)
}

export function createFollowUp(customerId: Id, data: {
  type?: string
  content: string
  followedAt?: string
  nextFollowUpAt?: string
}) {
  return post<FollowUpRow>(`/customers/${customerId}/follow-ups`, data)
}
