import type {
  VolunteerFormDTO,
  VolunteerFormDetailDTO,
  VolunteerChoiceDTO,
  RecommendationRequest,
  RecommendationResponseDTO,
} from '@gaokao/shared-types';
import { httpClient } from '../http-client';

export const volunteerApi = {
  // 推荐
  searchRecommendations(data: RecommendationRequest) {
    return httpClient.post<RecommendationResponseDTO>('/recommendations/search', data);
  },

  batchPredict(data: Record<string, unknown>) {
    return httpClient.post<{ results?: Array<{ planId: number; probability?: number; label?: string }> }>('/prediction/batch', data);
  },

  // 志愿表
  getForms() {
    return httpClient.get<VolunteerFormDTO[]>('/volunteer-forms');
  },

  createForm(name: string, year?: number) {
    return httpClient.post<VolunteerFormDTO>('/volunteer-forms', { name, year });
  },

  getFormDetail(formId: string) {
    return httpClient.get<VolunteerFormDetailDTO>(`/volunteer-forms/${formId}`);
  },

  updateFormName(formId: string, name: string) {
    return httpClient.patch<void>(`/volunteer-forms/${formId}/name`, { name });
  },

  copyForm(formId: string, newName: string, clientOperationId?: string) {
    return httpClient.post<VolunteerFormDTO>(`/volunteer-forms/${formId}/copy`, { newName, clientOperationId });
  },

  deleteForm(formId: string) {
    return httpClient.delete<void>(`/volunteer-forms/${formId}`);
  },

  // 志愿项
  addItem(formId: string, data: Partial<VolunteerChoiceDTO> & {
    expectedVersion: number;
    clientOperationId?: string;
  }) {
    return httpClient.post<VolunteerFormDetailDTO>(`/volunteer-forms/${formId}/items`, data);
  },

  deleteItem(formId: string, itemId: string, expectedVersion: number, clientOperationId?: string) {
    return httpClient.delete<void>(`/volunteer-forms/${formId}/items/${itemId}`, {
      params: { expectedVersion, clientOperationId },
    });
  },

  batchDeleteItems(formId: string, itemIds: string[], expectedVersion: number, clientOperationId?: string) {
    return httpClient.post<void>(`/volunteer-forms/${formId}/items/batch-delete`, {
      itemIds,
      expectedVersion,
      clientOperationId,
    });
  },

  moveItem(formId: string, itemId: string, targetPosition: number, expectedVersion: number, clientOperationId?: string) {
    return httpClient.post<void>(`/volunteer-forms/${formId}/items/${itemId}/move`, {
      targetPosition,
      expectedVersion,
      clientOperationId,
    });
  },

  updateItemNote(formId: string, itemId: string, note: string, expectedVersion: number, clientOperationId?: string) {
    return httpClient.patch<void>(`/volunteer-forms/${formId}/items/${itemId}/note`, {
      note,
      expectedVersion,
      clientOperationId,
    });
  },

  // 检查
  checkForm(formId: string) {
    return httpClient.post<void>(`/volunteer-forms/${formId}/check`);
  },

  getLatestCheckResult(formId: string) {
    return httpClient.get(`/volunteer-forms/${formId}/check/latest`);
  },

  // 导出
  exportForm(formId: string) {
    return httpClient.post<Blob>(`/volunteer-forms/${formId}/export`, undefined, {
      responseType: 'blob',
    });
  },
};
