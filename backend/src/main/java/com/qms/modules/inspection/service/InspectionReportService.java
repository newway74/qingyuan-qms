package com.qms.modules.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qms.common.exception.BizException;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.ResultCode;
import com.qms.framework.audit.AuditContext;
import com.qms.framework.audit.AuditLog;
import com.qms.modules.attachment.entity.Attachment;
import com.qms.modules.defect.event.ReportIssuedEvent;
import com.qms.modules.attachment.service.AttachmentService;
import com.qms.modules.inspection.entity.InspectionReport;
import com.qms.modules.inspection.entity.InspectionResult;
import com.qms.modules.inspection.entity.InspectionTask;
import com.qms.modules.inspection.mapper.InspectionReportMapper;
import com.qms.modules.inspection.mapper.InspectionResultMapper;
import com.qms.modules.inspection.mapper.InspectionTaskMapper;
import com.qms.modules.inspection.vo.ReportPdfData;
import com.qms.modules.inspection.vo.ReportVO;
import com.qms.modules.masterdata.entity.Product;
import com.qms.modules.masterdata.entity.ProductSku;
import com.qms.modules.masterdata.mapper.ProductMapper;
import com.qms.modules.masterdata.mapper.ProductSkuMapper;
import com.qms.modules.sampling.entity.Sample;
import com.qms.modules.sampling.mapper.SampleMapper;
import com.qms.modules.standard.entity.StandardItem;
import com.qms.modules.standard.entity.StandardTemplate;
import com.qms.modules.standard.mapper.StandardItemMapper;
import com.qms.modules.standard.mapper.StandardTemplateMapper;
import com.qms.modules.system.entity.SysUser;
import com.qms.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 质检报告服务：双签报告只读、PDF 导出、签发后固化 PDF 附件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionReportService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final InspectionReportMapper reportMapper;
    private final InspectionTaskMapper taskMapper;
    private final InspectionResultMapper resultMapper;
    private final SampleMapper sampleMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final StandardTemplateMapper templateMapper;
    private final StandardItemMapper itemMapper;
    private final SysUserMapper sysUserMapper;
    private final AttachmentService attachmentService;
    private final PdfReportRenderer pdfRenderer;
    private final ApplicationEventPublisher eventPublisher;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public PageResult<ReportVO> page(PageRequest request, String reportNo,
                                     String conclusion, String status) {
        LambdaQueryWrapper<InspectionReport> wrapper = new LambdaQueryWrapper<InspectionReport>()
                .like(reportNo != null && !reportNo.isBlank(), InspectionReport::getReportNo, reportNo)
                .eq(conclusion != null && !conclusion.isBlank(), InspectionReport::getConclusion, conclusion)
                .eq(status != null && !status.isBlank(), InspectionReport::getStatus, status)
                .orderByDesc(InspectionReport::getId);
        Page<InspectionReport> page = reportMapper.selectPage(
                new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        List<ReportVO> records = page.getRecords().stream().map(r -> toVO(r, false)).toList();
        return PageResult.of(page, records);
    }

    public ReportVO detail(Long id) {
        return toVO(getRequired(id), true);
    }

    public ReportVO byTask(Long taskId) {
        InspectionReport report = reportMapper.selectOne(new LambdaQueryWrapper<InspectionReport>()
                .eq(InspectionReport::getTaskId, taskId).last("LIMIT 1"));
        if (report == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "该任务尚未生成检验报告");
        }
        return toVO(report, true);
    }

    public byte[] pdfBytes(Long reportId) {
        InspectionReport report = getRequired(reportId);
        ReportPdfData data = buildPdfData(report);
        return pdfRenderer.render(data);
    }

    /** 签发权限：复核判定人/质量主管/管理员 */
    private void assertCanIssue() {
        var roles = com.qms.common.utils.SecurityUtils.getLoginUser().getRoles();
        if (roles.stream().noneMatch(r -> List.of("REVIEWER", "QA_MANAGER", "ADMIN").contains(r))) {
            throw new BizException(ResultCode.AUTH_FORBIDDEN, "仅复核判定人/质量主管可签发报告");
        }
    }

    /** 签发：SIGNED → ISSUED，固化 PDF 附件，签发后报告只读 */
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "质检报告", action = "ISSUE", bizType = "qc_inspection_report", bizIdExpr = "#id")
    public void issue(Long id) {
        assertCanIssue();
        InspectionReport report = getRequired(id);
        AuditContext.putBefore(writeJson(report));
        if (!"SIGNED".equals(report.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID,
                    "仅已双签未签发的报告可签发（已签发报告只读）");
        }
        ReportPdfData data = buildPdfData(report);
        data.setReportStatus("ISSUED");
        byte[] pdf = pdfRenderer.render(data);
        Attachment attachment = attachmentService.uploadBytes("REPORT_PDF", report.getId(),
                report.getReportNo() + ".pdf", pdf, "application/pdf", "pdf");
        report.setPdfAttachmentId(attachment.getId());
        report.setStatus("ISSUED");
        report.setIssuedAt(java.time.LocalDateTime.now());
        int rows = reportMapper.updateById(report);
        if (rows == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }
        // 签发联动：不合格/让步报告同事务自动创建不合格处置单（合格不产生，建单侧幂等）
        eventPublisher.publishEvent(new ReportIssuedEvent(report.getId()));
    }

    // ------------------------------------------------------------------

    private ReportVO toVO(InspectionReport report, boolean full) {
        ReportVO vo = new ReportVO();
        vo.setReport(report);
        InspectionTask task = taskMapper.selectById(report.getTaskId());
        if (task != null) {
            vo.setTaskNo(task.getTaskNo());
            Sample sample = sampleMapper.selectById(task.getSampleId());
            if (sample != null) {
                vo.setSampleNo(sample.getSampleNo());
                vo.setBatchNo(sample.getBatchNo());
                ProductSku sku = productSkuMapper.selectById(sample.getSkuId());
                if (sku != null) {
                    vo.setSkuCode(sku.getSkuCode());
                    vo.setSpec(sku.getSpec());
                    Product product = productMapper.selectById(sku.getProductId());
                    if (product != null) {
                        vo.setProductName(product.getProductName());
                    }
                }
            }
            if (task.getInspectorId() != null) {
                SysUser inspector = sysUserMapper.selectById(task.getInspectorId());
                vo.setInspectorName(inspector == null ? null : inspector.getRealName());
            }
            if (task.getReviewerId() != null) {
                SysUser reviewer = sysUserMapper.selectById(task.getReviewerId());
                vo.setReviewerName(reviewer == null ? null : reviewer.getRealName());
            }
            StandardTemplate template = templateMapper.selectById(task.getTemplateId());
            if (template != null) {
                vo.setTemplateName(template.getTemplateName());
                vo.setTemplateVersion(template.getVersion());
            }
        }
        return vo;
    }

    private ReportPdfData buildPdfData(InspectionReport report) {
        ReportPdfData data = new ReportPdfData();
        data.setReportNo(report.getReportNo());
        data.setConclusion(report.getConclusion());
        data.setAFailCount(report.getAFailCount());
        data.setBFailCount(report.getBFailCount());
        data.setCFailCount(report.getCFailCount());
        data.setInspectorHash(report.getInspectorSignHash());
        data.setReviewerHash(report.getReviewerSignHash());
        data.setInspectorSignedAt(report.getInspectorSignedAt() == null ? null
                : report.getInspectorSignedAt().format(DT_FMT));
        data.setReviewerSignedAt(report.getReviewerSignedAt() == null ? null
                : report.getReviewerSignedAt().format(DT_FMT));
        data.setIssuedAt(report.getIssuedAt() == null ? null : report.getIssuedAt().format(DT_FMT));
        data.setReportStatus(report.getStatus());

        InspectionTask task = taskMapper.selectById(report.getTaskId());
        if (task != null) {
            data.setTaskNo(task.getTaskNo());
            data.setRoundNo(String.valueOf(task.getRoundNo()));
            Sample sample = sampleMapper.selectById(task.getSampleId());
            if (sample != null) {
                data.setSampleNo(sample.getSampleNo());
                data.setBatchNo(sample.getBatchNo());
                data.setProductionDate(sample.getProductionDate() == null ? null
                        : sample.getProductionDate().format(DATE_FMT));
                data.setExpiryDate(sample.getExpiryDate() == null ? null
                        : sample.getExpiryDate().format(DATE_FMT));
                ProductSku sku = productSkuMapper.selectById(sample.getSkuId());
                if (sku != null) {
                    data.setSkuCode(sku.getSkuCode());
                    data.setSpec(sku.getSpec());
                    Product product = productMapper.selectById(sku.getProductId());
                    if (product != null) {
                        data.setProductName(product.getProductName());
                    }
                }
            }
            if (task.getInspectorId() != null) {
                SysUser inspector = sysUserMapper.selectById(task.getInspectorId());
                data.setInspectorName(inspector == null ? null : inspector.getRealName());
            }
            if (task.getReviewerId() != null) {
                SysUser reviewer = sysUserMapper.selectById(task.getReviewerId());
                data.setReviewerName(reviewer == null ? null : reviewer.getRealName());
            }
            StandardTemplate template = templateMapper.selectById(task.getTemplateId());
            if (template != null) {
                data.setTemplateName(template.getTemplateName());
                data.setTemplateVersion(template.getVersion());
            }
            data.setRows(buildRows(task));
        }
        return data;
    }

    private List<ReportPdfData.ResultRow> buildRows(InspectionTask task) {
        List<StandardItem> items = itemMapper.selectList(new LambdaQueryWrapper<StandardItem>()
                .eq(StandardItem::getTemplateId, task.getTemplateId())
                .orderByAsc(StandardItem::getSort)
                .orderByAsc(StandardItem::getId));
        List<InspectionResult> results = resultMapper.selectList(new LambdaQueryWrapper<InspectionResult>()
                .eq(InspectionResult::getTaskId, task.getId()));
        Map<Long, InspectionResult> byItemId = new HashMap<>();
        results.forEach(r -> byItemId.put(r.getItemId(), r));

        return items.stream().map(item -> {
            InspectionResult result = byItemId.get(item.getId());
            ReportPdfData.ResultRow row = new ReportPdfData.ResultRow();
            row.setGroupCode(item.getGroupCode());
            row.setItemName(item.getItemName());
            row.setStandard(standardDesc(item));
            row.setMeasured(result == null ? "—" : measuredDesc(item, result));
            row.setFinalJudgement(result == null ? "NONE"
                    : result.getFinalJudgement() == null ? result.getAutoJudgement()
                    : result.getFinalJudgement());
            row.setReviewNote(result == null ? null : result.getReviewNote());
            return row;
        }).toList();
    }

    private String standardDesc(StandardItem item) {
        String prefix = item.getDefectLevel() + "类"
                + (item.getVetoFlag() != null && item.getVetoFlag() == 1 ? "(一票否决)" : "") + "；";
        String rule = switch (item.getResultType()) {
            case "QUALITATIVE" -> "定性判定：符合/不符合";
            case "DOCUMENT" -> "上传" + item.getItemName() + "（凭证齐全）";
            default -> quantitativeStandard(item);
        };
        String method = item.getInspectMethod() == null || item.getInspectMethod().isBlank()
                ? "" : "；方法：" + item.getInspectMethod();
        return prefix + rule + method;
    }

    private String quantitativeStandard(StandardItem item) {
        if ("JJF1070".equalsIgnoreCase(item.getToleranceRule()) && item.getNominalValue() != null) {
            return "标示净含量 " + item.getNominalValue() + unit(item)
                    + "，实测须满足JJF1070允许短缺量";
        }
        if (item.getJudgeConfig() != null && item.getJudgeConfig().get("operator") != null) {
            Map<String, Object> cfg = item.getJudgeConfig();
            return "规则 " + cfg.get("operator") + " " + cfg.get("value")
                    + (cfg.get("value2") == null ? "" : " ~ " + cfg.get("value2")) + unit(item);
        }
        if (item.getMinValue() != null || item.getMaxValue() != null) {
            return "标准区间 " + (item.getMinValue() == null ? "−∞" : item.getMinValue())
                    + " ~ " + (item.getMaxValue() == null ? "+∞" : item.getMaxValue()) + unit(item);
        }
        return "记录实测值" + unit(item);
    }

    private String measuredDesc(StandardItem item, InspectionResult result) {
        return switch (item.getResultType()) {
            case "QUALITATIVE" -> switch (result.getQualitativeValue() == null
                    ? "" : result.getQualitativeValue()) {
                case "PASS" -> "符合";
                case "FAIL" -> "不符合";
                case "NA" -> "不适用";
                default -> "—";
            };
            case "DOCUMENT" -> result.getDocAttachmentId() == null ? "未上传" : "已上传凭证";
            default -> {
                BigDecimal v = result.getQuantitativeValue();
                yield v == null ? "—" : v.stripTrailingZeros().toPlainString() + unit(item);
            }
        };
    }

    private String unit(StandardItem item) {
        return item.getUnit() == null || item.getUnit().isBlank() ? "" : " " + item.getUnit();
    }

    private InspectionReport getRequired(Long id) {
        InspectionReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "质检报告不存在");
        }
        return report;
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("审计快照序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
