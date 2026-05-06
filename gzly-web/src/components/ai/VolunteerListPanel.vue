<template>
  <section id="volunteer-panel" class="vlp gz-card">
    <header class="vlp__head">
      <div class="vlp__title-block">
        <h3 class="vlp__title">
          <ListChecks :size="16" />
          完整志愿清单 <span class="vlp__count">({{ total }} 项)</span>
        </h3>
        <p class="vlp__desc">按梯度 / 机会指数 / 位次差 / 学校层次 / 城市多维排序，支持搜索与逐条展开复核。</p>
      </div>
      <div class="vlp__head-actions">
        <button class="vlp__icon-btn" type="button" :disabled="!!exporting" @click="handleExportImage">
          <Image :size="13" /> {{ exporting === 'image' ? '导出中…' : '长图' }}
        </button>
        <button class="vlp__icon-btn" type="button" :disabled="!!exporting" @click="handleExportExcel">
          <FileSpreadsheet :size="13" /> {{ exporting === 'excel' ? '导出中…' : 'Excel' }}
        </button>
        <button class="vlp__toggle" type="button" :aria-expanded="expanded" @click="expanded = !expanded">
          <ChevronDown :size="16" :class="{ rotated: !expanded }" />
          {{ expanded ? '收起' : '展开' }}
        </button>
      </div>
    </header>

    <div v-if="expanded" class="vlp__body">
      <div class="vlp__toolbar">
        <div class="vlp__sort-row" role="tablist" aria-label="排序方式">
          <button
            v-for="opt in sortOptions"
            :key="opt.key"
            type="button"
            class="vlp__chip"
            :class="{ active: sortKey === opt.key }"
            :aria-selected="sortKey === opt.key"
            @click="sortKey = opt.key"
          >
            {{ opt.label }}
          </button>
        </div>
        <div class="vlp__filter-row">
          <label class="vlp__search">
            <Search :size="14" />
            <input
              v-model.trim="keyword"
              type="search"
              placeholder="搜索学校 / 专业 / 城市"
              aria-label="搜索志愿"
            />
          </label>
          <div class="vlp__gradient-row" role="group" aria-label="梯度筛选">
            <button
              v-for="chip in gradientChips"
              :key="chip.key"
              type="button"
              class="vlp__gchip"
              :class="[gradientFilter === chip.key ? 'active' : '', `vlp__gchip--${chip.key}`]"
              @click="gradientFilter = chip.key"
            >
              {{ chip.label }}<span v-if="chip.count != null" class="vlp__gchip-count">{{ chip.count }}</span>
            </button>
          </div>
        </div>
      </div>

      <div v-if="!groups.length" class="vlp__empty">
        没有匹配的志愿。修改搜索或切换排序试试。
      </div>

      <div v-for="group in groups" :key="group.key" class="vlp__group">
        <div v-if="group.label" class="vlp__group-head">{{ group.label }}<span>{{ group.items.length }} 项</span></div>
        <div class="vlp__rows">
          <article
            v-for="item in group.items"
            :key="rowKey(item)"
            class="vlp__row"
            :class="{ expanded: isRowExpanded(item) }"
          >
            <button
              class="vlp__row-head"
              type="button"
              :aria-expanded="isRowExpanded(item)"
              @click="toggleRow(item)"
            >
              <span class="vlp__row-index">{{ item.index }}</span>
              <span class="vlp__row-gradient" :style="{ background: gradientColor(item.gradient).bg, color: gradientColor(item.gradient).fg }">
                {{ item.gradient }}
              </span>
              <span class="vlp__row-main">
                <span class="vlp__row-school">{{ item.universityName }}</span>
                <span class="vlp__row-major">{{ item.majorName }}</span>
                <span class="vlp__row-meta">
                  <span>{{ item.city || item.province || '—' }}</span>
                  <span v-if="schoolTierLabel(item)" class="vlp__row-tier">{{ schoolTierLabel(item) }}</span>
                </span>
              </span>
              <span class="vlp__row-metrics">
                <span class="vlp__metric">
                  <em>机会</em>
                  <strong>{{ item.chanceScore ?? '-' }}</strong>
                </span>
                <span class="vlp__metric">
                  <em>位次差</em>
                  <strong :class="rankGapClass(item)">{{ formatRankGap(item) }}</strong>
                </span>
                <span class="vlp__metric vlp__metric--confidence">
                  <em>参考度</em>
                  <strong>{{ item.confidenceLevel || '—' }}</strong>
                </span>
                <span v-if="item.riskLevel" class="vlp__risk" :class="`vlp__risk--${item.riskColor || 'gray'}`">
                  {{ item.riskLevel }}
                </span>
              </span>
              <ChevronDown :size="14" class="vlp__row-caret" :class="{ rotated: isRowExpanded(item) }" />
            </button>
            <div v-if="isRowExpanded(item)" class="vlp__row-body">
              <div v-if="item.recommendReason" class="vlp__row-block">
                <strong>推荐理由</strong>
                <p>{{ item.recommendReason }}</p>
              </div>
              <div v-if="item.riskReason" class="vlp__row-block vlp__row-block--warn">
                <strong>风险提示</strong>
                <p>{{ item.riskReason }}</p>
              </div>
              <div v-if="item.planRiskNote || item.planExpansionNote" class="vlp__row-block">
                <strong>计划趋势</strong>
                <p>{{ item.planRiskNote || item.planExpansionNote }}</p>
              </div>
              <div v-if="item.historyRecords && item.historyRecords.length" class="vlp__row-block">
                <strong>近三年录取</strong>
                <ul class="vlp__history-list">
                  <li v-for="rec in item.historyRecords.slice(0, 3)" :key="`${rec.year}-${rec.minScore}`">
                    <span>{{ rec.year }} 年</span>
                    <span>最低分 {{ rec.minScore || '—' }}</span>
                    <span>最低位次 {{ rec.minRank || '—' }}</span>
                    <span v-if="rec.planCount != null">计划 {{ rec.planCount }}</span>
                  </li>
                </ul>
              </div>
              <div v-if="!item.recommendReason && !item.riskReason && !(item.historyRecords && item.historyRecords.length)" class="vlp__row-block vlp__row-block--muted">
                暂无逐条结构化描述，请参考上方诊断正文或右侧 skills 对话。
              </div>
            </div>
          </article>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { showToast } from 'vant'
import {
  ChevronDown,
  FileSpreadsheet,
  Image,
  ListChecks,
  Search,
} from 'lucide-vue-next'
import type { VolunteerItem } from '@/types'
import { exportPlanExcel, exportPlanLongImage } from '@/api/volunteer'

type SortKey = 'default' | 'gradient' | 'chance' | 'confidence' | 'rankGap' | 'tier' | 'city'

const props = withDefaults(defineProps<{
  items: VolunteerItem[]
  planId?: number | null
  safetyCode?: string
  defaultExpanded?: boolean
}>(), {
  planId: null,
  safetyCode: '',
  defaultExpanded: false,
})

const expanded = ref(props.defaultExpanded)
const sortKey = ref<SortKey>('default')
const gradientFilter = ref<'all' | '冲' | '稳' | '保' | '垫'>('all')
const keyword = ref('')
const expandedRows = ref<Set<string>>(new Set())
const exporting = ref<'' | 'image' | 'excel'>('')

const total = computed(() => props.items.length)

const sortOptions: Array<{ key: SortKey; label: string }> = [
  { key: 'default', label: '默认顺序' },
  { key: 'gradient', label: '梯度优先' },
  { key: 'chance', label: '机会指数' },
  { key: 'confidence', label: '数据参考度' },
  { key: 'rankGap', label: '位次差 |小→大|' },
  { key: 'tier', label: '学校层次' },
  { key: 'city', label: '城市分组' },
]

const gradientChips = computed(() => {
  const counts: Record<string, number> = { 冲: 0, 稳: 0, 保: 0, 垫: 0 }
  props.items.forEach(item => {
    if (item.gradient && counts[item.gradient] != null) counts[item.gradient]++
  })
  return [
    { key: 'all' as const, label: '全部', count: props.items.length },
    { key: '冲' as const, label: '冲', count: counts['冲'] },
    { key: '稳' as const, label: '稳', count: counts['稳'] },
    { key: '保' as const, label: '保', count: counts['保'] },
    { key: '垫' as const, label: '垫', count: counts['垫'] },
  ]
})

const filteredItems = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return props.items.filter(item => {
    if (gradientFilter.value !== 'all' && item.gradient !== gradientFilter.value) return false
    if (!kw) return true
    const hay = `${item.universityName || ''}${item.majorName || ''}${item.city || ''}${item.province || ''}`.toLowerCase()
    return hay.includes(kw)
  })
})

const gradientOrder: Record<string, number> = { 冲: 0, 稳: 1, 保: 2, 垫: 3 }

// 985 > 211 > 双一流 > 公办 > 民办
function tierRank(item: VolunteerItem): number {
  const bucket = `${(item.tags || []).join('|')}|${item.schoolNature || ''}`
  if (/985/.test(bucket)) return 0
  if (/211/.test(bucket)) return 1
  if (/双一流|一流学科/.test(bucket)) return 2
  if (/公办/.test(bucket)) return 3
  if (/民办/.test(bucket)) return 4
  return 5
}

function schoolTierLabel(item: VolunteerItem): string {
  const bucket = `${(item.tags || []).join('|')}|${item.schoolNature || ''}`
  if (/985/.test(bucket)) return '985'
  if (/211/.test(bucket)) return '211'
  if (/双一流/.test(bucket)) return '双一流'
  if (/一流学科/.test(bucket)) return '一流学科'
  if (/公办/.test(bucket)) return '公办'
  if (/民办/.test(bucket)) return '民办'
  if (/中外|港澳|合作/.test(bucket)) return '合作办学'
  return ''
}

const sortedItems = computed(() => {
  const list = [...filteredItems.value]
  switch (sortKey.value) {
    case 'gradient':
      list.sort((a, b) => (gradientOrder[a.gradient] ?? 9) - (gradientOrder[b.gradient] ?? 9) || a.index - b.index)
      break
    case 'chance':
      list.sort((a, b) => (b.chanceScore ?? -1) - (a.chanceScore ?? -1))
      break
    case 'confidence':
      list.sort((a, b) => (b.dataConfidence ?? b.dataConfidenceScore ?? -1) - (a.dataConfidence ?? a.dataConfidenceScore ?? -1))
      break
    case 'rankGap':
      list.sort((a, b) => Math.abs(a.rankGap ?? a.rankDiff ?? 999999) - Math.abs(b.rankGap ?? b.rankDiff ?? 999999))
      break
    case 'tier':
      list.sort((a, b) => tierRank(a) - tierRank(b) || a.index - b.index)
      break
    case 'city':
      list.sort((a, b) => {
        const ca = a.city || a.province || 'z'
        const cb = b.city || b.province || 'z'
        return ca.localeCompare(cb, 'zh-CN') || a.index - b.index
      })
      break
    case 'default':
    default:
      list.sort((a, b) => a.index - b.index)
  }
  return list
})

const groups = computed<Array<{ key: string; label: string; items: VolunteerItem[] }>>(() => {
  if (sortKey.value === 'city') {
    const map = new Map<string, VolunteerItem[]>()
    sortedItems.value.forEach(item => {
      const key = item.city || item.province || '未标注城市'
      const bucket = map.get(key) ?? []
      bucket.push(item)
      map.set(key, bucket)
    })
    return Array.from(map.entries()).map(([label, items]) => ({ key: `city-${label}`, label, items }))
  }
  if (sortKey.value === 'gradient') {
    const order: Array<'冲' | '稳' | '保' | '垫'> = ['冲', '稳', '保', '垫']
    return order
      .map(g => ({ key: `grad-${g}`, label: `${g} · ${sortedItems.value.filter(i => i.gradient === g).length} 项`, items: sortedItems.value.filter(i => i.gradient === g) }))
      .filter(group => group.items.length)
  }
  return [{ key: 'all', label: '', items: sortedItems.value }]
})

function rowKey(item: VolunteerItem): string {
  return `${item.index}|${item.schoolId || ''}|${item.majorName}`
}

function isRowExpanded(item: VolunteerItem): boolean {
  return expandedRows.value.has(rowKey(item))
}

function toggleRow(item: VolunteerItem) {
  const key = rowKey(item)
  const next = new Set(expandedRows.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expandedRows.value = next
}

function formatRankGap(item: VolunteerItem): string {
  const v = item.rankGap ?? item.rankDiff
  if (v == null) return '—'
  const sign = v > 0 ? '+' : v < 0 ? '' : ''
  return `${sign}${Math.round(v).toLocaleString()}`
}

function rankGapClass(item: VolunteerItem): string {
  const v = item.rankGap ?? item.rankDiff
  if (v == null) return ''
  if (v > 3000) return 'is-safe'
  if (v > -3000) return 'is-mid'
  return 'is-risk'
}

function gradientColor(g: string): { bg: string; fg: string } {
  switch (g) {
    case '冲': return { bg: '#fef2f2', fg: '#b91c1c' }
    case '稳': return { bg: '#eff6ff', fg: '#1d4ed8' }
    case '保': return { bg: '#ecfdf5', fg: '#047857' }
    case '垫': return { bg: '#fffbeb', fg: '#b45309' }
    default: return { bg: '#f1f5f9', fg: '#475569' }
  }
}

async function handleExportImage() {
  if (!props.planId || !props.safetyCode) {
    showToast('缺少方案安全码，暂无法导出')
    return
  }
  if (exporting.value) return
  exporting.value = 'image'
  try {
    const res = await exportPlanLongImage(props.planId, props.safetyCode)
    downloadBlob(res.data, `gzly-volunteer-${props.planId}.png`)
  } catch (e: any) {
    showToast(e?.message || '导出长图失败')
  } finally {
    exporting.value = ''
  }
}

async function handleExportExcel() {
  if (!props.planId || !props.safetyCode) {
    showToast('缺少方案安全码，暂无法导出')
    return
  }
  if (exporting.value) return
  exporting.value = 'excel'
  try {
    const res = await exportPlanExcel(props.planId, props.safetyCode)
    downloadBlob(res.data, `gzly-volunteer-${props.planId}.xlsx`)
  } catch (e: any) {
    showToast(e?.message || '导出 Excel 失败')
  } finally {
    exporting.value = ''
  }
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}
</script>

<style scoped>
.vlp {
  padding: 14px 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  scroll-margin-top: 92px;
}

.vlp__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.vlp__title-block { flex: 1; min-width: 220px; }
.vlp__title {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  font-weight: 900;
  color: #1f2933;
}
.vlp__count {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 800;
  color: #64748b;
}
.vlp__desc {
  margin: 6px 0 0;
  font-size: 12.5px;
  color: #64748b;
  line-height: 1.7;
}

.vlp__head-actions { display: inline-flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.vlp__icon-btn,
.vlp__toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.12);
  color: #1f2933;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}
.vlp__icon-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.vlp__toggle .rotated { transform: rotate(-90deg); transition: transform 0.18s ease; }

.vlp__body { display: flex; flex-direction: column; gap: 12px; }

.vlp__toolbar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  background: #fcf7ec;
  border: 1px dashed rgba(95, 70, 48, 0.22);
  border-radius: 12px;
}
.vlp__sort-row { display: flex; flex-wrap: wrap; gap: 6px; }
.vlp__chip {
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.12);
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}
.vlp__chip.active {
  background: #1f2933;
  color: #fffdf7;
  border-color: #1f2933;
}

.vlp__filter-row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.vlp__search {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  height: 30px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.12);
  border-radius: 999px;
  flex: 1;
  min-width: 180px;
  color: #64748b;
}
.vlp__search input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  color: #1f2933;
}
.vlp__gradient-row { display: inline-flex; gap: 4px; flex-wrap: wrap; }
.vlp__gchip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.12);
  color: #475569;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}
.vlp__gchip.active { color: #1f2933; border-color: rgba(31, 41, 51, 0.3); background: #f7f2e8; }
.vlp__gchip--冲.active { background: #fef2f2; color: #b91c1c; border-color: rgba(185, 28, 28, 0.28); }
.vlp__gchip--稳.active { background: #eff6ff; color: #1d4ed8; border-color: rgba(29, 78, 216, 0.28); }
.vlp__gchip--保.active { background: #ecfdf5; color: #047857; border-color: rgba(4, 120, 87, 0.28); }
.vlp__gchip--垫.active { background: #fffbeb; color: #b45309; border-color: rgba(180, 83, 9, 0.28); }
.vlp__gchip-count {
  min-width: 18px;
  height: 16px;
  padding: 0 5px;
  border-radius: 999px;
  background: rgba(31, 41, 51, 0.12);
  color: #1f2933;
  font-size: 10px;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.vlp__empty {
  padding: 20px;
  text-align: center;
  font-size: 13px;
  color: #94a3b8;
}

.vlp__group { display: flex; flex-direction: column; gap: 6px; }
.vlp__group-head {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 800;
  color: #475569;
  padding: 2px 4px;
}
.vlp__group-head span {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 700;
}
.vlp__rows { display: flex; flex-direction: column; gap: 6px; }

.vlp__row {
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.08);
  border-radius: 10px;
  overflow: hidden;
}
.vlp__row.expanded {
  border-color: rgba(31, 41, 51, 0.24);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.06);
}
.vlp__row-head {
  width: 100%;
  display: grid;
  grid-template-columns: 28px 28px minmax(0, 1fr) auto 14px;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  background: transparent;
  border: none;
  cursor: pointer;
  text-align: left;
  font: inherit;
}
.vlp__row-index {
  min-width: 28px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: #f4f0e8;
  color: #475569;
  font-size: 11.5px;
  font-weight: 800;
}
.vlp__row-gradient {
  width: 26px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 900;
}
.vlp__row-main { display: grid; gap: 2px; min-width: 0; }
.vlp__row-school {
  font-size: 13.5px;
  font-weight: 900;
  color: #1f2933;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.vlp__row-major {
  font-size: 12px;
  color: #475569;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.vlp__row-meta {
  display: inline-flex;
  gap: 6px;
  font-size: 11px;
  color: #94a3b8;
  font-weight: 700;
}
.vlp__row-tier {
  background: #efe7d8;
  color: #5f4630;
  padding: 0 6px;
  border-radius: 999px;
}

.vlp__row-metrics {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.vlp__metric {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-end;
  min-width: 40px;
  line-height: 1.1;
}
.vlp__metric em {
  font-style: normal;
  font-size: 10px;
  color: #94a3b8;
  font-weight: 700;
}
.vlp__metric strong {
  font-size: 13px;
  font-weight: 900;
  color: #1f2933;
}
.vlp__metric strong.is-safe { color: #047857; }
.vlp__metric strong.is-mid { color: #1d4ed8; }
.vlp__metric strong.is-risk { color: #b91c1c; }
.vlp__metric--confidence strong { font-size: 11.5px; }

.vlp__risk {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
}
.vlp__risk--red { background: #fef2f2; color: #b91c1c; }
.vlp__risk--yellow { background: #fffbeb; color: #b45309; }
.vlp__risk--green { background: #ecfdf5; color: #047857; }
.vlp__risk--gray { background: #f1f5f9; color: #475569; }

.vlp__row-caret { color: #94a3b8; transition: transform 0.18s ease; }
.vlp__row-caret.rotated { transform: rotate(180deg); color: #1f2933; }

.vlp__row-body {
  border-top: 1px dashed rgba(31, 41, 51, 0.1);
  padding: 10px 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #fffbf0;
}
.vlp__row-block strong {
  display: block;
  font-size: 12px;
  font-weight: 800;
  color: #1f2933;
  margin-bottom: 4px;
}
.vlp__row-block p {
  margin: 0;
  font-size: 12.5px;
  color: #475569;
  line-height: 1.7;
}
.vlp__row-block--warn p { color: #b45309; }
.vlp__row-block--muted p { color: #94a3b8; }

.vlp__history-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 4px;
}
.vlp__history-list li {
  display: inline-flex;
  gap: 12px;
  font-size: 12px;
  color: #475569;
}
.vlp__history-list li span:first-child { color: #1f2933; font-weight: 800; min-width: 52px; }

@media (max-width: 640px) {
  .vlp__row-head {
    grid-template-columns: 24px 24px minmax(0, 1fr) auto;
    gap: 8px;
  }
  .vlp__row-caret { display: none; }
  .vlp__row-metrics { gap: 6px; }
  .vlp__metric--confidence { display: none; }
}
</style>
