import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AppLayout from '../layouts/AppLayout.vue'
import Login from '../views/Login.vue'

const routes = [
  { path: '/login', name: 'login', component: Login, meta: { public: true } },
  { path: '/', component: AppLayout, redirect: '/dashboard', children: [
    { path: 'dashboard', name: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '工作台' } },
    { path: 'requests', name: 'requests', component: () => import('../views/PurchaseRequests.vue'), meta: { title: '采购申请' } },
    { path: 'approvals', name: 'approvals', component: () => import('../views/Approvals.vue'), meta: { title: '审批中心' } },
    { path: 'suppliers', name: 'suppliers', component: () => import('../views/Suppliers.vue'), meta: { title: '供应商管理' } },
    { path: 'orders', name: 'orders', component: () => import('../views/Orders.vue'), meta: { title: '采购订单' } },
    { path: 'inventory', name: 'inventory', component: () => import('../views/Inventory.vue'), meta: { title: '库存管理' } },
    { path: 'admin', name: 'admin', component: () => import('../views/Admin.vue'), meta: { title: '系统管理' } }
  ] },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isAuthenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.name === 'login' && auth.isAuthenticated) return { name: 'dashboard' }
})
export default router
