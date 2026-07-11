package com.example.admission.recommendation.util;

import com.example.admission.recommendation.dto.PlanRecommendationResponse;
import com.example.admission.recommendation.dto.RecommendationRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 冲稳保组合混合器（本科数据库路径与专科模型路径共用）.
 *
 * <p>负责把一批已算好概率与冲稳保标签的专业，按用户设定的冲/稳/保比例
 * 分层抽取，产出 TopK 推荐列表并写入 recommendRank。</p>
 *
 * <p>本类抽取自专科模型服务，两条推荐路径共用同一套逻辑，
 * 避免本科、专科推荐行为分叉。</p>
 */
public final class PortfolioMixer {

    /** 冲刺推荐默认最低概率 */
    private static final BigDecimal DEFAULT_RUSH_PROBABILITY_MIN = BigDecimal.valueOf(20);

    private PortfolioMixer() {
    }

    /** 是否启用冲稳保混合：仅在默认概率排序、无标签/概率区间筛选时启用 */
    public static boolean shouldMix(RecommendationRequest req) {
        String sortBy = req.getSortBy() == null ? "probability" : req.getSortBy();
        return "probability".equals(sortBy)
                && !Boolean.TRUE.equals(req.getIncludeAllCandidates())
                && (req.getLabel() == null || req.getLabel().isBlank())
                && req.getProbabilityMin() == null
                && req.getProbabilityMax() == null;
    }

    /** 冲刺推荐最低概率，做 0-100 范围钳制 */
    public static BigDecimal rushProbabilityMin(RecommendationRequest req) {
        BigDecimal configured = req.getRushProbabilityMin();
        if (configured == null) {
            return DEFAULT_RUSH_PROBABILITY_MIN;
        }
        if (configured.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (configured.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100);
        }
        return configured;
    }

    /** 冲刺下限过滤：标签为"冲"且概率低于冲刺下限的计划被剔除 */
    public static boolean matchesRushFloor(PlanRecommendationResponse plan, RecommendationRequest req) {
        return !"冲".equals(plan.getLabel())
                || plan.getProbability() == null
                || plan.getProbability().compareTo(rushProbabilityMin(req)) >= 0;
    }

    /** 概率区间与标签过滤 */
    public static boolean matchesProbabilityAndLabel(PlanRecommendationResponse plan,
                                                      RecommendationRequest req) {
        if (req.getProbabilityMin() != null && plan.getProbability() != null
                && plan.getProbability().compareTo(req.getProbabilityMin()) < 0) {
            return false;
        }
        if (req.getProbabilityMax() != null && plan.getProbability() != null
                && plan.getProbability().compareTo(req.getProbabilityMax()) > 0) {
            return false;
        }
        if ("冲".equals(req.getLabel()) && plan.getProbability() != null
                && plan.getProbability().compareTo(rushProbabilityMin(req)) < 0) {
            return false;
        }
        return req.getLabel() == null || req.getLabel().isBlank() || req.getLabel().equals(plan.getLabel());
    }

    /** 按概率给出冲稳保标签 */
    public static String label(BigDecimal probability) {
        if (probability == null) {
            return null;
        }
        if (probability.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "保";
        }
        if (probability.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return "稳";
        }
        return "冲";
    }

    /** 排序比较器 */
    public static Comparator<PlanRecommendationResponse> comparator(RecommendationRequest req) {
        String sortBy = req.getSortBy() == null ? "probability" : req.getSortBy();
        boolean asc = "asc".equalsIgnoreCase(req.getSortDir());
        Comparator<PlanRecommendationResponse> comparator;
        switch (sortBy) {
            case "rankDiff" -> comparator = Comparator.comparing(
                    PlanRecommendationResponse::getRankDiff,
                    Comparator.nullsLast(Integer::compareTo));
            case "lastYearMinRank" -> comparator = Comparator.comparing(
                    PlanRecommendationResponse::getLastYearMinRank,
                    Comparator.nullsLast(Integer::compareTo));
            case "planCount" -> comparator = Comparator.comparing(
                    PlanRecommendationResponse::getPlanCount,
                    Comparator.nullsLast(Integer::compareTo));
            case "tuition" -> comparator = Comparator.comparing(
                    PlanRecommendationResponse::getTuition,
                    Comparator.nullsLast(BigDecimal::compareTo));
            default -> comparator = Comparator.comparing(
                    PlanRecommendationResponse::getProbability,
                    Comparator.nullsLast(BigDecimal::compareTo));
        }
        if (!asc) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(PlanRecommendationResponse::getSchoolName,
                Comparator.nullsLast(String::compareTo));
    }

    /**
     * 冲稳保分层混合，产出 TopK 推荐列表.
     *
     * @param sorted   已过滤的候选列表
     * @param pageSize 目标推荐数量
     * @param req      推荐请求（提供冲稳保比例与冲刺下限）
     * @return 混合后的列表，前 target 条写入 recommendRank
     */
    public static List<PlanRecommendationResponse> mix(List<PlanRecommendationResponse> sorted,
                                                       int pageSize,
                                                       RecommendationRequest req) {
        if (sorted.size() <= pageSize) {
            assignRecommendRanks(sorted, sorted.size());
            return sorted;
        }
        BigDecimal rushMin = rushProbabilityMin(req);
        List<PlanRecommendationResponse> rush = sorted.stream()
                .filter(plan -> "冲".equals(plan.getLabel()))
                .filter(plan -> plan.getProbability() == null || plan.getProbability().compareTo(rushMin) >= 0)
                .toList();
        List<PlanRecommendationResponse> stable = sorted.stream()
                .filter(plan -> "稳".equals(plan.getLabel()))
                .toList();
        List<PlanRecommendationResponse> safe = sorted.stream()
                .filter(plan -> "保".equals(plan.getLabel()))
                .toList();

        int target = Math.min(pageSize, sorted.size());
        PortfolioQuotas quotas = portfolioQuotas(target, req, rush.size(), stable.size(), safe.size());

        List<PlanRecommendationResponse> mixed = new ArrayList<>(sorted.size());
        mixed.addAll(pickStratified(rush, quotas.rush(), rushBands(rushMin)));
        mixed.addAll(pickStratified(stable, quotas.stable(), stableBands()));
        mixed.addAll(pickStratified(safe, quotas.safe(), safeBands()));

        Set<Long> picked = mixed.stream()
                .map(PlanRecommendationResponse::getPlanId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (PlanRecommendationResponse plan : sorted) {
            Long planId = plan.getPlanId();
            if (planId == null || !picked.contains(planId)) {
                mixed.add(plan);
                if (planId != null) {
                    picked.add(planId);
                }
            }
        }
        assignRecommendRanks(mixed, target);
        return mixed;
    }

    private static void assignRecommendRanks(List<PlanRecommendationResponse> plans, int target) {
        int limit = Math.min(target, plans.size());
        for (int i = 0; i < plans.size(); i++) {
            plans.get(i).setRecommendRank(i < limit ? i + 1 : null);
        }
    }

    private static List<PlanRecommendationResponse> pickStratified(List<PlanRecommendationResponse> source,
                                                                   int quota,
                                                                   List<ProbabilityBand> bands) {
        if (quota <= 0 || source.isEmpty()) {
            return List.of();
        }
        List<List<PlanRecommendationResponse>> bandPlans = new ArrayList<>();
        for (ProbabilityBand band : bands) {
            bandPlans.add(source.stream()
                    .filter(plan -> band.contains(plan.getProbability()))
                    .toList());
        }
        int[] available = bandPlans.stream().mapToInt(List::size).toArray();
        int[] bandQuotas = bandQuotas(quota, bands, available);

        Set<Long> picked = new HashSet<>();
        for (int i = 0; i < bandPlans.size(); i++) {
            addFirstNPlanIds(picked, bandPlans.get(i), bandQuotas[i]);
        }

        List<PlanRecommendationResponse> result = new ArrayList<>(Math.min(quota, source.size()));
        for (PlanRecommendationResponse plan : source) {
            Long planId = plan.getPlanId();
            if (planId != null && picked.contains(planId)) {
                result.add(plan);
            }
        }
        return result;
    }

    private static int[] bandQuotas(int quota, List<ProbabilityBand> bands, int[] available) {
        int[] desired = new int[bands.size()];
        double[] ideal = new double[bands.size()];
        int availableTotal = sum(available);
        int target = Math.min(quota, availableTotal);
        for (int i = 0; i < bands.size(); i++) {
            ideal[i] = target * bands.get(i).weight();
            desired[i] = (int) Math.floor(ideal[i]);
        }
        while (sum(desired) < target) {
            int best = bestRemainderIndex(ideal, desired);
            if (best < 0) {
                break;
            }
            desired[best]++;
        }

        int[] result = new int[bands.size()];
        for (int i = 0; i < bands.size(); i++) {
            result[i] = Math.min(desired[i], available[i]);
        }

        for (int i = 0; i < bands.size(); i++) {
            int missing = desired[i] - result[i];
            if (missing > 0) {
                fillFromAdjacent(result, available, bands, i, missing);
            }
        }
        while (sum(result) < target) {
            int best = bestWeightedCapacityIndex(result, available, bands);
            if (best < 0) {
                break;
            }
            result[best]++;
        }
        return result;
    }

    private static int bestRemainderIndex(double[] ideal, int[] current) {
        int best = -1;
        double bestRemainder = -1.0;
        for (int i = 0; i < ideal.length; i++) {
            double remainder = ideal[i] - Math.floor(ideal[i]);
            if (remainder > bestRemainder || (remainder == bestRemainder && current[i] < current[Math.max(best, 0)])) {
                best = i;
                bestRemainder = remainder;
            }
        }
        return best;
    }

    private static void fillFromAdjacent(int[] result,
                                         int[] available,
                                         List<ProbabilityBand> bands,
                                         int sourceIndex,
                                         int missing) {
        for (int distance = 1; distance < bands.size() && missing > 0; distance++) {
            int left = sourceIndex - distance;
            int right = sourceIndex + distance;
            int first = preferredAdjacent(left, right, bands);
            int second = first == left ? right : left;
            missing = fillBand(result, available, first, missing);
            missing = fillBand(result, available, second, missing);
        }
    }

    private static int preferredAdjacent(int left, int right, List<ProbabilityBand> bands) {
        if (left < 0) {
            return right;
        }
        if (right >= bands.size()) {
            return left;
        }
        return bands.get(right).weight() >= bands.get(left).weight() ? right : left;
    }

    private static int fillBand(int[] result, int[] available, int index, int missing) {
        if (index < 0 || index >= result.length || missing <= 0) {
            return missing;
        }
        int room = available[index] - result[index];
        int add = Math.min(room, missing);
        if (add > 0) {
            result[index] += add;
            missing -= add;
        }
        return missing;
    }

    private static int bestWeightedCapacityIndex(int[] result, int[] available, List<ProbabilityBand> bands) {
        int best = -1;
        double bestWeight = -1.0;
        for (int i = 0; i < result.length; i++) {
            if (result[i] >= available[i]) {
                continue;
            }
            double weight = bands.get(i).weight();
            if (weight > bestWeight) {
                best = i;
                bestWeight = weight;
            }
        }
        return best;
    }

    private static void addFirstNPlanIds(Set<Long> target,
                                         List<PlanRecommendationResponse> source,
                                         int count) {
        int limit = Math.min(count, source.size());
        for (int i = 0; i < limit; i++) {
            Long planId = source.get(i).getPlanId();
            if (planId != null) {
                target.add(planId);
            }
        }
    }

    private static List<ProbabilityBand> rushBands(BigDecimal rushMin) {
        double min = rushMin == null ? 20.0 : Math.max(0.0, rushMin.doubleValue());
        return List.of(
                new ProbabilityBand(min, 30.0, 0.25),
                new ProbabilityBand(Math.max(min, 30.0), 40.0, 0.30),
                new ProbabilityBand(Math.max(min, 40.0), 50.0, 0.45)
        );
    }

    private static List<ProbabilityBand> stableBands() {
        return List.of(
                new ProbabilityBand(50.0, 60.0, 0.30),
                new ProbabilityBand(60.0, 70.0, 0.40),
                new ProbabilityBand(70.0, 80.0, 0.30)
        );
    }

    private static List<ProbabilityBand> safeBands() {
        return List.of(
                new ProbabilityBand(80.0, 90.0, 0.20),
                new ProbabilityBand(90.0, 95.0, 0.35),
                new ProbabilityBand(95.0, 100.01, 0.45)
        );
    }

    private static PortfolioQuotas portfolioQuotas(int target,
                                                   RecommendationRequest req,
                                                   int rushAvailable,
                                                   int stableAvailable,
                                                   int safeAvailable) {
        PortfolioRatios ratios = portfolioRatios(req);
        double[] ideal = new double[] {
                target * ratios.rush(),
                target * ratios.stable(),
                target * ratios.safe()
        };
        int[] available = new int[] { rushAvailable, stableAvailable, safeAvailable };
        int[] quota = new int[] {
                Math.min(available[0], (int) Math.floor(ideal[0])),
                Math.min(available[1], (int) Math.floor(ideal[1])),
                Math.min(available[2], (int) Math.floor(ideal[2]))
        };

        while (sum(quota) < target && sum(quota) < sum(available)) {
            int best = -1;
            double bestRemainder = -1.0;
            for (int i = 0; i < quota.length; i++) {
                if (quota[i] >= available[i]) {
                    continue;
                }
                double remainder = ideal[i] - Math.floor(ideal[i]);
                if (remainder > bestRemainder) {
                    best = i;
                    bestRemainder = remainder;
                }
            }
            if (best < 0) {
                break;
            }
            quota[best]++;
        }
        return new PortfolioQuotas(quota[0], quota[1], quota[2]);
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    private static PortfolioRatios portfolioRatios(RecommendationRequest req) {
        double rush = ratioValue(req.getRushRatio(), 0.25);
        double stable = ratioValue(req.getStableRatio(), 1.0 / 3.0);
        double safe = ratioValue(req.getSafeRatio(), 0.0);
        if (req.getSafeRatio() == null) {
            safe = Math.max(0.0, 1.0 - rush - stable);
        }
        double sum = rush + stable + safe;
        if (sum <= 0.0) {
            return new PortfolioRatios(0.25, 1.0 / 3.0, 5.0 / 12.0);
        }
        return new PortfolioRatios(rush / sum, stable / sum, safe / sum);
    }

    private static double ratioValue(BigDecimal value, double fallback) {
        if (value == null) {
            return fallback;
        }
        double ratio = value.doubleValue();
        if (Double.isNaN(ratio) || Double.isInfinite(ratio) || ratio < 0.0) {
            return 0.0;
        }
        return Math.min(1.0, ratio);
    }

    private record PortfolioRatios(double rush, double stable, double safe) {
    }

    private record PortfolioQuotas(int rush, int stable, int safe) {
    }

    private record ProbabilityBand(double minInclusive, double maxExclusive, double weight) {
        boolean contains(BigDecimal probability) {
            if (probability == null) {
                return false;
            }
            double value = probability.doubleValue();
            return value >= minInclusive && value < maxExclusive;
        }
    }
}
