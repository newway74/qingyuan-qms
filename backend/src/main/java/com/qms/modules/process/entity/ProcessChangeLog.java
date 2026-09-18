package com.qms.modules.process.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 流程发布变更记录（只增表：无更新/删除路径）。
 */
@Data
@TableName(value = "qc_process_change_log", autoResultMap = true)
public class ProcessChangeLog {

    private Long id;

    private Long processDefId;

    private String processCode;

    private Integer fromVersion;

    private Integer toVersion;

    /** {added, removed, changed:[{nodeCode, before, after}]} */
    @TableField(value = "change_diff", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> changeDiff;

    private Long changedBy;

    private LocalDateTime changedAt;

    private Long tenantId;
}
