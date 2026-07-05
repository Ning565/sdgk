import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { UserDTO, LoginRequest } from '@gaokao/shared-types';
import { adminAuthApi } from '@gaokao/api-client';

export const useAdminAuthStore = defineStore('adminAuth', () => {
  const user = ref<UserDTO | null>(null);

  const isLoggedIn = computed(() => user.value !== null);

  /**
   * Check if there is an existing session by calling GET /admin/v1/auth/me.
   * Called on app startup to restore the session from the HttpOnly cookie.
   */
  async function checkAuth(): Promise<boolean> {
    try {
      const res = await adminAuthApi.getCurrentUser();
      user.value = res.data;
      return true;
    } catch {
      user.value = null;
      return false;
    }
  }

  /**
   * Login with username/password. The backend sets the HttpOnly session cookie via Set-Cookie.
   * After login, fetch user info to populate the store.
   */
  async function login(credentials: LoginRequest) {
    await adminAuthApi.login(credentials);
    const res = await adminAuthApi.getCurrentUser();
    user.value = res.data;
  }

  /**
   * Fetch current user info from the session cookie.
   * If the session is invalid, clear local state.
   */
  async function fetchUserInfo() {
    try {
      const res = await adminAuthApi.getCurrentUser();
      user.value = res.data;
    } catch {
      logout();
    }
  }

  /**
   * Logout — calls backend to invalidate the session, then clears local state.
   */
  async function logout() {
    try {
      await adminAuthApi.logout();
    } catch {
      // Even if the logout request fails, clear local state
    }
    user.value = null;
  }

  return { user, isLoggedIn, checkAuth, login, fetchUserInfo, logout };
});
