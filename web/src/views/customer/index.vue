<template>
  <div class="page-container">
    <el-card shadow="never" class="page-card">
      <div class="table-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="客户名称 / 电话 / 行业"
          clearable
          style="width: 240px"
          @keyup.enter="load"
        />
        <el-select v-model="query.level" placeholder="客户级别" clearable style="width: 120px">
          <el-option label="A 类" value="A" />
          <el-option label="B 类" value="B" />
          <el-option label="C 类" value="C" />
        </el-select>
        <el-select
          v-model="query.ownerId"
          placeholder="负责人"
          clearable
          filterable
          style="width: 180px"
        >
          <el-option
            v-for="e in employees"
            :key="e.id"
            :label="e.realName || e.username"
            :value="e.id"
          />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>

        <div class="spacer" />

        <el-button v-permission="'customer:create'" type="primary" @click="openCreate">
          新增客户
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="name" label="客户名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="level" label="级别" width="70" align="center" />
        <el-table-column prop="industry" label="行业" width="110" show-overflow-tooltip />
        <el-table-column prop="phone" label="电话" width="140" />
        <el-table-column label="负责人" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.inPool" type="warning" size="small">公海</el-tag>
            <span v-else>{{ row.ownerName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最后跟进" width="160">
          <template #default="{ row }">{{ row.lastFollowUpAt || '从未跟进' }}</template>
        </el-table-column>
        <el-table-column prop="contactCount" label="联系人" width="80" align="center" />
        <el-table-column label="操作" :width="isMobile ? 180 : 260" :fixed="isMobile ? false : 'right'">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-permission="'customer:update'" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button
              v-permission="'customer:transfer'"
              link
              type="primary"
              @click="openTransfer(row)"
            >
              转移
            </el-button>
            <el-button
              v-if="!row.inPool"
              v-permission="'customer:transfer'"
              link
              type="warning"
              @click="release(row)"
            >
              退回公海
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

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑客户' : '新增客户'" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="客户名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入客户名称" />
        </el-form-item>
        <el-form-item label="客户级别">
          <el-radio-group v-model="form.level">
            <el-radio value="A">A</el-radio>
            <el-radio value="B">B</el-radio>
            <el-radio value="C">C</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="行业">
          <el-input v-model="form.industry" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="form.source" placeholder="如：转介绍 / 展会 / 广告" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="负责人">
          <el-select v-model="form.ownerId" placeholder="留空则进入公海池" clearable filterable style="width: 100%">
            <el-option
              v-for="e in employees"
              :key="e.id"
              :label="e.realName || e.username"
              :value="e.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="多个标签用逗号分隔" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 转移归属 -->
    <el-dialog v-model="transferVisible" title="转移客户归属" width="460px">
      <el-alert
        type="info"
        :closable="false"
        title="每次归属变更都会写入公海池流转日志，可追溯"
        style="margin-bottom: 12px"
      />
      <el-form label-width="90px">
        <el-form-item label="客户">
          <span>{{ transferTarget?.name }}</span>
        </el-form-item>
        <el-form-item label="当前负责人">
          <span>{{ transferTarget?.ownerName || '公海池' }}</span>
        </el-form-item>
        <el-form-item label="新负责人">
          <el-select v-model="transferOwnerId" placeholder="请选择" filterable style="width: 100%">
            <el-option
              v-for="e in employees"
              :key="e.id"
              :label="e.realName || e.username"
              :value="e.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitTransfer">确认转移</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉：基本信息 + 联系人 + 跟进记录 -->
    <el-drawer v-model="detailVisible" :title="detail?.name || '客户详情'" size="640px">
      <el-descriptions v-if="detail" :column="isMobile ? 1 : 2" border size="small">
        <el-descriptions-item label="级别">{{ detail.level || '-' }}</el-descriptions-item>
        <el-descriptions-item label="行业">{{ detail.industry || '-' }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ detail.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ detail.source || '-' }}</el-descriptions-item>
        <el-descriptions-item label="负责人">
          {{ detail.inPool ? '公海池' : detail.ownerName }}
        </el-descriptions-item>
        <el-descriptions-item label="最后跟进">
          {{ detail.lastFollowUpAt || '从未跟进' }}
        </el-descriptions-item>
        <el-descriptions-item label="标签" :span="2">{{ detail.tags || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="detailTab" style="margin-top: 16px">
        <el-tab-pane label="联系人" name="contact">
          <div class="table-toolbar">
            <el-button
              v-permission="'customer:update'"
              size="small"
              type="primary"
              @click="openContactForm()"
            >
              新增联系人
            </el-button>
          </div>
          <el-table :data="contacts" size="small" border>
            <el-table-column prop="name" label="姓名" width="100" />
            <el-table-column prop="position" label="职务" width="110" />
            <el-table-column prop="phone" label="电话" width="130" />
            <el-table-column label="主联系人" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.primaryContact" type="success" size="small">是</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button v-permission="'customer:update'" link type="danger" @click="removeContact(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="跟进记录" name="followup">
          <el-form :model="followUpForm" label-width="80px" size="small">
            <el-form-item label="方式">
              <el-select v-model="followUpForm.type" style="width: 160px">
                <el-option label="电话" value="PHONE" />
                <el-option label="拜访" value="VISIT" />
                <el-option label="微信" value="WECHAT" />
                <el-option label="邮件" value="EMAIL" />
                <el-option label="其它" value="OTHER" />
              </el-select>
            </el-form-item>
            <el-form-item label="内容">
              <el-input v-model="followUpForm.content" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item>
              <el-button
                v-permission="'customer:followup'"
                type="primary"
                size="small"
                :loading="submitting"
                @click="submitFollowUp"
              >
                提交跟进
              </el-button>
              <span class="followup-hint">
                提交后会刷新客户的「最后跟进时间」，影响公海池回收判定
              </span>
            </el-form-item>
          </el-form>

          <el-timeline style="margin-top: 8px">
            <el-timeline-item
              v-for="item in followUps"
              :key="item.id"
              :timestamp="item.followedAt"
              placement="top"
            >
              <div class="followup-item">
                <b>{{ item.employeeName || '系统' }}</b>
                <el-tag size="small" effect="plain">{{ followUpTypeLabel(item.type) }}</el-tag>
                <div class="followup-content">{{ item.content }}</div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="followUps.length === 0" description="还没有跟进记录" :image-size="70" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createContact,
  createCustomer,
  createFollowUp,
  deleteContact,
  getCustomer,
  listContacts,
  pageCustomers,
  pageFollowUps,
  releaseCustomer,
  transferCustomer,
  updateCustomer,
  type ContactRow,
  type CustomerForm,
  type CustomerRow,
  type FollowUpRow
} from '@/api/customer'
import { listEmployeeOptions, type EmployeeOption } from '@/api/employee'
import { useIsMobile } from '@/composables/useIsMobile'
import type { Id } from '@/types'

// 手机上取消「操作」列固定：固定列会占掉大半屏宽，剩下的内容几乎看不见
const isMobile = useIsMobile()

const loading = ref(false)
const submitting = ref(false)
const rows = ref<CustomerRow[]>([])
const total = ref(0)
const employees = ref<EmployeeOption[]>([])

const query = reactive({
  keyword: '',
  level: '',
  ownerId: undefined as Id | undefined,
  pageNum: 1,
  pageSize: 10
})

// ---------------- 列表 ----------------

async function load() {
  loading.value = true
  try {
    const page = await pageCustomers({
      keyword: query.keyword || undefined,
      level: query.level || undefined,
      ownerId: query.ownerId,
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
  query.level = ''
  query.ownerId = undefined
  query.pageNum = 1
  load()
}

// ---------------- 新增 / 编辑 ----------------

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<CustomerForm & { id?: Id }>({})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入客户名称', trigger: 'blur' }]
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    name: '',
    level: 'B',
    industry: '',
    phone: '',
    source: '',
    ownerId: undefined,
    tags: '',
    remark: ''
  })
  formVisible.value = true
}

function openEdit(row: CustomerRow) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    level: row.level,
    industry: row.industry,
    phone: row.phone,
    source: row.source,
    ownerId: undefined,
    tags: row.tags,
    remark: row.remark
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
        await updateCustomer(form.id, form)
      } else {
        await createCustomer(form)
      }
      ElMessage.success('保存成功')
      formVisible.value = false
      await load()
    } finally {
      submitting.value = false
    }
  })
}

// ---------------- 转移 / 退回 ----------------

const transferVisible = ref(false)
const transferTarget = ref<CustomerRow | null>(null)
const transferOwnerId = ref<Id | undefined>()

function openTransfer(row: CustomerRow) {
  transferTarget.value = row
  transferOwnerId.value = undefined
  transferVisible.value = true
}

async function submitTransfer() {
  if (!transferTarget.value || !transferOwnerId.value) {
    ElMessage.warning('请选择新负责人')
    return
  }
  submitting.value = true
  try {
    await transferCustomer(transferTarget.value.id, transferOwnerId.value, '后台转移')
    ElMessage.success('已转移')
    transferVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

async function release(row: CustomerRow) {
  const { value } = await ElMessageBox.prompt('请输入退回原因', '退回公海池', {
    inputValue: '手动退回',
    inputValidator: (v) => (v && v.trim() ? true : '原因不能为空')
  })
  await releaseCustomer(row.id, value)
  ElMessage.success('已退回公海池')
  await load()
}

// ---------------- 详情 ----------------

const detailVisible = ref(false)
const detailTab = ref('contact')
const detail = ref<CustomerRow | null>(null)
const contacts = ref<ContactRow[]>([])
const followUps = ref<FollowUpRow[]>([])
const followUpForm = reactive({ type: 'PHONE', content: '' })

async function openDetail(row: CustomerRow) {
  detail.value = await getCustomer(row.id)
  detailVisible.value = true
  detailTab.value = 'contact'
  await Promise.all([loadContacts(row.id), loadFollowUps(row.id)])
}

async function loadContacts(customerId: Id) {
  contacts.value = await listContacts(customerId)
}

async function loadFollowUps(customerId: Id) {
  const page = await pageFollowUps(customerId, { pageNum: 1, pageSize: 20 })
  followUps.value = page.records
}

async function openContactForm() {
  if (!detail.value) {
    return
  }
  const { value } = await ElMessageBox.prompt('请输入联系人姓名', '新增联系人')
  if (!value) {
    return
  }
  await createContact(detail.value.id, { name: value })
  ElMessage.success('已新增')
  await loadContacts(detail.value.id)
}

async function removeContact(row: ContactRow) {
  await ElMessageBox.confirm(`确定删除联系人「${row.name}」吗？`, '提示', { type: 'warning' })
  await deleteContact(row.customerId, row.id)
  ElMessage.success('已删除')
  await loadContacts(row.customerId)
}

async function submitFollowUp() {
  if (!detail.value || !followUpForm.content.trim()) {
    ElMessage.warning('请填写跟进内容')
    return
  }
  submitting.value = true
  try {
    await createFollowUp(detail.value.id, { ...followUpForm })
    followUpForm.content = ''
    ElMessage.success('已记录跟进')
    await loadFollowUps(detail.value.id)
    await load()
  } finally {
    submitting.value = false
  }
}

const FOLLOW_UP_TYPES: Record<string, string> = {
  PHONE: '电话',
  VISIT: '拜访',
  WECHAT: '微信',
  EMAIL: '邮件',
  OTHER: '其它'
}

function followUpTypeLabel(type?: string) {
  return type ? FOLLOW_UP_TYPES[type] || type : '其它'
}

// ---------------- 初始化 ----------------

onMounted(async () => {
  await load()
  try {
    employees.value = await listEmployeeOptions()
  } catch {
    // 没有员工管理权限时下拉为空，不影响列表
  }
})
</script>

<style scoped>
.followup-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.followup-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.followup-content {
  width: 100%;
  margin-top: 4px;
  color: #606266;
  white-space: pre-wrap;
}
</style>
