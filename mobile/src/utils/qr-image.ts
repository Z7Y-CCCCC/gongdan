import QrCode from 'qrcode-reader'

export const QR_MAX_FILE_BYTES = 10 * 1024 * 1024
export const QR_MAX_PIXELS = 1024 * 1024

export function decodeQrPixels(bitmap: { width: number; height: number; data: Uint8ClampedArray | Uint8Array }) {
  const { width, height, data } = bitmap
  if (!Number.isInteger(width) || !Number.isInteger(height) || width <= 0 || height <= 0
    || width * height > QR_MAX_PIXELS || data.length !== width * height * 4) {
    return Promise.reject(new Error('图片尺寸不适合扫码'))
  }
  return new Promise<string>((resolve, reject) => {
    const reader = new QrCode()
    reader.callback = (error, result) => {
      if (error || !result?.result) reject(new Error('未识别到二维码，请重新拍摄'))
      else resolve(result.result)
    }
    try { reader.decode(bitmap) } catch { reject(new Error('未识别到二维码，请重新拍摄')) }
  })
}

export function readQrImage(url: string) {
  return new Promise<string>((resolve, reject) => {
    const image = new Image()
    image.onload = () => {
      const width = image.naturalWidth, height = image.naturalHeight
      if (!width || !height || width * height > 16_000_000) { reject(new Error('图片过大，请裁剪后重试')); return }
      const scale = Math.min(1, 1024 / Math.max(width, height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.max(1, Math.round(width * scale))
      canvas.height = Math.max(1, Math.round(height * scale))
      const context = canvas.getContext('2d', { willReadFrequently: true })
      if (!context) { reject(new Error('浏览器无法读取图片，请输入工单号')); return }
      try {
        context.drawImage(image, 0, 0, canvas.width, canvas.height)
        decodeQrPixels(context.getImageData(0, 0, canvas.width, canvas.height)).then(resolve, reject)
      } catch { reject(new Error('图片读取失败，请重新选择')) }
    }
    image.onerror = () => reject(new Error('图片读取失败，请重新选择'))
    image.src = url
  })
}
