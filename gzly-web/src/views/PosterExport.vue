<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useVolunteerStore } from '@/stores/volunteer'
import { useAuthStore } from '@/stores/auth'
import { fetchVolunteerPlan } from '@/api/volunteer'
import { fetchMyPlanDetail } from '@/api/myPlans'
import { formDataFromPlan } from '@/utils/volunteer-plan'
import { showLoadingToast, closeToast, showToast } from 'vant'
import type { VolunteerPlan } from '@/types'
import {
  ArrowLeft,
  Image,
  Download,
  RefreshCw,
  AlertTriangle,
} from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const volunteerStore = useVolunteerStore()
const authStore = useAuthStore()

const posterRef = ref<HTMLDivElement | null>(null)
const posterImage = ref('')
const isExporting = ref(false)

function gradientColor(g: string): string {
  const map: Record<string, string> = {
    '冲': '#ef4444',
    '稳': '#2563eb',
    '保': '#10b981',
    '垫': '#f59e0b',
  }
  return map[g] || '#94a3b8'
}

async function exportPoster() {
  if (!posterRef.value || isExporting.value) return

  isExporting.value = true
  showLoadingToast({ message: '生成中...', forbidClick: true, duration: 0 })

  try {
    const html2canvas = (await import('html2canvas')).default
    // html2canvas 在 cloneDocument 里渲染时不会继承全局 body 字体栈，
    // 需要在 onclone 阶段把中文字体显式注入到克隆文档的 root，避免中文 tofu。
    const cjkFontStack = `-apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", "Heiti SC", "Hiragino Sans GB", "Source Han Sans CN", "Noto Sans CJK SC", sans-serif`
    const canvas = await html2canvas(posterRef.value, {
      scale: 2,
      useCORS: true,
      backgroundColor: '#ffffff',
      onclone(clonedDoc) {
        const root = clonedDoc.documentElement
        root.style.fontFamily = cjkFontStack
        const body = clonedDoc.body
        if (body) body.style.fontFamily = cjkFontStack
        clonedDoc.querySelectorAll<HTMLElement>('.poster-content, .poster-content *').forEach((el) => {
          el.style.fontFamily = cjkFontStack
        })
      },
    })

    const ctx = canvas.getContext('2d')
    if (ctx) {
      ctx.save()
      // 水印同样需要中文字体，否则 "AI生成 仅供参考" 也会 tofu
      ctx.font = `16px ${cjkFontStack}`
      ctx.fillStyle = 'rgba(0,0,0,0.06)'
      ctx.rotate((-30 * Math.PI) / 180)
      const text = 'AI生成 仅供参考'
      for (let y = -canvas.height; y < canvas.height * 2; y += 100) {
        for (let x = -canvas.width; x < canvas.width * 2; x += 200) {
          ctx.fillText(text, x, y)
        }
      }
      ctx.restore()
    }

    posterImage.value = canvas.toDataURL('image/png')
    closeToast()
  } catch {
    closeToast()
    showToast('导出失败，请重试')
  } finally {
    isExporting.value = false
  }
}

function savePoster() {
  if (!posterImage.value) return
  const link = document.createElement('a')
  link.download = `贵州志愿方案_${volunteerStore.formData.totalScore}分.png`
  link.href = posterImage.value
  link.click()
}

onMounted(async () => {
  if (volunteerStore.planItems.length === 0) {
    await restorePlan()
  }
  if (volunteerStore.planItems.length === 0) {
    showToast('暂无方案数据，请重新生成方案')
    router.push('/volunteer')
  }
})

async function restorePlan() {
  const saved = volunteerStore.getSavedPlanMeta()
  const planId = Number(route.query.planId || saved?.planId)
  if (!planId) return
  let safetyCode = String(route.query.safetyCode || route.query.accessKey || saved?.safetyCode || saved?.accessKey || '')
  if (!safetyCode && authStore.isAuthenticated) {
    try {
      const detailRes = await fetchMyPlanDetail(planId)
      const detail = detailRes.data.data as VolunteerPlan
      if (detail.items?.length) {
        volunteerStore.setPlanFromResponse(detail)
        volunteerStore.setFormData(formDataFromPlan(detail))
        return
      }
      safetyCode = detail.safetyCode || detail.accessKey || ''
    } catch {
      safetyCode = ''
    }
  }
  if (!safetyCode) return
  try {
    const res = await fetchVolunteerPlan(planId, safetyCode)
    const plan = res.data.data
    volunteerStore.setPlanFromResponse(plan, safetyCode)
    volunteerStore.setFormData(formDataFromPlan(plan))
    if (route.query.safetyCode || route.query.accessKey) {
      router.replace({ path: route.path, query: { ...route.query, safetyCode: undefined, accessKey: undefined } })
    }
  } catch {
    volunteerStore.clearPlan()
  }
}
</script>

<template>
  <div class="poster-page">
    <!-- Header -->
    <header class="page-header">
      <div class="page-header-inner">
        <button class="back-btn" @click="router.back()">
          <ArrowLeft :size="20" />
        </button>
        <h1 class="page-header-title">导出志愿表</h1>
      </div>
    </header>

    <!-- Poster Preview (for screenshot) -->
    <div v-show="!posterImage" class="poster-scroll">
      <div class="poster-wrap">
        <div ref="posterRef" class="poster-content">
          <!-- Header -->
          <div class="poster-header">
            <h2>贵州高考志愿方案</h2>
            <p class="poster-subtitle">AI 智能填报助手生成</p>
          </div>

          <!-- Student Info -->
          <div class="poster-info">
            <div class="info-row">
              <span>高考总分：<strong>{{ volunteerStore.formData.totalScore }}</strong> 分</span>
              <span>全省位次：<strong>{{ volunteerStore.formData.provinceRank?.toLocaleString() }}</strong></span>
            </div>
            <div class="info-row">
              <span>首选科目：<strong>{{ volunteerStore.formData.firstSubject }}</strong></span>
              <span>再选科目：<strong>{{ volunteerStore.formData.resubjects.join('、') }}</strong></span>
            </div>
          </div>

          <!-- Table -->
          <table class="poster-table">
            <thead>
              <tr>
                <th>序号</th>
                <th>梯度</th>
                <th>院校</th>
                <th>专业</th>
                <th>参考位次</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in volunteerStore.planItems" :key="item.index">
                <td class="td-index">{{ item.index }}</td>
                <td>
                  <span class="mini-gradient" :style="{ background: gradientColor(item.gradient) }">
                    {{ item.gradient }}
                  </span>
                </td>
                <td class="td-uni">{{ item.universityName }}</td>
                <td class="td-major">{{ item.majorName }}</td>
                <td class="td-rank">{{ item.historyMinRank.toLocaleString() }}</td>
              </tr>
            </tbody>
          </table>

          <!-- Footer Disclaimer -->
          <div class="poster-footer">
            <p class="footer-warning">
              <AlertTriangle :size="13" style="vertical-align: -2px;" />
              本方案由 AI 自动生成，仅供参考，不作为最终填报依据。
            </p>
            <p class="footer-tip">请以贵州省招生考试院、目标高校招生章程和专业目录为准，考生需结合官方信息自主决策。</p>
            <p class="footer-time">生成时间：{{ new Date().toLocaleString('zh-CN') }}</p>
          </div>

          <!-- Watermark -->
          <div class="watermark-layer"></div>
        </div>
      </div>
    </div>

    <!-- Exported Image Preview -->
    <div v-if="posterImage" class="preview-area">
      <div class="preview-wrap">
        <img :src="posterImage" class="preview-img" alt="志愿表长图" />
      </div>
    </div>

    <!-- Bottom Actions -->
    <div class="bottom-bar safe-bottom">
      <div class="bottom-inner">
        <template v-if="!posterImage">
          <button class="action-btn action-btn--primary action-btn--full" :disabled="isExporting" @click="exportPoster">
            <Image :size="18" />
            <span>{{ isExporting ? '生成中...' : '生成长图' }}</span>
          </button>
        </template>
        <template v-else>
          <button class="action-btn action-btn--primary" @click="savePoster">
            <Download :size="16" />
            <span>保存图片</span>
          </button>
          <button class="action-btn action-btn--outline" @click="posterImage = ''">
            <RefreshCw :size="16" />
            <span>重新生成</span>
          </button>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.poster-page {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
}

/* ---- Header ---- */
.page-header {
  position: sticky;
  top: 0;
  z-index: var(--gz-z-sticky);
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}

.page-header-inner {
  display: flex;
  align-items: center;
  gap: var(--gz-space-3);
  max-width: var(--gz-desktop-max);
  margin: 0 auto;
  padding: var(--gz-space-3) var(--gz-space-4);
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: #f3f4f6;
  color: #374151;
  cursor: pointer;
  transition: background var(--gz-transition-fast);
}

.back-btn:hover {
  background: #e5e7eb;
}

.page-header-title {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

/* ---- Poster Scroll ---- */
.poster-scroll {
  flex: 1;
  overflow-y: auto;
}

.poster-wrap {
  max-width: 640px;
  margin: 0 auto;
  padding: var(--gz-space-4);
}

.poster-content {
  position: relative;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  box-shadow: none;
}

.poster-header {
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: #fff;
  text-align: center;
  padding: 28px 16px 24px;
}

.poster-header h2 {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 4px;
}

.poster-subtitle {
  font-size: 13px;
  opacity: 0.8;
}

.poster-info {
  padding: var(--gz-space-4);
  background: var(--gz-primary-50);
  border-bottom: 1px solid rgba(37, 99, 235, 0.08);
}

.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--gz-text-secondary);
  margin-bottom: 4px;
}

.info-row:last-child {
  margin-bottom: 0;
}

.info-row strong {
  color: var(--gz-text-primary);
  font-weight: 600;
}

/* ---- Table ---- */
.poster-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.poster-table th {
  background: #f8f9fc;
  padding: 10px 6px;
  text-align: center;
  font-weight: 600;
  font-size: 11px;
  color: var(--gz-text-secondary);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.poster-table td {
  padding: 7px 6px;
  text-align: center;
  border-bottom: 1px solid rgba(0, 0, 0, 0.03);
  color: var(--gz-text-primary);
  font-size: 12px;
}

.poster-table tr:nth-child(even) td {
  background: rgba(0, 0, 0, 0.015);
}

.td-index {
  font-weight: 600;
  color: var(--gz-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.td-uni {
  text-align: left;
  font-weight: 500;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.td-major {
  text-align: left;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.td-rank {
  font-variant-numeric: tabular-nums;
  font-weight: 500;
}

.mini-gradient {
  display: inline-block;
  padding: 1px 8px;
  border-radius: var(--gz-radius-full);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
}

/* ---- Footer ---- */
.poster-footer {
  padding: var(--gz-space-4);
  text-align: center;
  background: var(--gz-warning-bg);
  border-top: 1px solid var(--gz-warning-border);
}

.footer-warning {
  font-size: 13px;
  font-weight: 600;
  color: var(--gz-warning-text);
  margin-bottom: 4px;
}

.footer-tip {
  font-size: 12px;
  color: var(--gz-warning-text);
  opacity: 0.85;
  margin-bottom: 4px;
}

.footer-time {
  font-size: 11px;
  color: var(--gz-text-tertiary);
}

.watermark-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 10;
  background-image:
    repeating-linear-gradient(
      -45deg,
      transparent,
      transparent 60px,
      rgba(0, 0, 0, 0.015) 60px,
      rgba(0, 0, 0, 0.015) 61px
    );
}

/* ---- Preview ---- */
.preview-area {
  flex: 1;
  overflow-y: auto;
}

.preview-wrap {
  max-width: 640px;
  margin: 0 auto;
  padding: var(--gz-space-4);
}

.preview-img {
  width: 100%;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: none;
}

/* ---- Bottom ---- */
.bottom-bar {
  background: #fff;
  border-top: 1px solid #e5e7eb;
}

.bottom-inner {
  display: flex;
  gap: var(--gz-space-3);
  max-width: 640px;
  margin: 0 auto;
  padding: var(--gz-space-3) var(--gz-space-4);
}

.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px 16px;
  border-radius: var(--gz-radius-full);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform var(--gz-transition-fast), box-shadow var(--gz-transition-fast);
  border: none;
}

.action-btn:active {
  transform: scale(0.97);
}

.action-btn--full {
  flex: unset;
  width: 100%;
}

.action-btn--primary {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: #fff;
  box-shadow: 0 3px 12px rgba(37, 99, 235, 0.3);
}

.action-btn--primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-btn--outline {
  background: #f9fafb;
  color: var(--gz-text-secondary);
  border: 1.5px solid rgba(0, 0, 0, 0.1);
}

.action-btn--outline:hover {
  border-color: var(--gz-primary);
  color: var(--gz-primary);
}
</style>
