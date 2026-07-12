/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: true,
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // SSE 流式响应需要禁用代理层缓冲，否则最后的 done 事件可能丢失
        headers: { 'X-Accel-Buffering': 'no', 'Cache-Control': 'no-cache' },
      },
    },
  },
})
