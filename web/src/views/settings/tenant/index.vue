<template>
  <div class="page-container">
    <el-card v-loading="loading" shadow="never" class="page-card" style="max-width: 760px">
      <template #header>
        <div class="card-header">
          <span>企业信息</span>
          <el-button
            v-permission="'tenant:config:update'"
            type="primary"
            size="small"
            :loading="submitting"
            @click="saveTenant"
          >
            保存
          </el-button>
        </div>
      </template>

      <el-form :model="tenantForm" label-width="110px">
        <el-form-item label="企业编码">
          <el-input :model-value="tenant?.code" disabled />
          <div class="field-hint">企业编码不可修改</div>
        </el-form-item>
        <el-form-item label="企业名称">
          <el-input v-model="tenantForm.name" placeholder="会显示在管理后台左上角与登录页" />
        </el-form-item>
        <el-form-item label="地区编码">
          <el-input v-model="tenantForm.region" placeholder="如 CN-HUBEI" />
        </el-form-item>
        <el-form-item label="地区名称">
          <el-input v-model="tenantForm.regionName" placeholder="如 湖北" />
        </el-form-item>
        <el-form-item label="时区">
          <el-input v-model="tenantForm.timezone" placeholder="Asia/Shanghai" />
        </el-form-item>
        <el-form-item label="币种">
          <el-input v-model="tenantForm.currency" placeholder="CNY" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="tenantForm.contactName" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="tenantForm.contactPhone" />
        </el-form-item>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        title="初始企业信息由 okcrm.setup.* 配置在首次启动时写入；这里改的是数据库，即时生效、不用重启。"
      />
    </el-card>

    <el-card v-loading="loading" shadow="never" class="page-card" style="max-width: 760px">
      <template #header>
        <div class="card-header">
          <span>租户配置</span>
          <el-button
            v-permission="'tenant:config:update'"
            type="primary"
            size="small"
            :loading="submitting"
            @click="saveConfig"
          >
            保存配置
          </el-button>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 16px"
        title="这些配置按企业独立存储，改完立即生效，不影响其它企业。"
      />

      <el-form label-width="180px">
        <el-form-item v-for="item in configItems" :key="item.key" :label="item.label">
          <el-input v-model="configs[item.key]" :placeholder="item.hint" />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchCurrentConfig,
  fetchCurrentTenant,
  updateCurrentConfig,
  updateCurrentTenant,
  type TenantForm,
  type TenantRow
} from '@/api/tenant'

const loading = ref(false)
const submitting = ref(false)
const tenant = ref<TenantRow | null>(null)
const tenantForm = reactive<TenantForm>({})
const configs = reactive<Record<string, string>>({})

/**
 * 目前只有公海池回收相关的配置项。
 * 新增配置项时，后端在 TenantConfigKeys 里定义 key，这里补一行展示即可。
 */
const configItems = [
  { key: 'pool.recycle.enabled', label: '公海自动回收开关', hint: 'true / false' },
  { key: 'pool.recycle.days', label: '超期天数', hint: '默认 30' },
  {
    key: 'pool.recycle.basis',
    label: '回收时间口径',
    hint: 'LAST_FOLLOWUP（按最后跟进）/ CREATE_TIME（按创建时间）'
  },
  { key: 'pool.claim.limit', label: '单人持有上限', hint: '0 表示不限制' }
]

async function load() {
  loading.value = true
  try {
    tenant.value = await fetchCurrentTenant()
    const t = tenant.value
    Object.assign(tenantForm, {
      name: t.name,
      region: t.region,
      regionName: t.regionName,
      timezone: t.timezone,
      currency: t.currency,
      contactName: t.contactName,
      contactPhone: t.contactPhone
    })

    const current = await fetchCurrentConfig()
    for (const item of configItems) {
      configs[item.key] = current[item.key] ?? ''
    }
  } finally {
    loading.value = false
  }
}

async function saveTenant() {
  submitting.value = true
  try {
    tenant.value = await updateCurrentTenant({ ...tenantForm })
    ElMessage.success('企业信息已保存')
  } finally {
    submitting.value = false
  }
}

async function saveConfig() {
  submitting.value = true
  try {
    await updateCurrentConfig({ ...configs })
    ElMessage.success('配置已保存')
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
</style>
