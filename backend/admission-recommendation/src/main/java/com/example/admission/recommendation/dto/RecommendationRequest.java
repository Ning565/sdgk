package com.example.admission.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 推荐搜索请求体.
 *
 * <p>包含所有筛选条件，对应 POST /api/v1/recommendations/search 的请求 JSON。</p>
 */
@Data
public class RecommendationRequest {

    // ==================== 必填基础参数 ====================

    /** 招生年度，必填 */
    private Integer year;

    /** 教育层次: UNDERGRADUATE(本科) / SPECIALIZED(专科) */
    private String educationLevel;

    /** 考生分数，用于专科模型解释；核心排序优先使用 candidateRank */
    private Integer score;

    /** 考生全省位次，用于实时计算专科模型概率 */
    private Integer rank;

    /** 考生选科，如 ["物理","化学","生物"] */
    private List<String> subjects;

    // ==================== 关键词 ====================

    /** 院校或专业关键词（模糊搜索 school_name 和 major_name） */
    private String keyword;

    // ==================== 院校筛选 ====================

    /** 省份列表（IN 查询） */
    private List<String> province;

    /** 城市列表（IN 查询） */
    private List<String> city;

    /** 院校性质: 公办/民办/独立学院 等 */
    private List<String> schoolType;

    /** 院校标签: 双一流/985/211 等 */
    private List<String> schoolTag;

    // ==================== 专业筛选 ====================

    /** 专业门类: 工学/理学/文学 等 */
    private List<String> majorCategory;

    /** 专业类: 计算机类/电子信息类 等 */
    private List<String> majorSubcategory;

    /** 招生类型: 普通类/中外合作/校企合作 等 */
    private List<String> enrollmentType;

    /** 校区代码 */
    private List<String> campusCode;

    // ==================== 排除筛选 ====================

    /** 排除的专业门类 */
    private List<String> excludeMajorCategory;

    /** 排除的专业类 */
    private List<String> excludeMajorSubcategory;

    /** 是否不接受中外合作: true = 排除中外合作 */
    private Boolean excludeSinoForeign;

    /** 是否不接受校企合作: true = 排除校企合作 */
    private Boolean excludeSchoolEnterprise;

    // ==================== 数值范围筛选 ====================

    /** 学费最小值 */
    private Integer tuitionMin;

    /** 学费最大值 */
    private Integer tuitionMax;

    /** 招生人数最小值 */
    private Integer planCountMin;

    /** 招生人数最大值 */
    private Integer planCountMax;

    /** 上一年最低位次上限（只推荐位次 <= 此值的计划） */
    private Integer minRankMax;

    // ==================== 概率筛选 ====================

    /** 录取概率最小值（0-100） */
    @Min(0)
    @Max(100)
    private BigDecimal probabilityMin;

    /** 录取概率最大值（0-100） */
    @Min(0)
    @Max(100)
    private BigDecimal probabilityMax;

    /** 冲稳保标签筛选: 冲/稳/保 */
    private String label;

    // ==================== 组合推荐参数 ====================

    /** 组合推荐返回数量；为空时使用默认推荐数量 */
    @Min(1)
    @Max(10000)
    private Integer recommendationCount;

    /** 冲刺推荐比例，0-1；为空时使用默认比例 */
    private BigDecimal rushRatio;

    /** 稳妥推荐比例，0-1；为空时使用默认比例 */
    private BigDecimal stableRatio;

    /** 保底推荐比例，0-1；为空时使用默认比例 */
    private BigDecimal safeRatio;

    /** 冲刺推荐最低概率；默认 20 */
    @Min(0)
    @Max(100)
    private BigDecimal rushProbabilityMin;

    /** 查看完整候选池；true 时冲稳保比例只作为统计含义，不裁剪展示结果 */
    private Boolean includeAllCandidates;

    // ==================== 排序与分页 ====================

    /** 排序字段: probability / rankDiff / lastYearMinRank / planCount / tuition */
    private String sortBy;

    /** 排序方向: asc / desc */
    private String sortDir;

    /** 页码，从1开始 */
    @Min(1)
    private Integer pageNo = 1;

    /** 每页数量 */
    @Min(1)
    @Max(10000)
    private Integer pageSize = 20;

    // ==================== 选科参数 ====================

    /**
     * 考生选科组合索引 (0-19).
     * <p>用于位图匹配过滤。如果为 null，则不限制选科。</p>
     */
    private Integer subjectComboIndex;
}
