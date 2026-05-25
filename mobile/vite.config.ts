import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import path from 'path'

export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5174
  },
  resolve: {
    alias: {
      'vue': path.resolve(__dirname, 'node_modules/@dcloudio/uni-h5-vue/dist/vue.runtime.esm.js')
    }
  }
})
