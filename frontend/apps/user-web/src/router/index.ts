import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginPage.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/candidate',
    children: [
      {
        path: 'candidate',
        name: 'Candidate',
        component: () => import('@/views/CandidatePage.vue'),
        meta: { title: '考生信息', requiresAuth: true },
      },
      {
        path: 'recommendations',
        name: 'Recommendations',
        component: () => import('@/views/RecommendationsPage.vue'),
        meta: { title: '推荐列表', requiresAuth: true },
      },
      {
        path: 'schools/:schoolId',
        name: 'SchoolDetail',
        component: () => import('@/views/SchoolDetailPage.vue'),
        meta: { title: '院校详情', requiresAuth: true },
      },
      {
        path: 'plans/:planId',
        name: 'PlanDetail',
        component: () => import('@/views/PlanDetailPage.vue'),
        meta: { title: '专业详情', requiresAuth: true },
      },
      {
        path: 'volunteer-forms',
        name: 'VolunteerForms',
        component: () => import('@/views/VolunteerFormListPage.vue'),
        meta: { title: '志愿表列表', requiresAuth: true },
      },
      {
        path: 'volunteer-forms/:formId',
        name: 'VolunteerFormDetail',
        component: () => import('@/views/VolunteerFormDetailPage.vue'),
        meta: { title: '志愿表详情', requiresAuth: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundPage.vue'),
    meta: { title: '404' },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
});

router.beforeEach(async (to, _from, next) => {
  document.title = `${to.meta.title || '高考志愿通'} - 高考志愿通`;

  if (to.meta.requiresAuth) {
    const authStore = useAuthStore();
    // Only call checkAuth if we don't have user info yet (avoids redundant API calls)
    if (!authStore.user) {
      await authStore.checkAuth();
    }
    if (!authStore.isLoggedIn) {
      next({ name: 'Login', query: { redirect: to.fullPath } });
      return;
    }
  }
  next();
});

export default router;
