import { createApp } from 'vue'
import { createPinia } from 'pinia'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

// 组件由 unplugin-vue-components 在编译期按 <el-xxx> 自动引入（JS 按需、样式自动注入）。
// 全量引入 ElementPlus 会拖慢首屏（整库 JS 约 2.4MB + CSS 约 350KB），故不再 app.use(ElementPlus)。
//
// 但要注意：ElMessage / ElMessageBox / ElLoading / ElNotification 这类是「编程式」API——
// 它们不在模板里，插件扫不到，必须在这里手动引一次，否则调用时不渲染（表现为点了没反应）。
// 这里只引入它们的独立样式文件，不是整个库，开销可以忽略。
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-loading.css'
import 'element-plus/theme-chalk/el-notification.css'

import './styles/index.css'

import App from './App.vue'
import router from './router'
import { permissionDirective } from './directives/permission'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 全量注册图标：菜单里的 icon 字段是图标组件名，需要动态解析
// （SidebarItem.vue 用<component :is="menu.icon" />，名字来自后端菜单数据）
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.directive('permission', permissionDirective)

app.mount('#app')
