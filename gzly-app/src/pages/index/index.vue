<script setup lang="ts">
import { PROVINCES } from '@/constants/provinces'
import DisclaimerGate from '@/components/DisclaimerGate.vue'
import heroBg from '@/static/hero-bg.png'

interface Feature {
  key: string
  label: string
  desc: string
  kind: 'tab' | 'page'
  url: string
}

const features: Feature[] = [
  { key: 'university', label: '院校查询', desc: '全国院校信息一键查询，985/211/双一流分类筛选', kind: 'tab', url: '/pages/university/university' },
  { key: 'scoreline', label: '历年分数线', desc: '历年投档线与位次数据，精准参考', kind: 'tab', url: '/pages/scoreline/scoreline' },
  { key: 'volunteer', label: '智能填报', desc: '一键生成平行志愿，冲 / 稳 / 保科学分配', kind: 'page', url: '/pages/volunteer/volunteer' },
  { key: 'ai', label: 'AI 解读', desc: '对志愿方案做结构化深度参考分析', kind: 'page', url: '/pages/ai-analysis/ai-analysis' },
  { key: 'aiqa', label: 'AI 问答', desc: '未上线地区政策方向多轮问答', kind: 'page', url: '/pages/ai-chat/ai-chat' },
  { key: 'planner', label: '专业规划师', desc: '兴趣 / 学科 / 性格匹配专业方向', kind: 'page', url: '/pages/major-planner/major-planner' },
  { key: 'special', label: '特殊招生', desc: '强基 / 综评 / 专项等政策线索', kind: 'page', url: '/pages/special-admissions/special-admissions' },
  { key: 'encouragement', label: '考生加油墙', desc: '写下并查看考生互相鼓励', kind: 'page', url: '/pages/encouragement/encouragement' },
]

const steps = [
  { n: 1, title: '查询信息', desc: '免费浏览全国院校信息与历年投档数据' },
  { n: 2, title: '输入成绩', desc: '填写总分 / 全省排位 / 首选再选科目' },
  { n: 3, title: '确认提交', desc: '阅读免责声明并确认，一键提交' },
  { n: 4, title: '生成方案', desc: '自动匹配冲 / 稳 / 保 / 垫梯度方案' },
]

const ICONS: Record<string, string> = {
  university: '<path d="M3 21h18"/><path d="M5 21V9l7-4 7 4v12"/><path d="M9.5 21v-6h5v6"/><path d="M9 11h.01M15 11h.01"/>',
  scoreline: '<path d="M3 3v18h18"/><rect x="7" y="11" width="3" height="6" rx="0.5"/><rect x="12" y="7" width="3" height="10" rx="0.5"/><rect x="17" y="13" width="3" height="4" rx="0.5"/>',
  volunteer: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h4"/>',
  ai: '<path d="M12 3l1.7 4.6L18.3 9l-4.6 1.4L12 15l-1.7-4.6L5.7 9l4.6-1.4z"/>',
  aiqa: '<path d="M21 11.5a8.4 8.4 0 0 1-12.7 7.2L3 20l1.3-5A8.4 8.4 0 1 1 21 11.5z"/><path d="M8.5 11.5h7M8.5 8.5h5"/>',
  planner: '<circle cx="12" cy="12" r="9"/><path d="M15.6 8.4l-2 5.2-5.2 2 2-5.2z"/>',
  special: '<circle cx="12" cy="8" r="5"/><path d="M8.5 12.5L7 22l5-3 5 3-1.5-9.5"/>',
  encouragement: '<path d="M12 20s-7-4.3-9.3-8.5C1 8 3 5 6 5c2 0 3.2 1.2 4 2.3C10.8 6.2 12 5 14 5c3 0 5 3 3.3 6.5C19 15.7 12 20 12 20z"/>',
}
function iconSvg(key: string): string {
  const inner = ICONS[key] || ''
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#1d4ed8" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`
  return 'data:image/svg+xml,' + encodeURIComponent(svg)
}

function openFeature(f: Feature) {
  if (f.kind === 'tab') uni.switchTab({ url: f.url })
  else uni.navigateTo({ url: f.url })
}
function goVolunteer() {
  uni.navigateTo({ url: '/pages/volunteer/volunteer' })
}
function goRegion(code: string) {
  uni.navigateTo({ url: `/pages/region/region?provinceCode=${code}` })
}
</script>

<template>
  <view class="home">
    <view class="topbar">数据来源于公开渠道，方案仅供参考，请以各省考试院官方数据为准</view>

    <view class="hero">
      <image class="hero-bg" :src="heroBg" mode="aspectFill" />
      <view class="hero-mask" />
      <view class="hero-content">
        <view class="hero-badge">公益高考志愿辅助</view>
        <view class="hero-title">高考志愿助手</view>
        <view class="hero-sub">基于新高考多省口径 · 院校 / 分数线 / 志愿 / AI 解读</view>
        <view class="hero-stat">
          <text class="hero-num">96</text>
          <view class="hero-stat-r">
            <text class="hero-stat-1">个平行志愿</text>
            <text class="hero-stat-2">智能生成参考</text>
          </view>
        </view>
        <view class="hero-cta" hover-class="cta-hover" @click="goVolunteer">开始填报 →</view>
      </view>
    </view>

    <view class="section">
      <view class="sec-title">省份专区</view>
      <view class="prov-grid">
        <view v-for="p in PROVINCES" :key="p.code" class="prov-chip" hover-class="prov-hover" @click="goRegion(p.code)">
          <text class="prov-name">{{ p.name }}</text>
          <text class="prov-mode">{{ p.workspaceMode === 'full' ? '完整' : '查询' }}</text>
        </view>
      </view>
    </view>

    <view class="section">
      <view class="sec-title">核心功能</view>
      <view class="grid">
        <view v-for="f in features" :key="f.key" class="card fcard" hover-class="fcard-hover" @click="openFeature(f)">
          <text class="free-tag">免费</text>
          <view class="f-tile">
            <image class="f-ic" :src="iconSvg(f.key)" />
          </view>
          <view class="f-name">{{ f.label }}</view>
          <view class="f-desc">{{ f.desc }}</view>
        </view>
      </view>
    </view>

    <view class="section">
      <view class="sec-title">使用流程</view>
      <view class="grid">
        <view v-for="s in steps" :key="s.n" class="card step">
          <text class="step-n">{{ s.n }}</text>
          <view class="step-title">{{ s.title }}</view>
          <view class="step-desc">{{ s.desc }}</view>
        </view>
      </view>
    </view>

    <view class="foot">数据仅供参考，请以各省招生考试院与高校官方材料为准，不构成录取预测。</view>

    <DisclaimerGate />
  </view>
</template>

<style scoped lang="scss">
.home {
  padding-bottom: 50rpx;
}
.topbar {
  background: $gz-warn-bg;
  border-bottom: 1rpx solid $gz-warn-border;
  color: $gz-warn;
  font-size: 22rpx;
  line-height: 1.5;
  text-align: center;
  padding: 14rpx 24rpx;
}

/* Hero */
.hero {
  position: relative;
  height: 470rpx;
  overflow: hidden;
}
.hero-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.hero-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(118deg, rgba(11, 22, 52, 0.9) 0%, rgba(13, 27, 62, 0.64) 50%, rgba(20, 40, 90, 0.4) 100%);
}
.hero-content {
  position: absolute;
  inset: 0;
  padding: 44rpx 40rpx;
  display: flex;
  flex-direction: column;
}
.hero-badge {
  align-self: flex-start;
  font-size: 22rpx;
  color: #fff;
  background: rgba(255, 255, 255, 0.16);
  border: 1rpx solid rgba(255, 255, 255, 0.3);
  border-radius: 999rpx;
  padding: 9rpx 24rpx;
}
.hero-title {
  margin-top: 26rpx;
  font-size: 60rpx;
  font-weight: 800;
  color: #fff;
  letter-spacing: 1rpx;
}
.hero-sub {
  margin-top: 14rpx;
  font-size: 25rpx;
  color: rgba(255, 255, 255, 0.86);
}
.hero-stat {
  margin-top: 24rpx;
  display: flex;
  align-items: center;
  gap: 18rpx;
}
.hero-num {
  font-size: 84rpx;
  font-weight: 800;
  color: #fff;
  line-height: 1;
}
.hero-stat-r {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.hero-stat-1 {
  font-size: 26rpx;
  color: #fff;
  font-weight: 600;
}
.hero-stat-2 {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.78);
}
.hero-cta {
  margin-top: auto;
  align-self: flex-start;
  font-size: 28rpx;
  font-weight: 700;
  color: #fff;
  background: rgba(255, 255, 255, 0.18);
  border: 1rpx solid rgba(255, 255, 255, 0.36);
  border-radius: 16rpx;
  padding: 18rpx 40rpx;
}
.cta-hover {
  background: rgba(255, 255, 255, 0.3);
}

/* Sections */
.section {
  padding: 30rpx 24rpx 0;
}
.sec-title {
  font-size: 36rpx;
  font-weight: 800;
  color: $gz-text;
  letter-spacing: -0.5rpx;
  margin-bottom: 22rpx;
}
.grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
}
.fcard {
  position: relative;
  width: calc(50% - 10rpx);
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  padding: 28rpx 24rpx;
}
.fcard-hover {
  transform: translateY(-2rpx);
}
.free-tag {
  position: absolute;
  top: 22rpx;
  right: 22rpx;
  font-size: 19rpx;
  color: $gz-success;
  background: rgba(16, 185, 129, 0.1);
  border-radius: 8rpx;
  padding: 3rpx 12rpx;
}
.f-tile {
  width: 84rpx;
  height: 84rpx;
  border-radius: 20rpx;
  background: $gz-primary-50;
  display: flex;
  align-items: center;
  justify-content: center;
}
.f-ic {
  width: 44rpx;
  height: 44rpx;
}
.f-name {
  margin-top: 20rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: $gz-text;
}
.f-desc {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: $gz-text-sub;
  line-height: 1.6;
}

/* Steps */
.step {
  width: calc(50% - 10rpx);
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  padding: 26rpx 24rpx;
}
.step-n {
  display: inline-block;
  width: 46rpx;
  height: 46rpx;
  border-radius: 12rpx;
  background: $gz-primary-50;
  color: $gz-primary;
  font-size: 24rpx;
  font-weight: 800;
  text-align: center;
  line-height: 46rpx;
}
.step-title {
  margin-top: 16rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: $gz-text;
}
.step-desc {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $gz-text-sub;
  line-height: 1.6;
}

.prov-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.prov-chip {
  width: calc(25% - 12rpx);
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: 16rpx;
  padding: 20rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}
.prov-hover {
  background: #f0f4ff;
}
.prov-name {
  font-size: 27rpx;
  font-weight: 700;
  color: $gz-text;
}
.prov-mode {
  font-size: 19rpx;
  color: $gz-text-weak;
}
.foot {
  margin-top: 38rpx;
  padding: 0 40rpx;
  font-size: 22rpx;
  color: $gz-text-weak;
  text-align: center;
  line-height: 1.7;
}
</style>
