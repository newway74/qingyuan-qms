package com.qms.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志：只增不删不修改（不继承 BaseEntity，无 deleted/updated 字段）
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog {

    @TableId
    private Long id;

    private String traceId;

    private Long userId;

    private String username;

    private String module;

    /** CREATE/UPDATE/DELETE_LOGIC/EXPORT/AUDIT/LOGIN 等 */
    private String action;

    private String bizType;

    private Long bizId;

    private String beforeValue;

    private String afterValue;

    private String ip;

    private String userAgent;

    /** 1成功 0失败 */
    private Integer result;

    private String errorMsg;

    private Long costMs;

    private LocalDateTime createdAt;
}
