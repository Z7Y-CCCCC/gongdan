import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import path from 'path'

function rpxToPx(code: string) {
  return code.replace(/(-?\d*\.?\d+)rpx\b/g, (_, value) => {
    const px = Number(value) / 2
    return `${Number.isInteger(px) ? px : Number(px.toFixed(3))}px`
  })
}

function h5RpxFallbackPlugin() {
  const isH5 = () => process.env.UNI_PLATFORM === 'h5'
  return {
    name: 'h5-rpx-fallback',
    enforce: 'post' as const,
    transform(code: string, id: string) {
      if (!isH5()) return null
      if (id.endsWith('.vue') || id.endsWith('.css') || id.includes('type=style')) {
        return rpxToPx(code)
      }
      return null
    },
    generateBundle(_: unknown, bundle: Record<string, any>) {
      if (!isH5()) return
      for (const asset of Object.values(bundle)) {
        if (asset.type === 'asset' && asset.fileName.endsWith('.css') && typeof asset.source === 'string') {
          asset.source = rpxToPx(asset.source)
        }
      }
    }
  }
}

export default defineConfig({
  plugins: [uni(), h5RpxFallbackPlugin()],
  server: {
    port: 5174
  },
  resolve: {
    alias: {
      'vue': path.resolve(__dirname, 'node_modules/@dcloudio/uni-h5-vue/dist/vue.runtime.esm.js')
    }
  }
})
