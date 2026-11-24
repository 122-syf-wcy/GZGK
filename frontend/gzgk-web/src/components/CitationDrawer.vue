<template>
  <transition name="slide">
    <div v-if="store.citationDrawerOpen && store.activeCitation" class="fixed inset-0 z-50 flex justify-end">
      <div class="fixed inset-0 bg-ink-900/15 backdrop-blur-[1px]" @click="store.closeCitation()" />
      <aside class="relative z-10 flex h-full w-full max-w-md flex-col border-l border-line bg-white shadow-2xl">
        <div class="flex items-center justify-between border-b border-line px-6 py-4">
          <span class="inline-flex items-center gap-1.5 rounded-full bg-sage-100 px-3 py-1 text-xs font-medium text-pine-800">
            <BookOpenCheck class="h-3.5 w-3.5" />
            <span>官方章程溯源 · 引用 #{{ store.activeCitation.index }}</span>
          </span>
          <button
            type="button"
            class="rounded-lg p-1.5 text-ink-500 transition hover:bg-stonewarm-50 hover:text-ink-900"
            @click="store.closeCitation()"
          >
            <X class="h-4 w-4" />
          </button>
        </div>

        <div class="flex-1 space-y-5 overflow-y-auto px-6 py-5">
          <div>
            <h3 class="text-lg font-semibold leading-snug text-ink-900">
              {{ store.activeCitation.title }}
            </h3>
            <div class="mt-2.5 flex flex-wrap items-center gap-2">
              <span
                v-if="store.activeCitation.verified"
                class="inline-flex items-center gap-1 rounded-full bg-sage-100 px-2.5 py-0.5 text-xs font-medium text-pine-700"
              >
                <ShieldCheck class="h-3.5 w-3.5" />
                官方来源已核验
              </span>
              <span
                v-if="store.activeCitation.schoolName"
                class="rounded-full bg-ochre-50 px-2.5 py-0.5 text-xs font-medium text-ochre-600"
              >
                {{ store.activeCitation.schoolName }}
              </span>
              <span class="rounded-full bg-stonewarm-50 px-2.5 py-0.5 text-xs text-ink-500">
                {{ docTypeLabel(store.activeCitation.docType) }}
              </span>
            </div>
          </div>

          <!-- Parchment Editorial Quote Card -->
          <div class="rounded-2xl border border-line bg-[#FAF8F2] p-4 shadow-editorial">
            <div class="border-l-[3px] border-pine-700 pl-3.5 text-[14px] leading-relaxed text-ink-900">
              <p v-if="store.activeCitation.highlight" class="mb-2 rounded bg-ochre-200/45 px-1.5 py-0.5 font-medium">
                {{ store.activeCitation.highlight }}
              </p>
              <p class="whitespace-pre-line text-ink-700">{{ store.activeCitation.excerpt }}</p>
            </div>
          </div>

          <div class="rounded-xl border border-line/80 bg-paper px-4 py-3 text-xs leading-relaxed text-ink-500">
            检索切片 ID：<code class="font-mono text-ink-700">{{ store.activeCitation.chunkId }}</code><br />
            本条内容由 PostgreSQL 16 `pgvector` HNSW + 稀疏全文混合检索（RRF）召回。正式填报前请核对高校当年最新官方章程全文。
          </div>
        </div>

        <div class="flex items-center gap-3 border-t border-line px-6 py-4">
          <a
            v-if="store.activeCitation.sourceUrl"
            :href="store.activeCitation.sourceUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex flex-1 items-center justify-center gap-1.5 rounded-xl border border-line bg-white px-4 py-2.5 text-sm font-medium text-ink-900 transition hover:bg-stonewarm-50"
          >
            <span>查看学校官方原文</span>
            <ExternalLink class="h-3.5 w-3.5" />
          </a>
          <button
            type="button"
            class="inline-flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-pine-700 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-pine-800"
            @click="markVerified"
          >
            <Check class="h-4 w-4" />
            <span>{{ verifiedLocal ? '已标记核验' : '标记人工已核验' }}</span>
          </button>
        </div>
      </aside>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { BookOpenCheck, Check, ExternalLink, ShieldCheck, X } from 'lucide-vue-next';
import { useWorkspaceStore } from '@/stores/workspace';

const store = useWorkspaceStore();
const verifiedLocal = ref(false);

watch(
  () => store.activeCitation?.chunkId,
  () => {
    verifiedLocal.value = false;
  },
);

function markVerified() {
  verifiedLocal.value = true;
  setTimeout(() => store.closeCitation(), 450);
}

function docTypeLabel(t: string) {
  if (t === 'charter') return '高校招生章程';
  if (t === 'policy') return '考试院政策';
  return '填报方法论库';
}
</script>
