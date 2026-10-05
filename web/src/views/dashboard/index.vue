<template>
  <div class="page-container">
    <el-card class="page-card" shadow="never">
      <div class="welcome">
        <div>
          <h3 class="welcome-title">你好，{{ store.displayName }}</h3>
          <p class="welcome-sub">
            当前企业：<b>{{ store.tenant?.name || '未设置企业' }}</b>
            <template v-if="store.tenant">
              ｜地区：{{ store.tenant.regionName || store.tenant.region }}
              ｜时区：{{ store.tenant.timezone }}
              ｜币种：{{ store.tenant.currency }}
            </template>
          </p>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col v-if="store.hasModule('customer')" :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">我的客户</div>
          <div class="stat-value">{{ stats.myCustomers }}</div>
          <div class="stat-hint">受数据权限限制，仅统计你可见的客户</div>
        </el-card>
      </el-col>
      <el-col v-if="store.hasModule('pool')" :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">公海池客户</div>
          <div class="stat-value">{{ stats.poolCustomers }}</div>
          <div class="stat-hint">无归属、可被领取的客户</div>
        </el-card>
      </el-col>
      <el-col v-if="store.has('iam:employee:list')" :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">员工数</div>
          <div class="stat-value">{{ stats.employees }}</div>
          <div class="stat-hint">本企业全部员工</div>
        </el-card>
      </el-col>
      <el-col v-if="store.has('iam:position:list')" :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">岗位数</div>
          <div class="stat-value">{{ stats.positions }}</div>
          <div class="stat-hint">岗位即角色，承载权限与数据范围</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never" class="page-card">
          <template #header>当前企业已购模块</template>
          <el-empty v-if="store.modules.length === 0" description="暂无模块授权" :image-size="80" />
          <div v-else class="module-list">
            <div v-for="key in store.modules" :key="key" class="module-item">
              <el-tag effect="plain">{{ moduleLabel(key) }}</el-tag>
              <span class="module-desc">{{ moduleDesc(key) }}</span>
            </div>
          </div>
          <el-alert
            v-if="store.modules.length > 0"
            type="info"
            :closable="false"
            style="margin-top: 12px"
            title="菜单是按「已购模块 ∩ 岗位权限」动态下发的：未购买的模块不会出现在左侧菜单里。"
          />
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never" class="page-card">
          <template #header>我的权限</template>
          <div class="perm-wrap">
            <el-tag
              v-for="code in store.permissions"
              :key="code"
              size="small"
              type="info"
              effect="plain"
              class="perm-tag"
            >
              {{ code }}
            </el-tag>
            <el-empty v-if="store.permissions.length === 0" description="暂无权限" :image-size="70" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { pageCustomers } from '@/api/customer'
import { pagePoolCustomers } from '@/api/pool'
import { pageEmployees } from '@/api/employee'
import { listPositions } from '@/api/position'
import { useUserStore } from '@/stores/user'

const store = useUserStore()

const stats = reactive({
  myCustomers: 0,
  poolCustomers: 0,
  employees: 0,
  positions: 0
})

const MODULE_LABELS: Record<string, string> = {
  tenant: '租户与地区',
  iam: '组织与权限',
  customer: '客户管理',
  pool: '公海池'
}

const MODULE_DESCS: Record<string, string> = {
  tenant: '企业开通、地区属性、租户级配置',
  iam: '岗位（即角色）、员工、数据权限',
  customer: '客户档案、联系人、跟进记录、归属分配',
  pool: '公海领取、指派、超期未跟进自动回收'
}

function moduleLabel(key: string) {
  return MODULE_LABELS[key] || key
}

function moduleDesc(key: string) {
  return MODULE_DESCS[key] || ''
}

onMounted(async () => {
  const tasks: Promise<unknown>[] = []

  if (store.hasModule('customer')) {
    tasks.push(pageCustomers({ pageSize: 1 }).then((r) => (stats.myCustomers = r.total)))
  }
  if (store.hasModule('pool')) {
    tasks.push(pagePoolCustomers({ pageSize: 1 }).then((r) => (stats.poolCustomers = r.total)))
  }
  if (store.has('iam:employee:list')) {
    tasks.push(pageEmployees({ pageSize: 1 }).then((r) => (stats.employees = r.total)))
  }
  if (store.has('iam:position:list')) {
    tasks.push(listPositions().then((r) => (stats.positions = r.length)))
  }

  // 用 allSettled：某个模块没买或没权限时，其余统计仍要正常显示
  await Promise.allSettled(tasks)
})
</script>

<style scoped>
.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.welcome-title {
  margin: 0 0 6px;
  font-size: 18px;
}

.welcome-sub {
  margin: 0;
  color: #606266;
  font-size: 13px;
}

.stat-card {
  text-align: center;
}

.stat-label {
  color: #909399;
  font-size: 13px;
}

.stat-value {
  margin: 8px 0 4px;
  font-size: 30px;
  font-weight: 600;
  color: #1f3a5f;
}

.stat-hint {
  color: #c0c4cc;
  font-size: 12px;
}

.module-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.module-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.module-desc {
  color: #606266;
  font-size: 13px;
}

.perm-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 220px;
  overflow-y: auto;
}

.perm-tag {
  font-family: Menlo, Consolas, monospace;
}
</style>
