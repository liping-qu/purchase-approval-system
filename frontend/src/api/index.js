import http from './http'
import { mockApi } from './mock'

const useMock = import.meta.env.VITE_ENABLE_MOCK === 'true'
async function request(method, url, data, fallback, config = {}) {
  try {
    const response = await http.request({ method, url, data, params: method === 'get' ? data : undefined, ...config })
    return response.data
  } catch (error) {
    if (useMock && fallback) return fallback()
    throw error
  }
}

/**
 * 接口清单。
 *
 * 约定：这里的 URL 与后端 Controller 的 @RequestMapping 一一对应，
 * 返回值是后端 ApiResponse 里的 data 部分（由 http.js 拦截器解包）。
 * 凡是"页面需要展示名称"的列表，一律走后端已经 JOIN 好的展示态接口，
 * 不再由前端拿着 id 去猜字段名——那正是之前"表格全空白"的根因。
 */
export const api = {
  login: (payload) => request('post', '/auth/login', payload, () => mockApi.login(payload)),
  me: () => request('get', '/auth/me', null, () => mockApi.me?.()),
  dashboard: () => request('get', '/dashboard/summary', null, () => mockApi.dashboard()),

  requests: (params) => request('get', '/purchase-requests', params, () => mockApi.requests(params)),
  requestDetail: (id) => request('get', `/purchase-requests/${id}`, null, () => mockApi.requestDetail?.(id)),
  createRequest: (payload) => request('post', '/purchase-requests', payload, () => mockApi.createRequest(payload)),
  submitRequest: (id) => request('post', `/purchase-requests/${id}/submit`, null, () => mockApi.submitRequest(id)),
  withdrawRequest: (id) => request('post', `/purchase-requests/${id}/withdraw`, null, () => mockApi.withdrawRequest?.(id)),

  pending: (params) => request('get', '/approvals/pending', params, () => mockApi.requests(params)),
  approve: (id, payload) => request('post', `/approvals/${id}`, { action: 'APPROVE', comment: payload?.comment || '' }, () => mockApi.approve(id, true, payload?.comment)),
  reject: (id, payload) => request('post', `/approvals/${id}`, { action: 'REJECT', comment: payload?.comment || '' }, () => mockApi.approve(id, false, payload?.comment)),

  products: () => request('get', '/products', null, () => mockApi.products?.() ?? []),
  suppliers: () => request('get', '/suppliers', null, () => mockApi.suppliers()),
  inactiveSuppliers: () => request('get', '/suppliers/inactive', null, () => []),
  createSupplier: (payload) => request('post', '/suppliers', payload, () => mockApi.createSupplier(payload)),
  updateSupplier: (id, payload) => request('put', `/suppliers/${id}`, payload, () => mockApi.updateSupplier(id, payload)),
  deleteSupplier: (id) => request('delete', `/suppliers/${id}`, null, () => mockApi.deleteSupplier(id)),
  restoreSupplier: (id) => request('post', `/suppliers/${id}/restore`, null, () => true),

  orders: () => request('get', '/purchase-orders', null, () => mockApi.orders()),
  orderableRequests: () => request('get', '/purchase-orders/orderable', null, () => mockApi.requests()),
  createOrder: (payload) => request('post', '/purchase-orders', payload, () => mockApi.createOrder?.(payload)),
  receiveOrder: (id) => request('post', `/purchase-orders/${id}/delivered`, null, () => mockApi.receiveOrder(id)),

  inventory: () => request('get', '/inventory/stock', null, () => mockApi.inventory()),
  adjustInventory: (productId, quantity) => request('post', `/inventory/${productId}/adjust`, { quantity }, () => mockApi.adjustInventory(productId, quantity)),
  logs: (params) => request('get', '/admin/operation-logs', params, () => mockApi.logs()),
  users: () => request('get', '/users', null, () => mockApi.users())
}
