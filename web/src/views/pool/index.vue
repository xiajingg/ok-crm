<template>
  <div class="page-container">
    <el-alert
      type="info"
      :closable="false"
      class="page-card"
      title="公海池里的客户没有归属人，所有员工都可以领取。领取后会写入流转日志；若超过租户配置的天数未跟进，会被系统自动回收。"
    />

    <el-card shadow="never" class="page-card">
      <div class="table-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="客户名称 / 电话"
          clearable
          style="width: 240px"
          @keyup.enter="load"
        />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="() => { query.keyword = ''; load() }">重置</el-button>

        <div class="spacer" />

        <el-tag type="info">共 {{ total }} 个公海客户</el-tag>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="name" label="客户名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="level" label="级别" width="70" align="center" />
        <el-table-column prop="industry" label="行业" width="120" show-overflow-tooltip />
        <el-table-column label="进入公海时间" width="170">
          <template #default="{ row }">{{ row.enterPoolAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="最后跟进" width="170">
          <template #default="{ row }">{{ row.lastFollowUpAt || '从未跟进' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" :fixed="isMobile ? false : 'right'">
          <template #default="{ row }">
            <el-button
              v-permission="'pool:claim'"
              link
              type="primary"
              @click="claim(row)"
            >
              领取
            </el-button>
            <el-button
              v-permission="'pool:assign'"
              link
              type="primary"
              @click="openAssign(row)"
            >
              指派
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @change="load"
        />
      </div>
    </el-card>

    <el-dialog v-model="assignVisible" title="指派客户" width="460px">
      <el-form label-width="90px">
        <el-form-item label="客户">
          <span>{{ assignTarget?.name }}</span>
        </el-form-item>
        <el-form-item label="指派给">
          <el-select v-model="assignEmployeeId" placeholder="请选择员工" filterable style="width: 100%">
            <el-option
              v-for="e in employees"
              :key="e.id"
              :label="e.realName || e.username"
              :value="e.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="assignRemark" placeholder="如：华东区重点客户" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAssign">确认指派</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  assignCustomer,
  claimCustomer,
  pagePoolCustomers,
  type PoolCustomerRow
} from '@/api/pool'
import { listEmployeeOptions, type EmployeeOption } from '@/api/employee'
import type { Id } from '@/types'
import { useIsMobile } from '@/composables/useIsMobile'

// 手机上取消「操作」列固定：固定列会占掉大半屏宽，剩下的内容几乎看不见
const isMobile = useIsMobile()

const loading = ref(false)
const submitting = ref(false)
const rows = ref<PoolCustomerRow[]>([])
const total = ref(0)
const employees = ref<EmployeeOption[]>([])

const query = reactive({ keyword: '', pageNum: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try {
    const page = await pagePoolCustomers({
      keyword: query.keyword || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function claim(row: PoolCustomerRow) {
  await ElMessageBox.confirm(`确定领取客户「${row.name}」吗？`, '领取客户', { type: 'info' })
  await claimCustomer(row.id)
  ElMessage.success('领取成功，已归入你的客户列表')
  await load()
}

const assignVisible = ref(false)
const assignTarget = ref<PoolCustomerRow | null>(null)
const assignEmployeeId = ref<Id | undefined>()
const assignRemark = ref('')

function openAssign(row: PoolCustomerRow) {
  assignTarget.value = row
  assignEmployeeId.value = undefined
  assignRemark.value = ''
  assignVisible.value = true
}

async function submitAssign() {
  if (!assignTarget.value || !assignEmployeeId.value) {
    ElMessage.warning('请选择员工')
    return
  }
  submitting.value = true
  try {
    await assignCustomer(assignTarget.value.id, assignEmployeeId.value, assignRemark.value)
    ElMessage.success('指派成功')
    assignVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await load()
  try {
    employees.value = await listEmployeeOptions()
  } catch {
    // 无员工列表权限时指派下拉为空
  }
})
</script>
