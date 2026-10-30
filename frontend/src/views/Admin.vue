<template>
  <div class="page">
    <div class="page-heading"><div><h1>系统管理</h1><p>管理用户、角色、权限及系统操作日志。</p></div></div>
    <el-tabs v-model="active">
      <el-tab-pane label="用户管理" name="users">
        <el-card class="panel-card">
          <el-table v-loading="loadingUsers" :data="users" stripe>
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="nickname" label="姓名" />
            <el-table-column prop="department" label="部门" />
            <el-table-column prop="roles" label="角色">
              <template #default="{ row }">
                <el-tag v-for="role in row.roles || []" :key="role" size="small" class="role-tag">{{ roleLabel(role) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '正常' : '停用' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="角色与权限" name="roles">
        <el-card class="panel-card">
          <div class="role-grid">
            <div v-for="item in roleList" :key="item.name" class="role-card">
              <div class="role-title"><el-tag :type="item.type">{{ item.name }}</el-tag><b>{{ item.label }}</b></div>
              <p>{{ item.permissions.join(' · ') }}</p>
            </div>
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="操作日志" name="logs">
        <el-card class="panel-card">
          <el-table v-loading="loadingLogs" :data="logs" stripe>
            <el-table-column prop="createTime" label="时间" width="180" />
            <el-table-column prop="operatorName" label="操作人" width="120">
              <template #default="{ row }">{{ row.operatorName || (row.operatorId ? `用户#${row.operatorId}` : '—') }}</template>
            </el-table-column>
            <el-table-column prop="module" label="模块" width="120" />
            <el-table-column prop="action" label="操作内容" min-width="180" />
            <el-table-column prop="bizId" label="业务ID" width="100" />
            <el-table-column prop="costMs" label="耗时" width="90">
              <template #default="{ row }">{{ row.costMs != null ? `${row.costMs} ms` : '—' }}</template>
            </el-table-column>
            <el-table-column prop="result" label="结果" width="100">
              <template #default="{ row }">
                <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ip" label="IP地址" width="140" />
            <template #empty><el-empty description="暂无操作日志" /></template>
          </el-table>
          <div class="pagination">
            <el-pagination
              background layout="total, prev, pager, next"
              :total="logTotal" :page-size="pageSize" :current-page="page"
              @current-change="(p) => { page = p; loadLogs() }" />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'

const active = ref('users')
const users = ref([])
const logs = ref([])
const loadingUsers = ref(false)
const loadingLogs = ref(false)
const logTotal = ref(0)
const page = ref(1)
const pageSize = 20

const roleList = [
  { name: 'ADMIN', label: '系统管理员', type: 'danger', permissions: ['用户管理', '角色管理', '系统配置', '操作日志'] },
  { name: 'MANAGER', label: '部门负责人', type: 'warning', permissions: ['查看部门申请', '审批采购申请'] },
  { name: 'PURCHASER', label: '采购人员', type: 'primary', permissions: ['供应商管理', '创建采购订单'] },
  { name: 'WAREHOUSE', label: '仓库管理员', type: 'success', permissions: ['入库确认', '库存管理'] },
  { name: 'EMPLOYEE', label: '普通员工', type: 'info', permissions: ['创建采购申请', '查看个人申请'] }
]
const roleLabel = (code) => roleList.find((r) => r.name === code)?.label || code

/**
 * 字段名以接口实际返回为准：
 * 用户 {@code status} 是 1/0 整数（不是 'ACTIVE'），日志时间戳叫 {@code createTime}
 * （不叫 time），操作人叫 {@code operatorName}。改造前按后者取值，于是状态列恒为
 * undefined、日志的时间和操作人两列永远是空白——接口有数据，页面就是显示不出来。
 */
async function loadUsers() {
  loadingUsers.value = true
  try {
    const data = await api.users()
    users.value = Array.isArray(data) ? data : (data?.records || [])
  } finally {
    loadingUsers.value = false
  }
}

async function loadLogs() {
  loadingLogs.value = true
  try {
    const data = await api.logs({ page: page.value, size: pageSize })
    logs.value = data?.records || (Array.isArray(data) ? data : [])
    logTotal.value = data?.total ?? logs.value.length
  } finally {
    loadingLogs.value = false
  }
}

onMounted(() => {
  loadUsers()
  loadLogs()
})
</script>
