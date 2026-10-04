<template>
  <el-container class="app-shell">
    <el-aside :width="collapsed ? '72px' : '232px'" class="app-aside">
      <div class="brand"><span class="brand-mark">P</span><span v-if="!collapsed" class="brand-name">采购管理系统</span></div>
      <el-menu :default-active="route.path" router :collapse="collapsed" class="main-menu">
        <el-menu-item index="/dashboard"><el-icon><DataAnalysis /></el-icon><span>工作台</span></el-menu-item>
        <el-menu-item index="/requests"><el-icon><Document /></el-icon><span>采购申请</span></el-menu-item>
        <el-menu-item index="/approvals"><el-icon><Checked /></el-icon><span>审批中心</span></el-menu-item>
        <el-menu-item index="/suppliers"><el-icon><Shop /></el-icon><span>供应商管理</span></el-menu-item>
        <el-menu-item index="/orders"><el-icon><Tickets /></el-icon><span>采购订单</span></el-menu-item>
        <el-menu-item index="/inventory"><el-icon><Box /></el-icon><span>库存管理</span></el-menu-item>
        <el-menu-item index="/admin"><el-icon><Setting /></el-icon><span>系统管理</span></el-menu-item>
      </el-menu>
      <div v-if="!collapsed" class="aside-tip"><span class="tip-dot"></span>系统运行正常<br /><small>Spring Boot 3 · Vue 3</small></div>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <div class="header-left"><el-button text class="collapse-btn" @click="collapsed = !collapsed"><el-icon><Expand v-if="collapsed" /><Fold v-else /></el-icon></el-button><el-breadcrumb separator="/"><el-breadcrumb-item>企业采购管理</el-breadcrumb-item><el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item></el-breadcrumb></div>
        <div class="header-right"><el-button text class="notice" @click="noticeVisible = true"><el-badge :value="3" class="item"><el-icon><Bell /></el-icon></el-badge></el-button><el-dropdown @command="handleCommand"><span class="user-entry"><el-avatar :size="32" class="avatar">{{ initials }}</el-avatar><span class="user-name">{{ auth.user.nickname || auth.user.username || '访客' }}</span><el-icon><ArrowDown /></el-icon></span><template #dropdown><el-dropdown-menu><el-dropdown-item command="profile">个人资料</el-dropdown-item><el-dropdown-item divided command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div>
      </el-header>
      <el-main class="app-main"><router-view /></el-main>
    </el-container>
  </el-container>
  <el-drawer v-model="noticeVisible" title="通知中心" size="360px"><el-timeline><el-timeline-item timestamp="刚刚" type="primary">有 3 条采购申请等待审批</el-timeline-item><el-timeline-item timestamp="今天 10:22">订单 PO20261003001 已完成入库</el-timeline-item><el-timeline-item timestamp="昨天 16:30">库存预警：A4打印纸低于最低库存</el-timeline-item></el-timeline></el-drawer>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataAnalysis, Document, Checked, Shop, Tickets, Box, Setting, Expand, Fold, Bell, ArrowDown } from '@element-plus/icons-vue'

const route = useRoute(); const router = useRouter(); const auth = useAuthStore(); const collapsed = ref(false); const noticeVisible = ref(false)
const initials = computed(() => (auth.user.nickname || auth.user.username || '用').slice(0, 1).toUpperCase())
function handleCommand(command) { if (command === 'profile') ElMessage.info('个人资料功能即将开放'); if (command === 'logout') ElMessageBox.confirm('确认退出当前账号？', '提示', { type: 'warning' }).then(() => { auth.logout(); router.push('/login') }).catch(() => {}) }
</script>
