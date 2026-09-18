package com.qms.modules.inspection.engine;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 综合判定结果：缺陷计数 + 建议结论 + 让步可能性。
 */
@Data
public class JudgeSummary {

    private int aFailCount;
    private int bFailCount;
    private int cFailCount;

    /** 存在 A 类一票否决项不合格 */
    private boolean vetoFail;

    /** 按模板限值综合为不合格 */
    private boolean unqualified;

    /** 是否可提让步接收（模板允许且无A类，且存在B/C不合格） */
    private boolean concessionPossible;

    /** 系统建议结论 QUALIFIED/UNQUALIFIED */
    private String suggestedConclusion;

    /** 不合格项明细（分组、项名、等级、实测、是否否决） */
    private List<Map<String, Object>> failures = new ArrayList<>();
}
