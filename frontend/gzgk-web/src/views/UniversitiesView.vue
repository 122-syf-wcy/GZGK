<template>
  <div class="mx-auto max-w-6xl space-y-5 px-6 py-6">
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-xl font-bold text-ink-900">全国院校与专业库</h1>
        <p class="mt-0.5 text-xs text-ink-500">
          L1 Caffeine 内存字典 + PostgreSQL `pg_trgm` 毫秒级模糊检索（已收录 {{ total.toLocaleString() }} 所高校）。
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <input
          v-model="keyword"
          type="text"
          placeholder="搜索校名、省份或城市…"
          class="w-60 rounded-full border border-line bg-white px-4 py-2 text-xs text-ink-900 placeholder:text-ink-400 focus:border-pine-600 focus:outline-none"
          @input="debouncedSearch"
        />
        <button
          v-for="t in ['', '985', '211', '双一流', '公办']"
          :key="t || 'all'"
          type="button"
          :class="[
            'rounded-full border px-3 py-1.5 text-xs font-medium transition',
            selectedTag === t
              ? 'border-pine-700 bg-sage-100 text-pine-800'
              : 'border-line bg-white text-ink-700 hover:bg-sage-50',
          ]"
          @click="selectTag(t)"
        >
          {{ t || '全部' }}
        </button>
      </div>
    </div>

    <div class="grid gap-3.5 sm:grid-cols-2 lg:grid-cols-3">
      <article
        v-for="u in items"
        :key="u.schoolId"
        class="flex flex-col justify-between rounded-2xl border border-line bg-white p-4 shadow-editorial transition hover:border-pine-500/40"
      >
        <div>
          <div class="flex items-start justify-between gap-2">
            <h3 class="text-[15px] font-semibold text-ink-900">{{ u.name }}</h3>
            <span class="shrink-0 rounded-full bg-paper px-2.5 py-0.5 text-[11px] text-ink-500">
              {{ u.province }} · {{ u.city }}
            </span>
          </div>
          <div class="mt-2 flex flex-wrap gap-1.5">
            <span v-if="u.is985" class="rounded bg-pine-700 px-1.5 py-0.5 text-[10.5px] font-medium text-white">985</span>
            <span v-if="u.is211" class="rounded bg-sage-100 px-1.5 py-0.5 text-[10.5px] font-medium text-pine-800">211</span>
            <span v-if="u.isDualClass" class="rounded bg-sage-50 px-1.5 py-0.5 text-[10.5px] font-medium text-sage-600">双一流</span>
            <span class="rounded bg-stonewarm-50 px-1.5 py-0.5 text-[10.5px] text-ink-500">{{ u.natureName || '公办' }}</span>
          </div>
          <p class="mt-2.5 line-clamp-3 text-xs leading-relaxed text-ink-500">
            {{ u.summary || `${u.belong || u.province}主管${u.typeName || '综合'}类高等院校。` }}
          </p>
        </div>

        <div class="mt-3.5 flex items-center justify-between border-t border-line/70 pt-3 text-xs">
          <router-link
            :to="`/score-lines?school=${encodeURIComponent(u.name)}`"
            class="font-medium text-pine-700 hover:underline"
          >
            查历年分数线 →
          </router-link>
          <a
            v-if="u.schoolSite"
            :href="u.schoolSite"
            target="_blank"
            rel="noopener noreferrer"
            class="text-ink-400 hover:text-pine-700"
          >
            官网 ↗
          </a>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { UniversityRecord } from '@gzgk/shared';
import { coreApi } from '@/api/client';

const keyword = ref('');
const selectedTag = ref('');
const total = ref(2198);
const items = ref<UniversityRecord[]>([]);

const fallbackUnis: UniversityRecord[] = [
  { schoolId: '538', name: '贵州大学', province: '贵州', city: '贵阳市', level: '本科', typeName: '综合', tags: ['211', '双一流', '公办'], is985: false, is211: true, isDualClass: true, natureName: '公办', belong: '贵州省', schoolSite: 'https://www.gzu.edu.cn/', officialCharterUrl: 'https://rso.gzu.edu.cn/', summary: '教育部与贵州省部省合建高校，国家“211工程”与“双一流”建设高校。' },
  { schoolId: '110', name: '西南大学', province: '重庆', city: '重庆市', level: '本科', typeName: '综合', tags: ['211', '双一流', '公办'], is985: false, is211: true, isDualClass: true, natureName: '公办', belong: '教育部', schoolSite: 'https://www.swu.edu.cn/', officialCharterUrl: 'https://bkzsw.swu.edu.cn/', summary: '教育部直属重点综合大学，软件工程与师范学科特色鲜明。' },
  { schoolId: '141', name: '重庆邮电大学', province: '重庆', city: '重庆市', level: '本科', typeName: '理工', tags: ['公办'], is985: false, is211: false, isDualClass: false, natureName: '公办', belong: '重庆市', schoolSite: 'https://www.cqupt.edu.cn/', officialCharterUrl: 'https://zs.cqupt.edu.cn/', summary: '信息通信与计算机学科行业认可度高，川渝工科热门性价比院校。' },
  { schoolId: '512', name: '福州大学', province: '福建', city: '福州市', level: '本科', typeName: '理工', tags: ['211', '双一流', '公办'], is985: false, is211: true, isDualClass: true, natureName: '公办', belong: '福建省', schoolSite: 'https://www.fzu.edu.cn/', officialCharterUrl: 'https://zsb.fzu.edu.cn/', summary: '国家“双一流”建设高校，电子信息、电气与化学化工学科实力突出。' },
];

let timer: ReturnType<typeof setTimeout> | null = null;

async function fetchUniversities() {
  try {
    const res = await coreApi.searchUniversities({ keyword: keyword.value, tag: selectedTag.value, page: 1, size: 18 });
    total.value = res.total;
    items.value = res.items;
  } catch {
    const kw = keyword.value.trim();
    items.value = fallbackUnis.filter((u) => (!kw || u.name.includes(kw) || u.city.includes(kw)) && (!selectedTag.value || u.tags.includes(selectedTag.value)));
  }
}

function debouncedSearch() {
  if (timer) clearTimeout(timer);
  timer = setTimeout(fetchUniversities, 150);
}

function selectTag(t: string) {
  selectedTag.value = t;
  fetchUniversities();
}

onMounted(fetchUniversities);
</script>
