import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import 'element-plus/dist/index.css'
import './styles/index.css'

import App from './App.vue'
import router from './router'
import { permissionDirective } from './directives/permission'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 全量注册图标：菜单里的 icon 字段是图标组件名，需要动态解析
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.directive('permission', permissionDirective)

app.mount('#app')
