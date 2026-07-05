import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { UserDTO, LoginRequest } from '@gaokao/shared-types';
import { authApi } from '@gaokao/api-client';

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserDTO | null>(null);

  const isLoggedIn = computed(() => user.value !== null);
  const userRole = computed(() => user.value?.status || null);

  /**
   * Check if there is an existing session by calling GET /v1/auth/me.
   * Called on app startup to restore the session from the HttpOnly cookie.
   */
  async function checkAuth(): Promise<boolean> {
    try {
      const res = await authApi.getCurrentUser();
      user.value = res.data;
      return true;
    } catch {
      user.value = null;
      return false;
    }
  }

  /**
   * Register new account. Backend sets HttpOnly session cookie.
   */
  async function register(credentials: LoginRequest) {
    await authApi.register(credentials);
    const res = await authApi.getCurrentUser();
    user.value = res.data;
  }

  /**
   * Login via username+password. Backend sets HttpOnly session cookie.
   */
  async function login(credentials: LoginRequest) {
    await authApi.login(credentials);
    const res = await authApi.getCurrentUser();
    user.value = res.data;
  }

  /**
   * Fetch current user info from the session cookie.
   * If the session is invalid, clear local state.
   */
  async function fetchUserInfo() {
    try {
      const res = await authApi.getCurrentUser();
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
      await authApi.logout();
    } catch {
      // Even if the logout request fails, clear local state
    }
    user.value = null;
  }

  return { user, isLoggedIn, userRole, checkAuth, register, login, fetchUserInfo, logout };
});
