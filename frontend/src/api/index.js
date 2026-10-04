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

export const api = {
  login: (payload) => request('post', '/auth/login', payload, () => mockApi.login(payload)),
  dashboard: () => request('get', '/dashboard', null, () => mockApi.dashboard()),
  requests: (params) => request('get', '/purchase-requests', params, () => mockApi.requests(params)),
  createRequest: (payload) => request('post', '/purchase-requests', payload, () => mockApi.createRequest(payload)),
  submitRequest: (id) => request('post', `/purchase-requests/${id}/submit`, null, () => mockApi.submitRequest(id)),
  approve: (id, payload) => request('post', `/approvals/${id}`, { action: 'APPROVE', comment: payload?.comment || '' }, () => mockApi.approve(id, true, payload?.comment)),
  reject: (id, payload) => request('post', `/approvals/${id}`, { action: 'REJECT', comment: payload?.comment || '' }, () => mockApi.approve(id, false, payload?.comment)),
  suppliers: () => request('get', '/suppliers', null, () => mockApi.suppliers()),
  createSupplier: (payload) => request('post', '/suppliers', payload, () => mockApi.createSupplier(payload)),
  updateSupplier: (id, payload) => request('put', `/suppliers/${id}`, payload, () => mockApi.updateSupplier(id, payload)),
  deleteSupplier: (id) => request('delete', `/suppliers/${id}`, null, () => mockApi.deleteSupplier(id)),
  orders: () => request('get', '/purchase-orders', null, () => mockApi.orders()),
  receiveOrder: (id) => request('post', `/purchase-orders/${id}/delivered`, null, () => mockApi.receiveOrder(id)),
  inventory: () => request('get', '/inventory/stock', null, () => mockApi.inventory()),
  adjustInventory: (id, quantity) => request('post', `/inventory/${id}/adjust`, { quantity }, () => mockApi.adjustInventory(id, quantity)),
  logs: () => request('get', '/operation-logs', null, () => mockApi.logs()),
  users: () => request('get', '/users', null, () => mockApi.users())
}
