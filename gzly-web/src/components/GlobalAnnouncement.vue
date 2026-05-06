<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Bell, X } from 'lucide-vue-next'
import { fetchCurrentAnnouncement } from '@/api/announcement'
import type { Announcement } from '@/types'
import { renderMarkdown } from '@/utils/markdown'

const route = useRoute()
const showAnnouncement = ref(false)
const currentAnnouncement = ref<Announcement | null>(null)
const fetched = ref(false)
const ANNOUNCEMENT_STORAGE_PREFIX = 'gzly-announcement-dismissed'

const announcementHtml = computed(() => renderMarkdown(currentAnnouncement.value?.contentMd || ''))

function isHomeRoute(): boolean {
  return route.name === 'Home' || route.path === '/'
}

function storageKey(announcement: Announcement): string {
  const version = announcement.updatedAt || announcement.publishedAt || ''
  return `${ANNOUNCEMENT_STORAGE_PREFIX}:${announcement.id || 'draft'}:${version}`
}

function hasDismissed(announcement: Announcement): boolean {
  try {
    return localStorage.getItem(storageKey(announcement)) === '1'
  } catch {
    return false
  }
}

function markDismissed(announcement: Announcement): void {
  try {
    localStorage.setItem(storageKey(announcement), '1')
  } catch {
    // localStorage may be unavailable in private or restricted browser contexts.
  }
}

function closeAnnouncement(): void {
  if (currentAnnouncement.value) {
    markDismissed(currentAnnouncement.value)
  }
  showAnnouncement.value = false
}

async function ensureLoaded(): Promise<void> {
  if (fetched.value) return
  fetched.value = true
  try {
    const res = await fetchCurrentAnnouncement()
    const announcement = res.data?.data || null
    if (!announcement?.id) return
    currentAnnouncement.value = announcement
  } catch {
    currentAnnouncement.value = null
  }
}

async function tryShowOnHome(): Promise<void> {
  if (!isHomeRoute()) return
  await ensureLoaded()
  const announcement = currentAnnouncement.value
  if (!announcement || hasDismissed(announcement)) return
  showAnnouncement.value = true
}

onMounted(() => {
  void tryShowOnHome()
})

watch(
  () => route.fullPath,
  () => {
    if (isHomeRoute()) {
      void tryShowOnHome()
    } else {
      showAnnouncement.value = false
    }
  },
)
</script>

<template>
  <teleport to="body">
    <div v-if="showAnnouncement && currentAnnouncement" class="announcement-overlay" @click.self="closeAnnouncement">
      <div class="announcement-modal" role="dialog" aria-modal="true" :aria-label="currentAnnouncement.title">
        <button class="announcement-close" type="button" aria-label="关闭公告" @click="closeAnnouncement">
          <X :size="18" />
        </button>
        <div class="announcement-head">
          <div class="announcement-icon"><Bell :size="18" /></div>
          <div>
            <div class="announcement-kicker">系统公告</div>
            <h3 class="announcement-title">{{ currentAnnouncement.title }}</h3>
          </div>
        </div>
        <div class="announcement-body markdown-body" v-html="announcementHtml"></div>
        <div class="announcement-actions">
          <button class="announcement-btn" type="button" @click="closeAnnouncement">我知道了</button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<style scoped>
.announcement-overlay {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: rgba(15, 23, 42, 0.48);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.announcement-modal {
  position: relative;
  width: min(720px, 100%);
  max-height: min(80vh, 720px);
  overflow: auto;
  background: #fffdfa;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 24px;
  box-shadow: 0 28px 60px rgba(15, 23, 42, 0.18);
  padding: 24px;
}

.announcement-close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: #f8fafc;
  color: #475569;
  cursor: pointer;
}

.announcement-head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.announcement-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: #0f172a;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.announcement-kicker {
  font-size: 12px;
  font-weight: 800;
  color: #1d4ed8;
  letter-spacing: 0.06em;
}

.announcement-title {
  margin-top: 6px;
  font-size: 24px;
  line-height: 1.25;
  font-weight: 800;
  color: #0f172a;
}

.announcement-body {
  margin-top: 18px;
  font-size: 14px;
  line-height: 1.75;
  color: #334155;
}

.announcement-body :deep(h2),
.announcement-body :deep(h3),
.announcement-body :deep(h4) {
  margin: 0 0 10px;
  color: #111827;
}

.announcement-body :deep(h2:not(:first-child)),
.announcement-body :deep(h3:not(:first-child)),
.announcement-body :deep(h4:not(:first-child)) {
  margin-top: 18px;
}

.announcement-body :deep(p) {
  margin: 0 0 12px;
}

.announcement-body :deep(ul) {
  margin: 0 0 12px;
  padding-left: 20px;
}

.announcement-body :deep(li) {
  margin: 4px 0;
}

.announcement-body :deep(blockquote) {
  margin: 12px 0;
  padding: 10px 12px;
  border-left: 3px solid #1d4ed8;
  background: #f8fafc;
  color: #475569;
}

.announcement-actions {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.announcement-btn {
  min-height: 42px;
  padding: 0 18px;
  border: none;
  border-radius: 12px;
  background: #0f172a;
  color: #fff;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
}
</style>
