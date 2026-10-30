<template>
  <div class="page">
    <div class="page-heading">
      <div><h1>供应商管理</h1><p>维护供应商资料与合作状态。</p></div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增供应商</el-button>
    </div>

    <el-card class="panel-card filter-card">
      <el-form inline>
        <el-form-item>
          <el-input v-model="keyword" clearable placeholder="搜索供应商名称、联系人或电话" :prefix-icon="Search" style="width: 320px" />
        </el-form-item>
        <el-form-item v-if="isAdmin">
          <el-checkbox v-model="showInactive" @change="load">显示已停用</el-checkbox>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="panel-card">
      <el-table v-loading="loading" :data="displayRows" stripe>
        <el-table-column prop="name" label="供应商名称" min-width="220" />
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="contact" label="联系人" width="120" />
        <el-table-column prop="phone" label="联系电话" width="155" />
        <el-table-column prop="address" label="地址" min-width="190" />
        <el-table-column prop="active" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.active === 1 ? 'success' : 'info'" size="small">{{ row.active === 1 ? '正常' : '已停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.active === 1">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="isAdmin" link type="danger" @click="remove(row)">停用</el-button>
            </template>
            <el-button v-else-if="isAdmin" link type="success" @click="restore(row)">恢复</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无供应商" /></template>
      </el-table>
    </el-card>
  </div>

  <el-dialog v-model="dialogVisible" :title="editing ? '编辑供应商' : '新增供应商'" width="520px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="供应商名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="编码"><el-input v-model="form.code" placeholder="留空则不填" /></el-form-item>
      <el-form-item label="联系人" prop="contact"><el-input v-model="form.contact" /></el-form-item>
      <el-form-item label="联系电话" prop="phone"><el-input v-model="form.phone" /></el-form-item>
      <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import { Plus, Search } from '@element-plus/icons-vue'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const inactiveRows = ref([])
const keyword = ref('')
const showInactive = ref(false)
const dialogVisible = ref(false)
const editing = ref(false)
const formRef = ref()

/**
 * 状态字段是 {@code active}（1/0），不是 {@code status}（ACTIVE/INACTIVE）；
 * 表里也没有 {@code creditLevel} —— 改造前表格按不存在的字段渲染，
 * 状态列一直是"未知"，信用等级列显示成孤零零一个"级"字。
 * 现在按后端真实字段来，没有的数据就不做假列。
 */
const form = reactive({ id: null, name: '', code: '', contact: '', phone: '', address: '' })
const rules = {
  name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  contact: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }]
}

const isAdmin = computed(() => auth.roles.includes('ADMIN'))

const displayRows = computed(() => {
  const text = keyword.value.toLowerCase()
  const all = showInactive.value ? [...rows.value, ...inactiveRows.value] : rows.value
  return all.filter((x) => !text || `${x.name || ''}${x.contact || ''}${x.phone || ''}`.toLowerCase().includes(text))
})

async function load() {
  loading.value = true
  try {
    const data = await api.suppliers()
    rows.value = Array.isArray(data) ? data : (data?.records || [])
    if (showInactive.value && isAdmin.value) {
      const off = await api.inactiveSuppliers()
      inactiveRows.value = Array.isArray(off) ? off : []
    } else {
      inactiveRows.value = []
    }
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: null, name: '', code: '', contact: '', phone: '', address: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, { id: row.id, name: row.name, code: row.code, contact: row.contact, phone: row.phone, address: row.address })
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = { name: form.name, code: form.code, contact: form.contact, phone: form.phone, address: form.address }
    if (editing.value) await api.updateSupplier(form.id, payload)
    else await api.createSupplier(payload)
    ElMessage.success('供应商已保存')
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  const confirmed = await ElMessageBox.confirm(`确认停用供应商“${row.name}”？停用后不能再下单，可在“显示已停用”里恢复。`, '停用确认', { type: 'warning' })
    .then(() => true).catch(() => false)
  if (!confirmed) return
  await api.deleteSupplier(row.id)
  ElMessage.success('已停用')
  load()
}

/** 恢复是独立动作：编辑不会顺手把停用的供应商"复活"（后端已拒绝编辑已停用记录）。 */
async function restore(row) {
  await api.restoreSupplier(row.id)
  ElMessage.success('已恢复')
  load()
}

onMounted(load)
</script>
