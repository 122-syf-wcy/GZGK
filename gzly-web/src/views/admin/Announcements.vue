<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { fetchAdminAnnouncements, publishAdminAnnouncement, saveAdminAnnouncement } from '@/api/admin'
import type { Announcement } from '@/types'
import { renderMarkdown } from '@/utils/markdown'
import { showSuccessToast, showToast } from 'vant'
import { Bell, Megaphone, Save, Send } from 'lucide-vue-next'

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const page = ref(1)
const pageSize = 20
const statusFilter = ref<number | undefined>(undefined)
const items = ref<Announcement[]>([])
const total = ref(0)
const selectedId = ref<number | null>(null)

const form = reactive<Announcement>({
  title: '',
  contentMd: '',
  status: 0,
  popupEnabled: 1,
  sortOrder: 0,
})

const currentItem = computed(() => items.value.find(item => item.id === selectedId.value) || null)
const previewHtml = computed(() => renderMarkdown(form.contentMd || ''))

async function loadData() {
  loading.value = true
  try {
    const res = await fetchAdminAnnouncements(page.value, pageSize, statusFilter.value)
    const data = res.data?.data || {}
    items.value = data.items || []
    total.value = data.total || 0
    if (!selectedId.value && items.value.length) {
      selectItem(items.value[0])
    } else if (selectedId.value) {
      const found = items.value.find(item => item.id === selectedId.value)
      if (found) selectItem(found)
    }
  } catch (error: any) {
    showToast(error?.message || '公告加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  selectedId.value = null
  form.id = undefined
  form.title = ''
  form.contentMd = ''
  form.status = 0
  form.popupEnabled = 1
  form.sortOrder = 0
  form.publishedAt = undefined
}

function selectItem(item: Announcement) {
  selectedId.value = item.id || null
  form.id = item.id
  form.title = item.title
  form.contentMd = item.contentMd
  form.status = item.status ?? 0
  form.popupEnabled = item.popupEnabled ?? 1
  form.sortOrder = item.sortOrder ?? 0
  form.publishedAt = item.publishedAt
}

async function saveCurrent() {
  if (!form.title.trim()) {
    showToast('请输入公告标题')
    return
  }
  if (!form.contentMd.trim()) {
    showToast('请输入公告内容')
    return
  }
  saving.value = true
  try {
    const res = await saveAdminAnnouncement({ ...form })
    const saved = res.data?.data
    showSuccessToast('保存成功')
    await loadData()
    if (saved?.id) {
      const found = items.value.find(item => item.id === saved.id)
      if (found) selectItem(found)
    }
  } catch (error: any) {
    showToast(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function togglePublish(item: Announcement, published: boolean) {
  if (!item.id) return
  publishing.value = true
  try {
    await publishAdminAnnouncement(item.id, published)
    showSuccessToast(published ? '已发布公告' : '已下线公告')
    await loadData()
  } catch (error: any) {
    showToast(error?.message || '状态更新失败')
  } finally {
    publishing.value = false
  }
}

function statusLabel(status?: number) {
  return status === 1 ? '已发布' : status === 2 ? '已停用' : '草稿'
}

function statusClass(status?: number) {
  return status === 1 ? 'status--published' : status === 2 ? 'status--disabled' : 'status--draft'
}

onMounted(loadData)
</script>

<template>
  <div class="announcement-page">
    <div class="page-inner">
      <div class="page-head">
        <div>
          <h1 class="page-title">公告管理</h1>
          <p class="page-desc">系统管理员可发布 Markdown 公告，已发布公告会在首页以弹窗方式展示给用户。</p>
        </div>
        <button class="primary-btn" @click="resetForm">
          <Bell :size="16" />
          新建公告
        </button>
      </div>

      <div class="layout-grid">
        <section class="list-panel gz-card">
          <div class="panel-head">
            <div>
              <h2>公告列表</h2>
              <p>共 {{ total }} 条</p>
            </div>
            <div class="filter-row">
              <button class="filter-chip" :class="{ active: statusFilter === undefined }" @click="statusFilter = undefined; loadData()">全部</button>
              <button class="filter-chip" :class="{ active: statusFilter === 0 }" @click="statusFilter = 0; loadData()">草稿</button>
              <button class="filter-chip" :class="{ active: statusFilter === 1 }" @click="statusFilter = 1; loadData()">已发布</button>
              <button class="filter-chip" :class="{ active: statusFilter === 2 }" @click="statusFilter = 2; loadData()">已停用</button>
            </div>
          </div>

          <div v-if="loading" class="empty-state">加载中…</div>
          <div v-else-if="items.length === 0" class="empty-state">暂无公告</div>
          <div v-else class="announcement-list">
            <article
              v-for="item in items"
              :key="item.id"
              class="announcement-item"
              :class="{ active: selectedId === item.id }"
              @click="selectItem(item)"
            >
              <div class="announcement-item__head">
                <strong>{{ item.title }}</strong>
                <span class="status-chip" :class="statusClass(item.status)">{{ statusLabel(item.status) }}</span>
              </div>
              <p class="announcement-item__meta">
                {{ item.popupEnabled ? '首页弹窗开启' : '首页弹窗关闭' }} · 排序 {{ item.sortOrder ?? 0 }}
              </p>
              <p class="announcement-item__time">
                {{ item.publishedAt ? `发布时间：${item.publishedAt}` : '未发布' }}
              </p>
              <div class="announcement-item__actions">
                <button class="ghost-btn" :disabled="publishing" @click.stop="togglePublish(item, item.status !== 1)">
                  <Send :size="14" />
                  {{ item.status === 1 ? '下线' : '发布' }}
                </button>
              </div>
            </article>
          </div>
        </section>

        <section class="editor-panel gz-card">
          <div class="panel-head">
            <div>
              <h2>{{ form.id ? '编辑公告' : '新建公告' }}</h2>
              <p>支持标题、列表、强调、引用和链接。</p>
            </div>
            <button class="primary-btn" :disabled="saving" @click="saveCurrent">
              <Save :size="16" />
              {{ saving ? '保存中…' : '保存公告' }}
            </button>
          </div>

          <div class="form-grid">
            <div class="field-block">
              <label>公告标题</label>
              <input v-model="form.title" class="text-input" type="text" placeholder="例如：系统公开测试说明" />
            </div>

            <div class="field-inline-row">
              <div class="field-block">
                <label>状态</label>
                <select v-model="form.status" class="text-input">
                  <option :value="0">草稿</option>
                  <option :value="1">已发布</option>
                  <option :value="2">已停用</option>
                </select>
              </div>

              <div class="field-block">
                <label>首页弹窗</label>
                <select v-model="form.popupEnabled" class="text-input">
                  <option :value="1">开启</option>
                  <option :value="0">关闭</option>
                </select>
              </div>

              <div class="field-block">
                <label>排序值</label>
                <input v-model.number="form.sortOrder" class="text-input" type="number" placeholder="0" />
              </div>
            </div>

            <div class="preview-grid">
              <div class="field-block">
                <label>Markdown 内容</label>
                <textarea
                  v-model="form.contentMd"
                  class="text-area"
                  placeholder="# 公告标题\n\n- 测试范围\n- 已知说明\n\n> 结果仅供参考，请以官方为准。"
                ></textarea>
              </div>
              <div class="field-block">
                <label>预览</label>
                <div class="preview-card markdown-body" v-html="previewHtml || '<p>暂无内容</p>'"></div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.announcement-page { min-height: 100%; }
.page-inner { max-width: 1320px; margin: 0 auto; padding: 20px 16px; }
.page-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 18px; }
.page-title { font-size: 24px; font-weight: 800; color: #17181c; }
.page-desc { margin-top: 6px; font-size: 14px; line-height: 1.7; color: #6a6c72; }
.layout-grid { display: grid; gap: 16px; }
.panel-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 16px; }
.panel-head h2 { font-size: 18px; font-weight: 800; color: #17181c; }
.panel-head p { margin-top: 4px; font-size: 13px; color: #6a6c72; }
.primary-btn, .ghost-btn, .filter-chip { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 40px; padding: 0 14px; border-radius: 12px; font-size: 13px; font-weight: 700; cursor: pointer; }
.primary-btn { border: none; background: #17181c; color: #fff; }
.ghost-btn { border: 1px solid rgba(15,23,42,0.08); background: #fff; color: #383a40; }
.filter-row { display: flex; flex-wrap: wrap; gap: 8px; }
.filter-chip { border: 1px solid rgba(15,23,42,0.08); background: #fff; color: #4b4d54; }
.filter-chip.active { background: #17181c; border-color: #17181c; color: #fff; }
.announcement-list { display: flex; flex-direction: column; gap: 10px; }
.announcement-item { padding: 16px; border-radius: 16px; border: 1px solid rgba(15,23,42,0.08); background: #ffffff; cursor: pointer; }
.announcement-item.active { border-color: #17181c; box-shadow: 0 0 0 2px rgba(29,78,216,0.08); }
.announcement-item__head { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; }
.announcement-item__head strong { font-size: 15px; line-height: 1.5; color: #17181c; }
.announcement-item__meta, .announcement-item__time { margin-top: 6px; font-size: 12px; color: #6a6c72; }
.announcement-item__actions { margin-top: 12px; }
.status-chip { display: inline-flex; align-items: center; min-height: 26px; padding: 0 10px; border-radius: 999px; font-size: 11px; font-weight: 700; }
.status--draft { background: #fafaf8; color: #6a6c72; }
.status--published { background: #eef2ee; color: #2f6650; }
.status--disabled { background: #f7efef; color: #a03535; }
.empty-state { padding: 24px; text-align: center; color: #97999e; }
.form-grid { display: flex; flex-direction: column; gap: 14px; }
.field-block { display: flex; flex-direction: column; gap: 8px; }
.field-block label { font-size: 13px; font-weight: 700; color: #383a40; }
.field-inline-row { display: grid; gap: 12px; }
.text-input, .text-area { width: 100%; padding: 10px 12px; border: 1px solid rgba(15,23,42,0.08); border-radius: 12px; background: #fff; font-size: 14px; color: #17181c; }
.text-area { min-height: 320px; resize: vertical; }
.preview-grid { display: grid; gap: 14px; }
.preview-card { min-height: 320px; padding: 16px; border-radius: 16px; border: 1px solid rgba(15,23,42,0.08); background: #fff; overflow-wrap: anywhere; }
.markdown-body { font-size: 14px; line-height: 1.75; color: #383a40; }
.markdown-body :deep(h2), .markdown-body :deep(h3), .markdown-body :deep(h4) { margin: 0 0 10px; color: #17181c; line-height: 1.35; }
.markdown-body :deep(h2:not(:first-child)), .markdown-body :deep(h3:not(:first-child)), .markdown-body :deep(h4:not(:first-child)) { margin-top: 18px; }
.markdown-body :deep(p) { margin: 0 0 12px; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { margin: 0 0 12px; padding-left: 20px; }
.markdown-body :deep(li) { margin: 4px 0; }
.markdown-body :deep(blockquote) { margin: 12px 0; padding: 10px 12px; border-left: 3px solid #17181c; background: #fafaf8; color: #4b4d54; }
@media (min-width: 1024px) {
  .layout-grid { grid-template-columns: 360px minmax(0, 1fr); align-items: start; }
  .field-inline-row { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .preview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
