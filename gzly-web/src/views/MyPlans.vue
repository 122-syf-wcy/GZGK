<template>
  <div class="my-plans">
    <header class="my-plans__head">
      <button class="auth-back" type="button" aria-label="返回" @click="goBack">
        <ArrowLeft :size="18" />
      </button>
      <div class="my-plans__title-block">
        <h1 class="my-plans__title">我的志愿空间</h1>
        <p class="my-plans__sub">{{ greeting }}</p>
      </div>
      <button class="logout-btn" type="button" @click="onLogout">
        <LogOut :size="14" /> 退出
      </button>
    </header>

    <section v-if="profileSummary" class="profile-card">
      <div class="profile-card__row">
        <div class="profile-card__cell">
          <span class="profile-card__label">已生成 / 上限</span>
          <strong class="profile-card__value">{{ profileSummary.usedPlans }} / {{ profileSummary.maxPlans }}</strong>
        </div>
        <div class="profile-card__cell">
          <span class="profile-card__label">剩余次数</span>
          <strong class="profile-card__value">{{ profileSummary.remainPlans }}</strong>
        </div>
        <div class="profile-card__cell" v-if="profileSummary.expiresAt">
          <span class="profile-card__label">有效期至</span>
          <strong class="profile-card__value">{{ formatDate(profileSummary.expiresAt) }}</strong>
        </div>
      </div>
    </section>

    <section class="action-row">
      <button class="primary-btn" type="button" @click="router.push('/volunteer')">
        <Plus :size="16" /> 生成新方案
      </button>
      <button class="ghost-btn" type="button" @click="openClaimDialog">
        <Link2 :size="14" /> 绑定旧方案
      </button>
      <button class="ghost-btn" type="button" @click="loadList">
        <RefreshCw :size="14" /> 刷新
      </button>
    </section>

    <div v-if="claimOpen" class="claim-overlay" role="dialog" aria-modal="true" @click.self="closeClaimDialog">
      <div class="claim-dialog">
        <header class="claim-dialog__head">
          <h3>绑定旧方案到本账号</h3>
          <button type="button" class="claim-dialog__close" aria-label="关闭" @click="closeClaimDialog">×</button>
        </header>
        <p class="claim-dialog__desc">
          如果你之前用其他设备 / 未登录状态下生成过方案，请输入方案 ID 与安全码，
          把它一次性挂到当前账号；之后即可在「我的志愿空间」直接看到。
        </p>
        <label class="claim-dialog__field">
          <span>方案 ID</span>
          <input v-model.trim="claimPlanIdInput" type="text" inputmode="numeric" placeholder="例如 168" :disabled="claimLoading" />
        </label>
        <label class="claim-dialog__field">
          <span>安全码</span>
          <input v-model.trim="claimSafetyCode" type="text" placeholder="生成方案后提示保存的安全码" :disabled="claimLoading" />
        </label>
        <p v-if="claimError" class="claim-dialog__error">{{ claimError }}</p>
        <footer class="claim-dialog__foot">
          <button type="button" class="ghost-btn" :disabled="claimLoading" @click="closeClaimDialog">取消</button>
          <button type="button" class="primary-btn" :disabled="!canClaim || claimLoading" @click="submitClaim">
            {{ claimLoading ? '绑定中…' : '确认绑定' }}
          </button>
        </footer>
      </div>
    </div>

    <section v-if="loading" class="loading">加载中…</section>
    <section v-else-if="!plans.length" class="empty-state">
      <ShieldAlert :size="32" />
      <p>还没有任何方案。</p>
      <p class="empty-tip">点击「生成新方案」开始你的志愿草稿。</p>
    </section>
    <section v-else class="plans-list">
      <article
        v-for="plan in plans"
        :key="plan.id"
        class="plan-card"
        @click="openPlan(plan.id)"
      >
        <header class="plan-card__head">
          <span class="plan-card__province">{{ provinceLabel(plan.provinceCode) }}</span>
          <span class="plan-card__time">{{ formatDate(plan.createdAt) }}</span>
        </header>
        <div class="plan-card__row">
          <div>
            <span class="plan-card__label">分数 · 位次</span>
            <strong class="plan-card__big">{{ plan.totalScore || '-' }} · {{ plan.provinceRank || '-' }}</strong>
          </div>
          <div>
            <span class="plan-card__label">志愿数</span>
            <strong class="plan-card__big">{{ plan.itemCount || 0 }}</strong>
          </div>
          <div>
            <span class="plan-card__label">策略</span>
            <strong class="plan-card__small">{{ plan.strategyMode || '-' }}</strong>
          </div>
        </div>
        <footer class="plan-card__foot">
          <span class="badge" :class="plan.hasAiAnalysis ? 'badge--ok' : 'badge--muted'">
            {{ plan.hasAiAnalysis ? 'AI 已解读' : '未解读' }}
          </span>
          <span class="plan-card__cta">
            查看 <ChevronRight :size="14" />
          </span>
        </footer>
      </article>
    </section>

    <footer v-if="totalPages > 1" class="pager">
      <button :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import {
  ArrowLeft,
  ChevronRight,
  Link2,
  LogOut,
  Plus,
  RefreshCw,
  ShieldAlert,
} from 'lucide-vue-next'
import { claimMyPlan, listMyPlans, type MyPlanListItem } from '@/api/myPlans'
import { fetchMe } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const plans = ref<MyPlanListItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(true)

// 绑定旧匿名方案对话框状态
const claimOpen = ref(false)
const claimPlanIdInput = ref('')
const claimSafetyCode = ref('')
const claimLoading = ref(false)
const claimError = ref('')
const canClaim = computed(() => {
  const pid = Number(claimPlanIdInput.value)
  return Number.isInteger(pid) && pid > 0 && claimSafetyCode.value.length > 0
})

const profileSummary = computed(() => authStore.profile)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const greeting = computed(() => {
  const nick = authStore.profile.nickname?.trim()
  return nick ? `欢迎回来，${nick}` : '欢迎回来'
})

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/')
}

async function refreshProfile() {
  try {
    const res = await fetchMe()
    const data = res.data.data
    authStore.updateProfile({
      userId: data.userId,
      identifier: data.identifier,
      nickname: data.nickname,
      maxPlans: data.maxPlans,
      usedPlans: data.usedPlans,
      remainPlans: data.remainPlans,
      expiresAt: data.expiresAt ?? null,
    })
  } catch {
    // 忽略 profile 拉取失败，axios 拦截器会清理过期 token
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await listMyPlans(page.value, size.value)
    plans.value = res.data.data.items || []
    total.value = res.data.data.total || 0
  } catch (e: any) {
    showToast(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openPlan(id: number) {
  router.push({ name: 'VolunteerResult', query: { planId: String(id) } })
}

function openClaimDialog() {
  claimPlanIdInput.value = ''
  claimSafetyCode.value = ''
  claimError.value = ''
  claimOpen.value = true
}

function closeClaimDialog() {
  if (claimLoading.value) return
  claimOpen.value = false
}

async function submitClaim() {
  if (!canClaim.value || claimLoading.value) return
  claimLoading.value = true
  claimError.value = ''
  try {
    const res = await claimMyPlan(Number(claimPlanIdInput.value), claimSafetyCode.value)
    const data = res.data?.data
    if (data?.alreadyOwned) {
      showSuccessToast('该方案此前已绑定到当前账号')
    } else {
      showSuccessToast('绑定成功')
    }
    claimOpen.value = false
    page.value = 1
    await loadList()
  } catch (e: any) {
    claimError.value = e?.message || '绑定失败，请确认 ID 与安全码是否正确'
  } finally {
    claimLoading.value = false
  }
}

function changePage(target: number) {
  if (target < 1 || target > totalPages.value) return
  page.value = target
  loadList()
}

function onLogout() {
  authStore.logout()
  showSuccessToast('已退出登录')
  router.replace('/volunteer')
}

function provinceLabel(code?: string) {
  switch (code) {
    case 'GZ': return '贵州'
    case 'SC': return '四川'
    case 'HB': return '湖北'
    case 'AH': return '安徽'
    default: return code || '其他'
  }
}

function formatDate(value?: string | null) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

onMounted(async () => {
  await refreshProfile()
  if (!authStore.isAuthenticated) {
    router.replace('/volunteer')
    return
  }
  loadList()
})
</script>

<style scoped>
.my-plans {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px 16px 48px;
  min-height: 100dvh;
  background: #fbf6e8;
}
.my-plans__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}
.my-plans__title-block {
  flex: 1;
}
.my-plans__title {
  font-size: 22px;
  font-weight: 900;
  color: #1f2933;
}
.my-plans__sub {
  font-size: 13px;
  color: #475569;
  margin-top: 4px;
}
.auth-back, .logout-btn {
  height: 36px;
  border-radius: 999px;
  border: 1px solid rgba(31, 41, 51, 0.12);
  background: #fffdf7;
  color: #1f2933;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.auth-back { width: 36px; }
.logout-btn { padding: 0 12px; gap: 4px; font-size: 12px; font-weight: 700; }
.profile-card {
  background: #fffdf7;
  border: 1px solid rgba(95, 70, 48, 0.18);
  border-radius: 16px;
  padding: 14px 14px;
  margin-bottom: 16px;
}
.profile-card__row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.profile-card__cell {
  display: grid;
  gap: 4px;
}
.profile-card__label {
  font-size: 11px;
  color: #64748b;
  font-weight: 700;
}
.profile-card__value {
  font-size: 16px;
  font-weight: 900;
  color: #1f2933;
}
.action-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
.primary-btn, .ghost-btn {
  min-height: 42px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 14px;
}
.primary-btn {
  background: linear-gradient(140deg, #1f2933 0%, #0f172a 100%);
  color: #fffdf7;
  border: none;
  flex: 1;
  justify-content: center;
}
.ghost-btn {
  background: #f7f2e8;
  color: #1f2933;
  border: 1px solid rgba(31, 41, 51, 0.1);
}
.loading, .empty-state {
  text-align: center;
  padding: 32px 16px;
  color: #475569;
}
.empty-tip { color: #94a3b8; margin-top: 4px; font-size: 12px; }
.plans-list {
  display: grid;
  gap: 10px;
}
.plan-card {
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.1);
  border-radius: 14px;
  padding: 14px;
  cursor: pointer;
  transition: transform 0.14s ease, box-shadow 0.14s ease;
}
.plan-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(31, 41, 51, 0.08);
}
.plan-card__head {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #475569;
  font-weight: 700;
  margin-bottom: 8px;
}
.plan-card__province { color: #5f4630; }
.plan-card__row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.plan-card__row > div {
  display: grid;
  gap: 2px;
}
.plan-card__label {
  font-size: 11px;
  color: #94a3b8;
}
.plan-card__big {
  font-size: 16px;
  font-weight: 900;
  color: #1f2933;
}
.plan-card__small {
  font-size: 13px;
  color: #1f2933;
  font-weight: 700;
}
.plan-card__foot {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.badge {
  font-size: 11px;
  padding: 3px 9px;
  border-radius: 999px;
  font-weight: 700;
}
.badge--ok { background: #ecfdf5; color: #047857; }
.badge--muted { background: #f1f5f9; color: #64748b; }
.plan-card__cta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #1f2933;
  font-weight: 700;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  color: #475569;
  font-size: 13px;
}
.pager button {
  min-height: 32px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid rgba(31, 41, 51, 0.1);
  background: #fffdf7;
  cursor: pointer;
}
.pager button:disabled { opacity: 0.5; cursor: not-allowed; }

/* 绑定旧方案对话框 */
.claim-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.claim-dialog {
  width: 100%;
  max-width: 420px;
  background: #fffdf7;
  border-radius: 16px;
  padding: 18px 18px 16px;
  border: 1px solid rgba(31, 41, 51, 0.1);
  box-shadow: 0 18px 32px rgba(15, 23, 42, 0.18);
  display: grid;
  gap: 12px;
}
.claim-dialog__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.claim-dialog__head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 900;
  color: #1f2933;
}
.claim-dialog__close {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  border: 1px solid rgba(31, 41, 51, 0.12);
  background: #f7f2e8;
  color: #1f2933;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}
.claim-dialog__desc {
  font-size: 12px;
  color: #475569;
  line-height: 1.55;
  margin: 0;
}
.claim-dialog__field {
  display: grid;
  gap: 4px;
}
.claim-dialog__field span {
  font-size: 12px;
  color: #64748b;
  font-weight: 700;
}
.claim-dialog__field input {
  height: 38px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid rgba(31, 41, 51, 0.15);
  background: #fffdf7;
  font-size: 14px;
  color: #1f2933;
  outline: none;
}
.claim-dialog__field input:focus {
  border-color: #1f2933;
}
.claim-dialog__error {
  margin: 0;
  font-size: 12px;
  color: #b91c1c;
}
.claim-dialog__foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.claim-dialog__foot .ghost-btn,
.claim-dialog__foot .primary-btn {
  flex: none;
  min-width: 96px;
  justify-content: center;
}
</style>
