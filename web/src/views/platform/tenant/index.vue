<template>
  <div class="page-container">
    <el-card shadow="never" class="page-card">
      <el-alert
        type="warning"
        :closable="false"
        style="margin-bottom: 12px"
        title="这是平台超管的跨租户管理界面。开通租户时会自动初始化三个默认岗位（管理员 / 销售主管 / 销售）并分配好权限，同时创建一个默认管理员账号。"
      />

      <div class="table-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="租户编码 / 企业名称"
          clearable
          style="width: 240px"
          @keyup.enter="load"
        />
        <el-button type="primary" @click="load">查询</el-button>
        <div class="spacer" />
        <el-button type="primary" @click="openCreate">开通租户</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="code" label="租户编码" width="130" />
        <el-table-column prop="name" label="企业名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="地区" width="130">
          <template #default="{ row }">{{ row.regionName || row.region }}</template>
        </el-table-column>
        <el-table-column label="时区 / 币种" width="150">
          <template #default="{ row }">{{ row.timezone }} / {{ row.currency }}</template>
        </el-table-column>
        <el-table-column prop="contactName" label="联系人" width="110" />
        <el-table-column prop="contactPhone" label="联系电话" width="140" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openModules(row)">模块授权</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
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

    <!-- 开通 / 编辑租户 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑租户' : '开通租户'" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="租户编码" prop="code">
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 acme（登录时使用）" />
        </el-form-item>
        <el-form-item label="企业名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="地区" prop="region">
          <el-input v-model="form.region" placeholder="如 CN-HUBEI" />
        </el-form-item>
        <el-form-item label="地区名称">
          <el-input v-model="form.regionName" placeholder="如 湖北" />
        </el-form-item>
        <el-form-item label="时区">
          <el-input v-model="form.timezone" placeholder="Asia/Shanghai" />
        </el-form-item>
        <el-form-item label="币种">
          <el-input v-model="form.currency" placeholder="CNY" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" />
        </el-form-item>
        <el-form-item label="服务到期日">
          <el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="开通模块">
          <el-checkbox-group v-model="form.modules">
            <el-checkbox v-for="m in catalog" :key="m.moduleKey" :value="m.moduleKey">
              {{ m.name }}
            </el-checkbox>
          </el-checkbox-group>
          <div class="hint">不勾选则默认开通全部已注册模块</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 模块授权 -->
    <el-drawer v-model="moduleVisible" :title="`模块授权 - ${moduleTarget?.name ?? ''}`" size="620px">
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 12px"
        title="撤销授权后，该租户调用对应模块的接口会得到「当前租户未购买该模块」，前端菜单也会自动消失。"
      />
      <el-table v-loading="moduleLoading" :data="modules" border>
        <el-table-column prop="name" label="模块" width="140" />
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="授权状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.licensed ? 'success' : 'info'" size="small">
              {{ row.licensed ? '已授权' : '未授权' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button v-if="!row.licensed" link type="primary" @click="grant(row)">授权</el-button>
            <el-button v-else link type="danger" @click="revoke(row)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  changeTenantStatus,
  createTenant,
  fetchModuleCatalog,
  fetchTenantModules,
  grantModules,
  pageTenants,
  revokeModule,
  updateTenant,
  type ModuleRow,
  type TenantForm,
  type TenantRow
} from '@/api/tenant'

const loading = ref(false)
const submitting = ref(false)
const rows = ref<TenantRow[]>([])
const total = ref(0)
const catalog = ref<ModuleRow[]>([])

const query = reactive({ keyword: '', pageNum: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try {
    const page = await pageTenants({
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

// ---------------- 开通 / 编辑 ----------------

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<TenantForm & { id?: number }>({})

const formRules: FormRules = {
  code: [{ required: true, message: '请输入租户编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入企业名称', trigger: 'blur' }],
  region: [{ required: true, message: '请输入地区编码', trigger: 'blur' }]
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    code: '',
    name: '',
    region: 'CN-HUBEI',
    regionName: '湖北',
    timezone: 'Asia/Shanghai',
    currency: 'CNY',
    contactName: '',
    contactPhone: '',
    expireDate: undefined,
    modules: []
  })
  formVisible.value = true
}

function openEdit(row: TenantRow) {
  Object.assign(form, {
    id: row.id,
    code: row.code,
    name: row.name,
    region: row.region,
    regionName: row.regionName,
    timezone: row.timezone,
    currency: row.currency,
    contactName: row.contactName,
    contactPhone: row.contactPhone,
    expireDate: row.expireDate
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
      if (form.id) {
        await updateTenant(form.id, form)
      } else {
        await createTenant(form)
        ElMessage.success('开通成功，默认管理员账号为 admin / admin123456')
      }
      formVisible.value = false
      await load()
    } finally {
      submitting.value = false
    }
  })
}

async function toggleStatus(row: TenantRow) {
  const next = row.status !== 1
  await ElMessageBox.confirm(`确定${next ? '启用' : '停用'}「${row.name}」吗？`, '提示', {
    type: 'warning'
  })
  await changeTenantStatus(row.id, next)
  ElMessage.success('已更新')
  await load()
}

// ---------------- 模块授权 ----------------

const moduleVisible = ref(false)
const moduleLoading = ref(false)
const moduleTarget = ref<TenantRow | null>(null)
const modules = ref<ModuleRow[]>([])

async function openModules(row: TenantRow) {
  moduleTarget.value = row
  moduleVisible.value = true
  await loadModules()
}

async function loadModules() {
  if (!moduleTarget.value) {
    return
  }
  moduleLoading.value = true
  try {
    modules.value = await fetchTenantModules(moduleTarget.value.id)
  } finally {
    moduleLoading.value = false
  }
}

async function grant(row: ModuleRow) {
  if (!moduleTarget.value) {
    return
  }
  await grantModules(moduleTarget.value.id, { moduleKeys: [row.moduleKey], remark: '后台授权' })
  ElMessage.success(`已授权「${row.name}」`)
  await loadModules()
}

async function revoke(row: ModuleRow) {
  if (!moduleTarget.value) {
    return
  }
  await ElMessageBox.confirm(
    `确定撤销「${row.name}」模块吗？撤销后该租户将无法使用相关功能。`,
    '提示',
    { type: 'warning' }
  )
  await revokeModule(moduleTarget.value.id, row.moduleKey)
  ElMessage.success('已撤销')
  await loadModules()
}

onMounted(async () => {
  await load()
  try {
    catalog.value = await fetchModuleCatalog()
  } catch {
    // 忽略：价目表加载失败不影响租户列表
  }
})
</script>

<style scoped>
.hint {
  font-size: 12px;
  color: #909399;
}
</style>
