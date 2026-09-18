package com.qms.modules.sampling.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 样品视图（列表/详情/扫码共用）。
 */
@Data
public class SampleVO {

    private Long id;
    private String sampleNo;
    private Long samplingId;
    private String samplingNo;
    private Long skuId;
    private String skuCode;
    private String productName;
    private String spec;
    private String packageForm;
    private String sampleType;
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String packageBatchCheck;
    private String packageBatchNote;
    private String status;
    private LocalDateTime receivedAt;
    private Long receiverId;
    private String receiverName;
    private Integer retainFlag;
    private String retainLocation;
    private LocalDate retainUntil;
    private String disposeType;
    private String disposeRemark;
    private LocalDateTime disposedAt;

    /** 关联检验任务 */
    private Long taskId;
    private String taskNo;
    private String taskStatus;

    private LocalDateTime createdAt;
}
