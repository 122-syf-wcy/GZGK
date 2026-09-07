<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'

/**
 * deeplink：gzly://plan?planId=123
 * 只接受 planId，凭证从本机存档取；取不到时结果页会引导用安全码找回。
 */
function handleDeeplink(raw?: string) {
  if (!raw || !raw.startsWith('gzly://')) return
  const match = /[?&]planId=(\d+)/.exec(raw)
  if (!match) return
  // 等首页栈初始化完成再跳，避免启动期 navigateTo 竞态
  setTimeout(() => {
    uni.navigateTo({ url: `/pages/volunteer/result?planId=${match[1]}` })
  }, 400)
}

onLaunch(() => {
  // #ifdef APP-PLUS
  handleDeeplink(plus.runtime.arguments as string)
  // globalEvent 未进 @dcloudio/types 的 Plus 声明，但运行时存在
  const plusGlobal = plus as unknown as {
    globalEvent: { addEventListener: (type: string, cb: () => void) => void }
  }
  plusGlobal.globalEvent.addEventListener('newintent', () => {
    handleDeeplink(plus.runtime.arguments as string)
  })
  // #endif
})
</script>

<style lang="scss">
/**
 * 全局 CSS 自定义属性，只在这里输出一次。
 * SCSS 变量与 mixin 在 uni.scss 中，由 uni-app 自动注入各组件。
 *
 * 视觉基调：明亮、开阔、有希望感。
 * 界面本身接近单色——白底、近黑文字、黑色胶囊按钮；颜色全部由摄影图片提供。
 * 这是参考图看起来干净的根本原因：UI 不和照片抢颜色。
 */
page {
  /* ---- 背景与表面 ---- */
  --gz-bg: #ffffff;
  --gz-surface: #ffffff;
  --gz-surface-2: #f5f7f8;
  --gz-surface-3: #eef1f4;
  --gz-border: rgba(16, 24, 40, 0.09);
  --gz-border-strong: rgba(16, 24, 40, 0.16);
  --gz-shadow: 0 8rpx 32rpx rgba(16, 24, 40, 0.06);
  --gz-shadow-lg: 0 24rpx 64rpx rgba(16, 24, 40, 0.1);

  /* ---- 文字 ---- */
  --gz-text: #0e1116;
  --gz-text-2: #5a6472;
  --gz-text-3: #98a2b3;
  --gz-text-inverse: #ffffff;

  /* ---- 主操作：近黑胶囊，参考图里的 CTA 一致 ---- */
  --gz-ink: #0e1116;

  /* ---- 状态色（浅底口径）---- */
  --gz-ok: #16a34a;
  --gz-warn: #d97706;
  --gz-danger: #dc2626;
  --gz-muted: #98a2b3;
  --gz-ok-bg: rgba(22, 163, 74, 0.09);
  --gz-warn-bg: rgba(217, 119, 6, 0.09);

  /* ---- 梯度色：冲/稳/保/垫 ---- */
  --gz-chong: #dc2626;
  --gz-wen: #2563eb;
  --gz-bao: #16a34a;
  --gz-dian: #7c3aed;

  /* ---- 字体：衬线只用于 hero 级大标题 ---- */
  --gz-font-sans: -apple-system, BlinkMacSystemFont, "PingFang SC", "HarmonyOS Sans SC",
    "Source Han Sans SC", "Noto Sans CJK SC", "Microsoft YaHei", sans-serif;
  --gz-font-serif: "Songti SC", "Source Han Serif SC", "Noto Serif CJK SC", Georgia, serif;

  /* ---- 圆角 ---- */
  --gz-radius-sm: 10rpx;
  --gz-radius-md: 18rpx;
  --gz-radius-lg: 28rpx;
  --gz-radius-xl: 40rpx;
  --gz-radius-full: 999rpx;

  background: var(--gz-bg);
  color: var(--gz-text);
  font-family: var(--gz-font-sans);
  font-size: 28rpx;
  line-height: 1.6;
}

view,
text,
scroll-view,
image {
  box-sizing: border-box;
}
</style>
