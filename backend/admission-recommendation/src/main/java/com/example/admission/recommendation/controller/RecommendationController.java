package com.example.admission.recommendation.controller;

import com.example.admission.common.ApiResponse;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.example.admission.recommendation.dto.RecommendationRequest;
import com.example.admission.recommendation.dto.RecommendationResponse;
import com.example.admission.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;

/**
 * 推荐搜索控制器.
 *
 * <p>基础路径： {@code /api/v1/recommendations}</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /** Session 中存储用户ID的键名 */
    private static final String SESSION_USER_ID = "userId";

    /**
     * 推荐搜索接口.
     *
     * <p>执行三层过滤引擎（硬过滤 -> 用户筛选 -> 排序与院校分组），
     * 返回院校分组列表和推荐元数据。</p>
     *
     * <p>如果用户已登录（session 中有 userId），则关联预测结果获取概率；
     * 未登录用户也可查询，但概率字段为 null（前端展示"待计算"）。</p>
     *
     * <h3>请求示例</h3>
     * <pre>{@code
     * POST /api/v1/recommendations/search
     * Content-Type: application/json
     *
     * {
     *   "year": 2025,
     *   "educationLevel": "UNDERGRADUATE",
     *   "keyword": "",
     *   "province": ["山东"],
     *   "majorCategory": ["工学"],
     *   "tuitionMax": 20000,
     *   "minRankMax": 50000,
     *   "probabilityMin": 30,
     *   "label": "稳",
     *   "pageNo": 1,
     *   "pageSize": 20,
     *   "sortBy": "probability",
     *   "sortDir": "desc"
     * }
     * }</pre>
     *
     * @param req 请求对象uest HTTP 请求 推荐搜索请求体
     * @param session 当前 HTTP Session HTTP session（用于获取当前用户ID）
     * @return {@link ApiResponse} 包含 {@link RecommendationResponse}
     */
    @PostMapping("/search")
    public ApiResponse<RecommendationResponse> search(
            @Valid @RequestBody RecommendationRequest request,
            HttpSession session) {

        log.info("Recommendation search: year={}, edu={}, keyword={}, page={}/{}, sortBy={}",
                request.getYear(), request.getEducationLevel(),
                request.getKeyword(),
                request.getPageNo(), request.getPageSize(),
                request.getSortBy());

        // 尝试从 session 获取用户ID（未登录也可查询）
        Long userId = getUserIdFromSession(session);

        try {
            RecommendationResponse response = recommendationService.search(request, userId);
            log.info("Recommendation search completed: totalPlans={}, totalSchools={}",
                    response.getTotalPlans(), response.getTotalSchools());
            return ApiResponse.success(response);
        } catch (BusinessException e) {
            log.warn("Recommendation search failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Recommendation search error", e);
            throw new BusinessException(ErrorCode.RECOMMENDATION_FAILED, e.getMessage(), e);
        }
    }

    /**
     * 从 HTTP session 中获取当前用户ID.
     *
     * @param session 当前 HTTP Session HTTP session
     * @return 用户ID，未登录时返回 null
     */
    private Long getUserIdFromSession(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object userIdAttr = session.getAttribute(SESSION_USER_ID);
        if (userIdAttr instanceof Long) {
            return (Long) userIdAttr;
        }
        return null;
    }
}
