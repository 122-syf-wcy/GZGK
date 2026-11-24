<template>
  <div class="flex h-screen w-screen overflow-hidden bg-paper text-ink-900">
    <!-- Persistent Collapsible Warm Ivory Left Sidebar (matching user reference) -->
    <aside
      :class="[
        'flex shrink-0 flex-col border-r border-line bg-sidebar transition-all duration-200',
        store.sidebarCollapsed ? 'w-16' : 'w-60',
      ]"
    >
      <!-- Top Brand Row -->
      <div class="flex h-14 items-center justify-between px-4">
        <router-link to="/" class="flex items-center gap-2.5 overflow-hidden">
          <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-sage-100 text-pine-700">
            <GraduationCap class="h-5 w-5" />
          </div>
          <span v-if="!store.sidebarCollapsed" class="truncate text-[16px] font-semibold tracking-tight text-ink-900">
            志愿辅助
          </span>
        </router-link>
        <div v-if="!store.sidebarCollapsed" class="flex items-center gap-1 text-ink-500">
          <router-link
            to="/universities"
            title="快速查院校"
            class="rounded-lg p-1.5 transition hover:bg-sage-100 hover:text-pine-700"
          >
            <Search class="h-4 w-4" />
          </router-link>
          <button
            type="button"
            title="收起侧边栏"
            class="rounded-lg p-1.5 transition hover:bg-sage-100 hover:text-pine-700"
            @click="store.sidebarCollapsed = true"
          >
            <PanelLeftClose class="h-4 w-4" />
          </button>
        </div>
        <button
          v-else
          type="button"
          title="展开侧边栏"
          class="mx-auto rounded-lg p-1.5 text-ink-500 transition hover:bg-sage-100 hover:text-pine-700"
          @click="store.sidebarCollapsed = false"
        >
          <PanelLeftOpen class="h-4 w-4" />
        </button>
      </div>

      <!-- Primary Action Pill: + 新建方案 -->
      <div class="px-3 pt-1">
        <router-link
          to="/new-plan"
          :class="[
            'flex items-center gap-2.5 rounded-xl bg-sage-100 px-3 py-2.5 text-sm font-medium text-pine-800 transition hover:bg-sage-200/80',
            store.sidebarCollapsed ? 'justify-center px-0' : '',
          ]"
        >
          <Plus class="h-4 w-4 shrink-0" />
          <span v-if="!store.sidebarCollapsed">新建方案</span>
        </router-link>
      </div>

      <!-- Main Navigation -->
      <nav class="mt-3 space-y-0.5 px-3">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          :class="[
            'flex items-center gap-2.5 rounded-xl px-3 py-2 text-[14px] transition',
            isActive(item.match)
              ? 'bg-sage-100/80 font-medium text-pine-800'
              : 'text-ink-700 hover:bg-stonewarm-50 hover:text-ink-900',
            store.sidebarCollapsed ? 'justify-center px-0' : '',
          ]"
        >
          <component :is="item.icon" class="h-[17px] w-[17px] shrink-0 text-ink-500" />
          <span v-if="!store.sidebarCollapsed">{{ item.label }}</span>
        </router-link>
      </nav>

      <!-- Workspaces Tree -->
      <div v-if="!store.sidebarCollapsed" class="mt-6 flex-1 overflow-y-auto px-3">
        <div class="flex items-center justify-between px-2 pb-1.5 text-[12px] font-medium text-ink-400">
          <span>工作区</span>
          <router-link to="/new-plan" class="rounded p-0.5 hover:bg-sage-100 hover:text-pine-700">
            <Plus class="h-3.5 w-3.5" />
          </router-link>
        </div>

        <div class="space-y-0.5">
          <div class="flex items-center gap-2 rounded-xl px-2.5 py-2 text-[13.5px] font-medium text-ink-900">
            <Folder class="h-4 w-4 text-pine-700" />
            <span class="truncate">2026 {{ store.activePlan.provinceName }}本科志愿</span>
          </div>
          <div class="ml-4 space-y-0.5 border-l border-line pl-3">
            <router-link
              :to="`/workspace/${store.activePlan.planId}`"
              :class="[
                'flex items-center gap-2 rounded-lg px-2.5 py-1.5 text-[13px] transition',
                route.name === 'workspace'
                  ? 'bg-sage-100 font-medium text-pine-800'
                  : 'text-ink-500 hover:bg-stonewarm-50 hover:text-ink-900',
              ]"
            >
              <span class="h-1.5 w-1.5 rounded-full bg-pine-700" />
              <span>方案工作台</span>
            </router-link>
            <router-link
              :to="`/report/${store.activePlan.planId}`"
              :class="[
                'flex items-center gap-2 rounded-lg px-2.5 py-1.5 text-[13px] transition',
                route.name === 'report'
                  ? 'bg-sage-100 font-medium text-pine-800'
                  : 'text-ink-500 hover:bg-stonewarm-50 hover:text-ink-900',
              ]"
            >
              <span class="h-1.5 w-1.5 rounded-full bg-sage-500" />
              <span>深度诊断报告</span>
            </router-link>
          </div>
        </div>

        <!-- Recent Threads (LangGraph Checkpointer Recents) -->
        <div class="mt-6 px-2 pb-1.5 text-[12px] font-medium text-ink-400">最近</div>
        <div class="space-y-0.5">
          <router-link
            v-for="t in store.recentThreads"
            :key="t.id"
            :to="t.route"
            class="flex items-center gap-2 rounded-lg px-2.5 py-1.5 text-[13px] text-ink-700 transition hover:bg-stonewarm-50 hover:text-ink-900"
          >
            <Clock class="h-3.5 w-3.5 shrink-0 text-ink-400" />
            <span class="truncate">{{ t.title }}</span>
          </router-link>
        </div>
      </div>

      <!-- Bottom Student Profile -->
      <div class="mt-auto border-t border-line p-3">
        <router-link
          to="/new-plan"
          :class="[
            'flex items-center gap-2.5 rounded-xl px-2 py-1.5 transition hover:bg-stonewarm-50',
            store.sidebarCollapsed ? 'justify-center px-0' : '',
          ]"
        >
          <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-sage-100 text-xs font-semibold text-pine-800">
            考
          </div>
          <div v-if="!store.sidebarCollapsed" class="min-w-0 flex-1">
            <div class="truncate text-[13px] font-medium text-ink-900">
              {{ store.activePlan.provinceName }} · {{ store.activePlan.totalScore }}分
            </div>
            <div class="truncate text-[11px] text-ink-400 tabular-nums">
              {{ store.activePlan.firstSubject }}类 · {{ store.activePlan.provinceRank.toLocaleString() }}位
            </div>
          </div>
          <ChevronRight v-if="!store.sidebarCollapsed" class="h-4 w-4 text-ink-400" />
        </router-link>
      </div>
    </aside>

    <!-- Main Viewport -->
    <div class="flex min-w-0 flex-1 flex-col">
      <!-- Minimal Top Breadcrumb Bar -->
      <header class="flex h-14 shrink-0 items-center justify-between border-b border-line/70 bg-paper/90 px-6 backdrop-blur">
        <div class="flex items-center gap-2 text-[13.5px] text-ink-500">
          <router-link to="/" class="flex items-center gap-1.5 transition hover:text-pine-700">
            <Home class="h-4 w-4" />
            <span>我的工作区</span>
          </router-link>
          <span>/</span>
          <span class="font-medium text-ink-900">{{ currentBreadcrumb }}</span>
          <span
            v-if="route.name === 'workspace' || route.name === 'report'"
            class="ml-2 hidden rounded-full bg-sage-100 px-2.5 py-0.5 text-xs font-medium text-pine-800 tabular-nums md:inline-flex"
          >
            {{ store.activePlan.firstSubject }}·{{ store.activePlan.resubjects.join('·') }} {{ store.activePlan.totalScore }}分 · {{ store.activePlan.provinceRank.toLocaleString() }}位
          </span>
        </div>

        <div class="flex items-center gap-2.5">
          <span
            class="hidden items-center gap-1.5 rounded-full border border-line bg-white px-3 py-1 text-xs text-ink-700 sm:inline-flex"
          >
            <span
              :class="[
                'h-2 w-2 rounded-full',
                store.activePlan.readinessStatus === 'FULL' ? 'bg-sage-600' : 'bg-ochre-600',
              ]"
            />
            <span>{{ store.activePlan.provinceName }}数据 {{ store.activePlan.readinessStatus }}</span>
          </span>
          <router-link
            :to="`/workspace/${store.activePlan.planId}`"
            class="inline-flex items-center gap-1.5 rounded-full border border-line bg-white px-3 py-1.5 text-xs font-medium text-ink-700 transition hover:border-pine-500/40 hover:text-pine-800"
          >
            <Clock class="h-3.5 w-3.5" />
            <span>工作台 v{{ store.activePlan.version }}</span>
          </router-link>
        </div>
      </header>

      <!-- Route Content -->
      <main class="relative min-h-0 flex-1 overflow-y-auto">
        <slot />
      </main>
    </div>

    <!-- Global Slide-Over RAG Official Charter Citation Drawer -->
    <CitationDrawer />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import {
  BarChart3,
  Bot,
  Building2,
  ChevronRight,
  Clock,
  FileText,
  Folder,
  GraduationCap,
  Home,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  Search,
} from 'lucide-vue-next';
import { useWorkspaceStore } from '@/stores/workspace';
import CitationDrawer from '@/components/CitationDrawer.vue';

const route = useRoute();
const store = useWorkspaceStore();

const navItems = [
  { to: '/', match: 'home', label: 'AI 志愿顾问', icon: Bot },
  { to: '/universities', match: 'universities', label: '院校与专业', icon: Building2 },
  { to: '/score-lines', match: 'score-lines', label: '历年分数线', icon: BarChart3 },
  { to: '/policies', match: 'policies', label: '政策与章程', icon: FileText },
];

function isActive(match: string) {
  return route.name === match;
}

const currentBreadcrumb = computed(() => {
  if (route.name === 'workspace') return `2026 ${store.activePlan.provinceName}本科志愿 / 方案工作台`;
  if (route.name === 'report') return `2026 ${store.activePlan.provinceName}本科志愿 / 深度诊断报告`;
  return (route.meta.breadcrumb as string) || `2026 ${store.activePlan.provinceName}本科志愿`;
});

onMounted(() => {
  store.ensurePoliciesLoaded();
});
</script>
