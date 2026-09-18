package com.qms.modules.inspection.vo;

import com.qms.modules.inspection.entity.InspectionReport;
import com.qms.modules.inspection.entity.InspectionResult;
import com.qms.modules.inspection.entity.InspectionTask;
import com.qms.modules.standard.entity.StandardItem;
import com.qms.modules.standard.entity.StandardTemplate;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 检验任务工作台详情：任务 + 模板/检验项快照 + 结果 + 报告 + 可执行动作。
 */
@Data
public class TaskDetailVO {

    private TaskListVO task;

    private StandardTemplate template;

    private List<StandardItem> items;

    private List<InspectionResult> results;

    private InspectionReport report;

    /** 提交时系统综合判定快照（待复核前为建议） */
    private JudgeSummaryVO summary;

    /** 当前用户在当前状态可执行的动作 */
    private Set<String> allowedActions;

    @Data
    public static class JudgeSummaryVO {
        private Integer aFailCount;
        private Integer bFailCount;
        private Integer cFailCount;
        private Boolean vetoFail;
        private Boolean unqualified;
        private Boolean concessionPossible;
        private String suggestedConclusion;
    }
}
