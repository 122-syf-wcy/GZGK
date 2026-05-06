<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { fetchAdminAiConfig, fetchAdminAiModels, saveAdminAiConfig, testAdminAiConfig } from '@/api/admin'
import type { AdminAiConfigTestResult, SaveAdminAiConfigRequest } from '@/types'
import { showSuccessToast, showToast } from 'vant'
import { Bot, KeyRound, Save, Search, ShieldCheck, Wifi } from 'lucide-vue-next'

defineOptions({ name: 'AdminAiConfig' })

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const loadingModels = ref(false)
const apiKeyInput = ref('')
const apiKeyMasked = ref('')
const configSource = ref('')
const updatedAt = ref('')
const testResult = ref<AdminAiConfigTestResult | null>(null)
const modelOptions = ref<string[]>([])
const canQueryModels = computed(() => Boolean(form.baseUrl.trim() && (apiKeyInput.value.trim() || apiKeyMasked.value)))

const form = reactive<SaveAdminAiConfigRequest>({
  providerName: 'OpenAI兼容服务',
  baseUrl: '',
  apiKey: '',
  chatModel: 'gpt-4o-mini',
  reviewModel: 'gpt-4o-mini',
  visionModel: 'gpt-4o',
  maxTokens: 2600,
  temperature: 0.7,
  systemPrompt: '',
  enabled: true,
})

function applyConfig(data: any) {
  form.providerName = data.providerName || 'OpenAI兼容服务'
  form.baseUrl = data.baseUrl || ''
  form.chatModel = data.chatModel || 'gpt-4o-mini'
  form.reviewModel = data.reviewModel || form.chatModel
  form.visionModel = data.visionModel || form.chatModel
  form.maxTokens = data.maxTokens || 2600
  form.temperature = data.temperature ?? 0.7
  form.systemPrompt = data.systemPrompt || ''
  form.enabled = data.enabled !== false
  apiKeyInput.value = ''
  apiKeyMasked.value = data.apiKeyMasked || ''
  configSource.value = data.configSource || ''
  updatedAt.value = data.updatedAt || ''
}

async function loadData() {
  loading.value = true
  try {
    const res = await fetchAdminAiConfig()
    applyConfig(res.data?.data || {})
  } catch (error: any) {
    showToast(error?.message || 'AI配置加载失败')
  } finally {
    loading.value = false
  }
}

function validateForm() {
  if (!form.baseUrl.trim()) return '请输入AI接口地址'
  if (!/^https?:\/\//i.test(form.baseUrl.trim())) return '接口地址必须以 http:// 或 https:// 开头'
  if (!form.chatModel.trim()) return '请输入志愿解读模型'
  if (!form.reviewModel.trim()) return '请输入问答文本审核模型'
  if (!form.visionModel.trim()) return '请输入图片审核模型'
  if (form.maxTokens < 256 || form.maxTokens > 12000) return 'maxTokens 需在 256 到 12000 之间'
  if (form.temperature < 0 || form.temperature > 2) return 'temperature 需在 0 到 2 之间'
  return ''
}

function buildPayload() {
  const payload: SaveAdminAiConfigRequest = {
    ...form,
    baseUrl: form.baseUrl.trim(),
    providerName: form.providerName.trim() || 'OpenAI兼容服务',
    chatModel: form.chatModel.trim(),
    reviewModel: form.reviewModel.trim(),
    visionModel: form.visionModel.trim(),
    systemPrompt: form.systemPrompt.trim(),
  }
  if (apiKeyInput.value.trim()) {
    payload.apiKey = apiKeyInput.value.trim()
  } else {
    delete payload.apiKey
  }
  return payload
}

function optionsFor(currentModel: string) {
  const current = currentModel.trim()
  if (current && !modelOptions.value.includes(current)) {
    return [current, ...modelOptions.value]
  }
  return modelOptions.value
}

async function saveConfig() {
  const error = validateForm()
  if (error) {
    showToast(error)
    return
  }
  saving.value = true
  try {
    const res = await saveAdminAiConfig(buildPayload())
    applyConfig(res.data?.data || {})
    showSuccessToast('AI配置已保存')
  } catch (error: any) {
    showToast(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function testConnection() {
  const error = validateForm()
  if (error) {
    showToast(error)
    return
  }
  testing.value = true
  testResult.value = null
  try {
    const res = await testAdminAiConfig(buildPayload())
    const result = res.data?.data || null
    testResult.value = result
    if (result?.success) {
      showSuccessToast('AI连接测试成功')
    } else {
      showToast(result?.message || 'AI连接测试失败')
    }
  } catch (error: any) {
    showToast(error?.message || 'AI连接测试失败')
  } finally {
    testing.value = false
  }
}

async function queryModels() {
  if (!form.baseUrl.trim()) {
    showToast('请输入AI接口地址')
    return
  }
  if (!apiKeyInput.value.trim() && !apiKeyMasked.value) {
    showToast('请输入API Key后再查询模型')
    return
  }
  loadingModels.value = true
  try {
    const res = await fetchAdminAiModels(buildPayload())
    const result = res.data?.data
    modelOptions.value = result?.models || []
    if (result?.success) {
      showSuccessToast(result.message || '模型列表已更新')
    } else {
      showToast(result?.message || '模型查询失败')
    }
  } catch (error: any) {
    showToast(error?.message || '模型查询失败')
  } finally {
    loadingModels.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="ai-config-page">
    <div class="page-inner">
      <div class="page-head">
        <div>
          <h1 class="page-title">AI配置</h1>
          <p class="page-desc">统一管理志愿AI解读、问答文本审核和图片审核的 OpenAI 兼容接口。</p>
        </div>
        <div class="head-actions">
          <button class="secondary-btn" :disabled="testing || saving || loading" @click="testConnection">
            <Wifi :size="16" />
            {{ testing ? '测试中…' : '测试连接' }}
          </button>
          <button class="primary-btn" :disabled="saving || loading" @click="saveConfig">
            <Save :size="16" />
            {{ saving ? '保存中…' : '保存配置' }}
          </button>
        </div>
      </div>

      <section class="status-card gz-card">
        <div class="status-icon">
          <Bot :size="22" />
        </div>
        <div>
          <h2>{{ form.enabled ? 'AI服务已启用' : 'AI服务已停用' }}</h2>
          <p>
            当前来源：{{ configSource === 'database' ? '数据库配置' : '环境变量兜底' }}
            <span v-if="updatedAt"> · 更新于 {{ updatedAt }}</span>
          </p>
          <div
            v-if="testResult"
            class="test-result"
            :class="{ 'test-result--ok': testResult.success, 'test-result--fail': !testResult.success }"
          >
            {{ testResult.message }}
            <span v-if="testResult.latencyMs"> · {{ testResult.latencyMs }}ms</span>
            <span v-if="testResult.model"> · {{ testResult.model }}</span>
          </div>
        </div>
      </section>

      <section class="config-grid">
        <div class="editor-panel gz-card">
          <div class="panel-head">
            <div>
              <h2>服务连接</h2>
              <p>密钥只会保存到后端数据库，接口不会向前端返回明文。</p>
            </div>
          </div>

          <div class="form-grid">
            <div class="field-block">
              <label>服务商名称</label>
              <input v-model="form.providerName" class="text-input" placeholder="例如：OpenAI兼容服务 / 自建代理" />
            </div>

            <div class="field-block">
              <label>Base URL</label>
              <input v-model="form.baseUrl" class="text-input" placeholder="https://example.com/v1" />
              <small>支持填写到域名根路径或 `/v1`，后端会自动拼接 `/chat/completions`。</small>
            </div>

            <div class="field-block">
              <label>API Key</label>
              <div class="key-row">
                <input v-model="apiKeyInput" class="text-input" type="password" placeholder="留空则保留现有密钥" />
                <button
                  class="secondary-btn key-query-btn"
                  :disabled="loadingModels || saving || testing || !canQueryModels"
                  @click="queryModels"
                >
                  <Search :size="15" />
                  {{ loadingModels ? '查询中…' : '查询模型' }}
                </button>
                <span class="key-mask">
                  <KeyRound :size="14" />
                  {{ apiKeyMasked || '未配置' }}
                </span>
              </div>
            </div>

            <div class="field-inline-row">
              <label class="switch-row">
                <input v-model="form.enabled" type="checkbox" />
                <span>启用AI服务</span>
              </label>
            </div>
          </div>
        </div>

        <div class="editor-panel gz-card">
          <div class="panel-head">
            <div>
              <h2>模型参数</h2>
              <p>问答审核可单独指定轻量模型，图片审核可指定视觉模型。</p>
            </div>
            <button class="secondary-btn panel-action" :disabled="loadingModels || saving || testing || !canQueryModels" @click="queryModels">
              <Search :size="15" />
              {{ loadingModels ? '查询中…' : '查询模型' }}
            </button>
          </div>

          <div class="form-grid">
            <div class="field-block">
              <label>志愿AI解读模型</label>
              <select v-if="modelOptions.length" v-model="form.chatModel" class="text-input">
                <option v-for="model in optionsFor(form.chatModel)" :key="model" :value="model">{{ model }}</option>
              </select>
              <input v-else v-model="form.chatModel" class="text-input" placeholder="gpt-4o-mini" />
            </div>

            <div class="field-block">
              <label>问答文本审核模型</label>
              <select v-if="modelOptions.length" v-model="form.reviewModel" class="text-input">
                <option v-for="model in optionsFor(form.reviewModel)" :key="model" :value="model">{{ model }}</option>
              </select>
              <input v-else v-model="form.reviewModel" class="text-input" placeholder="gpt-4o-mini" />
            </div>

            <div class="field-block">
              <label>图片审核模型</label>
              <select v-if="modelOptions.length" v-model="form.visionModel" class="text-input">
                <option v-for="model in optionsFor(form.visionModel)" :key="model" :value="model">{{ model }}</option>
              </select>
              <input v-else v-model="form.visionModel" class="text-input" placeholder="gpt-4o" />
            </div>

            <div v-if="modelOptions.length" class="model-options-note">
              已加载 {{ modelOptions.length }} 个模型，可在上方下拉框中选择；未查询前仍支持手动输入。
            </div>

            <div class="field-inline-row">
              <div class="field-block">
                <label>maxTokens</label>
                <input v-model.number="form.maxTokens" class="text-input" type="number" min="256" max="12000" />
              </div>
              <div class="field-block">
                <label>temperature</label>
                <input v-model.number="form.temperature" class="text-input" type="number" min="0" max="2" step="0.1" />
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="prompt-panel gz-card">
        <div class="panel-head">
          <div>
            <h2>志愿AI解读系统提示词</h2>
            <p>用于 `/volunteer/ai-analysis`，问答审核仍走固定安全审核模板。</p>
          </div>
          <div class="safe-badge">
            <ShieldCheck :size="15" />
            不返回密钥
          </div>
        </div>
        <textarea
          v-model="form.systemPrompt"
          class="prompt-area"
          placeholder="请输入给AI的系统提示词，例如：你是一名资深的贵州省高考志愿填报顾问..."
        ></textarea>
      </section>
    </div>
  </div>
</template>

<style scoped>
.ai-config-page {
  min-height: 100dvh;
  background: #f5f3ee;
}

.page-inner {
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  padding: 24px;
}

.page-head,
.panel-head,
.status-card,
.key-row,
.switch-row,
.safe-badge {
  display: flex;
  align-items: center;
}

.page-head {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.page-title {
  margin: 0;
  font-size: 26px;
  line-height: 1.2;
}

.page-desc {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 14px;
}

.head-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.secondary-btn,
.primary-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 16px;
  color: #fff;
  font-weight: 700;
  background: #111827;
  border: 0;
  border-radius: 999px;
}

.secondary-btn {
  color: #111827;
  background: #fff;
  border: 1px solid #dbe3ea;
}

.secondary-btn:disabled,
.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.panel-action {
  min-height: 34px;
  padding: 0 12px;
}

.key-query-btn {
  flex-shrink: 0;
  min-height: 42px;
  border-radius: 12px;
}

.status-card {
  gap: 14px;
  margin-bottom: 18px;
}

.status-icon {
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #1d4ed8;
  background: #dbeafe;
  border-radius: 14px;
}

.status-card h2,
.panel-head h2 {
  margin: 0;
  font-size: 18px;
}

.status-card p,
.panel-head p,
.field-block small {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.test-result {
  display: inline-flex;
  align-items: center;
  margin-top: 10px;
  padding: 7px 10px;
  font-size: 12px;
  font-weight: 700;
  border-radius: 999px;
}

.test-result--ok {
  color: #166534;
  background: #dcfce7;
}

.test-result--fail {
  color: #991b1b;
  background: #fee2e2;
}

.config-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}

.editor-panel,
.prompt-panel {
  padding: 20px;
}

.panel-head {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.form-grid {
  display: grid;
  gap: 16px;
}

.field-inline-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field-block {
  display: grid;
  gap: 7px;
}

.field-block label {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.model-options-note {
  padding: 10px 12px;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
  background: #dbeafe;
  border-radius: 12px;
}

.text-input,
.prompt-area {
  width: 100%;
  color: #111827;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.text-input {
  min-height: 42px;
  padding: 0 12px;
}

.prompt-area {
  min-height: 220px;
  padding: 12px;
  line-height: 1.7;
  resize: vertical;
}

.text-input:focus,
.prompt-area:focus {
  border-color: #1d4ed8;
  box-shadow: 0 0 0 3px rgba(29, 78, 216, 0.12);
}

.key-row {
  gap: 10px;
}

.key-row .text-input {
  flex: 1;
}

.key-mask,
.safe-badge {
  flex-shrink: 0;
  gap: 6px;
  min-height: 34px;
  padding: 0 10px;
  color: #166534;
  font-size: 12px;
  font-weight: 700;
  background: #dcfce7;
  border-radius: 999px;
}

.switch-row {
  gap: 8px;
  color: #334155;
  font-weight: 700;
}

.switch-row input {
  width: 18px;
  height: 18px;
}

.prompt-panel {
  margin-top: 18px;
}

@media (max-width: 920px) {
  .page-head,
  .config-grid {
    display: block;
  }

  .head-actions {
    display: grid;
    grid-template-columns: 1fr;
    margin-top: 14px;
  }

  .secondary-btn,
  .primary-btn {
    justify-content: center;
    width: 100%;
  }

  .editor-panel + .editor-panel {
    margin-top: 18px;
  }

  .field-inline-row {
    grid-template-columns: 1fr;
  }

  .key-row {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
