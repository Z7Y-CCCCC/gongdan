import { createSSRApp } from 'vue'
import App from './App.vue'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
// #ifdef H5
import '@dcloudio/uni-h5/style/api/modal.css'
import '@dcloudio/uni-h5/style/api/toast.css'
import '@dcloudio/uni-h5/style/api/action-sheet.css'
import './styles/h5-api.css'
import './h5-routes'
import { UniServiceJSBridge, UniViewJSBridge, getApp, getCurrentPages, plugin, setupApp, uni } from '@dcloudio/uni-h5'
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
    UniViewJSBridge,
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
