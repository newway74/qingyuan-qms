package com.qms.modules.inspection.engine;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 单个检验项判定结果。
 */
@Data
@AllArgsConstructor
public class JudgeItemResult {

    /** PASS/FAIL/NONE（NONE=未录入无法判定） */
    private String judgement;

    private String message;

    /** 判定规则与阈值快照（落 judge_snapshot） */
    private Map<String, Object> snapshot;
}
