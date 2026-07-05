import type {
  DataImportDTO,
  DataVersionDTO,
  DataLinkDTO,
  AuditLogDTO,
  UserDTO,
  PageResponse,
  PageRequest,
} from '@gaokao/shared-types';
import { adminHttpClient } from '../http-client';

export const adminApi = {
  // 数据导入
  createImportBatch(formData: FormData) {
    return adminHttpClient.post<DataImportDTO>('/import-batches', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  getImportBatches(params: PageRequest & { type?: string }) {
    return adminHttpClient.get<PageResponse<DataImportDTO>>('/import-batches', { params });
  },

  getImportBatch(batchId: string) {
    return adminHttpClient.get<DataImportDTO>(`/import-batches/${batchId}`);
  },

  parseImportBatch(batchId: string) {
    return adminHttpClient.post<void>(`/import-batches/${batchId}/parse`);
  },

  validateImportBatch(batchId: string) {
    return adminHttpClient.post<void>(`/import-batches/${batchId}/validate`);
  },

  publishImportBatch(batchId: string) {
    return adminHttpClient.post<void>(`/import-batches/${batchId}/publish`);
  },

  cancelImportBatch(batchId: string) {
    return adminHttpClient.post<void>(`/import-batches/${batchId}/cancel`);
  },

  exportImportErrors(batchId: string) {
    return adminHttpClient.get<Blob>(`/import-batches/${batchId}/errors/export`, {
      responseType: 'blob',
    });
  },

  // 数据版本
  getVersions(params: PageRequest) {
    return adminHttpClient.get<PageResponse<DataVersionDTO>>('/data-versions', { params });
  },

  rollbackVersion(versionId: string) {
    return adminHttpClient.post<void>(`/data-versions/${versionId}/rollback`);
  },

  // 外链管理
  getLinks() {
    return adminHttpClient.get<DataLinkDTO[]>('/links');
  },

  updateLink(id: string, data: Partial<DataLinkDTO>) {
    return adminHttpClient.put<DataLinkDTO>(`/links/${id}`, data);
  },

  // 审计日志
  getAuditLogs(params: PageRequest) {
    return adminHttpClient.get<PageResponse<AuditLogDTO>>('/audit-logs', { params });
  },

  // 用户角色管理
  getUsers(params: PageRequest) {
    return adminHttpClient.get<PageResponse<UserDTO>>('/users', { params });
  },

  updateUserRole(userId: string, role: string) {
    return adminHttpClient.put<void>(`/users/${userId}/role`, { role });
  },

  toggleUserStatus(userId: string) {
    return adminHttpClient.put<void>(`/users/${userId}/toggle-status`);
  },

  // Dashboard
  getDashboardStats() {
    return adminHttpClient.get<Record<string, number>>('/dashboard/stats');
  },
};
