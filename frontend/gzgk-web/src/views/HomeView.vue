<template>
  <div class="mx-auto flex min-h-full max-w-4xl flex-col justify-center px-6 py-12">
    <!-- Serene Editorial Headline (1:1 inspired by reference) -->
    <div class="text-center">
      <h1 class="text-[36px] font-bold tracking-tight text-ink-900 sm:text-[42px]">
        今天，想从哪里开始？
      </h1>
      <p class="mt-2.5 text-[15px] text-ink-500">
        查院校、读章程，或从成绩出发，一起梳理志愿方案。
      </p>
    </div>

    <!-- Unified Agent Prompt Composer Card -->
    <form
      class="mt-8 rounded-[22px] border border-line bg-white p-5 shadow-composer transition focus-within:border-pine-600/45"
      @submit.prevent="launchFromPrompt"
    >
      <div class="flex items-center gap-2 text-xs text-ink-500">
        <span>选择一个工作区开始</span>
        <button
          type="button"
          class="inline-flex items-center gap-1.5 rounded-lg px-2 py-1 font-medium text-ink-900 transition hover:bg-sage-50"
          @click="router.push('/new-plan')"
        >
          <Folder class="h-3.5 w-3.5 text-pine-700" />
          <span>2026 {{ store.activePlan.provinceName }}本科志愿</span>
          <ChevronDown class="h-3 w-3 text-ink-400" />
        </button>
      </div>

      <textarea
        v-model="prompt"
        rows="3"
        placeholder="告诉我你的分数、位次，或想了解的专业与院校…"
        class="mt-3 w-full resize-none bg-transparent text-[15px] leading-relaxed text-ink-900 placeholder:text-ink-400 focus:outline-none"
        @keydown.enter.exact.prevent="launchFromPrompt"
      />

      <div class="mt-3 flex flex-wrap items-center justify-between gap-3 pt-1">
        <div class="flex items-center gap-2">
          <button
            type="button"
            title="新建成绩建档"
            class="flex h-8 w-8 items-center justify-center rounded-full border border-line text-ink-700 transition hover:bg-sage-50 hover:text-pine-800"
            @click="router.push('/new-plan')"
          >
            <Plus class="h-4 w-4" />
          </button>

          <select
            v-model="mode"
            class="rounded-full border border-line bg-paper px-3 py-1.5 text-xs font-medium text-ink-700 focus:border-pine-600 focus:outline-none"
          >
            <option value="advisor">志愿规划 · 沙箱调优</option>
            <option value="report">多专家深度诊断</option>
            <option value="charter">招生章程核验</option>
          </select>
        </div>

        <div class="flex items-center gap-3">
          <span class="hidden text-xs text-ink-400 sm:inline">也可以直接描述你的目标</span>
          <button
            type="submit"
            class="flex h-10 w-10 items-center justify-center rounded-full bg-pine-700 text-white shadow-sm transition hover:bg-pine-800"
          >
            <ArrowRight class="h-4 w-4" />
          </button>
        </div>
      </div>
    </form>

    <!-- 4 Quick-Action Intent Pills -->
    <div class="mt-5 flex flex-wrap items-center justify-center gap-2.5">
      <button
        type="button"
        class="inline-flex items-center gap-2 rounded-full border border-line bg-white px-4 py-2 text-[13.5px] font-medium text-ink-700 shadow-editorial transition hover:border-pine-500/40 hover:bg-sage-50 hover:text-pine-800"
        @click="router.push('/new-plan')"
      >
        <FileSpreadsheet class="h-4 w-4 text-pine-700" />
        <span>生成志愿方案</span>
      </button>
      <button
        type="button"
        class="inline-flex items-center gap-2 rounded-full border border-line bg-white px-4 py-2 text-[13.5px] font-medium text-ink-700 shadow-editorial transition hover:border-pine-500/40 hover:bg-sage-50 hover:text-pine-800"
        @click="router.push('/universities')"
      >
        <Building2 class="h-4 w-4 text-pine-700" />
        <span>查院校专业</span>
      </button>
      <button
        type="button"
        class="inline-flex items-center gap-2 rounded-full border border-line bg-white px-4 py-2 text-[13.5px] font-medium text-ink-700 shadow-editorial transition hover:border-pine-500/40 hover:bg-sage-50 hover:text-pine-800"
        @click="router.push(`/report/${store.activePlan.planId}`)"
      >
        <BarChart3 class="h-4 w-4 text-pine-700" />
        <span>分析已有方案</span>
      </button>
      <button
        type="button"
        class="inline-flex items-center gap-2 rounded-full border border-line bg-white px-4 py-2 text-[13.5px] font-medium text-ink-700 shadow-editorial transition hover:border-pine-500/40 hover:bg-sage-50 hover:text-pine-800"
        @click="router.push('/policies')"
      >
        <ShieldCheck class="h-4 w-4 text-pine-700" />
        <span>解读招生政策</span>
      </button>
    </div>

    <!-- Continue Exploring 3-Card Row -->
    <div class="mt-12">
      <div class="mb-3.5 flex items-center justify-between">
        <h2 class="text-[15px] font-semibold text-ink-900">继续探索</h2>
        <router-link
          :to="`/workspace/${store.activePlan.planId}`"
          class="inline-flex items-center gap-1 text-xs font-medium text-pine-700 hover:underline"
        >
          <span>进入工作台</span>
          <ArrowRight class="h-3.5 w-3.5" />
        </router-link>
      </div>

      <div class="grid gap-3.5 sm:grid-cols-3">
        <router-link
          :to="`/workspace/${store.activePlan.planId}`"
          class="group flex items-center gap-3.5 rounded-2xl border border-line bg-white p-4 shadow-editorial transition hover:border-pine-500/40"
        >
          <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-sage-100 text-pine-700">
            <GraduationCap class="h-5 w-5" />
          </div>
          <div class="min-w-0 flex-1">
            <div class="truncate text-sm font-semibold text-ink-900">
              2026 {{ store.activePlan.provinceName }}本科志愿
            </div>
            <div class="mt-0.5 truncate text-xs text-ink-400">
              方案工作台 · {{ store.activePlan.items.length }} 志愿 v{{ store.activePlan.version }}
            </div>
          </div>
          <ChevronRight class="h-4 w-4 text-ink-400 transition group-hover:translate-x-0.5 group-hover:text-pine-700" />
        </router-link>

        <router-link
          to="/universities"
          class="group flex items-center gap-3.5 rounded-2xl border border-line bg-white p-4 shadow-editorial transition hover:border-pine-500/40"
        >
          <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-sage-100 text-pine-700">
            <Building2 class="h-5 w-5" />
          </div>
          <div class="min-w-0 flex-1">
            <div class="truncate text-sm font-semibold text-ink-900">院校与专业探索</div>
            <div class="mt-0.5 truncate text-xs text-ink-400">2,198 所全国高校 L1 秒级检索</div>
          </div>
          <ChevronRight class="h-4 w-4 text-ink-400 transition group-hover:translate-x-0.5 group-hover:text-pine-700" />
        </router-link>

        <router-link
          to="/policies"
          class="group flex items-center gap-3.5 rounded-2xl border border-line bg-white p-4 shadow-editorial transition hover:border-pine-500/40"
        >
          <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-sage-100 text-pine-700">
            <FileText class="h-5 w-5" />
          </div>
          <div class="min-w-0 flex-1">
            <div class="truncate text-sm font-semibold text-ink-900">政策与章程问答</div>
            <div class="mt-0.5 truncate text-xs text-ink-400">8 省批次规则 · PGVector 溯源</div>
          </div>
          <ChevronRight class="h-4 w-4 text-ink-400 transition group-hover:translate-x-0.5 group-hover:text-pine-700" />
        </router-link>
      </div>
    </div>

    <p class="mt-10 text-center text-xs text-ink-400">
      {{ STANDARD_DISCLAIMER }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  ArrowRight,
  BarChart3,
  Building2,
  ChevronDown,
  ChevronRight,
  FileSpreadsheet,
  FileText,
  Folder,
  GraduationCap,
  Plus,
  ShieldCheck,
} from 'lucide-vue-next';
import { STANDARD_DISCLAIMER } from '@gzgk/shared';
import { useWorkspaceStore } from '@/stores/workspace';

const router = useRouter();
const store = useWorkspaceStore();
const prompt = ref('');
const mode = ref('advisor');

function launchFromPrompt() {
  const q = prompt.value.trim();
  if (!q) {
    router.push(`/workspace/${store.activePlan.planId}`);
    return;
  }
  store.pendingPromptFromHome = q;
  store.pendingModeFromHome = mode.value;
  if (mode.value === 'report') {
    router.push(`/report/${store.activePlan.planId}`);
  } else {
    router.push(`/workspace/${store.activePlan.planId}`);
  }
}
</script>
