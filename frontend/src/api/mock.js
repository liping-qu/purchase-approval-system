const key = 'procurement_mock_state'
const id = () => `${Date.now()}${Math.floor(Math.random() * 1000)}`
const defaults = {
  suppliers: [
    { id: 1, name: '联想企业服务有限公司', contact: '王敏', phone: '13800138001', address: '北京市海淀区', creditLevel: 'A', status: 'ACTIVE' },
    { id: 2, name: '京东企业购', contact: '陈晨', phone: '13900139002', address: '北京市朝阳区', creditLevel: 'A', status: 'ACTIVE' },
    { id: 3, name: '晨光文具供应链', contact: '李梅', phone: '13700137003', address: '上海市浦东新区', creditLevel: 'B', status: 'ACTIVE' }
  ],
  requests: [
    { id: 1001, requestNo: 'PR2026010001', applicant: '张三', department: '技术部', reason: '开发部门新增员工，需要采购开发设备', amount: 45800, status: 'PENDING', createTime: '2026-10-03 09:12:00', items: [{ productName: 'Mac Mini', quantity: 2, unitPrice: 12000 }, { productName: '显示器', quantity: 2, unitPrice: 3490 }] },
    { id: 1002, requestNo: 'PR2026010002', applicant: '李四', department: '市场部', reason: '季度市场活动物料', amount: 8200, status: 'APPROVED', createTime: '2026-10-02 14:26:00', items: [{ productName: '宣传展架', quantity: 10, unitPrice: 820 }] },
    { id: 1003, requestNo: 'PR2026010003', applicant: '王五', department: '行政部', reason: '补充办公用品库存', amount: 2980, status: 'REJECTED', createTime: '2026-10-01 11:05:00', items: [{ productName: 'A4打印纸', quantity: 20, unitPrice: 149 }] }
  ],
  orders: [
    { id: 2001, orderNo: 'PO20261003001', requestNo: 'PR2026010002', supplier: '京东企业购', amount: 8200, status: 'DELIVERED', createTime: '2026-10-03 16:20:00' },
    { id: 2002, orderNo: 'PO20261001001', requestNo: 'PR2026010004', supplier: '联想企业服务有限公司', amount: 26000, status: 'PURCHASING', createTime: '2026-10-01 10:20:00' }
  ],
  inventory: [
    { id: 1, productName: 'Mac Mini', category: '电脑设备', unit: '台', quantity: 16, minimumStock: 5, updatedAt: '2026-10-03 16:30:00' },
    { id: 2, productName: '显示器', category: '电脑设备', unit: '台', quantity: 32, minimumStock: 10, updatedAt: '2026-10-03 16:30:00' },
    { id: 3, productName: 'A4打印纸', category: '办公用品', unit: '箱', quantity: 4, minimumStock: 10, updatedAt: '2026-10-02 09:00:00' },
    { id: 4, productName: '键盘', category: '电脑配件', unit: '个', quantity: 28, minimumStock: 10, updatedAt: '2026-09-30 14:12:00' }
  ],
  logs: [
    { id: 1, operator: 'admin', module: '审批管理', action: '审批采购申请', result: '成功', ip: '127.0.0.1', time: '2026-10-03 16:33:10' },
    { id: 2, operator: 'zhangsan', module: '采购申请', action: '提交采购申请', result: '成功', ip: '127.0.0.1', time: '2026-10-03 09:12:00' },
    { id: 3, operator: 'admin', module: '供应商', action: '新增供应商', result: '成功', ip: '127.0.0.1', time: '2026-10-02 18:04:25' }
  ]
}

function state() {
  try { return JSON.parse(localStorage.getItem(key)) || structuredClone(defaults) } catch { return structuredClone(defaults) }
}
function save(value) { localStorage.setItem(key, JSON.stringify(value)); return value }
const wait = (value) => new Promise((resolve) => setTimeout(() => resolve(value), 180))

export const mockApi = {
  async login(payload) { return wait({ token: 'mock-token', user: { id: 1, username: payload.username || 'admin', nickname: payload.username === 'admin' ? '系统管理员' : payload.username, roles: ['ADMIN'] } }) },
  async dashboard() { const s = state(); return wait({ totalRequests: s.requests.length + 24, pendingRequests: s.requests.filter((x) => x.status === 'PENDING').length, approvedRequests: s.requests.filter((x) => x.status === 'APPROVED').length + 8, inventoryAlerts: s.inventory.filter((x) => x.quantity < x.minimumStock).length, recent: s.requests.slice(0, 5) }) },
  async requests(params = {}) { let data = state().requests; if (params.status) data = data.filter((x) => x.status === params.status); if (params.keyword) data = data.filter((x) => `${x.requestNo}${x.reason}${x.applicant}`.includes(params.keyword)); return wait({ records: data, total: data.length }) },
  async createRequest(payload) { const s = state(); const total = (payload.items || []).reduce((sum, x) => sum + Number(x.quantity || 0) * Number(x.unitPrice || 0), 0); const item = { id: id(), requestNo: `PR${new Date().toISOString().slice(0, 7).replace('-', '')}${String(s.requests.length + 1).padStart(4, '0')}`, applicant: '当前用户', department: '技术部', reason: payload.reason, amount: total, status: payload.submit ? 'PENDING' : 'DRAFT', createTime: new Date().toLocaleString('zh-CN', { hour12: false }), items: payload.items || [] }; s.requests.unshift(item); save(s); return wait(item) },
  async submitRequest(idValue) { const s = state(); const item = s.requests.find((x) => String(x.id) === String(idValue)); if (item) item.status = 'PENDING'; save(s); return wait(item) },
  async approve(idValue, approved, comment) { const s = state(); const item = s.requests.find((x) => String(x.id) === String(idValue)); if (item) { item.status = approved ? 'APPROVED' : 'REJECTED'; item.approvalComment = comment }; save(s); return wait(item) },
  async suppliers() { return wait({ records: state().suppliers }) },
  async createSupplier(payload) { const s = state(); const item = { ...payload, id: id(), status: 'ACTIVE' }; s.suppliers.unshift(item); save(s); return wait(item) },
  async updateSupplier(idValue, payload) { const s = state(); const item = s.suppliers.find((x) => String(x.id) === String(idValue)); Object.assign(item || {}, payload); save(s); return wait(item) },
  async deleteSupplier(idValue) { const s = state(); s.suppliers = s.suppliers.filter((x) => String(x.id) !== String(idValue)); save(s); return wait(true) },
  async orders() { return wait({ records: state().orders }) },
  async receiveOrder(idValue) { const s = state(); const item = s.orders.find((x) => String(x.id) === String(idValue)); if (item) item.status = 'COMPLETED'; save(s); return wait(item) },
  async inventory() { return wait({ records: state().inventory }) },
  async adjustInventory(idValue, quantity) { const s = state(); const item = s.inventory.find((x) => String(x.id) === String(idValue)); if (item) { item.quantity += Number(quantity); item.updatedAt = new Date().toLocaleString('zh-CN', { hour12: false }) }; save(s); return wait(item) },
  async logs() { return wait({ records: state().logs }) },
  async users() { return wait({ records: [{ id: 1, username: 'admin', nickname: '系统管理员', department: '信息技术部', roles: ['ADMIN'], status: '正常' }, { id: 2, username: 'zhangsan', nickname: '张三', department: '技术部', roles: ['EMPLOYEE'], status: '正常' }, { id: 3, username: 'lisi', nickname: '李四', department: '市场部', roles: ['MANAGER'], status: '正常' }] }) }
}
