import type { TransitionProps } from 'vue'

// A leaving dialog still paints briefly, but must no longer capture focus or input.
export const workspaceDialogMotion: TransitionProps = {
  name: 'workspace-dialog-motion',
  type: 'transition',
  appear: true,
  onBeforeLeave: (element) => element.setAttribute('inert', ''),
  onLeaveCancelled: (element) => element.removeAttribute('inert'),
}
