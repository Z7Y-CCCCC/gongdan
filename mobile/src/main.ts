import { createSSRApp } from 'vue'
import App from './App.vue'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
// #ifdef H5
import './h5-routes'
import { UniServiceJSBridge, getApp, getCurrentPages, plugin, setupApp, uni } from '@dcloudio/uni-h5'
// #endif

export function createApp() {
  const app = createSSRApp(App)
  app.component('uni-icons', UniIcons)
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
  app.component('uni-icons', UniIcons)
  app.mount('#app')
}
// #endif
