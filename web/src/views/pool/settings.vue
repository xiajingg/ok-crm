<template>
  <div class="page-container">
    <el-card v-loading="loading" shadow="never" class="page-card" style="max-width: 720px">
      <template #header>
        <div class="card-header">
          <span>公海池回收规则</span>
          <el-tag type="info" size="small">按企业独立配置</el-tag>
        </div>
      </template>

      <el-alert
        type="warning"
        :closable="false"
        style="margin-bottom: 16px"
        title="不同企业「多久没跟进算超期」的标准差异很大，所以这里做成可配而不是写死。规则保存后立即对下一次回收生效。"
      />

      <el-form :model="form" label-width="140px">
        <el-form-item label="启用自动回收">
          <el-switch v-model="form.enabled" />
          <span class="hint">关闭后只能手动退回公海</span>
        </el-form-item>

        <el-form-item label="超期天数">
          <el-input-number v-model="form.days" :min="1" :max="3650" :disabled="!form.enabled" />
          <span class="hint">超过该天数未按口径跟进，客户自动回到公海</span>
        </el-form-item>

        <el-form-item label="时间口径">
          <el-radio-group v-model="form.basis" :disabled="!form.enabled">
            <el-radio value="LAST_FOLLOWUP">按最后跟进时间</el-radio>
            <el-radio value="CREATE_TIME">按客户创建时间</el-radio>
          </el-radio-group>
          <div class="hint block">
            按最后跟进时间：从未跟进的客户以创建时间兜底，避免永远回收不掉。
          </div>
        </el-form-item>

        <el-form-item label="单人持有上限">
          <el-input-number v-model="form.claimLimit" :min="0" :max="100000" />
          <span class="hint">0 表示不限制；设置后员工领取时会校验</span>
        </el-form-item>

        <el-form-item>
          <el-button
            v-permission="'pool:settings:update'"
            type="primary"
            :loading="submitting"
            @click="save"
          >
            保存规则
          </el-button>
          <el-button
            v-permission="'pool:settings:update'"
            :loading="running"
            @click="runNow"
          >
            立即执行一次回收
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        title="生产环境该任务默认每天凌晨 2 点自动执行；多实例部署时会用分布式锁保证同一时刻只有一个实例在跑。"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getPoolSettings, runRecycleNow, updatePoolSettings, type PoolSettings } from '@/api/pool'

const loading = ref(false)
const submitting = ref(false)
const running = ref(false)

const form = reactive<PoolSettings>({
  enabled: false,
  days: 30,
  basis: 'LAST_FOLLOWUP',
  claimLimit: 0
})

async function load() {
  loading.value = true
  try {
    Object.assign(form, await getPoolSettings())
  } finally {
    loading.value = false
  }
}

async function save() {
  submitting.value = true
  try {
    await updatePoolSettings({ ...form })
    ElMessage.success('规则已保存')
    await load()
  } finally {
    submitting.value = false
  }
}

async function runNow() {
  running.value = true
  try {
    const count = await runRecycleNow()
    ElMessage.success(`本次回收了 ${count} 个客户`)
  } finally {
    running.value = false
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

.hint {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.hint.block {
  display: block;
  margin-left: 0;
  margin-top: 4px;
  line-height: 1.6;
}
</style>
