package com.example.admission.volunteer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 移动志愿项排序请求.
 */
@Data
public class MoveItemRequest {

    @NotNull(message = "目标位置不能为空")
    @Min(value = 1, message = "目标位置最小为1")
    private Integer targetPosition;

    /** 乐观锁版本号 */
    @NotNull(message = "expectedVersion不能为空")
    private Integer expectedVersion;

    /** 幂等操作ID */
    private String clientOperationId;
}
