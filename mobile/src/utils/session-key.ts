export function mobileSessionKey() {
  const user = uni.getStorageSync('user')
  return JSON.stringify([uni.getStorageSync('token') || '', user?.factoryId, user?.id])
}
