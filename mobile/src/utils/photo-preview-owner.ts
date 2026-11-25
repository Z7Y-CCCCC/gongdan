let owner: (() => void) | undefined

// Separate report cards can finish authenticated downloads concurrently. Only
// one card may own the H5 modal; superseding a card also cancels its late UI.
export function claimPhotoPreview(close: () => void) {
  if (owner && owner !== close) owner()
  owner = close
  return () => { if (owner === close) owner = undefined }
}
