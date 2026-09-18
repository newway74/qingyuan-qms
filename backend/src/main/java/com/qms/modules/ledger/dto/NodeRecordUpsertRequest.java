package com.qms.modules.ledger.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

/**
 * 商品流程节点记录更新请求（商品详情页内录入）。
 * 统一承载节点状态、完成日期、负责人、结论、关联供应商、备注与自定义字段值。
 */
@Data
public class NodeRecordUpsertRequest {

    /** NOT_STARTED 未开始 / IN_PROGRESS 进行中 / DONE 已完成 / REJECTED 不通过 */
    @NotBlank(message = "节点状态不能为空")
    @Size(max = 12)
    private String status;

    /** 完成日期（状态为已完成/不通过时必填） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate finishDate;

    /** 负责人姓名 */
    @Size(max = 64)
    private String ownerName;

    /** 节点结论（状态为已完成/不通过时必填） */
    @Size(max = 255)
    private String conclusion;

    /** 关联供应商 id（仅该节点定义允许关联供应商时生效） */
    private Long supplierId;

    @Size(max = 1000)
    private String remark;

    /**
     * 自定义字段值，key=字段编码（fieldCode）。
     * 文本/多行文本/日期/结论存字符串；下拉存选项字符串；附件字段存附件 id 字符串。
     */
    private Map<String, Object> fieldValues;
}
