import type { SchoolDTO, SchoolDetailDTO, AdmissionPlanDTO, PageResponse } from '@gaokao/shared-types';
import { httpClient } from '../http-client';

export const schoolApi = {
  getList(params: Record<string, unknown>) {
    return httpClient.get<PageResponse<SchoolDTO>>('/schools', { params });
  },

  getDetail(schoolId: string) {
    return httpClient.get<SchoolDetailDTO>(`/schools/${schoolId}`);
  },

  getPlans(schoolId: string) {
    return httpClient.get<AdmissionPlanDTO[]>(`/schools/${schoolId}/plans`);
  },

  getPlanDetail(planId: string) {
    return httpClient.get<AdmissionPlanDTO>(`/plans/${planId}`);
  },
};
