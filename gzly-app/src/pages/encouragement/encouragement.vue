<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchEncouragementMessages, submitEncouragementMessage, type EncouragementMessage } from '@/api/encouragement'

const list = ref<EncouragementMessage[]>([])
const loading = ref(false)
const nickname = ref('')
const content = ref('')
const submitting = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await fetchEncouragementMessages(30)
  } catch (e) {
    uni.showToast({ title: (e as Error).message, icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function submit() {
  const c = content.value.trim()
  if (c.length < 4 || c.length > 100) {
    uni.showToast({ title: '留言需 4-100 字', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await submitEncouragementMessage({ nickname: nickname.value.trim() || undefined, content: c })
    content.value = ''
    uni.showToast({ title: '已送达，加油！', icon: 'none' })
    await load()
  } catch (e) {
    uni.showToast({ title: (e as Error).message, icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onLoad(() => load())
</script>

<template>
  <view class="page">
    <view class="card post">
      <view class="sec">写下你的加油</view>
      <input v-model="nickname" class="ri" placeholder="昵称（可选）" maxlength="20" />
      <textarea v-model="content" class="ta" placeholder="给自己或同届考生一句鼓励（4-100 字）" maxlength="100" />
      <view class="submit" :class="{ disabled: submitting }" hover-class="btn-hover" @click="submit">
        {{ submitting ? '发送中…' : '送上祝福' }}
      </view>
    </view>

    <view class="sec-title">大家的加油</view>
    <view v-if="loading" class="hint">加载中…</view>
    <view v-else-if="list.length === 0" class="hint">还没有留言，来当第一个</view>
    <view v-else class="list">
      <view v-for="m in list" :key="m.id" class="card msg">
        <view class="m-content">{{ m.content }}</view>
        <view class="m-foot">
          <text class="m-nick">{{ m.nickname || '匿名考生' }}</text>
          <text v-if="m.createdAt" class="m-time">{{ (m.createdAt || '').slice(0, 10) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: 20rpx 24rpx 60rpx;
}
.card {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
}
.post {
  padding: 26rpx;
  margin-bottom: 24rpx;
}
.sec {
  font-size: 28rpx;
  font-weight: 700;
  color: $gz-text;
  margin-bottom: 16rpx;
}
.ri {
  height: 72rpx;
  border: 1rpx solid $gz-border;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
  background: $gz-bg-subtle;
  margin-bottom: 14rpx;
}
.ta {
  width: 100%;
  height: 140rpx;
  border: 1rpx solid $gz-border;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  font-size: 26rpx;
  background: $gz-bg-subtle;
}
.submit {
  margin-top: 18rpx;
  height: 82rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.submit.disabled {
  opacity: 0.6;
}
.btn-hover {
  opacity: 0.85;
}
.sec-title {
  font-size: 32rpx;
  font-weight: 800;
  color: $gz-text;
  margin-bottom: 18rpx;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.msg {
  padding: 24rpx;
}
.m-content {
  font-size: 27rpx;
  color: $gz-text;
  line-height: 1.7;
}
.m-foot {
  margin-top: 14rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.m-nick {
  font-size: 23rpx;
  color: $gz-primary;
  font-weight: 600;
}
.m-time {
  font-size: 21rpx;
  color: $gz-text-weak;
}
.hint {
  margin-top: 30rpx;
  text-align: center;
  font-size: 24rpx;
  color: $gz-text-weak;
}
</style>
