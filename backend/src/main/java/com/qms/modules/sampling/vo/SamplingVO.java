package com.qms.modules.sampling.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 抽样单视图（含产品冗余信息与样品清单）。
 */
@Data
public class SamplingVO {

    private Long id;
    private String samplingNo;
    private String source;
    private Long skuId;
    private String skuCode;
    private String productName;
    private Long productId;
    private String spec;
    private String packageForm;
    private String categoryName;
    private String brand;
    private String supplierName;
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String storageCondition;
    private BigDecimal sampleQuantity;
    private String quantityUnit;
    private String samplingLocation;
    private Long samplerId;
    private String samplerName;
    private LocalDateTime sampledAt;
    private String remark;
    private String cancelReason;
    private Integer shelfLifeDaysSnapshot;
    private Integer expiryDiffDays;
    private String status;
    private Long processDefId;
    private Integer lockVersion;
    private LocalDateTime createdAt;

    private List<SampleVO> samples;
}
