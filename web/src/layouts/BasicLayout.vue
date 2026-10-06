<template>
  <el-container class="layout">
    <!-- 桌面端：固定侧栏 -->
    <el-aside v-if="!isMobile" width="220px" class="layout-aside">
      <side-menu />
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <!-- 移动端：汉堡按钮唤出抽屉菜单 -->
          <el-button v-if="isMobile" text class="menu-toggle" @click="drawerVisible = true">
            <el-icon :size="20"><Fold /></el-icon>
          </el-button>

          <el-breadcrumb separator="/">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentTitle">{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <!-- 已启用模块标签在手机上会挤成一团，直接不渲染 -->
          <div v-if="!isMobile" class="module-tags">
            <el-tag v-for="m in store.modules" :key="m" size="small" effect="plain">
              {{ moduleLabel(m) }}
            </el-tag>
          </div>

          <el-dropdown @command="onCommand">
            <span class="user-chip">
              <el-icon><UserFilled /></el-icon>
              <span class="user-name">{{ store.displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>企业：{{ tenantName }}</el-dropdown-item>
                <el-dropdown-item disabled>账号：{{ store.user?.username }}</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 移动端：抽屉式菜单，点菜单项后自动收起 -->
    <el-drawer
      v-if="isMobile"
      v-model="drawerVisible"
      direction="ltr"
      size="240px"
      :with-header="false"
      class="side-drawer"
    >
      <side-menu @select="drawerVisible = false" />
    </el-drawer>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import SideMenu from '@/components/SideMenu.vue'
import { useIsMobile } from '@/composables/useIsMobile'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const route = useRoute()
const isMobile = useIsMobile()

const drawerVisible = ref(false)

const currentTitle = computed(() => (route.meta.title as string) || '')
const tenantName = computed(() => store.tenant?.name || '未设置企业')

const MODULE_LABELS: Record<string, string> = {
  tenant: '企业设置',
  iam: '组织与权限',
  customer: '客户管理',
  pool: '公海池'
}

function moduleLabel(key: string) {
  return MODULE_LABELS[key] || key
}

async function onCommand(command: string) {
  if (command !== 'logout') {
    return
  }
  await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  store.logout()
  // 直接整页跳转：动态路由是按用户注入的，重载页面才能保证下一个用户拿到干净的路由表
  window.location.href = '/login'
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.layout-aside {
  background-color: #1f2d3d;
  overflow-y: auto;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.menu-toggle {
  padding: 4px;
  margin-left: -8px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.module-tags {
  display: flex;
  gap: 6px;
  max-width: 420px;
  overflow: hidden;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #303133;
  outline: none;
}

.layout-main {
  background: #f0f2f5;
  padding: 0;
}

/* 手机上顶部与内容区留白收紧 */
@media (max-width: 768px) {
  .layout-header {
    padding: 0 10px;
    height: 52px;
  }

  /* 用户名过长时截断，别把汉堡按钮挤没 */
  .user-name {
    max-width: 84px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>

<!--
  抽屉是 teleport 到 body 的，scoped 样式管不到它的内部结构，
  所以这段必须放在非 scoped 的 style 里。
-->
<style>
.side-drawer .el-drawer__body {
  padding: 0;
  overflow-y: auto;
  background-color: #1f2d3d;
}
</style>
