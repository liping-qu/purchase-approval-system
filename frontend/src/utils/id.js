/**
 * 生成一次「用户意图」的幂等键。
 *
 * 关键点：幂等键不是"每个 HTTP 请求一个"，而是"每次用户操作一个"。
 * 网络超时后重试、按钮连点，都必须复用同一个 key，服务端才能识别出
 * "这是同一次提交"，从而回放上次结果而不是再执行一遍。
 */
export function newIdempotencyKey() {
  const uuid = globalThis.crypto?.randomUUID?.()
  if (uuid) return uuid
  // 旧浏览器的兜底：时间戳 + 随机串，长度满足后端 8–64 位校验
  return `k${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}
