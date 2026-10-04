<template>
  <div class="page-container">
    <el-card shadow="never" class="page-card">
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 12px"
        title="岗位即角色：岗位同时承载「职务」和「权限」。员工可以兼任多个岗位，最终权限是各岗位权限的并集，数据范围取最宽的一档。"
      />

      <div class="table-toolbar">
        <el-input
          v-model="keyword"
          placeholder="岗位名称 / 编码"
          clearable
          style="width: 220px"
          @keyup.enter="load"
        />
        <el-button type="primary" @click="load">查询</el-button>
        <div class="spacer" />
        <el-button v-permission="'iam:position:create'" type="primary" @click="openCreate">
          新增岗位
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="name" label="岗位名称" width="140" />
        <el-table-column prop="code" label="编码" width="150" />
        <el-table-column label="数据范围" width="130">
          <template #default="{ row }">
            <el-tag :type="row.dataScope === 'ALL' ? 'success' : 'warning'" size="small">
              {{ row.dataScopeLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限数" width="90" align="center">
          <template #default="{ row }">{{ row.permissions.length }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'iam:position:update'" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-permission="'iam:position:delete'" link type="danger" @click="remove(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑岗位' : '新增岗位'" width="640px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="岗位编码" prop="code">
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 sales / sales_manager" />
        </el-form-item>
        <el-form-item label="岗位名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-radio-group v-model="form.dataScope">
            <el-radio value="ALL">全部数据（可看所有客户）</el-radio>
            <el-radio value="SELF">仅本人数据（只看自己负责的客户）</el-radio>
          </el-radio-group>
          <div class="hint">公海池客户不受数据范围限制，否则公海对谁都不可见。</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            :model-value="form.status === 1"
            @update:model-value="(v: any) => (form.status = v ? 1 : 0)"
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.remark" />
        </el-form-item>
        <el-form-item label="权限">
          <div class="perm-tree-wrap">
            <el-tree
              ref="treeRef"
              :data="tree"
              node-key="key"
              show-checkbox
              default-expand-all
              :props="{ label: 'label', children: 'children' }"
            >
              <template #default="{ data }">
                <span class="tree-node">
                  <span>{{ data.label }}</span>
                  <el-tag v-if="data.type === 'BUTTON'" size="small" type="info" effect="plain">
                    按钮
                  </el-tag>
                  <span v-if="data.key" class="tree-code">{{ data.key }}</span>
                </span>
              </template>
            </el-tree>
          </div>
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
  createPosition,
  deletePosition,
  fetchPermissionCatalog,
  listPositions,
  updatePosition,
  type PermissionNode,
  type PositionForm,
  type PositionRow
} from '@/api/position'

interface TreeNode {
  key: string
  label: string
  type?: string
  children?: TreeNode[]
}

const loading = ref(false)
const submitting = ref(false)
const keyword = ref('')
const rows = ref<PositionRow[]>([])
const catalog = ref<PermissionNode[]>([])
const tree = ref<TreeNode[]>([])
const treeRef = ref()

const MODULE_LABELS: Record<string, string> = {
  tenant: '租户与地区',
  iam: '组织与权限',
  customer: '客户管理',
  pool: '公海池'
}

/** 把扁平权限点组装成「模块 → 菜单 → 按钮」三层树 */
function buildTree(list: PermissionNode[]): TreeNode[] {
  const byParent = new Map<string, PermissionNode[]>()
  for (const item of list) {
    const parent = item.parentCode || '0'
    const bucket = byParent.get(parent) ?? []
    bucket.push(item)
    byParent.set(parent, bucket)
  }

  const build = (parent: string): TreeNode[] =>
    (byParent.get(parent) ?? []).map((item) => {
      const children = build(item.code)
      return {
        key: item.code,
        label: item.name,
        type: item.type,
        children: children.length > 0 ? children : undefined
      }
    })

  // 顶层再按模块分组，方便按「买了哪些模块」逐块勾选
  return Object.entries(MODULE_LABELS).map(([moduleKey, label]) => ({
    key: `__module__${moduleKey}`,
    label: `${label}（${moduleKey}）`,
    children: build('0').filter((node) => belongsToModule(node.key, moduleKey))
  })).filter((node) => (node.children?.length ?? 0) > 0)
}

function belongsToModule(code: string, moduleKey: string) {
  return catalog.value.some((p) => p.code === code && p.moduleKey === moduleKey)
}

async function load() {
  loading.value = true
  try {
    rows.value = await listPositions(keyword.value || undefined)
  } finally {
    loading.value = false
  }
}

// ---------------- 表单 ----------------

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<PositionForm & { id?: number }>({})

const formRules: FormRules = {
  code: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }]
}

function openCreate() {
  Object.assign(form, { id: undefined, code: '', name: '', dataScope: 'SELF', status: 1, remark: '' })
  formVisible.value = true
  treeRef.value?.setCheckedKeys([])
}

function openEdit(row: PositionRow) {
  Object.assign(form, {
    id: row.id,
    code: row.code,
    name: row.name,
    dataScope: row.dataScope,
    status: row.status,
    remark: row.remark
  })
  formVisible.value = true
  // 只回填叶子节点，父节点由 el-tree 自动推导为半选
  const leafKeys = row.permissions.filter((code) => {
    const node = catalog.value.find((p) => p.code === code)
    if (!node) return false
    return !catalog.value.some((p) => p.parentCode === code)
  })
  treeRef.value?.setCheckedKeys(leafKeys)
}

async function submitForm() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate(async (valid) => {
    if (!valid) {
      return
    }
    // 全选 + 半选都要提交：半选的父菜单不带上的话，前端菜单树会缺父节点
    const checked = treeRef.value?.getCheckedKeys() ?? []
    const halfChecked = treeRef.value?.getHalfCheckedKeys() ?? []
    const permissions = [...checked, ...halfChecked].filter(
      (key: string) => !key.startsWith('__module__')
    )

    submitting.value = true
    try {
      const payload = { ...form, permissions }
      if (form.id) {
        await updatePosition(form.id, payload)
      } else {
        await createPosition(payload)
      }
      ElMessage.success('保存成功')
      formVisible.value = false
      await load()
    } finally {
      submitting.value = false
    }
  })
}

async function remove(row: PositionRow) {
  await ElMessageBox.confirm(
    `确定删除岗位「${row.name}」吗？已被员工引用的岗位无法删除。`,
    '提示',
    { type: 'warning' }
  )
  await deletePosition(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  catalog.value = await fetchPermissionCatalog()
  tree.value = buildTree(catalog.value)
  await load()
})
</script>

<style scoped>
.perm-tree-wrap {
  width: 100%;
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tree-code {
  color: #c0c4cc;
  font-size: 12px;
  font-family: Menlo, Consolas, monospace;
}

.hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
