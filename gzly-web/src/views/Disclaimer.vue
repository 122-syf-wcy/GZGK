<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowLeft, Clock, ShieldCheck } from 'lucide-vue-next'
import {
  AI_GENERATED_NOTICE,
  COMPLIANCE_SECTIONS,
  DISCLAIMER_UPDATED_AT,
  DISCLAIMER_VERSION,
} from '@/constants/compliance'

const router = useRouter()
</script>

<template>
  <div class="disclaimer-page">
    <header class="page-header">
      <div class="page-header-inner">
        <button class="back-btn" @click="router.back()">
          <ArrowLeft :size="20" />
        </button>
        <h1 class="page-header-title">免责声明</h1>
      </div>
    </header>

    <div class="content-layout">
      <div class="disclaimer-card gz-card">
        <div class="card-top">
          <div class="card-icon">
            <ShieldCheck :size="22" />
          </div>
          <h2 class="card-title">公益志愿填报辅助告知</h2>
          <p class="card-subtitle">版本 {{ DISCLAIMER_VERSION }} · {{ AI_GENERATED_NOTICE }}</p>
        </div>

        <div class="body">
          <section v-for="section in COMPLIANCE_SECTIONS" :key="section.title" class="clause">
            <h3>{{ section.title }}</h3>
            <p v-for="paragraph in section.paragraphs" :key="paragraph">{{ paragraph }}</p>
            <ul v-if="section.bullets?.length">
              <li v-for="bullet in section.bullets" :key="bullet">{{ bullet }}</li>
            </ul>
          </section>
        </div>

        <div class="update-time">
          <Clock :size="12" />
          <span>最后更新时间：{{ DISCLAIMER_UPDATED_AT }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.disclaimer-page {
  min-height: 100dvh;
}

.page-header {
  position: sticky;
  top: 0;
  z-index: var(--gz-z-sticky);
  background: var(--gz-glass-bg);
  backdrop-filter: blur(var(--gz-glass-blur));
  -webkit-backdrop-filter: blur(var(--gz-glass-blur));
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.page-header-inner {
  display: flex;
  align-items: center;
  gap: var(--gz-space-3);
  max-width: var(--gz-desktop-max);
  margin: 0 auto;
  padding: var(--gz-space-3) var(--gz-space-4);
}

.back-btn {
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
  transition: background var(--gz-transition-fast);
}

.back-btn:hover {
  background: rgba(0, 0, 0, 0.05);
}

.page-header-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--gz-text-primary);
}

.content-layout {
  max-width: 760px;
  margin: 0 auto;
  padding: var(--gz-space-4);
}

.disclaimer-card {
  padding: var(--gz-space-6);
}

.card-top {
  text-align: center;
  margin-bottom: var(--gz-space-6);
}

.card-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--gz-radius-lg);
  background: #0f172a;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--gz-space-3);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.18);
}

.card-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--gz-text-primary);
  margin-bottom: 4px;
}

.card-subtitle {
  font-size: 13px;
  line-height: 1.6;
  color: var(--gz-text-tertiary);
}

.body {
  font-size: 14px;
  line-height: 1.8;
  color: var(--gz-text-secondary);
}

.clause {
  margin-bottom: var(--gz-space-4);
  padding: var(--gz-space-4);
  background: rgba(15, 23, 42, 0.025);
  border-radius: var(--gz-radius-md);
  border-left: 3px solid #0f172a;
}

.clause h3 {
  font-size: 15px;
  font-weight: 700;
  color: var(--gz-text-primary);
  margin-bottom: 6px;
}

.clause p {
  margin-bottom: 6px;
}

.clause ul {
  padding-left: var(--gz-space-5);
  margin: 6px 0;
}

.clause li {
  margin-bottom: 4px;
}

.clause li::marker {
  color: #0f172a;
}

.update-time {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  color: var(--gz-text-tertiary);
  margin-top: var(--gz-space-5);
  padding-top: var(--gz-space-4);
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}

@media (min-width: 768px) {
  .content-layout {
    padding: var(--gz-space-8);
  }

  .disclaimer-card {
    padding: var(--gz-space-8);
  }
}
</style>
