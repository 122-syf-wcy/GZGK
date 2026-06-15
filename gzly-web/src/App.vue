<script setup lang="ts">
import GlobalAnnouncement from '@/components/GlobalAnnouncement.vue'
import GlobalWarning from '@/components/GlobalWarning.vue'
import GlobalDisclaimerFooter from '@/components/GlobalDisclaimerFooter.vue'
import SiteEntryGate from '@/components/SiteEntryGate.vue'
</script>

<template>
  <div class="app-wrapper">
    <GlobalWarning />
    <GlobalAnnouncement />
    <main class="app-main">
      <router-view v-slot="{ Component }">
        <transition name="gz-fade" mode="out-in">
          <keep-alive :include="['Home']">
            <component :is="Component" />
          </keep-alive>
        </transition>
      </router-view>
    </main>
    <GlobalDisclaimerFooter />
    <SiteEntryGate />
  </div>
</template>

<style scoped>
.app-wrapper {
  min-height: 100dvh;
  position: relative;
}

.app-main {
  width: 100%;
}

/* Page transition */
.gz-fade-enter-active,
.gz-fade-leave-active {
  transition: opacity 200ms ease, transform 200ms ease;
}

.gz-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.gz-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
