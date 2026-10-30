<template>
  <div class="page">
    <div class="page-heading">
      <div><h1>审批中心</h1><p>处理部门采购申请，审批意见将被完整记录。</p></div>
      <div class="heading-badge"><span>{{ rows.length }}</span> 条待处理</div>
    </div>

    <el-card class="panel-card filter-card">
      <el-form inline>
        <el-form-item label="关键词">
          <el-input v-model="keyword" clearable placeholder="申请编号、事由或申请人" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="panel-card">
      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="requestNo" label="申请编号" width="180" />
        <el-table-column prop="reason" label="采购事由" min-width="250" show-overflow-tooltip />
        <el-table-column prop="applicant" label="申请人" width="100" />
        <el-table-column prop="department" label="部门" width="110" />
        <el-table-column prop="amount" label="金额" width="125">
          <template #default="{ row }">¥ {{ Number(row.amount || 0).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="175" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" type="primary" link @click="openApprove(row, true)">通过</el-button>
            <el-button v-if="row.status === 'PENDING'" type="danger" link @click="openApprove(row, false)">驳回</el-button>
            <el-button link @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无待审批申请" /></template>
      </el-table>
    </el-card>
  </div>

  <el-dialog v-model="approveVisible" :title="approved ? '通过采购申请' : '驳回采购申请'" width="480px">
    <p class="dialog-tip">{{ approved ? '审批通过后，采购人员可以创建采购订单。' : '请填写驳回原因，申请人将会收到通知。' }}</p>
    <el-input v-model="comment" type="textarea" :rows="4" :placeholder="approved ? '审批意见（选填）' : '请输入驳回原因'" />
    <template #footer>
      <el-button @click="approveVisible = false">取消</el-button>
      <el-button :type="approved ? 'primary' : 'danger'" :loading="saving" @click="confirmApprove">
        确认{{ approved ? '通过' : '驳回' }}
      </el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="detailVisible" title="申请详情" width="620px">
    <el-descriptions v-if="detail" border :column="2">
      <el-descriptions-item label="申请编号">{{ detail.requestNo }}</el-descriptions-item>
      <el-descriptions-item label="状态"><StatusTag :status="detail.status" /></el-descriptions-item>
      <el-descriptions-item label="申请人">{{ detail.applicant }}</el-descriptions-item>
      <el-descriptions-item label="部门">{{ detail.department }}</el-descriptions-item>
      <el-descriptions-item label="采购事由" :span="2">{{ detail.reason }}</el-descriptions-item>
      <el-descriptions-item label="总金额">¥ {{ Number(detail.amount || 0).toLocaleString() }}</el-descriptions-item>
    </el-descriptions>
    <h4 v-if="detail?.items?.length">采购明细</h4>
    <el-table v-if="detail?.items?.length" :data="detail.items" size="small">
      <el-table-column prop="productName" label="商品" />
      <el-table-column prop="quantity" label="数量" width="90" />
      <el-table-column prop="unitPrice" label="单价" width="120" />
      <el-table-column prop="amount" label="小计" width="130" />
    </el-table>
  </el-dialog>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import StatusTag from '../components/StatusTag.vue'
import { Search } from '@element-plus/icons-vue'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const keyword = ref('')
const approveVisible = ref(false)
const detailVisible = ref(false)
const approved = ref(true)
const comment = ref('')
const selected = ref(null)
const detail = ref(null)

/**
 * 待办列表走专用接口。
 *
 * <p>改造前这里调的是 {@code /purchase-requests}，而那个接口返回的是"我的申请"——
 * 负责人点进审批中心，看到的永远是自己提交的单子，别人的一条都没有，
 * 于是"审批中心"这个页面在有数据的环境里永远是空的。</p>
 */
async function load() {
  loading.value = true
  try {
    const data = await api.pending({ keyword: keyword.value || undefined })
    rows.value = Array.isArray(data) ? data : (data?.records || [])
  } catch (e) {
    if (e?.response?.status === 403) ElMessage.warning('当前账号没有审批权限')
    else ElMessage.error(e?.response?.data?.message || '加载待办失败')
  } finally {
    loading.value = false
  }
}

function openApprove(row, value) {
  selected.value = row
  approved.value = value
  comment.value = ''
  approveVisible.value = true
}

async function confirmApprove() {
  if (!approved.value && !comment.value.trim()) return ElMessage.warning('请填写驳回原因')
  saving.value = true
  try {
    const fn = approved.value ? api.approve : api.reject
    await fn(selected.value.id, { comment: comment.value })
    ElMessage.success(approved.value ? '申请已通过' : '申请已驳回')
    approveVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '审批失败')
  } finally {
    saving.value = false
  }
}

async function openDetail(row) {
  detail.value = { ...row, items: [] }
  detailVisible.value = true
  try {
    detail.value = await api.requestDetail(row.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
  }
}

onMounted(load)
</script>
