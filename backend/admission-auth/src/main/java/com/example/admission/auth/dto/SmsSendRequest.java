package com.example.admission.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发送短信验证码请求 DTO。
 * <p>
 * 验证手机号符合中国大陆格式（移动/联通/电信：1[3-9]XXXXXXXXX）。
 * </p>
 */
@Data
public class SmsSendRequest {

    /**
     * 手机号，须符合中国大陆手机号格式。
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    /**
     * 用户是否已同意服务条款。
     */
    @NotNull(message = "请先同意服务条款")
    private Boolean agreeTerms;
}
