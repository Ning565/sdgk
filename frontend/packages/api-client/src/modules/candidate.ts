import type { CandidateProfile } from '@gaokao/shared-types';
import { httpClient } from '../http-client';

export const candidateApi = {
  getProfile(year: number) {
    return httpClient.get<CandidateProfile>(`/candidate-profiles/${year}`);
  },

  updateProfile(year: number, data: Partial<CandidateProfile>) {
    return httpClient.put<CandidateProfile>(`/candidate-profiles/${year}`, data);
  },

  resolveScoreRank(year: number, score: number) {
    return httpClient.get<{ year: number; score: number; cumulativeCount: number; dataVersionId: number; dataVersionName: string }>('/score-ranks/resolve', { params: { year, score } });
  },
};
