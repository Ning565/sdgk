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
import com.example.admission.recommendation.util.PortfolioMixer;
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
 * <p>统一的推荐流程为：硬过滤 → 位次感知概率 → 位次兜底 → 冲稳保混合 → TopK：
 * <ol>
 *   <li>硬过滤 — 版本、年度、状态、选科、教育层次等不可跳过的条件（SQL 层）</li>
 *   <li>用户筛选 — 关键词、地区、院校性质、专业门类、学费等用户可控条件（SQL 层）</li>
 *   <li>位次感知概率 — 逐条按考生位次与参考位次计算录取概率（Java 层）</li>
 *   <li>位次兜底 — 无自身位次参考的新增/特殊招生计划，回退到同校最宽松位次估算并做硬剔除</li>
 *   <li>冲稳保混合 + 分组分页 — 由 {@link PortfolioMixer} 完成组合，再按院校聚合</li>
 * </ol>
 *
 * <p>本科走数据库路径（{@link RecommendationMapper}），专科 2026 走
 * {@link SpecializedModelRecommendationService} 的文件模型路径，两者共用同一套
 * 概率过滤与冲稳保混合逻辑（{@link PortfolioMixer}）。</p>
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
    /** 候选池上限：全量拉取后在 Java 侧算概率与混合 */
    private static final int MAX_CANDIDATE_POOL = 10_000;
    /** 无自身位次参考的兜底专业概率上限（不进"保"） */
    private static final double FALLBACK_PROBABILITY_CAP = 50.0;
    /** 院校位次兜底裕度：兜底专业考生位次不得差于同校最宽松参考位次 10% 以上 */
    private static final double SCHOOL_FLOOR_MARGIN = 1.10;
    /** sigmoid 概率的最小尺度参数 */
    private static final double MIN_PROBABILITY_SCALE = 8000.0;

    /**
     * 执行推荐搜索.
     *
     * @param req    推荐搜索请求
     * @param userId 当前用户ID（可选，用于获取个性化预测结果）
     * @return 推荐搜索响应
     * @throws BusinessException 如果参数无效或查询失败
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
        DatabaseCandidates db = collectDatabaseCandidates(req, userId);
        List<PlanRecommendationResponse> candidates = db.candidates();

        long candidatePlanCount = candidates.size();
        int totalSchools = distinctSchools(candidates);

        List<PlanRecommendationResponse> page = mixAndPage(candidates, req);
        List<SchoolGroupResponse> schoolGroups = SchoolGrouper.group(page, req.getSortBy(), req.getSortDir());

        return RecommendationResponse.builder()
                .schoolGroups(schoolGroups)
                .totalPlans(candidatePlanCount)
                .eligiblePlanCount(db.eligibleCount())
                .candidatePlanCount(candidatePlanCount)
                .recommendedPlanCount(page.size())
                .totalSchools(totalSchools)
                .planDataVersion(db.planDataVersion())
                .historyDataVersion(db.historyDataVersion())
                .modelVersion(null)
                .updatedAt(Instant.now())
                .traceId(TraceContext.getTraceId())
                .build();
    }

    private RecommendationResponse searchUnlimited(RecommendationRequest req, Long userId) {
        List<PlanRecommendationResponse> candidates = new ArrayList<>();
        String planDataVersion = null;
        String historyDataVersion = null;
        String modelVersion = null;

        // 专科候选：2026 使用专科文件模型
        if (req.getYear() != null
                && req.getYear() == 2026
                && specializedModelRecommendationService.isAvailable()) {
            RecommendationRequest specializedReq = copyRequest(req);
            specializedReq.setEducationLevel(EDU_SPECIALIZED);
            candidates.addAll(specializedModelRecommendationService.collectEligible(specializedReq));
            planDataVersion = "organized-2026-specialized";
            historyDataVersion = "history-2022-2025";
            modelVersion = specializedModelRecommendationService.modelVersion();
        }

        // 本科候选：数据库路径
        try {
            RecommendationRequest undergraduateReq = copyRequest(req);
            undergraduateReq.setEducationLevel(EDU_UNDERGRADUATE);
            DatabaseCandidates db = collectDatabaseCandidates(undergraduateReq, userId);
            candidates.addAll(db.candidates());
            planDataVersion = joinVersion(planDataVersion, db.planDataVersion());
            historyDataVersion = joinVersion(historyDataVersion, db.historyDataVersion());
        } catch (BusinessException e) {
            if (!ErrorCode.DATA_NOT_FOUND.equals(e.getErrorCode())) {
                throw e;
            }
            log.warn("Undergraduate recommendation skipped for unlimited search: {}", e.getMessage());
        }

        long candidatePlanCount = candidates.size();
        int totalSchools = distinctSchools(candidates);

        List<PlanRecommendationResponse> page = mixAndPage(candidates, req);
        List<SchoolGroupResponse> schoolGroups = SchoolGrouper.group(page, req.getSortBy(), req.getSortDir());

        return RecommendationResponse.builder()
                .schoolGroups(schoolGroups)
                .totalPlans(candidatePlanCount)
                .eligiblePlanCount(candidatePlanCount)
                .candidatePlanCount(candidatePlanCount)
                .recommendedPlanCount(page.size())
                .totalSchools(totalSchools)
                .planDataVersion(planDataVersion)
                .historyDataVersion(historyDataVersion)
                .modelVersion(modelVersion)
                .updatedAt(Instant.now())
                .traceId(TraceContext.getTraceId())
                .build();
    }

    /**
     * 拉取本科数据库候选池并逐条算概率、做位次兜底与概率过滤（未分页、未混合）.
     */
    private DatabaseCandidates collectDatabaseCandidates(RecommendationRequest req, Long userId) {
        // 1. 获取当前生效的数据版本
        ActiveDataVersion planVersion = dataVersionService.getActiveVersion(DATA_TYPE_PLAN, req.getYear());
        if (planVersion == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND,
                    "招生年度 " + req.getYear() + " 的招生计划数据尚未发布");
        }
        ActiveDataVersion historyVersion = dataVersionService.getActiveVersion(
                DATA_TYPE_HISTORY, req.getYear() - 1);

        // 2. 获取考生位次
        final Integer candidateRank = req.getRank() != null ? req.getRank()
                : (userId != null ? getCandidateRank(userId) : null);

        // 3. 全量拉取候选池（仅硬过滤 + 用户筛选）
        List<RecommendationPlanVO> pool = recommendationMapper.searchCandidates(
                req, planVersion.getDataVersionId(), req.getSubjectComboIndex(), MAX_CANDIDATE_POOL);

        // 4. 逐条位次感知概率 + 位次兜底 + 概率/冲刺下限过滤
        List<PlanRecommendationResponse> candidates = buildCandidates(pool, candidateRank, req);

        return new DatabaseCandidates(candidates, pool.size(),
                "v" + planVersion.getDataVersionId(),
                historyVersion != null ? "v" + historyVersion.getDataVersionId() : null);
    }

    private List<PlanRecommendationResponse> buildCandidates(List<RecommendationPlanVO> pool,
                                                             Integer candidateRank,
                                                             RecommendationRequest req) {
        Map<Long, Integer> schoolReferenceRank = buildSchoolReferenceRank(pool);
        List<PlanRecommendationResponse> candidates = new ArrayList<>(pool.size());
        for (RecommendationPlanVO vo : pool) {
            PlanRecommendationResponse plan = estimatePlan(vo, candidateRank, schoolReferenceRank);
            if (plan == null) {
                continue;
            }
            if (!PortfolioMixer.matchesProbabilityAndLabel(plan, req)) {
                continue;
            }
            if (!PortfolioMixer.matchesRushFloor(plan, req)) {
                continue;
            }
            candidates.add(plan);
        }
        return candidates;
    }

    /**
     * 构建院校级最宽松参考位次表.
     *
     * <p>每个院校取其各专业参考位次（predicted_rank 优先，否则上年最低位次）中的
     * 最大值，代表该校最容易录取的分数线，用于无位次专业的兜底估算与硬剔除。</p>
     */
    private static Map<Long, Integer> buildSchoolReferenceRank(List<RecommendationPlanVO> pool) {
        Map<Long, Integer> map = new HashMap<>();
        for (RecommendationPlanVO vo : pool) {
            Integer own = ownReferenceRank(vo);
            if (own == null || vo.getSchoolId() == null) {
                continue;
            }
            map.merge(vo.getSchoolId(), own, Math::max);
        }
        return map;
    }

    /** 计划自身的参考位次：预测位次优先，否则上年最低位次；都无则 null。 */
    private static Integer ownReferenceRank(RecommendationPlanVO vo) {
        if (vo.getPredictedRank() != null) {
            return vo.getPredictedRank();
        }
        return vo.getLastYearMinRank();
    }

    /**
     * 将候选 VO 估算为专业推荐响应；不可达或无参考的计划返回 null 被剔除.
     */
    private PlanRecommendationResponse estimatePlan(RecommendationPlanVO vo,
                                                    Integer candidateRank,
                                                    Map<Long, Integer> schoolReferenceRank) {
        Integer own = ownReferenceRank(vo);
        boolean fallback = own == null;
        Integer reference = own != null ? own
                : (vo.getSchoolId() != null ? schoolReferenceRank.get(vo.getSchoolId()) : null);

        // 全校都无位次参考，无法判定可达 → 剔除，避免臆造概率
        if (reference == null) {
            return null;
        }

        // 严格院校位次兜底：兜底专业若考生位次明显差于同校最宽松参考位次，直接剔除（含名校新增专业）
        if (fallback && candidateRank != null
                && candidateRank > reference * SCHOOL_FLOOR_MARGIN) {
            return null;
        }

        BigDecimal probability = null;
        String label = null;
        BigDecimal confidence = null;
        if (candidateRank != null) {
            if (vo.getProbability() != null && vo.getLabel() != null) {
                // 已有 profile 级预测结果，直接采用
                probability = vo.getProbability();
                label = vo.getLabel();
            } else {
                double spread = referenceSpread(vo, reference);
                double scale = Math.max(MIN_PROBABILITY_SCALE, spread / 3.2);
                double raw = 100.0 / (1.0 + Math.exp((candidateRank - reference) / scale));
                double clamped = Math.max(1.0, Math.min(99.99, raw));
                if (fallback) {
                    // 无自身位次参考：概率封顶，永不进"保"
                    clamped = Math.min(clamped, FALLBACK_PROBABILITY_CAP);
                }
                probability = BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
                label = fallback ? labelWithoutSafe(probability) : PortfolioMixer.label(probability);
            }
            confidence = fallback ? BigDecimal.valueOf(0.45) : BigDecimal.valueOf(0.75);
        }

        Integer rankDiff = (own != null && candidateRank != null) ? own - candidateRank : null;
        String planChange = SchoolGrouper.computePlanChange(vo.getPlanCount(), vo.getLastYearPlanCount());

        return PlanRecommendationResponse.builder()
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
                .lastYearMinRank(vo.getLastYearMinRank())
                .twoYearMinRank(vo.getTwoYearMinRank())
                .threeYearMinRank(vo.getThreeYearMinRank())
                .lastYearPlanCount(vo.getLastYearPlanCount())
                .lastYearMinScore(vo.getLastYearMinScore())
                .probability(probability)
                .label(label)
                .predictedRank(vo.getPredictedRank())
                .rankRangeMin(vo.getRankRangeMin())
                .rankRangeMax(vo.getRankRangeMax())
                .confidence(confidence)
                .rankDiff(rankDiff)
                .planChange(planChange)
                .build();
    }

    /** 参考位次跨度：优先用位次区间宽度，否则取参考位次的 16%。 */
    private static double referenceSpread(RecommendationPlanVO vo, int reference) {
        if (vo.getRankRangeMin() != null && vo.getRankRangeMax() != null
                && vo.getRankRangeMax() > vo.getRankRangeMin()) {
            return vo.getRankRangeMax() - vo.getRankRangeMin();
        }
        return Math.abs(reference) * 0.16;
    }

    /** 兜底专业标签：概率已封顶 50%，最高只能进"稳"，永不进"保"。 */
    private static String labelWithoutSafe(BigDecimal probability) {
        if (probability.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return "稳";
        }
        return "冲";
    }

    /** 冲稳保混合 + 分页，本科与不限路径共用。 */
    private static List<PlanRecommendationResponse> mixAndPage(List<PlanRecommendationResponse> candidates,
                                                               RecommendationRequest req) {
        int pageNo = req.getPageNo() == null || req.getPageNo() < 1 ? 1 : req.getPageNo();
        int pageSize = req.getRecommendationCount() != null ? req.getRecommendationCount()
                : (req.getPageSize() != null ? req.getPageSize() : 20);
        pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));

        List<PlanRecommendationResponse> ordered = new ArrayList<>(candidates);
        if (PortfolioMixer.shouldMix(req)) {
            ordered = PortfolioMixer.mix(ordered, pageSize, req);
        } else {
            ordered.sort(PortfolioMixer.comparator(req));
        }
        int from = Math.min((pageNo - 1) * pageSize, ordered.size());
        int to = Math.min(from + pageSize, ordered.size());
        return new ArrayList<>(ordered.subList(from, to));
    }

    private static int distinctSchools(List<PlanRecommendationResponse> plans) {
        return (int) plans.stream()
                .map(PlanRecommendationResponse::getSchoolId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
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
        target.setIncludeAllCandidates(source.getIncludeAllCandidates());
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
            req.setSortDir("asc");
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
        log.debug("Getting candidate rank for userId={} (placeholder)", userId);
        return null;
    }

    /** 本科数据库候选池收集结果 */
    private record DatabaseCandidates(List<PlanRecommendationResponse> candidates,
                                      long eligibleCount,
                                      String planDataVersion,
                                      String historyDataVersion) {
    }
}
