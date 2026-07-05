package com.example.admission.volunteercheck.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 检查结果响应.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckResultResponse {

    /** 检查运行ID */
    private Long checkRunId;

    /** 问题总数 */
    private int totalIssues;

    /** 错误数 */
    private int errorCount;

    /** 警告数 */
    private int warningCount;

    /** 信息数 */
    private int infoCount;

    /** 问题列表 */
    private List<CheckIssueResponse> issues;

    /** 检查时间 */
    private LocalDateTime checkTime;
}
