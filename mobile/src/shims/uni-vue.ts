export * from 'vue/dist/vue.runtime.esm-bundler.js'
import { getCurrentInstance } from 'vue/dist/vue.runtime.esm-bundler.js'

export const isInSSRComponentSetup = false

export function injectHook(type: string, hook: (...args: unknown[]) => unknown, target = getCurrentInstance()) {
  if (!target) return undefined
  const instance = target as unknown as Record<string, Array<(...args: unknown[]) => unknown>>
  const hooks = instance[type] || (instance[type] = [])
  const wrappedHook = (...args: unknown[]) => hook(...args)
  hooks.push(wrappedHook)
  return wrappedHook
}
