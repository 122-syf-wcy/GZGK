<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import axios from 'axios'
import {
  Menu,
  X,
  LogOut,
  Shield,
  Lock,
} from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()

const sidebarOpen = ref(false)
const isAuthed = ref(false)
const loginPassword = ref('')
const loginError = ref('')
const loginLoading = ref(false)

onMounted(async () => {
  const token = localStorage.getItem('gz_token')
  if (token) {
    try {
      const res = await axios.get('/api/admin/stats', { headers: { Authorization: `Bearer ${token}` } })
      if (res.data?.code === 0) {
        isAuthed.value = true
        return
      }
    } catch { /* token invalid */ }
    localStorage.removeItem('gz_token')
  }
  isAuthed.value = false
})

async function doAdminLogin() {
  loginLoading.value = true
  loginError.value = ''
  try {
    const res = await axios.post('/api/admin/login', { password: loginPassword.value })
    if (res.data?.code === 0 && res.data.data?.token) {
      localStorage.setItem('gz_token', res.data.data.token)
      isAuthed.value = true
    } else {
      loginError.value = res.data?.message || '登录失败'
    }
  } catch (e: any) {
    loginError.value = e.response?.data?.message || '登录失败'
  } finally {
    loginLoading.value = false
  }
}

function doLogout() {
  localStorage.removeItem('gz_token')
  isAuthed.value = false
  loginPassword.value = ''
}

// 一级核心：志愿主流程相关，每天/每周高频访问
const primaryNav = [
  { key: 'dashboard', label: '数据看板', iconImg: '/admin-dashboard.png', path: '/admin' },
  { key: 'data-year-readiness', label: '数据准备进度', iconImg: '/admin-dashboard.png', path: '/admin/data-year-readiness' },
  { key: 'plans', label: '方案记录', iconImg: '/admin-plans.png', path: '/admin/plans' },
  { key: 'users', label: '用户管理', iconImg: '/admin-users.png', path: '/admin/users' },
  { key: 'universities', label: '院校管理', iconImg: '/admin-university.png', path: '/admin/universities' },
  { key: 'score-lines', label: '分数线管理', iconImg: '/admin-scoreline.png', path: '/admin/score-lines' },
  { key: 'official-links', label: '官方入口', iconImg: '/admin-university.png', path: '/admin/official-links' },
]

// 二级：偶发使用的运营/AI/数据治理工具
const secondaryNav = [
  { key: 'announcements', label: '公告管理', iconImg: '/admin-dashboard.png', path: '/admin/announcements' },
  { key: 'ai-config', label: 'AI配置', iconImg: '/admin-dashboard.png', path: '/admin/ai-config' },
  { key: 'ai-qa', label: 'AI问答', iconImg: '/admin-dashboard.png', path: '/admin/ai-qa' },
  { key: 'data-review-cq-gs-xj', label: 'CQ/GS/XJ审核', iconImg: '/admin-dashboard.png', path: '/admin/data-review/cq-gs-xj' },
  { key: 'feedbacks', label: '反馈管理', iconImg: '/admin-users.png', path: '/admin/feedbacks' },
  { key: 'ops', label: '巡检状态', iconImg: '/admin-dashboard.png', path: '/admin/ops' },
  { key: 'server-security', label: '服务器安全', iconImg: '/admin-dashboard.png', path: '/admin/security/server' },
]

// 历史保留：以下功能此前已开发，但和主志愿流程关联较弱，仅保留路由可访问、不在
// 侧栏频繁占位（直接访问 /admin/<path> 仍可使用，避免删除既有数据）：
// - encouragement-messages（加油墙留言管理）
// - alumni-review（校友审核）
// - qa-review（院校问答审核）
const navItems = [...primaryNav, ...secondaryNav]

const activeKey = computed(() => {
  const p = route.path
  if (p === '/admin') return 'dashboard'
  const match = navItems.find(n => n.path !== '/admin' && p.startsWith(n.path))
  return match?.key || 'dashboard'
})

function navigateTo(path: string) {
  router.push(path)
  sidebarOpen.value = false
}
</script>

<template>
  <div>
  <!-- Admin Login Gate -->
  <div v-if="!isAuthed" class="admin-login-gate">
    <div class="login-card">
      <Lock :size="32" style="color: var(--gz-primary, #2563eb); margin-bottom: 16px;" />
      <h2 style="margin: 0 0 8px; font-size: 20px;">管理后台</h2>
      <p style="color: #6b7280; font-size: 14px; margin: 0 0 20px;">请输入管理密码</p>
      <form @submit.prevent="doAdminLogin" style="width: 100%;">
        <input
          v-model="loginPassword"
          type="password"
          placeholder="管理密码"
          class="login-input"
        />
        <p v-if="loginError" style="color: #ef4444; font-size: 13px; margin: 0 0 8px;">{{ loginError }}</p>
        <button
          type="submit"
          :disabled="loginLoading || !loginPassword"
          style="width: 100%; padding: 10px; background: #111; color: #fff; border: none; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer;"
        >{{ loginLoading ? '验证中...' : '进入后台' }}</button>
      </form>
    </div>
  </div>

  <div v-else class="admin-layout">
    <!-- Mobile Header -->
    <header class="admin-mobile-header">
      <button class="menu-toggle" @click="sidebarOpen = !sidebarOpen">
        <Menu :size="22" />
      </button>
      <div class="mobile-brand">
        <Shield :size="18" />
        <span>GZLY 管理后台</span>
      </div>
    </header>

    <!-- Sidebar Overlay (mobile) -->
    <div
      v-if="sidebarOpen"
      class="sidebar-overlay"
      @click="sidebarOpen = false"
    ></div>

    <!-- Sidebar -->
    <aside class="admin-sidebar" :class="{ open: sidebarOpen }">
      <div class="sidebar-brand">
        <div class="brand-icon">
          <Shield :size="20" />
        </div>
        <div class="brand-text">
          <span class="brand-title">GZLY</span>
          <span class="brand-sub">管理后台</span>
        </div>
        <button class="sidebar-close" @click="sidebarOpen = false">
          <X :size="18" />
        </button>
      </div>

      <nav class="sidebar-nav">
        <button
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: activeKey === item.key }"
          @click="navigateTo(item.path)"
        >
          <img :src="item.iconImg" class="nav-icon-img" alt="" />
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="sidebar-footer">
        <button class="nav-item" @click="router.push('/')">
          <LogOut :size="18" />
          <span>返回前台</span>
        </button>
        <button class="nav-item nav-item--logout" @click="doLogout">
          <LogOut :size="18" />
          <span>退出登录</span>
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="admin-main">
      <router-view />
    </main>
  </div>
  </div>
</template>

<style scoped>
.admin-login-gate {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100dvh;
  background: linear-gradient(180deg, #fbfaf7 0%, #f3efe7 100%);
}
.login-card {
  background: #fffdfa;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 20px;
  padding: 40px 32px;
  width: 360px;
  max-width: 90vw;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.06);
}
.login-input {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid #e5e7eb;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
  margin-bottom: 12px;
}
.login-input:focus {
  border-color: #2563eb;
}

.admin-layout {
  display: flex;
  min-height: 100dvh;
}

/* ---- Mobile Header ---- */
.admin-mobile-header {
  display: flex;
  align-items: center;
  gap: var(--gz-space-3);
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: var(--gz-z-sticky);
  padding: var(--gz-space-3) var(--gz-space-4);
  background: rgba(255, 253, 250, 0.94);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.menu-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: var(--gz-radius-sm);
  background: transparent;
  color: var(--gz-text-primary);
  cursor: pointer;
}

.mobile-brand {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  font-weight: 700;
  color: var(--gz-text-primary);
}

/* ---- Overlay ---- */
.sidebar-overlay {
  position: fixed;
  inset: 0;
  z-index: 199;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(2px);
}

/* ---- Sidebar ---- */
.admin-sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 260px;
  z-index: 200;
  display: flex;
  flex-direction: column;
  background: #fffdfa;
  border-right: 1px solid rgba(15, 23, 42, 0.08);
  transform: translateX(-100%);
  transition: transform var(--gz-transition-normal);
}

.admin-sidebar.open {
  transform: translateX(0);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: var(--gz-space-3);
  padding: var(--gz-space-5) var(--gz-space-4);
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.brand-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--gz-radius-md);
  background: #0f172a;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-text {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.brand-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--gz-text-primary);
  letter-spacing: 0.02em;
}

.brand-sub {
  font-size: 12px;
  color: var(--gz-text-tertiary);
}

.sidebar-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--gz-radius-sm);
  background: transparent;
  color: var(--gz-text-tertiary);
  cursor: pointer;
}

/* ---- Nav ---- */
.sidebar-nav {
  flex: 1;
  padding: var(--gz-space-3);
  overflow-y: auto;
}

.nav-icon-img {
  width: 20px;
  height: 20px;
  object-fit: contain;
  opacity: 0.7;
}

.nav-item.active .nav-icon-img {
  opacity: 1;
  filter: brightness(0) saturate(100%) invert(26%) sepia(94%) saturate(2048%) hue-rotate(216deg) brightness(96%) contrast(92%);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--gz-space-3);
  width: 100%;
  padding: 10px var(--gz-space-3);
  border: none;
  border-radius: 12px;
  background: transparent;
  color: var(--gz-text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--gz-transition-fast);
  margin-bottom: 2px;
  text-align: left;
}

.nav-item:hover {
  background: rgba(15, 23, 42, 0.04);
  color: var(--gz-text-primary);
}

.nav-item.active {
  background: #f5f7fb;
  color: #0f172a;
  font-weight: 700;
  box-shadow: inset 3px 0 0 #1d4ed8;
}

.sidebar-footer {
  padding: var(--gz-space-3);
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.nav-item--logout {
  color: var(--gz-text-tertiary);
}

.nav-item--logout:hover {
  color: var(--gz-danger);
  background: rgba(239, 68, 68, 0.06);
}

/* ---- Main Content ---- */
.admin-main {
  flex: 1;
  min-width: 0;
  padding-top: 56px; /* mobile header height */
  overflow-y: auto;
  background: linear-gradient(180deg, #fbfaf7 0%, #f5f3ee 100%);
}

/* ---- Desktop ---- */
@media (min-width: 1024px) {
  .admin-mobile-header {
    display: none;
  }

  .sidebar-overlay {
    display: none;
  }

  .admin-sidebar {
    position: sticky;
    top: 0;
    height: 100dvh;
    transform: translateX(0);
    box-shadow: 2px 0 12px rgba(0, 0, 0, 0.04);
  }

  .sidebar-close {
    display: none;
  }

  .admin-main {
    padding-top: 0;
  }
}
</style>
