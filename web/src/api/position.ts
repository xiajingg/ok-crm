import { del, get, post, put } from './request'
import type { Id } from '@/types'

export interface PositionRow {
  id: Id
  code: string
  name: string
  /** ALL | SELF */
  dataScope: string
  dataScopeLabel: string
  status: number
  statusLabel: string
  sortOrder: number
  remark?: string
  permissions: string[]
  createdAt: string
}

export interface PositionForm {
  code?: string
  name?: string
  dataScope?: string
  status?: number
  sortOrder?: number
  remark?: string
  permissions?: string[]
}

export interface PermissionNode {
  code: string
  name: string
  moduleKey: string
  /** MENU | BUTTON */
  type: string
  parentCode?: string
  path?: string
  component?: string
  icon?: string
  sortOrder?: number
}

export function listPositions(keyword?: string) {
  return get<PositionRow[]>('/positions', { keyword })
}

export function getPosition(id: Id) {
  return get<PositionRow>(`/positions/${id}`)
}

export function createPosition(data: PositionForm) {
  return post<PositionRow>('/positions', data)
}

export function updatePosition(id: Id, data: PositionForm) {
  return put<PositionRow>(`/positions/${id}`, data)
}

export function deletePosition(id: Id) {
  return del<void>(`/positions/${id}`)
}

/** 单独保存权限树 */
export function assignPositionPermissions(id: Id, permissionCodes: string[]) {
  return put<void>(`/positions/${id}/permissions`, permissionCodes)
}

/** 权限点目录（用于权限树渲染） */
export function fetchPermissionCatalog(moduleKey?: string) {
  return get<PermissionNode[]>('/permissions/catalog', { moduleKey })
}
