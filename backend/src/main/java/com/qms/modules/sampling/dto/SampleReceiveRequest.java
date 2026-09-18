package com.qms.modules.sampling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 收样登记：喷码核对 + 一次生成检验样/留样/备样 + 自动建检验任务。
 */
@Data
public class SampleReceiveRequest {

    @NotNull(message = "抽样单id不能为空")
    private Long samplingId;

    /** 喷码核对 MATCH/MISMATCH */
    @NotBlank(message = "请完成包装喷码批号与生产日期核对")
    private String packageBatchCheck;

    /** MISMATCH 时强制填写差异说明 */
    private String packageBatchNote;

    /** 喷码实际生产日期（不传默认抽样单） */
    private LocalDate productionDate;

    /** 喷码实际保质期至（不传默认抽样单） */
    private LocalDate expiryDate;

    /** 检验样数量，默认1；每个检验样自动生成一个检验任务 */
    @Min(value = 1, message = "至少1个检验样")
    private Integer inspectionCount = 1;

    /** 是否同时生成留样 */
    private Boolean retainFlag = false;

    private String retainLocation;

    private LocalDate retainUntil;

    /** 是否同时生成备样 */
    private Boolean backupFlag = false;
}
