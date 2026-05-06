<template>
  <section id="zxf-skills" class="zxf-skills-card gz-card">
    <header class="zxf-skills-card__head">
      <div>
        <span class="zxf-skills-card__eyebrow">GitHub skills · 当前方案上下文</span>
        <h3>张雪峰.skills 填报服务</h3>
        <p>先读取公开 skills 策略，再结合上方 AI 分析、志愿清单、位次、扩招指数和院校招生指数回答。</p>
      </div>
      <button class="zxf-skills-card__source" type="button" @click="$emit('show-sources')">
        <ExternalLink :size="14" />
        来源
      </button>
    </header>

    <slot />
    <p class="zxf-skills-card__note">该服务只作公开策略参考，不代表张雪峰本人或任何机构意见，不构成录取承诺。</p>
  </section>
</template>

<script setup lang="ts">
import { ExternalLink } from 'lucide-vue-next'

defineEmits<{ 'show-sources': [] }>()
</script>

<style scoped>
.zxf-skills-card {
  margin-top: 12px;
  padding: 18px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.1);
  box-shadow: 0 10px 28px rgba(31, 41, 51, 0.06);
}

.zxf-skills-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.zxf-skills-card__eyebrow {
  display: block;
  font-size: 12px;
  font-weight: 900;
  color: #5f4630;
}

.zxf-skills-card :deep(h3) {
  margin-top: 4px;
  font-size: 20px;
  line-height: 1.25;
  font-weight: 950;
  color: #1f2933;
}

.zxf-skills-card__head p,
.zxf-skills-card__note {
  margin-top: 7px;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.zxf-skills-card__source {
  min-height: 36px;
  padding: 0 11px;
  border-radius: 999px;
  border: 1px solid rgba(31, 41, 51, 0.12);
  background: #f7f2e8;
  color: #1f2933;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 900;
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
}

@media (max-width: 640px) {
  .zxf-skills-card {
    border-radius: 12px;
    padding: 14px;
  }

  .zxf-skills-card__head {
    flex-direction: column;
  }

  .zxf-skills-card__source {
    align-self: flex-start;
  }
}
</style>
