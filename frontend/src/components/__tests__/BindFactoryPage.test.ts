import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import BindFactoryPage from '../BindFactoryPage.vue'

// Mock the api module
vi.mock('../../api', () => ({
  bindFactory: vi.fn()
}))

import { bindFactory } from '../../api'
const mockBindFactory = vi.mocked(bindFactory)

describe('BindFactoryPage', () => {
  let wrapper: ReturnType<typeof mount>

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    wrapper?.unmount()
  })

  function createWrapper(phone = '13800138000') {
    wrapper = mount(BindFactoryPage, {
      props: { phone }
    })
    return wrapper
  }

  describe('Form rendering', () => {
    it('renders the bind form with factory code and authorization code inputs', () => {
      createWrapper()
      const inputs = wrapper.findAll('input')
      expect(inputs.length).toBe(2)
      expect(inputs[0].attributes('placeholder')).toBe('请输入工厂代码')
      expect(inputs[1].attributes('placeholder')).toBe('请输入授权码')
    })

    it('displays the phone number in the hint text', () => {
      createWrapper('13912345678')
      expect(wrapper.text()).toContain('13912345678')
      expect(wrapper.text()).toContain('尚未绑定工厂')
    })

    it('renders submit and back buttons', () => {
      createWrapper()
      const buttons = wrapper.findAll('button')
      expect(buttons.some(b => b.text().includes('确认绑定'))).toBe(true)
      expect(buttons.some(b => b.text().includes('返回登录'))).toBe(true)
    })
  })

  describe('Form validation', () => {
    it('shows error when factory code is empty on submit', async () => {
      createWrapper()
      await wrapper.find('form').trigger('submit')
      expect(wrapper.text()).toContain('请输入工厂代码')
      expect(mockBindFactory).not.toHaveBeenCalled()
    })

    it('shows error when authorization code is empty on submit', async () => {
      createWrapper()
      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('FACTORY1')
      await wrapper.find('form').trigger('submit')
      expect(wrapper.text()).toContain('请输入授权码')
      expect(mockBindFactory).not.toHaveBeenCalled()
    })

    it('does not show error when both fields are filled', async () => {
      mockBindFactory.mockResolvedValue({
        token: 'test-token',
        user: { id: 1, username: '13800138000', name: '13800138000', role: 'ADMIN', roleLabel: '管理员', virtualUser: false, enabled: true, createdAt: '2024-01-01' },
        defaultPassword: 'abc123'
      })
      createWrapper()
      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('FACTORY1')
      await inputs[1].setValue('AUTH1234')
      await wrapper.find('form').trigger('submit')
      await flushPromises()
      expect(wrapper.text()).not.toContain('请输入工厂代码')
      expect(wrapper.text()).not.toContain('请输入授权码')
    })
  })

  describe('API call', () => {
    it('calls bindFactory API with correct payload on valid submit', async () => {
      mockBindFactory.mockResolvedValue({
        token: 'test-token',
        user: { id: 1, username: '13800138000', name: '13800138000', role: 'ADMIN', roleLabel: '管理员', virtualUser: false, enabled: true, createdAt: '2024-01-01' },
        defaultPassword: 'abc123'
      })
      createWrapper('13800138000')
      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('FACTORY1')
      await inputs[1].setValue('AUTH1234')
      await wrapper.find('form').trigger('submit')
      await flushPromises()

      expect(mockBindFactory).toHaveBeenCalledWith({
        phone: '13800138000',
        factoryCode: 'FACTORY1',
        authorizationCode: 'AUTH1234'
      })
    })

    it('displays error message from API on failure', async () => {
      mockBindFactory.mockRejectedValue(new Error('授权码无效'))
      createWrapper()
      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('FACTORY1')
      await inputs[1].setValue('BADCODE1')
      await wrapper.find('form').trigger('submit')
      await flushPromises()

      expect(wrapper.text()).toContain('授权码无效')
    })
  })

  describe('Success state', () => {
    it('shows default password after successful binding', async () => {
      mockBindFactory.mockResolvedValue({
        token: 'test-token',
        user: { id: 1, username: '13800138000', name: '13800138000', role: 'ADMIN', roleLabel: '管理员', virtualUser: false, enabled: true, createdAt: '2024-01-01' },
        defaultPassword: 'Xy3k9P'
      })
      createWrapper()
      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('FACTORY1')
      await inputs[1].setValue('AUTH1234')
      await wrapper.find('form').trigger('submit')
      await flushPromises()

      expect(wrapper.text()).toContain('绑定成功')
      expect(wrapper.text()).toContain('Xy3k9P')
      expect(wrapper.text()).toContain('默认登录密码')
    })

    it('emits back event when clicking return to login after success', async () => {
      mockBindFactory.mockResolvedValue({
        token: 'test-token',
        user: { id: 1, username: '13800138000', name: '13800138000', role: 'ADMIN', roleLabel: '管理员', virtualUser: false, enabled: true, createdAt: '2024-01-01' },
        defaultPassword: 'abc123'
      })
      createWrapper()
      const inputs = wrapper.findAll('input')
      await inputs[0].setValue('FACTORY1')
      await inputs[1].setValue('AUTH1234')
      await wrapper.find('form').trigger('submit')
      await flushPromises()

      await wrapper.find('.primary-btn').trigger('click')
      expect(wrapper.emitted('back')).toBeTruthy()
    })
  })

  describe('Back navigation', () => {
    it('emits back event when clicking return button in form', async () => {
      createWrapper()
      const backBtn = wrapper.findAll('button').find(b => b.text().includes('返回登录'))
      await backBtn!.trigger('click')
      expect(wrapper.emitted('back')).toBeTruthy()
    })
  })
})
