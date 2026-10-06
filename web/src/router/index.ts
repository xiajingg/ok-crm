import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { useUserStore } from '@/stores/user'
import type { MenuNode } from '@/types'

/** 后端菜单里用于「纯分组」的占位组件名，不产生真实路由 */
const GROUP_COMPONENT = 'Layout'

const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    name: 'Root',
    component: BasicLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台' }
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { public: true, title: '无权限' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    // ⚠️ 这里**不能**标 public。动态路由是登录后才注入的，刷新任意业务页时
    // 第一次匹配到的就是这条 catch-all；如果它标了 public，守卫会直接放行，
    // 动态路由永远没机会加载 —— 症状是「刷新页面就 404，点菜单进去却正常」。
    meta: { title: '页面不存在' }
  }
]

/**
 * 后端菜单里的 component 形如 {@code customer/index}，
 * 这里映射到 {@code src/views} 下的真实文件。
 *
 * <p>这就是「按模块独立售卖」在前端的落地点：没买公海池模块时后端不下发
 * pool 菜单，前端连对应组件都不会被注册，不需要维护两套前端代码。</p>
 */
const viewModules = import.meta.glob('../views/**/*.vue')

function resolveComponent(component: string) {
  return viewModules[`../views/${component}.vue`]
}

function buildRoutes(menus: MenuNode[]): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = []

  for (const menu of menus) {
    if (menu.path && menu.component && menu.component !== GROUP_COMPONENT) {
      const loader = resolveComponent(menu.component)
      if (loader) {
        routes.push({
          path: menu.path,
          name: menu.code,
          component: loader,
          meta: { title: menu.name, moduleKey: menu.moduleKey }
        })
      } else {
        console.warn(`[router] 菜单 ${menu.code} 指向的组件不存在: views/${menu.component}.vue`)
      }
    }
    if (menu.children?.length) {
      routes.push(...buildRoutes(menu.children))
    }
  }
  return routes
}

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async (to) => {
  const store = useUserStore()

  if (to.meta.public) {
    return true
  }

  if (!store.token) {
    return { path: '/login', query: to.fullPath === '/' ? undefined : { redirect: to.fullPath } }
  }

  // 首次进入受保护页面：拉资料 + 菜单，并把菜单转成真实路由注入
  if (!store.routesReady) {
    try {
      await store.loadProfile()
      await store.loadMenus()
      buildRoutes(store.menus).forEach((route) => router.addRoute('Root', route))
      store.routesReady = true
      // 路由表刚变化，重走一次本次导航，否则会落到 404。
      //
      // ⚠️ 这里必须用 path 而不是 `{ ...to }`：此时 to 已经被解析成了 404 那条
      // catch-all 路由，展开会把 name: 'NotFound' 一起带过去，重新导航又按名字匹配回 404。
      // 症状是「动态路由页面刷新/直接输网址就 404，点菜单进去却正常」。
      return { path: to.fullPath, replace: true }
    } catch (error) {
      console.error('[router] 初始化用户上下文失败', error)
      store.logout()
      return { path: '/login' }
    }
  }

  return true
})

export default router
