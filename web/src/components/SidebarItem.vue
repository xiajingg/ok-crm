<template>
  <!-- 有子菜单：渲染折叠菜单；若自身也带页面，则把自身作为第一个子项，避免「分组节点点不进去」 -->
  <el-sub-menu v-if="childItems.length" :index="menu.code">
    <template #title>
      <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
      <span>{{ menu.name }}</span>
    </template>
    <sidebar-item v-for="child in childItems" :key="child.code" :menu="child" />
  </el-sub-menu>

  <el-menu-item v-else :index="menu.path || menu.code">
    <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
    <template #title>{{ menu.name }}</template>
  </el-menu-item>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MenuNode } from '@/types'

defineOptions({ name: 'SidebarItem' })

const props = defineProps<{ menu: MenuNode }>()

const GROUP_COMPONENT = 'Layout'

const childItems = computed<MenuNode[]>(() => {
  const children = props.menu.children ?? []
  if (children.length === 0) {
    return []
  }
  // pool:menu 这类节点既有自己的页面又有子页面，补一个指向自身的条目
  if (props.menu.path && props.menu.component && props.menu.component !== GROUP_COMPONENT) {
    return [{ ...props.menu, name: `${props.menu.name}列表`, children: undefined }, ...children]
  }
  return children
})
</script>
