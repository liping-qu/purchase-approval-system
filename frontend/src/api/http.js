import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
const http = axios.create({ baseURL, timeout: 8000, headers: { 'Content-Type': 'application/json' } })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('procurement_token')
  if (token) config.headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`
  return config
})

http.interceptors.response.use((response) => {
  const body = response.data
  if (body && typeof body === 'object' && 'data' in body && ('code' in body || 'success' in body)) return { ...response, data: body.data }
  return response
}, (error) => Promise.reject(error))

export default http
