package com.qms.modules.inspection.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 检验结果批量保存。
 */
@Data
public class ResultBatchSaveRequest {

    @NotEmpty(message = "结果不能为空")
    @Valid
    private List<ResultItemInput> results;
}
