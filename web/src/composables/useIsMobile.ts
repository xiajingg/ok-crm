import { onMounted, onUnmounted, ref } from 'vue'

/**
 * 是否处于移动端视口。
 *
 * <p>用 `matchMedia` 而不是监听 resize：浏览器只在跨越断点时回调一次，开销更小，
 * 而且能正确处理设备旋转。</p>
 *
 * <p>注意要在组件 `setup` 里调用（依赖 onMounted / onUnmounted）。</p>
 */
export function useIsMobile(breakpoint = 768) {
  const query = `(max-width: ${breakpoint - 1}px)`
  // 同步初始化：避免首次渲染先按桌面布局画一遍、挂载后再切到移动端（会闪一下）
  const isMobile = ref(typeof window !== 'undefined' && window.matchMedia(query).matches)
  let mql: MediaQueryList | null = null

  const update = () => {
    isMobile.value = mql ? mql.matches : window.innerWidth < breakpoint
  }

  onMounted(() => {
    mql = window.matchMedia(query)
    update()
    mql.addEventListener('change', update)
  })

  onUnmounted(() => {
    mql?.removeEventListener('change', update)
  })

  return isMobile
}
