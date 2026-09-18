package com.qms.modules.process.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 品控流程定义（一行一版本；DRAFT/PUBLISHED/ARCHIVED）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_process_def")
public class ProcessDef extends BaseEntity {

    private Long id;

    private String processCode;

    private String processName;

    private Integer version;

    /** DRAFT/PUBLISHED/ARCHIVED */
    private String status;

    private String remark;

    private Long publishedBy;

    private LocalDateTime publishedAt;

    @Version
    private Integer lockVersion;
}
