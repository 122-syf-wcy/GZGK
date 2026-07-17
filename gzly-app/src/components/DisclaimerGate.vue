<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { SITE_DISCLAIMER_VERSION, SHORT_DISCLAIMER, DISCLAIMER_POINTS, LIABILITY_NOTICE } from '@/constants/disclaimer'
import { getDisclaimerAcceptedVersion, setDisclaimerAccepted } from '@/utils/storage'

const visible = ref(false)
const step = ref<1 | 2 | 3>(1)
const agreed = ref(false)

onMounted(() => {
  if (getDisclaimerAcceptedVersion() !== SITE_DISCLAIMER_VERSION) {
    visible.value = true
    step.value = 1
    agreed.value = false
  }
})

function agree() {
  if (!agreed.value) {
    uni.showToast({ title: '请先勾选「我已阅读并同意」', icon: 'none' })
    return
  }
  setDisclaimerAccepted(SITE_DISCLAIMER_VERSION)
  visible.value = false
}
function disagree() {
  step.value = 2
}
function reconsider() {
  step.value = 1
}
function openLegal() {
  uni.navigateTo({ url: '/pages/legal/legal' })
}
function quit() {
  // #ifdef APP-PLUS
  plus.runtime.quit()
  // #endif
  // #ifndef APP-PLUS
  step.value = 3
  // #endif
}
</script>

<template>
  <view v-if="visible" class="mask">
    <view class="dlg">
      <!-- 主页：用户须知 -->
      <template v-if="step === 1">
        <view class="dlg-title">用户须知与免责声明</view>
        <view class="dlg-sub">请在使用前阅读并同意以下条款</view>
        <scroll-view scroll-y class="dlg-body">
          <text class="lead">{{ SHORT_DISCLAIMER }}</text>
          <view class="liability">
            <text class="liability-h">责任限制（请重点阅读）</text>
            <text class="liability-t">{{ LIABILITY_NOTICE }}</text>
          </view>
          <view v-for="(p, i) in DISCLAIMER_POINTS" :key="i" class="pt">
            <text class="pt-n">{{ i + 1 }}</text>
            <text class="pt-t">{{ p }}</text>
          </view>
          <view class="block">
            <text class="block-h">隐私与数据</text>
            <text class="block-t">本工具为公益项目，不强制注册登录；志愿方案以你设置的「安全码」在本机保护，AI 问答以匿名「对话码」进行，不收集与高考无关的个人敏感信息。</text>
          </view>
          <view class="link" hover-class="h" @click="openLegal">查看完整《用户协议与免责声明》《隐私政策》›</view>
          <text class="ver">协议版本：{{ SITE_DISCLAIMER_VERSION }}</text>
        </scroll-view>
        <view class="confirm" @click="agreed = !agreed">
          <view class="cb" :class="{ on: agreed }"><text v-if="agreed" class="cb-ok">✓</text></view>
          <text class="cb-t">我已逐条阅读并同意《用户协议与免责声明》和《隐私政策》</text>
        </view>
        <view class="acts">
          <view class="btn ghost" hover-class="h" @click="disagree">不同意</view>
          <view class="btn primary" :class="{ disabled: !agreed }" hover-class="h" @click="agree">同意并继续</view>
        </view>
      </template>

      <!-- 二次确认 -->
      <template v-else-if="step === 2">
        <view class="dlg-title">温馨提示</view>
        <view class="dlg-body small">
          <text class="lead">需要同意《用户须知与免责声明》后才能使用本公益工具。要不要再看看？</text>
        </view>
        <view class="acts">
          <view class="btn ghost" hover-class="h" @click="quit">仍要退出</view>
          <view class="btn primary" hover-class="h" @click="reconsider">再想想</view>
        </view>
      </template>

      <!-- 退出（H5 无法关闭浏览器时的兜底说明） -->
      <template v-else>
        <view class="dlg-title">已退出</view>
        <view class="dlg-body small">
          <text class="lead">你未同意用户须知，已退出。如需使用本工具，请重新查看并同意。</text>
        </view>
        <view class="acts">
          <view class="btn primary" hover-class="h" @click="reconsider">重新查看须知</view>
        </view>
      </template>
    </view>
  </view>
</template>

<style scoped lang="scss">
.mask {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(15, 23, 42, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
}
.dlg {
  width: 100%;
  max-width: 620rpx;
  background: $gz-card;
  border-radius: 28rpx;
  padding: 36rpx 32rpx 28rpx;
  box-shadow: 0 20rpx 60rpx rgba(15, 23, 42, 0.3);
}
.dlg-title {
  font-size: 34rpx;
  font-weight: 800;
  color: $gz-text;
  text-align: center;
}
.dlg-sub {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: $gz-text-weak;
  text-align: center;
}
.dlg-body {
  margin-top: 22rpx;
  max-height: 760rpx;
}
.dlg-body.small {
  margin-top: 18rpx;
}
.lead {
  display: block;
  font-size: 25rpx;
  color: $gz-text-sub;
  line-height: 1.8;
}
.pt {
  display: flex;
  gap: 14rpx;
  margin-top: 18rpx;
}
.pt-n {
  flex: none;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: $gz-primary-50;
  color: $gz-primary;
  font-size: 22rpx;
  font-weight: 800;
  text-align: center;
  line-height: 36rpx;
}
.pt-t {
  flex: 1;
  font-size: 24rpx;
  color: $gz-text-sub;
  line-height: 1.75;
}
.liability {
  margin-top: 18rpx;
  padding: 18rpx 20rpx;
  background: $gz-warn-bg;
  border: 1rpx solid $gz-warn-border;
  border-radius: 16rpx;
}
.liability-h {
  display: block;
  font-size: 24rpx;
  font-weight: 800;
  color: $gz-warn;
  margin-bottom: 8rpx;
}
.liability-t {
  display: block;
  font-size: 23rpx;
  color: $gz-warn;
  line-height: 1.8;
}
.link {
  margin-top: 20rpx;
  font-size: 23rpx;
  font-weight: 600;
  color: $gz-primary;
}
.block {
  margin-top: 24rpx;
  padding: 18rpx 20rpx;
  background: $gz-bg-subtle;
  border-radius: 16rpx;
}
.block-h {
  display: block;
  font-size: 24rpx;
  font-weight: 800;
  color: $gz-text;
  margin-bottom: 8rpx;
}
.block-t {
  display: block;
  font-size: 23rpx;
  color: $gz-text-sub;
  line-height: 1.75;
}
.ver {
  display: block;
  margin-top: 22rpx;
  font-size: 21rpx;
  color: $gz-text-weak;
}
.confirm {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  margin-top: 24rpx;
  padding: 16rpx 18rpx;
  background: $gz-primary-50;
  border-radius: 14rpx;
}
.cb {
  flex: none;
  width: 38rpx;
  height: 38rpx;
  border-radius: 8rpx;
  border: 2rpx solid $gz-primary;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cb.on {
  background: $gz-primary;
}
.cb-ok {
  color: #fff;
  font-size: 24rpx;
  font-weight: 800;
}
.cb-t {
  flex: 1;
  font-size: 23rpx;
  color: $gz-text;
  line-height: 1.6;
}
.acts {
  display: flex;
  gap: 18rpx;
  margin-top: 22rpx;
}
.btn.disabled {
  opacity: 0.45;
}
.btn {
  flex: 1;
  height: 84rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
}
.btn.primary {
  background: $gz-primary;
  color: #fff;
}
.btn.ghost {
  background: $gz-bg-subtle;
  color: $gz-text-sub;
  border: 1rpx solid $gz-border;
}
.h {
  opacity: 0.85;
}
</style>
