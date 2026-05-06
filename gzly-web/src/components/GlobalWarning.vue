<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { AlertTriangle } from 'lucide-vue-next'
import { getProvinceConfig } from '@/constants/provinces'

const route = useRoute()

const rawProvinceCode = computed(() => route.params.provinceCode ?? route.query.provinceCode)
const warningText = computed(() => {
  if (!rawProvinceCode.value) {
    return '数据与建议仅供参考，请以对应省级考试院和高校官方信息为准。'
  }
  const province = getProvinceConfig(rawProvinceCode.value)
  return `数据与建议仅供参考，请以${province.officialSource}和高校官方信息为准。`
})
</script>

<template>
  <div class="global-warning-bar">
    <AlertTriangle :size="15" :stroke-width="2.5" />
    <span>{{ warningText }}</span>
  </div>
</template>
