import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,

    // 需要让域名/局域网/内网穿透访问开发服务器时，把它设成 '0.0.0.0'（默认只监听 localhost）：
    //   VITE_HOST=0.0.0.0 npm run dev
    host: process.env.VITE_HOST || 'localhost',

    // Vite 会校验请求的 Host 头，不在白名单里的域名会被直接拒绝：
    //   Blocked request. This host ("xxx") is not allowed.
    // 用域名访问时把域名加进来（多个用逗号分隔）：
    //   VITE_ALLOWED_HOSTS=okcrm.onekey-ai.top npm run dev
    // 不配置时默认放行所有域名 —— 方便，但意味着 dev server 不设防，
    // 切勿把它直接暴露到公网；正式部署请用 npm run build + Nginx。
    allowedHosts: process.env.VITE_ALLOWED_HOSTS
      ? process.env.VITE_ALLOWED_HOSTS.split(',').map((item) => item.trim()).filter(Boolean)
      : true,

    // 后端 context-path 是 /api，这里保持前缀转发即可。
    // 目标地址可用环境变量覆盖，便于本机端口冲突时换端口：
    //   VITE_API_TARGET=http://127.0.0.1:9001 npm run dev
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://127.0.0.1:9001',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500
  }
})
