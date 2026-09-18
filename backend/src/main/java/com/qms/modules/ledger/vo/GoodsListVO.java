package com.qms.modules.ledger.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商品台账列表行（含品类名称等展示字段）。
 */
@Data
public class GoodsListVO {

    private Long id;
    private String sku;
    private String commonName;
    private String spec;
    private String manufacturer;
    private String approvalNo;
    private String upc;
    private String brand;
    private Long categoryL1Id;
    private Long categoryL2Id;
    private String categoryL1Name;
    private String categoryL2Name;
    private LocalDate meetingDate;
    private String cooperateResult;
    private LocalDate launchDate;
    private String currentNodeCode;
    private String currentNodeName;
    private Long templateId;
    private Integer templateVersion;
    private Long npiProjectId;
    private String dataSource;
    private String remark;

    /** 资料重点缺口数（缺失 + 待确认） */
    private Long gapCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
