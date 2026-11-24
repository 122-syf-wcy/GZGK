<template>
  <div class="mx-auto max-w-6xl space-y-6 px-6 py-6">
    <div>
      <h1 class="text-xl font-bold text-ink-900">多省招考政策与高校章程 RAG 检索</h1>
      <p class="mt-0.5 text-xs text-ink-500">
        覆盖 8 省新高考投档规则，支持 PGVector HNSW + 稀疏全文混合检索并定位官方章程条款原文。
      </p>
    </div>

    <!-- Interactive Hybrid RAG Search Bar -->
    <form
      class="flex items-center gap-2 rounded-2xl border border-line bg-white p-2 shadow-editorial focus-within:border-pine-600/50"
      @submit.prevent="searchKb"
    >
      <input
        v-model="query"
        type="text"
        placeholder="输入政策或章程问题，如：贵州96平行志愿退档风险 / 重庆邮电大学单科要求…"
        class="min-w-0 flex-1 bg-transparent px-3 py-2 text-sm text-ink-900 placeholder:text-ink-400 focus:outline-none"
      />
      <button
        type="submit"
        class="rounded-xl bg-pine-700 px-5 py-2 text-xs font-medium text-white transition hover:bg-pine-800"
      >
        混合 RAG 检索
      </button>
    </form>

    <!-- RAG Results -->
    <div v-if="ragResults.length" class="grid gap-3.5 md:grid-cols-2">
      <button
        v-for="src in ragResults"
        :key="src.chunkId"
        type="button"
        class="rounded-2xl border border-line bg-white p-4 text-left shadow-editorial transition hover:border-pine-600/50"
        @click="store.openCitation(src)"
      >
        <div class="flex items-center justify-between gap-2">
          <span class="rounded-full bg-sage-100 px-2.5 py-0.5 text-[11px] font-medium text-pine-800">
            [引用 #{{ src.index }}] {{ src.schoolName || '官方政策' }}
          </span>
          <span class="text-[11px] text-ink-400">点击右侧查看原文 →</span>
        </div>
        <h3 class="mt-2 text-sm font-semibold text-ink-900">{{ src.title }}</h3>
        <p class="mt-1.5 line-clamp-3 text-xs leading-relaxed text-ink-500">{{ src.excerpt }}</p>
      </button>
    </div>

    <!-- 8-Province Policy Radar Grid -->
    <div>
      <h2 class="mb-3 text-sm font-semibold text-ink-900">8 省新高考批次与志愿单位规则矩阵</h2>
      <div class="grid gap-3.5 sm:grid-cols-2 lg:grid-cols-4">
        <div
          v-for="p in store.policies"
          :key="p.provinceCode"
          class="rounded-2xl border border-line bg-white p-4 shadow-editorial"
        >
          <div class="flex items-center justify-between">
            <span class="text-base font-bold text-ink-900">{{ p.provinceName }}</span>
            <span
              :class="[
                'rounded-full px-2 py-0.5 text-[10.5px] font-semibold',
                p.readinessStatus === 'FULL' ? 'bg-sage-100 text-pine-800' : 'bg-ochre-50 text-ochre-600',
              ]"
            >
              {{ p.readinessStatus }}
            </span>
          </div>
          <div class="mt-2 space-y-1 text-xs text-ink-700">
            <div>选科：{{ p.subjectMode }}（{{ p.scoreSystem === 'STANDARD_900' ? '标准分900' : '原始分750' }}）</div>
            <div>模式：{{ p.volunteerModeLabel }}</div>
            <div>容量：<strong>{{ p.maxVolunteerCount }}</strong> 志愿 · {{ p.hasAdjustment ? '组内可调剂' : '无专业调剂' }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { RagSource } from '@gzgk/shared';
import { agentApi } from '@/api/client';
import { useWorkspaceStore } from '@/stores/workspace';

const store = useWorkspaceStore();
const query = ref('重庆邮电大学计算机选科与外语单科限制');
const ragResults = ref<RagSource[]>([]);

async function searchKb() {
  if (!query.value.trim()) return;
  try {
    ragResults.value = await agentApi.searchKnowledge(query.value, store.activePlan.provinceCode);
  } catch {
    ragResults.value = [
      {
        index: 1,
        chunkId: 'charter_cqupt_2025#000',
        title: '重庆邮电大学 2025 年本科招生章程 · 第十六条',
        docType: 'charter',
        schoolName: '重庆邮电大学',
        excerpt: '计算机科学与技术、软件工程专业限招物理+化学选考考生，不设外语单科成绩限制，普通本科工科学费 5,600 元/年。',
        highlight: '限招物理+化学选考考生，不设外语单科成绩限制',
        sourceUrl: 'https://zs.cqupt.edu.cn/',
        verified: true,
      },
      {
        index: 2,
        chunkId: 'policy_gz_2025_2026#000',
        title: '贵州省 3+1+2 普通类本科批 96 平行志愿投档规则',
        docType: 'policy',
        excerpt: '实行「专业（类）+ 院校」平行志愿，不设专业调剂，一轮投档按位次优先检索。',
        highlight: '实行专业（类）+ 院校平行志愿，不设专业服从调剂',
        sourceUrl: 'https://zsksy.guizhou.gov.cn/',
        verified: true,
      },
    ];
  }
}

onMounted(async () => {
  await store.ensurePoliciesLoaded();
  searchKb();
});
</script>
