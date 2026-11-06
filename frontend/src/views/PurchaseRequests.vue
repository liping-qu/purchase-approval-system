<template>
  <div class="page">
    <div class="page-heading">
      <div><h1>采购申请</h1><p>创建和跟踪企业采购申请，完整记录申请到完成的过程。</p></div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建采购申请</el-button>
    </div>

    <el-card class="panel-card filter-card">
      <el-form inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="编号、事由或申请人" clearable @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 150px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审批" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="采购中" value="PURCHASING" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="panel-card">
      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="requestNo" label="申请编号" width="180" />
        <el-table-column prop="reason" label="采购事由" min-width="240" show-overflow-tooltip />
        <el-table-column prop="applicant" label="申请人" width="110" />
        <el-table-column prop="department" label="所属部门" width="120" />
        <el-table-column prop="amount" label="申请金额" width="130">
          <template #default="{ row }">¥ {{ Number(row.amount || 0).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请时间" width="175" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="view(row)">详情</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="submit(row)">提交</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="danger" @click="withdraw(row)">撤回</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无采购申请" /></template>
      </el-table>
      <div class="pagination">
        <el-pagination background layout="total, prev, pager, next" :total="rows.length" :page-size="10" />
      </div>
    </el-card>
  </div>

  <el-dialog v-model="dialogVisible" title="新建采购申请" width="720px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="采购事由" prop="reason">
        <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="请说明采购原因和用途" />
      </el-form-item>
      <el-form-item label="采购明细">
        <div class="item-list">
          <div class="item-row item-row-head">
            <span>商品</span><span>数量</span><span>单价</span><span></span>
          </div>
          <div v-for="(item, index) in form.items" :key="index" class="item-row">
            <el-select v-model="item.productId" filterable placeholder="选择商品" @change="(id) => pickProduct(item, id)">
              <el-option v-for="p in products" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
            <el-input-number v-model="item.quantity" :min="1" controls-position="right" />
            <el-input-number v-model="item.unitPrice" :min="0" :precision="2" controls-position="right" />
            <el-button text type="danger" :icon="Delete" @click="removeItem(index)" />
          </div>
          <el-button link type="primary" :icon="Plus" @click="addItem">添加明细</el-button>
        </div>
      </el-form-item>
      <el-form-item label="预计金额">
        <span class="amount-total">¥ {{ totalAmount.toLocaleString(undefined, { minimumFractionDigits: 2 }) }}</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button type="primary" :loading="saving" @click="save(true)">提交申请</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="detailVisible" title="采购申请详情" width="640px">
    <el-descriptions v-if="selected" :column="2" border>
      <el-descriptions-item label="申请编号">{{ selected.requestNo }}</el-descriptions-item>
      <el-descriptions-item label="状态"><StatusTag :status="selected.status" /></el-descriptions-item>
      <el-descriptions-item label="申请人">{{ selected.applicant }}</el-descriptions-item>
      <el-descriptions-item label="部门">{{ selected.department }}</el-descriptions-item>
      <el-descriptions-item label="采购事由" :span="2">{{ selected.reason }}</el-descriptions-item>
      <el-descriptions-item label="金额">¥ {{ Number(selected.amount || 0).toLocaleString() }}</el-descriptions-item>
      <el-descriptions-item label="申请时间">{{ selected.createTime }}</el-descriptions-item>
    </el-descriptions>
    <h4 v-if="selected?.items?.length">采购明细</h4>
    <el-table v-if="selected?.items?.length" :data="selected.items" size="small">
      <el-table-column prop="productName" label="商品" />
      <el-table-column prop="quantity" label="数量" width="90" />
      <el-table-column prop="unitPrice" label="单价" width="120" />
      <el-table-column prop="amount" label="小计" width="130" />
    </el-table>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { newIdempotencyKey } from '../utils/id'
import StatusTag from '../components/StatusTag.vue'
import { Plus, Search, Refresh, Delete } from '@element-plus/icons-vue'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const products = ref([])
const filters = reactive({ keyword: '', status: '' })
const dialogVisible = ref(false)
const detailVisible = ref(false)
const selected = ref(null)
const formRef = ref()

const emptyItem = () => ({ productId: null, quantity: 1, unitPrice: 0 })
const form = reactive({ reason: '', items: [emptyItem()] })
/**
 * 本次「新建申请」的幂等键：打开弹窗时生成一次，失败重试沿用同一个。
 * 每次点击都新生成的话，服务端就无法区分"重试"和"真的想再建一张"。
 */
const idemKey = ref('')
const rules = { reason: [{ required: true, message: '请填写采购事由', trigger: 'blur' }] }
const totalAmount = computed(() => form.items.reduce((sum, i) => sum + Number(i.quantity || 0) * Number(i.unitPrice || 0), 0))

async function load() {
  loading.value = true
  try {
    const data = await api.requests({ keyword: filters.keyword || undefined, status: filters.status || undefined })
    rows.value = Array.isArray(data) ? data : (data?.records || [])
  } finally {
    loading.value = false
  }
}

function reset() {
  filters.keyword = ''
  filters.status = ''
  load()
}

async function openCreate() {
  Object.assign(form, { reason: '', items: [emptyItem()] })
  idemKey.value = newIdempotencyKey() // 一次操作一个键，重试复用
  dialogVisible.value = true
  if (!products.value.length) products.value = await api.products()
}

function addItem() { form.items.push(emptyItem()) }
function removeItem(index) { if (form.items.length > 1) form.items.splice(index, 1) }

/** 选中商品时带出参考价，用户仍可改——参考价只是省一次手输，不是约束。 */
function pickProduct(item, id) {
  const p = products.value.find((x) => x.id === id)
  if (p && p.referencePrice != null && !Number(item.unitPrice)) item.unitPrice = Number(p.referencePrice)
}

async function save(submitAfterCreate) {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (form.items.some((x) => !x.productId)) return ElMessage.warning('请选择采购商品')
  if (form.items.some((x) => !x.quantity || x.quantity < 1)) return ElMessage.warning('采购数量至少为 1')
  saving.value = true
  try {
    // 后端只认 productId：商品名称是下单时的快照，由服务端按 id 回填，
    // 客户端传名称没有任何意义（也无法校验它跟 id 是否对得上）。
    // 带幂等键：超时重试 / 连点两次不会变成两张草稿，第二次会直接拿回第一张
    // submit=true 让后端在同一事务里建单并提交，不再分两跳
    const created = await api.createRequest({
      reason: form.reason,
      items: form.items.map((i) => ({ productId: i.productId, quantity: i.quantity, unitPrice: i.unitPrice })),
      submit: submitAfterCreate
    }, idemKey.value)
    // 后端已原子提交；只有忽略 submit 字段的旧版本才会返回 DRAFT，此时补一次提交
    if (submitAfterCreate && created?.status === 'DRAFT') {
      if (!created?.id) throw new Error('草稿已保存，但未拿到单据编号，无法自动提交')
      await api.submitRequest(created.id)
    }
    ElMessage.success(submitAfterCreate ? '采购申请已提交' : '草稿已保存')
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/** 详情走独立接口：列表行不含明细，弹窗需要 items 才能显示商品。 */
async function view(row) {
  selected.value = { ...row, items: [] }
  detailVisible.value = true
  try {
    selected.value = await api.requestDetail(row.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
  }
}

async function submit(row) {
  await api.submitRequest(row.id)
  ElMessage.success('申请已提交')
  load()
}

async function withdraw(row) {
  const confirmed = await ElMessageBox.confirm('撤回后需要重新提交，确认撤回吗？', '提示', { type: 'warning' })
    .then(() => true).catch(() => false)
  if (!confirmed) return
  // 改造前这里只改本地 row.status —— 刷新就复原，后端根本不知道被撤回过。
  await api.withdrawRequest(row.id)
  ElMessage.success('申请已撤回')
  load()
}

onMounted(load)
</script>
