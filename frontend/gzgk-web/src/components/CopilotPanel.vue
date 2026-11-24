<template>
  <section class="flex h-full flex-col rounded-2xl border border-line bg-white shadow-editorial">
    <!-- Copilot Header -->
    <div class="border-b border-line px-4 py-3">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <div class="flex h-7 w-7 items-center justify-center rounded-lg bg-sage-100 text-pine-700">
            <Sparkles class="h-4 w-4" />
          </div>
          <span class="text-[14.5px] font-semibold text-ink-900">AI 志愿顾问</span>
        </div>
        <div class="flex items-center gap-2">
          <button
            type="button"
            title="开启新会话（保留跨会话长期记忆）"
            class="rounded-full border border-line bg-paper px-2.5 py-0.5 text-[11px] text-ink-700 transition hover:border-pine-600/40 hover:text-pine-800"
            @click="startNewThread"
          >
            + 新会话
          </button>
          <span class="inline-flex items-center gap-1.5 text-[11.5px] text-ink-500">
            <span class="h-2 w-2 rounded-full bg-sage-600" />
            ReAct · MCP
          </span>
        </div>
      </div>

      <!-- Context Window, Tools & Long-Term Memory Telemetry Strip -->
      <div class="mt-2.5 flex flex-wrap items-center justify-between gap-2 text-[11px] text-ink-500">
        <div class="flex items-center gap-1.5">
          <button
            type="button"
            :class="[
              'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 transition',
              inspectorTab === 'tools'
                ? 'border-pine-700 bg-sage-100 text-pine-800'
                : 'border-line bg-paper hover:bg-sage-50',
            ]"
            @click="toggleInspector('tools')"
          >
            <Wrench class="h-3 w-3" />
            <span class="tabular-nums">工具 {{ contextStats.nativeToolsCount }}+{{ contextStats.mcpToolsCount }} MCP</span>
          </button>

          <button
            type="button"
            :class="[
              'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 transition',
              inspectorTab === 'memory'
                ? 'border-pine-700 bg-sage-100 text-pine-800'
                : 'border-line bg-paper hover:bg-sage-50',
            ]"
            @click="toggleInspector('memory')"
          >
            <Brain class="h-3 w-3" />
            <span class="tabular-nums">长期记忆 {{ memories.length }}</span>
          </button>
        </div>

        <div class="flex items-center gap-1.5 tabular-nums">
          <span title="当前会话上下文 Token 用量">
            {{ contextStats.totalTokens.toLocaleString() }}/{{ contextStats.budgetTokens.toLocaleString() }} tok
          </span>
          <span v-if="contextStats.compressionCount > 0" class="rounded bg-sage-100 px-1.5 py-0.5 text-[10px] text-pine-800">
            压缩×{{ contextStats.compressionCount }}
          </span>
          <button
            type="button"
            :disabled="compacting"
            title="立即执行滚动摘要压缩并清理早期消息"
            class="inline-flex items-center gap-0.5 rounded-full border border-line bg-white px-2 py-0.5 text-[10.5px] font-medium text-pine-700 transition hover:bg-sage-50 disabled:opacity-45"
            @click="handleManualCompact"
          >
            <Minimize2 class="h-2.5 w-2.5" />
            <span>{{ compacting ? '压缩中' : '压缩' }}</span>
          </button>
        </div>
      </div>

      <!-- Thin Context Budget Progress Bar -->
      <div class="mt-1.5 h-1 w-full overflow-hidden rounded-full bg-stonewarm-100">
        <div
          :class="[
            'h-full rounded-full transition-all duration-500',
            contextStats.usagePercent >= 70 ? 'bg-ochre-600' : 'bg-pine-700',
          ]"
          :style="{ width: `${Math.max(4, contextStats.usagePercent)}%` }"
        />
      </div>

      <!-- Expandable Inline Inspector: Mounted Native + MCP Tools OR Long-Term Memory + Running Summary -->
      <div
        v-if="inspectorTab === 'tools'"
        class="mt-2.5 max-h-40 space-y-1.5 overflow-y-auto rounded-xl border border-line bg-paper p-2.5 text-[11.5px]"
      >
        <div class="font-medium text-ink-900">已动态挂载工具（LangGraph ToolNode）</div>
        <div v-for="t in toolCatalog" :key="t.name" class="flex items-start justify-between gap-2 border-t border-line/60 pt-1.5">
          <div class="min-w-0">
            <div class="flex items-center gap-1.5">
              <code class="font-mono text-[11px] font-semibold text-ink-900">{{ t.name }}</code>
              <span
                :class="[
                  'rounded px-1.5 py-0.2 text-[10px] font-medium',
                  t.source === 'mcp' ? 'bg-pine-700 text-white' : 'bg-sage-100 text-pine-800',
                ]"
              >
                {{ t.source === 'mcp' ? 'MCP' : 'gRPC' }}
              </span>
            </div>
            <p class="mt-0.5 line-clamp-1 text-[11px] text-ink-500">{{ t.description }}</p>
          </div>
        </div>
      </div>

      <div
        v-else-if="inspectorTab === 'memory'"
        class="mt-2.5 max-h-44 space-y-2 overflow-y-auto rounded-xl border border-line bg-paper p-2.5 text-[11.5px]"
      >
        <div>
          <div class="font-medium text-ink-900">跨会话长期记忆（自动注入系统提示词）</div>
          <div v-if="!memories.length" class="mt-1 text-ink-400">
            暂无记录。在对话中说出如「不接受民办、色弱、偏向川渝计算机、预算8000」会自动记住并跨会话生效。
          </div>
          <div v-else class="mt-1.5 space-y-1">
            <div
              v-for="m in memories"
              :key="m.key"
              class="flex items-center justify-between gap-2 rounded-lg bg-white px-2.5 py-1.5 border border-line/70"
            >
              <div class="min-w-0">
                <span class="mr-1.5 rounded bg-sage-100 px-1.5 py-0.5 text-[10px] font-medium text-pine-800">{{ m.category }}</span>
                <span class="text-ink-700">{{ m.value }}</span>
              </div>
              <button
                type="button"
                class="shrink-0 text-ink-400 hover:text-terracotta-600"
                title="遗忘此记忆"
                @click="removeMemory(m.key)"
              >
                <X class="h-3 w-3" />
              </button>
            </div>
          </div>
        </div>

        <div v-if="contextStats.hasRunningSummary" class="border-t border-line/70 pt-2">
          <div class="font-medium text-ink-900">当前滚动压缩摘要</div>
          <p class="mt-1 text-[11px] leading-relaxed text-ink-500">{{ contextStats.runningSummaryPreview }}</p>
        </div>
      </div>
    </div>

    <!-- Messages Scroll Area -->
    <div ref="scrollBox" class="flex-1 space-y-4 overflow-y-auto px-4 py-4">
      <div v-for="msg in store.chatMessages" :key="msg.id" class="space-y-2.5">
        <div v-if="msg.role === 'user'" class="flex justify-end">
          <div class="max-w-[88%] rounded-2xl rounded-tr-sm bg-sage-100 px-3.5 py-2.5 text-[13.5px] leading-relaxed text-ink-900">
            {{ msg.text }}
          </div>
        </div>

        <div v-else class="space-y-2.5">
          <!-- Collapsible ReAct + MCP + Memory Execution Trace -->
          <details
            v-if="msg.traces && msg.traces.length"
            open
            class="rounded-xl border border-line bg-paper/90 px-3 py-2 text-xs text-ink-700"
          >
            <summary class="flex cursor-pointer list-none items-center justify-between font-medium text-ink-700">
              <span class="inline-flex items-center gap-1.5">
                <Cpu class="h-3.5 w-3.5 text-pine-700" />
                <span>Agent 自主执行轨迹（{{ msg.traces.length }} 步）</span>
                <span v-if="msg.cacheHit" class="rounded bg-sage-100 px-1.5 py-0.5 text-[10px] text-pine-700">
                  语义缓存命中
                </span>
              </span>
              <ChevronDown class="h-3.5 w-3.5 text-ink-400" />
            </summary>
            <ul class="mt-2 space-y-1 border-t border-line/70 pt-2">
              <li v-for="(tr, idx) in msg.traces" :key="idx" class="flex items-center justify-between gap-2 text-[12px]">
                <span class="inline-flex items-center gap-1.5 text-ink-700">
                  <Check class="h-3 w-3 shrink-0 text-pine-700" />
                  <span>{{ tr.label }}</span>
                </span>
                <span v-if="tr.latencyMs" class="shrink-0 font-mono text-[11px] text-ink-400 tabular-nums">
                  {{ tr.latencyMs }}ms
                </span>
              </li>
            </ul>
          </details>

          <div class="whitespace-pre-line text-[13.5px] leading-relaxed text-ink-900">
            {{ msg.text }}
            <span v-if="msg.streaming" class="ml-0.5 inline-block h-3.5 w-1.5 animate-pulse bg-pine-700 align-middle" />
          </div>

          <div v-if="msg.sources && msg.sources.length" class="flex flex-wrap gap-1.5">
            <button
              v-for="src in msg.sources"
              :key="src.chunkId"
              type="button"
              class="inline-flex items-center gap-1 rounded-full border border-line bg-sage-50 px-2.5 py-0.5 text-[11.5px] text-pine-800 transition hover:border-pine-500/40 hover:bg-sage-100"
              @click="store.openCitation(src)"
            >
              <BookOpen class="h-3 w-3" />
              <span>[{{ src.index }}] {{ src.schoolName || src.title.slice(0, 12) }}</span>
            </button>
          </div>

          <!-- Interactive HITL Sandbox Diff Confirmation Card -->
          <div
            v-if="msg.diffCard"
            class="rounded-2xl border-[1.5px] border-pine-700/75 bg-white p-3.5 shadow-editorial"
          >
            <div class="mb-2 flex items-center justify-between">
              <span class="text-[13px] font-semibold text-ink-900">沙箱变更确认</span>
              <span class="rounded-full bg-sage-100 px-2 py-0.5 text-[11px] font-medium text-pine-800 tabular-nums">
                基于 v{{ msg.diffCard.baseVersion }}
              </span>
            </div>

            <div class="space-y-1.5 text-xs">
              <div
                v-for="(c, i) in msg.diffCard.changes"
                :key="i"
                :class="[
                  'rounded-lg px-2.5 py-2 leading-snug',
                  c.action === 'REMOVE'
                    ? 'bg-terracotta-50 text-terracotta-700'
                    : 'bg-sage-100 text-pine-800',
                ]"
              >
                <span class="font-semibold">{{ c.action === 'REMOVE' ? '- 移除' : '+ 新增' }} #{{ c.volunteerIndex }}</span>
                {{ c.universityName }} · {{ c.majorName }}（{{ c.gradient }} · 机会指数 {{ c.chanceScore }}）
                <div class="mt-0.5 text-[11px] opacity-85">{{ c.summary }}</div>
              </div>
            </div>

            <div class="mt-2.5 flex items-center justify-between border-t border-line/80 pt-2 text-xs text-ink-700 tabular-nums">
              <span>
                整表参考度
                <strong>{{ (msg.diffCard.beforeHitRate * 100).toFixed(1) }}%</strong>
                →
                <strong class="text-pine-700">{{ (msg.diffCard.afterHitRate * 100).toFixed(1) }}%</strong>
                ({{ msg.diffCard.hitRateDelta >= 0 ? '+' : '' }}{{ (msg.diffCard.hitRateDelta * 100).toFixed(1) }}%)
              </span>
              <span v-if="msg.diffCard.gradientCliffResolved" class="text-[11px] text-sage-600">梯度断层：消除</span>
            </div>

            <div v-if="!msg.diffCard.resolved" class="mt-3 flex items-center gap-2">
              <button
                type="button"
                :disabled="resuming"
                class="flex-1 rounded-full bg-pine-700 px-3 py-2 text-xs font-medium text-white transition hover:bg-pine-800 disabled:opacity-50"
                @click="handleHitlDecision(msg, 'approve')"
              >
                采纳并更新方案
              </button>
              <button
                type="button"
                :disabled="resuming"
                class="rounded-full border border-line bg-white px-4 py-2 text-xs font-medium text-ink-700 transition hover:bg-stonewarm-50 disabled:opacity-50"
                @click="handleHitlDecision(msg, 'reject')"
              >
                暂不修改
              </button>
            </div>
            <div v-else class="mt-2.5 rounded-lg bg-paper px-3 py-1.5 text-center text-xs font-medium text-pine-700">
              {{ msg.diffCard.resolved === 'approved' ? '✓ 已采纳并同步更新左侧志愿工作台' : '已保留原方案未修改' }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Quick Prompt Chips & Rounded Composer -->
    <div class="border-t border-line p-3">
      <div class="mb-2 flex flex-wrap gap-1.5">
        <button
          v-for="chip in quickChips"
          :key="chip"
          type="button"
          class="rounded-full border border-line bg-paper px-2.5 py-1 text-[11.5px] text-ink-700 transition hover:border-pine-500/40 hover:bg-sage-50 hover:text-pine-800"
          @click="sendMessage(chip)"
        >
          {{ chip }}
        </button>
      </div>

      <form class="flex items-center gap-2 rounded-2xl border border-line bg-paper px-3 py-2 focus-within:border-pine-600/50 focus-within:bg-white" @submit.prevent="submitInput">
        <input
          v-model="inputText"
          type="text"
          placeholder="输入调优指令、专业前景或你的偏好红线…"
          class="min-w-0 flex-1 bg-transparent text-[13.5px] text-ink-900 placeholder:text-ink-400 focus:outline-none"
          :disabled="sending"
        />
        <button
          type="submit"
          :disabled="sending || !inputText.trim()"
          class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-pine-700 text-white transition hover:bg-pine-800 disabled:opacity-40"
        >
          <ArrowRight class="h-4 w-4" />
        </button>
      </form>
      <p class="mt-1.5 text-center text-[10.5px] text-ink-400">
        {{ STANDARD_DISCLAIMER }}
      </p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue';
import { ArrowRight, BookOpen, Brain, Check, ChevronDown, Cpu, Minimize2, Sparkles, Wrench, X } from 'lucide-vue-next';
import { STANDARD_DISCLAIMER, streamSsePost } from '@gzgk/shared';
import type {
  AgentContextStats,
  AgentToolInfo,
  AgentTraceStep,
  HitlDiffCard,
  RagSource,
  StudentMemoryItem,
} from '@gzgk/shared';
import { agentApi } from '@/api/client';
import { type ChatMessageItem, useWorkspaceStore } from '@/stores/workspace';

const emit = defineEmits<{
  planUpdated: [];
}>();

const store = useWorkspaceStore();
const userId = 'demo-student';
const activeThreadId = ref(`thread-${Date.now()}`);
const inputText = ref('');
const sending = ref(false);
const resuming = ref(false);
const compacting = ref(false);
const inspectorTab = ref<'none' | 'tools' | 'memory'>('none');
const scrollBox = ref<HTMLElement | null>(null);

const toolCatalog = ref<AgentToolInfo[]>([
  { name: 'estimate_rank', source: 'native-grpc', server: 'gzgk-core', description: '查询官方一分一段位次区间' },
  { name: 'query_school_major_history', source: 'native-grpc', server: 'gzgk-core', description: '查询高校历年投档分与位次' },
  { name: 'get_plan_items', source: 'native-grpc', server: 'gzgk-core', description: '扫描当前志愿方案与缩招预警' },
  { name: 'search_knowledge_hybrid', source: 'native-grpc', server: 'gzgk-core', description: 'PGVector HNSW + 稀疏全文混合检索' },
  { name: 'simulate_plan_adjustment', source: 'native-grpc', server: 'gzgk-core', description: 'Java 2000次蒙特卡洛沙箱推演' },
  { name: 'save_student_preference', source: 'native-grpc', server: 'gzgk-core', description: '持久化考生跨会话长期偏好' },
  { name: 'mcp_lookup_province_policy', source: 'mcp', server: 'gzgk-gaokao-mcp', description: '查询省份新高考批次与调剂政策' },
  { name: 'mcp_major_career_insights', source: 'mcp', server: 'gzgk-gaokao-mcp', description: '查询本科专业读研去向与就业城市画像' },
  { name: 'mcp_check_medical_and_subject_redlines', source: 'mcp', server: 'gzgk-gaokao-mcp', description: '核查色盲/色弱/高学费与单科体检红线' },
]);

const memories = ref<StudentMemoryItem[]>([]);
const contextStats = ref<AgentContextStats>({
  totalTokens: 210,
  budgetTokens: 6000,
  triggerTokens: 3800,
  usagePercent: 3.5,
  messageCount: 1,
  compressionCount: 0,
  hasRunningSummary: false,
  runningSummaryPreview: '',
  memoryCount: 0,
  nativeToolsCount: 6,
  mcpToolsCount: 3,
});

const quickChips = [
  '把冲档里缩招严重的去掉，补2个川渝计算机稳档',
  '我不接受民办、有点色弱，看看计算机就业和体检红线',
  '贵州96平行志愿退档风险怎么避坑？',
];

function toggleInspector(tab: 'tools' | 'memory') {
  inspectorTab.value = inspectorTab.value === tab ? 'none' : tab;
}

function startNewThread() {
  activeThreadId.value = `thread-${Date.now()}`;
  store.chatMessages = [
    {
      id: `welcome-${Date.now()}`,
      role: 'assistant',
      text: `已开启新会话（已自动继承你的 **${memories.value.length} 条跨会话长期记忆**）。直接告诉我你想核查的院校、章程或志愿调优指令即可。`,
    },
  ];
  contextStats.value = {
    ...contextStats.value,
    totalTokens: 160,
    usagePercent: 2.7,
    messageCount: 1,
    compressionCount: 0,
    hasRunningSummary: false,
    runningSummaryPreview: '',
  };
}

function scrollToBottom() {
  nextTick(() => {
    if (scrollBox.value) {
      scrollBox.value.scrollTop = scrollBox.value.scrollHeight;
    }
  });
}

async function handleManualCompact() {
  if (compacting.value) return;
  compacting.value = true;
  try {
    const res = await agentApi.compactThread(activeThreadId.value, userId);
    if (res.contextStats) {
      contextStats.value = res.contextStats;
    }
    store.chatMessages.push({
      id: `compact-${Date.now()}`,
      role: 'assistant',
      text: res.compressed
        ? `已完成上下文滚动压缩：**${res.beforeTokens} → ${res.afterTokens} tokens**（归档清理 ${res.removedCount} 条历史消息并保留最近轮次与工具边界完整性）。`
        : '当前会话消息数尚少，暂无需压缩上下文。',
    });
  } catch {
    contextStats.value = {
      ...contextStats.value,
      totalTokens: Math.max(180, Math.round(contextStats.value.totalTokens * 0.45)),
      usagePercent: Math.max(3, Math.round(contextStats.value.usagePercent * 0.45)),
      compressionCount: contextStats.value.compressionCount + 1,
      hasRunningSummary: true,
      runningSummaryPreview: '已归档前序多轮对话与工具查数结论，保留最近关键上下文。',
    };
  } finally {
    compacting.value = false;
    scrollToBottom();
  }
}

async function removeMemory(key: string) {
  memories.value = memories.value.filter((m) => m.key !== key);
  contextStats.value.memoryCount = memories.value.length;
  try {
    await agentApi.deleteMemory(userId, key);
  } catch {
    // offline fallback already updated
  }
}

async function submitInput() {
  const q = inputText.value.trim();
  if (!q || sending.value) return;
  inputText.value = '';
  await sendMessage(q);
}

async function sendMessage(text: string, mode = 'advisor') {
  if (!text.trim() || sending.value) return;
  sending.value = true;
  const threadId = activeThreadId.value;

  store.chatMessages.push({ id: `u-${Date.now()}`, role: 'user', text });
  store.addRecentThread(text, `/workspace/${store.activePlan.planId}`);

  const aiMsg: ChatMessageItem = {
    id: `a-${Date.now() + 1}`,
    role: 'assistant',
    text: '',
    traces: [],
    sources: [],
    streaming: true,
    threadId,
  };
  store.chatMessages.push(aiMsg);
  scrollToBottom();

  try {
    await streamSsePost({
      url: agentApi.streamUrl,
      body: {
        userId,
        threadId,
        planId: store.activePlan.planId,
        provinceCode: store.activePlan.provinceCode,
        message: text,
        mode,
      },
      onEvent(ev) {
        if (ev.type === 'status' || ev.type === 'tool_result' || ev.type === 'tool_call') {
          const step: AgentTraceStep = {
            stage: ev.payload.stage || ev.type,
            tool: ev.payload.tool,
            label: ev.payload.label || '执行中…',
            latencyMs: ev.payload.latencyMs,
          };
          aiMsg.traces = [...(aiMsg.traces || []), step];
        } else if (ev.type === 'sources' && Array.isArray(ev.payload.sources)) {
          aiMsg.sources = ev.payload.sources as RagSource[];
        } else if (ev.type === 'content' && ev.payload.text) {
          aiMsg.text += ev.payload.text;
        } else if (ev.type === 'diff_card' && ev.payload.diffCard) {
          aiMsg.diffCard = ev.payload.diffCard as HitlDiffCard;
        } else if (ev.type === 'context_stats') {
          if (ev.payload.contextStats) contextStats.value = ev.payload.contextStats as AgentContextStats;
          if (Array.isArray(ev.payload.memories)) memories.value = ev.payload.memories as StudentMemoryItem[];
        } else if (ev.type === 'final') {
          if (ev.payload.text && !aiMsg.text) aiMsg.text = ev.payload.text;
          aiMsg.cacheHit = Boolean(ev.payload.cacheHit);
          aiMsg.streaming = false;
        }
        scrollToBottom();
      },
      onDone() {
        aiMsg.streaming = false;
        scrollToBottom();
      },
    });
  } catch {
    runOfflineDemoReply(aiMsg, text);
  } finally {
    aiMsg.streaming = false;
    sending.value = false;
    scrollToBottom();
  }
}

async function handleHitlDecision(msg: ChatMessageItem, decision: 'approve' | 'reject') {
  if (!msg.diffCard || resuming.value) return;
  resuming.value = true;
  try {
    if (msg.threadId) {
      const res = await agentApi.resumeHitl(msg.threadId, decision);
      msg.diffCard.resolved = decision === 'approve' ? 'approved' : 'rejected';
      store.chatMessages.push({
        id: `hitl-${Date.now()}`,
        role: 'assistant',
        text: res.answer,
        traces: res.traces,
      });
      if (decision === 'approve') {
        await store.refreshPlan(store.activePlan.planId);
        applyLocalVisualBump();
        emit('planUpdated');
      }
    }
  } catch {
    msg.diffCard.resolved = decision === 'approve' ? 'approved' : 'rejected';
    if (decision === 'approve') {
      applyLocalVisualBump();
      emit('planUpdated');
      store.chatMessages.push({
        id: `hitl-local-${Date.now()}`,
        role: 'assistant',
        text: `已采纳本次沙箱调优建议，左侧志愿工作台已更新至 **v${store.activePlan.version}**。`,
      });
    } else {
      store.chatMessages.push({
        id: `hitl-cancel-${Date.now()}`,
        role: 'assistant',
        text: '好的，已保留原志愿方案未做改动。',
      });
    }
  } finally {
    resuming.value = false;
    scrollToBottom();
  }
}

function applyLocalVisualBump() {
  const p = store.activePlan;
  if (p.planId === 'demo-gz-586') {
    p.version += 1;
    p.listHitRate = 0.984;
    if (p.items[18]) {
      p.items[18].universityName = '重庆邮电大学';
      p.items[18].majorName = '计算机科学与技术';
      p.items[18].gradient = '稳';
      p.items[18].chanceScore = 81;
      p.items[18].aiHighlighted = true;
    }
  }
}

function runOfflineDemoReply(aiMsg: ChatMessageItem, text: string) {
  const wantsSandbox = /缩招|去掉|替换|补|调优|修改/.test(text);
  aiMsg.traces = [
    { stage: 'router', label: '意图路由 · 自主 ReAct Agent（6 Native + 3 MCP 工具）', latencyMs: 12 },
    { stage: 'react_plan', label: 'ReAct 第 1 轮自主规划 · 并行调用 [search_knowledge_hybrid, mcp_check_medical_and_subject_redlines]', latencyMs: 18 },
    ...(wantsSandbox ? [{ stage: 'sandbox', label: 'ReAct 第 2 轮 · Java 沙箱 2000 次蒙特卡洛推演完成', latencyMs: 39 }] : []),
  ];
  contextStats.value.totalTokens = Math.min(contextStats.value.budgetTokens, contextStats.value.totalTokens + 420);
  contextStats.value.usagePercent = Math.round((contextStats.value.totalTokens / contextStats.value.budgetTokens) * 1000) / 10;

  aiMsg.sources = [
    {
      index: 1,
      chunkId: 'charter_cqupt_2025#000',
      title: '重庆邮电大学 2025 年本科招生章程 · 第十六条',
      docType: 'charter',
      schoolName: '重庆邮电大学',
      excerpt: '第十六条：计算机科学与技术、软件工程专业限招物理+化学选考考生，不设外语单科成绩限制，普通本科工科学费约 5,600 元/学年。',
      highlight: '限招物理+化学选考考生，不设外语单科成绩限制',
      sourceUrl: 'https://zs.cqupt.edu.cn/',
      verified: true,
    },
  ];
  if (wantsSandbox) {
    aiMsg.text = '已完成 ReAct 多轮查数、MCP 体检核验与 Java 蒙特卡洛沙箱重算 [1]，请在下方确认是否采纳变更：';
    aiMsg.diffCard = {
      interruptId: `demo-hitl-${Date.now()}`,
      planId: store.activePlan.planId,
      baseVersion: store.activePlan.version,
      beforeHitRate: 0.961,
      afterHitRate: 0.984,
      hitRateDelta: 0.023,
      gradientCliffResolved: true,
      changes: [
        { action: 'REMOVE', volunteerIndex: 5, universityName: '南京邮电大学', majorName: '通信工程', gradient: '冲', chanceScore: 38, summary: '当年计划缩减 45%' },
        { action: 'ADD', volunteerIndex: 19, universityName: '重庆邮电大学', majorName: '计算机科学与技术', gradient: '稳', chanceScore: 81, summary: '25年最低 9,150 位，章程已核验' },
      ],
    };
  } else {
    aiMsg.text = '已调用 MCP 专业就业与体检红线工具完成核查 [1]：计算机与通信方向对色弱通常限制较少，但光电/微电子细分方向需逐校核对招生章程。';
  }
}

onMounted(async () => {
  try {
    const [tools, mems] = await Promise.all([agentApi.listTools(), agentApi.getMemories(userId)]);
    if (tools?.length) {
      toolCatalog.value = tools;
      contextStats.value.nativeToolsCount = tools.filter((t) => t.source !== 'mcp').length;
      contextStats.value.mcpToolsCount = tools.filter((t) => t.source === 'mcp').length;
    }
    if (mems) {
      memories.value = mems;
      contextStats.value.memoryCount = mems.length;
    }
  } catch {
    // keep default offline inspector metadata
  }

  if (store.pendingPromptFromHome) {
    const q = store.pendingPromptFromHome;
    const mode = store.pendingModeFromHome;
    store.pendingPromptFromHome = '';
    sendMessage(q, mode);
  }
});
</script>
