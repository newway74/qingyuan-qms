package com.qms.modules.defect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 不合格缺陷明细：与不合格检验结果一一对应，随单生成，不可物理删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_defect_item")
public class DefectItem extends BaseEntity {

    private Long id;

    private Long caseId;

    private Long resultId;

    /** A/B/C */
    private String defectLevel;

    private String itemName;

    private String failDesc;
}
