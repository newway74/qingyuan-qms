package com.qms.modules.standard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.exception.BizException;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.ResultCode;
import com.qms.common.utils.SecurityUtils;
import com.qms.framework.audit.AuditContext;
import com.qms.framework.audit.AuditLog;
import com.qms.framework.statemachine.StateMachineEngine;
import com.qms.modules.masterdata.entity.Category;
import com.qms.modules.masterdata.mapper.CategoryMapper;
import com.qms.modules.npi.entity.NpiProject;
import com.qms.modules.npi.mapper.NpiProjectMapper;
import com.qms.modules.standard.dto.GradeTemplateGenerateRequest;
import com.qms.modules.standard.dto.ItemBatchSaveRequest;
import com.qms.modules.standard.dto.StandardItemRequest;
import com.qms.modules.standard.dto.StdReviewRequest;
import com.qms.modules.standard.dto.TemplateCopyRequest;
import com.qms.modules.standard.dto.TemplateUpsertRequest;
import com.qms.modules.standard.entity.StandardItem;
import com.qms.modules.standard.entity.StandardTemplate;
import com.qms.modules.standard.entity.StdReview;
import com.qms.modules.standard.mapper.StandardItemMapper;
import com.qms.modules.standard.mapper.StandardTemplateMapper;
import com.qms.modules.standard.mapper.StdReviewMapper;
import com.qms.modules.standard.vo.TemplateDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 检验标准模板版本化服务。
 * 不变量：
 * 1) 一行=一个版本；DRAFT 可编辑/编排检验项，PUBLISHED 不可变，ARCHIVED 只读；
 * 2) 同一 template_code 同时最多一个 DRAFT；发布后旧 PUBLISHED 自动归档；
 * 3) template_code 一经使用永久不可复用（含已删除）；检验项替换走逻辑删除 + 审计前后值；
 * 4) 发布必须至少含一个检验项，定量项 min≤max。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandardTemplateService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_REVIEWING = "REVIEWING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private static final String BIZ_TYPE = "qc_standard_template";
    private static final Set<String> GROUP_CODES = Set.of(
            "SENSORY", "PACKAGE_LABEL", "NET_CONTENT", "PHYSICO", "MICRO", "CERT_DOC");
    private static final Set<String> RESULT_TYPES = Set.of("QUALITATIVE", "QUANTITATIVE", "DOCUMENT");
    private static final Set<String> DEFECT_LEVELS = Set.of("A", "B", "C");
    private static final Set<String> GRADES = Set.of("HIGH", "MID", "LOW");
    private static final Set<String> REVIEW_NODES = Set.of("PROCUREMENT", "BOSS");

    private final StandardTemplateMapper templateMapper;
    private final StandardItemMapper itemMapper;
    private final CategoryMapper categoryMapper;
    private final StdReviewMapper reviewMapper;
    private final NpiProjectMapper projectMapper;
    private final StateMachineEngine stateMachine;
    private final ObjectMapper objectMapper;

    // ---------------- 查询 ----------------

    public PageResult<StandardTemplate> page(PageRequest request, String templateName,
                                             String templateCode, Long categoryId, String status,
                                             String grade, Long npiProjectId) {
        LambdaQueryWrapper<StandardTemplate> wrapper = new LambdaQueryWrapper<StandardTemplate>()
                .like(templateName != null && !templateName.isBlank(), StandardTemplate::getTemplateName, templateName)
                .like(templateCode != null && !templateCode.isBlank(), StandardTemplate::getTemplateCode, templateCode)
                .eq(categoryId != null, StandardTemplate::getCategoryId, categoryId)
                .eq(status != null && !status.isBlank(), StandardTemplate::getStatus, status)
                .eq(grade != null && !grade.isBlank(), StandardTemplate::getGrade, grade)
                .eq(npiProjectId != null, StandardTemplate::getNpiProjectId, npiProjectId)
                .orderByDesc(StandardTemplate::getTemplateCode)
                .orderByDesc(StandardTemplate::getVersion);
        Page<StandardTemplate> page = templateMapper.selectPage(
                new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        return PageResult.of(page);
    }

    public TemplateDetailVO detail(Long id) {
        StandardTemplate template = getRequired(id);
        return toDetailVO(template);
    }

    public List<StandardTemplate> versions(Long id) {
        StandardTemplate template = getRequired(id);
        return templateMapper.selectList(new LambdaQueryWrapper<StandardTemplate>()
                .eq(StandardTemplate::getTemplateCode, template.getTemplateCode())
                .orderByDesc(StandardTemplate::getVersion));
    }

    /**
     * 生效模板查询：按品类+包装形态取当前 PUBLISHED 版本（阶段3收样建任务时引用）。
     * 品类沿父链向上回溯（子类未配置时继承上级品类模板，最深10层防环）；
     * 每一层的包装形态匹配优先级：精确形态 > 不区分形态(package_form 为空)。
     */
    public TemplateDetailVO effective(Long categoryId, String packageForm) {
        if (categoryId == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "categoryId不能为空");
        }
        StandardTemplate hit = null;
        Long currentId = categoryId;
        for (int depth = 0; depth < 10 && currentId != null && hit == null; depth++) {
            List<StandardTemplate> all = templateMapper.selectList(new LambdaQueryWrapper<StandardTemplate>()
                    .eq(StandardTemplate::getCategoryId, currentId)
                    .eq(StandardTemplate::getStatus, STATUS_PUBLISHED)
                    .orderByDesc(StandardTemplate::getVersion));
            StandardTemplate generic = null;
            for (StandardTemplate t : all) {
                if (packageForm != null && packageForm.equals(t.getPackageForm())) {
                    hit = t;
                    break;
                }
                if ((t.getPackageForm() == null || t.getPackageForm().isBlank()) && generic == null) {
                    generic = t;
                }
            }
            if (hit == null) hit = generic;
            if (hit == null) {
                Category current = categoryMapper.selectById(currentId);
                currentId = current == null ? null : current.getParentId();
            }
        }
        if (hit == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "该品类/包装形态暂无已发布的检验标准模板");
        }
        return toDetailVO(hit);
    }

    // ---------------- 草稿编辑 ----------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验标准模板", action = "CREATE", bizType = "qc_standard_template")
    public Long create(TemplateUpsertRequest request) {
        if (request.getTemplateCode() == null || request.getTemplateCode().isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "模板编码不能为空");
        }
        validateCategory(request.getCategoryId());
        validateGrade(request.getGrade());
        ensureCodeUnused(request.getTemplateCode().trim());
        StandardTemplate template = new StandardTemplate();
        apply(template, request);
        template.setTemplateCode(request.getTemplateCode().trim());
        template.setVersion(1);
        template.setStatus(STATUS_DRAFT);
        templateMapper.insert(template);
        return template.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验标准模板", action = "UPDATE", bizType = "qc_standard_template", bizIdExpr = "#request.id")
    public void update(TemplateUpsertRequest request) {
        if (request.getId() == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "id不能为空");
        }
        StandardTemplate existing = getRequired(request.getId());
        requireDraft(existing);
        putBefore(existing);
        validateCategory(request.getCategoryId());
        apply(existing, request);
        // 业务编码不可改
        existing.setTemplateCode(existing.getTemplateCode());
        int rows = templateMapper.updateById(existing);
        if (rows == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }
    }

    /**
     * 基于某版本创建同编码的新草稿版本（复制模板头与全部检验项）。
     */
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验标准模板", action = "CREATE", bizType = "qc_standard_template", bizIdExpr = "#id")
    public Long revise(Long id) {
        StandardTemplate base = getRequired(id);
        Long openDraft = templateMapper.selectCount(new LambdaQueryWrapper<StandardTemplate>()
                .eq(StandardTemplate::getTemplateCode, base.getTemplateCode())
                .eq(StandardTemplate::getStatus, STATUS_DRAFT));
        if (openDraft != null && openDraft > 0) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "该模板已存在未发布的草稿版本，请先发布或放弃该草稿");
        }
        int nextVersion = maxVersion(base.getTemplateCode()) + 1;
        StandardTemplate draft = new StandardTemplate();
        draft.setTemplateCode(base.getTemplateCode());
        draft.setTemplateName(base.getTemplateName());
        draft.setCategoryId(base.getCategoryId());
        draft.setPackageForm(base.getPackageForm());
        draft.setVersion(nextVersion);
        draft.setStatus(STATUS_DRAFT);
        draft.setMaxAFail(base.getMaxAFail());
        draft.setMaxBFail(base.getMaxBFail());
        draft.setMaxCFail(base.getMaxCFail());
        draft.setConcessionAllowed(base.getConcessionAllowed());
        draft.setRemark(base.getRemark());
        draft.setGrade(base.getGrade());
        draft.setNpiProjectId(base.getNpiProjectId());
        draft.setRegulationBasis(base.getRegulationBasis());
        draft.setMarketBenchmark(base.getMarketBenchmark());
        templateMapper.insert(draft);
        copyItems(base.getId(), draft.getId());
        return draft.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验标准模板", action = "COPY", bizType = "qc_standard_template", bizIdExpr = "#id")
    public Long copy(Long id, TemplateCopyRequest request) {
        StandardTemplate base = getRequired(id);
        ensureCodeUnused(request.getTemplateCode().trim());
        StandardTemplate draft = new StandardTemplate();
        draft.setTemplateCode(request.getTemplateCode().trim());
        draft.setTemplateName(request.getTemplateName() == null || request.getTemplateName().isBlank()
                ? base.getTemplateName() + "-副本" : request.getTemplateName().trim());
        draft.setCategoryId(base.getCategoryId());
        draft.setPackageForm(base.getPackageForm());
        draft.setVersion(1);
        draft.setStatus(STATUS_DRAFT);
        draft.setMaxAFail(base.getMaxAFail());
        draft.setMaxBFail(base.getMaxBFail());
        draft.setMaxCFail(base.getMaxCFail());
        draft.setConcessionAllowed(base.getConcessionAllowed());
        draft.setRemark(base.getRemark());
        draft.setGrade(base.getGrade());
        draft.setNpiProjectId(base.getNpiProjectId());
        draft.setRegulationBasis(base.getRegulationBasis());
        draft.setMarketBenchmark(base.getMarketBenchmark());
        templateMapper.insert(draft);
        copyItems(base.getId(), draft.getId());
        return draft.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验标准模板", action = "APPROVE", bizType = "qc_standard_template", bizIdExpr = "#id")
    public void publish(Long id) {
        StandardTemplate template = getRequired(id);
        requireDraft(template);
        if (template.getGrade() != null) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID,
                    "高/中/低档位标准必须经采购会签、老板批准后发布，请走评审流程");
        }
        List<StandardItem> items = listItems(id);
        validateItemsForPublish(items);
        putBefore(template);

        archiveSameCode(template.getTemplateCode());
        template.setStatus(STATUS_PUBLISHED);
        template.setPublishedBy(SecurityUtils.getCurrentUserId());
        template.setPublishedAt(LocalDateTime.now());
        int rows = templateMapper.updateById(template);
        if (rows == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }
    }

    // ---------------- 新品档位标准 / 评审流 ----------------

    /** 项目下三档标准（各档取全部版本，前端取最新）。 */
    public List<StandardTemplate> listByProject(Long projectId) {
        return templateMapper.selectList(new LambdaQueryWrapper<StandardTemplate>()
                .eq(StandardTemplate::getNpiProjectId, projectId)
                .orderByAsc(StandardTemplate::getGrade)
                .orderByDesc(StandardTemplate::getVersion));
    }

    public List<StdReview> reviews(Long templateId) {
        return reviewMapper.selectList(new LambdaQueryWrapper<StdReview>()
                .eq(StdReview::getTemplateId, templateId)
                .orderByAsc(StdReview::getId));
    }

    /**
     * 新品项目内，依据品类现行通用标准生成某档位草稿（含全部检验项），
     * 随后可在草稿上调整限值/项目、维护国标依据与竞品对标。
     */
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验标准模板", action = "CREATE", bizType = "qc_standard_template")
    public Long generateGradeTemplate(GradeTemplateGenerateRequest request) {
        validateGrade(request.getGrade());
        NpiProject project = projectMapper.selectById(request.getProjectId());
        if (project == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "新品项目不存在");
        }
        String grade = request.getGrade().trim().toUpperCase();
        Long exists = templateMapper.selectCount(new LambdaQueryWrapper<StandardTemplate>()
                .eq(StandardTemplate::getNpiProjectId, project.getId())
                .eq(StandardTemplate::getGrade, grade));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID,
                    "该项目的" + gradeLabel(grade) + "标准已存在，可在其基础上修订新版本");
        }
        StandardTemplate base = effective(project.getCategoryId(), null).getTemplate();
        String code = "TPL_NP" + project.getProjectNo().replace("NP", "") + "_" + grade.charAt(0);
        ensureCodeUnused(code);
        StandardTemplate draft = new StandardTemplate();
        draft.setTemplateCode(code);
        draft.setTemplateName(request.getTemplateName() != null && !request.getTemplateName().isBlank()
                ? request.getTemplateName().trim()
                : project.getProjectName() + "（" + gradeLabel(grade) + "）");
        draft.setCategoryId(project.getCategoryId());
        draft.setGrade(grade);
        draft.setNpiProjectId(project.getId());
        draft.setRegulationBasis(trim(request.getRegulationBasis()));
        draft.setMarketBenchmark(trim(request.getMarketBenchmark()));
        draft.setVersion(1);
        draft.setStatus(STATUS_DRAFT);
        draft.setMaxAFail(base.getMaxAFail());
        draft.setMaxBFail(base.getMaxBFail());
        draft.setMaxCFail(base.getMaxCFail());
        draft.setConcessionAllowed(base.getConcessionAllowed());
        templateMapper.insert(draft);
        copyItems(base.getId(), draft.getId());
        return draft.getId();
    }

    /** 提交评审：DRAFT → REVIEWING（需至少一个检验项）。 */
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验标准模板", action = "SUBMIT", bizType = "qc_standard_template", bizIdExpr = "#id")
    public void submitReview(Long id) {
        StandardTemplate template = getRequired(id);
        requireDraft(template);
        validateItemsForPublish(listItems(id));
        String next = stateMachine.nextState(BIZ_TYPE, STATUS_DRAFT, "SUBMIT_REVIEW", currentRoles());
        template.setStatus(next);
        templateMapper.updateById(template);
        insertReview(template.getId(), "SUBMIT", "SUBMIT", "提交采购/老板评审");
    }

    /**
     * 评审动作：
     * 采购会签通过仅记录（仍在评审中）；采购或老板驳回回草稿；
     * 老板批准前要求采购已会签，批准即发布（同编码旧版归档）。
     */
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验标准模板", action = "REVIEW", bizType = "qc_standard_template", bizIdExpr = "#id")
    public void review(Long id, StdReviewRequest request) {
        StandardTemplate template = getRequired(id);
        if (!STATUS_REVIEWING.equals(template.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "仅评审中的标准允许该操作");
        }
        String node = request.getNode().trim().toUpperCase();
        if (!REVIEW_NODES.contains(node)) {
            throw new BizException(ResultCode.PARAM_INVALID, "非法评审节点：" + node);
        }
        boolean pass = !Boolean.FALSE.equals(request.getPass());

        if (!pass) {
            String next = stateMachine.nextState(BIZ_TYPE, STATUS_REVIEWING, "REVIEW_REJECT", currentRoles());
            template.setStatus(next);
            templateMapper.updateById(template);
            insertReview(id, node, "REJECT", request.getComment());
            return;
        }

        if ("PROCUREMENT".equals(node)) {
            insertReview(id, node, "PASS", request.getComment());
            return;
        }

        // BOSS 批准：必须已有采购会签通过记录
        Long procurementPassed = reviewMapper.selectCount(new LambdaQueryWrapper<StdReview>()
                .eq(StdReview::getTemplateId, id)
                .eq(StdReview::getNode, "PROCUREMENT")
                .eq(StdReview::getAction, "PASS"));
        if (procurementPassed == null || procurementPassed == 0) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "请先完成采购会签，再提交老板批准");
        }
        validateItemsForPublish(listItems(id));
        putBefore(template);
        archiveSameCode(template.getTemplateCode());
        String next = stateMachine.nextState(BIZ_TYPE, STATUS_REVIEWING, "REVIEW_APPROVE", currentRoles());
        template.setStatus(next);
        template.setPublishedBy(SecurityUtils.getCurrentUserId());
        template.setPublishedAt(LocalDateTime.now());
        if (templateMapper.updateById(template) == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }
        insertReview(id, node, "PASS", request.getComment());
    }

    // ---------------- 检验项编排 ----------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验标准项", action = "UPDATE", bizType = "qc_standard_item", bizIdExpr = "#templateId")
    public void saveItems(Long templateId, ItemBatchSaveRequest request) {
        StandardTemplate template = getRequired(templateId);
        requireDraft(template);
        List<StandardItem> oldItems = listItems(templateId);
        putBefore(oldItems);
        request.getItems().forEach(this::validateItem);

        // 整组替换：旧行逻辑删除（deleted=1 保留），插入新行
        for (StandardItem old : oldItems) {
            itemMapper.deleteById(old.getId());
        }
        int sort = 0;
        for (StandardItemRequest req : request.getItems()) {
            StandardItem item = new StandardItem();
            item.setTemplateId(templateId);
            item.setGroupCode(req.getGroupCode().trim());
            item.setItemName(req.getItemName().trim());
            item.setInspectMethod(trim(req.getInspectMethod()));
            item.setResultType(req.getResultType().trim());
            item.setDefectLevel(req.getDefectLevel().trim());
            item.setVetoFlag(req.getVetoFlag() == null ? 0 : req.getVetoFlag());
            item.setRequiredFlag(req.getRequiredFlag() == null ? 1 : req.getRequiredFlag());
            item.setMinValue(req.getMinValue());
            item.setMaxValue(req.getMaxValue());
            item.setNominalValue(req.getNominalValue());
            item.setUnit(trim(req.getUnit()));
            item.setToleranceRule(trim(req.getToleranceRule()));
            item.setJudgeConfig(req.getJudgeConfig());
            item.setSort(req.getSort() == null ? sort : req.getSort());
            itemMapper.insert(item);
            sort++;
        }
    }

    // ------------------------------------------------------------------

    private void apply(StandardTemplate template, TemplateUpsertRequest request) {
        template.setTemplateName(request.getTemplateName().trim());
        template.setCategoryId(request.getCategoryId());
        template.setPackageForm(trim(request.getPackageForm()));
        template.setGrade(request.getGrade() == null || request.getGrade().isBlank()
                ? null : request.getGrade().trim().toUpperCase());
        template.setNpiProjectId(request.getNpiProjectId());
        template.setRegulationBasis(trim(request.getRegulationBasis()));
        template.setMarketBenchmark(trim(request.getMarketBenchmark()));
        template.setMaxAFail(request.getMaxAFail() == null ? 0 : request.getMaxAFail());
        template.setMaxBFail(request.getMaxBFail() == null ? 0 : request.getMaxBFail());
        template.setMaxCFail(request.getMaxCFail() == null ? 0 : request.getMaxCFail());
        template.setConcessionAllowed(request.getConcessionAllowed() == null ? 0 : request.getConcessionAllowed());
        template.setRemark(trim(request.getRemark()));
    }

    private TemplateDetailVO toDetailVO(StandardTemplate template) {
        TemplateDetailVO vo = new TemplateDetailVO();
        vo.setTemplate(template);
        Category category = categoryMapper.selectById(template.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getName());
        vo.setItems(listItems(template.getId()));
        return vo;
    }

    private List<StandardItem> listItems(Long templateId) {
        return itemMapper.selectList(new LambdaQueryWrapper<StandardItem>()
                .eq(StandardItem::getTemplateId, templateId)
                .orderByAsc(StandardItem::getSort)
                .orderByAsc(StandardItem::getId));
    }

    private void copyItems(Long fromTemplateId, Long toTemplateId) {
        List<StandardItem> source = listItems(fromTemplateId);
        for (StandardItem src : source) {
            StandardItem item = new StandardItem();
            item.setTemplateId(toTemplateId);
            item.setGroupCode(src.getGroupCode());
            item.setItemName(src.getItemName());
            item.setInspectMethod(src.getInspectMethod());
            item.setResultType(src.getResultType());
            item.setDefectLevel(src.getDefectLevel());
            item.setVetoFlag(src.getVetoFlag());
            item.setRequiredFlag(src.getRequiredFlag());
            item.setMinValue(src.getMinValue());
            item.setMaxValue(src.getMaxValue());
            item.setNominalValue(src.getNominalValue());
            item.setUnit(src.getUnit());
            item.setToleranceRule(src.getToleranceRule());
            item.setJudgeConfig(src.getJudgeConfig());
            item.setSort(src.getSort());
            itemMapper.insert(item);
        }
    }

    private int maxVersion(String templateCode) {
        return templateMapper.selectList(new LambdaQueryWrapper<StandardTemplate>()
                        .eq(StandardTemplate::getTemplateCode, templateCode))
                .stream().mapToInt(StandardTemplate::getVersion).max().orElse(0);
    }

    private void validateCategory(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "所选品类不存在");
        }
    }

    private void validateGrade(String grade) {
        if (grade != null && !grade.isBlank() && !GRADES.contains(grade.trim().toUpperCase())) {
            throw new BizException(ResultCode.PARAM_INVALID, "档位仅支持 HIGH/MID/LOW");
        }
    }

    public static String gradeLabel(String grade) {
        if (grade == null) {
            return "";
        }
        return switch (grade) {
            case "HIGH" -> "高档";
            case "MID" -> "中档";
            case "LOW" -> "低档";
            default -> grade;
        };
    }

    private void archiveSameCode(String templateCode) {
        List<StandardTemplate> oldPublished = templateMapper.selectList(new LambdaQueryWrapper<StandardTemplate>()
                .eq(StandardTemplate::getTemplateCode, templateCode)
                .eq(StandardTemplate::getStatus, STATUS_PUBLISHED));
        for (StandardTemplate old : oldPublished) {
            old.setStatus(STATUS_ARCHIVED);
            templateMapper.updateById(old);
        }
    }

    private void insertReview(Long templateId, String node, String action, String comment) {
        StdReview review = new StdReview();
        review.setTemplateId(templateId);
        review.setNode(node);
        review.setAction(action);
        review.setComment(trim(comment));
        review.setOperatorId(SecurityUtils.getCurrentUserId());
        review.setOperatorName(SecurityUtils.getCurrentUsername());
        review.setOperatedAt(LocalDateTime.now());
        review.setTenantId(SecurityUtils.getTenantId());
        reviewMapper.insert(review);
    }

    private java.util.Set<String> currentRoles() {
        var roles = SecurityUtils.getLoginUser().getRoles();
        return roles == null ? java.util.Set.of() : roles;
    }

    private void ensureCodeUnused(String code) {
        Long count = templateMapper.countByCodeIncludeDeleted(SecurityUtils.getTenantId(), code);
        if (count != null && count > 0) {
            throw new BizException(ResultCode.DATA_DUPLICATED, "模板编码已被占用（编码一经使用不可复用）");
        }
    }

    private void requireDraft(StandardTemplate template) {
        if (!STATUS_DRAFT.equals(template.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "仅草稿状态的模板允许该操作（已发布版本不可变）");
        }
    }

    private void validateItem(StandardItemRequest item) {
        if (!GROUP_CODES.contains(item.getGroupCode().trim())) {
            throw new BizException(ResultCode.PARAM_INVALID, "非法检验分组：" + item.getGroupCode());
        }
        if (!RESULT_TYPES.contains(item.getResultType().trim())) {
            throw new BizException(ResultCode.PARAM_INVALID, "非法结果类型：" + item.getResultType());
        }
        if (!DEFECT_LEVELS.contains(item.getDefectLevel().trim())) {
            throw new BizException(ResultCode.PARAM_INVALID, "缺陷等级仅支持 A/B/C");
        }
        if (item.getMinValue() != null && item.getMaxValue() != null
                && item.getMinValue().compareTo(item.getMaxValue()) > 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "检验项[" + item.getItemName() + "]下限不能大于上限");
        }
    }

    private void validateItemsForPublish(List<StandardItem> items) {
        if (items.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "模板尚未配置任何检验项，不能发布");
        }
        items.forEach(item -> {
            if (item.getMinValue() != null && item.getMaxValue() != null
                    && item.getMinValue().compareTo(item.getMaxValue()) > 0) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "检验项[" + item.getItemName() + "]下限不能大于上限");
            }
        });
    }

    private StandardTemplate getRequired(Long id) {
        StandardTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "检验标准模板不存在");
        }
        return template;
    }

    private void putBefore(Object entity) {
        try {
            AuditContext.putBefore(objectMapper.writeValueAsString(entity));
        } catch (Exception e) {
            log.warn("模板 before 快照序列化失败: {}", e.getMessage());
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
