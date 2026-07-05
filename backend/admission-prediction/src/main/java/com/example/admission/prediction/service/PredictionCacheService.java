package com.example.admission.prediction.service;

import com.example.admission.prediction.dto.PredictionResponse;
import com.example.admission.prediction.entity.PredictionResult;
import com.example.admission.prediction.mapper.PredictionResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 预测结果缓存服务.
 *
 * <p>缓存键: profileHash + planId + planDataVersion + modelVersion.
 * 缓存命中时直接返回，未命中时计算后存入 prediction_result 表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionCacheService {

    private final PredictionResultMapper predictionResultMapper;

    private static final String MODEL_VERSION = "simple-v1.0";

    /**
     * 从缓存中查找已有的预测结果.
     *
     * @param profileHash    考生档案哈希
     * @param planId 招生计划 ID         计划ID
     * @param planDataVersion 数据版本ID
     * @return 缓存命中时返回预测响应，否则返回 null
     */
    public PredictionResponse getCached(String profileHash, Long planId, Long planDataVersion) {
        PredictionResult cached = predictionResultMapper.findCached(
                profileHash, planId, planDataVersion, MODEL_VERSION);
        if (cached == null) {
            return null;
        }
        log.debug("Cache hit: profileHash={}, planId={}", profileHash, planId);

        List<String> reasons = cached.getReasonText() != null
                ? Arrays.asList(cached.getReasonText().split("\n"))
                : List.of();

        return PredictionResponse.builder()
                .planId(cached.getPlanId())
                .probability(cached.getProbability())
                .label(cached.getLabel())
                .predictedRankMin(cached.getPredictedRankMin())
                .predictedRankMax(cached.getPredictedRankMax())
                .confidence(reasons.size() >= 3 ? "高" : reasons.size() >= 2 ? "中" : "低")
                .reasons(reasons)
                .modelVersion(cached.getModelVersion())
                .build();
    }

    /**
     * 将预测结果存入缓存.
     *
     * @param profileHash     考生档案哈希
     * @param planDataVersion 数据版本ID
     * @param response        预测响应
     */
    @Transactional
    public void saveCache(String profileHash, Long planDataVersion, PredictionResponse response) {
        PredictionResult entity = new PredictionResult();
        entity.setProfileHash(profileHash);
        entity.setPlanId(response.getPlanId());
        entity.setPlanDataVersion(planDataVersion);
        entity.setModelVersion(MODEL_VERSION);
        entity.setProbability(response.getProbability());
        entity.setPredictedRankMin(response.getPredictedRankMin());
        entity.setPredictedRankMax(response.getPredictedRankMax());
        entity.setLabel(response.getLabel());

        if (response.getReasons() != null && !response.getReasons().isEmpty()) {
            entity.setReasonText(String.join("\n", response.getReasons()));
            entity.setReasonCode(response.getLabel());
        }

        entity.setIsValid(1);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        predictionResultMapper.insert(entity);
        log.debug("Prediction cached: profileHash={}, planId={}, label={}",
                profileHash, response.getPlanId(), response.getLabel());
    }

    /**
     * 使指定计划的缓存失效.
     *
     * @param planId 招生计划 ID 计划ID
     */
    @Transactional
    public void invalidateByPlanId(Long planId) {
        log.info("Invalidating prediction cache for planId={}", planId);
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PredictionResult> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        wrapper.eq(PredictionResult::getPlanId, planId)
               .set(PredictionResult::getIsValid, 0);
        predictionResultMapper.update(null, wrapper);
    }
}
