<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'

onLaunch(() => {
  const token = uni.getStorageSync('token')
  if (!token) {
    uni.reLaunch({ url: '/pages/bind/index' })
  }
})
</script>

<style>
html,
body,
#app {
  width: 100%;
  min-height: 100%;
  margin: 0;
  background: #f3f5f5;
  overflow-x: hidden;
}

page {
  min-height: 100%;
  background: #f3f5f5;
  color: #202926;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
}

view,
scroll-view,
swiper,
swiper-item {
  display: block;
  box-sizing: border-box;
}

text {
  box-sizing: border-box;
}

input,
textarea,
button {
  box-sizing: border-box;
  font: inherit;
}

button {
  border: 0;
  padding: 0;
}

button::after {
  border: 0;
}

.page {
  min-height: 100vh;
  padding: 24rpx;
  box-sizing: border-box;
}

.card {
  background: #fff;
  border-radius: 14rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.04);
}

.green {
  color: #00a982;
}

.primary {
  background: #00b889;
  color: #fff;
}

.ghost {
  background: #fff;
  color: #00a982;
  border: 1rpx solid #9edbd0;
}

.muted {
  color: #8a9693;
}
</style>
