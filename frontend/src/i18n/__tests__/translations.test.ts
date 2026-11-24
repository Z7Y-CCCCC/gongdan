import { describe, expect, it } from 'vitest'
import { setUiLocale, t } from '../index'
describe('translation without altering authored data', () => {
  it('translates dynamic UI parameters and complete backend validation messages', () => {
    setUiLocale('en-US')
    expect(t('新建{0}', [t('工序')])).toMatch(/^New /)
    expect(t('工序名称不能为空')).not.toContain('不能为空')
    expect(t('新密码不能为空；两次输入的密码不一致')).toBe(t('新密码不能为空') + '; ' + t('两次输入的密码不一致'))
  })
  it('preserves unknown text and unknown compound segments verbatim', () => {
    setUiLocale('en-US')
    expect(t('客户原文  A\nB')).toBe('客户原文  A\nB')
    expect(t('新密码不能为空；客户原文')).toBe('新密码不能为空；客户原文')
  })
  it('keeps parameters and newlines in translated copyable account details', () => {
    setUiLocale('en-US')
    const translated = t('账号：{0}\n姓名：{1}\n角色：{2}\n部门：{3}\n密码：{4}', ['qa', '保存', 'ADMIN', '制造', 'not-a-real-password'])
    expect(translated).toContain('Name: 保存')
    expect(translated).toContain('\n')
  })
})
