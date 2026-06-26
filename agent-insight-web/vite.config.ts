import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
        port: 9281,
    proxy: {
      '/api': {
        target: 'http://localhost:9280',
        changeOrigin: true,
      },
    },
  },
})
