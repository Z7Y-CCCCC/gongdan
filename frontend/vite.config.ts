import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import { uiI18nTransform } from './scripts/ui-i18n-transform.mjs'

export default defineConfig({
  plugins: [uiI18nTransform(), vue()],
  build: {
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return;
          }
          if (id.includes('/exceljs/') || id.includes('/file-saver/')) {
            return 'export-vendor';
          }
          return 'vendor';
        }
      }
    }
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ureport': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
