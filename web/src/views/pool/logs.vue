<template>
  <div class="page-container">
    <el-card shadow="never" class="page-card">
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 12px"
        title="每一次客户归属变化都会留痕：领取、指派、转移、退回、系统自动回收。纠纷时可以用它追溯「这个客户是谁、什么时候进的公海」。"
      />

      <div class="table-toolbar">
        <el-input
          v-model="query.customerId"
          placeholder="按客户 ID 过滤（可留空）"
          clearable
          style="width: 220px"
          @keyup.enter="load"
        />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="() => { query.customerId = ''; query.pageNum = 1; load() }">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="customerName" label="客户" min-width="160" show-overflow-tooltip />
        <el-table-column label="动作" width="120">
          <template #default="{ row }">
            <el-tag :type="actionTagType(row.action)" size="small">{{ row.actionLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变更前负责人" width="130">
          <template #default="{ row }">{{ row.fromOwnerName || '公海池' }}</template>
        </el-table-column>
        <el-table-column label="变更后负责人" width="130">
          <template #default="{ row }">{{ row.toOwnerName || '公海池' }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="operatorName" label="操作人" width="110" />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { pagePoolLogs, type PoolLogRow } from '@/api/pool'

const loading = ref(false)
const rows = ref<PoolLogRow[]>([])
const total = ref(0)
const query = reactive({ customerId: '', pageNum: 1, pageSize: 20 })

async function load() {
  loading.value = true
  try {
    const page = await pagePoolLogs({
      customerId: query.customerId ? Number(query.customerId) : undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function actionTagType(action: string) {
  switch (action) {
    case 'CLAIM':
      return 'success'
    case 'ASSIGN':
      return 'primary'
    case 'TRANSFER':
      return 'warning'
    case 'RECYCLE':
      return 'danger'
    default:
      return 'info'
  }
}

onMounted(load)
</script>
