<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import FloatingTabBar from '@/components/FloatingTabBar.vue'
import GzButton from '@/components/GzButton.vue'
import GzEmpty from '@/components/GzEmpty.vue'
import GzSheet from '@/components/GzSheet.vue'
import GzTag from '@/components/GzTag.vue'
import { getCloudPlan, listCloudPlans, deleteAccount, type CloudPlanItem } from '@/api/auth'
import { submitFeedback } from '@/api/query'
import { DISCLAIMER_UPDATED_AT, DISCLAIMER_VERSION } from '@/constants/compliance'
import { getProvinceText } from '@/constants/provinces'
import { useAuthStore } from '@/stores/auth'
import { usePlanStore } from '@/stores/plan'
import { inputValue } from '@/utils/request'

const store = usePlanStore()
const auth = useAuthStore()
const archive = computed(() => store.archive)

// ---- 云端方案 ----
const cloudPlans = ref<CloudPlanItem[]>([])
const cloudLoading = ref(false)
const cloudOpening = ref(false)

async function loadCloudPlans() {
  if (!auth.isLoggedIn) {
    cloudPlans.value = []
    return
  }
  cloudLoading.value = true
  try {
    const res = await listCloudPlans(1, 20)
    cloudPlans.value = res.items
  } catch {
    // 401 时请求层已清会话；其余失败静默，云端列表非关键路径
    cloudPlans.value = []
  } finally {
    cloudLoading.value = false
  }
}

/** 云端方案走 token 鉴权拉全量，落回本地后进结果页 */
async function openCloudPlan(item: CloudPlanItem) {
  if (cloudOpening.value) return
  cloudOpening.value = true
  uni.showLoading({ title: '正在拉取方案', mask: true })
  try {
    const plan = await getCloudPlan(item.id)
    store.setPlan(plan)
    uni.hideLoading()
    uni.navigateTo({ url: `/pages/volunteer/result?planId=${plan.id}` })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e as Error).message || '拉取失败', icon: 'none' })
  } finally {
    cloudOpening.value = false
  }
}

function goLogin() {
  uni.navigateTo({ url: '/pages/me/login' })
}

function logout() {
  uni.showModal({
    title: '退出登录',
    content: '退出后本机存档仍然保留，云端方案下次登录同一邮箱可再次查看。',
    confirmText: '退出',
    success: (res) => {
      if (!res.confirm) return
      auth.clearSession()
      cloudPlans.value = []
      uni.showToast({ title: '已退出登录', icon: 'none' })
    },
  })
}

/** 注销：双重确认，成功后清会话。后端会把账号信息立即匿名化。 */
function destroyAccount() {
  uni.showModal({
    title: '注销账号',
    content: '注销后账号信息立即匿名化且无法恢复，云端绑定关系一并解除；本机存档的方案与安全码不受影响。确定继续吗？',
    confirmText: '继续注销',
    confirmColor: '#DC2626',
    success: (first) => {
      if (!first.confirm) return
      uni.showModal({
        title: '最后确认',
        content: '这是最后一步，注销后无法撤销。',
        confirmText: '确认注销',
        confirmColor: '#DC2626',
        success: async (second) => {
          if (!second.confirm) return
          try {
            await deleteAccount()
            auth.clearSession()
            cloudPlans.value = []
            uni.showToast({ title: '账号已注销', icon: 'none' })
          } catch (e) {
            uni.showToast({ title: (e as Error).message || '注销失败', icon: 'none' })
          }
        },
      })
    },
  })
}

const LINKS = [
  { key: 'claim', title: '用安全码找回方案', desc: '换机或清理数据后从这里恢复', url: '/pages/me/claim' },
  { key: 'encourage', title: '考生加油墙', desc: '看看其他人写下的话', url: '/pages/me/encouragement' },
  { key: 'feedback', title: '问题反馈', desc: '哪里不好用、数据有误都告诉我们', action: 'feedback' },
  { key: 'disclaimer', title: '免责声明与使用边界', desc: `版本 ${DISCLAIMER_VERSION}`, url: '/pages/me/disclaimer' },
]

// ---- 反馈 ----
const feedbackVisible = ref(false)
const feedbackText = ref('')
const feedbackSending = ref(false)

const feedbackReady = computed(() => feedbackText.value.trim().length >= 10)

function onFeedbackInput(e: unknown) {
  feedbackText.value = inputValue(e)
}

async function sendFeedback() {
  if (!feedbackReady.value || feedbackSending.value) return
  feedbackSending.value = true
  try {
    await submitFeedback(feedbackText.value.trim(), 'app/me')
    feedbackVisible.value = false
    feedbackText.value = ''
    uni.showToast({ title: '已收到，谢谢反馈', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: (e as Error).message || '提交失败', icon: 'none' })
  } finally {
    feedbackSending.value = false
  }
}

function openPlan(planId: number) {
  uni.navigateTo({ url: `/pages/volunteer/result?planId=${planId}` })
}

function copyCode(code: string) {
  uni.setClipboardData({
    data: code,
    success: () => uni.showToast({ title: '安全码已复制', icon: 'none' }),
  })
}

function removePlan(planId: number) {
  uni.showModal({
    title: '删除本机记录',
    content: '只会删除本机保存的凭证。如果没有别处备份安全码，这份方案将无法找回。',
    confirmText: '仍然删除',
    confirmColor: '#DC2626',
    success: (res) => {
      if (res.confirm) store.removeArchived(planId)
    },
  })
}

function openLink(link: { url?: string; action?: string }) {
  if (link.action === 'feedback') {
    feedbackVisible.value = true
    return
  }
  if (link.url) uni.navigateTo({ url: link.url })
}

onShow(() => {
  // 登录页返回后刷新登录态视图与云端列表
  loadCloudPlans()
})
</script>

<template>
  <view class="page">
    <view class="head">
      <text class="head__title">我的</text>
      <text class="head__desc">
        {{ auth.isLoggedIn ? '方案已支持云端同步，换机登录同一邮箱即可找回。' : '不登录也能用：方案凭安全码保存在本机。登录后可云端同步，换机不丢。' }}
      </text>
    </view>

    <!-- 账号 -->
    <view v-if="!auth.isLoggedIn" class="account account--guest" @tap="goLogin">
      <view class="account__main">
        <text class="account__title">登录 / 注册</text>
        <text class="account__desc">邮箱验证码登录，本机方案自动绑定到账号</text>
      </view>
      <text class="account__go">›</text>
    </view>

    <view v-else class="account">
      <view class="account__main">
        <view class="account__row">
          <text class="account__title">{{ auth.nickname }}</text>
          <GzTag tone="ok">已登录</GzTag>
        </view>
        <text class="account__desc">{{ auth.email }}</text>
      </view>
      <text class="account__logout" @tap="logout">退出</text>
    </view>

    <!-- 云端方案 -->
    <view v-if="auth.isLoggedIn" class="sec">
      <view class="sec__head">
        <text class="sec__title">云端方案</text>
        <GzTag>凭账号访问</GzTag>
      </view>

      <GzEmpty
        v-if="!cloudLoading && !cloudPlans.length"
        title="云端还没有方案"
        desc="生成新方案会自动绑定到账号；旧方案可在「安全码找回」后自动同步。"
      />

      <view v-for="p in cloudPlans" :key="p.id" class="acard" @tap="openCloudPlan(p)">
        <view class="acard__top">
          <text class="acard__prov">{{ getProvinceText(p.provinceCode).shortName }}</text>
          <text class="acard__count">{{ p.itemCount }} 条志愿</text>
        </view>
        <text class="acard__meta">{{ p.totalScore }} 分 · 位次 {{ p.provinceRank }} · {{ p.createdAt }}</text>
      </view>
    </view>

    <view class="sec">
      <view class="sec__head">
        <text class="sec__title">方案存档</text>
        <GzTag>最多 5 份</GzTag>
      </view>

      <GzEmpty
        v-if="!archive.length"
        title="还没有生成过方案"
        desc="在「志愿」里填写成绩与选科后生成，方案会自动存在这里。"
      />

      <view v-for="a in archive" :key="a.planId" class="acard">
        <view class="acard__main" @tap="openPlan(a.planId)">
          <view class="acard__top">
            <text class="acard__prov">{{ a.provinceName }}</text>
            <text class="acard__count">{{ a.itemCount }} 条志愿</text>
          </view>
          <text class="acard__meta">{{ a.totalScore }} 分 · 位次 {{ a.provinceRank }} · {{ a.createdAt }}</text>
        </view>
        <view class="acard__foot">
          <view class="acard__code" @tap="copyCode(a.safetyCode)">
            <text class="acard__code-label">安全码</text>
            <text class="acard__code-value">{{ a.safetyCode }}</text>
            <text class="acard__code-copy">复制</text>
          </view>
          <text class="acard__del" @tap="removePlan(a.planId)">删除</text>
        </view>
      </view>

      <view v-if="archive.length" class="tip">
        <text class="tip__text">
          安全码是找回方案的唯一凭证，建议复制后另存一份。系统不会通过其他方式帮你恢复。
        </text>
      </view>
    </view>

    <view class="sec">
      <view v-for="l in LINKS" :key="l.key" class="row" @tap="openLink(l)">
        <view class="row__main">
          <text class="row__title">{{ l.title }}</text>
          <text class="row__desc">{{ l.desc }}</text>
        </view>
        <text class="row__go">›</text>
      </view>
    </view>

    <view v-if="auth.isLoggedIn" class="danger" @tap="destroyAccount">
      <text class="danger__text">注销账号</text>
    </view>

    <text class="ver">免责声明版本 {{ DISCLAIMER_VERSION }} · 更新于 {{ DISCLAIMER_UPDATED_AT }}</text>

    <GzSheet :visible="feedbackVisible" title="问题反馈" :max-ratio="0.6" @close="feedbackVisible = false">
      <textarea
        class="fb__area"
        :value="feedbackText"
        placeholder="至少 10 个字。请不要留下手机号等个人联系方式。"
        placeholder-class="fb__ph"
        :maxlength="500"
        @input="onFeedbackInput"
      />
      <text class="fb__count">{{ feedbackText.length }}/500</text>
      <template #footer>
        <GzButton block :disabled="!feedbackReady" :loading="feedbackSending" @tap="sendFeedback">
          提交反馈
        </GzButton>
      </template>
    </GzSheet>

    <FloatingTabBar />
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--gz-bg);
  padding: calc(#{$gz-space-5} + env(safe-area-inset-top)) $gz-page-x calc(200rpx + env(safe-area-inset-bottom));
}

.head {
  margin-bottom: $gz-space-5;

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

.account {
  @include gz-card;
  @include gz-pressable;
  display: flex;
  align-items: center;
  padding: $gz-space-4 $gz-space-3;
  margin-bottom: $gz-space-5;

  &--guest {
    background: var(--gz-ink);
    border-color: var(--gz-ink);
  }

  &--guest .account__title,
  &--guest .account__go {
    color: var(--gz-text-inverse);
  }

  &--guest .account__desc {
    color: rgba(255, 255, 255, 0.66);
  }

  &__main {
    flex: 1;
    min-width: 0;
  }

  &__row {
    display: flex;
    align-items: center;
  }

  &__row .account__title {
    margin-right: 14rpx;
  }

  &__title {
    font-family: var(--gz-font-serif);
    font-size: $gz-text-section;
    color: var(--gz-text);
  }

  &__desc {
    display: block;
    margin-top: 6rpx;
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
    @include gz-ellipsis;
  }

  &__go {
    font-size: 40rpx;
    color: var(--gz-text-3);
    margin-left: $gz-space-2;
  }

  &__logout {
    flex-shrink: 0;
    margin-left: $gz-space-2;
    padding: 10rpx 26rpx;
    border-radius: var(--gz-radius-full);
    border: 2rpx solid var(--gz-border-strong);
    font-size: $gz-text-xs;
    color: var(--gz-text-2);
  }
}

.danger {
  margin-bottom: $gz-space-4;
  padding: $gz-space-3;
  text-align: center;

  &__text {
    font-size: $gz-text-sm;
    color: var(--gz-danger);
  }
}

.sec {
  margin-bottom: $gz-space-5;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $gz-space-3;
  }

  &__title {
    font-family: var(--gz-font-serif);
    font-size: $gz-text-title;
    color: var(--gz-text);
  }
}

.acard {
  @include gz-card;
  padding: $gz-space-3;
  margin-bottom: $gz-space-2;

  &__top {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
  }

  &__prov {
    font-size: $gz-text-section;
    color: var(--gz-text);
  }

  &__count {
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
  }

  &__meta {
    display: block;
    margin-top: 6rpx;
    font-size: 20rpx;
    color: var(--gz-text-3);
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: $gz-space-3;
    padding-top: $gz-space-2;
    border-top: 2rpx solid var(--gz-border);
  }

  &__code {
    display: flex;
    align-items: center;
  }

  &__code-label {
    font-size: 20rpx;
    color: var(--gz-text-3);
    margin-right: 12rpx;
  }

  &__code-value {
    font-family: var(--gz-font-serif);
    font-size: $gz-text-sm;
    color: var(--gz-text);
    letter-spacing: 2rpx;
  }

  &__code-copy {
    font-size: 20rpx;
    color: var(--gz-wen);
    margin-left: 16rpx;
  }

  &__del {
    font-size: 20rpx;
    color: var(--gz-text-3);
  }
}

.tip {
  margin-top: $gz-space-2;
  padding: $gz-space-3;
  border-radius: var(--gz-radius-md);
  background: rgba(217, 119, 6, 0.08);

  &__text {
    font-size: 20rpx;
    color: var(--gz-text-2);
    line-height: 1.8;
  }
}

.row {
  @include gz-card;
  @include gz-pressable;
  display: flex;
  align-items: center;
  padding: $gz-space-3;
  margin-bottom: $gz-space-2;

  &__main {
    flex: 1;
    min-width: 0;
  }

  &__title {
    display: block;
    font-size: $gz-text-body;
    color: var(--gz-text);
  }

  &__desc {
    display: block;
    margin-top: 4rpx;
    font-size: 20rpx;
    color: var(--gz-text-3);
  }

  &__go {
    font-size: 40rpx;
    color: var(--gz-text-3);
    margin-left: $gz-space-2;
  }
}

.ver {
  display: block;
  text-align: center;
  font-size: 20rpx;
  color: var(--gz-text-3);
}

.fb {
  &__area {
    width: 100%;
    height: 320rpx;
    padding: $gz-space-3;
    border-radius: var(--gz-radius-md);
    background: var(--gz-surface-2);
    font-size: $gz-text-sm;
    color: var(--gz-text);
    line-height: 1.7;
    box-sizing: border-box;
  }

  &__ph {
    color: var(--gz-text-3);
  }

  &__count {
    display: block;
    text-align: right;
    margin: 8rpx 0 $gz-space-3;
    font-size: 20rpx;
    color: var(--gz-text-3);
  }
}
</style>
