<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import GzEmpty from '@/components/GzEmpty.vue'
import GzNavBar from '@/components/GzNavBar.vue'
import GzSegmented from '@/components/GzSegmented.vue'
import GzSheet from '@/components/GzSheet.vue'
import GzSkeleton from '@/components/GzSkeleton.vue'
import { getScoreLineSchools, getScoreLineYears, getSchoolHistory, type SchoolScoreRow } from '@/api/query'
import { getProvinceText, normalizeProvinceCode, PROVINCE_LIST } from '@/constants/provinces'
import type { ProvinceCode } from '@/types'

const provinceCode = ref<ProvinceCode>('GZ')
const subjectType = ref('物理类')
const year = ref(2025)
const years = ref<number[]>([])

const rows = ref<SchoolScoreRow[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const failed = ref(false)

const historyVisible = ref(false)
const historyRows = ref<Array<SchoolScoreRow & { rankSourceType?: string }>>([])
const historyTitle = ref('')
const historyLoading = ref(false)

async function loadYears() {
  try {
    years.value = await getScoreLineYears(provinceCode.value)
    if (years.value.length && !years.value.includes(year.value)) year.value = years.value[0]
  } catch {
    years.value = []
  }
}

async function load(reset = false) {
  if (reset) {
    page.value = 1
    loading.value = true
  }
  failed.value = false
  try {
    const res = await getScoreLineSchools({
      provinceCode: provinceCode.value,
      year: year.value,
      subjectType: subjectType.value,
      page: page.value,
      pageSize: 20,
    })
    total.value = res.total
    rows.value = reset ? res.records : [...rows.value, ...res.records]
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function openHistory(row: SchoolScoreRow) {
  historyTitle.value = row.universityName
  historyVisible.value = true
  historyLoading.value = true
  try {
    historyRows.value = await getSchoolHistory({
      provinceCode: provinceCode.value,
      schoolId: row.schoolId,
      subjectType: subjectType.value,
    })
  } catch {
    historyRows.value = []
  } finally {
    historyLoading.value = false
  }
}

function switchProvince(code: ProvinceCode) {
  if (code === provinceCode.value) return
  provinceCode.value = code
  loadYears().then(() => load(true))
}

onReachBottom(() => {
  if (rows.value.length >= total.value) return
  page.value += 1
  load(false)
})

onLoad(async (q) => {
  provinceCode.value = normalizeProvinceCode(q?.provinceCode)
  await loadYears()
  load(true)
})

onPullDownRefresh(async () => {
  await load(true)
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="page">
    <GzNavBar title="历年分数线" />

    <view class="pad">
      <view class="head">
        <text class="head__title">院校投档线</text>
        <text class="head__desc">
          当前展示院校级数据。专业级数据请在生成方案或院校详情中核验。
        </text>
      </view>

      <scroll-view class="prov" scroll-x :show-scrollbar="false">
        <view class="prov__row">
          <view
            v-for="p in PROVINCE_LIST"
            :key="p.code"
            class="prov__item"
            :class="{ 'prov__item--active': p.code === provinceCode }"
            @tap="switchProvince(p.code)"
          >
            <text class="prov__text">{{ p.shortName }}</text>
          </view>
        </view>
      </scroll-view>

      <view class="filters">
        <GzSegmented
          v-model="subjectType"
          :options="[{ value: '物理类', label: '物理类' }, { value: '历史类', label: '历史类' }]"
          @update:model-value="load(true)"
        />
      </view>

      <view v-if="years.length" class="years">
        <text
          v-for="y in years"
          :key="y"
          class="years__item"
          :class="{ 'years__item--active': y === year }"
          @tap="year = y; load(true)"
        >
          {{ y }} 年
        </text>
      </view>

      <GzSkeleton v-if="loading" :rows="5" />

      <GzEmpty
        v-else-if="failed"
        title="分数线读取失败"
        retry-text="重试"
        @retry="load(true)"
      />

      <GzEmpty
        v-else-if="!rows.length"
        title="该地区暂无可用数据"
        :desc="`${getProvinceText(provinceCode).shortName}的官方分数线尚未导入，导入并通过核验后会在这里显示。`"
      />

      <template v-else>
        <view v-for="row in rows" :key="row.schoolId" class="srow" @tap="openHistory(row)">
          <view class="srow__main">
            <text class="srow__name">{{ row.universityName }}</text>
            <text class="srow__meta">{{ row.city }} · 收录 {{ row.yearCount }} 年</text>
          </view>
          <view class="srow__nums">
            <text class="srow__score">{{ row.minScore }}</text>
            <text class="srow__rank">位次 {{ row.minRank }}</text>
          </view>
        </view>
        <view v-if="rows.length >= total" class="more"><text class="more__text">已经到底了</text></view>
      </template>
    </view>

    <GzSheet :visible="historyVisible" :title="historyTitle" @close="historyVisible = false">
      <GzSkeleton v-if="historyLoading" :rows="2" />
      <template v-else>
        <view v-for="h in historyRows" :key="h.year" class="hrow">
          <text class="hrow__y">{{ h.year }}</text>
          <text class="hrow__c">最低分 {{ h.minScore }}</text>
          <text class="hrow__c">位次 {{ h.minRank }}</text>
          <text v-if="h.rankSourceType === 'score_rank_converted'" class="hrow__flag">一分一段换算</text>
        </view>
        <view class="hnote">
          <text class="hnote__text">
            位次标注为「一分一段换算」的记录，其原始数据只有最低分，位次由官方一分一段表换算得出，存在区间误差。
          </text>
        </view>
      </template>
    </GzSheet>
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

.head {
  padding: $gz-space-3 0 $gz-space-3;

  &__title {
    display: block;
    font-family: var(--gz-font-serif);
    font-size: 52rpx;
    color: var(--gz-text);
  }

  &__desc {
    display: block;
    margin-top: 10rpx;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    line-height: 1.7;
  }
}

.prov {
  white-space: nowrap;
  margin-bottom: $gz-space-3;

  &__row {
    display: inline-flex;
  }

  &__item {
    flex-shrink: 0;
    padding: 12rpx 28rpx;
    margin-right: 12rpx;
    border-radius: var(--gz-radius-full);
    background: var(--gz-surface-2);

    &--active {
      background: var(--gz-ink);
    }
  }

  &__text {
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    white-space: nowrap;
  }

  &__item--active &__text {
    color: var(--gz-text-inverse);
  }
}

.filters {
  margin-bottom: $gz-space-3;
}

.years {
  display: flex;
  flex-wrap: wrap;
  margin-bottom: $gz-space-3;

  &__item {
    padding: 8rpx 24rpx;
    margin: 0 12rpx 12rpx 0;
    border-radius: var(--gz-radius-full);
    border: 2rpx solid var(--gz-border);
    font-size: $gz-text-xs;
    color: var(--gz-text-2);

    &--active {
      border-color: var(--gz-ink);
      color: var(--gz-text);
    }
  }
}

.srow {
  @include gz-card;
  @include gz-pressable;
  display: flex;
  align-items: center;
  padding: $gz-space-3;
  margin-bottom: $gz-space-2;

  &__main {
    flex: 1;
    min-width: 0;
  }

  &__name {
    display: block;
    font-size: $gz-text-body;
    color: var(--gz-text);
    @include gz-ellipsis;
  }

  &__meta {
    display: block;
    margin-top: 4rpx;
    font-size: 20rpx;
    color: var(--gz-text-3);
  }

  &__nums {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    margin-left: $gz-space-2;
  }

  &__score {
    font-family: var(--gz-font-serif);
    font-size: $gz-text-title;
    line-height: 1;
    color: var(--gz-text);
  }

  &__rank {
    margin-top: 6rpx;
    font-size: 20rpx;
    color: var(--gz-text-3);
  }
}

.more {
  padding: $gz-space-4 0;
  text-align: center;

  &__text {
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
  }
}

.hrow {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 2rpx solid var(--gz-border);

  &__y {
    width: 110rpx;
    font-size: $gz-text-body;
    color: var(--gz-text);
  }

  &__c {
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    margin-right: 24rpx;
  }

  &__flag {
    font-size: 18rpx;
    color: var(--gz-warn);
  }
}

.hnote {
  padding: $gz-space-3 0 $gz-space-6;

  &__text {
    font-size: 20rpx;
    color: var(--gz-text-3);
    line-height: 1.8;
  }
}
</style>
