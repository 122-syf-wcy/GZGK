<script setup lang="ts">
import { computed } from 'vue'
import { sanitizeHttpUrl } from '@/utils/markdown'

const props = withDefaults(defineProps<{
  url?: string | null
  label?: string
  showInvalid?: boolean
}>(), {
  label: '',
  showInvalid: false,
})

const safeHref = computed(() => sanitizeHttpUrl(props.url))
</script>

<template>
  <a v-if="safeHref" :href="safeHref" target="_blank" rel="noopener noreferrer">
    <slot>{{ label || safeHref }}</slot>
  </a>
  <span v-else-if="showInvalid" aria-disabled="true">
    <slot>{{ label || '链接待核验' }}</slot>
  </span>
</template>
