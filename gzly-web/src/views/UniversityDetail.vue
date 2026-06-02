<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUniversityBySchoolId, getUniversityDetail, getUniversityOfficialLinks } from '@/api/university'
import {
  AlertTriangle,
  ArrowLeft,
  Award,
  BarChart3,
  BookOpen,
  BriefcaseBusiness,
  Building2,
  Crown,
  ExternalLink,
  FileText,
  Globe,
  GraduationCap,
  Link2,
  Mail,
  MapPin,
  Newspaper,
  Phone,
  Play,
  ShieldCheck,
  Star,
  UserPlus,
  Wallet,
} from 'lucide-vue-next'
import type { OfficialLink, University } from '@/types'
import axios from 'axios'
import { listMedia, checkSchoolHasAdmin } from '@/api/alumni'
import { sanitizeHttpUrl } from '@/utils/markdown'
import SafeExternalLink from '@/components/SafeExternalLink.vue'
import UniversityQa from '@/components/UniversityQa.vue'
import { getProvinceConfig, normalizeProvinceCode } from '@/constants/provinces'

const route = useRoute()
const router = useRouter()
const uni = ref<University | null>(null)
const officialLink = ref<OfficialLink | null>(null)
const loading = ref(true)
const cdnInfo = ref<Record<string, any> | null>(null)
const showVideo = ref(false)
const currentProvinceCode = computed(() => normalizeProvinceCode(route.query.provinceCode))
const currentProvince = computed(() => getProvinceConfig(currentProvinceCode.value))

onMounted(async () => {
  const id = Number(route.params.id)
  const schoolId = String(route.query.schoolId || '')
  if (!id && !schoolId) { router.back(); return }
  try {
    let data: University | null = null
    if (schoolId) {
      const res = await getUniversityBySchoolId(schoolId, currentProvinceCode.value)
      if (res.data.code === 0 && res.data.data) data = res.data.data
    }
    if (!data && id) {
      const res = await getUniversityDetail(id, currentProvinceCode.value)
      if (res.data.code === 0 && res.data.data) data = res.data.data
    }
    if (data) {
      uni.value = data
      if (uni.value?.schoolId) {
        fetchCdnInfo(uni.value.schoolId)
        fetchOfficialLinks(uni.value.schoolId)
      }
    }
  } catch { /* */ } finally {
    loading.value = false
  }
})

const MAX_GALLERY_PHOTOS = 12
const MAX_HERO_BANNERS = 6

const heroBanners = ref<string[]>([])
const photos = ref<string[]>([])
const activePhotoIdx = ref(0)
const alumniNews = ref<any[]>([])
const alumniFiles = ref<any[]>([])
const hasAdmin = ref(false)

function goUniversitySearch(): void {
  router.push({ path: '/university', query: currentProvince.value.routeQuery })
}

function mergeUniqueMedia(current: string[], incoming: string[], max: number): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const url of [...current, ...incoming]) {
    const normalized = String(url || '').trim()
    if (!normalized || seen.has(normalized)) continue
    seen.add(normalized)
    result.push(normalized)
    if (result.length >= max) break
  }
  return result
}

async function fetchCdnInfo(sid: string) {
  try {
    const r = await axios.get(`/cdn-proxy/www/2.0/school/${sid}/info.json`, {
      timeout: 8000,
    })
    if (r.data?.data) cdnInfo.value = r.data.data
  } catch { /* CDN data is optional */ }

  try {
    const r2 = await axios.get('/school_photos.json', { timeout: 5000 })
    const allPhotos = r2.data as Record<string, string[]>
    if (allPhotos[sid]) photos.value = mergeUniqueMedia([], allPhotos[sid], MAX_GALLERY_PHOTOS)
  } catch { /* photos are optional */ }

  try {
    const r3 = await listMedia(sid, 1)
    if (r3.data?.data) {
      const all = r3.data.data as any[]
      const ugcBanners = all.filter((m: any) => m.mediaType === 4)
      const ugcPhotos = all.filter((m: any) => m.mediaType === 1)
      heroBanners.value = mergeUniqueMedia(heroBanners.value, ugcBanners.map((m: any) => m.url), MAX_HERO_BANNERS)
      photos.value = mergeUniqueMedia(photos.value, ugcPhotos.map((m: any) => m.url), MAX_GALLERY_PHOTOS)
      alumniNews.value = all.filter((m: any) => m.mediaType === 2)
      alumniFiles.value = all.filter((m: any) => m.mediaType === 3 && alumniFileHref(m))
    }
  } catch { /* UGC content optional */ }

  try {
    const r4 = await checkSchoolHasAdmin(sid)
    if (r4.data?.data) hasAdmin.value = true
  } catch { /* optional */ }
}

async function fetchOfficialLinks(sid: string) {
  try {
    const res = await getUniversityOfficialLinks(sid, currentProvinceCode.value)
    officialLink.value = res.data?.data || null
  } catch {
    officialLink.value = null
  }
}

function openVideo() {
  showVideo.value = true
}

function closeVideo() {
  showVideo.value = false
}

function parseNewsCaption(caption: string) {
  try { return JSON.parse(caption) } catch { return { title: caption, content: '', link: '' } }
}

function safeUploadUrl(url?: string | null) {
  const value = String(url || '').trim()
  if (!value || /[\u0000-\u001F\u007F]/.test(value)) return ''
  return value.startsWith('/uploads/') && !value.includes('..') ? value : ''
}

function alumniFileHref(file: { url?: string | null }) {
  return safeUploadUrl(file.url) || sanitizeHttpUrl(file.url)
}

function safeTelHref(phone?: string | null) {
  const first = String(phone || '').split(',')[0].trim()
  if (!/^[+0-9][0-9+\-\s()]{4,30}$/.test(first)) return ''
  return `tel:${first.replace(/\s+/g, '')}`
}

function safeMailHref(email?: string | null) {
  const value = String(email || '').split(',')[0].trim()
  if (!/^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+$/.test(value)) return ''
  return `mailto:${value}`
}

function tagColor(tag: string): string {
  const map: Record<string, string> = {
    '985': '#dc2626', '211': '#d97706', '双一流': '#2563eb',
    '公办': '#059669', '民办': '#7c3aed', '中外合作办学': '#0891b2',
  }
  return map[tag] || '#64748b'
}

function tagIcon(tag: string) {
  if (tag === '985') return Crown
  if (tag === '211') return Award
  if (tag === '双一流') return Star
  return Building2
}

function isOpportunityCity(city = '', province = '') {
  return ['北京', '上海', '广州', '深圳', '杭州', '南京', '武汉', '成都', '西安', '重庆', '苏州']
    .some((keyword) => city.includes(keyword) || province.includes(keyword))
}

const heroTagList = computed(() => {
  if (!uni.value) return []
  const tags = [...(uni.value.tags || [])]
  const natureName = (uni.value as any).natureName || ''
  if (natureName && !tags.includes(natureName)) {
    tags.push(natureName)
  }
  return tags
})

const heroBackgroundUrl = computed(() => {
  if (heroBanners.value.length > 0) return heroBanners.value[0]
  if (photos.value.length > 0) return photos.value[0]
  if (cdnInfo.value?.video?.img_url) return cdnInfo.value.video.img_url
  if (cdnInfo.value?.video_pc?.img_url) return cdnInfo.value.video_pc.img_url
  return ''
})

const decisionCards = computed(() => {
  if (!uni.value) return []
  const tags = uni.value.tags || []
  const isSinoForeign = tags.includes('中外合作办学') || tags.includes('内地与港澳台合作办学') || ((uni.value as any).natureName || '').includes('中外合作')
  const isPrivate = (uni.value as any).natureName === '民办' || tags.includes('民办')
  const cityStrong = isOpportunityCity(uni.value.city || '', uni.value.province || '')
  return [
    {
      title: '平台判断',
      value: tags.includes('985') || tags.includes('211') || tags.includes('双一流') ? '平台资源较强' : '更看专业和城市匹配',
      hint: tags.includes('985') || tags.includes('211') || tags.includes('双一流')
        ? '如果你看重升学、平台和学校背景，这类院校通常更值得重点关注。'
        : '如果你更看重专业落地，就要结合专业方向和城市机会一起看，不要只看校名。',
      tone: 'primary',
    },
    {
      title: '成本提示',
      value: isSinoForeign ? '高成本合作办学' : isPrivate ? '需重点核对学费' : '常规预算更友好',
      hint: isSinoForeign
        ? '中外合作 / 港澳台合作项目通常学费更高，务必在招生章程中确认收费和培养模式。'
        : isPrivate
          ? '民办院校建议额外核对学费、住宿费和转专业规则。'
          : '当前学校更适合预算敏感型家庭作为常规备选。',
      tone: isSinoForeign || isPrivate ? 'warn' : 'safe',
    },
    {
      title: '城市机会',
      value: cityStrong ? '城市资源更集中' : '城市机会需结合行业判断',
      hint: cityStrong
        ? '实习、校招和生活选择通常更丰富，适合重视城市平台的考生。'
        : '如果你看重就业机会，建议把学校所在城市和目标行业一起对比。',
      tone: cityStrong ? 'primary' : 'default',
    },
    {
      title: '更适合谁',
      value: tags.includes('985') || tags.includes('211') || tags.includes('双一流') ? '学校优先 / 升学导向' : '专业优先 / 务实导向',
      hint: tags.includes('985') || tags.includes('211') || tags.includes('双一流')
        ? '更适合看重平台、升学环境和综合背景的考生。'
        : '更适合已经明确专业方向、希望尽快落到具体专业和就业路径的考生。',
      tone: 'default',
    },
  ]
})

const cautionList = computed(() => {
  if (!uni.value) return []
  const tags = uni.value.tags || []
  const cautions: string[] = []
  if (tags.includes('中外合作办学') || tags.includes('内地与港澳台合作办学')) {
    cautions.push('合作办学项目请重点核对学费、培养模式、证书授予和外语要求。')
  }
  if ((uni.value as any).natureName === '民办' || tags.includes('民办')) {
    cautions.push('民办院校建议额外确认学费、住宿费、调剂政策和就业去向。')
  }
  if (!cdnInfo.value?.ruanke_rank && !cdnInfo.value?.xueke_rank) {
    cautions.push('缺少公开排名或学科评估时，更要回到招生章程、专业目录和就业数据。')
  }
  if (!(uni.value as any).schoolSite) {
    cautions.push('当前缺少学校官网链接，建议人工补查招生章程和官方招生网。')
  }
  return cautions
})

const quickDecisionLinks = computed(() => {
  if (!uni.value) return []
  const links: Array<{ label: string; href: string }> = []
  const schoolSite = sanitizeHttpUrl(officialLink.value?.schoolSite) || sanitizeHttpUrl((uni.value as any).schoolSite)
  if (schoolSite) {
    links.push({ label: '学校官网', href: schoolSite })
  }
  const admissionSite = sanitizeHttpUrl(officialLink.value?.admissionSite)
  const admissionBrochureUrl = sanitizeHttpUrl(officialLink.value?.admissionBrochureUrl)
  const majorCatalogUrl = sanitizeHttpUrl(officialLink.value?.majorCatalogUrl)
  const tuitionInfoUrl = sanitizeHttpUrl(officialLink.value?.tuitionInfoUrl)
  if (admissionSite) links.push({ label: '招生网', href: admissionSite })
  if (admissionBrochureUrl) links.push({ label: '招生章程', href: admissionBrochureUrl })
  if (majorCatalogUrl) links.push({ label: '专业目录', href: majorCatalogUrl })
  if (tuitionInfoUrl) links.push({ label: '收费标准', href: tuitionInfoUrl })
  const phoneHref = safeTelHref((uni.value as any).phone)
  if (phoneHref) {
    links.push({ label: '招生电话', href: phoneHref })
  }
  const mailHref = safeMailHref((uni.value as any).email)
  if (mailHref) {
    links.push({ label: '招生邮箱', href: mailHref })
  }
  return links
})

const officialDocs = computed(() => {
  const schoolSite = sanitizeHttpUrl(officialLink.value?.schoolSite) || sanitizeHttpUrl((uni.value as any)?.schoolSite)
  const admissionSite = sanitizeHttpUrl(officialLink.value?.admissionSite)
  const admissionBrochureUrl = sanitizeHttpUrl(officialLink.value?.admissionBrochureUrl)
  const majorCatalogUrl = sanitizeHttpUrl(officialLink.value?.majorCatalogUrl)
  const docs = [
    { key: 'schoolSite', label: '学校官网', url: schoolSite, status: schoolSite ? '已收录' : '待补充' },
    { key: 'admissionSite', label: '招生网', url: admissionSite, status: admissionSite ? '已收录' : '待补充' },
    { key: 'admissionBrochureUrl', label: '招生章程', url: admissionBrochureUrl, status: admissionBrochureUrl ? '已收录' : '待补充' },
    { key: 'majorCatalogUrl', label: '专业目录', url: majorCatalogUrl, status: majorCatalogUrl ? '已收录' : '待补充' },
  ]

  const tuitionInfoUrl = sanitizeHttpUrl(officialLink.value?.tuitionInfoUrl)
  if (tuitionInfoUrl) {
    docs.push({
      key: 'tuitionInfoUrl',
      label: '收费标准',
      url: tuitionInfoUrl,
      status: '已收录',
    })
  }

  return docs
})

const tuitionInfo = computed(() => {
  if (!officialLink.value) return null
  const summary = officialLink.value.tuitionSummary?.trim() || ''
  const remark = officialLink.value.tuitionRemark?.trim() || ''
  const url = sanitizeHttpUrl(officialLink.value.tuitionInfoUrl) || sanitizeHttpUrl(officialLink.value.admissionBrochureUrl) || ''
  if (!summary && !remark && !url) return null
  return { summary, remark, url }
})

const parsedRules = computed(() => {
  if (!officialLink.value) return []
  return [
    { key: 'adjustmentRule', label: '调剂规则', value: officialLink.value.adjustmentRule },
    { key: 'foreignLanguageRule', label: '外语要求', value: officialLink.value.foreignLanguageRule },
    { key: 'physicalExamRule', label: '体检限制', value: officialLink.value.physicalExamRule },
    { key: 'singleSubjectRule', label: '单科要求', value: officialLink.value.singleSubjectRule },
    { key: 'majorCatalogSummary', label: '专业目录摘要', value: officialLink.value.majorCatalogSummary },
  ].filter(item => item.value)
})
</script>

<template>
  <div class="detail-page">
    <header class="page-header">
      <div class="page-header-inner">
        <button class="back-btn" @click="goUniversitySearch"><ArrowLeft :size="20" /></button>
        <h1 class="page-header-title">{{ uni?.name || '院校详情' }}</h1>
      </div>
    </header>

    <div v-if="loading" class="loading-state">
      <van-loading size="24" color="var(--gz-primary)" />
    </div>

    <div v-else-if="!uni" class="empty-state">
      <van-empty description="院校不存在" />
    </div>

    <div v-else class="detail-body">
      <!-- Hero Card — Enhanced -->
      <div class="hero-card">
        <div
          class="hero-bg"
          :style="heroBackgroundUrl ? { backgroundImage: 'url(' + heroBackgroundUrl + ')' } : {}"
        >
          <div class="hero-bg-overlay"></div>
          <div class="hero-pattern"></div>
        </div>
        <div class="hero-content">
          <div class="hero-logo-wrap">
            <img
              v-if="uni.logoUrl"
              class="hero-logo"
              :src="uni.logoUrl"
              :alt="uni.name"
            />
            <div v-else class="hero-logo hero-logo--placeholder">{{ uni.name.charAt(0) }}</div>
          </div>
          <div class="hero-text">
            <div class="hero-meta-row">
              <span class="hero-location"><MapPin :size="12" /> {{ uni.province }} · {{ uni.city }}</span>
              <span v-if="(uni as any).typeName" class="hero-type">{{ (uni as any).typeName }}</span>
            </div>
            <p class="hero-subline">{{ (uni as any).natureName || '办学性质待补充' }}<span v-if="(uni as any).belong"> · {{ (uni as any).belong }}</span></p>
            <div class="hero-tags">
              <span
                v-for="tag in heroTagList"
                :key="tag"
                class="hero-tag"
                :style="{ color: '#fff', background: tagColor(tag) }"
              >
                <component :is="tagIcon(tag)" :size="11" />
                {{ tag }}
              </span>
            </div>
          </div>
        </div>
        <div v-if="cdnInfo?.motto" class="hero-motto">{{ cdnInfo.motto }}</div>
      </div>

      <!-- Hero Banner -->
      <div v-if="heroBanners.length > 0" class="gallery-section">
        <van-swipe class="banner-swipe" :autoplay="4000" indicator-color="#2563eb">
          <van-swipe-item v-for="(url, idx) in heroBanners" :key="'b'+idx">
            <img class="banner-img" :src="url" loading="lazy" @error="($event.target as HTMLImageElement).style.display = 'none'" />
          </van-swipe-item>
        </van-swipe>
      </div>

      <!-- Photo Grid (九宫格) -->
      <div v-if="photos.length > 0" class="gallery-section">
        <div class="gallery-header">
          <h3 class="gallery-title">校园风光</h3>
          <span class="gallery-count">{{ photos.length }} 张</span>
        </div>
        <div class="photo-nine-grid">
          <div v-for="(url, idx) in photos" :key="idx" class="nine-grid-item">
            <img :src="url" loading="lazy" @error="($event.target as HTMLImageElement).style.display = 'none'" />
          </div>
        </div>
      </div>

      <!-- Legacy swiper for backward compat (hidden if new grid shows) -->
      <div v-if="false" class="gallery-section">
        <van-swipe
          class="gallery-swipe"
          :autoplay="4000"
          indicator-color="#2563eb"
          @change="(idx: number) => activePhotoIdx = idx"
        >
          <van-swipe-item v-for="(url, idx) in photos" :key="idx">
            <img class="gallery-img" :src="url" loading="lazy" @error="($event.target as HTMLImageElement).style.display = 'none'" />
          </van-swipe-item>
        </van-swipe>
      </div>

      <!-- Video Banner -->
      <div v-if="cdnInfo?.video?.img_url || cdnInfo?.video_pc?.img_url" class="media-card" @click="openVideo">
        <img class="media-thumb" :src="cdnInfo.video?.img_url || cdnInfo.video_pc?.img_url" :alt="(uni?.name || '') + ' 宣传视频'" />
        <div class="media-play"><Play :size="28" /></div>
        <span class="media-label">校园宣传片</span>
      </div>

      <!-- Rankings -->
      <div v-if="cdnInfo?.ruanke_rank || cdnInfo?.us_rank" class="rank-section">
        <div v-if="cdnInfo.ruanke_rank && cdnInfo.ruanke_rank !== '0'" class="rank-item">
          <span class="rank-num">{{ cdnInfo.ruanke_rank }}</span>
          <span class="rank-label">软科排名</span>
        </div>
        <div v-if="cdnInfo.us_rank && cdnInfo.us_rank !== '0' && cdnInfo.us_rank !== 0" class="rank-item">
          <span class="rank-num">{{ cdnInfo.us_rank }}</span>
          <span class="rank-label">US排名</span>
        </div>
        <div v-if="cdnInfo.num_academician" class="rank-item">
          <span class="rank-num">{{ cdnInfo.num_academician }}</span>
          <span class="rank-label">院士</span>
        </div>
        <div v-if="cdnInfo.num_doctor" class="rank-item">
          <span class="rank-num">{{ cdnInfo.num_doctor }}</span>
          <span class="rank-label">博士点</span>
        </div>
        <div v-if="cdnInfo.num_master" class="rank-item">
          <span class="rank-num">{{ cdnInfo.num_master }}</span>
          <span class="rank-label">硕士点</span>
        </div>
      </div>

      <div class="info-section">
        <h3 class="section-title"><GraduationCap :size="16" /> 报考决策摘要</h3>
        <div class="decision-card-grid">
          <div
            v-for="card in decisionCards"
            :key="card.title"
            class="decision-card"
            :class="`decision-card--${card.tone}`"
          >
            <div class="decision-card-title">{{ card.title }}</div>
            <div class="decision-card-value">{{ card.value }}</div>
            <p class="decision-card-hint">{{ card.hint }}</p>
          </div>
        </div>
        <div class="decision-links-card info-card">
          <div class="decision-links-head">
            <div>
              <h4>填报前建议先核对这几项</h4>
              <p>把“官网、招生章程、收费标准、专业限制”放在最后确认环节。</p>
            </div>
            <div class="decision-link-actions">
              <SafeExternalLink
                v-for="link in quickDecisionLinks"
                :key="link.label"
                class="decision-link-btn"
                :url="link.href"
              >
                {{ link.label }}
                <ExternalLink :size="13" />
              </SafeExternalLink>
            </div>
          </div>
          <div v-if="cautionList.length" class="decision-warning-list">
            <div v-for="warning in cautionList" :key="warning" class="decision-warning-item">
              <AlertTriangle :size="14" />
              <span>{{ warning }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="info-section">
        <h3 class="section-title"><Wallet :size="16" /> 收费信息</h3>
        <div v-if="tuitionInfo" class="info-card tuition-card">
          <div class="tuition-card-head">
            <div>
              <h4 class="tuition-card-title">收费摘要</h4>
              <p class="tuition-card-subtitle">优先展示已提取的收费内容，帮助快速判断学校成本。</p>
            </div>
            <SafeExternalLink
              v-if="tuitionInfo.url"
              class="tuition-card-link"
              :url="tuitionInfo.url"
            >
              查看原文
              <ExternalLink :size="13" />
            </SafeExternalLink>
          </div>
          <p v-if="tuitionInfo.summary" class="tuition-card-text">{{ tuitionInfo.summary }}</p>
          <p v-else class="tuition-card-text">{{ tuitionInfo.remark }}</p>
          <p
            v-if="tuitionInfo.summary && tuitionInfo.remark && tuitionInfo.remark !== tuitionInfo.summary"
            class="tuition-card-note"
          >
            {{ tuitionInfo.remark }}
          </p>
        </div>
        <div v-else class="info-card official-remark-card">
          <h4>收费信息</h4>
          <p>暂未提取到收费摘要，建议优先查看招生章程或学校官网核验学费与住宿费。</p>
        </div>
      </div>

      <div class="info-section">
        <h3 class="section-title"><ShieldCheck :size="16" /> 官方报考入口</h3>
        <div class="official-doc-grid">
          <SafeExternalLink
            v-for="doc in officialDocs"
            :key="doc.key"
            class="official-doc-card"
            :class="{ 'official-doc-card--empty': !doc.url }"
            :url="doc.url"
            show-invalid
          >
            <div class="official-doc-head">
              <span class="official-doc-title">{{ doc.label }}</span>
              <span class="official-doc-status" :class="doc.url ? 'official-doc-status--ok' : 'official-doc-status--empty'">
                {{ doc.status }}
              </span>
            </div>
            <p class="official-doc-url">
              {{ doc.url || '暂未收录，建议前往学校招生网或官网核验。' }}
            </p>
            <span class="official-doc-action">{{ doc.url ? '打开链接' : '待补充' }}</span>
          </SafeExternalLink>
        </div>
        <div v-if="officialLink?.tuitionRemark" class="info-card official-remark-card">
          <h4>收费提示</h4>
          <p>{{ officialLink.tuitionRemark }}</p>
        </div>
      </div>

      <div v-if="parsedRules.length" class="info-section">
        <h3 class="section-title"><Wallet :size="16" /> 规则摘要提要</h3>
        <div class="rule-grid">
          <div v-for="rule in parsedRules" :key="rule.key" class="rule-card info-card">
            <div class="rule-title">{{ rule.label }}</div>
            <p class="rule-text">{{ rule.value }}</p>
          </div>
        </div>
      </div>

      <!-- Subject Rankings -->
      <div v-if="cdnInfo?.xueke_rank && Object.keys(cdnInfo.xueke_rank).length > 0" class="info-section">
        <h3 class="section-title"><BarChart3 :size="16" /> 学科评估</h3>
        <div class="info-card xueke-grid">
          <div v-for="(count, grade) in cdnInfo.xueke_rank" :key="grade" class="xueke-item" :class="'xueke--' + String(grade).charAt(0).toLowerCase()">
            <span class="xueke-grade">{{ grade }}</span>
            <span class="xueke-count">{{ count }}个学科</span>
          </div>
        </div>
      </div>

      <!-- Info Cards -->
      <div class="info-section info-section--intro">
        <h3 class="section-title"><BookOpen :size="16" /> 学校简介</h3>
        <div class="info-card">
          <p class="intro-text">{{ (uni as any).content || '暂无简介信息' }}</p>
        </div>
      </div>

      <div class="info-two-col">
        <div class="info-section">
          <h3 class="section-title"><Building2 :size="16" /> 基本信息</h3>
          <div class="info-card info-grid">
            <div class="info-row">
              <span class="info-label">隶属</span>
              <span class="info-value">{{ (uni as any).belong || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">办学性质</span>
              <span class="info-value">{{ (uni as any).natureName || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">院校类型</span>
              <span class="info-value">{{ (uni as any).typeName || '-' }}</span>
            </div>
            <div v-if="(uni as any).address" class="info-row">
              <span class="info-label">地址</span>
              <span class="info-value info-value--address">{{ (uni as any).address }}</span>
            </div>
          </div>
        </div>

        <div v-if="(uni as any).phone || (uni as any).email || (uni as any).schoolSite" class="info-section">
          <h3 class="section-title"><Phone :size="16" /> 联系方式</h3>
          <div class="info-card contact-list">
            <a v-if="safeTelHref((uni as any).phone)" class="contact-item" :href="safeTelHref((uni as any).phone)">
              <Phone :size="15" />
              <span>{{ (uni as any).phone }}</span>
            </a>
            <a v-if="safeMailHref((uni as any).email)" class="contact-item" :href="safeMailHref((uni as any).email)">
              <Mail :size="15" />
              <span>{{ (uni as any).email }}</span>
            </a>
            <SafeExternalLink v-if="sanitizeHttpUrl((uni as any).schoolSite)" class="contact-item" :url="(uni as any).schoolSite">
              <Globe :size="15" />
              <span>{{ (uni as any).schoolSite }}</span>
            </SafeExternalLink>
          </div>
        </div>
      </div>

      <!-- Alumni News -->
      <div class="info-section">
        <h3 class="section-title"><Newspaper :size="16" /> 校园资讯</h3>
        <div v-if="alumniNews.length > 0" class="alumni-news-list">
          <div v-for="item in alumniNews" :key="item.id" class="alumni-news-card info-card">
            <h4 class="alumni-news-title">{{ parseNewsCaption(item.caption).title }}</h4>
            <p v-if="parseNewsCaption(item.caption).content" class="alumni-news-content">
              {{ parseNewsCaption(item.caption).content }}
            </p>
            <SafeExternalLink v-if="sanitizeHttpUrl(parseNewsCaption(item.caption).link)" :url="parseNewsCaption(item.caption).link" class="alumni-news-link">
              <Link2 :size="13" /> 查看详情
            </SafeExternalLink>
          </div>
        </div>
        <div v-else class="info-card alumni-empty-card">
          <Newspaper :size="28" class="alumni-empty-icon" />
          <p class="alumni-empty-text">暂无校园资讯</p>
          <span class="alumni-empty-hint">{{ hasAdmin ? '维护员正在整理中...' : '成为校友维护员即可发布学校最新动态' }}</span>
          <button v-if="!hasAdmin" class="alumni-empty-btn" @click="router.push('/alumni/apply')">
            <UserPlus :size="14" /> 申请成为维护员
          </button>
        </div>
      </div>

      <!-- Alumni Files -->
      <div class="info-section">
        <h3 class="section-title"><FileText :size="16" /> 相关资料</h3>
        <div v-if="alumniFiles.length > 0" class="info-card alumni-file-list">
          <a v-for="f in alumniFiles" :key="f.id" :href="alumniFileHref(f)" target="_blank" rel="noopener noreferrer" class="alumni-file-item">
            <div class="alumni-file-icon"><FileText :size="18" /></div>
            <span class="alumni-file-name">{{ f.caption || '文件下载' }}</span>
          </a>
        </div>
        <div v-else class="info-card alumni-empty-card">
          <FileText :size="28" class="alumni-empty-icon" />
          <p class="alumni-empty-text">暂无相关资料</p>
          <span class="alumni-empty-hint">{{ hasAdmin ? '维护员正在整理中...' : '校友维护员可上传招生简章、专业介绍等文件' }}</span>
        </div>
      </div>

      <!-- 问答专区 -->
      <div v-if="uni?.schoolId" class="info-section">
        <UniversityQa :school-id="uni.schoolId" />
      </div>

    </div>

    <!-- Video Modal -->
    <teleport to="body">
      <div v-if="showVideo" class="video-overlay" @click.self="closeVideo">
        <div class="video-modal">
          <button class="video-close" @click="closeVideo">&times;</button>
          <video
            v-if="cdnInfo?.video?.url || cdnInfo?.video_pc?.url"
            class="video-player"
            :src="cdnInfo.video?.url || cdnInfo.video_pc?.url"
            controls
            autoplay
            playsinline
          />
        </div>
      </div>
    </teleport>
  </div>
</template>

<style scoped>
.detail-page {
  min-height: 100dvh;
  background: #f9fafb;
  padding-bottom: 40px;
}

/* ---- Header ---- */
.page-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}
.page-header-inner {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 1200px;
  margin: 0 auto;
  padding: 12px 16px;
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
  transition: background 0.15s;
}
.back-btn:hover { background: #e5e7eb; }
.page-header-title {
  flex: 1;
  font-size: 17px;
  font-weight: 700;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.loading-state { display: flex; justify-content: center; padding: 80px 0; }

/* ---- Body ---- */
.detail-body {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px;
}

/* ---- Hero Enhanced ---- */
.hero-card {
  position: relative;
  margin-top: 12px;
  border-radius: 16px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
}
.hero-bg {
  position: relative;
  height: 190px;
  background: linear-gradient(135deg, #f0f4ff 0%, #e8ecf8 100%);
  background-size: cover;
  background-position: center;
  overflow: hidden;
}
.hero-bg-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(15,23,42,0.12) 0%, rgba(15,23,42,0.28) 100%);
}
.hero-pattern {
  display: none;
}
.hero-content {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 16px;
  margin-top: 0;
  padding: 16px 20px 20px;
}
.hero-logo-wrap {
  position: relative;
  margin-top: -22px;
}
.hero-logo-wrap::after {
  display: none;
}
.hero-logo {
  width: 74px;
  height: 74px;
  border-radius: 18px;
  border: 3px solid #fff;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  background: #fff;
  object-fit: contain;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}
.hero-logo--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 800;
  color: #fff;
  background: #4f46e5;
}
.hero-text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: flex-start;
  flex: 1;
  min-width: 0;
  padding-top: 8px;
}
.hero-meta-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 0;
}
.hero-location {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 13px;
  color: #64748b;
}
.hero-type {
  font-size: 12px;
  color: #2563eb;
  background: #eff6ff;
  padding: 2px 10px;
  border-radius: 20px;
  font-weight: 600;
}
.hero-subline {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: #64748b;
  text-align: left;
}
.hero-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: 6px;
  margin-top: 14px;
}
.hero-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}
.hero-tag--outline {
  color: #64748b !important;
  background: #f8fafc !important;
  border: 1px solid #e2e8f0;
  box-shadow: none;
}
.hero-motto {
  text-align: center;
  padding: 10px 20px 16px;
  font-size: 13px;
  color: #64748b;
  font-style: italic;
  letter-spacing: 0.06em;
  border-top: 1px solid #f1f5f9;
}

/* ---- Banner ---- */
.banner-swipe { border-radius: 12px; overflow: hidden; }
.banner-img { width: 100%; height: 180px; object-fit: cover; }

/* ---- Nine Grid ---- */
.photo-nine-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  border-radius: 12px;
  overflow: hidden;
}
.nine-grid-item {
  aspect-ratio: 1;
  overflow: hidden;
}
.nine-grid-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* ---- Photo Carousel ---- */
.gallery-section {
  margin-top: 16px;
}
.gallery-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding: 0 2px;
}
.gallery-title {
  font-size: 15px;
  font-weight: 700;
  color: #334155;
}
.gallery-count {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}
.gallery-swipe {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
}
.gallery-img {
  width: 100%;
  height: 220px;
  object-fit: cover;
  display: block;
}

/* ---- Tuition Card ---- */
.tuition-card {
  border: 1px solid #e5e7eb;
  background: linear-gradient(180deg, #ffffff 0%, #fafaf9 100%);
}

.tuition-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.tuition-card-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 4px;
}

.tuition-card-subtitle {
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

.tuition-card-link {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 12px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.tuition-card-text {
  font-size: 13px;
  line-height: 1.85;
  color: #334155;
}

.tuition-card-note {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #e5e7eb;
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

/* ---- Media Banner ---- */
.media-card {
  position: relative;
  margin-top: 16px;
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
}
.media-thumb {
  width: 100%;
  height: 200px;
  object-fit: cover;
  display: block;
  transition: transform 0.3s ease;
}
.media-card:hover .media-thumb {
  transform: scale(1.02);
}
.media-play {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(0,0,0,0.5);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  transition: transform 0.2s ease;
}
.media-card:hover .media-play {
  transform: translate(-50%, -50%) scale(1.1);
}
.media-label {
  position: absolute;
  bottom: 10px;
  left: 14px;
  padding: 3px 10px;
  border-radius: 6px;
  background: rgba(0,0,0,0.5);
  backdrop-filter: blur(4px);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}
.media-video {
  width: 100%;
  max-height: 300px;
  background: #000;
  display: block;
}

/* ---- Rankings ---- */
.rank-section {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  overflow-x: auto;
  scrollbar-width: none;
}
.rank-section::-webkit-scrollbar { display: none; }
.rank-item {
  flex: 1;
  min-width: 70px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 14px 8px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
}
.rank-num {
  font-size: 22px;
  font-weight: 800;
  color: #111827;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.rank-label {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 500;
}

.decision-card-grid {
  display: grid;
  gap: 10px;
}

.decision-card {
  padding: 16px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
}

.decision-card--primary {
  background: linear-gradient(180deg, #ffffff, #eff6ff);
}

.decision-card--warn {
  background: linear-gradient(180deg, #ffffff, #fff7ed);
}

.decision-card--safe {
  background: linear-gradient(180deg, #ffffff, #ecfdf5);
}

.decision-card-title {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.decision-card-value {
  margin-top: 8px;
  font-size: 19px;
  line-height: 1.3;
  font-weight: 900;
  color: #0f172a;
}

.decision-card-hint {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: #475569;
}

.decision-links-card {
  margin-top: 10px;
}

.decision-links-head {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.decision-links-head h4 {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
}

.decision-links-head p {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

.decision-link-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.decision-link-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 12px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.decision-warning-list {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.decision-warning-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 12px;
  line-height: 1.7;
}

.official-doc-grid {
  display: grid;
  gap: 10px;
}

.official-doc-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  text-decoration: none;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.official-doc-card:not(.official-doc-card--empty):hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.official-doc-card--empty {
  cursor: default;
  background: #f8fafc;
}

.official-doc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.official-doc-title {
  font-size: 14px;
  font-weight: 800;
  color: #0f172a;
}

.official-doc-status {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.official-doc-status--ok {
  background: #ecfdf5;
  color: #047857;
}

.official-doc-status--empty {
  background: #f1f5f9;
  color: #64748b;
}

.official-doc-url {
  font-size: 12px;
  line-height: 1.7;
  color: #475569;
  word-break: break-all;
}

.official-doc-action {
  font-size: 12px;
  font-weight: 700;
  color: #2563eb;
}

.official-remark-card {
  margin-top: 10px;
}

.official-remark-card h4 {
  font-size: 14px;
  font-weight: 800;
  color: #0f172a;
}

.official-remark-card p {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: #475569;
}

.rule-grid {
  display: grid;
  gap: 10px;
}

.rule-card {
  padding: 16px 18px;
}

.rule-title {
  font-size: 12px;
  font-weight: 800;
  color: #64748b;
}

.rule-text {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.75;
  color: #334155;
}

/* ---- Subject Rankings ---- */
.xueke-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 14px 16px;
}
.xueke-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 10px 16px;
  border-radius: 10px;
  min-width: 64px;
}
.xueke-grade {
  font-size: 16px;
  font-weight: 800;
}
.xueke-count {
  font-size: 10px;
  color: #64748b;
}
.xueke--a { background: #fef3c7; }
.xueke--a .xueke-grade { color: #d97706; }
.xueke--b { background: #dbeafe; }
.xueke--b .xueke-grade { color: #2563eb; }
.xueke--c { background: #f1f5f9; }
.xueke--c .xueke-grade { color: #64748b; }

/* ---- (motto moved into hero-motto) ---- */

/* ---- Sections ---- */
.info-section {
  margin-top: 20px;
}
.info-two-col {
  display: flex;
  flex-direction: column;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 10px;
  padding-left: 2px;
}
.info-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 18px;
  border: 1px solid #e5e7eb;
}
.intro-text {
  font-size: 14px;
  line-height: 1.75;
  color: #475569;
  text-align: justify;
}
.info-grid {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.info-row {
  display: flex;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}
.info-row:last-child { border-bottom: none; }
.info-label {
  flex-shrink: 0;
  width: 80px;
  font-size: 13px;
  color: #94a3b8;
  font-weight: 500;
}
.info-value {
  flex: 1;
  font-size: 14px;
  color: #334155;
  font-weight: 500;
}
.info-value--address {
  font-size: 13px;
  line-height: 1.5;
}

/* ---- Contact ---- */
.contact-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 4px 18px;
}
.contact-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
  text-decoration: none;
  color: #2563eb;
  font-size: 13px;
  transition: opacity 0.15s;
}
.contact-item:last-child { border-bottom: none; }
.contact-item:hover { opacity: 0.8; }
.contact-item:active { opacity: 0.6; }

/* ---- Alumni News ---- */
.alumni-news-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.alumni-news-card {
  cursor: default;
}
.alumni-news-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 6px;
}
.alumni-news-content {
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
  margin-bottom: 8px;
}
.alumni-news-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #2563eb;
  text-decoration: none;
  font-weight: 500;
}
.alumni-news-link:hover { text-decoration: underline; }

/* ---- Alumni Files ---- */
.alumni-file-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 4px 18px;
}
.alumni-file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
  text-decoration: none;
  color: #2563eb;
  transition: opacity 0.15s;
}
.alumni-file-item:last-child { border-bottom: none; }
.alumni-file-item:hover { opacity: 0.8; }
.alumni-file-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #eff6ff;
  color: #2563eb;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.alumni-file-name {
  font-size: 14px;
  font-weight: 500;
}

/* ---- Alumni Empty State ---- */
.alumni-empty-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 20px;
  text-align: center;
}
.alumni-empty-icon {
  color: #cbd5e1;
}
.alumni-empty-text {
  font-size: 15px;
  font-weight: 600;
  color: #64748b;
}
.alumni-empty-hint {
  font-size: 12px;
  color: #94a3b8;
}
.alumni-empty-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 6px;
  padding: 8px 18px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  color: #374151;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.alumni-empty-btn:hover {
  background: #f3f4f6;
  border-color: #9ca3af;
}

/* ---- Alumni CTA ---- */
.alumni-cta {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 24px;
  margin-bottom: 20px;
  padding: 16px 20px;
  background: linear-gradient(135deg, #eff6ff, #f0fdf4);
  border: 1px solid #dbeafe;
  border-radius: 14px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  color: #2563eb;
}
.alumni-cta:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(37,99,235,0.1);
}
.alumni-cta-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.alumni-cta-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e40af;
}
.alumni-cta-desc {
  font-size: 12px;
  color: #64748b;
}

/* ---- Video Modal ---- */
.video-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.video-modal {
  position: relative;
  width: 100%;
  max-width: 800px;
  border-radius: 16px;
  overflow: hidden;
  background: #000;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}
.video-close {
  position: absolute;
  top: 8px;
  right: 12px;
  z-index: 10;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(8px);
  color: #fff;
  font-size: 24px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}
.video-close:hover {
  background: rgba(255, 255, 255, 0.4);
}
.video-player {
  width: 100%;
  max-height: 70vh;
  display: block;
}

/* ==== Desktop (>= 768px) ==== */
@media (min-width: 768px) {
  .page-header-inner {
    padding: 14px 32px;
  }
  .page-header-title {
    font-size: 19px;
  }
  .detail-body {
    padding: 0 32px;
  }
  .media-thumb {
    height: 280px;
  }
  .media-card {
    border-radius: 16px;
  }
  .rank-item {
    padding: 18px 12px;
  }
  .rank-num {
    font-size: 26px;
  }
  .rank-label {
    font-size: 12px;
  }
  .hero-bg {
    height: 200px;
  }
  .hero-content {
    flex-direction: row;
    align-items: flex-start;
    gap: 28px;
    margin-top: 0;
    padding: 22px 36px 24px;
  }
  .hero-logo-wrap {
    margin-top: -56px;
  }
  .hero-logo {
    width: 112px;
    height: 112px;
    border-radius: 28px;
    border-width: 5px;
  }
  .hero-logo--placeholder {
    font-size: 44px;
  }
  .hero-text {
    align-items: flex-start;
    padding-bottom: 4px;
  }
  .hero-tags {
    justify-content: flex-start;
  }
  .hero-tag {
    font-size: 12px;
    padding: 5px 14px;
  }
  .hero-motto {
    text-align: left;
    padding: 12px 36px 18px;
  }

  .decision-card-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .official-doc-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .rule-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .decision-links-head {
    flex-direction: row;
    justify-content: space-between;
    align-items: flex-start;
  }

  .info-two-col {
    flex-direction: row;
    gap: 20px;
  }
  .info-two-col > .info-section {
    flex: 1;
    min-width: 0;
  }

  .info-card {
    padding: 20px 24px;
    border-radius: 16px;
  }
  .intro-text {
    font-size: 15px;
  }
  .info-label {
    width: 90px;
    font-size: 14px;
  }
  .info-value {
    font-size: 15px;
  }
  .contact-item {
    font-size: 14px;
    gap: 12px;
  }
  .section-title {
    font-size: 16px;
  }
}

/* ==== Wide Desktop (>= 1024px) ==== */
@media (min-width: 1024px) {
  .detail-body {
    padding: 0 48px;
  }
  .hero-content {
    padding: 24px 48px 32px;
  }
  .hero-logo-wrap {
    margin-top: -60px;
  }
  .photo-nine-grid {
    grid-template-columns: repeat(4, 1fr);
    gap: 6px;
  }
  .banner-img {
    height: 260px;
  }
  .gallery-img {
    height: 300px;
  }
  .alumni-news-list {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 14px;
  }
  .alumni-file-list {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 0 24px;
  }
  .info-section--intro .intro-text {
    max-width: 800px;
  }
  .xueke-grid {
    gap: 12px;
    padding: 18px 24px;
  }
  .xueke-item {
    padding: 12px 20px;
  }
}

@media (min-width: 1280px) {
  .page-header-inner,
  .detail-body {
    max-width: 1360px;
  }

  .decision-card-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .official-doc-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .rule-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .info-two-col {
    gap: 24px;
  }
}

/* ==== Compact Mobile (<= 374px) ==== */
@media (max-width: 374px) {
  .detail-body {
    padding: 0 12px;
  }
  .hero-content {
    padding: 12px 14px 18px;
  }
  .hero-logo-wrap {
    margin-top: -18px;
  }
  .hero-logo {
    width: 64px;
    height: 64px;
    border-radius: 16px;
  }
  .info-card {
    padding: 12px 14px;
  }
}
</style>
