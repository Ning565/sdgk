package com.example.admission.volunteer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 添加志愿项请求.
 */
@Data
public class AddVolunteerItemRequest {

    @NotNull(message = "招生计划ID不能为空")
    private Long planId;

    /** 乐观锁版本号 */
    @NotNull(message = "expectedVersion不能为空")
    private Integer expectedVersion;

    /** 幂等操作ID（5分钟内去重） */
    private String clientOperationId;

    private Long schoolId;
    private String schoolName;
    private String schoolCode;
    private String majorName;
    private String majorCode;
    private String province;
    private String city;
    private String schoolType;
    private String enrollmentType;
    private BigDecimal probability;
    private String label;
    private Integer planCount;
    private BigDecimal tuition;
    private String subjectRequirementText;
    private String planStatus;
    private Integer lastYearMinRank;
    private Integer predictedRank;
}
