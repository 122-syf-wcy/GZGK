<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Eye, BarChart3 } from 'lucide-vue-next'
import { fetchOnlineStats, type OnlineStats } from '@/api/siteStats'

const POLL_INTERVAL_MS = 30_000

const stats = ref<OnlineStats | null>(null)
const errored = ref(false)

let timer: number | null = null
let mounted = false

const windowMinutes = computed(() => {
  const seconds = stats.value?.windowSeconds ?? 0
  if (!seconds) return 5
  return Math.max(1, Math.round(seconds / 60))
})

const totalViewsLabel = computed(() => {
  const value = stats.value?.totalViews
  if (typeof value !== 'number' || value <= 0) return null
  return value.toLocaleString()
})

const todayViewsLabel = computed(() => {
  const value = stats.value?.todayViews
  if (typeof value !== 'number' || value <= 0) return null
  return value.toLocaleString()
})

async function pull(): Promise<void> {
  try {
    const res = await fetchOnlineStats()
    const payload = res.data?.data
    if (!mounted) return
    if (payload && typeof payload.activeUsers === 'number') {
      stats.value = payload
      errored.value = false
    } else {
      errored.value = true
    }
  } catch {
    if (!mounted) return
    errored.value = true
  }
}

onMounted(() => {
  mounted = true
  pull()
  timer = window.setInterval(pull, POLL_INTERVAL_MS)
})

onUnmounted(() => {
  mounted = false
  if (timer != null) {
    window.clearInterval(timer)
    timer = null
  }
})
</script>

<template>
  <div v-if="stats && !errored" class="online-counter-stack" role="status">
    <div
      class="online-counter"
      :aria-label="`当前 ${stats.activeUsers} 位访客在浏览（最近 ${windowMinutes} 分钟）`"
    >
      <span class="online-counter__pulse" aria-hidden="true"></span>
      <Eye :size="14" />
      <span class="online-counter__count">{{ stats.activeUsers.toLocaleString() }}</span>
      <span class="online-counter__label">人正在浏览</span>
      <span class="online-counter__hint">近 {{ windowMinutes }} 分钟</span>
    </div>
    <div
      v-if="totalViewsLabel"
      class="online-counter online-counter--total"
      :aria-label="`累计 ${totalViewsLabel} 次访问${todayViewsLabel ? `，今日 ${todayViewsLabel} 次` : ''}`"
    >
      <BarChart3 :size="14" />
      <span class="online-counter__count">{{ totalViewsLabel }}</span>
      <span class="online-counter__label">次访问</span>
      <span v-if="todayViewsLabel" class="online-counter__hint">今日 +{{ todayViewsLabel }}</span>
    </div>
  </div>
</template>

<style scoped>
.online-counter-stack {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.online-counter {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  color: #0f172a;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.online-counter--total {
  background: rgba(59, 130, 246, 0.12);
  color: #1d4ed8;
}

.online-counter--total .online-counter__count {
  color: #1d4ed8;
}

.online-counter--total .online-counter__label {
  color: #1e3a8a;
}

.online-counter--total .online-counter__hint {
  color: #2563eb;
  font-weight: 600;
}

.online-counter__pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.55);
  animation: online-counter-pulse 1.6s ease-out infinite;
}

.online-counter__count {
  font-size: 14px;
  font-weight: 800;
  color: #0f172a;
}

.online-counter__label {
  color: #1e293b;
}

.online-counter__hint {
  color: #64748b;
  font-weight: 500;
}

@keyframes online-counter-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.55);
  }
  60% {
    box-shadow: 0 0 0 8px rgba(34, 197, 94, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(34, 197, 94, 0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .online-counter__pulse {
    animation: none;
  }
}
</style>
