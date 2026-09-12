<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Brain, ChevronDown } from 'lucide-vue-next'

/**
 * ChatGPT 式思考过程块：
 * - 思考中：脉冲动画 + 实时思考文本（或阶段轮播文案）+ 计时
 * - 思考结束：自动折叠为「已深度思考 · 用时 Xs」，可点击展开回看
 */
const props = withDefaults(defineProps<{
  /** 是否处于思考阶段（正文尚未开始输出） */
  active: boolean
  /** 模型真实思考文本（reasoning），可为空 */
  reasoning?: string
  /** 思考用时（毫秒），结束后由父组件结算 */
  durationMs?: number
  /** 无真实思考文本时的阶段轮播文案 */
  phases?: string[]
}>(), {
  reasoning: '',
  durationMs: 0,
  phases: () => [
    '正在通读你的志愿方案…',
    '正在核对历年位次与招生计划…',
    '正在评估梯度结构与风险点…',
    '正在组织解读结论…',
  ],
})

const expanded = ref(false)
const phaseIndex = ref(0)
const elapsedSeconds = ref(0)
const scrollRef = ref<HTMLDivElement | null>(null)
let phaseTimer: ReturnType<typeof setInterval> | null = null
let tickTimer: ReturnType<typeof setInterval> | null = null

const hasReasoning = computed(() => props.reasoning.trim().length > 0)
const doneSeconds = computed(() => Math.max(1, Math.round(props.durationMs / 1000)))
/** 结束后若既无思考文本也无耗时（如缓存命中），整块隐藏 */
const visible = computed(() => props.active || hasReasoning.value || props.durationMs > 0)

watch(() => props.active, (active) => {
  if (active) {
    elapsedSeconds.value = 0
    phaseIndex.value = 0
    expanded.value = false
    tickTimer = setInterval(() => { elapsedSeconds.value += 1 }, 1000)
    phaseTimer = setInterval(() => {
      phaseIndex.value = (phaseIndex.value + 1) % props.phases.length
    }, 2600)
  } else {
    stopTimers()
  }
}, { immediate: true })

watch(() => props.reasoning, () => {
  if (props.active && scrollRef.value) {
    requestAnimationFrame(() => {
      if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
    })
  }
})

function stopTimers() {
  if (tickTimer) { clearInterval(tickTimer); tickTimer = null }
  if (phaseTimer) { clearInterval(phaseTimer); phaseTimer = null }
}

onBeforeUnmount(stopTimers)
</script>

<template>
  <div v-if="visible" class="thinking-block" :class="{ 'is-active': active }">
    <button
      type="button"
      class="thinking-block__head"
      :disabled="active || !hasReasoning"
      @click="expanded = !expanded"
    >
      <span class="thinking-block__icon" :class="{ pulsing: active }">
        <Brain :size="14" />
      </span>
      <span class="thinking-block__label">
        <template v-if="active">正在深度思考… {{ elapsedSeconds }}s</template>
        <template v-else>已深度思考<template v-if="durationMs > 0"> · 用时 {{ doneSeconds }}s</template></template>
      </span>
      <span v-if="active" class="thinking-block__dots" aria-hidden="true">
        <i></i><i></i><i></i>
      </span>
      <ChevronDown
        v-else-if="hasReasoning"
        :size="14"
        class="thinking-block__chevron"
        :class="{ rotated: expanded }"
      />
    </button>

    <div v-if="active" ref="scrollRef" class="thinking-block__body thinking-block__body--live">
      <template v-if="hasReasoning">{{ reasoning }}</template>
      <template v-else>{{ phases[phaseIndex] }}</template>
    </div>
    <div v-else-if="expanded && hasReasoning" class="thinking-block__body">
      {{ reasoning }}
    </div>
  </div>
</template>

<style scoped>
.thinking-block {
  margin-bottom: 14px;
  border: 1px solid rgba(23, 24, 28, 0.1);
  border-radius: 14px;
  background: var(--gz-bg-subtle, #fbfaf8);
  overflow: hidden;
}

.thinking-block.is-active {
  border-color: rgba(23, 24, 28, 0.16);
}

.thinking-block__head {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 40px;
  padding: 0 14px;
  border: none;
  background: transparent;
  color: #4b4d54;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  text-align: left;
}

.thinking-block__head:disabled {
  cursor: default;
}

.thinking-block__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: rgba(23, 24, 28, 0.06);
  color: #4b4d54;
  flex-shrink: 0;
}

.thinking-block__icon.pulsing {
  animation: thinking-pulse 1.6s ease-in-out infinite;
}

.thinking-block__label {
  flex: 1;
  min-width: 0;
}

.thinking-block__dots {
  display: inline-flex;
  gap: 4px;
}

.thinking-block__dots i {
  width: 5px;
  height: 5px;
  border-radius: 999px;
  background: #8e9097;
  animation: thinking-bounce 1.2s ease-in-out infinite;
}

.thinking-block__dots i:nth-child(2) {
  animation-delay: 0.18s;
}

.thinking-block__dots i:nth-child(3) {
  animation-delay: 0.36s;
}

.thinking-block__chevron {
  color: #8e9097;
  transition: transform 0.2s ease;
}

.thinking-block__chevron.rotated {
  transform: rotate(180deg);
}

.thinking-block__body {
  max-height: 260px;
  overflow-y: auto;
  padding: 4px 14px 12px 46px;
  color: #6a6c72;
  font-size: 12px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.thinking-block__body--live {
  max-height: 132px;
}

@keyframes thinking-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(23, 24, 28, 0.14); }
  50% { box-shadow: 0 0 0 6px rgba(23, 24, 28, 0.04); }
}

@keyframes thinking-bounce {
  0%, 100% { transform: translateY(0); opacity: 0.5; }
  50% { transform: translateY(-3px); opacity: 1; }
}
</style>
