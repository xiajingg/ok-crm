import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限指令。
 *
 * <pre>{@code
 * <el-button v-permission="'customer:create'">新增客户</el-button>
 * <el-button v-permission="['customer:update', 'customer:delete']">编辑</el-button>
 * }</pre>
 *
 * <p>注意这只是「界面显隐」，真正的拦截在后端 {@code @PreAuthorize} 上。
 * 前端隐藏按钮是为了体验，不是安全边界。</p>
 */
export const permissionDirective: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const codes = Array.isArray(binding.value) ? binding.value : [binding.value]
    if (codes.length === 0) {
      return
    }
    const store = useUserStore()
    const allowed = codes.some((code) => store.has(code))
    if (!allowed) {
      el.parentNode?.removeChild(el)
    }
  }
}
