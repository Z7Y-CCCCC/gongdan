import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  // Public-root asset URLs are browser URLs, not Windows file:// modules.
  // Keep relative-asset transforms enabled while preserving /factory.png in jsdom.
  plugins: [vue({ template: { transformAssetUrls: { includeAbsolute: false } } })],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    // Bound concurrent jsdom/TypeScript workers on developer machines shared with backend builds.
    maxWorkers: 2,
    testTimeout: 15000,
    hookTimeout: 30000,
    root: '.',
    include: ['src/**/*.{test,spec}.{ts,tsx}']
  }
})
