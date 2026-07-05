package com.example.admission.prediction.controller;

import com.example.admission.common.ApiResponse;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.example.admission.prediction.dto.PredictionRequest;
import com.example.admission.prediction.dto.PredictionResponse;
import com.example.admission.prediction.service.PredictionCacheService;
import com.example.admission.prediction.service.PredictionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 预测算法控制器.
 *
 * <p>基础路径： {@code /api/v1/prediction}</p>
 * <p>需要认证（Spring Security 拦截 /api/** 路由）</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/prediction")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionService predictionService;
    private final PredictionCacheService cacheService;

    /**
     * 批量预测接口.
     *
     * <p>对一批招生计划执行冲/稳/保预测，支持缓存加速。
     * 如果考生的档案哈希和数据版本均匹配，直接返回缓存结果。</p>
     *
     * <h3>请求示例</h3>
     * <pre>{@code
     * POST /api/v1/prediction/batch
     * Content-Type: application/json
     *
     * {
     *   "candidate": {
     *     "year": 2025,
     *     "score": 620,
     *     "rank": 8500,
     *     "subjects": ["物理", "化学", "生物"]
     *   },
     *   "plans": [
     *     {
     *       "planId": 1001,
     *       "history": [
     *         { "year": 2024, "lowestRank": 7800, "planCount": 120 },
     *         { "year": 2023, "lowestRank": 8200, "planCount": 110 }
     *       ]
     *     }
     *   ]
     * }
     * }</pre>
     *
     * @param req 请求对象uest HTTP 请求 预测请求（含考生信息和计划列表及历史数据）
     * @return 每个计划的预测结果列表
     */
    @PostMapping("/batch")
    public ApiResponse<List<PredictionResponse>> batchPredict(
            @Valid @RequestBody PredictionRequest request) {

        log.info("Prediction batch request: year={}, rank={}, planCount={}",
                request.getCandidate().getYear(),
                request.getCandidate().getRank(),
                request.getPlans().size());

        try {
            PredictionRequest.CandidateInfo candidate = request.getCandidate();

            // 计算考生档案哈希
            String profileHash = PredictionService.computeProfileHash(
                    candidate.getYear(),
                    candidate.getRank(),
                    candidate.getSubjects());

            // 注意：planDataVersion 由调用方提供（从 plan 的历史数据或数据版本获取）
            // 此处使用 candidateYear 作为简单的版本标识
            // 实际生产环境中应从 DataVersionService 获取当前活跃版本
            long planDataVersion = candidate.getYear();

            List<PredictionResponse> responses = new ArrayList<>();
            List<PredictionRequest.PlanInfo> uncachedPlans = new ArrayList<>();

            for (PredictionRequest.PlanInfo plan : request.getPlans()) {
                PredictionResponse cached = cacheService.getCached(
                        profileHash, plan.getPlanId(), planDataVersion);

                if (cached != null) {
                    responses.add(cached);
                } else {
                    uncachedPlans.add(plan);
                }
            }

            // 对未缓存的计划批量执行预测
            if (!uncachedPlans.isEmpty()) {
                PredictionRequest uncachedReq = new PredictionRequest();
                uncachedReq.setCandidate(candidate);
                uncachedReq.setPlans(uncachedPlans);

                List<PredictionResponse> newResponses = predictionService.predict(uncachedReq);

                for (PredictionResponse response : newResponses) {
                    try {
                        cacheService.saveCache(profileHash, planDataVersion, response);
                    } catch (Exception e) {
                        log.warn("Failed to cache prediction for planId={}: {}",
                                response.getPlanId(), e.getMessage());
                    }
                    responses.add(response);
                }
            }

            log.info("Prediction batch completed: {} results", responses.size());
            return ApiResponse.success(responses);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Prediction batch failed", e);
            throw new BusinessException(ErrorCode.PREDICTION_FAILED, e.getMessage(), e);
        }
    }

}
