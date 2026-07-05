package com.example.admission.prediction.service;

import com.example.admission.prediction.dto.PredictionRequest;
import com.example.admission.prediction.dto.PredictionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 预测算法服务.
 *
 * <p>基于位次比率的冲/稳/保三档预测算法。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionService {

    private static final String MODEL_VERSION = "simple-v1.0";

    /** 冲稳保标签的位次比率阈值 */
    private static final double BAO_THRESHOLD = 0.05;   // rankRatio > 0.05 → 保
    private static final double WEN_LOWER = -0.02;       // rankRatio >= -0.02 → 稳
    // rankRatio < -0.02 → 冲

    /** 概率缩放因子 */
    private static final double PROB_SCALE = 1000.0;

    /** 预测位次波动范围 (±10%) */
    private static final double RANK_RANGE_RATIO = 0.10;

    /**
     * 对考生和多个招生计划执行批量预测.
     *
     * @param req 请求对象uest HTTP 请求 预测请求（包含考生信息和计划列表）
     * @return 每个计划的预测结果列表
     */
    public List<PredictionResponse> predict(PredictionRequest request) {
        PredictionRequest.CandidateInfo candidate = request.getCandidate();
        Integer candidateRank = candidate.getRank();
        int candidateYear = candidate.getYear();

        List<PredictionResponse> responses = new ArrayList<>();

        for (PredictionRequest.PlanInfo plan : request.getPlans()) {
            PredictionResponse response = predictOne(candidateRank, candidateYear, plan);
            responses.add(response);
        }

        log.info("Prediction batch completed: candidateYear={}, candidateRank={}, planCount={}",
                candidateYear, candidateRank, responses.size());
        return responses;
    }

    /**
     * 对单个计划执行预测.
     */
    private PredictionResponse predictOne(Integer candidateRank, int candidateYear,
                                          PredictionRequest.PlanInfo plan) {
        List<PredictionRequest.HistoryEntry> history = plan.getHistory();
        List<String> reasons = new ArrayList<>();

        // 无历史数据时返回占位结果
        if (history == null || history.isEmpty()) {
            reasons.add("该计划无历史录取数据，无法计算预测概率");
            return PredictionResponse.builder()
                    .planId(plan.getPlanId())
                    .probability(null)
                    .label("待计算")
                    .predictedRankMin(null)
                    .predictedRankMax(null)
                    .confidence("低")
                    .reasons(reasons)
                    .modelVersion(MODEL_VERSION)
                    .build();
        }

        // 按年份降序排列，取最近一年的最低位次
        List<PredictionRequest.HistoryEntry> sortedHistory = history.stream()
                .filter(h -> h.getYear() != null)
                .sorted(Comparator.comparingInt(PredictionRequest.HistoryEntry::getYear).reversed())
                .collect(Collectors.toList());

        if (sortedHistory.isEmpty()) {
            reasons.add("该计划历史数据无有效年份");
            return PredictionResponse.builder()
                    .planId(plan.getPlanId())
                    .label("待计算")
                    .confidence("低")
                    .reasons(reasons)
                    .modelVersion(MODEL_VERSION)
                    .build();
        }

        // 取最近一年的最低位次
        PredictionRequest.HistoryEntry lastYear = sortedHistory.get(0);
        Integer lowestRank = lastYear.getLowestRank();

        if (lowestRank == null || lowestRank <= 0) {
            reasons.add("最近一年 (" + lastYear.getYear() + ") 无有效最低位次");
            return PredictionResponse.builder()
                    .planId(plan.getPlanId())
                    .label("待计算")
                    .confidence("低")
                    .reasons(reasons)
                    .modelVersion(MODEL_VERSION)
                    .build();
        }

        // 计算位次差和位次比率。高考位次数字越小表示排名越靠前。
        int rankDiff = lowestRank - candidateRank;  // 正数 = 考生位次优于最低录取位次
        double rankRatio = (double) rankDiff / lowestRank;

        // 判定标签
        String label;
        if (rankRatio > BAO_THRESHOLD) {
            label = PredictionResultLabel.BAO;
            reasons.add(String.format("考生位次(%,d)比去年最低位次(%,d)领先 %.1f%%，属于保底志愿",
                    candidateRank, lowestRank, rankRatio * 100));
        } else if (rankRatio >= WEN_LOWER) {
            label = PredictionResultLabel.WEN;
            reasons.add(String.format("考生位次(%,d)与去年最低位次(%,d)差距在 %.1f%%，属于稳妥志愿",
                    candidateRank, lowestRank, Math.abs(rankRatio) * 100));
        } else {
            label = PredictionResultLabel.CHONG;
            reasons.add(String.format("考生位次(%,d)比去年最低位次(%,d)落后 %.1f%%，属于冲刺志愿",
                    candidateRank, lowestRank, Math.abs(rankRatio) * 100));
        }

        // 计算概率: clamp(50 + rankRatio * 1000, 1, 99)
        int probability = (int) Math.round(50 + rankRatio * PROB_SCALE);
        probability = Math.max(1, Math.min(99, probability));

        // 预测位次范围: lowestRank ± 10%
        int predictedRankMin = (int) Math.round(lowestRank * (1 - RANK_RANGE_RATIO));
        int predictedRankMax = (int) Math.round(lowestRank * (1 + RANK_RANGE_RATIO));

        // 置信度: 基于历史数据年数
        int historyYears = (int) sortedHistory.stream()
                .map(PredictionRequest.HistoryEntry::getYear)
                .distinct()
                .count();
        String confidence;
        if (historyYears >= 3) {
            confidence = "高";
        } else if (historyYears >= 2) {
            confidence = "中";
        } else {
            confidence = "低";
        }
        reasons.add(String.format("基于 %d 年历史数据计算，置信度为%s", historyYears, confidence));

        if (lastYear.getPlanCount() != null) {
            reasons.add(String.format("去年该计划招生 %d 人", lastYear.getPlanCount()));
        }

        return PredictionResponse.builder()
                .planId(plan.getPlanId())
                .probability(probability)
                .label(label)
                .predictedRankMin(predictedRankMin)
                .predictedRankMax(predictedRankMax)
                .confidence(confidence)
                .reasons(reasons)
                .modelVersion(MODEL_VERSION)
                .build();
    }

    /**
     * 计算考生档案哈希值.
     *
     * <p>SHA-256(year + ":" + rank + ":" + sorted subjects joined by ",")</p>
     *
     * @param year 年份     招生年度
     * @param rank     考生位次
     * @param subjects 选考科目列表
     * @return 64位十六进制哈希字符串
     */
    public static String computeProfileHash(int year, int rank, List<String> subjects) {
        String sortedSubjects = (subjects == null || subjects.isEmpty())
                ? ""
                : subjects.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .sorted()
                        .collect(Collectors.joining(","));

        String input = year + ":" + rank + ":" + sortedSubjects;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    // --- Label constants (package-private) ---
    static final class PredictionResultLabel {
        static final String CHONG = "冲";
        static final String WEN = "稳";
        static final String BAO = "保";
    }
}
