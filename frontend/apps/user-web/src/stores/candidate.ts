import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { CandidateProfile } from '@gaokao/shared-types';
import { candidateApi } from '@gaokao/api-client';

export const useCandidateStore = defineStore('candidate', () => {
  const candidateProfile = ref<CandidateProfile | null>(null);
  const loading = ref(false);
  let activeRequestId = 0;
  let pendingRequests = 0;

  function beginRequest() {
    activeRequestId += 1;
    pendingRequests += 1;
    loading.value = true;
    return activeRequestId;
  }

  function endRequest() {
    pendingRequests = Math.max(0, pendingRequests - 1);
    loading.value = pendingRequests > 0;
  }

  async function fetchProfile(year: number) {
    const requestId = beginRequest();
    try {
      const res = await candidateApi.getProfile(year);
      if (requestId === activeRequestId) {
        candidateProfile.value = res.data;
      }
      return res.data;
    } finally {
      endRequest();
    }
  }

  async function updateProfile(year: number, data: Partial<CandidateProfile>) {
    const requestId = beginRequest();
    try {
      const res = await candidateApi.updateProfile(year, data);
      if (requestId === activeRequestId) {
        candidateProfile.value = res.data;
      }
      return res.data;
    } finally {
      endRequest();
    }
  }

  async function resolveScoreRank(year: number, score: number) {
    return candidateApi.resolveScoreRank(year, score);
  }

  return { candidateProfile, loading, fetchProfile, updateProfile, resolveScoreRank };
});
