<template>
  <div class="login-page">
    <div class="login-card">
      <h2 class="login-title">OK-CRM 客户管理系统</h2>
      <p class="login-subtitle">多租户 · 岗位权限 · 客户归属与公海池</p>

      <el-tabs v-model="mode" stretch>
        <el-tab-pane label="企业登录" name="tenant" />
        <el-tab-pane label="平台管理端" name="platform" />
      </el-tabs>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="submit"
      >
        <el-form-item v-if="mode === 'tenant'" label="企业" prop="tenantCode">
          <el-select v-model="form.tenantCode" placeholder="请选择企业" style="width: 100%">
            <el-option
              v-for="item in tenants"
              :key="item.code"
              :label="`${item.name}（${item.code}）`"
              :value="item.code"
            />
          </el-select>
          <div v-if="tenants.length === 0" class="login-tip">
            还没有企业？请先用「平台管理端」登录并开通租户。
          </div>
        </el-form-item>

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

      <!-- 仅开发环境显示默认凭据，避免生产部署把默认密码暴露在登录页上 -->
      <el-alert v-if="isDev" type="info" :closable="false" style="margin-top: 16px">
        <template #title>
          开发环境默认凭据：<br />
          平台超管 admin / admin123456<br />
          租户管理员 admin / admin123456（开通租户时自动创建）
        </template>
      </el-alert>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { fetchLoginTenants } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { TenantBrief } from '@/types'

const store = useUserStore()
const router = useRouter()
const route = useRoute()

const isDev = import.meta.env.DEV
const mode = ref<'tenant' | 'platform'>('tenant')
const tenants = ref<TenantBrief[]>([])
const loading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  tenantCode: '',
  username: 'admin',
  password: ''
})

const rules: FormRules = {
  tenantCode: [{ required: true, message: '请选择企业', trigger: 'change' }],
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(async () => {
  try {
    tenants.value = await fetchLoginTenants()
    if (tenants.value.length > 0 && !form.tenantCode) {
      form.tenantCode = tenants.value[0].code
    }
  } catch {
    // 全新部署还没有任何租户，属于正常情况，不打扰用户
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
      if (mode.value === 'platform') {
        await store.platformLogin({ username: form.username, password: form.password })
      } else {
        await store.login({
          tenantCode: form.tenantCode,
          username: form.username,
          password: form.password
        })
      }
      ElMessage.success('登录成功')
      const redirect = (route.query.redirect as string) || '/dashboard'
      await router.push(redirect)
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #e6a23c;
  line-height: 1.5;
}
</style>
