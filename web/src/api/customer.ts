import { del, get, post, put } from './request'
import type { PageResult } from '@/types'

export interface CustomerRow {
  id: number
  name: string
  industry?: string
  level?: string
  source?: string
  phone?: string
  address?: string
  ownerId?: number | null
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
  ownerId?: number | null
  tags?: string
  remark?: string
}

export interface ContactRow {
  id: number
  customerId: number
  name: string
  position?: string
  phone?: string
  email?: string
  primaryContact?: boolean
  remark?: string
  createdAt: string
}

export interface FollowUpRow {
  id: number
  customerId: number
  employeeId?: number
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
  ownerId?: number
  poolOnly?: boolean
  pageNum?: number
  pageSize?: number
}) {
  return get<PageResult<CustomerRow>>('/customers', params)
}

export function getCustomer(id: number) {
  return get<CustomerRow>(`/customers/${id}`)
}

export function createCustomer(data: CustomerForm) {
  return post<CustomerRow>('/customers', data)
}

export function updateCustomer(id: number, data: CustomerForm) {
  return put<CustomerRow>(`/customers/${id}`, data)
}

export function deleteCustomer(id: number) {
  return del<void>(`/customers/${id}`)
}

/** 分配/转移归属（会写入公海流转日志） */
export function transferCustomer(id: number, ownerId: number, remark?: string) {
  return put<void>(`/customers/${id}/transfer`, { ownerId, remark })
}

export function batchAssignCustomers(customerIds: number[], ownerId: number) {
  return post<number>('/customers/batch-assign', { customerIds, ownerId })
}

export function releaseCustomer(id: number, reason?: string) {
  return put<void>(`/customers/${id}/release`, null, { reason })
}

// ---------------- 联系人 ----------------

export function listContacts(customerId: number) {
  return get<ContactRow[]>(`/customers/${customerId}/contacts`)
}

export function createContact(customerId: number, data: Partial<ContactRow>) {
  return post<ContactRow>(`/customers/${customerId}/contacts`, data)
}

export function updateContact(customerId: number, contactId: number, data: Partial<ContactRow>) {
  return put<ContactRow>(`/customers/${customerId}/contacts/${contactId}`, data)
}

export function deleteContact(customerId: number, contactId: number) {
  return del<void>(`/customers/${customerId}/contacts/${contactId}`)
}

// ---------------- 跟进记录 ----------------

export function pageFollowUps(customerId: number, params: { pageNum?: number; pageSize?: number }) {
  return get<PageResult<FollowUpRow>>(`/customers/${customerId}/follow-ups`, params)
}

export function createFollowUp(customerId: number, data: {
  type?: string
  content: string
  followedAt?: string
  nextFollowUpAt?: string
}) {
  return post<FollowUpRow>(`/customers/${customerId}/follow-ups`, data)
}
