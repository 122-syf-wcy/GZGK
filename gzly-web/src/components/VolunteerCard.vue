<script setup lang="ts">
import { computed } from 'vue'
import { MapPin, TrendingUp, TrendingDown, Minus, ShieldCheck, ShieldAlert, TriangleAlert } from 'lucide-vue-next'
import type { VolunteerItem } from '@/types'

const props = defineProps<{
  item: VolunteerItem
}>()

const visibleChanceScore = computed(() => props.item.chanceScore || 0)

function gradientColor(g: string) {
  const map: Record<string, string> = {
    '冲': '#c04848',
    '稳': '#17181c',
    '保': '#2f7d5d',
    '垫': '#b98a2f',
  }
  return map[g] || '#97999e'
}

function gradientBg(g: string) {
  const map: Record<string, string> = {
    '冲': 'linear-gradient(135deg, #c04848, #f97316)',
    '稳': 'linear-gradient(135deg, #17181c, #2b2d33)',
    '保': 'linear-gradient(135deg, #2f7d5d, #4f9578)',
    '垫': 'linear-gradient(135deg, #b98a2f, #c29a45)',
  }
  return map[g] || '#97999e'
}

function tagColor(tag: string): string {
  const map: Record<string, string> = {
    '985': '#c04848',
    '211': '#b98a2f',
    '双一流': '#17181c',
    '公办': '#2f7d5d',
    '民办': '#6b5d8a',
  }
  return map[tag] || '#97999e'
}

function probBarColor(prob: number | undefined): string {
  if (!prob) return '#97999e'
  if (prob >= 80) return '#2f7d5d'
  if (prob >= 60) return '#17181c'
  if (prob >= 40) return '#b98a2f'
  return '#c04848'
}

function trendClass(trend: string | undefined): string {
  if (!trend) return 'trend--neutral'
  if (trend.includes('加剧')) return 'trend--hot'
  if (trend.includes('缓和')) return 'trend--cool'
  return 'trend--neutral'
}

function trendShort(trend: string | undefined): string {
  if (!trend) return '稳定'
  if (trend.includes('加剧')) return '竞争↑'
  if (trend.includes('缓和')) return '竞争↓'
  return '稳定'
}

function requirementSourceLabel(source: string | undefined): string {
  if (source === 'official_requirement') return '官方库'
  if (source === 'score_line') return '分数线字段'
  if (source === 'inferred') return '需复核'
  return '待核验'
}
</script>

<template>
  <div class="volunteer-card gz-card">
    <!-- Top Row: Index + Gradient + University -->
    <div class="card-top">
      <span class="card-index" :style="{ background: gradientBg(item.gradient) }">
        {{ item.index }}
      </span>
      <div class="card-title-group">
        <div class="card-title-row">
          <span class="card-gradient-tag" :style="{ color: gradientColor(item.gradient), background: gradientColor(item.gradient) + '14', borderColor: gradientColor(item.gradient) + '30' }">
            {{ item.gradient }}
          </span>
          <h3 class="card-university">{{ item.universityName }}</h3>
        </div>
        <div class="card-major">{{ item.majorName }}</div>
      </div>
    </div>

    <!-- Tags + Location -->
    <div class="card-meta">
      <div class="card-location">
        <MapPin :size="12" />
        <span>{{ item.province }} · {{ item.city }}</span>
      </div>
      <div class="card-tags">
        <span
          v-for="tag in item.tags"
          :key="tag"
          class="mini-tag"
          :style="{ color: tagColor(tag), background: tagColor(tag) + '14' }"
        >
          {{ tag }}
        </span>
      </div>
    </div>

    <!-- Stats Row -->
    <div class="card-stats">
      <div class="stat-cell">
        <span class="stat-label">{{ item.referenceYear }}最低分</span>
        <span class="stat-value stat-value--score">{{ item.historyMinScore }}</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-cell">
        <span class="stat-label">最低位次</span>
        <span class="stat-value">{{ item.historyMinRank.toLocaleString() }}</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-cell">
        <span class="stat-label">再选要求</span>
        <span class="stat-value stat-value--sub">
          {{ item.resubjectRequirement || '不限' }}
          <small class="requirement-source">{{ requirementSourceLabel(item.subjectRequirementSource) }}</small>
        </span>
      </div>
    </div>

    <!-- Algorithm Enhancement Row -->
    <div v-if="visibleChanceScore > 0" class="algo-row">
      <div class="algo-cell algo-cell--prob">
        <span class="algo-label">机会指数</span>
        <div class="algo-prob-bar">
          <div class="algo-prob-fill" :style="{ width: visibleChanceScore + '%', background: probBarColor(visibleChanceScore) }"></div>
        </div>
        <span class="algo-prob-text" :style="{ color: probBarColor(visibleChanceScore) }">{{ visibleChanceScore }}</span>
      </div>
      <div class="algo-divider"></div>
      <div class="algo-cell">
        <span class="algo-label">风险</span>
        <span class="algo-risk-badge" :class="'risk--' + (item.riskColor || 'gray')">
          <ShieldCheck v-if="item.riskColor === 'green'" :size="11" />
          <ShieldAlert v-else-if="item.riskColor === 'yellow'" :size="11" />
          <TriangleAlert v-else-if="item.riskColor === 'red'" :size="11" />
          {{ item.riskLevel || '未知' }}
        </span>
      </div>
      <div class="algo-divider"></div>
      <div class="algo-cell">
        <span class="algo-label">趋势</span>
        <span class="algo-trend" :class="trendClass(item.trend)">
          <TrendingDown v-if="item.trend?.includes('加剧')" :size="11" />
          <TrendingUp v-else-if="item.trend?.includes('缓和')" :size="11" />
          <Minus v-else :size="11" />
          {{ trendShort(item.trend) }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.volunteer-card {
  padding: var(--gz-space-4) var(--gz-space-5);
  transition: transform var(--gz-transition-spring), box-shadow var(--gz-transition-normal);
}

.volunteer-card:hover {
  transform: translateY(-1px);
}

/* ---- Top ---- */
.card-top {
  display: flex;
  align-items: flex-start;
  gap: var(--gz-space-3);
  margin-bottom: var(--gz-space-3);
}

.card-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--gz-radius-sm);
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
}

.card-title-group {
  flex: 1;
  min-width: 0;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: var(--gz-space-2);
  margin-bottom: 2px;
}

.card-gradient-tag {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: var(--gz-radius-full);
  font-size: 11px;
  font-weight: 700;
  border: 1px solid;
  letter-spacing: 0.04em;
}

.card-university {
  font-size: 15px;
  font-weight: 600;
  color: var(--gz-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.3;
}

.card-major {
  font-size: 13px;
  color: var(--gz-primary);
  font-weight: 500;
  line-height: 1.4;
}

/* ---- Meta ---- */
.card-meta {
  display: flex;
  align-items: center;
  gap: var(--gz-space-3);
  margin-bottom: var(--gz-space-3);
  flex-wrap: wrap;
}

.card-location {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: var(--gz-text-tertiary);
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.mini-tag {
  padding: 1px 7px;
  border-radius: var(--gz-radius-full);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

/* ---- Stats ---- */
.card-stats {
  display: flex;
  align-items: center;
  padding: var(--gz-space-3);
  background: var(--gz-primary-50);
  border-radius: var(--gz-radius-md);
}

.stat-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.stat-divider {
  width: 1px;
  height: 28px;
  background: rgba(23, 24, 28, 0.1);
  flex-shrink: 0;
}

.stat-label {
  font-size: 10px;
  color: var(--gz-text-tertiary);
  white-space: nowrap;
}

.stat-value {
  font-size: 14px;
  font-weight: 700;
  color: var(--gz-text-primary);
  font-variant-numeric: tabular-nums;
}

.stat-value--score {
  color: #c04848;
}

.stat-value--sub {
  font-size: 12px;
  font-weight: 500;
}

.requirement-source {
  display: block;
  margin-top: 2px;
  font-size: 9px;
  font-weight: 600;
  color: var(--gz-text-tertiary);
}

/* ---- Algorithm Enhancement ---- */
.algo-row {
  display: flex;
  align-items: center;
  margin-top: var(--gz-space-2);
  padding: var(--gz-space-2) var(--gz-space-3);
  background: linear-gradient(135deg, rgba(23, 24, 28, 0.04), rgba(47, 125, 93, 0.04));
  border-radius: var(--gz-radius-md);
  border: 1px solid rgba(23, 24, 28, 0.08);
}

.algo-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.algo-cell--prob {
  flex: 1.5;
}

.algo-divider {
  width: 1px;
  height: 24px;
  background: rgba(23, 24, 28, 0.08);
  flex-shrink: 0;
  margin: 0 4px;
}

.algo-label {
  font-size: 9px;
  color: var(--gz-text-tertiary);
  letter-spacing: 0.02em;
}

.algo-prob-bar {
  width: 100%;
  height: 4px;
  border-radius: 2px;
  background: rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.algo-prob-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.6s ease;
}

.algo-prob-text {
  font-size: 12px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.algo-risk-badge {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 1px 6px;
  border-radius: var(--gz-radius-full);
  font-size: 10px;
  font-weight: 600;
}

.risk--green {
  color: #2f7d5d;
  background: rgba(47, 125, 93, 0.1);
}

.risk--yellow {
  color: #b98a2f;
  background: rgba(185, 138, 47, 0.1);
}

.risk--red {
  color: #c04848;
  background: rgba(192, 72, 72, 0.1);
}

.risk--gray {
  color: #97999e;
  background: rgba(148, 163, 184, 0.1);
}

.algo-trend {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 10px;
  font-weight: 600;
}

.trend--hot {
  color: #c04848;
}

.trend--cool {
  color: #2f7d5d;
}

.trend--neutral {
  color: #6a6c72;
}
</style>
