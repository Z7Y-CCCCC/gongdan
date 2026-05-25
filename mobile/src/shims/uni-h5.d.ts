declare module '@dcloudio/uni-h5' {
  export const plugin: {
    install(app: unknown): void
  }
  export function setupApp<T>(component: T): T
}
