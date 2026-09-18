package com.qms.modules.attachment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 附件元数据（只增，不继承 BaseEntity、不逻辑删除）。
 */
@Data
@TableName("qc_attachment")
public class Attachment implements Serializable {

    private Long id;

    private String bizType;

    private Long bizId;

    private String fileName;

    private String fileExt;

    private Long fileSize;

    private String contentType;

    /** LOCAL / MINIO */
    private String storageType;

    private String bucket;

    private String objectKey;

    private String sha256;

    private Long uploadedBy;

    private Long tenantId;

    private LocalDateTime createdAt;
}
