import type {
  LoginRequest,
  UserDTO,
} from '@gaokao/shared-types';
import { httpClient, adminHttpClient } from '../http-client';

// User auth API (cookie-based session via HttpOnly cookie)
export const authApi = {
  register(data: LoginRequest) {
    return httpClient.post<void>('/auth/register', data);
  },

  login(data: LoginRequest) {
    return httpClient.post<void>('/auth/login', data);
  },

  logout() {
    return httpClient.post<void>('/auth/logout');
  },

  getCurrentUser() {
    return httpClient.get<UserDTO>('/auth/me');
  },
};

// Admin auth API (cookie-based session via HttpOnly cookie)
export const adminAuthApi = {
  login(data: LoginRequest) {
    return adminHttpClient.post<void>('/auth/login', data);
  },

  logout() {
    return adminHttpClient.post<void>('/auth/logout');
  },

  getCurrentUser() {
    return adminHttpClient.get<UserDTO>('/auth/me');
  },
};
