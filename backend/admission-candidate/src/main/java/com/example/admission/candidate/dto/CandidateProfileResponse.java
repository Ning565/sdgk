package com.example.admission.candidate.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考生档案响应 DTO.
 *
 * <p>返回考生的完整档案信息，包含脱敏手机号用于前端展示。</p>
 */
@Data
@Builder
public class CandidateProfileResponse {

    /** 档案ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 脱敏手机号（如 "138****1234"） */
    private String mobileMasked;

    /** 高考年份 */
    private Integer year;

    /** 高考分数 */
    private Integer score;

    /** 省排名位次 */
    private Integer rank;

    /** 位次来源: AUTO / MANUAL */
    private String rankSource;

    /** 选科组合索引（0-19） */
    private Integer subjectComboIndex;

    /** 选科科目列表（3门） */
    private List<String> subjects;

    /** 学历层次 */
    private String educationLevel;

    /** 意向地域列表 */
    private List<String> preferredRegions;

    /** 意向专业列表 */
    private List<String> preferredMajors;

    /** 排除专业列表 */
    private List<String> excludedMajors;

    /** 学费上限 */
    private Integer tuitionMax;

    /** 学校性质偏好 */
    private String schoolNature;

    /** 是否接受中外合作办学 */
    private Boolean acceptJointProgram;

    /** 合作类型偏好列表 */
    private List<String> cooperationTypes;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
