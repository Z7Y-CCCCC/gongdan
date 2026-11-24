export function scannedWorkOrderRoute(raw: string, plainAsCode = false): string {
  const value = raw.trim()
  if (!value || value.length > 2048) throw new Error('请输入工单号或工单二维码')
  let id = '', code = ''
  if (/^\d+$/.test(value) && !plainAsCode) id = value
  else if (/^(?:https?:\/\/|\/|#\/)/i.test(value)) {
    if (!/(?:#)?\/pages\/work-order-detail\/index\?/.test(value)) throw new Error('不是工单二维码')
    const query = value.slice(value.lastIndexOf('?') + 1)
    for (const pair of query.split('&')) {
      const separator = pair.indexOf('=')
      if (separator < 0) continue
      const key = pair.slice(0, separator)
      let content: string
      try { content = decodeURIComponent(pair.slice(separator + 1).replace(/\+/g, ' ')) }
      catch { throw new Error('二维码内容无效') }
      if (key === 'id') id = content
      if (key === 'code') code = content
    }
  } else {
    if (value.includes('://') || /[\r\n\x00]/.test(value)) throw new Error('不是工单二维码')
    code = value
  }
  if (id) {
    if (!/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id))) throw new Error('工单编号无效')
    return `/pages/work-order-detail/index?id=${id}`
  }
  if (!code.trim() || code.length > 128) throw new Error('工单号无效')
  return `/pages/work-order-detail/index?code=${encodeURIComponent(code.trim())}`
}
