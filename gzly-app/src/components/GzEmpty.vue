<script setup lang="ts">
/** 空态 / 失败态。失败态必须能重试，不能只给一句"加载失败"。 */
import GzButton from './GzButton.vue'

withDefaults(defineProps<{
  title?: string
  desc?: string
  /** 给了就显示重试按钮 */
  retryText?: string
}>(), { title: '暂无数据', desc: '', retryText: '' })

const emit = defineEmits<{ (e: 'retry'): void }>()
</script>

<template>
  <view class="empty">
    <view class="empty__mark" />
    <text class="empty__title">{{ title }}</text>
    <text v-if="desc" class="empty__desc">{{ desc }}</text>
    <GzButton v-if="retryText" type="ghost" size="sm" class="empty__btn" @tap="emit('retry')">
      {{ retryText }}
    </GzButton>
  </view>
</template>

<style lang="scss" scoped>
.empty {
  padding: $gz-space-8 $gz-space-4;
  display: flex;
  flex-direction: column;
  align-items: center;

  &__mark {
    width: 96rpx;
    height: 96rpx;
    border-radius: var(--gz-radius-full);
    background: var(--gz-surface-2);
    border: 2rpx solid var(--gz-border);
    margin-bottom: $gz-space-3;
  }

  &__title {
    font-size: $gz-text-body;
    color: var(--gz-text-2);
  }

  &__desc {
    margin-top: $gz-space-1;
    max-width: 520rpx;
    text-align: center;
    font-size: $gz-text-sm;
    color: var(--gz-text-3);
    line-height: 1.7;
  }

  &__btn {
    margin-top: $gz-space-3;
  }
}
</style>
