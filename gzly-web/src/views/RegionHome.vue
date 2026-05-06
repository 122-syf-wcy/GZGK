<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  BookOpenCheck,
  Building2,
  CheckCircle2,
  GraduationCap,
  LockKeyhole,
  MessageSquare,
  ShieldAlert,
  Table2,
} from 'lucide-vue-next'
import {
  getProvinceConfig,
  normalizeProvinceCode,
  type ProvinceConfig,
} from '@/constants/provinces'

defineOptions({ name: 'RegionHome' })

const route = useRoute()
const router = useRouter()

const provinceCode = computed(() => normalizeProvinceCode(route.params.provinceCode))
const province = computed(() => getProvinceConfig(provinceCode.value))
const isPreparing = computed(() => province.value.status !== 'open')
const isProfessionalGroupProvince = computed(() => province.value.volunteerUnitType === 'PROFESSIONAL_GROUP_45')

const featureCards = computed(() => [
  {
    key: 'university',
    title: '院校查询',
    desc: '全国院校库保持统一，进入详情后按当前地区招生数据复核。',
    status: '全国库',
    icon: Building2,
    path: '/university',
    tone: 'blue',
  },
  {
    key: 'score-line',
    title: '历年分数线',
    desc: province.value.scoreLineDescription,
    status: isPreparing.value ? '核验中' : '已开放',
    icon: Table2,
    path: '/score-line',
    tone: 'green',
  },
  {
    key: 'volunteer',
    title: '智能填报',
    desc: isPreparing.value
      ? province.value.volunteerLockDescription
      : `生成 ${province.value.targetCount} 个${province.value.volunteerUnit}草稿。`,
    status: isPreparing.value ? '锁定' : '已开放',
    icon: isPreparing.value ? LockKeyhole : GraduationCap,
    path: '/volunteer',
    tone: isPreparing.value ? 'amber' : 'dark',
    locked: isPreparing.value,
  },
  {
    key: 'special',
    title: '特殊类型招生',
    desc: province.value.specialAdmissionsHint,
    status: isPreparing.value ? '需复核' : '政策线索',
    icon: BookOpenCheck,
    path: '/special-admissions',
    tone: 'amber',
  },
])

const metrics = computed(() => [
  { label: '志愿单位', value: province.value.volunteerUnit, note: province.value.targetBatch },
  { label: '目标数量', value: `${province.value.targetCount}`, note: isProfessionalGroupProvince.value ? '院校专业组' : '平行志愿' },
  { label: '官方来源', value: province.value.shortName, note: province.value.officialSource },
  { label: '入口状态', value: province.value.statusLabel, note: isPreparing.value ? '先展示专区' : '主流程可用' },
])

watch(provinceCode, (code) => {
  if (route.params.provinceCode !== code) {
    router.replace({ path: `/region/${code}`, query: province.value.routeQuery })
  }
}, { immediate: true })

function goFeature(path: string): void {
  router.push({ path, query: province.value.routeQuery })
}

function goHome(): void {
  router.push('/')
}

function statusText(item: ProvinceConfig): string {
  return item.status === 'open' ? '已开放' : '数据准备中'
}
</script>

<template>
  <div class="gz-shell-page region-page">
    <header class="gz-shell-header">
      <div class="gz-shell-header-inner">
        <button type="button" class="gz-shell-back" aria-label="返回地区选择" @click="goHome">
          <ArrowLeft :size="20" />
        </button>
        <div class="gz-shell-heading">
          <div class="gz-shell-title">{{ province.name }}专区</div>
          <div class="gz-shell-subtitle">{{ province.targetBatch }} · {{ province.volunteerUnit }}</div>
        </div>
        <div class="gz-shell-header-extra" :class="`is-${province.statusTone}`">
          {{ statusText(province) }}
        </div>
      </div>
    </header>

    <main class="gz-shell-main region-main">
      <section class="gz-shell-hero region-hero">
        <div class="region-hero__copy">
          <span class="gz-shell-kicker">province workspace</span>
          <h1 class="gz-shell-hero-title">{{ province.heroTitle }}</h1>
          <p class="gz-shell-hero-desc">{{ province.heroDescription }}</p>
          <div class="gz-shell-chip-row region-hero__chips">
            <span class="gz-shell-chip is-soft-active">{{ province.targetBatch }}</span>
            <span class="gz-shell-chip is-soft-active">{{ province.targetCount }} 个{{ province.volunteerUnit }}</span>
            <span class="gz-shell-chip is-soft-active">{{ province.officialSource }}</span>
          </div>
        </div>

        <div class="gz-shell-metrics">
          <div v-for="metric in metrics" :key="metric.label" class="gz-shell-metric">
            <span class="gz-shell-metric-label">{{ metric.label }}</span>
            <span class="gz-shell-metric-value">{{ metric.value }}</span>
            <span class="gz-shell-metric-note">{{ metric.note }}</span>
          </div>
        </div>
      </section>

      <section class="gz-shell-panel region-status-card" :class="{ 'is-preparing': isPreparing }">
        <div class="region-status-card__icon">
          <LockKeyhole v-if="isPreparing" :size="22" />
          <CheckCircle2 v-else :size="22" />
        </div>
        <div>
          <h2>{{ province.dataStatusTitle }}</h2>
          <p>{{ province.dataStatusDescription }}</p>
        </div>
      </section>

      <section class="region-feature-grid" aria-label="地区功能入口">
        <button
          v-for="feature in featureCards"
          :key="feature.key"
          type="button"
          class="gz-shell-panel region-feature-card"
          :class="[`is-${feature.tone}`, { 'is-locked': feature.locked }]"
          @click="goFeature(feature.path)"
        >
          <span class="region-feature-card__icon">
            <component :is="feature.icon" :size="22" />
          </span>
          <span class="region-feature-card__body">
            <span class="region-feature-card__meta">{{ feature.status }}</span>
            <strong>{{ feature.title }}</strong>
            <small>{{ feature.desc }}</small>
          </span>
          <ArrowRight :size="18" class="region-feature-card__arrow" />
        </button>
      </section>

      <section class="gz-shell-panel region-source-card">
        <div>
          <h2>官方来源提醒</h2>
          <p>{{ province.footerReminder }}</p>
        </div>
        <div class="region-source-card__actions">
          <button type="button" class="gz-shell-action gz-shell-action--primary" @click="goFeature('/volunteer')">
            {{ province.volunteerCta }}
          </button>
          <button type="button" class="gz-shell-action" @click="goFeature('/score-line')">
            查看分数线
          </button>
        </div>
      </section>

      <section class="region-link-row">
        <button type="button" class="region-link" @click="router.push('/disclaimer')">
          <ShieldAlert :size="15" />
          查看免责声明
        </button>
        <button type="button" class="region-link" @click="router.push('/encouragement')">
          <MessageSquare :size="15" />
          考生留言
        </button>
      </section>
    </main>
  </div>
</template>

<style scoped>
.region-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.gz-shell-header-extra.is-open {
  border-color: rgba(16, 185, 129, 0.22);
  background: #ecfdf5;
  color: #047857;
}

.gz-shell-header-extra.is-preparing {
  border-color: rgba(180, 83, 9, 0.22);
  background: #fffbeb;
  color: #92400e;
}

.region-hero__chips {
  margin-top: 18px;
}

.region-status-card {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 20px;
}

.region-status-card.is-preparing {
  border-color: rgba(180, 83, 9, 0.18);
  background: linear-gradient(180deg, rgba(255, 253, 250, 0.98), rgba(255, 251, 235, 0.94));
}

.region-status-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 14px;
  background: #eff6ff;
  color: #1d4ed8;
}

.region-status-card.is-preparing .region-status-card__icon {
  background: #fffbeb;
  color: #92400e;
}

.region-status-card h2,
.region-source-card h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.25;
  color: #0f172a;
}

.region-status-card p,
.region-source-card p {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.8;
  color: #64748b;
}

.region-feature-grid {
  display: grid;
  gap: 12px;
}

.region-feature-card {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 18px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
  color: inherit;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.region-feature-card:hover {
  transform: translateY(-1px);
  border-color: rgba(29, 78, 216, 0.2);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.07);
}

.region-feature-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: #eff6ff;
  color: #1d4ed8;
}

.region-feature-card.is-green .region-feature-card__icon {
  background: #ecfdf5;
  color: #047857;
}

.region-feature-card.is-amber .region-feature-card__icon {
  background: #fffbeb;
  color: #92400e;
}

.region-feature-card.is-dark .region-feature-card__icon {
  background: #0f172a;
  color: #ffffff;
}

.region-feature-card__body {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.region-feature-card__meta {
  font-size: 12px;
  font-weight: 800;
  line-height: 1.2;
  color: #1d4ed8;
}

.region-feature-card.is-locked .region-feature-card__meta {
  color: #92400e;
}

.region-feature-card strong {
  font-size: 17px;
  line-height: 1.25;
  color: #0f172a;
}

.region-feature-card small {
  font-size: 13px;
  line-height: 1.6;
  color: #64748b;
}

.region-feature-card__arrow {
  color: #94a3b8;
}

.region-source-card {
  display: grid;
  gap: 18px;
  padding: 20px;
}

.region-source-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.region-link-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.region-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 999px;
  background: #fffdfa;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

@media (min-width: 768px) {
  .region-feature-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1024px) {
  .region-source-card {
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
  }
}
</style>
