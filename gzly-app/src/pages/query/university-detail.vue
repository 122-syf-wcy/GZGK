<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import GzEmpty from '@/components/GzEmpty.vue'
import GzNavBar from '@/components/GzNavBar.vue'
import GzSkeleton from '@/components/GzSkeleton.vue'
import GzTag from '@/components/GzTag.vue'
import { getOfficialLinks, getUniversity, type OfficialLinks, type UniversityDetail } from '@/api/query'

const detail = ref<UniversityDetail>()
const links = ref<OfficialLinks>({})
const loading = ref(true)
const failed = ref(false)
let currentId = 0

const LINK_LABELS: Array<[keyof OfficialLinks, string]> = [
  ['officialUrl', '学校官网'],
  ['admissionSiteUrl', '招生网'],
  ['admissionBrochureUrl', '招生章程'],
  ['majorCatalogUrl', '招生专业目录'],
  ['tuitionInfoUrl', '收费标准'],
]

async function load(id: number) {
  loading.value = true
  failed.value = false
  try {
    detail.value = await getUniversity(id)
    if (detail.value?.schoolId) {
      links.value = await getOfficialLinks(detail.value.schoolId)
    }
    if (!detail.value) failed.value = true
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

/** 外链只允许 http/https，防止渲染出 javascript: 之类的脏链接 */
function safeUrl(url?: string) {
  return url && /^https?:\/\//i.test(url) ? url : ''
}

function copyLink(url: string) {
  uni.setClipboardData({
    data: url,
    success: () => uni.showToast({ title: '链接已复制', icon: 'none' }),
  })
}

onLoad((q) => {
  currentId = Number(q?.id || 0)
  if (!currentId) {
    failed.value = true
    loading.value = false
    return
  }
  load(currentId)
})
</script>

<template>
  <view class="page">
    <GzNavBar :title="detail?.name || '院校详情'" />

    <view class="pad">
      <GzSkeleton v-if="loading" :rows="4" />

      <GzEmpty
        v-else-if="failed || !detail"
        title="院校信息读取失败"
        retry-text="重试"
        @retry="load(currentId)"
      />

      <template v-else>
        <view class="hero">
          <text class="hero__name">{{ detail.name }}</text>
          <view class="hero__tags">
            <GzTag v-for="t in detail.tags || []" :key="t" tone="info">{{ t }}</GzTag>
            <GzTag>{{ detail.natureName }}</GzTag>
            <GzTag>{{ detail.typeName }}</GzTag>
          </view>
          <text class="hero__loc">{{ detail.province }} · {{ detail.city }}</text>
        </view>

        <view class="sec">
          <text class="sec__title">学校简介</text>
          <text class="sec__p">{{ detail.content }}</text>
        </view>

        <view class="sec">
          <text class="sec__title">基本信息</text>
          <view class="kv"><text class="kv__k">主管部门</text><text class="kv__v">{{ detail.belong || '—' }}</text></view>
          <view class="kv"><text class="kv__k">地址</text><text class="kv__v">{{ detail.address || '—' }}</text></view>
        </view>

        <view class="sec">
          <text class="sec__title">官方核验入口</text>
          <text class="sec__hint">
            填报前请以下列官方材料为准。系统整理的数据可能存在更新滞后或解析误差。
          </text>
          <template v-for="[key, label] in LINK_LABELS" :key="key">
            <view v-if="safeUrl(links[key])" class="link" @tap="copyLink(safeUrl(links[key]))">
              <text class="link__label">{{ label }}</text>
              <text class="link__url">{{ links[key] }}</text>
              <text class="link__copy">复制</text>
            </view>
          </template>
        </view>
      </template>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--gz-bg);
  padding-bottom: calc(80rpx + env(safe-area-inset-bottom));
}

.pad {
  padding: 0 $gz-page-x;
}

.hero {
  padding: $gz-space-3 0 $gz-space-4;

  &__name {
    display: block;
    font-family: var(--gz-font-serif);
    font-size: 52rpx;
    color: var(--gz-text);
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    margin-top: $gz-space-2;
  }

  &__loc {
    display: block;
    margin-top: 10rpx;
    font-size: $gz-text-sm;
    color: var(--gz-text-3);
  }
}

.sec {
  @include gz-card;
  padding: $gz-space-4 $gz-space-3;
  margin-bottom: $gz-space-3;

  &__title {
    display: block;
    font-size: $gz-text-body;
    color: var(--gz-text);
    margin-bottom: $gz-space-2;
  }

  &__hint {
    display: block;
    margin-bottom: $gz-space-2;
    font-size: 20rpx;
    color: var(--gz-text-3);
    line-height: 1.7;
  }

  &__p {
    display: block;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    line-height: 1.85;
  }
}

.kv {
  display: flex;
  margin-bottom: 12rpx;

  &__k {
    width: 160rpx;
    font-size: $gz-text-sm;
    color: var(--gz-text-3);
  }

  &__v {
    flex: 1;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
  }
}

.link {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
  border-top: 2rpx solid var(--gz-border);

  &__label {
    width: 180rpx;
    font-size: $gz-text-sm;
    color: var(--gz-text);
  }

  &__url {
    flex: 1;
    min-width: 0;
    font-size: 20rpx;
    color: var(--gz-text-3);
    @include gz-ellipsis;
  }

  &__copy {
    font-size: $gz-text-xs;
    color: var(--gz-wen);
    margin-left: $gz-space-2;
  }
}
</style>
