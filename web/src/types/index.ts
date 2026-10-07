/** 与后端约定的公共类型 */

/**
 * 实体主键。
 *
 * 后端用 MyBatis-Plus 雪花算法生成 19 位 ID（约 2.1e18），**超过 JavaScript 的
 * `Number.MAX_SAFE_INTEGER`（9007199254740991 ≈ 9e15）**，`JSON.parse` 时会静默丢精度：
 *
 * ```
 * 后端下发   "2107832173509300225"
 * 写成数字   2107832173509300225  → JS 里实际是 2107832173509300200
 * ```
 *
 * 所以后端统一把这类 ID 序列化成**字符串**下发（见 `JacksonConfig`）。
 *
 * ⚠ 前端必须**原样回传**：不要 `Number(id)`、不要 `parseInt(id)`、不要参与算术。
 *   一转就丢精度，后端按这个 ID 查不到数据，报出来的是「存在无效的岗位，请刷新后重试」
 *   这种看起来毫无道理的错。
 */
export type Id = string

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
  id: Id
  code: string
  name: string
  region: string
  regionName?: string
  timezone?: string
  currency?: string
  status?: number
}

export interface UserInfo {
  id: Id
  username: string
  realName?: string
  platformAdmin: boolean
}

export interface TenantSummary {
  id: Id
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
