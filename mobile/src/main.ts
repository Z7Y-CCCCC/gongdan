import { createSSRApp } from 'vue'
import App from './App.vue'
// #ifdef H5
import './h5-routes'
import { UniServiceJSBridge, getApp, getCurrentPages, plugin, setupApp, uni } from '@dcloudio/uni-h5'
// #endif

export function createApp() {
  const app = createSSRApp(App)
  return { app }
}

// #ifdef H5
if (typeof window !== 'undefined') {
  Object.assign(window, {
    UniServiceJSBridge,
    getApp,
    getCurrentPages,
    uni
  })
  const app = createSSRApp(setupApp(App))
  app.use(plugin)
  app.mount('#app')
}
// #endif
