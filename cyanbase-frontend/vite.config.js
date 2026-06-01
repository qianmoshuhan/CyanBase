import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    // 核心：本地开发关闭严格模式，字体不再403
    fs: {
      strict: false
    },
    proxy: {
      '/auth': { target: 'http://localhost:8090', changeOrigin: true },
      '/system': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        bypass(req, res, options) {
          if (req.method === 'GET' && req.headers.accept?.includes('html')) {
            return '/index.html'
          }
        }
      },
      '/monitor': { target: 'http://localhost:8090', changeOrigin: true },
      '/form': { target: 'http://localhost:8090', changeOrigin: true },
      '/wf': { target: 'http://localhost:8090', changeOrigin: true },
      '/log': { target: 'http://localhost:8090', changeOrigin: true },
      '/workflow': { target: 'http://localhost:8090', changeOrigin: true },
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        configure(proxy, options) {
          proxy.on('proxyReq', (proxyReq, req) => {
            proxyReq.setHeader('X-Forwarded-For', req.socket.remoteAddress)
            proxyReq.setHeader('X-Real-IP', req.socket.remoteAddress)
          })
        }
      }
    },
    historyApiFallback: { rewrites: [{ from: /.*/, to: '/index.html' }] }
  }
})