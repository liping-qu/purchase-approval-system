<template><div class="page"><div class="page-heading"><div><h1>库存管理</h1><p>查看实时库存和库存流水，及时处理低库存预警。</p></div></div><div class="stat-grid compact"><div class="stat-card blue"><div><span>库存品类</span><strong>{{ rows.length }}</strong><small>种商品</small></div></div><div class="stat-card red"><div><span>库存预警</span><strong>{{ alerts }}</strong><small>低于最低库存</small></div></div></div><el-card class="panel-card"><el-table v-loading="loading" :data="rows" stripe><el-table-column prop="productName" label="商品名称" min-width="220" /><el-table-column prop="category" label="分类" width="140" /><el-table-column prop="unit" label="单位" width="90" /><el-table-column prop="quantity" label="当前库存" width="120"><template #default="{ row }"><b :class="{ 'low-stock': row.quantity < row.minimumStock }">{{ row.quantity }}</b></template></el-table-column><el-table-column prop="minimumStock" label="最低库存" width="120" /><el-table-column prop="updatedAt" label="更新时间" width="180" /><el-table-column label="操作" width="160"><template #default="{ row }"><el-button link type="primary" @click="adjust(row, 1)">入库</el-button><el-button link type="warning" @click="adjust(row, -1)">出库</el-button></template></el-table-column></el-table></el-card></div></template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { newIdempotencyKey } from '../utils/id'

const rows = ref([])
const loading = ref(false)
const alerts = computed(() => rows.value.filter((x) => x.quantity < x.minimumStock).length)

/**
 * 上一次「结果未知」的调整（网络超时、服务端已提交但响应丢了）。
 * 用户重试同样的商品和数量时复用它的 key，服务端据此回放上次结果，
 * 而不是再加一次库存 —— 这是"只改前端禁用按钮"永远做不到的那一半。
 */
let pending = null

async function load() {
  loading.value = true
  try {
    const data = await api.inventory()
    rows.value = data?.records || data?.data?.records || data || []
  } finally {
    loading.value = false
  }
}

async function adjust(row, sign) {
  const quantity = await ElMessageBox.prompt(
    sign > 0 ? '请输入入库数量' : '请输入出库数量',
    sign > 0 ? '商品入库' : '商品出库',
    { inputPattern: /^\d+(\.\d+)?$/, inputErrorMessage: '请输入数字' }
  ).then(({ value }) => Number(value) * sign).catch(() => null)
  if (quantity === null) return
  if (sign < 0 && row.quantity + quantity < 0) return ElMessage.warning('库存不足')

  const key = pending && pending.productId === row.productId && pending.quantity === quantity
    ? pending.key
    : newIdempotencyKey()
  pending = { productId: row.productId, quantity, key }

  try {
    await api.adjustInventory(row.productId, quantity, key)
    pending = null
    ElMessage.success(sign > 0 ? '入库成功' : '出库成功')
    load()
  } catch (e) {
    // 失败时保留 key：下次相同的调整会命中幂等回放，不会重复加减
    ElMessage.error(e?.response?.data?.message || e?.message || '调整失败')
  }
}

onMounted(load)
</script>
