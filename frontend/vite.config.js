import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 开发服务器。
 *
 * <p>补上 /api 代理不是"顺手加一行"，而是修一个真问题：后端默认 8080，
 * 前端 dev 在 5173，而 Spring Security 侧没有开 CORS。之前本地开发只能靠
 * VITE_API_BASE_URL 指到 8080，一旦忘了设，页面所有请求都会 404，
 * 表现就是"登录都点不动"。</p>
 *
 * <p>用同源代理后，浏览器看到的一直是 5173/api，不存在跨域；
 * 目标地址默认对齐 application.yml 的 8080，需要临时换端口时用
 * VITE_PROXY_TARGET 覆盖即可，不必改代码。</p>
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: process.env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  preview: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  build: { sourcemap: false }
})
