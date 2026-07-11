package com.example.admission.recommendation.service;

import com.example.admission.common.TraceContext;
import com.example.admission.recommendation.dto.PlanRecommendationResponse;
import com.example.admission.recommendation.dto.RecommendationRequest;
import com.example.admission.recommendation.dto.RecommendationResponse;
import com.example.admission.recommendation.dto.SchoolGroupResponse;
import com.example.admission.recommendation.util.PortfolioMixer;
import com.example.admission.recommendation.util.SchoolGrouper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * File-backed 2026 Shandong specialized recommendation model.
 *
 * <p>The Python training script writes a compact model artifact with p10/p50/p90
 * cutoff-rank estimates. This service computes candidate-specific probabilities
 * at request time, so it does not depend on precomputed profile rows in the DB.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpecializedModelRecommendationService {

    private static final String MODEL_VERSION = "specialized-rank-v2-specialist-workbook";
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 10_000;
    private static final int DEFAULT_RECOMMENDATION_COUNT = 96;

    @Value("${app.specialized-model.path:data/organized/modeling_outputs/specialized_rank_model_2026.csv}")
    private String modelPath;

    private volatile List<ModelPlan> cachedPlans;
    private volatile long cachedLastModified = -1L;

    public boolean isAvailable() {
        return Files.isRegularFile(resolveModelPath());
    }

    /** 专科模型版本号，供"不限"合并路径填充响应。 */
    public String modelVersion() {
        return MODEL_VERSION;
    }

    public RecommendationResponse search(RecommendationRequest req) {
        List<PlanRecommendationResponse> eligibleAll = collectAll(req);
        List<PlanRecommendationResponse> filtered = eligibleAll.stream()
                .filter(plan -> PortfolioMixer.matchesProbabilityAndLabel(plan, req))
                .filter(plan -> PortfolioMixer.matchesRushFloor(plan, req))
                .collect(Collectors.toList());

        long eligiblePlans = eligibleAll.size();
        long totalPlans = filtered.size();
        int totalSchools = (int) filtered.stream()
                .map(PlanRecommendationResponse::getSchoolId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        int pageNo = req.getPageNo() == null || req.getPageNo() < 1 ? 1 : req.getPageNo();
        int pageSize = req.getRecommendationCount() != null ? req.getRecommendationCount()
                : (req.getPageSize() == null ? DEFAULT_RECOMMENDATION_COUNT : req.getPageSize());
        pageSize = Math.max(MIN_PAGE_SIZE, Math.min(MAX_PAGE_SIZE, pageSize));
        if (PortfolioMixer.shouldMix(req)) {
            filtered = PortfolioMixer.mix(filtered, pageSize, req);
        } else {
            filtered.sort(PortfolioMixer.comparator(req));
        }
        int from = Math.min((pageNo - 1) * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());
        List<PlanRecommendationResponse> page = new ArrayList<>(filtered.subList(from, to));
        List<SchoolGroupResponse> groups = SchoolGrouper.group(page, req.getSortBy(), req.getSortDir());

        return RecommendationResponse.builder()
                .schoolGroups(groups)
                .totalPlans(totalPlans)
                .eligiblePlanCount(eligiblePlans)
                .candidatePlanCount(totalPlans)
                .recommendedPlanCount(page.size())
                .totalSchools(totalSchools)
                .planDataVersion("organized-2026-specialized")
                .historyDataVersion("history-2022-2025")
                .modelVersion(MODEL_VERSION)
                .updatedAt(Instant.now())
                .traceId(TraceContext.getTraceId())
                .build();
    }

    /**
     * 供"不限"学历合并路径复用：返回已算概率、并经概率/标签/冲刺下限过滤的专科候选列表.
     *
     * <p>不做分页与冲稳保混合，由调用方合并本专科候选后统一处理。</p>
     */
    public List<PlanRecommendationResponse> collectEligible(RecommendationRequest req) {
        return collectAll(req).stream()
                .filter(plan -> PortfolioMixer.matchesProbabilityAndLabel(plan, req))
                .filter(plan -> PortfolioMixer.matchesRushFloor(plan, req))
                .collect(Collectors.toList());
    }

    /** 应用选科硬过滤与用户筛选后，逐条计算概率得到的完整候选列表（未做概率/标签过滤）。 */
    private List<PlanRecommendationResponse> collectAll(RecommendationRequest req) {
        List<ModelPlan> plans = loadPlans();
        Integer candidateRank = req.getRank();
        Set<String> subjects = normalizeSubjects(req.getSubjects());
        return plans.stream()
                .filter(plan -> matchesSubjects(plan, subjects))
                .filter(plan -> matchesUserFilters(plan, req))
                .map(plan -> toResponse(plan, candidateRank))
                .collect(Collectors.toList());
    }

    private List<ModelPlan> loadPlans() {
        Path path = resolveModelPath();
        try {
            long lastModified = Files.getLastModifiedTime(path).toMillis();
            List<ModelPlan> local = cachedPlans;
            if (local != null && cachedLastModified == lastModified) {
                return local;
            }
            synchronized (this) {
                if (cachedPlans != null && cachedLastModified == lastModified) {
                    return cachedPlans;
                }
                List<ModelPlan> loaded = readCsv(path);
                cachedPlans = loaded;
                cachedLastModified = lastModified;
                log.info("Loaded specialized model artifact: path={}, rows={}", path, loaded.size());
                return loaded;
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法读取专科模型文件: " + path.toAbsolutePath(), e);
        }
    }

    private Path resolveModelPath() {
        Path configured = Path.of(modelPath);
        if (Files.isRegularFile(configured)) {
            return configured;
        }
        String defaultSuffix = "data/organized/modeling_outputs/specialized_rank_model_2026.csv";
        Path[] candidates = new Path[] {
                Path.of(defaultSuffix),
                Path.of("../" + defaultSuffix),
                Path.of("../../" + defaultSuffix)
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return configured;
    }

    private List<ModelPlan> readCsv(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return List.of();
            }
            List<String> headers = parseCsvLine(stripBom(headerLine));
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                index.put(headers.get(i), i);
            }
            List<ModelPlan> plans = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> values = parseCsvLine(line);
                plans.add(ModelPlan.from(values, index));
            }
            return plans;
        }
    }

    private static String stripBom(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF'
                ? value.substring(1)
                : value;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private static boolean matchesSubjects(ModelPlan plan, Set<String> subjects) {
        if (plan.subjectTokens.isEmpty()) {
            return true;
        }
        if (subjects == null || subjects.isEmpty()) {
            return false;
        }
        return subjects.containsAll(plan.subjectTokens);
    }

    private static boolean matchesUserFilters(ModelPlan plan, RecommendationRequest req) {
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            String kw = req.getKeyword().trim().toLowerCase(Locale.ROOT);
            if (!lower(plan.schoolName).contains(kw) && !lower(plan.majorName).contains(kw)) {
                return false;
            }
        }
        if (req.getProvince() != null && !req.getProvince().isEmpty()
                && !req.getProvince().contains(plan.province)) {
            return false;
        }
        if (req.getCity() != null && !req.getCity().isEmpty()
                && !req.getCity().contains(plan.city)) {
            return false;
        }
        if (req.getSchoolType() != null && !req.getSchoolType().isEmpty()
                && req.getSchoolType().stream().noneMatch(v -> containsNormalized(plan.schoolType, v))) {
            return false;
        }
        if (req.getMajorCategory() != null && !req.getMajorCategory().isEmpty()
                && !req.getMajorCategory().contains(plan.majorCategory)) {
            return false;
        }
        if (req.getMajorSubcategory() != null && !req.getMajorSubcategory().isEmpty()
                && !req.getMajorSubcategory().contains(plan.majorSubcategory)) {
            return false;
        }
        if (req.getExcludeMajorCategory() != null && !req.getExcludeMajorCategory().isEmpty()
                && req.getExcludeMajorCategory().contains(plan.majorCategory)) {
            return false;
        }
        if (req.getExcludeMajorSubcategory() != null && !req.getExcludeMajorSubcategory().isEmpty()
                && req.getExcludeMajorSubcategory().contains(plan.majorSubcategory)) {
            return false;
        }
        if (req.getEnrollmentType() != null && !req.getEnrollmentType().isEmpty()
                && req.getEnrollmentType().stream().noneMatch(v -> containsNormalized(plan.enrollmentType, v))) {
            return false;
        }
        if (Boolean.TRUE.equals(req.getExcludeSinoForeign()) && plan.enrollmentType.contains("中外")) {
            return false;
        }
        if (Boolean.TRUE.equals(req.getExcludeSchoolEnterprise()) && plan.enrollmentType.contains("校企")) {
            return false;
        }
        if (req.getTuitionMin() != null && plan.tuition != null && plan.tuition < req.getTuitionMin()) {
            return false;
        }
        if (req.getTuitionMax() != null && plan.tuition != null && plan.tuition > req.getTuitionMax()) {
            return false;
        }
        if (req.getPlanCountMin() != null && plan.planCount < req.getPlanCountMin()) {
            return false;
        }
        if (req.getPlanCountMax() != null && plan.planCount > req.getPlanCountMax()) {
            return false;
        }
        if (req.getMinRankMax() != null && plan.lastYearMinRank != null
                && plan.lastYearMinRank > req.getMinRankMax()) {
            return false;
        }
        return true;
    }

    private static PlanRecommendationResponse toResponse(ModelPlan plan, Integer candidateRank) {
        BigDecimal probability = computeProbability(plan, candidateRank);
        String label = label(probability);
        Integer rankDiff = candidateRank == null ? null : plan.predictedRankP50 - candidateRank;
        return PlanRecommendationResponse.builder()
                .planId(plan.planId)
                .schoolId(plan.schoolId)
                .schoolName(plan.schoolName)
                .schoolCode(plan.schoolCode)
                .province(plan.province)
                .city(plan.city)
                .schoolType(plan.schoolType)
                .schoolTag(plan.schoolTag)
                .majorName(plan.majorName)
                .majorCategory(plan.majorCategory)
                .majorSubcategory(plan.majorSubcategory)
                .educationLevel(plan.educationLevel)
                .enrollmentType(plan.enrollmentType)
                .planCount(plan.planCount)
                .tuition(plan.tuition == null ? null : BigDecimal.valueOf(plan.tuition))
                .duration(plan.duration)
                .planStatus("ACTIVE")
                .subjectRequirementText(plan.subjectRequirement)
                .lastYearMinRank(plan.lastYearMinRank)
                .twoYearMinRank(plan.twoYearMinRank)
                .threeYearMinRank(plan.threeYearMinRank)
                .lastYearPlanCount(plan.lastYearPlanCount)
                .lastYearMinScore(plan.lastYearMinScore == null ? null : BigDecimal.valueOf(plan.lastYearMinScore))
                .probability(probability)
                .label(label)
                .predictedRank(plan.predictedRankP50)
                .rankRangeMin(plan.predictedRankP10)
                .rankRangeMax(plan.predictedRankP90)
                .confidence(confidenceNumber(plan.confidence))
                .rankDiff(rankDiff)
                .planChange(plan.planChange)
                .build();
    }

    private static BigDecimal computeProbability(ModelPlan plan, Integer candidateRank) {
        if (candidateRank == null || candidateRank <= 0) {
            return BigDecimal.valueOf(50);
        }
        double spread = Math.max(plan.predictedRankP90 - plan.predictedRankP10, plan.predictedRankP50 * 0.08);
        double scale = Math.max(8000.0, spread / 3.2);
        double raw = 100.0 / (1.0 + Math.exp((candidateRank - plan.predictedRankP50) / scale));
        double clamped = Math.max(1.0, Math.min(99.99, raw));
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
    }

    private static String label(BigDecimal probability) {
        if (probability.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "保";
        }
        if (probability.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return "稳";
        }
        return "冲";
    }

    private static BigDecimal confidenceNumber(String confidence) {
        if ("高".equals(confidence)) {
            return BigDecimal.valueOf(0.85);
        }
        if ("中".equals(confidence)) {
            return BigDecimal.valueOf(0.65);
        }
        return BigDecimal.valueOf(0.45);
    }

    private static Set<String> normalizeSubjects(List<String> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (String subject : subjects) {
            if (subject == null || subject.isBlank()) {
                continue;
            }
            String normalized = subject.trim();
            if ("政治".equals(normalized)) {
                normalized = "思想政治";
            }
            result.add(normalized);
        }
        return result;
    }

    private static boolean containsNormalized(String source, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return lower(source).contains(expected.trim().toLowerCase(Locale.ROOT));
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record ModelPlan(
            Long planId,
            Long schoolId,
            String schoolCode,
            String schoolName,
            String majorCode,
            String majorName,
            String province,
            String city,
            String schoolType,
            String schoolTag,
            String majorCategory,
            String majorSubcategory,
            String enrollmentType,
            String educationLevel,
            int planCount,
            Integer tuition,
            Integer duration,
            String subjectRequirement,
            Set<String> subjectTokens,
            Integer lastYearMinScore,
            Integer lastYearMinRank,
            Integer twoYearMinRank,
            Integer threeYearMinRank,
            Integer lastYearPlanCount,
            int predictedRankP10,
            int predictedRankP50,
            int predictedRankP90,
            String confidence,
            String planChange
    ) {
        static ModelPlan from(List<String> values, Map<String, Integer> index) {
            return new ModelPlan(
                    longValue(values, index, "plan_id"),
                    longValue(values, index, "school_id"),
                    value(values, index, "school_code"),
                    value(values, index, "school_name"),
                    value(values, index, "major_code"),
                    value(values, index, "major_name"),
                    value(values, index, "province"),
                    value(values, index, "city"),
                    value(values, index, "school_type"),
                    value(values, index, "school_tag"),
                    value(values, index, "major_category"),
                    value(values, index, "major_subcategory"),
                    value(values, index, "enrollment_type"),
                    value(values, index, "education_level"),
                    intValue(values, index, "plan_count", 0),
                    nullableInt(values, index, "tuition"),
                    nullableInt(values, index, "duration"),
                    value(values, index, "subject_requirement"),
                    tokenSet(value(values, index, "subject_tokens")),
                    nullableInt(values, index, "last_year_min_score"),
                    nullableInt(values, index, "last_year_min_rank"),
                    nullableInt(values, index, "two_year_min_rank"),
                    nullableInt(values, index, "three_year_min_rank"),
                    nullableInt(values, index, "last_year_plan_count"),
                    intValue(values, index, "predicted_rank_p10", 0),
                    intValue(values, index, "predicted_rank_p50", 0),
                    intValue(values, index, "predicted_rank_p90", 0),
                    value(values, index, "confidence"),
                    value(values, index, "plan_change")
            );
        }

        private static String value(List<String> values, Map<String, Integer> index, String key) {
            Integer i = index.get(key);
            if (i == null || i >= values.size()) {
                return "";
            }
            String value = values.get(i);
            return value == null ? "" : value.trim();
        }

        private static Long longValue(List<String> values, Map<String, Integer> index, String key) {
            String value = value(values, index, key);
            if (value.isBlank()) {
                return null;
            }
            return Long.parseLong(value);
        }

        private static int intValue(List<String> values, Map<String, Integer> index, String key, int fallback) {
            Integer value = nullableInt(values, index, key);
            return value == null ? fallback : value;
        }

        private static Integer nullableInt(List<String> values, Map<String, Integer> index, String key) {
            String value = value(values, index, key);
            if (value.isBlank()) {
                return null;
            }
            int dot = value.indexOf('.');
            if (dot >= 0) {
                value = value.substring(0, dot);
            }
            return Integer.parseInt(value);
        }

        private static Set<String> tokenSet(String raw) {
            if (raw == null || raw.isBlank()) {
                return Set.of();
            }
            Set<String> tokens = new HashSet<>();
            for (String token : raw.split("\\|")) {
                if (!token.isBlank()) {
                    tokens.add(token.trim());
                }
            }
            return tokens;
        }
    }
}
