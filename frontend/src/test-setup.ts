import { beforeEach } from 'vitest'
import { config } from '@vue/test-utils'
import { i18nPlugin, setUiLocale, setUiTimezone } from './i18n'
config.global.plugins.push(i18nPlugin)
beforeEach(() => { setUiLocale('zh-CN'); setUiTimezone('Asia/Shanghai') })
