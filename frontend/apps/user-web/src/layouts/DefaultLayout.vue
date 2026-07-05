<script setup lang="ts">
import { useRouter } from 'vue-router';
import AppHeader from '@/components/AppHeader.vue';
import AppFooter from '@/components/AppFooter.vue';

const router = useRouter();
</script>

<template>
  <el-container class="app-layout">
    <el-header class="app-layout__header">
      <AppHeader />
    </el-header>
    <el-main class="app-layout__main">
      <div class="app-layout__content">
        <router-view />
      </div>
    </el-main>
    <el-footer class="app-layout__footer">
      <AppFooter />
    </el-footer>
  </el-container>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--el-bg-color-page);
}

.app-layout__header {
  padding: 0;
  height: auto;
  position: sticky;
  top: 0;
  z-index: 100;
}

.app-layout__main {
  flex: 1;
  display: flex;
  justify-content: center;
  padding: 24px;
}

.app-layout__content {
  width: 100%;
  max-width: 1200px;
}

.app-layout__footer {
  padding: 0;
  height: auto;
}
</style>
