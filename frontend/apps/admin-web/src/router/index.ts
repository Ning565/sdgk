import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAdminAuthStore } from '@/stores/auth';

const routes: RouteRecordRaw[] = [
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('@/views/AdminLoginPage.vue'),
    meta: { title: '后台登录', requiresAuth: false },
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/DashboardPage.vue'),
        meta: { title: '数据总览', requiresAuth: true },
      },
      {
        path: 'imports/score-rank',
        name: 'ScoreRankImport',
        component: () => import('@/views/ScoreRankImportPage.vue'),
        meta: { title: '一分一段导入', requiresAuth: true },
      },
      {
        path: 'imports/plans',
        name: 'PlansImport',
        component: () => import('@/views/PlansImportPage.vue'),
        meta: { title: '招生计划导入', requiresAuth: true },
      },
      {
        path: 'imports/history',
        name: 'HistoryImport',
        component: () => import('@/views/HistoryImportPage.vue'),
        meta: { title: '历史数据导入', requiresAuth: true },
      },
      {
        path: 'imports/:batchId',
        name: 'ImportBatchDetail',
        component: () => import('@/views/ImportBatchDetailPage.vue'),
        meta: { title: '导入批次详情', requiresAuth: true },
      },
      {
        path: 'links',
        name: 'AdminLinks',
        component: () => import('@/views/LinksPage.vue'),
        meta: { title: '外链管理', requiresAuth: true },
      },
      {
        path: 'data-versions',
        name: 'DataVersions',
        component: () => import('@/views/DataVersionsPage.vue'),
        meta: { title: '数据版本', requiresAuth: true },
      },
      {
        path: 'audit-logs',
        name: 'AuditLogs',
        component: () => import('@/views/AuditLogsPage.vue'),
        meta: { title: '审计日志', requiresAuth: true },
      },
      {
        path: 'users-roles',
        name: 'UsersRoles',
        component: () => import('@/views/UsersRolesPage.vue'),
        meta: { title: '用户角色', requiresAuth: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/admin/dashboard',
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to, _from, next) => {
  document.title = `${to.meta.title || '管理后台'} - 高考志愿通`;

  if (to.meta.requiresAuth) {
    const authStore = useAdminAuthStore();
    // Only call checkAuth if we don't have user info yet (avoids redundant API calls)
    if (!authStore.user) {
      await authStore.checkAuth();
    }
    if (!authStore.isLoggedIn) {
      next({ name: 'AdminLogin', query: { redirect: to.fullPath } });
      return;
    }
  }
  next();
});

export default router;
