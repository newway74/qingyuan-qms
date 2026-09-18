package com.qms.modules.standard.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 检验标准模板（一行一版本；DRAFT/PUBLISHED/ARCHIVED）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_standard_template")
public class StandardTemplate extends BaseEntity {

    private Long id;

    /** 模板业务编码，跨版本相同 */
    private String templateCode;

    private String templateName;

    private Long categoryId;

    /** 适用包装形态，NULL/空=全部 */
    private String packageForm;

    /** 新品引入档位：HIGH/MID/LOW，NULL=通用标准 */
    private String grade;

    /** 来源新品引入项目 */
    private Long npiProjectId;

    /** 国标/法规依据（GB/药典/NMPA 注册要求等） */
    private String regulationBasis;

    /** 市面竞品对标说明 */
    private String marketBenchmark;

    private Integer version;

    /** DRAFT/REVIEWING/PUBLISHED/ARCHIVED */
    private String status;

    /** A类允许不合格数 */
    private Integer maxAFail;
    /** B类允许不合格数 */
    private Integer maxBFail;
    /** C类允许不合格数 */
    private Integer maxCFail;

    /** 是否允许让步接收 1是 0否 */
    private Integer concessionAllowed;

    private String remark;

    private Long publishedBy;

    private LocalDateTime publishedAt;

    @Version
    private Integer lockVersion;
}
