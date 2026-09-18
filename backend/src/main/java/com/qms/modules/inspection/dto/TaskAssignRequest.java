package com.qms.modules.inspection.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 任务分配：指定检验员与复核判定人。
 */
@Data
public class TaskAssignRequest {

    @NotNull(message = "任务id不能为空")
    private Long taskId;

    @NotNull(message = "检验员不能为空")
    private Long inspectorId;

    @NotNull(message = "复核判定人不能为空")
    private Long reviewerId;
}
