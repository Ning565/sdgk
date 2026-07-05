import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { CandidateProfile } from '@gaokao/shared-types';
import { candidateApi } from '@gaokao/api-client';

export const useCandidateStore = defineStore('candidate', () => {
  const candidateProfile = ref<CandidateProfile | null>(null);
  const loading = ref(false);

  async function fetchProfile(year: number) {
    loading.value = true;
    try {
      const res = await candidateApi.getProfile(year);
      candidateProfile.value = res.data;
    } finally {
      loading.value = false;
    }
  }

  async function updateProfile(year: number, data: Partial<CandidateProfile>) {
    loading.value = true;
    try {
      const res = await candidateApi.updateProfile(year, data);
      candidateProfile.value = res.data;
    } finally {
      loading.value = false;
    }
  }

  async function resolveScoreRank(year: number, score: number) {
    return candidateApi.resolveScoreRank(year, score);
  }

  return { candidateProfile, loading, fetchProfile, updateProfile, resolveScoreRank };
});
