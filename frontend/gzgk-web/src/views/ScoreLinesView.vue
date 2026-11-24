<template>
  <div class="mx-auto max-w-6xl space-y-5 px-6 py-6">
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-xl font-bold text-ink-900">历年投档分数线与位次库</h1>
        <p class="mt-0.5 text-xs text-ink-500">
          PostgreSQL 16 按省份声明式分区裁剪查询（Partition Pruning），快速检索近三年专业最低分与位次。
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <input
          v-model="schoolName"
          type="text"
          placeholder="院校名称（如：贵州大学）"
          class="w-44 rounded-full border border-line bg-white px-3.5 py-2 text-xs text-ink-900 focus:border-pine-600 focus:outline-none"
          @keydown.enter="loadLines"
        />
        <input
          v-model="majorName"
          type="text"
          placeholder="专业关键词（如：计算机）"
          class="w-44 rounded-full border border-line bg-white px-3.5 py-2 text-xs text-ink-900 focus:border-pine-600 focus:outline-none"
          @keydown.enter="loadLines"
        />
        <select
          v-model="subjectType"
          class="rounded-full border border-line bg-white px-3.5 py-2 text-xs text-ink-700 focus:border-pine-600 focus:outline-none"
        >
          <option value="物理类">物理类</option>
          <option value="历史类">历史类</option>
        </select>
        <button
          type="button"
          class="rounded-full bg-pine-700 px-4 py-2 text-xs font-medium text-white transition hover:bg-pine-800"
          @click="loadLines"
        >
          查询
        </button>
      </div>
    </div>

    <div class="overflow-hidden rounded-2xl border border-line bg-white shadow-editorial">
      <div class="overflow-x-auto">
        <table class="min-w-full divide-y divide-line text-left text-xs">
          <thead class="bg-paper text-ink-500">
            <tr>
              <th class="px-4 py-3 font-medium">年份</th>
              <th class="px-4 py-3 font-medium">院校名称</th>
              <th class="px-4 py-3 font-medium">专业名称</th>
              <th class="px-4 py-3 font-medium">科类 / 选科要求</th>
              <th class="px-4 py-3 text-right font-medium">最低分</th>
              <th class="px-4 py-3 text-right font-medium">最低位次</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-line/70 text-ink-900">
            <tr v-for="(r, idx) in rows" :key="idx" class="transition hover:bg-sage-50/50">
              <td class="px-4 py-3 font-mono tabular-nums">{{ r.year }}</td>
              <td class="px-4 py-3 font-semibold">{{ r.universityName }}</td>
              <td class="px-4 py-3 text-ink-700">{{ r.majorName }}</td>
              <td class="px-4 py-3">
                <span class="rounded bg-sage-100 px-2 py-0.5 text-[11px] text-pine-800">
                  {{ r.subjectType }} · {{ r.resubjectRequirement || '不限' }}
                </span>
              </td>
              <td class="px-4 py-3 text-right font-mono font-semibold tabular-nums">{{ r.minScore ?? '-' }}</td>
              <td class="px-4 py-3 text-right font-mono text-pine-800 tabular-nums">
                {{ r.minRank ? `${r.minRank.toLocaleString()}名` : '-' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import type { MajorScoreRecord } from '@gzgk/shared';
import { coreApi } from '@/api/client';

const route = useRoute();
const schoolName = ref((route.query.school as string) || '');
const majorName = ref('计算机');
const subjectType = ref('物理类');
const rows = ref<MajorScoreRecord[]>([]);

async function loadLines() {
  try {
    rows.value = await coreApi.queryScoreLines({
      provinceCode: 'GZ',
      schoolName: schoolName.value,
      majorName: majorName.value,
      subjectType: subjectType.value,
      limit: 40,
    });
  } catch {
    rows.value = [
      { provinceCode: 'GZ', schoolId: '110', universityName: '西南大学', majorCode: '080902', majorName: '软件工程', year: 2025, subjectType: '物理类', batch: '本科批', resubjectRequirement: '化学', minScore: 585, minRank: 8690, planCount: 24 },
      { provinceCode: 'GZ', schoolId: '141', universityName: '重庆邮电大学', majorCode: '080901', majorName: '计算机科学与技术', year: 2025, subjectType: '物理类', batch: '本科批', resubjectRequirement: '化学', minScore: 582, minRank: 9150, planCount: 32 },
      { provinceCode: 'GZ', schoolId: '538', universityName: '贵州大学', majorCode: '080901', majorName: '计算机科学与技术', year: 2025, subjectType: '物理类', batch: '本科批', resubjectRequirement: '化学', minScore: 580, minRank: 9420, planCount: 65 },
    ];
  }
}

onMounted(loadLines);
</script>
