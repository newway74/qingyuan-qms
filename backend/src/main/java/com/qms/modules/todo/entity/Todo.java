package com.qms.modules.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一待办（非质量记录，无逻辑删除需求）。
 */
@Data
@TableName("qc_todo")
public class Todo implements Serializable {

    private Long id;

    /** 为空=角色待办 */
    private Long userId;

    private String roleCode;

    /** RECEIVE/INSPECT/REVIEW/DEFECT/APPROVAL/RECT/ALERT */
    private String todoType;

    private String bizType;

    private Long bizId;

    private String title;

    private String bizNo;

    private Integer priority;

    /** 0待办 1已处理 */
    private Integer status;

    private LocalDateTime deadline;

    private LocalDateTime handledAt;

    private Long handlerId;

    private Long tenantId;

    private LocalDateTime createdAt;
}
