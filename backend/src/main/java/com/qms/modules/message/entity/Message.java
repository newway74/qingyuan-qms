package com.qms.modules.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内消息（非质量记录，无逻辑删除需求）。
 */
@Data
@TableName("qc_message")
public class Message implements Serializable {

    private Long id;

    /** 为空=角色消息 */
    private Long receiverId;

    private String roleCode;

    /** TODO/ALERT/SYSTEM */
    private String msgType;

    private String title;

    private String content;

    private String bizType;

    private Long bizId;

    /** 0未读 1已读 */
    private Integer isRead;

    private LocalDateTime readAt;

    private Long tenantId;

    private LocalDateTime createdAt;
}
