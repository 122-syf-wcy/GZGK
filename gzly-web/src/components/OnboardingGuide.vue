<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ChevronRight, MapPin, PenLine, Sparkles } from 'lucide-vue-next'

/** 首访引导只出现一次；版本号变更可重新触发。 */
const STORAGE_KEY = 'gz_onboarding_done_v1'

const visible = ref(false)
const step = ref(0)

const steps = [
  {
    icon: MapPin,
    title: '选择你的省份',
    desc: '从首页选择省份进入工作台，系统会按当地批次与志愿规则建模，例如贵州普通本科批为 96 个「专业（类）+ 院校」平行志愿。',
  },
  {
    icon: PenLine,
    title: '填 4 项就能生成',
    desc: '只需总分、首选科目和两门再选科目。位次不清楚也没关系，系统会用省考试院官方一分一段自动估算区间；填报策略与偏好已有均衡默认值，需要时再展开「高级偏好」调整。',
  },
  {
    icon: Sparkles,
    title: '看依据，再决策',
    desc: '每条志愿都标注历史位次依据与数据置信度，展开详情可查近三年录取记录和官方核验入口，还可以用 AI 解读和 Excel 导出辅助全家讨论。所有结果仅供参考，请以考试院与高校官方信息为准。',
  },
]

function next() {
  if (step.value < steps.length - 1) {
    step.value += 1
    return
  }
  finish()
}

function finish() {
  visible.value = false
  try {
    localStorage.setItem(STORAGE_KEY, '1')
  } catch {
    // 存储不可用时静默放弃，下次访问会再展示
  }
}

onMounted(() => {
  try {
    visible.value = !localStorage.getItem(STORAGE_KEY)
  } catch {
    visible.value = false
  }
})
</script>

<template>
  <transition name="onboarding-fade">
    <div v-if="visible" class="onboarding-overlay" role="dialog" aria-modal="true" aria-label="新手引导">
      <div class="onboarding-card">
        <div class="onboarding-card__icon">
          <component :is="steps[step].icon" :size="30" />
        </div>
        <div class="onboarding-card__step">第 {{ step + 1 }} 步 / 共 {{ steps.length }} 步</div>
        <h3 class="onboarding-card__title">{{ steps[step].title }}</h3>
        <p class="onboarding-card__desc">{{ steps[step].desc }}</p>
        <div class="onboarding-card__dots">
          <span
            v-for="(item, idx) in steps"
            :key="item.title"
            class="onboarding-card__dot"
            :class="{ active: idx === step }"
          ></span>
        </div>
        <div class="onboarding-card__actions">
          <button type="button" class="onboarding-skip" @click="finish">跳过</button>
          <button type="button" class="onboarding-next" @click="next">
            {{ step === steps.length - 1 ? '开始使用' : '下一步' }}
            <ChevronRight :size="15" />
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.onboarding-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(23, 24, 28, 0.55);
  backdrop-filter: blur(3px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.onboarding-card {
  width: 100%;
  max-width: 380px;
  background: #fff;
  border-radius: 20px;
  padding: 28px 24px 22px;
  text-align: center;
  box-shadow: 0 24px 48px rgba(23, 24, 28, 0.25);
}

.onboarding-card__icon {
  width: 64px;
  height: 64px;
  margin: 0 auto;
  border-radius: 999px;
  background: #17181c;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(23, 24, 28, 0.28);
}

.onboarding-card__step {
  margin-top: 14px;
  font-size: 11px;
  font-weight: 700;
  color: #97999e;
  letter-spacing: 0.06em;
}

.onboarding-card__title {
  margin-top: 6px;
  font-size: 19px;
  font-weight: 800;
  color: #17181c;
}

.onboarding-card__desc {
  margin-top: 10px;
  font-size: 13px;
  color: #4b4d54;
  line-height: 1.8;
  text-align: left;
}

.onboarding-card__dots {
  margin-top: 16px;
  display: flex;
  justify-content: center;
  gap: 6px;
}

.onboarding-card__dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #e3e2de;
  transition: all 0.2s ease;
}

.onboarding-card__dot.active {
  width: 20px;
  background: #17181c;
}

.onboarding-card__actions {
  margin-top: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.onboarding-skip {
  min-height: 42px;
  padding: 0 16px;
  border: none;
  background: transparent;
  color: #97999e;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.onboarding-next {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 42px;
  padding: 0 18px;
  border: none;
  border-radius: 999px;
  background: #17181c;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(23, 24, 28, 0.24);
}

.onboarding-fade-enter-active,
.onboarding-fade-leave-active {
  transition: opacity 0.25s ease;
}

.onboarding-fade-enter-from,
.onboarding-fade-leave-to {
  opacity: 0;
}
</style>
