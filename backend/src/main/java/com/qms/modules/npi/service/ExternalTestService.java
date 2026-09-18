package com.qms.modules.npi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qms.common.exception.BizException;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.ResultCode;
import com.qms.framework.audit.AuditLog;
import com.qms.framework.biz.BizNoGenerator;
import com.qms.modules.npi.dto.ExtTestUpsertRequest;
import com.qms.modules.npi.entity.ExternalTest;
import com.qms.modules.npi.mapper.ExternalTestMapper;
import com.qms.modules.npi.vo.ExtTestListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** 国家/第三方检测送检：登记送检 → 回填检测报告结论（CMA/CNAS 资质机构）。 */
@Service
@RequiredArgsConstructor
public class ExternalTestService {

    private static final Set<String> CONCLUSIONS = Set.of("PASS", "FAIL", "PARTIAL");

    private final ExternalTestMapper extTestMapper;
    private final NpiProjectService projectService;
    private final BizNoGenerator bizNoGenerator;

    public PageResult<ExtTestListVO> page(PageRequest request, Long projectId, String status) {
        LambdaQueryWrapper<ExternalTest> wrapper = new LambdaQueryWrapper<ExternalTest>()
                .eq(projectId != null, ExternalTest::getProjectId, projectId)
                .eq(status != null && !status.isBlank(), ExternalTest::getStatus, status)
                .orderByDesc(ExternalTest::getId);
        Page<ExternalTest> page = extTestMapper.selectPage(
                new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        List<ExtTestListVO> rows = page.getRecords().stream().map(t -> {
            ExtTestListVO vo = new ExtTestListVO();
            vo.setTest(t);
            vo.setProjectName(projectService.getRequired(t.getProjectId()).getProjectName());
            return vo;
        }).toList();
        return PageResult.of(page, rows);
    }

    public ExternalTest detail(Long id) {
        ExternalTest test = extTestMapper.selectById(id);
        if (test == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "外检单不存在");
        }
        return test;
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "外检送检", action = "SAVE", bizType = "qc_external_test")
    public Long save(ExtTestUpsertRequest request) {
        projectService.getRequired(request.getProjectId());
        ExternalTest test = request.getId() == null ? new ExternalTest() : detail(request.getId());
        test.setProjectId(request.getProjectId());
        test.setSupplierId(request.getSupplierId());
        test.setProductName(request.getProductName());
        test.setSampleDesc(request.getSampleDesc());
        test.setLabName(request.getLabName());
        test.setLabQualification(request.getLabQualification());
        test.setTestItems(request.getTestItems());
        test.setSentAt(request.getSentAt());
        test.setReportNo(request.getReportNo());
        test.setReportDate(request.getReportDate());
        test.setRemark(request.getRemark());

        boolean reportedBefore = "REPORTED".equals(test.getStatus());
        if (request.getConclusion() != null) {
            String conclusion = request.getConclusion().trim().toUpperCase();
            if (!CONCLUSIONS.contains(conclusion)) {
                throw new BizException(ResultCode.PARAM_INVALID, "检测结论仅支持 PASS/FAIL/PARTIAL");
            }
            test.setConclusion(conclusion);
        } else if (test.getConclusion() == null) {
            test.setConclusion("PENDING");
        }

        String oldStatus = test.getStatus();
        test.setStatus(deriveStatus(test));
        if (request.getId() == null) {
            test.setTestNo(bizNoGenerator.next("WJ"));
            extTestMapper.insert(test);
        } else {
            extTestMapper.updateById(test);
        }
        if ("REPORTED".equals(test.getStatus()) && !reportedBefore) {
            projectService.addTimeline(request.getProjectId(), "EXT_TEST", "EXT_REPORT",
                    "检测报告回填：" + test.getLabName() + "，结论 "
                            + conclusionText(test.getConclusion()), test.getReportNo());
        }
        return test.getId();
    }

    /** 报告号+结论齐全=已出报告；已寄样=已送检；否则计划中。 */
    private String deriveStatus(ExternalTest test) {
        if (test.getReportNo() != null && !test.getReportNo().isBlank()
                && !"PENDING".equals(test.getConclusion())) {
            return "REPORTED";
        }
        if (test.getSentAt() != null) {
            return "SENT";
        }
        return "PLANNED";
    }

    private String conclusionText(String c) {
        return switch (c) {
            case "PASS" -> "合格";
            case "FAIL" -> "不合格";
            case "PARTIAL" -> "部分合格";
            default -> "待出结果";
        };
    }
}
