import { createApp } from 'vue'
import App from './App.vue'
import './styles.css'
import './workspace-dialog-motion.css'
import { i18nPlugin } from './i18n'

createApp(App).use(i18nPlugin).mount('#app')
