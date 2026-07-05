<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { ElMessage } from 'element-plus';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const navItems = [
  { path: '/candidate', label: '考生信息' },
  { path: '/recommendations', label: '智能推荐' },
  { path: '/volunteer-forms', label: '我的志愿表' },
];

function handleLogout() {
  authStore.logout();
  ElMessage.success('已退出登录');
  router.push('/login');
}

function isActive(path: string): boolean {
  return route.path.startsWith(path);
}
</script>

<template>
  <div class="app-header">
    <el-menu
      :default-active="route.path"
      mode="horizontal"
      :ellipsis="false"
      class="app-header__menu"
    >
      <div class="app-header__brand">
        <span class="app-header__logo">G</span>
        <span class="app-header__title">高考志愿通</span>
      </div>

      <div class="app-header__nav">
        <el-menu-item
          v-for="item in navItems"
          :key="item.path"
          :index="item.path"
          @click="router.push(item.path)"
        >
          {{ item.label }}
        </el-menu-item>
      </div>

      <div class="app-header__actions">
        <template v-if="authStore.isLoggedIn">
          <el-dropdown trigger="click">
            <span class="app-header__user">
              <el-avatar :size="32" icon="UserFilled" />
              <span class="app-header__username">
                {{ authStore.user?.nickname || authStore.user?.username || '用户' }}
              </span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/candidate')">
                  个人信息
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button type="primary" size="small" @click="router.push('/login')">
            登录
          </el-button>
        </template>
      </div>
    </el-menu>
  </div>
</template>

<style scoped>
.app-header {
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.app-header__menu {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  max-width: 1200px;
  margin: 0 auto;
  border-bottom: none !important;
}
.app-header__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 32px;
  cursor: pointer;
  user-select: none;
}
.app-header__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: var(--el-color-primary);
  color: #fff;
  border-radius: 8px;
  font-weight: 700;
  font-size: 16px;
}
.app-header__title {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.app-header__nav {
  flex: 1;
  display: flex;
  border-bottom: none !important;
}
.app-header__nav .el-menu-item {
  border-bottom: none !important;
}
.app-header__actions {
  display: flex;
  align-items: center;
}
.app-header__user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.2s;
}
.app-header__user:hover {
  background: var(--el-fill-color-light);
}
.app-header__username {
  font-size: 14px;
  color: var(--el-text-color-regular);
}
</style>
