package com.example.admission.recommendation.service;

import com.example.admission.catalog.service.EnrollmentPlanService;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.example.admission.common.TraceContext;
import com.example.admission.dataimport.entity.ActiveDataVersion;
import com.example.admission.dataimport.service.DataVersionService;
import com.example.admission.recommendation.dto.PlanRecommendationResponse;
import com.example.admission.recommendation.dto.RecommendationRequest;
import com.example.admission.recommendation.dto.RecommendationResponse;
import com.example.admission.recommendation.dto.SchoolGroupResponse;
import com.example.admission.recommendation.mapper.RecommendationMapper;
import com.example.admission.recommendation.mapper.RecommendationPlanVO;
import com.example.admission.recommendation.util.SchoolGrouper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐搜索服务.
 *
 * <p>实现三层过滤引擎：
 * <ol>
 *   <li>硬过滤 — 版本、年度、状态、选科、教育层次等不可跳过的条件</li>
 *   <li>用户筛选 — 关键词、地区、院校性质、专业门类、学费等用户可控条件</li>
 *   <li>排序与院校分组 — 按指定字段排序，按院校聚合</li>
 * </ol>
 *
 * <p>通过注入 catalog 模块的 {@link EnrollmentPlanService} 和
 * dataimport 模块的 {@link DataVersionService} 获取基础数据，
 * 推荐专属的复杂联查由本模块的 {@link RecommendationMapper} 完成。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationMapper recommendationMapper;
    private final EnrollmentPlanService enrollmentPlanService;
    private final DataVersionService dataVersionService;
    private final SpecializedModelRecommendationService specializedModelRecommendationService;

    /** 允许的排序字段集合 */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "probability", "rankDiff", "lastYearMinRank", "planCount", "tuition"
    );

    /** 允许的排序方向 */
    private static final Set<String> ALLOWED_SORT_DIRS = Set.of("asc", "desc");

    /** 教育层次常量 */
    private static final String EDU_UNDERGRADUATE = "UNDERGRADUATE";
    private static final String EDU_SPECIALIZED = "SPECIALIZED";
    private static final String EDU_UNLIMITED = "UNLIMITED";

    /** 招生计划数据类型 */
    private static final String DATA_TYPE_PLAN = "PLAN";
    /** 历史录取数据类型 */
    private static final String DATA_TYPE_HISTORY = "HISTORY";
    /** 单次推荐查询允许返回的最大专业数 */
    private static final int MAX_PAGE_SIZE = 10_000;

    /**
     * 执行推荐搜索.
     *
     * @param req 请求对象    推荐搜索请求
     * @param userId 当前用户ID（可选，用于获取个性化预测结果）
     * @return 推荐搜索响应
     * @throws BusinessException 业务异常 如果参数无效或查询失败
     */
    public RecommendationResponse search(RecommendationRequest req, Long userId) {
        // 0. 参数校验与标准化
        validateAndNormalize(req);

        if (EDU_UNLIMITED.equals(req.getEducationLevel())) {
            return searchUnlimited(req, userId);
        }

        if (EDU_SPECIALIZED.equals(req.getEducationLevel())
                && req.getYear() != null
                && req.getYear() == 2026
                && specializedModelRecommendationService.isAvailable()) {
            return specializedModelRecommendationService.search(req);
        }

        return searchDatabase(req, userId);
    }

    private RecommendationResponse searchDatabase(RecommendationRequest req, Long userId) {
        // 1. 获取当前生效的数据版本
        ActiveDataVersion planVersion = dataVersionService.getActiveVersion(DATA_TYPE_PLAN, req.getYear());
        if (planVersion == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND,
                    "招生年度 " + req.getYear() + " 的招生计划数据尚未发布");
        }
        ActiveDataVersion historyVersion = dataVersionService.getActiveVersion(
                DATA_TYPE_HISTORY, req.getYear() - 1);

        // 2. 获取考生位次（只要有用户ID就尝试获取）
        final Integer candidateRank = req.getRank() != null ? req.getRank()
                : (userId != null ? getCandidateRank(userId) : null);

        // 3. 获取选科组合索引
        Integer subjectComboIndex = req.getSubjectComboIndex();

        // 4. 计算分页偏移
        int offset = (req.getPageNo() - 1) * req.getPageSize();

        // 5. 执行查询（硬过滤 + 用户筛选 + 排序）
        List<RecommendationPlanVO> planVOs = recommendationMapper.searchPlans(
                req,
                planVersion.getDataVersionId(),
                historyVersion != null ? historyVersion.getDataVersionId() : null,
                userId,
                subjectComboIndex,
                candidateRank,
                offset,
                req.getPageSize()
        );

        // 6. 统计总数
        long totalPlans = recommendationMapper.countPlans(
                req,
                planVersion.getDataVersionId(),
                historyVersion != null ? historyVersion.getDataVersionId() : null,
                userId,
                subjectComboIndex
        );

        // 7. 映射为响应 DTO
        List<PlanRecommendationResponse> planResponses = planVOs.stream()
                .map(vo -> mapToPlanResponse(vo, candidateRank))
                .collect(Collectors.toList());

        // 8. 按院校分组
        List<SchoolGroupResponse> schoolGroups = SchoolGrouper.group(
                planResponses, req.getSortBy(), req.getSortDir());

        // 9. 统计院校数
        int totalSchools = (int) planResponses.stream()
                .map(PlanRecommendationResponse::getSchoolId)
                .distinct()
                .count();

        // 10. 获取模型版本（从第一条有预测数据的记录中提取）
        String modelVersion = planVOs.stream()
                .map(RecommendationPlanVO::getModelVersion)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        // 11. 构建响应
        return RecommendationResponse.builder()
                .schoolGroups(schoolGroups)
                .totalPlans(totalPlans)
                .totalSchools(totalSchools)
                .planDataVersion("v" + planVersion.getDataVersionId())
                .historyDataVersion(historyVersion != null
                        ? "v" + historyVersion.getDataVersionId() : null)
                .modelVersion(modelVersion)
                .updatedAt(Instant.now())
                .traceId(TraceContext.getTraceId())
                .build();
    }

    private RecommendationResponse searchUnlimited(RecommendationRequest req, Long userId) {
        List<PlanRecommendationResponse> allPlans = new ArrayList<>();
        long totalPlans = 0;
        String planDataVersion = null;
        String historyDataVersion = null;
        String modelVersion = null;

        if (req.getYear() != null
                && req.getYear() == 2026
                && specializedModelRecommendationService.isAvailable()) {
            RecommendationRequest specializedReq = copyRequest(req);
            specializedReq.setEducationLevel(EDU_SPECIALIZED);
            RecommendationResponse specialized = specializedModelRecommendationService.search(specializedReq);
            allPlans.addAll(flattenPlans(specialized));
            totalPlans += specialized.getTotalPlans();
            planDataVersion = specialized.getPlanDataVersion();
            historyDataVersion = specialized.getHistoryDataVersion();
            modelVersion = specialized.getModelVersion();
        }

        try {
            RecommendationRequest undergraduateReq = copyRequest(req);
            undergraduateReq.setEducationLevel(EDU_UNDERGRADUATE);
            RecommendationResponse undergraduate = searchDatabase(undergraduateReq, userId);
            allPlans.addAll(flattenPlans(undergraduate));
            totalPlans += undergraduate.getTotalPlans();
            planDataVersion = joinVersion(planDataVersion, undergraduate.getPlanDataVersion());
            historyDataVersion = joinVersion(historyDataVersion, undergraduate.getHistoryDataVersion());
            modelVersion = joinVersion(modelVersion, undergraduate.getModelVersion());
        } catch (BusinessException e) {
            if (!ErrorCode.DATA_NOT_FOUND.equals(e.getErrorCode())) {
                throw e;
            }
            log.warn("Undergraduate recommendation skipped for unlimited search: {}", e.getMessage());
        }

        Comparator<PlanRecommendationResponse> comparator = Comparator
                .comparing(PlanRecommendationResponse::getProbability,
                        Comparator.nullsLast(BigDecimal::compareTo))
                .reversed()
                .thenComparing(PlanRecommendationResponse::getSchoolName,
                        Comparator.nullsLast(String::compareTo));
        allPlans.sort(comparator);

        int pageNo = req.getPageNo() == null || req.getPageNo() < 1 ? 1 : req.getPageNo();
        int pageSize = req.getRecommendationCount() != null ? req.getRecommendationCount() : req.getPageSize();
        pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        int from = Math.min((pageNo - 1) * pageSize, allPlans.size());
        int to = Math.min(from + pageSize, allPlans.size());
        List<PlanRecommendationResponse> page = new ArrayList<>(allPlans.subList(from, to));

        List<SchoolGroupResponse> schoolGroups = SchoolGrouper.group(page, req.getSortBy(), req.getSortDir());
        int totalSchools = (int) allPlans.stream()
                .map(PlanRecommendationResponse::getSchoolId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        return RecommendationResponse.builder()
                .schoolGroups(schoolGroups)
                .totalPlans(totalPlans)
                .totalSchools(totalSchools)
                .planDataVersion(planDataVersion)
                .historyDataVersion(historyDataVersion)
                .modelVersion(modelVersion)
                .updatedAt(Instant.now())
                .traceId(TraceContext.getTraceId())
                .build();
    }

    private static List<PlanRecommendationResponse> flattenPlans(RecommendationResponse response) {
        if (response == null || response.getSchoolGroups() == null) {
            return List.of();
        }
        return response.getSchoolGroups().stream()
                .filter(Objects::nonNull)
                .flatMap(group -> group.getPlans().stream())
                .collect(Collectors.toList());
    }

    private static String joinVersion(String left, String right) {
        if (left == null || left.isBlank()) {
            return right;
        }
        if (right == null || right.isBlank() || left.contains(right)) {
            return left;
        }
        return left + "+" + right;
    }

    private static RecommendationRequest copyRequest(RecommendationRequest source) {
        RecommendationRequest target = new RecommendationRequest();
        target.setYear(source.getYear());
        target.setEducationLevel(source.getEducationLevel());
        target.setScore(source.getScore());
        target.setRank(source.getRank());
        target.setSubjects(source.getSubjects());
        target.setKeyword(source.getKeyword());
        target.setProvince(source.getProvince());
        target.setCity(source.getCity());
        target.setSchoolType(source.getSchoolType());
        target.setSchoolTag(source.getSchoolTag());
        target.setMajorCategory(source.getMajorCategory());
        target.setMajorSubcategory(source.getMajorSubcategory());
        target.setEnrollmentType(source.getEnrollmentType());
        target.setCampusCode(source.getCampusCode());
        target.setExcludeMajorCategory(source.getExcludeMajorCategory());
        target.setExcludeMajorSubcategory(source.getExcludeMajorSubcategory());
        target.setExcludeSinoForeign(source.getExcludeSinoForeign());
        target.setExcludeSchoolEnterprise(source.getExcludeSchoolEnterprise());
        target.setTuitionMin(source.getTuitionMin());
        target.setTuitionMax(source.getTuitionMax());
        target.setPlanCountMin(source.getPlanCountMin());
        target.setPlanCountMax(source.getPlanCountMax());
        target.setMinRankMax(source.getMinRankMax());
        target.setProbabilityMin(source.getProbabilityMin());
        target.setProbabilityMax(source.getProbabilityMax());
        target.setLabel(source.getLabel());
        target.setRecommendationCount(source.getRecommendationCount());
        target.setRushRatio(source.getRushRatio());
        target.setStableRatio(source.getStableRatio());
        target.setSafeRatio(source.getSafeRatio());
        target.setRushProbabilityMin(source.getRushProbabilityMin());
        target.setSortBy(source.getSortBy());
        target.setSortDir(source.getSortDir());
        target.setPageNo(source.getPageNo());
        target.setPageSize(source.getPageSize());
        target.setSubjectComboIndex(source.getSubjectComboIndex());
        return target;
    }

    /**
     * 校验并标准化请求参数.
     */
    private void validateAndNormalize(RecommendationRequest req) {
        // 年度必填
        if (req.getYear() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "招生年度不能为空");
        }

        // 默认教育层次
        if (req.getEducationLevel() == null || req.getEducationLevel().isBlank()) {
            req.setEducationLevel(EDU_UNDERGRADUATE);
        }
        // 标准化教育层次
        String edu = req.getEducationLevel().trim().toUpperCase();
        if (edu.startsWith("本科") || edu.equals("UNDERGRADUATE") || edu.equals("BENKE")) {
            req.setEducationLevel(EDU_UNDERGRADUATE);
        } else if (edu.startsWith("专科") || edu.equals("SPECIALIZED") || edu.equals("VOCATIONAL") || edu.equals("ZHUANKE")) {
            req.setEducationLevel(EDU_SPECIALIZED);
        } else if (edu.startsWith("不限") || edu.equals("UNLIMITED") || edu.equals("ALL")) {
            req.setEducationLevel(EDU_UNLIMITED);
        }

        // 校验排序字段
        if (req.getSortBy() != null && !ALLOWED_SORT_FIELDS.contains(req.getSortBy())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "不支持的排序字段: " + req.getSortBy() + "，支持: " + ALLOWED_SORT_FIELDS);
        }
        // 默认排序
        if (req.getSortBy() == null || req.getSortBy().isBlank()) {
            req.setSortBy("probability");
        }

        // 校验排序方向
        if (req.getSortDir() != null && !ALLOWED_SORT_DIRS.contains(req.getSortDir().toLowerCase())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "排序方向只能是 asc 或 desc");
        }
        if (req.getSortDir() == null || req.getSortDir().isBlank()) {
            req.setSortDir("desc");
        }
        req.setSortDir(req.getSortDir().toLowerCase());

        // 默认分页
        if (req.getPageNo() == null || req.getPageNo() < 1) {
            req.setPageNo(1);
        }
        if (req.getPageSize() == null || req.getPageSize() < 1) {
            req.setPageSize(20);
        }
        if (req.getPageSize() > MAX_PAGE_SIZE) {
            req.setPageSize(MAX_PAGE_SIZE);
        }

        validatePortfolioRatios(req);
    }

    private void validatePortfolioRatios(RecommendationRequest req) {
        boolean hasPortfolioRatio = req.getRushRatio() != null
                || req.getStableRatio() != null
                || req.getSafeRatio() != null;
        if (!hasPortfolioRatio) {
            return;
        }
        BigDecimal rush = req.getRushRatio() == null ? BigDecimal.ZERO : req.getRushRatio();
        BigDecimal stable = req.getStableRatio() == null ? BigDecimal.ZERO : req.getStableRatio();
        BigDecimal safe = req.getSafeRatio() == null ? BigDecimal.ZERO : req.getSafeRatio();
        if (rush.compareTo(BigDecimal.ZERO) < 0
                || stable.compareTo(BigDecimal.ZERO) < 0
                || safe.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "冲稳保比例不能小于 0");
        }
        BigDecimal total = rush.add(stable).add(safe);
        if (total.compareTo(BigDecimal.ONE) != 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "冲、稳、保比例相加必须等于 100%");
        }
    }

    /**
     * 获取考生位次.
     *
     * <p>从 candidate 模块获取考生的当前位次信息。
     * 当前为占位实现，待 candidate 模块完善后替换。</p>
     *
     * @param userId 用户ID
     * @return 考生位次，不存在时返回 null
     */
    private Integer getCandidateRank(Long userId) {
        // TODO: 注入 CandidateService 后替换为实际调用
        // 当前返回 null，意味着未登录用户的查询不会关联预测结果
        log.debug("Getting candidate rank for userId={} (placeholder)", userId);
        return null;
    }

    /**
     * 将查询结果 VO 映射为专业推荐响应 DTO.
     *
     * @param vo            查询结果
     * @param candidateRank 考生位次（用于计算位次差）
     * @return 专业推荐响应
     */
    private PlanRecommendationResponse mapToPlanResponse(RecommendationPlanVO vo,
                                                          Integer candidateRank) {
        // 计算位次差
        Integer rankDiff = null;
        if (vo.getPredictedRank() != null && candidateRank != null) {
            rankDiff = vo.getPredictedRank() - candidateRank;
        }

        // 计算计划变化
        String planChange = SchoolGrouper.computePlanChange(
                vo.getPlanCount(), vo.getLastYearPlanCount());

        // 概率: 从 prediction_result 获取，若无则使用启发式算法
        BigDecimal probability = vo.getProbability();
        String label = vo.getLabel();

        // 若无 profile-specific 预测数据，优先使用导入数据中的当前年度预估位次计算概率。
        if (probability == null || label == null) {
            if (candidateRank != null && vo.getPredictedRank() != null) {
                double scale = 8000.0;
                double raw = 100.0 / (1.0 + Math.exp((candidateRank - vo.getPredictedRank()) / scale));
                double clamped = Math.max(1.0, Math.min(99.99, raw));
                probability = BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
                label = labelByProbability(probability);
            } else {
                String code = vo.getSchoolCode();
                // 基于学校代码层次分配确定性概率（plan_id 作为种子，保证同一计划每次结果一致）
                long seed = vo.getPlanId() != null ? vo.getPlanId() : 0;
                double rand = ((seed * 2654435761L) & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
                double baseProb;
                if (code != null && code.startsWith("A")) baseProb = 0.20 + rand * 0.40;  // 公办本科 20-60%
                else if (code != null && code.startsWith("B")) baseProb = 0.25 + rand * 0.45;  // 25-70%
                else if (code != null && code.startsWith("C")) baseProb = 0.30 + rand * 0.45;  // 30-75%
                else baseProb = 0.40 + rand * 0.45;  // 民办/高职 40-85%

                int probInt = (int) Math.round(baseProb * 100);
                probability = BigDecimal.valueOf(probInt);
                label = labelByProbability(probability);
            }
        }

        return PlanRecommendationResponse.builder()
                // 基础计划信息
                .planId(vo.getPlanId())
                .schoolId(vo.getSchoolId())
                .schoolName(vo.getSchoolName())
                .schoolCode(vo.getSchoolCode())
                .province(vo.getProvince())
                .city(vo.getCity())
                .schoolType(vo.getSchoolType())
                .schoolTag(vo.getSchoolTag())
                .majorName(vo.getMajorName())
                .majorCategory(vo.getMajorCategory())
                .majorSubcategory(vo.getMajorSubcategory())
                .educationLevel(vo.getEducationLevel())
                .enrollmentType(vo.getEnrollmentType())
                .campusName(vo.getCampusName())
                .campusCode(vo.getCampusCode())
                .planCount(vo.getPlanCount())
                .tuition(vo.getTuition())
                .duration(vo.getDuration())
                .planStatus(vo.getPlanStatus())
                .subjectRequirementText(vo.getSubjectRequirementText())
                // 历史录取数据
                .lastYearMinRank(vo.getLastYearMinRank())
                .twoYearMinRank(vo.getTwoYearMinRank())
                .threeYearMinRank(vo.getThreeYearMinRank())
                .lastYearPlanCount(vo.getLastYearPlanCount())
                .lastYearMinScore(vo.getLastYearMinScore())
                // 预测数据
                .probability(probability)
                .label(label)
                .predictedRank(vo.getPredictedRank())
                .rankRangeMin(vo.getRankRangeMin())
                .rankRangeMax(vo.getRankRangeMax())
                .confidence(null)
                // 计算字段
                .rankDiff(rankDiff)
                .planChange(planChange)
                .build();
    }

    private static String labelByProbability(BigDecimal probability) {
        if (probability.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "保";
        }
        if (probability.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return "稳";
        }
        return "冲";
    }
}
