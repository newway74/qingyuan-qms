package com.qms.modules.sampling.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 样品（条码可扫；检验样/留样/备样）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_sample")
public class Sample extends BaseEntity {

    private Long id;

    private String sampleNo;

    private Long samplingId;

    private Long skuId;

    /** INSPECTION/RETAIN/BACKUP */
    private String sampleType;

    private String batchNo;

    private LocalDate productionDate;

    private LocalDate expiryDate;

    /** 喷码核对 MATCH/MISMATCH */
    private String packageBatchCheck;

    private String packageBatchNote;

    /** PENDING_RECEIVE/IN_INSPECTION/RETAINING/DISPOSED */
    private String status;

    private LocalDateTime receivedAt;

    private Long receiverId;

    private Integer retainFlag;

    private String retainLocation;

    private LocalDate retainUntil;

    /** DESTROY/RETURN */
    private String disposeType;

    private String disposeRemark;

    private LocalDateTime disposedAt;

    @Version
    private Integer lockVersion;
}
