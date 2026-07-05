<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAdminAuthStore } from '@/stores/auth';
import { ElMessage } from 'element-plus';

const router = useRouter();
const route = useRoute();
const authStore = useAdminAuthStore();
const isCollapsed = ref(false);

const menuItems = [
  {
    index: '/admin/dashboard',
    title: '数据总览',
    icon: 'DataAnalysis',
  },
  {
    index: '/admin/imports',
    title: '数据导入',
    icon: 'Upload',
    children: [
      { index: '/admin/imports/score-rank', title: '一分一段导入' },
      { index: '/admin/imports/plans', title: '招生计划导入' },
      { index: '/admin/imports/history', title: '历史数据导入' },
    ],
  },
  {
    index: '/admin/links',
    title: '外链管理',
    icon: 'Link',
  },
  {
    index: '/admin/data-versions',
    title: '数据版本',
    icon: 'Collection',
  },
  {
    index: '/admin/audit-logs',
    title: '审计日志',
    icon: 'Document',
  },
  {
    index: '/admin/users-roles',
    title: '用户角色',
    icon: 'User',
  },
];

const activeMenu = computed(() => {
  const path = route.path;
  if (path.startsWith('/admin/imports')) return '/admin/imports';
  return path;
});

function handleMenuClick(index: string) {
  router.push(index);
}

function handleLogout() {
  authStore.logout();
  ElMessage.success('已退出登录');
  router.push('/admin/login');
}
</script>

<template>
  <el-container class="admin-layout">
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="admin-layout__aside">
      <div class="admin-sidebar">
        <div class="admin-sidebar__brand" @click="router.push('/admin/dashboard')">
          <span class="admin-sidebar__logo">G</span>
          <span v-show="!isCollapsed" class="admin-sidebar__title">高考志愿通</span>
        </div>

        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapsed"
          :collapse-transition="false"
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409eff"
          @select="handleMenuClick"
        >
          <template v-for="item in menuItems" :key="item.index">
            <el-sub-menu v-if="item.children" :index="item.index">
              <template #title>
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.title }}</span>
              </template>
              <el-menu-item
                v-for="child in item.children"
                :key="child.index"
                :index="child.index"
              >
                {{ child.title }}
              </el-menu-item>
            </el-sub-menu>

            <el-menu-item v-else :index="item.index">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </div>
    </el-aside>

    <el-container>
      <el-header class="admin-layout__header">
        <div class="admin-header">
          <div class="admin-header__left">
            <el-button
              type="default"
              :icon="isCollapsed ? 'Expand' : 'Fold'"
              @click="isCollapsed = !isCollapsed"
            />
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
              <el-breadcrumb-item v-if="route.meta.title">
                {{ route.meta.title }}
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>

          <div class="admin-header__right">
            <el-dropdown trigger="click">
              <span class="admin-header__user">
                <el-avatar :size="32" icon="UserFilled" />
                <span>{{ authStore.user?.nickname || '管理员' }}</span>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="router.push('/admin/users-roles')">
                    个人信息
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout">
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-header>

      <el-main class="admin-layout__main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
}
.admin-layout__aside {
  background-color: #304156;
  overflow: hidden;
  transition: width 0.3s;
}
.admin-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.admin-sidebar__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px;
  cursor: pointer;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}
.admin-sidebar__logo {
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
  flex-shrink: 0;
}
.admin-sidebar__title {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
}
.admin-layout__header {
  padding: 0;
  height: 56px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 0 20px;
}
.admin-header__left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.admin-header__right {
  display: flex;
  align-items: center;
}
.admin-header__user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.admin-layout__main {
  background: var(--el-bg-color-page);
  padding: 20px;
}
</style>
