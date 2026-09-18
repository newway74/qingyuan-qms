package com.qms.modules.npi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qms.common.exception.BizException;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.ResultCode;
import com.qms.common.utils.SecurityUtils;
import com.qms.framework.audit.AuditLog;
import com.qms.framework.biz.BizNoGenerator;
import com.qms.framework.statemachine.StateMachineEngine;
import com.qms.modules.masterdata.entity.Category;
import com.qms.modules.masterdata.entity.Product;
import com.qms.modules.masterdata.entity.Supplier;
import com.qms.modules.masterdata.mapper.CategoryMapper;
import com.qms.modules.masterdata.mapper.ProductMapper;
import com.qms.modules.masterdata.mapper.SupplierMapper;
import com.qms.modules.npi.dto.ProjectActionRequest;
import com.qms.modules.npi.dto.ProjectUpsertRequest;
import com.qms.modules.npi.entity.ExternalTest;
import com.qms.modules.npi.entity.FactoryAudit;
import com.qms.modules.npi.entity.FactoryAuditItem;
import com.qms.modules.npi.entity.NpiEval;
import com.qms.modules.npi.entity.NpiEvalItem;
import com.qms.modules.npi.entity.NpiProject;
import com.qms.modules.npi.entity.NpiTimeline;
import com.qms.modules.npi.mapper.ExternalTestMapper;
import com.qms.modules.npi.mapper.FactoryAuditItemMapper;
import com.qms.modules.npi.mapper.FactoryAuditMapper;
import com.qms.modules.npi.mapper.NpiEvalItemMapper;
import com.qms.modules.npi.mapper.NpiEvalMapper;
import com.qms.modules.npi.mapper.NpiProjectMapper;
import com.qms.modules.npi.mapper.NpiTimelineMapper;
import com.qms.modules.npi.vo.AuditDetailVO;
import com.qms.modules.npi.vo.EvalDetailVO;
import com.qms.modules.npi.vo.GateStatus;
import com.qms.modules.npi.vo.ProjectDetailVO;
import com.qms.modules.npi.vo.ProjectListVO;
import com.qms.modules.standard.entity.StdReview;
import com.qms.modules.standard.entity.StandardTemplate;
import com.qms.modules.standard.service.StandardTemplateService;
import com.qms.modules.system.entity.SysUser;
import com.qms.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 新品引入项目服务：阶段推进（状态机强校验）、闸门检查与详情聚合。
 */
@Service
@RequiredArgsConstructor
public class NpiProjectService {

    public static final String BIZ_TYPE = "qc_npi_project";

    private static final Set<String> GRADES = Set.of("HIGH", "MID", "LOW");
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "DRAFT", "STD_REVIEW", "SOURCING", "AUDIT", "PRODUCING", "EXT_TEST", "LISTING_REVIEW");

    private final NpiProjectMapper projectMapper;
    private final NpiTimelineMapper timelineMapper;
    private final NpiEvalMapper evalMapper;
    private final NpiEvalItemMapper evalItemMapper;
    private final FactoryAuditMapper auditMapper;
    private final FactoryAuditItemMapper auditItemMapper;
    private final ExternalTestMapper extTestMapper;
    private final CategoryMapper categoryMapper;
    private final SupplierMapper supplierMapper;
    private final ProductMapper productMapper;
    private final SysUserMapper userMapper;
    private final StandardTemplateService standardService;
    private final StateMachineEngine stateMachine;
    private final BizNoGenerator bizNoGenerator;

    // ---------------- 查询 ----------------

    public PageResult<ProjectListVO> page(PageRequest request, String status, String projectName) {
        LambdaQueryWrapper<NpiProject> wrapper = new LambdaQueryWrapper<NpiProject>()
                .eq(status != null && !status.isBlank(), NpiProject::getStatus, status)
                .like(projectName != null && !projectName.isBlank(), NpiProject::getProjectName, projectName)
                .orderByDesc(NpiProject::getId);
        Page<NpiProject> page = projectMapper.selectPage(
                new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        List<ProjectListVO> rows = page.getRecords().stream().map(p -> {
            ProjectListVO vo = new ProjectListVO();
            vo.setProject(p);
            Category c = categoryMapper.selectById(p.getCategoryId());
            vo.setCategoryName(c == null ? null : c.getName());
            if (p.getChosenSupplierId() != null) {
                Supplier s = supplierMapper.selectById(p.getChosenSupplierId());
                vo.setChosenSupplierName(s == null ? null : s.getSupplierName());
            }
            if (p.getProductId() != null) {
                Product pr = productMapper.selectById(p.getProductId());
                vo.setProductName(pr == null ? null : pr.getProductName());
            }
            if (p.getInitiatorId() != null) {
                SysUser u = userMapper.selectById(p.getInitiatorId());
                vo.setInitiatorName(u == null ? null : u.getRealName());
            }
            return vo;
        }).toList();
        return PageResult.of(page, rows);
    }

    public ProjectDetailVO detail(Long id) {
        NpiProject project = getRequired(id);
        ProjectDetailVO vo = new ProjectDetailVO();
        vo.setProject(project);

        Category category = categoryMapper.selectById(project.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getName());
        if (project.getChosenSupplierId() != null) {
            Supplier supplier = supplierMapper.selectById(project.getChosenSupplierId());
            vo.setChosenSupplierName(supplier == null ? null : supplier.getSupplierName());
        }
        if (project.getProductId() != null) {
            Product product = productMapper.selectById(project.getProductId());
            vo.setProductName(product == null ? null : product.getProductName());
        }
        if (project.getInitiatorId() != null) {
            SysUser initiator = userMapper.selectById(project.getInitiatorId());
            vo.setInitiatorName(initiator == null ? null : initiator.getRealName());
        }

        // 三档标准（各档最新版本）+ 评审记录
        Map<String, StandardTemplate> gradeTemplates = new LinkedHashMap<>();
        Map<Long, List<StdReview>> reviewMap = new HashMap<>();
        for (StandardTemplate t : standardService.listByProject(id)) {
            gradeTemplates.putIfAbsent(t.getGrade(), t);
            reviewMap.put(t.getId(), standardService.reviews(t.getId()));
        }
        vo.setGradeTemplates(gradeTemplates);
        vo.setTemplateReviews(reviewMap);

        // 送样评估（含评分项、供应商名）
        List<EvalDetailVO> evalVOs = new ArrayList<>();
        for (NpiEval eval : listEvals(id)) {
            EvalDetailVO evo = new EvalDetailVO();
            evo.setEval(eval);
            Supplier s = supplierMapper.selectById(eval.getSupplierId());
            evo.setSupplierName(s == null ? null : s.getSupplierName());
            evo.setItems(evalItemMapper.selectList(new LambdaQueryWrapper<NpiEvalItem>()
                    .eq(NpiEvalItem::getEvalId, eval.getId())
                    .orderByAsc(NpiEvalItem::getId)));
            evalVOs.add(evo);
        }
        vo.setEvals(evalVOs);

        // 验厂（含检查项）
        List<AuditDetailVO> auditVOs = new ArrayList<>();
        for (FactoryAudit audit : auditMapper.selectList(new LambdaQueryWrapper<FactoryAudit>()
                .eq(FactoryAudit::getProjectId, id).orderByDesc(FactoryAudit::getId))) {
            AuditDetailVO avo = new AuditDetailVO();
            avo.setAudit(audit);
            Supplier s = supplierMapper.selectById(audit.getSupplierId());
            avo.setSupplierName(s == null ? null : s.getSupplierName());
            avo.setProjectName(project.getProjectName());
            avo.setItems(auditItemMapper.selectList(new LambdaQueryWrapper<FactoryAuditItem>()
                    .eq(FactoryAuditItem::getAuditId, audit.getId())
                    .orderByAsc(FactoryAuditItem::getSort)
                    .orderByAsc(FactoryAuditItem::getId)));
            auditVOs.add(avo);
        }
        vo.setAudits(auditVOs);

        vo.setExtTests(extTestMapper.selectList(new LambdaQueryWrapper<ExternalTest>()
                .eq(ExternalTest::getProjectId, id).orderByDesc(ExternalTest::getId)));

        vo.setTimeline(timelineMapper.selectList(new LambdaQueryWrapper<NpiTimeline>()
                .eq(NpiTimeline::getProjectId, id).orderByAsc(NpiTimeline::getId)));

        vo.setAllowedActions(stateMachine.allowedActions(
                BIZ_TYPE, project.getStatus(), currentRoles()));
        vo.setGates(buildGates(project));
        return vo;
    }

    // ---------------- 立项/编辑 ----------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "新品项目", action = "CREATE", bizType = "qc_npi_project")
    public Long create(ProjectUpsertRequest request) {
        if (categoryMapper.selectById(request.getCategoryId()) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "拟引入品类不存在");
        }
        NpiProject project = new NpiProject();
        project.setProjectNo(bizNoGenerator.next("NP"));
        apply(project, request);
        project.setStatus("DRAFT");
        project.setInitiatorId(SecurityUtils.getCurrentUserId());
        projectMapper.insert(project);
        addTimeline(project.getId(), "DRAFT", "CREATE", "新品会立项", request.getBackground());
        return project.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "新品项目", action = "UPDATE", bizType = "qc_npi_project", bizIdExpr = "#request.id")
    public void update(ProjectUpsertRequest request) {
        NpiProject project = getRequired(request.getId());
        if (!"DRAFT".equals(project.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "仅立项草稿阶段允许修改项目信息");
        }
        if (categoryMapper.selectById(request.getCategoryId()) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "拟引入品类不存在");
        }
        apply(project, request);
        projectMapper.updateById(project);
    }

    // ---------------- 阶段推进 ----------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "新品项目", action = "STAGE", bizType = "qc_npi_project", bizIdExpr = "#id")
    public void act(Long id, String action, ProjectActionRequest req) {
        NpiProject project = getRequired(id);
        String next = stateMachine.nextState(BIZ_TYPE, project.getStatus(), action, currentRoles());
        switch (action) {
            case "SUBMIT_STD" -> doSubmitStd(project);
            case "REJECT_STD" -> addTimeline(id, "STD_REVIEW", action, "标准评审驳回，重新起草", req.getComment());
            case "APPROVE_STD" -> doApproveStd(project, req);
            case "FIX_SUPPLIER" -> doFixSupplier(project, req);
            case "AUDIT_PASS" -> doAuditResult(project, true);
            case "AUDIT_FAIL" -> doAuditResult(project, false);
            case "SEND_EXT" -> addTimeline(id, "EXT_TEST", action, "量产完成，安排送检", req.getComment());
            case "TEST_PASS" -> {
                requirePassedExtTest(project);
                project.setExtTestPassedAt(LocalDateTime.now());
                addTimeline(id, "LISTING_REVIEW", action, "国家检测机构检测合格", req.getComment());
            }
            case "TEST_FAIL" -> addTimeline(id, "PRODUCING", action, "外检不合格，退回整改重产", req.getComment());
            case "RELEASE" -> doRelease(project, req);
            case "TERMINATE" -> project.setTerminateReason(req.getComment());
            default -> throw new BizException(ResultCode.PARAM_INVALID, "未知动作：" + action);
        }
        project.setStatus(next);
        if (projectMapper.updateById(project) == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }
    }

    private void doSubmitStd(NpiProject project) {
        long gradeCount = standardService.listByProject(project.getId()).stream()
                .map(StandardTemplate::getGrade).filter(GRADES::contains).distinct().count();
        if (gradeCount < 3) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID,
                    "请先完成高/中/低三档标准起草（当前 " + gradeCount + " 档），再提交评审");
        }
        project.setStdSubmittedAt(LocalDateTime.now());
        addTimeline(project.getId(), "STD_REVIEW", "SUBMIT_STD",
                "三档标准起草完成，提交采购/老板评审", null);
    }

    private void doApproveStd(NpiProject project, ProjectActionRequest req) {
        if (req.getTemplateId() == null || req.getGrade() == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "评审通过时必须选定寻源档位与对应标准");
        }
        String grade = req.getGrade().trim().toUpperCase();
        if (!GRADES.contains(grade)) {
            throw new BizException(ResultCode.PARAM_INVALID, "档位仅支持 HIGH/MID/LOW");
        }
        StandardTemplate chosen = standardService.listByProject(project.getId()).stream()
                .filter(t -> grade.equals(t.getGrade()) && "PUBLISHED".equals(t.getStatus()))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.BIZ_STATE_INVALID,
                        "所选" + gradeLabel(grade) + "标准尚未评审发布，不能作为寻源标准"));
        if (!chosen.getId().equals(req.getTemplateId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "选定标准与档位不匹配");
        }
        project.setTargetGrade(grade);
        project.setSelectedTemplateId(chosen.getId());
        project.setStdApprovedAt(LocalDateTime.now());
        addTimeline(project.getId(), "SOURCING", "APPROVE_STD",
                "评审共识达成，采购按" + gradeLabel(grade) + "标准寻源", req.getComment());
    }

    private void doFixSupplier(NpiProject project, ProjectActionRequest req) {
        Long supplierId = req.getSupplierId();
        if (supplierId == null && req.getEvalId() != null) {
            NpiEval eval = evalMapper.selectById(req.getEvalId());
            if (eval != null) {
                supplierId = eval.getSupplierId();
            }
        }
        if (supplierId == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "请选择定点供应商");
        }
        final Long fixedSupplierId = supplierId;
        NpiEval chosen = evalMapper.selectOne(new LambdaQueryWrapper<NpiEval>()
                .eq(NpiEval::getProjectId, project.getId())
                .eq(NpiEval::getSupplierId, fixedSupplierId)
                .eq(NpiEval::getStatus, "SUBMITTED")
                .orderByDesc(NpiEval::getTotalScore).last("LIMIT 1"));
        if (chosen == null) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID,
                    "该供应商尚无已提交的送样评估结论，不能定点");
        }
        project.setChosenSupplierId(fixedSupplierId);
        project.setSupplierFixedAt(LocalDateTime.now());
        // 定点标记互斥
        evalMapper.selectList(new LambdaQueryWrapper<NpiEval>()
                .eq(NpiEval::getProjectId, project.getId())).forEach(e -> {
                    int flag = e.getSupplierId().equals(fixedSupplierId) ? 1 : 0;
            if ((e.getSelectedFlag() == null ? 0 : e.getSelectedFlag()) != flag) {
                e.setSelectedFlag(flag);
                evalMapper.updateById(e);
            }
        });
        Supplier supplier = supplierMapper.selectById(supplierId);
        addTimeline(project.getId(), "AUDIT", "FIX_SUPPLIER",
                "送样评估对比完成，定点供应商：" + (supplier == null ? "" : supplier.getSupplierName()),
                req.getComment());
    }

    private void doAuditResult(NpiProject project, boolean pass) {
        FactoryAudit latest = auditMapper.selectOne(new LambdaQueryWrapper<FactoryAudit>()
                .eq(FactoryAudit::getProjectId, project.getId())
                .eq(FactoryAudit::getStatus, "CONFIRMED")
                .orderByDesc(FactoryAudit::getId).last("LIMIT 1"));
        if (latest == null) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "尚无已确认的验厂结论");
        }
        if (pass) {
            if (!"PASS".equals(latest.getConclusion())) {
                throw new BizException(ResultCode.BIZ_STATE_INVALID,
                        "最新验厂结论为" + auditConclusionText(latest.getConclusion()) + "，不能按通过推进；限期整改请先安排复审");
            }
            project.setAuditPassedAt(LocalDateTime.now());
            addTimeline(project.getId(), "PRODUCING", "AUDIT_PASS",
                    "实地验厂合格（生产/仓储/冷链合规），进入量产", null);
        } else {
            addTimeline(project.getId(), "SOURCING", "AUDIT_FAIL",
                    "验厂结论" + auditConclusionText(latest.getConclusion()) + "，退回重新寻源/更换供应商", null);
        }
    }

    private void requirePassedExtTest(NpiProject project) {
        ExternalTest passed = extTestMapper.selectOne(new LambdaQueryWrapper<ExternalTest>()
                .eq(ExternalTest::getProjectId, project.getId())
                .eq(ExternalTest::getStatus, "REPORTED")
                .eq(ExternalTest::getConclusion, "PASS")
                .orderByDesc(ExternalTest::getId).last("LIMIT 1"));
        if (passed == null) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID,
                    "尚无检测结论为“合格”的外检报告，不能推进上市放行");
        }
    }

    private void doRelease(NpiProject project, ProjectActionRequest req) {
        List<GateStatus> gates = buildGates(project);
        List<GateStatus> failed = gates.stream().filter(g -> !g.isOk()).toList();
        if (!failed.isEmpty()) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID,
                    "上市放行前置条件未满足：" + String.join("；",
                            failed.stream().map(GateStatus::getDetail).toList()));
        }
        if (req.getProductId() != null) {
            Product product = productMapper.selectById(req.getProductId());
            if (product == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "关联产品档案不存在");
            }
            project.setProductId(req.getProductId());
        }
        project.setListedAt(LocalDateTime.now());
        addTimeline(project.getId(), "LISTED", "RELEASE",
                "前置闸门全部通过，准予上市销售", req.getComment());
    }

    // ---------------- 闸门 ----------------

    /** 六个固定闸门的实时状态（详情页展示，推进时后端强校验）。 */
    public List<GateStatus> buildGates(NpiProject p) {
        List<StandardTemplate> templates = standardService.listByProject(p.getId());
        long gradeCount = templates.stream().map(StandardTemplate::getGrade)
                .filter(GRADES::contains).distinct().count();
        boolean chosenPublished = p.getSelectedTemplateId() != null && templates.stream()
                .anyMatch(t -> t.getId().equals(p.getSelectedTemplateId()) && "PUBLISHED".equals(t.getStatus()));

        Long evalCount = evalMapper.selectCount(new LambdaQueryWrapper<NpiEval>()
                .eq(NpiEval::getProjectId, p.getId()));
        boolean evalReady = p.getChosenSupplierId() != null && evalCount != null && evalCount > 0;

        FactoryAudit latestAudit = auditMapper.selectOne(new LambdaQueryWrapper<FactoryAudit>()
                .eq(FactoryAudit::getProjectId, p.getId())
                .eq(FactoryAudit::getStatus, "CONFIRMED")
                .orderByDesc(FactoryAudit::getId).last("LIMIT 1"));
        boolean auditReady = latestAudit != null && "PASS".equals(latestAudit.getConclusion());

        ExternalTest latestExt = extTestMapper.selectOne(new LambdaQueryWrapper<ExternalTest>()
                .eq(ExternalTest::getProjectId, p.getId())
                .orderByDesc(ExternalTest::getId).last("LIMIT 1"));
        boolean extReady = latestExt != null && "REPORTED".equals(latestExt.getStatus())
                && "PASS".equals(latestExt.getConclusion());

        List<GateStatus> gates = new ArrayList<>();
        gates.add(new GateStatus("三档标准起草", gradeCount >= 3,
                gradeCount >= 3 ? "高/中/低三档标准已起草" : "已起草 " + gradeCount + " 档，需补齐三档"));
        gates.add(new GateStatus("标准评审与选档", chosenPublished,
                chosenPublished ? "选定档位标准已评审发布" : "采购/老板评审通过并选定寻源档位"));
        gates.add(new GateStatus("送样评估与定点", evalReady,
                evalReady ? "已完成送样评估并定点" : "至少一家供应商完成送样评估并定点"));
        gates.add(new GateStatus("实地验厂", auditReady,
                auditReady ? "验厂结论：合格" : "验厂结论需为合格"));
        gates.add(new GateStatus("国家检测送检", extReady,
                extReady ? "外检报告结论：合格" : "需登记合格的第三方/国家检测报告"));
        gates.add(new GateStatus("上市放行",
                chosenPublished && evalReady && auditReady && extReady,
                "全部前置闸门通过后放行"));
        return gates;
    }

    // ---------------- 公共辅助（供同模块服务复用） ----------------

    public NpiProject getRequired(Long id) {
        NpiProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "新品项目不存在");
        }
        return project;
    }

    public void addTimeline(Long projectId, String stage, String action, String title, String comment) {
        NpiTimeline timeline = new NpiTimeline();
        timeline.setProjectId(projectId);
        timeline.setStage(stage);
        timeline.setAction(action);
        timeline.setTitle(title);
        timeline.setComment(comment);
        timeline.setOperatorId(SecurityUtils.getCurrentUserId());
        timeline.setOperatorName(SecurityUtils.getCurrentUsername());
        timeline.setCreatedAt(LocalDateTime.now());
        timeline.setTenantId(SecurityUtils.getTenantId());
        timelineMapper.insert(timeline);
    }

    public List<NpiEval> listEvals(Long projectId) {
        return evalMapper.selectList(new LambdaQueryWrapper<NpiEval>()
                .eq(NpiEval::getProjectId, projectId)
                .orderByAsc(NpiEval::getRoundNo)
                .orderByDesc(NpiEval::getTotalScore)
                .orderByDesc(NpiEval::getId));
    }

    public List<FactoryAudit> listAudits(Long projectId) {
        return auditMapper.selectList(new LambdaQueryWrapper<FactoryAudit>()
                .eq(FactoryAudit::getProjectId, projectId).orderByDesc(FactoryAudit::getId));
    }

    public List<ExternalTest> listExtTests(Long projectId) {
        return extTestMapper.selectList(new LambdaQueryWrapper<ExternalTest>()
                .eq(ExternalTest::getProjectId, projectId).orderByDesc(ExternalTest::getId));
    }

    public static String gradeLabel(String grade) {
        return StandardTemplateService.gradeLabel(grade);
    }

    private String auditConclusionText(String conclusion) {
        return switch (conclusion) {
            case "PASS" -> "合格";
            case "CONDITIONAL" -> "限期整改";
            case "FAIL" -> "不合格";
            default -> conclusion;
        };
    }

    private void apply(NpiProject project, ProjectUpsertRequest request) {
        project.setProjectName(request.getProjectName().trim());
        project.setCategoryId(request.getCategoryId());
        project.setBrand(request.getBrand());
        project.setBackground(request.getBackground());
        project.setMeetingAt(request.getMeetingAt());
        project.setAttendees(request.getAttendees());
        project.setTargetListingDate(request.getTargetListingDate());
    }

    private Set<String> currentRoles() {
        var roles = SecurityUtils.getLoginUser().getRoles();
        return roles == null ? Set.of() : roles;
    }
}
