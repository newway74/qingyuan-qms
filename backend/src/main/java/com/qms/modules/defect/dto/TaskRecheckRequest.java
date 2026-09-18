package com.qms.modules.defect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发起复检：基于已判定任务创建子任务（原任务/原报告不可变）。
 */
@Data
public class TaskRecheckRequest {

    @NotBlank(message = "复检原因不能为空")
    private String reason;
}
