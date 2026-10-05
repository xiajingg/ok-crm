<template>
  <div class="login-page">
    <div class="login-card">
      <h2 class="login-title">{{ title }}</h2>
      <p class="login-subtitle">客户管理系统</p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="submit"
      >
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" placeholder="请输入账号" clearable />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>

        <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">
          登 录
        </el-button>
      </el-form>

      <!-- 仅开发环境显示默认凭据，避免生产部署把初始密码暴露在登录页上 -->
      <el-alert v-if="isDev" type="info" :closable="false" style="margin-top: 16px">
        <template #title>
          开发环境默认凭据：admin / admin123456<br />
          生产部署的初始密码由启动日志输出（或由 okcrm.setup.admin.password 指定）
        </template>
      </el-alert>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { fetchDeploymentInfo } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const router = useRouter()
const route = useRoute()

const isDev = import.meta.env.DEV
const loading = ref(false)
const formRef = ref<FormInstance>()
const deploymentName = ref<string | null>(null)

const form = reactive({
  username: 'admin',
  password: ''
})

/** 登录页标题带上企业名，部署到客户那边看起来才像他们自己的系统 */
const title = computed(() => (deploymentName.value ? `${deploymentName.value} 客户管理系统` : 'OK-CRM'))

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(async () => {
  try {
    const info = await fetchDeploymentInfo()
    deploymentName.value = info.tenantName
  } catch {
    // 企业还没初始化时接口返回空对象，忽略即可
  }
})

async function submit() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate(async (valid) => {
    if (!valid) {
      return
    }
    loading.value = true
    try {
      await store.login({ username: form.username, password: form.password })
      ElMessage.success('登录成功')
      const redirect = (route.query.redirect as string) || '/dashboard'
      await router.push(redirect)
    } finally {
      loading.value = false
    }
  })
}
</script>
