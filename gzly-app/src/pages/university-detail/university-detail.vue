<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getUniversityDetail, getUniversityOfficialLinks } from '@/api/university'
import { getSchoolPhotos, getSchoolCdnInfo, type SchoolCdnInfo } from '@/utils/schoolMedia'
import type { University, OfficialLink } from '@/types'
import heroBg from '@/static/hero-bg.png'

const detail = ref<University | null>(null)
const links = ref<OfficialLink | null>(null)
const photos = ref<string[]>([])
const cdn = ref<SchoolCdnInfo | null>(null)
const loading = ref(true)
const errorMsg = ref('')

async function loadDetail(id: number) {
  loading.value = true
  errorMsg.value = ''
  try {
    detail.value = await getUniversityDetail(id)
    const schoolId = detail.value?.schoolId
    if (schoolId) {
      try {
        links.value = await getUniversityOfficialLinks(schoolId)
      } catch {
        links.value = null
      }
      // 校园图片 / CDN 档案：异步加载，失败不影响主信息
      getSchoolPhotos(schoolId)
        .then((p) => (photos.value = p))
        .catch(() => {})
      getSchoolCdnInfo(schoolId)
        .then((c) => (cdn.value = c))
        .catch(() => {})
    }
  } catch (e) {
    errorMsg.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

const ranks = computed(() => {
  const c = cdn.value
  if (!c) return []
  const out: { num: string; label: string }[] = []
  if (c.ruankeRank) out.push({ num: c.ruankeRank, label: '软科排名' })
  if (c.usRank) out.push({ num: c.usRank, label: 'US 排名' })
  if (c.numAcademician) out.push({ num: c.numAcademician, label: '院士' })
  if (c.numDoctor) out.push({ num: c.numDoctor, label: '博士点' })
  if (c.numMaster) out.push({ num: c.numMaster, label: '硕士点' })
  return out
})
const xueke = computed(() => {
  const x = cdn.value?.xuekeRank
  if (!x) return []
  return Object.keys(x).map((g) => ({ grade: g, count: x[g] }))
})
/** 顶部 banner：宣传片封面 > 首张校园图 > 兜底校园图（保证每个院校都有图） */
const bannerUrl = computed(() => cdn.value?.videoPoster || photos.value[0] || heroBg)
function previewPhoto(i: number) {
  if (!photos.value.length) return
  uni.previewImage({ urls: photos.value, current: i })
}

/* ---- 标签配色 ---- */
function tagColor(tag: string): string {
  const map: Record<string, string> = {
    '985': '#dc2626',
    '211': '#d97706',
    '双一流': '#2563eb',
    '公办': '#059669',
    '民办': '#7c3aed',
    '中外合作办学': '#0891b2',
  }
  return map[tag] || '#64748b'
}

const heroTags = computed(() => {
  const d = detail.value
  if (!d) return []
  const tags = [...(d.tags || [])]
  if (d.natureName && !tags.includes(d.natureName)) tags.push(d.natureName)
  return tags
})

/* ---- 报考决策摘要（纯前端计算，无需额外接口） ---- */
const isTop = computed(() => {
  const t = detail.value?.tags || []
  return t.includes('985') || t.includes('211') || t.includes('双一流')
})
const isSinoForeign = computed(() => {
  const t = detail.value?.tags || []
  return t.includes('中外合作办学') || t.includes('内地与港澳台合作办学') || (detail.value?.natureName || '').includes('中外合作')
})
const isPrivate = computed(() => detail.value?.natureName === '民办' || (detail.value?.tags || []).includes('民办'))
const cityStrong = computed(() => {
  const city = detail.value?.city || ''
  const province = detail.value?.province || ''
  return ['北京', '上海', '广州', '深圳', '杭州', '南京', '武汉', '成都', '西安', '重庆', '苏州'].some(
    (k) => city.includes(k) || province.includes(k),
  )
})

const decisionCards = computed(() => {
  if (!detail.value) return []
  return [
    {
      title: '平台判断',
      value: isTop.value ? '平台资源较强' : '更看专业和城市匹配',
      hint: isTop.value
        ? '看重升学、平台与背景的话，这类院校更值得重点关注。'
        : '更看重专业落地，要结合专业方向与城市机会一起看，别只看校名。',
      tone: 'primary',
    },
    {
      title: '成本提示',
      value: isSinoForeign.value ? '高成本合作办学' : isPrivate.value ? '需重点核对学费' : '常规预算更友好',
      hint: isSinoForeign.value
        ? '中外合作 / 港澳台合作项目学费更高，务必在招生章程确认收费与培养模式。'
        : isPrivate.value
          ? '民办院校建议额外核对学费、住宿费和转专业规则。'
          : '更适合预算敏感型家庭作为常规备选。',
      tone: isSinoForeign.value || isPrivate.value ? 'warn' : 'safe',
    },
    {
      title: '城市机会',
      value: cityStrong.value ? '城市资源更集中' : '城市机会需结合行业',
      hint: cityStrong.value
        ? '实习、校招与生活选择通常更丰富，适合重视城市平台的考生。'
        : '看重就业机会的话，建议把所在城市与目标行业一起对比。',
      tone: cityStrong.value ? 'primary' : 'default',
    },
    {
      title: '更适合谁',
      value: isTop.value ? '学校优先 / 升学导向' : '专业优先 / 务实导向',
      hint: isTop.value
        ? '更适合看重平台、升学环境和综合背景的考生。'
        : '更适合已明确专业方向、希望尽快落到具体专业与就业路径的考生。',
      tone: 'default',
    },
  ]
})

const cautions = computed(() => {
  if (!detail.value) return []
  const t = detail.value.tags || []
  const out: string[] = []
  if (t.includes('中外合作办学') || t.includes('内地与港澳台合作办学'))
    out.push('合作办学项目请重点核对学费、培养模式、证书授予和外语要求。')
  if (isPrivate.value) out.push('民办院校建议额外确认学费、住宿费、调剂政策和就业去向。')
  if (!detail.value.schoolSite && !links.value?.schoolSite)
    out.push('当前缺少学校官网链接，建议人工补查招生章程和官方招生网。')
  out.push('录取以各省招生考试院与高校当年官方章程、招生计划为准，本页信息仅供参考。')
  return out
})

/* ---- 官方报考入口（始终展示 4 项 + 收费） ---- */
interface DocItem {
  label: string
  url: string
}
const officialDocs = computed<DocItem[]>(() => {
  const l = links.value
  const site = l?.schoolSite || detail.value?.schoolSite || ''
  const docs: DocItem[] = [
    { label: '学校官网', url: site },
    { label: '招生网', url: l?.admissionSite || '' },
    { label: '招生章程', url: l?.admissionBrochureUrl || '' },
    { label: '专业目录', url: l?.majorCatalogUrl || '' },
  ]
  if (l?.tuitionInfoUrl) docs.push({ label: '收费标准', url: l.tuitionInfoUrl })
  return docs
})

/* ---- 收费信息 ---- */
const tuition = computed(() => {
  const l = links.value
  if (!l) return null
  const summary = (l.tuitionSummary || '').trim()
  const remark = (l.tuitionRemark || '').trim()
  const url = l.tuitionInfoUrl || l.admissionBrochureUrl || ''
  if (!summary && !remark && !url) return null
  return { summary, remark, url }
})

/* ---- 规则摘要 ---- */
const rules = computed(() => {
  const l = links.value
  if (!l) return []
  return [
    { label: '调剂规则', value: l.adjustmentRule },
    { label: '外语要求', value: l.foreignLanguageRule },
    { label: '体检限制', value: l.physicalExamRule },
    { label: '单科要求', value: l.singleSubjectRule },
    { label: '专业目录摘要', value: l.majorCatalogSummary },
  ].filter((r) => r.value && r.value.trim())
})

/* ---- 基本信息 ---- */
const basicRows = computed(() => {
  const d = detail.value
  if (!d) return []
  return [
    { k: '隶属', v: d.belong || '-' },
    { k: '办学性质', v: d.natureName || '-' },
    { k: '院校类型', v: d.typeName || '-' },
    { k: '办学层次', v: d.level || '-' },
    { k: '所在地', v: `${d.province || ''}${d.city ? ' · ' + d.city : ''}` || '-' },
    { k: '地址', v: d.address || '-' },
  ]
})

/* ---- 联系方式 ---- */
const contacts = computed(() => {
  const d = detail.value
  if (!d) return []
  const out: { k: string; v: string }[] = []
  if (d.phone) out.push({ k: '招生电话', v: d.phone })
  if (d.email) out.push({ k: '招生邮箱', v: d.email })
  const site = d.schoolSite || links.value?.schoolSite
  if (site) out.push({ k: '学校官网', v: site })
  return out
})

function openLink(url?: string) {
  if (!url) {
    uni.showToast({ title: '暂未收录，建议到官方招生网核验', icon: 'none' })
    return
  }
  // #ifdef H5
  window.open(url, '_blank')
  // #endif
  // #ifndef H5
  uni.setClipboardData({
    data: url,
    success: () => uni.showToast({ title: '链接已复制，请到浏览器打开', icon: 'none' }),
  })
  // #endif
}
function copyText(v?: string) {
  if (!v) return
  uni.setClipboardData({ data: v, success: () => uni.showToast({ title: '已复制', icon: 'none' }) })
}

onLoad((options) => {
  const id = Number(options?.id || 0)
  if (id) loadDetail(id)
  else {
    loading.value = false
    errorMsg.value = '缺少院校参数'
  }
})
</script>

<template>
  <view class="page">
    <view v-if="loading" class="hint">加载中…</view>
    <view v-else-if="errorMsg" class="hint error">{{ errorMsg }}</view>

    <template v-else-if="detail">
      <!-- Hero（图片 banner） -->
      <view class="hero">
        <view class="banner">
          <image class="banner-img" :src="bannerUrl" mode="aspectFill" />
          <view class="banner-mask" />
          <view v-if="cdn && cdn.videoUrl" class="banner-play" hover-class="doc-hover" @click="openLink(cdn.videoUrl)">
            <text class="bp-ic">▶</text><text class="bp-tx">宣传片</text>
          </view>
        </view>
        <view class="hero-body">
          <image v-if="detail.logoUrl" class="logo" :src="detail.logoUrl" mode="aspectFit" />
          <view v-else class="logo logo-ph">{{ detail.name.charAt(0) }}</view>
          <view class="hero-info">
            <text class="uname">{{ detail.name }}</text>
            <view class="meta">
              <text class="m">{{ detail.province }}{{ detail.city ? ' · ' + detail.city : '' }}</text>
              <text v-if="detail.typeName" class="m dot">{{ detail.typeName }}</text>
            </view>
            <text v-if="detail.belong" class="belong">{{ detail.belong }}</text>
            <view v-if="heroTags.length" class="tags">
              <text v-for="t in heroTags" :key="t" class="tag" :style="{ background: tagColor(t) }">{{ t }}</text>
            </view>
            <text v-if="cdn && cdn.motto" class="motto">校训 · {{ cdn.motto }}</text>
          </view>
        </view>
      </view>

      <!-- 排名 -->
      <view v-if="ranks.length" class="ranks">
        <view v-for="r in ranks" :key="r.label" class="rk">
          <text class="rk-n">{{ r.num }}</text>
          <text class="rk-l">{{ r.label }}</text>
        </view>
      </view>

      <!-- 校园相册 -->
      <view v-if="photos.length" class="card">
        <view class="sec"><text class="bar" />校园相册<text class="cnt">{{ photos.length }} 张</text></view>
        <view class="grid">
          <image v-for="(p, i) in photos" :key="i" class="g-img" :src="p" mode="aspectFill" @click="previewPhoto(i)" />
        </view>
      </view>

      <!-- 报考决策摘要 -->
      <view class="sec-h"><text class="bar" />报考决策摘要</view>
      <view v-for="c in decisionCards" :key="c.title" class="dcard" :class="'tone-' + c.tone">
        <view class="dcard-top">
          <text class="dcard-title">{{ c.title }}</text>
          <text class="dcard-value">{{ c.value }}</text>
        </view>
        <text class="dcard-hint">{{ c.hint }}</text>
      </view>

      <!-- 填报前核对 -->
      <view class="card">
        <view class="sec"><text class="bar" />填报前建议先核对</view>
        <view class="quick">
          <text
            v-for="d in officialDocs"
            :key="d.label"
            class="qbtn"
            :class="{ off: !d.url }"
            hover-class="qbtn-hover"
            @click="openLink(d.url)"
          >{{ d.label }}{{ d.url ? ' ›' : ' · 待补充' }}</text>
        </view>
        <view class="warns">
          <view v-for="(w, i) in cautions" :key="i" class="warn"><text class="warn-i">!</text><text class="warn-t">{{ w }}</text></view>
        </view>
      </view>

      <!-- 官方报考入口 -->
      <view class="card">
        <view class="sec"><text class="bar" />官方报考入口</view>
        <view v-for="d in officialDocs" :key="d.label" class="doc" hover-class="doc-hover" @click="openLink(d.url)">
          <view class="doc-l">
            <text class="doc-label">{{ d.label }}</text>
            <text class="doc-status" :class="d.url ? 'ok' : 'empty'">{{ d.url ? '已收录' : '待补充' }}</text>
          </view>
          <text class="doc-url">{{ d.url || '暂未收录，建议前往学校招生网或官网核验' }}</text>
          <text class="doc-act">{{ d.url ? '复制链接 ›' : '待补充' }}</text>
        </view>
        <text class="tip">App 内不直接渲染外部页面，请以高校官方招生网与章程为准。</text>
      </view>

      <!-- 收费信息 -->
      <view class="card">
        <view class="sec"><text class="bar" />收费信息</view>
        <template v-if="tuition">
          <text v-if="tuition.summary" class="para">{{ tuition.summary }}</text>
          <text v-else-if="tuition.remark" class="para">{{ tuition.remark }}</text>
          <text v-if="tuition.summary && tuition.remark && tuition.remark !== tuition.summary" class="para note">{{ tuition.remark }}</text>
          <view v-if="tuition.url" class="linkmini" hover-class="qbtn-hover" @click="openLink(tuition.url)">查看收费原文 ›</view>
        </template>
        <text v-else class="para muted">暂未提取到收费摘要，建议优先查看招生章程或学校官网核验学费与住宿费。</text>
      </view>

      <!-- 规则摘要 -->
      <view v-if="rules.length" class="card">
        <view class="sec"><text class="bar" />规则摘要提要</view>
        <view v-for="r in rules" :key="r.label" class="rule">
          <text class="rule-k">{{ r.label }}</text>
          <text class="rule-v">{{ r.value }}</text>
        </view>
      </view>

      <!-- 基本信息 -->
      <view class="card">
        <view class="sec"><text class="bar" />基本信息</view>
        <view v-for="row in basicRows" :key="row.k" class="kv">
          <text class="k">{{ row.k }}</text>
          <text class="v">{{ row.v }}</text>
        </view>
      </view>

      <!-- 联系方式 -->
      <view v-if="contacts.length" class="card">
        <view class="sec"><text class="bar" />联系方式</view>
        <view v-for="c in contacts" :key="c.k" class="contact" hover-class="doc-hover" @click="copyText(c.v)">
          <text class="k">{{ c.k }}</text>
          <text class="contact-v">{{ c.v }}</text>
        </view>
        <text class="tip">点击可复制。</text>
      </view>

      <!-- 学科评估 -->
      <view v-if="xueke.length" class="card">
        <view class="sec"><text class="bar" />学科评估</view>
        <view class="xk">
          <view v-for="x in xueke" :key="x.grade" class="xk-i" :class="'xk-' + x.grade.charAt(0).toLowerCase()">
            <text class="xk-g">{{ x.grade }}</text>
            <text class="xk-n">{{ x.count }} 个</text>
          </view>
        </view>
      </view>

      <!-- 学校简介 -->
      <view v-if="detail.content" class="card">
        <view class="sec"><text class="bar" />学校简介</view>
        <text class="intro">{{ detail.content }}</text>
      </view>

      <view class="notice">本页信息整合自公开渠道，仅供参考；录取请以各省招生考试院与高校当年官方材料为准。</view>
    </template>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: 20rpx 24rpx 60rpx;
}
.card {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  padding: 26rpx;
  margin-bottom: 18rpx;
  box-shadow: 0 6rpx 18rpx rgba(15, 23, 42, 0.04);
}

/* Hero banner */
.hero {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  margin-bottom: 18rpx;
  overflow: hidden;
  box-shadow: 0 8rpx 22rpx rgba(15, 23, 42, 0.05);
}
.banner {
  position: relative;
  height: 300rpx;
}
.banner-img {
  width: 100%;
  height: 100%;
  background: $gz-bg-subtle;
}
.banner-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.05), rgba(15, 23, 42, 0.6));
}
.banner-play {
  position: absolute;
  top: 18rpx;
  right: 18rpx;
  display: flex;
  align-items: center;
  gap: 6rpx;
  background: rgba(0, 0, 0, 0.42);
  border-radius: 999rpx;
  padding: 8rpx 18rpx;
}
.bp-ic {
  color: #fff;
  font-size: 18rpx;
}
.bp-tx {
  color: #fff;
  font-size: 21rpx;
}
.hero-body {
  display: flex;
  gap: 20rpx;
  padding: 0 26rpx 26rpx;
  align-items: flex-start;
}
.logo {
  width: 110rpx;
  height: 110rpx;
  border-radius: 22rpx;
  background: #fff;
  border: 4rpx solid #fff;
  margin-top: -48rpx;
  position: relative;
  z-index: 2;
  flex: none;
  box-shadow: 0 6rpx 16rpx rgba(15, 23, 42, 0.12);
}
.logo-ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  font-weight: 800;
  color: #fff;
  background: $gz-primary;
}
.hero-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  padding-top: 8rpx;
}
.uname {
  font-size: 34rpx;
  font-weight: 800;
  color: $gz-text;
  line-height: 1.3;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12rpx;
}
.m {
  font-size: 23rpx;
  color: $gz-text-sub;
}
.m.dot {
  color: $gz-primary;
  background: $gz-primary-50;
  padding: 2rpx 12rpx;
  border-radius: 999rpx;
}
.belong {
  font-size: 22rpx;
  color: $gz-text-weak;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}
.tag {
  font-size: 20rpx;
  font-weight: 700;
  color: #fff;
  border-radius: 8rpx;
  padding: 5rpx 14rpx;
}
.motto {
  display: block;
  font-size: 22rpx;
  color: $gz-text-weak;
  letter-spacing: 1rpx;
}

/* 排名 */
.ranks {
  display: flex;
  gap: 12rpx;
  margin-bottom: 18rpx;
  overflow-x: auto;
}
.rk {
  flex: 1;
  min-width: 130rpx;
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: 18rpx;
  padding: 18rpx 8rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
  box-shadow: 0 6rpx 18rpx rgba(15, 23, 42, 0.04);
}
.rk-n {
  font-size: 32rpx;
  font-weight: 800;
  color: $gz-text;
}
.rk-l {
  font-size: 21rpx;
  color: $gz-text-weak;
}

/* 相册 */
.cnt {
  margin-left: auto;
  font-size: 21rpx;
  font-weight: 500;
  color: $gz-text-weak;
}
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8rpx;
}
.g-img {
  width: 100%;
  height: 196rpx;
  border-radius: 12rpx;
  background: $gz-bg-subtle;
}

/* 学科评估 */
.xk {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
.xk-i {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  padding: 14rpx 22rpx;
  border-radius: 14rpx;
  background: $gz-bg-subtle;
}
.xk-g {
  font-size: 26rpx;
  font-weight: 800;
  color: $gz-text;
}
.xk-n {
  font-size: 20rpx;
  color: $gz-text-weak;
}
.xk-a {
  background: #fef3c7;
}
.xk-a .xk-g {
  color: #d97706;
}
.xk-b {
  background: #dbeafe;
}
.xk-b .xk-g {
  color: #2563eb;
}
.xk-c {
  background: #f1f5f9;
}

/* 分区标题 */
.sec-h {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 28rpx;
  font-weight: 800;
  color: $gz-text;
  margin: 6rpx 4rpx 14rpx;
}
.sec {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 28rpx;
  font-weight: 800;
  color: $gz-text;
  margin-bottom: 16rpx;
}
.bar {
  width: 8rpx;
  height: 30rpx;
  border-radius: 6rpx;
  background: $gz-primary;
}

/* 决策卡 */
.dcard {
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  padding: 24rpx 26rpx;
  margin-bottom: 14rpx;
  box-shadow: 0 6rpx 18rpx rgba(15, 23, 42, 0.04);
}
.dcard.tone-primary {
  background: linear-gradient(180deg, #fffdfa, #eff4ff);
}
.dcard.tone-warn {
  background: linear-gradient(180deg, #fffdfa, #fff7ed);
}
.dcard.tone-safe {
  background: linear-gradient(180deg, #fffdfa, #ecfdf5);
}
.dcard.tone-default {
  background: $gz-card;
}
.dcard-top {
  display: flex;
  align-items: baseline;
  gap: 14rpx;
  margin-bottom: 8rpx;
}
.dcard-title {
  flex: none;
  font-size: 22rpx;
  font-weight: 700;
  color: $gz-text-weak;
}
.dcard-value {
  font-size: 28rpx;
  font-weight: 800;
  color: $gz-text;
}
.dcard-hint {
  font-size: 23rpx;
  color: $gz-text-sub;
  line-height: 1.7;
}

/* 快捷核对按钮 */
.quick {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 18rpx;
}
.qbtn {
  font-size: 23rpx;
  font-weight: 600;
  color: $gz-primary;
  background: $gz-primary-50;
  border-radius: 999rpx;
  padding: 10rpx 22rpx;
}
.qbtn.off {
  color: $gz-text-weak;
  background: $gz-bg-subtle;
}
.qbtn-hover {
  opacity: 0.7;
}
.warns {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.warn {
  display: flex;
  gap: 12rpx;
  background: #fff7ed;
  border-radius: 14rpx;
  padding: 14rpx 18rpx;
}
.warn-i {
  flex: none;
  width: 30rpx;
  height: 30rpx;
  border-radius: 50%;
  background: #f59e0b;
  color: #fff;
  font-size: 20rpx;
  font-weight: 800;
  text-align: center;
  line-height: 30rpx;
}
.warn-t {
  flex: 1;
  font-size: 22rpx;
  color: #9a3412;
  line-height: 1.6;
}

/* 官方入口 */
.doc {
  border: 1rpx solid $gz-border;
  border-radius: 16rpx;
  padding: 20rpx 22rpx;
  margin-bottom: 12rpx;
  background: $gz-bg-subtle;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.doc-hover {
  background: #f0f4ff;
}
.doc-l {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.doc-label {
  font-size: 26rpx;
  font-weight: 700;
  color: $gz-text;
}
.doc-status {
  font-size: 20rpx;
  font-weight: 700;
  padding: 3rpx 14rpx;
  border-radius: 999rpx;
}
.doc-status.ok {
  color: #047857;
  background: #ecfdf5;
}
.doc-status.empty {
  color: $gz-text-weak;
  background: #eef1f5;
}
.doc-url {
  font-size: 21rpx;
  color: $gz-text-sub;
  word-break: break-all;
  line-height: 1.5;
}
.doc-act {
  font-size: 22rpx;
  color: $gz-primary;
}
.tip {
  display: block;
  margin-top: 8rpx;
  font-size: 21rpx;
  color: $gz-text-weak;
  line-height: 1.6;
}

/* 收费 / 规则 文本 */
.para {
  display: block;
  font-size: 25rpx;
  color: $gz-text-sub;
  line-height: 1.85;
}
.para.note {
  margin-top: 12rpx;
  padding-top: 12rpx;
  border-top: 1rpx dashed $gz-border;
  font-size: 23rpx;
  color: $gz-text-weak;
}
.para.muted {
  color: $gz-text-weak;
}
.linkmini {
  margin-top: 14rpx;
  align-self: flex-start;
  font-size: 23rpx;
  color: $gz-primary;
  background: $gz-primary-50;
  border-radius: 999rpx;
  padding: 10rpx 22rpx;
}
.rule {
  padding: 16rpx 0;
  border-bottom: 1rpx solid $gz-border;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.rule:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.rule-k {
  font-size: 23rpx;
  font-weight: 700;
  color: $gz-primary;
}
.rule-v {
  font-size: 24rpx;
  color: $gz-text-sub;
  line-height: 1.75;
}

/* 基本信息 / 联系 */
.kv {
  display: flex;
  gap: 18rpx;
  padding: 14rpx 0;
  border-bottom: 1rpx solid $gz-border;
}
.kv:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.k {
  flex: none;
  width: 130rpx;
  font-size: 24rpx;
  color: $gz-text-weak;
}
.v {
  flex: 1;
  font-size: 24rpx;
  color: $gz-text;
  line-height: 1.6;
}
.contact {
  display: flex;
  gap: 18rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid $gz-border;
  align-items: center;
}
.contact:last-of-type {
  border-bottom: none;
}
.contact-v {
  flex: 1;
  font-size: 24rpx;
  color: $gz-primary;
  word-break: break-all;
  line-height: 1.5;
}

.intro {
  font-size: 25rpx;
  color: $gz-text-sub;
  line-height: 1.85;
}
.hint {
  margin-top: 80rpx;
  text-align: center;
  font-size: 26rpx;
  color: $gz-text-weak;
}
.hint.error {
  color: #d4380d;
}
.notice {
  margin-top: 16rpx;
  font-size: 22rpx;
  color: $gz-warn;
  background: $gz-warn-bg;
  border: 1rpx solid $gz-warn-border;
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
  line-height: 1.7;
}
</style>
