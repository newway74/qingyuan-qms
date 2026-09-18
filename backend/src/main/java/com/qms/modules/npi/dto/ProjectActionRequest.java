package com.qms.modules.npi.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 项目阶段推进通用请求。
 * APPROVE_STD：grade + templateId 必填（选档）；
 * FIX_SUPPLIER：supplierId 必填（可由 evalId 推导）；
 * RELEASE：productId 可选（关联已建产品档案）。
 */
@Data
public class ProjectActionRequest {

    @Size(max = 500)
    private String comment;

    /** HIGH/MID/LOW */
    private String grade;

    private Long templateId;

    private Long supplierId;

    private Long evalId;

    private Long productId;
}
