import type { t } from './index'
import type { formatUiDate } from './date'
declare module 'vue' {
  interface ComponentCustomProperties { $t: typeof t; $date: typeof formatUiDate }
}
export {}
