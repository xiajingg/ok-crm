<template>
  <div class="page-container">
    <el-card shadow="never" class="page-card">
      <div class="table-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="账号 / 姓名 / 手机号"
          clearable
          style="width: 220px"
          @keyup.enter="load"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer" />
        <el-button v-permission="'iam:employee:create'" type="primary" @click="openCreate">
          新增员工
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="username" label="登录账号" width="140" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column label="岗位" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="name in row.positionNames" :key="name" size="small" style="margin-right: 6px">
              {{ name }}
            </el-tag>
            <span v-if="row.positionNames.length === 0" class="muted">未分配岗位</span>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="170">
          <template #default="{ row }">{{ row.lastLoginAt || '从未登录' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'iam:employee:update'" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-permission="'iam:employee:update'" link type="primary" @click="resetPassword(row)">
              重置密码
            </el-button>
            <el-button
              v-permission="'iam:employee:update'"
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-permission="'iam:employee:delete'" link type="danger" @click="remove(row)">
              删除
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

    <el-dialog v-model="formVisible" :title="form.id ? '编辑员工' : '新增员工'" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="登录账号" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="租户内唯一" />
        </el-form-item>
        <el-form-item :label="form.id ? '重置密码' : '初始密码'" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="form.id ? '留空表示不修改' : '至少 6 位'"
          />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="关联岗位">
          <el-select
            v-model="form.positionIds"
            multiple
            filterable
            placeholder="可兼任多个岗位"
            style="width: 100%"
          >
            <el-option v-for="p in positions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <div class="hint">
            权限取所有岗位的并集，数据范围取最宽的一档。
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            :model-value="form.status === 1"
            @update:model-value="(v: any) => (form.status = v ? 1 : 0)"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  changeEmployeeStatus,
  createEmployee,
  deleteEmployee,
  pageEmployees,
  resetEmployeePassword,
  updateEmployee,
  type EmployeeForm,
  type EmployeeRow
} from '@/api/employee'
import { listPositions, type PositionRow } from '@/api/position'

const loading = ref(false)
const submitting = ref(false)
const rows = ref<EmployeeRow[]>([])
const total = ref(0)
const positions = ref<PositionRow[]>([])

const query = reactive({
  keyword: '',
  status: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10
})

async function load() {
  loading.value = true
  try {
    const page = await pageEmployees({
      keyword: query.keyword || undefined,
      status: query.status,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function reset() {
  query.keyword = ''
  query.status = undefined
  query.pageNum = 1
  load()
}

// ---------------- 表单 ----------------

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<EmployeeForm & { id?: number }>({})

const formRules: FormRules = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [
    {
      validator: (_rule, value, callback) => {
        if (!form.id && !value) {
          callback(new Error('请输入初始密码'))
        } else if (value && value.length < 6) {
          callback(new Error('密码至少 6 位'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    username: '',
    password: '',
    realName: '',
    phone: '',
    email: '',
    status: 1,
    remark: '',
    positionIds: []
  })
  formVisible.value = true
}

function openEdit(row: EmployeeRow) {
  Object.assign(form, {
    id: row.id,
    username: row.username,
    password: '',
    realName: row.realName,
    phone: row.phone,
    email: row.email,
    status: row.status,
    remark: row.remark,
    positionIds: [...row.positionIds]
  })
  formVisible.value = true
}

async function submitForm() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate(async (valid) => {
    if (!valid) {
      return
    }
    submitting.value = true
    try {
      const payload: EmployeeForm = { ...form }
      if (!payload.password) {
        delete payload.password
      }
      if (form.id) {
        await updateEmployee(form.id, payload)
      } else {
        await createEmployee(payload)
      }
      ElMessage.success('保存成功')
      formVisible.value = false
      await load()
    } finally {
      submitting.value = false
    }
  })
}

async function resetPassword(row: EmployeeRow) {
  const { value } = await ElMessageBox.prompt(
    `为「${row.realName || row.username}」设置新密码`,
    '重置密码',
    { inputType: 'password', inputValidator: (v) => (v && v.length >= 6 ? true : '密码至少 6 位') }
  )
  await resetEmployeePassword(row.id, value)
  ElMessage.success('密码已重置')
}

async function toggleStatus(row: EmployeeRow) {
  const next = row.status !== 1
  await ElMessageBox.confirm(
    `确定${next ? '启用' : '停用'}「${row.realName || row.username}」吗？`,
    '提示',
    { type: 'warning' }
  )
  await changeEmployeeStatus(row.id, next)
  ElMessage.success('已更新')
  await load()
}

async function remove(row: EmployeeRow) {
  await ElMessageBox.confirm(`确定删除员工「${row.realName || row.username}」吗？`, '提示', {
    type: 'warning'
  })
  await deleteEmployee(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  await load()
  try {
    positions.value = await listPositions()
  } catch {
    // 无岗位列表权限时下拉为空
  }
})
</script>

<style scoped>
.muted {
  color: #c0c4cc;
}

.hint {
  font-size: 12px;
  color: #909399;
}
</style>
