package com.qms.modules.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 商品台账新增/编辑请求。
 * id 为空表示建档；templateId 为空时建档自动使用系统启用的默认模板。
 */
@Data
public class GoodsUpsertRequest {

    private Long id;

    @NotBlank(message = "SKU 不能为空")
    @Size(max = 50, message = "SKU 最长 50 个字符")
    private String sku;

    @NotBlank(message = "产品通用名不能为空")
    @Size(max = 200, message = "产品通用名最长 200 个字符")
    private String commonName;

    @Size(max = 150, message = "规格最长 150 个字符")
    private String spec;

    @Size(max = 200, message = "生产企业最长 200 个字符")
    private String manufacturer;

    @Size(max = 100)
    private String approvalNo;

    @Size(max = 20, message = "UPC 最长 20 个字符")
    private String upc;

    @Size(max = 100)
    private String brand;

    private Long categoryL1Id;

    private Long categoryL2Id;

    private LocalDate meetingDate;

    /** YES / PENDING / NO，默认 PENDING */
    private String cooperateResult;

    private LocalDate launchDate;

    /** 指定流程模板（版本行 id）；为空则用系统默认模板 */
    private Long templateId;

    /** 可选关联新品引入项目 */
    private Long npiProjectId;

    @Size(max = 500)
    private String remark;
}
