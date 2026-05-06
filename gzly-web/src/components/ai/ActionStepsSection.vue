<template>
  <section id="action-list" class="action-card gz-card">
    <div class="action-card__head">
      <ListChecks :size="18" />
      <h3>先做这几步</h3>
    </div>
    <div class="action-list">
      <div
        v-for="action in actions"
        :key="action.title"
        class="action-item"
        :class="{ 'action-item--warn': action.tone === 'warn' }"
      >
        <strong>{{ action.title }}</strong>
        <span>{{ action.desc }}</span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ListChecks } from 'lucide-vue-next'

defineProps<{
  actions: Array<{ title: string; desc: string; tone?: 'warn' | 'normal' }>
}>()
</script>
