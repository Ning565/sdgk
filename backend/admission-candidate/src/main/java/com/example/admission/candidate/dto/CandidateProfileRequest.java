package com.example.admission.candidate.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * 创建/更新考生档案请求 DTO.
 *
 * <p>考生填报个人档案时提交的数据，包含分数、位次、选科、偏好等信息。
 * 服务端会进行二次校验：subjects 必须为3门、rank>0、分数在年度有效范围。</p>
 */
@Data
public class CandidateProfileRequest {

    /** 高考年份（必填） */
    @NotNull(message = "高考年份不能为空")
    private Integer year;

    /** 高考分数（必填，需在年度有效范围内） */
    @NotNull(message = "高考分数不能为空")
    private Integer score;

    /** 省排名位次（必填，>0） */
    @NotNull(message = "位次不能为空")
    @Positive(message = "位次必须为正数")
    private Integer rank;

    /** 位次来源: AUTO(自动匹配一分一段表) / MANUAL(手动填写) */
    @NotBlank(message = "位次来源不能为空")
    private String rankSource;

    /** 选科科目列表（必填，3门） */
    @NotNull(message = "选科科目不能为空")
    @Size(min = 3, max = 3, message = "选科科目必须为3门")
    private List<String> subjects;

    /** 学历层次: UNDERGRADUATE(本科) / VOCATIONAL(专科) / UNLIMITED */
    private String educationLevel;

    /** 意向地域列表 */
    private List<String> preferredRegions;

    /** 意向专业列表 */
    private List<String> preferredMajors;

    /** 排除专业列表 */
    private List<String> excludedMajors;

    /** 学费上限（元/年），null 表示不限 */
    @PositiveOrZero(message = "学费上限不能为负数")
    private Integer tuitionMax;

    /** 学校性质偏好: PUBLIC(公办) / PRIVATE(民办) / UNLIMITED(不限) */
    private String schoolNature;

    /** 是否接受中外合作办学 */
    private Boolean acceptJointProgram;

    /** 合作类型偏好列表 */
    private List<String> cooperationTypes;

    /** 备注说明 */
    @Size(max = 500, message = "备注不能超过500字")
    private String remark;

    // --- Constants ---

    public static final String RANK_SOURCE_AUTO = "AUTO";
    public static final String RANK_SOURCE_MANUAL = "MANUAL";

    public static final String EDU_UNDERGRADUATE = "UNDERGRADUATE";
    public static final String EDU_VOCATIONAL = "VOCATIONAL";
    public static final String EDU_UNLIMITED = "UNLIMITED";

    public static final String SCHOOL_PUBLIC = "PUBLIC";
    public static final String SCHOOL_PRIVATE = "PRIVATE";
    public static final String SCHOOL_UNLIMITED = "UNLIMITED";
}
