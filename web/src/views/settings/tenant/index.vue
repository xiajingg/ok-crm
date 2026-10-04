<template>
  <div class="page-container">
    <el-card v-loading="loading" shadow="never" class="page-card" style="max-width: 760px">
      <template #header>企业信息</template>
      <el-descriptions v-if="tenant" :column="2" border>
        <el-descriptions-item label="企业名称">{{ tenant.name }}</el-descriptions-item>
        <el-descriptions-item label="租户编码">{{ tenant.code }}</el-descriptions-item>
        <el-descriptions-item label="地区">
          {{ tenant.regionName || tenant.region }}
        </el-descriptions-item>
        <el-descriptions-item label="时区">{{ tenant.timezone }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ tenant.currency }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ tenant.statusLabel }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="平台超管没有所属企业" :image-size="80" />
    </el-card>

    <el-card v-if="tenant" v-loading="loading" shadow="never" class="page-card" style="max-width: 760px">
      <template #header>租户配置</template>
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 16px"
        title="这些配置按企业独立存储，改完立即生效，不影响其它租户。"
      />
      <el-form label-width="180px">
        <el-form-item
          v-for="item in configItems"
          :key="item.key"
          :label="item.label"
        >
          <el-input v-model="configs[item.key]" :placeholder="item.hint" />
        </el-form-item>
        <el-form-item>
          <el-button
            v-permission="'tenant:config:update'"
            type="primary"
            :loading="submitting"
            @click="save"
          >
            保存配置
          </el-button>
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
  type TenantRow
} from '@/api/tenant'

const loading = ref(false)
const submitting = ref(false)
const tenant = ref<TenantRow | null>(null)
const configs = reactive<Record<string, string>>({})

/**
 * 目前只有公海池回收相关的配置项。
 * 新增配置项时，后端在 TenantConfigKeys 里定义 key，这里补一行展示即可。
 */
const configItems = [
  {
    key: 'pool.recycle.enabled',
    label: '公海自动回收开关',
    hint: 'true / false'
  },
  {
    key: 'pool.recycle.days',
    label: '超期天数',
    hint: '默认 30'
  },
  {
    key: 'pool.recycle.basis',
    label: '回收时间口径',
    hint: 'LAST_FOLLOWUP（按最后跟进）/ CREATE_TIME（按创建时间）'
  },
  {
    key: 'pool.claim.limit',
    label: '单人持有上限',
    hint: '0 表示不限制'
  }
]

async function load() {
  loading.value = true
  try {
    try {
      tenant.value = await fetchCurrentTenant()
    } catch {
      tenant.value = null
      return
    }
    const current = await fetchCurrentConfig()
    for (const item of configItems) {
      configs[item.key] = current[item.key] ?? ''
    }
  } finally {
    loading.value = false
  }
}

async function save() {
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
