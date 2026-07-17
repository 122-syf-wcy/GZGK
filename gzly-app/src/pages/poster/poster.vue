<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { exportPlanLongImage } from '@/api/volunteer'
import { getSafetyCode } from '@/utils/storage'

const planId = ref(0)
const img = ref('')
const loading = ref(false)
const errorMsg = ref('')
const needCode = ref(false)
const inputCode = ref('')

async function run(code: string) {
  loading.value = true
  errorMsg.value = ''
  try {
    img.value = await exportPlanLongImage(planId.value, code)
    needCode.value = false
  } catch (e) {
    errorMsg.value = (e as Error).message || '导出失败'
    if (!img.value) needCode.value = true
  } finally {
    loading.value = false
  }
}

function submitCode() {
  const c = inputCode.value.trim()
  if (!c) {
    uni.showToast({ title: '请输入安全码', icon: 'none' })
    return
  }
  run(c)
}

function savePoster() {
  if (!img.value) return
  // #ifdef H5
  const a = document.createElement('a')
  a.href = img.value
  a.download = `volunteer-${planId.value}.png`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  uni.showToast({ title: '已开始下载', icon: 'none' })
  // #endif
  // #ifndef H5
  const base64 = img.value.split(',')[1] || ''
  const userDataPath = (uni as unknown as { env?: { USER_DATA_PATH?: string } }).env?.USER_DATA_PATH || ''
  const path = `${userDataPath}/volunteer-${planId.value}.png`
  const fs = uni.getFileSystemManager()
  fs.writeFile({
    filePath: path,
    data: base64,
    encoding: 'base64',
    success: () => {
      uni.saveImageToPhotosAlbum({
        filePath: path,
        success: () => uni.showToast({ title: '已保存到相册', icon: 'none' }),
        fail: () => uni.showToast({ title: '保存失败，请检查相册权限', icon: 'none' }),
      })
    },
    fail: () => uni.showToast({ title: '保存失败', icon: 'none' }),
  })
  // #endif
}

onLoad((options) => {
  planId.value = Number(options?.id || 0)
  const code = getSafetyCode(planId.value)
  if (code) run(code)
  else needCode.value = true
})
</script>

<template>
  <view class="page">
    <view v-if="needCode && !img" class="card">
      <view class="sec">输入安全码导出志愿长图</view>
      <input v-model="inputCode" class="ri" placeholder="生成方案时设置的安全码" />
      <view class="btn" hover-class="btn-hover" @click="submitCode">生成长图</view>
      <view v-if="errorMsg" class="err">{{ errorMsg }}</view>
    </view>

    <view v-if="loading" class="loading">
      <text class="loading-t">正在生成志愿长图…</text>
      <text class="loading-s">服务端渲染，请稍候。</text>
    </view>

    <template v-if="img && !loading">
      <scroll-view scroll-y class="poster-wrap">
        <image class="poster" :src="img" mode="widthFix" />
      </scroll-view>
      <view class="save" hover-class="btn-hover" @click="savePoster">保存图片</view>
      <view class="tip">长图含完整志愿清单与免责声明；如保存失败，可长按图片保存。</view>
    </template>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: 24rpx;
}
.card {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  padding: 26rpx;
}
.sec {
  font-size: 28rpx;
  font-weight: 700;
  color: $gz-text;
  margin-bottom: 16rpx;
}
.ri {
  height: 76rpx;
  border: 1rpx solid $gz-border;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
  margin-bottom: 16rpx;
  background: $gz-bg-subtle;
}
.btn {
  height: 80rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn-hover {
  opacity: 0.85;
}
.err {
  margin-top: 14rpx;
  font-size: 23rpx;
  color: #d4380d;
}
.loading {
  margin-top: 80rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}
.loading-t {
  font-size: 28rpx;
  font-weight: 700;
  color: $gz-text;
}
.loading-s {
  font-size: 23rpx;
  color: $gz-text-weak;
}
.poster-wrap {
  max-height: 70vh;
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  overflow: hidden;
}
.poster {
  width: 100%;
}
.save {
  margin-top: 20rpx;
  height: 86rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 29rpx;
  font-weight: 700;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.tip {
  margin-top: 14rpx;
  font-size: 22rpx;
  color: $gz-text-weak;
  text-align: center;
  line-height: 1.6;
}
</style>
