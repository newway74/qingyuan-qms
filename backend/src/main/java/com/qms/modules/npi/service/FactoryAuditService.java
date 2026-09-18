package com.qms.modules.npi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qms.common.exception.BizException;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.ResultCode;
import com.qms.framework.audit.AuditLog;
import com.qms.framework.biz.BizNoGenerator;
import com.qms.modules.masterdata.entity.Supplier;
import com.qms.modules.masterdata.mapper.SupplierMapper;
import com.qms.modules.npi.dto.AuditItemInput;
import com.qms.modules.npi.dto.AuditUpsertRequest;
import com.qms.modules.npi.entity.FactoryAudit;
import com.qms.modules.npi.entity.FactoryAuditItem;
import com.qms.modules.npi.mapper.FactoryAuditItemMapper;
import com.qms.modules.npi.mapper.FactoryAuditMapper;
import com.qms.modules.npi.vo.AuditListVO;
import com.qms.modules.npi.vo.AuditDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 实地验厂：检查表评分（资质/生产/仓储/冷链/质量体系），结论确认与整改复审。 */
@Service
@RequiredArgsConstructor
public class FactoryAuditService {

    private static final Set<String> CONCLUSIONS = Set.of("PASS", "CONDITIONAL", "FAIL");

    private final FactoryAuditMapper auditMapper;
    private final FactoryAuditItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final NpiProjectService projectService;
    private final BizNoGenerator bizNoGenerator;

    public PageResult<AuditListVO> page(PageRequest request, Long projectId, String status) {
        LambdaQueryWrapper<FactoryAudit> wrapper = new LambdaQueryWrapper<FactoryAudit>()
                .eq(projectId != null, FactoryAudit::getProjectId, projectId)
                .eq(status != null && !status.isBlank(), FactoryAudit::getStatus, status)
                .orderByDesc(FactoryAudit::getId);
        Page<FactoryAudit> page = auditMapper.selectPage(
                new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        List<AuditListVO> rows = page.getRecords().stream().map(a -> {
            AuditListVO vo = new AuditListVO();
            vo.setAudit(a);
            Supplier s = supplierMapper.selectById(a.getSupplierId());
            vo.setSupplierName(s == null ? null : s.getSupplierName());
            vo.setProjectName(projectService.getRequired(a.getProjectId()).getProjectName());
            return vo;
        }).toList();
        return PageResult.of(page, rows);
    }

    public AuditDetailVO detail(Long id) {
        FactoryAudit audit = getRequired(id);
        AuditDetailVO vo = new AuditDetailVO();
        vo.setAudit(audit);
        Supplier supplier = supplierMapper.selectById(audit.getSupplierId());
        vo.setSupplierName(supplier == null ? null : supplier.getSupplierName());
        vo.setProjectName(projectService.getRequired(audit.getProjectId()).getProjectName());
        vo.setItems(listItems(id));
        return vo;
    }

    /** 新建验厂单时带出默认检查表（五大类六项）。 */
    public List<FactoryAuditItem> defaultItems() {
        return List.of(
                item(null, "QUALIFICATION", "资质证照合规", "GMP/药品生产许可/食品生产许可(SC)/经营许可在有效期内，经营范围覆盖拟供品类", 1),
                item(null, "PRODUCTION", "生产车间与设备合规", "厂房布局/洁净区/设备校准/虫害控制符合规范", 2),
                item(null, "PRODUCTION", "生产过程质量控制", "批生产记录、出厂检验、批号追溯体系完整", 3),
                item(null, "WAREHOUSE", "仓储条件合规", "分区存放、离地离墙、防潮防虫、效期与先进先出管理", 4),
                item(null, "COLD_CHAIN", "冷链保障能力", "冷藏冷冻库、温湿度连续监测记录、冷链运输温控（如适用）", 5),
                item(null, "QUALITY_SYSTEM", "质量体系与人员", "质量管理制度、不合格品控制、质检人员配置与培训", 6)
        );
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "实地验厂", action = "SAVE", bizType = "qc_factory_audit")
    public Long save(AuditUpsertRequest request) {
        projectService.getRequired(request.getProjectId());
        if (supplierMapper.selectById(request.getSupplierId()) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "验厂供应商不存在");
        }
        FactoryAudit audit = request.getId() == null ? new FactoryAudit() : getRequired(request.getId());
        if (request.getId() != null && "CONFIRMED".equals(audit.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "已确认的验厂单不可修改；整改请新建复审单");
        }
        audit.setProjectId(request.getProjectId());
        audit.setSupplierId(request.getSupplierId());
        audit.setParentAuditId(request.getParentAuditId());
        audit.setAuditType(request.getAuditType() == null || request.getAuditType().isBlank()
                ? "INITIAL" : request.getAuditType());
        audit.setPlannedAt(request.getPlannedAt());
        audit.setAuditedAt(request.getAuditedAt());
        audit.setAuditors(request.getAuditors());
        audit.setRectifyRequirement(request.getRectifyRequirement());
        audit.setRectifyDeadline(request.getRectifyDeadline());
        audit.setRemark(request.getRemark());

        boolean confirming = request.getConfirmConclusion() != null;
        List<AuditItemInput> itemInputs = request.getItems() == null ? List.of() : request.getItems();

        if (request.getId() == null) {
            audit.setAuditNo(bizNoGenerator.next("YC"));
            audit.setStatus("PLANNED");
            auditMapper.insert(audit);
        } else {
            itemMapper.delete(new LambdaQueryWrapper<FactoryAuditItem>()
                    .eq(FactoryAuditItem::getAuditId, audit.getId()));
        }

        int sort = 0;
        for (AuditItemInput in : itemInputs) {
            FactoryAuditItem item = new FactoryAuditItem();
            item.setAuditId(audit.getId());
            item.setCategory(in.getCategory().trim());
            item.setItemName(in.getItemName().trim());
            item.setRequirement(in.getRequirement());
            item.setMaxScore(in.getMaxScore() == null ? new BigDecimal("100") : in.getMaxScore());
            item.setScore(in.getScore());
            item.setResult(in.getResult());
            item.setNote(in.getNote());
            item.setSort(in.getSort() == null ? sort : in.getSort());
            itemMapper.insert(item);
            sort++;
        }

        if (confirming) {
            String conclusion = request.getConfirmConclusion().trim().toUpperCase();
            if (!CONCLUSIONS.contains(conclusion)) {
                throw new BizException(ResultCode.PARAM_INVALID, "验厂结论仅支持 PASS/CONDITIONAL/FAIL");
            }
            List<FactoryAuditItem> scored = listItems(audit.getId()).stream()
                    .filter(i -> i.getScore() != null && !"NA".equals(i.getResult())).toList();
            if (scored.isEmpty()) {
                throw new BizException(ResultCode.BIZ_STATE_INVALID, "请先完成检查项评分，再确认验厂结论");
            }
            BigDecimal total = scored.stream()
                    .map(i -> i.getScore().multiply(i.getMaxScore()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(scored.stream().map(FactoryAuditItem::getMaxScore)
                            .reduce(BigDecimal.ZERO, BigDecimal::add), 2, RoundingMode.HALF_UP);
            audit.setTotalScore(total);
            audit.setConclusion(conclusion);
            audit.setStatus("CONFIRMED");
            if (audit.getAuditedAt() == null) {
                audit.setAuditedAt(LocalDateTime.now());
            }
            projectService.addTimeline(request.getProjectId(), "AUDIT", "AUDIT_CONFIRM",
                    "验厂结论确认：" + conclusionText(conclusion) + "（总分 " + total + "）",
                    request.getRectifyRequirement());
        } else if (!"PLANNED".equals(audit.getStatus())) {
            audit.setStatus("IN_PROGRESS");
        } else if (audit.getAuditedAt() != null || !itemInputs.isEmpty()) {
            audit.setStatus("IN_PROGRESS");
        }
        auditMapper.updateById(audit);
        return audit.getId();
    }

    private List<FactoryAuditItem> listItems(Long auditId) {
        return itemMapper.selectList(new LambdaQueryWrapper<FactoryAuditItem>()
                .eq(FactoryAuditItem::getAuditId, auditId)
                .orderByAsc(FactoryAuditItem::getSort));
    }

    private FactoryAudit getRequired(Long id) {
        FactoryAudit audit = auditMapper.selectById(id);
        if (audit == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "验厂单不存在");
        }
        return audit;
    }

    private static FactoryAuditItem item(Long auditId, String category, String name, String requirement, int sort) {
        FactoryAuditItem i = new FactoryAuditItem();
        i.setAuditId(auditId);
        i.setCategory(category);
        i.setItemName(name);
        i.setRequirement(requirement);
        i.setMaxScore(new BigDecimal("100"));
        i.setSort(sort);
        return i;
    }

    private String conclusionText(String c) {
        return switch (c) {
            case "PASS" -> "合格";
            case "CONDITIONAL" -> "限期整改";
            case "FAIL" -> "不合格";
            default -> c;
        };
    }
}
