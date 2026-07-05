package com.example.admission.volunteer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新志愿项备注请求.
 */
@Data
public class UpdateNoteRequest {

    @Size(max = 200, message = "备注最长200字符")
    private String note;

    /** 乐观锁版本号 */
    @NotNull(message = "expectedVersion不能为空")
    private Integer expectedVersion;

    /** 幂等操作ID */
    private String clientOperationId;
}
