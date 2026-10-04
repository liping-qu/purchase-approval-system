<template>
  <div class="login-page"><div class="login-art"><div class="art-content"><div class="brand big"><span class="brand-mark">P</span><span>采购管理系统</span></div><h1>让每一次采购<br />都清晰可追溯</h1><p>从申请、审批到入库，一站式管理企业采购流程</p><div class="flow"><span>提交申请</span><i>→</i><span>审批</span><i>→</i><span>采购</span><i>→</i><span>入库</span></div></div></div><div class="login-panel"><div class="login-card"><div class="mobile-brand"><span class="brand-mark">P</span> 企业采购管理系统</div><h2>欢迎回来</h2><p class="login-subtitle">登录你的管理工作台</p><el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="submit"><el-form-item prop="username"><el-input v-model="form.username" size="large" placeholder="请输入用户名" :prefix-icon="User" /></el-form-item><el-form-item prop="password"><el-input v-model="form.password" size="large" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock" /></el-form-item><div class="login-options"><el-checkbox v-model="remember">记住我</el-checkbox><a href="#" @click.prevent="forgot">忘记密码？</a></div><el-button class="login-button" type="primary" size="large" native-type="submit" :loading="loading">登 录</el-button></el-form><div class="demo-tip"><span>演示账号</span><code>admin</code><code>admin123</code><el-button link type="primary" @click="fillDemo">一键填入</el-button></div></div><p class="login-footer">© 2026 Enterprise Procurement System</p></div></div>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
const router = useRouter(); const route = useRoute(); const auth = useAuthStore(); const formRef = ref(); const loading = ref(false); const remember = ref(true)
const form = reactive({ username: '', password: '' }); const rules = { username: [{ required: true, message: '请输入用户名', trigger: 'blur' }], password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 4, message: '密码至少 4 位', trigger: 'blur' }] }
function fillDemo() { form.username = 'admin'; form.password = 'admin123' }
async function submit() { const valid = await formRef.value.validate().catch(() => false); if (!valid) return; loading.value = true; try { await auth.login(form); ElMessage.success('登录成功'); router.push(route.query.redirect || '/dashboard') } catch (e) { ElMessage.error(e?.response?.data?.message || '登录失败，请检查账号密码') } finally { loading.value = false } }
function forgot() { ElMessage.info('请联系系统管理员重置密码') }
</script>
