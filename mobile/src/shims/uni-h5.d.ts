declare module '@dcloudio/uni-h5' {
  export const plugin: {
    install(app: unknown): void
  }
  export const UniServiceJSBridge: unknown
  export const uni: unknown
  export function getApp(options?: { allowDefault?: boolean }): unknown
  export function getCurrentPages(): Array<{ route: string; $page?: { options?: Record<string, string> } }>
  export function setupApp<T>(component: T): T
}
