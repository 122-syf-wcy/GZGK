<script setup lang="ts">
import { computed, ref } from 'vue'
import GzButton from '@/components/GzButton.vue'
import GzField from '@/components/GzField.vue'
import GzNavBar from '@/components/GzNavBar.vue'
import { fetchPlan } from '@/api/volunteer'
import { usePlanStore } from '@/stores/plan'

const store = usePlanStore()

const planIdText = ref('')
const safetyCode = ref('')
const submitting = ref(false)
const errorText = ref('')

const canSubmit = computed(() => Number(planIdText.value) > 0 && safetyCode.value.trim().length >= 4)

async function claim() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  errorText.value = ''
  try {
    // accessKey 与 safetyCode 二选一即可，后端两种凭证都接受
    const plan = await fetchPlan(Number(planIdText.value), safetyCode.value.trim(), '')
    store.setPlan(plan)
    uni.showToast({ title: '找回成功', icon: 'none' })
    setTimeout(() => uni.redirectTo({ url: `/pages/volunteer/result?planId=${plan.id}` }), 600)
  } catch (e) {
    errorText.value = (e as Error).message || '方案不存在或安全码不正确'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page">
    <GzNavBar title="安全码找回" />

    <view class="pad">
      <view class="head">
        <text class="head__title">用安全码找回</text>
        <text class="head__desc">
          方案的所有权凭证是生成时给出的方案编号与安全码，换机或清理数据后凭它们可以恢复。已登录账号的方案也可以直接在「我的 · 云端方案」查看。
        </text>
      </view>

      <view class="form">
        <GzField
          v-model="planIdText"
          label="方案编号"
          type="number"
          placeholder="生成时展示的数字编号"
        />
        <GzField
          v-model="safetyCode"
          label="安全码"
          placeholder="区分大小写"
          :error="errorText"
          class="gap"
        />

        <GzButton block :disabled="!canSubmit" :loading="submitting" class="gap-lg" @tap="claim">
          找回方案
        </GzButton>
      </view>

      <view class="warn">
        <text class="warn__title">找不到安全码怎么办</text>
        <text class="warn__text">
          安全码只在生成时展示并保存在本机，服务端不提供按手机号或邮箱找回的通道。如果本机记录已被清除且没有另存，只能重新填写成绩生成一份新方案。
        </text>
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
  padding: $gz-space-3 0 $gz-space-5;

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
    line-height: 1.7;
  }
}

.form {
  @include gz-card;
  padding: $gz-space-4 $gz-space-3;
}

.gap {
  margin-top: $gz-space-3;
}

.gap-lg {
  margin-top: $gz-space-5;
}

.warn {
  margin-top: $gz-space-4;
  padding: $gz-space-3;
  border-radius: var(--gz-radius-md);
  background: rgba(217, 119, 6, 0.08);

  &__title {
    display: block;
    font-size: $gz-text-sm;
    color: var(--gz-warn);
    margin-bottom: 8rpx;
  }

  &__text {
    display: block;
    font-size: 20rpx;
    color: var(--gz-text-2);
    line-height: 1.8;
  }
}
</style>
