<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import GzButton from '@/components/GzButton.vue'
import GzEmpty from '@/components/GzEmpty.vue'
import GzField from '@/components/GzField.vue'
import GzNavBar from '@/components/GzNavBar.vue'
import GzSkeleton from '@/components/GzSkeleton.vue'
import { listEncouragement, postEncouragement, type EncouragementMessage } from '@/api/query'

const list = ref<EncouragementMessage[]>([])
const loading = ref(true)
const failed = ref(false)

const nickname = ref('')
const content = ref('')
const submitting = ref(false)
const errorText = ref('')

const canSubmit = computed(() => content.value.trim().length >= 4)

async function load() {
  loading.value = true
  failed.value = false
  try {
    list.value = await listEncouragement(20)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  errorText.value = ''
  try {
    const created = await postEncouragement(nickname.value.trim(), content.value.trim())
    list.value = [created, ...list.value]
    content.value = ''
    uni.showToast({ title: '已发布', icon: 'none' })
  } catch (e) {
    errorText.value = (e as Error).message || '发布失败'
  } finally {
    submitting.value = false
  }
}

onLoad(load)

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="page">
    <GzNavBar title="考生加油墙" />

    <view class="pad">
      <view class="head">
        <text class="head__title">写给同路人</text>
        <text class="head__desc">不留联系方式、不发广告。这里只留一句真心话。</text>
      </view>

      <view class="form">
        <GzField v-model="nickname" label="昵称（可留空）" placeholder="默认显示为「贵州考生」" :maxlength="12" />
        <GzField
          v-model="content"
          label="想说的话"
          placeholder="4 到 120 字"
          :maxlength="120"
          :error="errorText"
          class="gap"
        />
        <GzButton block :disabled="!canSubmit" :loading="submitting" class="gap" @tap="submit">
          发布
        </GzButton>
      </view>

      <GzSkeleton v-if="loading" :rows="3" />
      <GzEmpty v-else-if="failed" title="留言读取失败" retry-text="重试" @retry="load" />
      <GzEmpty v-else-if="!list.length" title="还没有留言" desc="来做第一个。" />

      <view v-for="m in list" :key="m.id" class="msg">
        <view class="msg__top">
          <text class="msg__nick">{{ m.nickname }}</text>
          <text class="msg__time">{{ m.createdAt }}</text>
        </view>
        <text class="msg__content">{{ m.content }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--gz-bg);
  padding-bottom: calc(80rpx + env(safe-area-inset-bottom));
}

.pad {
  padding: 0 $gz-page-x;
}

.head {
  padding: $gz-space-3 0 $gz-space-4;

  &__title {
    display: block;
    font-family: var(--gz-font-serif);
    font-size: 52rpx;
    color: var(--gz-text);
  }

  &__desc {
    display: block;
    margin-top: 10rpx;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
  }
}

.form {
  @include gz-card;
  padding: $gz-space-4 $gz-space-3;
  margin-bottom: $gz-space-4;
}

.gap {
  margin-top: $gz-space-3;
}

.msg {
  @include gz-card;
  padding: $gz-space-3;
  margin-bottom: $gz-space-2;
  box-shadow: none;
  background: var(--gz-surface-2);

  &__top {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
  }

  &__nick {
    font-size: $gz-text-sm;
    color: var(--gz-text);
  }

  &__time {
    font-size: 20rpx;
    color: var(--gz-text-3);
  }

  &__content {
    display: block;
    margin-top: 8rpx;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    line-height: 1.8;
  }
}
</style>
