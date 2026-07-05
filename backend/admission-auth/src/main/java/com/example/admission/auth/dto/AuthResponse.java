package com.example.admission.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证成功后的响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * 认证用户的 ID。
     */
    private Long userId;

    /**
     * 用户展示昵称。
     */
    private String nickname;

    /**
     * 用户是否仍需完善考生档案。
     * 用户尚未关联考生档案时返回 {@code true}。
     */
    private Boolean needProfile;
}
