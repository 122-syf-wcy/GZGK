<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { batchRecommend } from '@/api/algorithm'
import { useVolunteerStore } from '@/stores/volunteer'
import type { RecommendItem } from '@/types'
import { Sparkles, GraduationCap, Hash, Trophy, RefreshCw, ChevronRight } from 'lucide-vue-next'

const store = useVolunteerStore()
const loading = ref(false)
const items = ref<RecommendItem[]>([])
const hasError = ref(false)

function toSubjectType(first: string): string {
  return first === '物理' ? '物理类' : '历史类'
}

async function load() {
  const plan = store.planItems
  if (!plan.length) return
  loading.value = true
  hasError.value = false
  try {
    let seeds = plan.filter(v => v.gradient === '稳' && v.schoolId).slice(0, 5)
    if (!seeds.length) seeds = plan.filter(v => v.schoolId).slice(0, 5)
    const selected = seeds.map(v => ({ schoolId: v.schoolId!, majorName: v.majorName }))
    if (!selected.length) return
    const res = await batchRecommend(
      selected,
      toSubjectType(store.formData.firstSubject),
      store.formData.provinceRank,
      12,
    )
    if (res.data?.data) items.value = res.data.data
  } catch {
    hasError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

function pct(s: number) { return Math.round(s * 100) }

function clr(s: number): string {
  const p = s * 100
  if (p >= 80) return '#2f7d5d'
  if (p >= 60) return '#17181c'
  if (p >= 40) return '#b98a2f'
  return '#c04848'
}
</script>

<template>
  <section v-if="loading || items.length > 0 || hasError" class="rec-section">
    <div class="rec-header">
      <div class="rec-title-row">
        <div class="rec-icon"><Sparkles :size="14" /></div>
        <h3 class="rec-title">相似院校推荐</h3>
      </div>
      <button v-if="!loading" class="rec-refresh" @click="load">
        <RefreshCw :size="14" />
      </button>
    </div>

    <div v-if="loading" class="rec-loading">
      <div class="rec-skeleton" v-for="i in 3" :key="i">
        <div class="sk-a"></div><div class="sk-b"></div><div class="sk-c"></div>
      </div>
    </div>

    <div v-else-if="hasError" class="rec-empty">
      <p>获取推荐失败</p>
      <button class="rec-retry" @click="load">重试</button>
    </div>

    <div v-else class="rec-scroll">
      <div
        v-for="(it, idx) in items"
        :key="it.schoolId + it.majorName + idx"
        class="rec-card"
      >
        <div class="rec-badge" :style="{ background: clr(it.similarity) + '14', color: clr(it.similarity) }">
          匹配 {{ pct(it.similarity) }}%
        </div>
        <div class="rec-uni-row">
          <div class="rec-avatar" :style="{ background: clr(it.similarity) }">
            <GraduationCap :size="14" color="#fff" />
          </div>
          <h4 class="rec-name">{{ it.universityName }}</h4>
        </div>
        <p class="rec-major">{{ it.majorName }}</p>
        <div class="rec-stats">
          <div class="rec-stat"><Trophy :size="11" /><span>{{ it.latestMinScore }}分</span></div>
          <div class="rec-stat"><Hash :size="11" /><span>{{ it.latestMinRank.toLocaleString() }}位</span></div>
        </div>
        <p class="rec-reason">{{ it.reason }}</p>
        <div class="rec-arrow"><ChevronRight :size="14" /></div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.rec-section {
  padding: 12px 16px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}
.rec-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.rec-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rec-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: linear-gradient(135deg, #b98a2f, #f97316);
  color: #fff;
}
.rec-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--gz-text-primary);
}
.rec-refresh {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.04);
  color: var(--gz-text-secondary);
  cursor: pointer;
  transition: background 150ms ease, color 150ms ease;
}
.rec-refresh:hover {
  background: var(--gz-primary-light);
  color: var(--gz-primary);
}
.rec-scroll {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  padding-bottom: 8px;
}
.rec-scroll::-webkit-scrollbar {
  display: none;
}
.rec-card {
  position: relative;
  flex-shrink: 0;
  width: 220px;
  padding: 16px;
  background: var(--gz-card-bg);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 14px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  scroll-snap-align: start;
  transition: transform 350ms cubic-bezier(0.34, 1.56, 0.64, 1),
    box-shadow 250ms ease;
  cursor: pointer;
}
.rec-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(23, 24, 28, 0.12);
}
.rec-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 9999px;
  font-size: 10px;
  font-weight: 700;
  margin-bottom: 8px;
}
.rec-uni-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.rec-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  flex-shrink: 0;
}
.rec-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--gz-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rec-major {
  font-size: 12px;
  color: var(--gz-primary);
  font-weight: 500;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rec-stats {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}
.rec-stat {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--gz-text-secondary);
  font-variant-numeric: tabular-nums;
}
.rec-reason {
  font-size: 11px;
  color: var(--gz-text-tertiary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.rec-arrow {
  position: absolute;
  top: 16px;
  right: 12px;
  color: var(--gz-text-tertiary);
  opacity: 0;
  transition: opacity 150ms ease;
}
.rec-card:hover .rec-arrow {
  opacity: 1;
}
.rec-loading {
  display: flex;
  gap: 12px;
  overflow: hidden;
}
.rec-skeleton {
  flex-shrink: 0;
  width: 220px;
  padding: 16px;
  background: var(--gz-card-bg);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.25);
}
.sk-a,
.sk-b,
.sk-c {
  border-radius: 4px;
  background: linear-gradient(
    90deg,
    rgba(0, 0, 0, 0.04) 25%,
    rgba(0, 0, 0, 0.08) 50%,
    rgba(0, 0, 0, 0.04) 75%
  );
  background-size: 200% 100%;
  animation: rec-shimmer 1.5s infinite;
}
.sk-a {
  width: 60%;
  height: 10px;
  margin-bottom: 12px;
}
.sk-b {
  width: 100%;
  height: 14px;
  margin-bottom: 8px;
}
.sk-c {
  width: 80%;
  height: 10px;
}
@keyframes rec-shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
.rec-empty {
  text-align: center;
  padding: 16px;
  font-size: 13px;
  color: var(--gz-text-tertiary);
}
.rec-retry {
  margin-top: 8px;
  padding: 6px 16px;
  border: 1px solid var(--gz-primary);
  border-radius: 9999px;
  background: transparent;
  color: var(--gz-primary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 150ms ease;
}
.rec-retry:hover {
  background: var(--gz-primary-light);
}
@media (min-width: 768px) {
  .rec-section {
    padding: 16px 32px;
  }
  .rec-card {
    width: 260px;
  }
  .rec-name {
    font-size: 15px;
  }
}
</style>
