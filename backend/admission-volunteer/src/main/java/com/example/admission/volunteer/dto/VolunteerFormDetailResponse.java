package com.example.admission.volunteer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

/**
 * 志愿表详情响应，包含 items 列表和冲稳保统计.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolunteerFormDetailResponse {

    private Long id;
    private Long userId;
    private Integer year;
    private String name;
    private Integer version;
    private Integer itemCount;
    private Integer maxItems;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 志愿项列表 */
    private List<VolunteerItemResponse> items;

    /** 统计: 冲 / 稳 / 保 个数 */
    private LabelStats stats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolunteerItemResponse {
        private Long id;
        private Long planId;
        private Long schoolId;
        private String schoolCode;
        private String schoolName;
        private String majorName;
        private String majorCode;
        private String province;
        private String city;
        private String schoolType;
        private String enrollmentType;
        private java.math.BigDecimal probability;
        private String label;
        private Integer planCount;
        private BigDecimal tuition;
        private String subjectRequirementText;
        private String planStatus;
        private Integer lastYearMinRank;
        private Integer twoYearMinRank;
        private Integer threeYearMinRank;
        private Integer predictedRank;
        private Integer sortOrder;
        private String note;
        private LocalDateTime addedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabelStats {
        private int chongCount;
        private int wenCount;
        private int baoCount;
    }
}
