declare module 'qrcode-reader' {
  export default class QrCode {
    callback: (error: unknown, result?: { result: string }) => void
    decode(bitmap: { width: number; height: number; data: Uint8ClampedArray | Uint8Array }): void
  }
}
