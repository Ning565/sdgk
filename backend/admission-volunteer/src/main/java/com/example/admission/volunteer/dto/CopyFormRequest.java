package com.example.admission.volunteer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 复制志愿表请求.
 */
@Data
public class CopyFormRequest {

    @NotBlank(message = "新志愿表名称不能为空")
    @Size(max = 50, message = "名称最长50字符")
    private String newName;

    /** 幂等操作ID */
    private String clientOperationId;
}
