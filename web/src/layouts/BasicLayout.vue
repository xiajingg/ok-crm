<template>
  <el-container class="layout">
    <el-aside width="220px" class="layout-aside">
      <div class="layout-logo">
        <div class="logo-title">OK-CRM</div>
        <div class="logo-sub">{{ tenantName }}</div>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        unique-opened
        background-color="#1f2d3d"
        text-color="#c0c4cc"
        active-text-color="#ffd04b"
      >
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <template #title>工作台</template>
        </el-menu-item>
        <sidebar-item v-for="menu in store.menus" :key="menu.code" :menu="menu" />
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentTitle">{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <el-tooltip content="当前企业已购买的模块" placement="bottom">
            <div class="module-tags">
              <el-tag v-for="m in store.modules" :key="m" size="small" effect="plain">
                {{ moduleLabel(m) }}
              </el-tag>
            </div>
          </el-tooltip>

          <el-dropdown @command="onCommand">
            <span class="user-chip">
              <el-icon><UserFilled /></el-icon>
              <span>{{ store.displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  企业：{{ store.tenant?.name || '平台管理端' }}
                </el-dropdown-item>
                <el-dropdown-item disabled>
                  账号：{{ store.user?.username }}
                </el-dropdown-item>
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
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import SidebarItem from '@/components/SidebarItem.vue'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const route = useRoute()

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => (route.meta.title as string) || '')
const tenantName = computed(() => store.tenant?.name || (store.isPlatformAdmin ? '平台管理端' : '未选择企业'))

const MODULE_LABELS: Record<string, string> = {
  tenant: '租户与地区',
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

.layout-logo {
  height: 64px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 16px;
  border-bottom: 1px solid #2c3e50;
}

.logo-title {
  font-size: 18px;
  font-weight: 700;
  color: #ffd04b;
  letter-spacing: 1px;
}

.logo-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #8d9bab;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.layout-aside :deep(.el-menu) {
  border-right: none;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
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
</style>
