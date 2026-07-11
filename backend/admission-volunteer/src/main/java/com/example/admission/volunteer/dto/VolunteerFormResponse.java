package com.example.admission.volunteer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 志愿表摘要响应.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolunteerFormResponse {

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
}
