package com.qms.modules.sampling.vo;

import com.qms.modules.sampling.entity.Batch;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 批次台账行（含 SKU/产品冗余与效期计算）。
 */
@Data
public class BatchVO {

    private Batch batch;

    private String skuCode;

    private String spec;

    private String productName;

    /** 距效期天数（负数=已过期） */
    private Long daysToExpiry;

    /** 最近收样时间（台账追溯用） */
    private LocalDate latestReceiveDate;

    private LocalDateTime latestCheckAt;
}
