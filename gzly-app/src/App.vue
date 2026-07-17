<script setup lang="ts">
import { onLaunch, onShow } from '@dcloudio/uni-app'

onLaunch(() => {
  // 首启入口：后续在此接入合规门禁、版本检查、登录态恢复等
})

onShow(() => {
  // 应用回到前台
})
</script>

<template>
  <!-- App.vue 的 <template> 需保持为空：仅承载全局逻辑与全局样式 -->
</template>

<style>
/* App.vue 的样式在 uni-app 中为全局样式；1:1 还原 web 的暖纸底 + 极淡编辑纹理 */
page {
  background-color: #f5f3ee;
  background-image:
    linear-gradient(rgba(15, 23, 42, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, 0.03) 1px, transparent 1px),
    radial-gradient(circle at 15% 12%, rgba(29, 78, 216, 0.05) 0%, transparent 26%),
    radial-gradient(circle at 82% 18%, rgba(180, 83, 9, 0.05) 0%, transparent 24%);
  background-size: 28px 28px, 28px 28px, auto, auto;
  color: #111827;
  font-size: 28rpx;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', 'Microsoft YaHei', sans-serif;
}

view,
text {
  box-sizing: border-box;
}

/* 全站统一卡片柔和阴影（对齐 web --gz-card-shadow） */
.card {
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
}
</style>
