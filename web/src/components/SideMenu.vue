<template>
  <div class="side-menu">
    <div class="side-logo">
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
      @select="emit('select')"
    >
      <el-menu-item index="/dashboard">
        <el-icon><HomeFilled /></el-icon>
        <template #title>工作台</template>
      </el-menu-item>
      <sidebar-item v-for="menu in store.menus" :key="menu.code" :menu="menu" />
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SidebarItem from '@/components/SidebarItem.vue'
import { useUserStore } from '@/stores/user'

/**
 * 侧边菜单。
 *
 * 桌面端放在固定侧栏里，移动端放在抽屉里 —— 抽出来是为了两处共用同一份菜单渲染逻辑，
 * 避免移动端再维护一套。
 */
const emit = defineEmits<{ select: [] }>()

const store = useUserStore()
const route = useRoute()

const activeMenu = computed(() => route.path)
const tenantName = computed(() => store.tenant?.name || '未设置企业')
</script>

<style scoped>
.side-menu {
  height: 100%;
  background-color: #1f2d3d;
}

.side-logo {
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

.side-menu :deep(.el-menu) {
  border-right: none;
}
</style>
