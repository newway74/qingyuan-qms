package com.qms.modules.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 电子签名记录（只增）。
 */
@Data
@TableName("qc_signature_record")
public class SignatureRecord implements Serializable {

    private Long id;

    /** TASK_SUBMIT/TASK_REVIEW/REPORT/DEFECT_APPROVAL */
    private String bizType;

    private Long bizId;

    private Long userId;

    private String username;

    private String signPurpose;

    /** 单据快照 SHA256 */
    private String snapshotHash;

    private String ip;

    private String userAgent;

    private LocalDateTime signedAt;
}
