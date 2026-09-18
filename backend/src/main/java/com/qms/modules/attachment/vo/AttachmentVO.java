package com.qms.modules.attachment.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 附件信息（只增记录，前端凭证列表复用）。
 */
@Data
public class AttachmentVO {

    private Long id;

    private String bizType;

    private Long bizId;

    private String fileName;

    private String fileExt;

    private Long fileSize;

    private String contentType;

    private Long uploadedBy;

    private LocalDateTime createdAt;
}
