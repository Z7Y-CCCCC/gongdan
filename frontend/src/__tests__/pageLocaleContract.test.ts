import { afterEach, describe, expect, it } from 'vitest'
import { setUiLocale, t } from '../i18n'
import pages from '../i18n/locales/en-pages.json'
afterEach(() => setUiLocale('zh-CN'))
describe('page translation preserves runtime values', () => {
  it('renders required-field errors in English while keeping the configured business field name', () => {
    setUiLocale('en-US')
    expect(t('请填写必填字段「{0}」', ['质检批次'])).toBe('Enter the required field "质检批次"')
  })
  it('keeps dynamic export quantities and codes unchanged in translated messages', () => {
    setUiLocale('en-US')
    expect(t('已按 BOM「{0}」带入 {1} 条用料', ['生产清单-甲', 3])).toBe('Applied 3 material lines from BOM "生产清单-甲"')
  })
  it('retains all placeholder multiplicities and has no untranslated UI phrase in the page catalog', () => {
    for (const [source, english] of Object.entries(pages)) {
      expect(english).not.toMatch(/[\u3400-\u9fff]/)
      expect(english.match(/\{\d+\}/g)?.sort() || []).toEqual(source.match(/\{\d+\}/g)?.sort() || [])
    }
  })
})
