<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { X } from 'lucide-vue-next'
import { fetchVolunteerPlan } from '@/api/volunteer'
import { persistSafetyCode, setCurrentSafetyCode, useVolunteerStore } from '@/stores/volunteer'
import { formDataFromPlan } from '@/utils/volunteer-plan'

const props = defineProps<{
  modelValue: boolean
  initialPlanId?: number | string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  restored: [planId: number]
}>()

const router = useRouter()
const volunteerStore = useVolunteerStore()
const planIdInput = ref('')
const safetyCodeInput = ref('')
const restoring = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

watch(
  () => visible.value,
  (next) => {
    if (next && props.initialPlanId && !planIdInput.value) {
      planIdInput.value = String(props.initialPlanId)
    }
  },
)

function closeDialog() {
  if (restoring.value) return
  visible.value = false
}

function resetSecret() {
  safetyCodeInput.value = ''
}

async function restorePlan() {
  const planId = Number(planIdInput.value)
  const safetyCode = safetyCodeInput.value.trim()
  if (!Number.isFinite(planId) || planId <= 0) {
    showToast('请输入正确的方案 ID')
    return
  }
  if (!safetyCode) {
    showToast('请输入方案查看凭证')
    return
  }

  restoring.value = true
  try {
    const res = await fetchVolunteerPlan(planId, safetyCode)
    const plan = res.data.data
    volunteerStore.setPlanFromResponse(plan, safetyCode)
    volunteerStore.setFormData(formDataFromPlan(plan))
    persistSafetyCode(plan.id || planId, safetyCode)
    setCurrentSafetyCode(safetyCode)
    visible.value = false
    resetSecret()
    emit('restored', plan.id || planId)
    showSuccessToast('方案已找回')
    router.push({ path: '/volunteer/result', query: { planId: String(plan.id || planId) } })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '找回失败，请核对方案 ID 和查看凭证'
    showToast(message)
  } finally {
    restoring.value = false
  }
}
</script>

<template>
  <van-popup
    v-model:show="visible"
    position="bottom"
    round
    :close-on-click-overlay="!restoring"
    :style="{ maxHeight: '86%', background: '#fffdfa' }"
    @closed="resetSecret"
  >
    <form class="restore-dialog" @submit.prevent="restorePlan">
      <div class="restore-dialog__head">
        <div>
          <div class="restore-dialog__kicker">plan restore</div>
          <h3 class="restore-dialog__title">找回历史方案</h3>
          <p class="restore-dialog__desc">输入方案 ID 和查看凭证，通过验证后继续查看原方案。</p>
        </div>
        <button class="restore-dialog__close" type="button" :disabled="restoring" @click="closeDialog">
          <X :size="18" />
        </button>
      </div>

      <div class="restore-dialog__body">
        <label class="restore-field">
          <span>方案 ID</span>
          <input
            v-model.trim="planIdInput"
            type="number"
            inputmode="numeric"
            min="1"
            autocomplete="off"
            placeholder="例如：711"
          />
        </label>
        <label class="restore-field">
          <span>方案查看凭证</span>
          <input
            v-model.trim="safetyCodeInput"
            type="password"
            autocomplete="one-time-code"
            placeholder="请输入生成时保存的凭证"
          />
        </label>
        <p class="restore-dialog__note">凭证只用于本次验证，不会在页面上明文展示；找回后地址栏不会携带凭证。</p>
      </div>

      <div class="restore-dialog__actions">
        <button class="restore-dialog__btn restore-dialog__btn--ghost" type="button" :disabled="restoring" @click="closeDialog">
          取消
        </button>
        <button class="restore-dialog__btn restore-dialog__btn--primary" type="submit" :disabled="restoring">
          {{ restoring ? '验证中...' : '找回方案' }}
        </button>
      </div>
    </form>
  </van-popup>
</template>

<style scoped>
.restore-dialog {
  padding: 20px;
}

.restore-dialog__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.restore-dialog__kicker {
  color: #2563eb;
  font-size: 11px;
  font-weight: 850;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.restore-dialog__title {
  margin: 4px 0 0;
  color: #0f172a;
  font-size: 20px;
  font-weight: 850;
}

.restore-dialog__desc {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.restore-dialog__close {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 999px;
  background: #f1f5f9;
  color: #334155;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.restore-dialog__body {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.restore-field {
  display: grid;
  gap: 7px;
  color: #334155;
  font-size: 13px;
  font-weight: 800;
}

.restore-field input {
  width: 100%;
  min-height: 44px;
  border: 1px solid #dbe3ef;
  border-radius: 12px;
  background: #ffffff;
  color: #0f172a;
  font-size: 15px;
  padding: 0 12px;
  outline: none;
}

.restore-field input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.restore-dialog__note {
  margin: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

.restore-dialog__actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.restore-dialog__btn {
  flex: 1;
  min-height: 42px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 850;
}

.restore-dialog__btn--ghost {
  border: 1px solid #e2e8f0;
  background: #ffffff;
  color: #475569;
}

.restore-dialog__btn--primary {
  border: none;
  background: #0f172a;
  color: #ffffff;
}

.restore-dialog__btn:disabled,
.restore-dialog__close:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (min-width: 768px) {
  .restore-dialog {
    width: min(460px, 100vw);
    margin: 0 auto;
  }
}
</style>
