package com.qms.modules.npi.vo;

import com.qms.modules.npi.entity.ExternalTest;
import com.qms.modules.npi.entity.FactoryAudit;
import com.qms.modules.npi.entity.NpiEval;
import com.qms.modules.npi.entity.NpiProject;
import com.qms.modules.npi.entity.NpiTimeline;
import com.qms.modules.standard.entity.StandardTemplate;
import com.qms.modules.standard.entity.StdReview;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 新品项目详情聚合：头信息 + 三档标准及评审 + 送样评估 + 验厂 + 外检 + 时间线 + 闸门。 */
@Data
public class ProjectDetailVO {

    private NpiProject project;

    private String categoryName;

    private String chosenSupplierName;

    private String productName;

    private String initiatorName;

    /** grade(HIGH/MID/LOW) -> 该档位最新版本模板 */
    private Map<String, StandardTemplate> gradeTemplates;

    /** 模板id -> 评审记录（升序） */
    private Map<Long, List<StdReview>> templateReviews;

    private List<EvalDetailVO> evals;

    private List<AuditDetailVO> audits;

    private List<ExternalTest> extTests;

    private List<NpiTimeline> timeline;

    /** 当前状态允许的动作（按当前用户角色过滤，前端按钮渲染） */
    private Set<String> allowedActions;

    /** 下一阶段闸门检查（前端可提示，动作执行时后端强校验） */
    private List<GateStatus> gates;
}
