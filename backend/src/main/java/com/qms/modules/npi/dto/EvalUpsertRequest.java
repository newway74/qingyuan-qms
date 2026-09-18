package com.qms.modules.npi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 送样评估单新建/编辑（整单保存；提交评估走单独动作）。 */
@Data
public class EvalUpsertRequest {

    private Long id;

    @NotNull(message = "新品项目不能为空")
    private Long projectId;

    @NotNull(message = "送样供应商不能为空")
    private Long supplierId;

    private Integer roundNo = 1;

    private String sampleDesc;

    private LocalDateTime receivedAt;

    /** PENDING/QUALIFIED/UNQUALIFIED */
    private String qualityConclusion;

    private String remark;

    @Valid
    private List<EvalItemInput> items;
}
