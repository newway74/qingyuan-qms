package com.qms.modules.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 产品质量状态变更（受控/冻结/淘汰恢复），必须填写原因并全程留痕。
 */
@Data
public class ProductQualityStatusRequest {

    @NotBlank(message = "目标质量状态不能为空")
    private String qualityStatus;

    @NotBlank(message = "质量状态变更必须填写原因（合规留痕）")
    @Size(max = 500, message = "变更原因最长500")
    private String reason;

    /** 乐观锁版本（必填，防止并发覆盖） */
    private Integer lockVersion;
}
