<template>
  <div class="page">
    <div class="page-heading">
      <div><h1>采购订单</h1><p>跟踪订单采购、到货和入库状态。</p></div>
      <el-button v-if="canOrder" type="primary" :icon="Plus" @click="openCreate">新建采购订单</el-button>
    </div>

    <el-card class="panel-card filter-card">
      <el-form inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="订单号、申请号、事由或供应商" clearable
                    @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 150px">
            <el-option label="待收货" value="CREATED" />
            <el-option label="已入库" value="DELIVERED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="panel-card">
      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="orderNo" label="订单编号" width="190" />
        <el-table-column prop="requestNo" label="关联申请" width="170" />
        <el-table-column prop="reason" label="采购事由" min-width="200" show-overflow-tooltip />
        <el-table-column prop="supplier" label="供应商" min-width="180" />
        <el-table-column prop="amount" label="订单金额" width="140">
          <template #default="{ row }">¥ {{ Number(row.amount || 0).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" width="175" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button v-if="row.status === 'CREATED' && canDeliver" link type="primary" @click="receive(row)">确认入库</el-button>
            <el-button link @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无采购订单" /></template>
      </el-table>

      <!-- 分页：total 来自服务端 COUNT，不是当前页行数；改前接口不返回 total，分页器永远只有一页 -->
      <el-pagination
        v-model:current-page="pager.page"
        v-model:page-size="pager.size"
        :total="pager.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="load"
        @size-change="search"
      />
    </el-card>
  </div>

  <el-dialog v-model="createVisible" title="新建采购订单" width="560px" destroy-on-close>
    <el-form label-width="90px">
      <el-form-item label="采购申请">
        <el-select v-model="form.requestId" filterable placeholder="选择一条已审批通过的申请" style="width: 100%">
          <el-option v-for="r in orderable" :key="r.id" :label="`${r.requestNo} · ${r.department} · ¥${r.amount}`" :value="r.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="供应商">
        <el-select v-model="form.supplierId" filterable placeholder="选择供应商" style="width: 100%">
          <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="采购事由">
        <span>{{ pickedRequest?.reason || '—' }}</span>
      </el-form-item>
      <el-form-item label="订单金额">
        <span class="amount-total">¥ {{ Number(pickedRequest?.amount || 0).toLocaleString() }}</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="confirmCreate">创建订单</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="visible" title="订单详情" width="640px">
    <el-descriptions v-if="detail" border :column="2">
      <el-descriptions-item label="订单编号">{{ detail.orderNo }}</el-descriptions-item>
      <el-descriptions-item label="关联申请">{{ detail.requestNo }}</el-descriptions-item>
      <el-descriptions-item label="供应商">{{ detail.supplier }}</el-descriptions-item>
      <el-descriptions-item label="金额">¥ {{ Number(detail.amount || 0).toLocaleString() }}</el-descriptions-item>
      <el-descriptions-item label="状态"><StatusTag :status="detail.status" /></el-descriptions-item>
      <el-descriptions-item label="下单时间">{{ detail.createTime }}</el-descriptions-item>
    </el-descriptions>

    <!-- 订单明细：改前详情只返回行数据，采购员看不到买了哪几样、各多少件 -->
    <h4 v-if="detail?.items?.length">订单明细</h4>
    <el-table v-if="detail?.items?.length" :data="detail.items" size="small">
      <el-table-column prop="productName" label="商品" />
      <el-table-column prop="quantity" label="数量" width="90" />
      <el-table-column prop="unitPrice" label="单价" width="120" />
      <el-table-column prop="amount" label="小计" width="130" />
    </el-table>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import StatusTag from '../components/StatusTag.vue'
import { useAuthStore } from '../stores/auth'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'

const auth = useAuthStore()
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const detail = ref(null)

const filters = reactive({ keyword: '', status: '' })
const pager = reactive({ page: 1, size: 10, total: 0 })

const createVisible = ref(false)
const orderable = ref([])
const suppliers = ref([])
const form = reactive({ requestId: null, supplierId: null })

const canOrder = computed(() => auth.roles.some((r) => r === 'ADMIN' || r === 'PURCHASER'))
const canDeliver = computed(() => auth.roles.some((r) => r === 'ADMIN' || r === 'WAREHOUSE'))
const pickedRequest = computed(() => orderable.value.find((r) => r.id === form.requestId))

async function load() {
  loading.value = true
  try {
    const data = await api.orders({
      page: pager.page,
      size: pager.size,
      keyword: filters.keyword || undefined,
      status: filters.status || undefined
    })
    // 服务端分页返回 {records,total,page,size}；仍兼容旧版数组返回
    rows.value = Array.isArray(data) ? data : (data?.records || [])
    pager.total = Array.isArray(data) ? data.length : Number(data?.total || 0)
  } finally {
    loading.value = false
  }
}

/** 查询条件变化要回到第一页，否则会停在越界页码上显示空白。 */
function search() {
  pager.page = 1
  load()
}

function reset() {
  filters.keyword = ''
  filters.status = ''
  search()
}

/** 下单入口：数据来自"已审批通过且还没有订单"的专用接口，重复下单在 UI 层就没有选项。 */
async function openCreate() {
  Object.assign(form, { requestId: null, supplierId: null })
  createVisible.value = true
  const [requests, supplierList] = await Promise.all([api.orderableRequests(), api.suppliers()])
  orderable.value = Array.isArray(requests) ? requests : []
  suppliers.value = Array.isArray(supplierList) ? supplierList : []
  if (!orderable.value.length) ElMessage.info('暂无可下单的申请，请先完成审批')
}

async function confirmCreate() {
  if (!form.requestId) return ElMessage.warning('请选择采购申请')
  if (!form.supplierId) return ElMessage.warning('请选择供应商')
  saving.value = true
  try {
    await api.createOrder({ requestId: form.requestId, supplierId: form.supplierId })
    ElMessage.success('采购订单已创建')
    createVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '创建订单失败')
  } finally {
    saving.value = false
  }
}

/** 只有 CREATED（待收货）才能入库；改前判断写的是 DELIVERED，于是"入库"按钮出现在已入库的单子上。 */
async function receive(row) {
  await api.receiveOrder(row.id)
  ElMessage.success('已确认入库，库存已更新')
  load()
}

async function openDetail(row) {
  // 先用列表行渲染，再拉详情补齐明细；详情失败也不至于让弹窗空白
  detail.value = row
  visible.value = true
  try {
    detail.value = await api.orderDetail(row.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '加载订单明细失败')
  }
}

onMounted(load)
</script>
