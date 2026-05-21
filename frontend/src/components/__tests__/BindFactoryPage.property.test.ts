import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import fc from 'fast-check'
import BindFactoryPage from '../BindFactoryPage.vue'

// Mock the api module
vi.mock('../../api', () => ({
  bindFactory: vi.fn()
}))

import { bindFactory } from '../../api'
const mockBindFactory = vi.mocked(bindFactory)

describe('BindFactoryPage - Property 1: Form validation accepts non-empty strings', () => {
  /**
   * Property 1: Form validation accepts non-empty strings
   *
   * For any non-empty factoryCode and non-empty authorizationCode,
   * the form validation should pass (no error message shown, API is called).
   *
   * **Validates: Requirements 2.10**
   */
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('non-empty factoryCode and authorizationCode pass validation and trigger API call', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.string({ minLength: 1 }).filter(s => s.trim().length > 0),
        fc.string({ minLength: 1 }).filter(s => s.trim().length > 0),
        async (factoryCode, authorizationCode) => {
          mockBindFactory.mockResolvedValue({
            token: 'test-token',
            user: {
              id: 1,
              username: '13800138000',
              name: '13800138000',
              role: 'ADMIN' as const,
              roleLabel: '管理员',
              virtualUser: false,
              enabled: true,
              createdAt: '2024-01-01'
            },
            defaultPassword: 'abc123'
          })

          const wrapper = mount(BindFactoryPage, {
            props: { phone: '13800138000' }
          })

          const inputs = wrapper.findAll('input')
          await inputs[0].setValue(factoryCode)
          await inputs[1].setValue(authorizationCode)
          await wrapper.find('form').trigger('submit')
          await flushPromises()

          // Validation should pass - no validation error messages shown
          expect(wrapper.text()).not.toContain('请输入工厂代码')
          expect(wrapper.text()).not.toContain('请输入授权码')
          // API should be called
          expect(mockBindFactory).toHaveBeenCalled()

          wrapper.unmount()
          vi.clearAllMocks()
        }
      ),
      { numRuns: 100 }
    )
  })
})

describe('BindFactoryPage - Property 2: Form validation rejects empty/whitespace-only strings', () => {
  /**
   * Property 2: Form validation rejects empty/whitespace-only strings
   *
   * For any combination where factoryCode or authorizationCode is empty or
   * whitespace-only, the form validation should fail (error message shown,
   * API is NOT called).
   *
   * **Validates: Requirements 2.10**
   */
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('empty/whitespace factoryCode is rejected with error and no API call', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.stringOf(fc.constant(' ')),  // whitespace-only strings (including empty)
        fc.string({ minLength: 1 }).filter(s => s.trim().length > 0),
        async (factoryCode, authorizationCode) => {
          const wrapper = mount(BindFactoryPage, {
            props: { phone: '13800138000' }
          })

          const inputs = wrapper.findAll('input')
          await inputs[0].setValue(factoryCode)
          await inputs[1].setValue(authorizationCode)
          await wrapper.find('form').trigger('submit')
          await flushPromises()

          // Validation should fail - error message shown
          expect(wrapper.text()).toContain('请输入工厂代码')
          // API should NOT be called
          expect(mockBindFactory).not.toHaveBeenCalled()

          wrapper.unmount()
          vi.clearAllMocks()
        }
      ),
      { numRuns: 100 }
    )
  })

  it('empty/whitespace authorizationCode is rejected with error and no API call', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.string({ minLength: 1 }).filter(s => s.trim().length > 0),
        fc.stringOf(fc.constant(' ')),  // whitespace-only strings (including empty)
        async (factoryCode, authorizationCode) => {
          const wrapper = mount(BindFactoryPage, {
            props: { phone: '13800138000' }
          })

          const inputs = wrapper.findAll('input')
          await inputs[0].setValue(factoryCode)
          await inputs[1].setValue(authorizationCode)
          await wrapper.find('form').trigger('submit')
          await flushPromises()

          // Validation should fail - error message shown
          expect(wrapper.text()).toContain('请输入授权码')
          // API should NOT be called
          expect(mockBindFactory).not.toHaveBeenCalled()

          wrapper.unmount()
          vi.clearAllMocks()
        }
      ),
      { numRuns: 100 }
    )
  })
})

describe('BindFactoryPage - Property 3: API error messages are correctly displayed', () => {
  /**
   * Property 3: API error messages are correctly displayed
   *
   * For any error message string returned by the API, the component should
   * display that exact error message to the user.
   *
   * **Validates: Requirements 2.10**
   */
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('any API error message is displayed exactly as received', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.string({ minLength: 1, maxLength: 100 })
          .map(s => s.trim())
          .filter(s => s.length > 0),
        async (errorMsg) => {
          mockBindFactory.mockRejectedValue(new Error(errorMsg))

          const wrapper = mount(BindFactoryPage, {
            props: { phone: '13800138000' }
          })

          const inputs = wrapper.findAll('input')
          await inputs[0].setValue('FACTORY1')
          await inputs[1].setValue('AUTH1234')
          await wrapper.find('form').trigger('submit')
          await flushPromises()

          // The error message from the API should be displayed
          expect(wrapper.text()).toContain(errorMsg)

          wrapper.unmount()
          vi.clearAllMocks()
        }
      ),
      { numRuns: 100 }
    )
  })
})
