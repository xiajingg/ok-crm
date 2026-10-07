import { del, get, post, put } from './request'
import type { PageResult, Id } from '@/types'

export interface EmployeeRow {
  id: Id
  username: string
  realName?: string
  phone?: string
  email?: string
  status: number
  statusLabel: string
  remark?: string
  positionIds: Id[]
  positionNames: string[]
  lastLoginAt?: string
  createdAt: string
}

export interface EmployeeOption {
  id: Id
  username: string
  realName?: string
  phone?: string
  status?: number
}

export interface EmployeeForm {
  username?: string
  password?: string
  realName?: string
  phone?: string
  email?: string
  status?: number
  remark?: string
  positionIds?: Id[]
}

export function pageEmployees(params: {
  keyword?: string
  status?: number
  pageNum?: number
  pageSize?: number
}) {
  return get<PageResult<EmployeeRow>>('/employees', params)
}

export function getEmployee(id: Id) {
  return get<EmployeeRow>(`/employees/${id}`)
}

export function createEmployee(data: EmployeeForm) {
  return post<EmployeeRow>('/employees', data)
}

export function updateEmployee(id: Id, data: EmployeeForm) {
  return put<EmployeeRow>(`/employees/${id}`, data)
}

export function deleteEmployee(id: Id) {
  return del<void>(`/employees/${id}`)
}

export function changeEmployeeStatus(id: Id, enabled: boolean) {
  return put<void>(`/employees/${id}/status`, null, { enabled })
}

export function resetEmployeePassword(id: Id, password: string) {
  return put<void>(`/employees/${id}/password`, null, { password })
}

/** 在职员工下拉（客户负责人选择用） */
export function listEmployeeOptions() {
  return get<EmployeeOption[]>('/employees/options')
}
