<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { ArrowLeft, ExternalLink, MapPin, Scale, ShieldCheck } from 'lucide-vue-next'
import type { University, VolunteerItem } from '@/types'
import { useVolunteerStore } from '@/stores/volunteer'
import { getUniversityBySchoolId } from '@/api/university'
import { buildPlanModeItems, type PlanMode } from '@/utils/volunteer-plan'
import { sanitizeHttpUrl } from '@/utils/markdown'

const route = useRoute()
const router = useRouter()
const volunteerStore = useVolunteerStore()

const loading = ref(true)
const universities = ref<Record<string, University>>({})

const schoolIds = computed(() => {
  const raw = String(route.query.schools || '')
  return raw.split(',').map(item => item.trim()).filter(Boolean).slice(0, 3)
})

const mode = computed<PlanMode>(() => {
  const value = String(route.query.mode || volunteerStore.formData.strategyMode || '均衡型')
  return value === '保守型' || value === '冲刺型' ? value : '均衡型'
})

const planItems = computed(() => buildPlanModeItems(volunteerStore.planItems, mode.value))

const compareItems = computed(() => {
  const map = new Map<string, VolunteerItem>()
  for (const item of planItems.value) {
    if (!item.schoolId || !schoolIds.value.includes(item.schoolId)) continue
    const existing = map.get(item.schoolId)
    if (!existing || (item.matchScore || 0) > (existing.matchScore || 0)) {
      map.set(item.schoolId, item)
    }
  }
  return schoolIds.value.map((id) => ({
    schoolId: id,
    item: map.get(id) || null,
    uni: universities.value[id] || null,
  }))
})

const compareRows = computed(() => [
  { label: '城市 / 地区', values: compareItems.value.map(col => [col.item?.province, col.item?.city].filter(Boolean).join(' · ') || col.uni?.province || '-') },
  { label: '办学性质', values: compareItems.value.map(col => col.item?.schoolNature || col.uni?.natureName || '-') },
  { label: '学校标签', values: compareItems.value.map(col => (col.item?.tags?.length ? col.item.tags.join(' / ') : col.uni?.tags?.join(' / ') || '-')) },
  { label: '主推专业', values: compareItems.value.map(col => col.item?.majorName || '-') },
  { label: '参考位次', values: compareItems.value.map(col => col.item?.historyMinRank?.toLocaleString() || '-') },
  { label: '机会指数', values: compareItems.value.map(col => typeof col.item?.chanceScore === 'number' ? `${col.item.chanceScore}` : '-') },
  { label: '风险等级', values: compareItems.value.map(col => col.item?.riskLevel || '-') },
  { label: '数据可信度', values: compareItems.value.map(col => `${col.item?.dataSourceType || '-'} / ${col.item?.confidenceLabel || '-'}`) },
  { label: '推荐原因', values: compareItems.value.map(col => col.item?.recommendReason || '-') },
  { label: '风险提醒', values: compareItems.value.map(col => col.item?.riskReason || '-') },
  { label: '替代思路', values: compareItems.value.map(col => col.item?.alternativeOption || '-') },
])

const matrixStyle = computed(() => ({
  gridTemplateColumns: `180px repeat(${schoolIds.value.length}, minmax(0, 1fr))`,
}))

onMounted(async () => {
  if (schoolIds.value.length < 2) {
    showToast('至少选择2所学校再对比')
    router.back()
    return
  }
  try {
    const responses = await Promise.all(
      schoolIds.value.map(async (schoolId) => {
        try {
          const res = await getUniversityBySchoolId(schoolId)
          return [schoolId, res.data.data] as const
        } catch {
          return [schoolId, null] as const
        }
      }),
    )
    universities.value = Object.fromEntries(
      responses.filter((entry): entry is readonly [string, University] => !!entry[1]),
    )
  } finally {
    loading.value = false
  }
})

function openExternal(href: string) {
  const url = sanitizeHttpUrl(href)
  if (!url) {
    showToast('链接格式异常，请到院校详情页人工核验')
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <div class="compare-page">
    <header class="page-header">
      <div class="page-header-inner">
        <button class="back-btn" @click="router.back()"><ArrowLeft :size="20" /></button>
        <h1 class="page-header-title">院校对比</h1>
      </div>
    </header>

    <div class="compare-wrap">
      <div class="compare-summary gz-card">
        <div>
          <h2 class="compare-title">{{ mode }}视图下的重点对比</h2>
          <p class="compare-desc">先看平台、风险与适配度，再决定实际填报顺序。</p>
        </div>
        <span class="compare-count">{{ schoolIds.length }} 所学校</span>
      </div>

      <div v-if="loading" class="loading-block gz-card">加载对比数据中...</div>

      <div v-else class="matrix-card gz-card">
        <div class="matrix-head" :style="matrixStyle">
          <div class="matrix-label">对比项</div>
          <div v-for="col in compareItems" :key="col.schoolId" class="matrix-school">
            <div class="school-head">
              <h3>{{ col.item?.universityName || col.uni?.name || col.schoolId }}</h3>
              <button
                v-if="col.uni?.schoolSite"
                class="school-link"
                @click="openExternal(col.uni.schoolSite)"
              >
                官网
                <ExternalLink :size="13" />
              </button>
            </div>
            <p class="school-major">{{ col.item?.majorName || '待补充主推专业' }}</p>
            <div class="school-meta">
              <span v-if="col.item?.matchTag"><ShieldCheck :size="12" /> {{ col.item.matchTag }}</span>
              <span v-if="col.item?.province"><MapPin :size="12" /> {{ [col.item.province, col.item.city].filter(Boolean).join(' · ') }}</span>
              <span v-if="col.item?.schoolNature"><Scale :size="12" /> {{ col.item.schoolNature }}</span>
            </div>
          </div>
        </div>

        <div v-for="row in compareRows" :key="row.label" class="matrix-row" :style="matrixStyle">
          <div class="matrix-label">{{ row.label }}</div>
          <div v-for="(value, idx) in row.values" :key="`${row.label}-${idx}`" class="matrix-cell">{{ value }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.compare-page {
  min-height: 100dvh;
  background: #fafaf8;
}

.page-header {
  position: sticky;
  top: 0;
  z-index: 20;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}

.page-header-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 10px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 12px;
  background: #f3f4f6;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #383a40;
}

.page-header-title {
  font-size: 18px;
  font-weight: 800;
  color: #17181c;
}

.compare-wrap {
  max-width: 1280px;
  margin: 0 auto;
  padding: 20px 16px 32px;
}

.compare-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.compare-title {
  font-family: var(--gz-font-display);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: #17181c;
}

.compare-desc {
  margin-top: 6px;
  font-size: 14px;
  color: #6a6c72;
}

.compare-count {
  padding: 8px 14px;
  border-radius: 999px;
  background: #f4f4f2;
  color: #17181c;
  font-weight: 800;
}

.loading-block {
  padding: 24px;
  text-align: center;
  color: #6a6c72;
}

.matrix-card {
  padding: 0;
  overflow: hidden;
}

.matrix-head,
.matrix-row {
  display: grid;
}

.matrix-label,
.matrix-school,
.matrix-cell {
  padding: 18px 16px;
  border-right: 1px solid rgba(23, 24, 28, 0.06);
  border-bottom: 1px solid rgba(23, 24, 28, 0.06);
}

.matrix-label {
  background: #fafaf8;
  font-size: 13px;
  font-weight: 800;
  color: #4b4d54;
}

.matrix-school {
  background: linear-gradient(180deg, #fff, #f8fbff);
}

.matrix-school h3 {
  font-size: 18px;
  font-weight: 900;
  color: #17181c;
}

.school-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.school-link {
  border: none;
  background: #f4f4f2;
  color: #17181c;
  border-radius: 999px;
  padding: 6px 10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 700;
}

.school-major {
  margin-top: 6px;
  font-size: 14px;
  color: #17181c;
  font-weight: 700;
}

.school-meta {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: #6a6c72;
}

.school-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.matrix-cell {
  font-size: 14px;
  line-height: 1.7;
  color: #383a40;
}

@media (max-width: 1023px) {
  .compare-summary {
    flex-direction: column;
    align-items: flex-start;
  }

  .matrix-card {
    overflow-x: auto;
  }

  .matrix-head,
  .matrix-row {
    min-width: 960px;
  }
}
</style>
