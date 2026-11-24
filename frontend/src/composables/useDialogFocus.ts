import { nextTick, onActivated, onBeforeUnmount, onDeactivated, watch, type Ref } from 'vue'

// Only the topmost modal owns Escape and the keyboard focus loop.
type DialogEntry = { id: symbol; root: Ref<HTMLElement | null>; focus: () => void }
const activeDialogs: DialogEntry[] = []
const selector = 'button, [href], input, select, textarea, [tabindex]'

function available(element: HTMLElement) {
  if (!element.isConnected || element.matches(':disabled, input[type="hidden"]') || element.closest('[hidden], [inert], [aria-hidden="true"]')) return false
  for (let current: HTMLElement | null = element; current; current = current.parentElement) {
    const style = getComputedStyle(current)
    if (style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse') return false
  }
  return true
}

function focusableElements(root: HTMLElement) {
  return Array.from(root.querySelectorAll<HTMLElement>(selector)).filter(element => element.tabIndex >= 0 && available(element))
}

export function useDialogFocus(
  root: Ref<HTMLElement | null>,
  visible: () => boolean,
  dismiss: () => void,
  initialFocus?: () => HTMLElement | null
) {
  const id = Symbol('dialog')
  let previous: HTMLElement | null = null
  let generation = 0
  let active = false
  let restorePending = false
  let deactivated = false
  const entry: DialogEntry = { id, root, focus: focusDialog }

  const top = () => activeDialogs[activeDialogs.length - 1]
  const isTop = () => top()?.id === id

  function release() {
    const index = activeDialogs.indexOf(entry)
    if (index >= 0) activeDialogs.splice(index, 1)
  }

  function restoreFocus() {
    const parent = top()
    if (parent?.root.value && available(parent.root.value)) {
      if (previous && available(previous) && parent.root.value.contains(previous)) previous.focus()
      else parent.focus()
    } else if (previous && available(previous)) previous.focus()
    previous = null
    restorePending = false
  }

  function focusDialog() {
    if (!root.value || !available(root.value)) return
    const preferred = initialFocus?.()
    const elements = focusableElements(root.value)
    const target = preferred && elements.includes(preferred) ? preferred : elements[0] || root.value
    target.focus()
  }

  function onKeydown(event: KeyboardEvent) {
    if (!active || event.defaultPrevented || !isTop()) return
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      dismiss()
      return
    }
    if (event.key !== 'Tab' || !root.value) return
    const elements = focusableElements(root.value)
    const current = document.activeElement
    const first = elements[0]
    const last = elements[elements.length - 1]
    if (!first) {
      event.preventDefault()
      root.value.focus()
    } else if (!root.value.contains(current) || current === root.value) {
      event.preventDefault()
      ;(event.shiftKey ? last : first)?.focus()
    } else if (event.shiftKey && current === first) {
      event.preventDefault()
      last?.focus()
    } else if (!event.shiftKey && current === last) {
      event.preventDefault()
      first.focus()
    }
  }

  document.addEventListener('keydown', onKeydown)
  async function synchronize(value: boolean) {
    if (active === value) return
    const currentGeneration = ++generation
    active = value
    if (value) {
      previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
      restorePending = false
      release()
      activeDialogs.push(entry)
      await nextTick()
      if (generation !== currentGeneration || !active || !isTop() || !root.value) return
      focusDialog()
    } else {
      restorePending = isTop()
      release()
      await nextTick()
      if (generation === currentGeneration && restorePending) restoreFocus()
    }
  }
  watch(visible, value => synchronize(value && !deactivated), { immediate: true, flush: 'post' })

  onDeactivated(() => {
    deactivated = true
    generation++
    const shouldRestore = isTop() && !!root.value?.contains(document.activeElement)
    active = false
    release()
    if (shouldRestore) restoreFocus()
    else { previous = null; restorePending = false }
  })
  onActivated(() => {
    deactivated = false
    void synchronize(visible())
  })

  onBeforeUnmount(() => {
    generation++
    const wasTop = isTop()
    active = false
    release()
    document.removeEventListener('keydown', onKeydown)
    if (wasTop || restorePending) restoreFocus()
  })
}
