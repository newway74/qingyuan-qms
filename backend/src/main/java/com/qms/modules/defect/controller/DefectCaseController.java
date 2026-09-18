package com.qms.modules.defect.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.defect.dto.CaseApproveRequest;
import com.qms.modules.defect.dto.CaseCancelRequest;
import com.qms.modules.defect.dto.CaseConcessionRequest;
import com.qms.modules.defect.dto.CaseCreateRequest;
import com.qms.modules.defect.dto.CaseExecuteRequest;
import com.qms.modules.defect.dto.CaseReviewRequest;
import com.qms.modules.defect.entity.DefectApproval;
import com.qms.modules.defect.service.DefectCaseService;
import com.qms.modules.defect.vo.CaseDetailVO;
import com.qms.modules.defect.vo.CaseListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "不合格处置闭环")
@RestController
@RequestMapping("/api/v1/defect/cases")
@RequiredArgsConstructor
public class DefectCaseController {

    private final DefectCaseService defectCaseService;

    @Operation(summary = "不合格单分页")
    @GetMapping
    @PreAuthorize("@perm.has('defect:list')")
    public R<PageResult<CaseListVO>> page(PageRequest request,
                                          @RequestParam(required = false) String caseNo,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String maxSeverity) {
        return R.ok(defectCaseService.page(request, caseNo, status, maxSeverity));
    }

    @Operation(summary = "手动建档（无自动建单场景的补录入口）")
    @PostMapping
    @PreAuthorize("@perm.has('defect:review')")
    public R<Long> create(@Valid @RequestBody CaseCreateRequest request) {
        return R.ok(defectCaseService.manualCreate(request));
    }

    @Operation(summary = "不合格单详情（缺陷明细+整改单）")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('defect:view')")
    public R<CaseDetailVO> detail(@PathVariable Long id) {
        return R.ok(defectCaseService.detail(id));
    }

    @Operation(summary = "审批时间线")
    @GetMapping("/{id}/timeline")
    @PreAuthorize("@perm.has('defect:view')")
    public R<List<DefectApproval>> timeline(@PathVariable Long id) {
        return R.ok(defectCaseService.listTimeline(id));
    }

    @Operation(summary = "评审：登记处置方式/根本原因")
    @PostMapping("/{id}/review")
    @PreAuthorize("@perm.has('defect:review')")
    public R<Void> review(@PathVariable Long id,
                          @Valid @RequestBody CaseReviewRequest request) {
        defectCaseService.review(id, request);
        return R.ok();
    }

    @Operation(summary = "审批通过/驳回（密码二次认证+签名）")
    @PostMapping("/{id}/approve")
    @PreAuthorize("@perm.has('defect:approve')")
    public R<Void> approve(@PathVariable Long id,
                           @Valid @RequestBody CaseApproveRequest request,
                           HttpServletRequest httpRequest) {
        defectCaseService.approve(id, request, clientIp(httpRequest),
                httpRequest.getHeader("User-Agent"));
        return R.ok();
    }

    @Operation(summary = "登记处置完成（RECTIFY 须整改验证通过）")
    @PostMapping("/{id}/execute")
    @PreAuthorize("@perm.has('defect:execute')")
    public R<Void> execute(@PathVariable Long id,
                           @Valid @RequestBody CaseExecuteRequest request) {
        defectCaseService.execute(id, request);
        return R.ok();
    }

    @Operation(summary = "让步接收审批（A类硬拦截，密码+签名）")
    @PostMapping("/{id}/concession")
    @PreAuthorize("@perm.has('defect:concession')")
    public R<Void> concession(@PathVariable Long id,
                              @Valid @RequestBody CaseConcessionRequest request,
                              HttpServletRequest httpRequest) {
        defectCaseService.concession(id, request, clientIp(httpRequest),
                httpRequest.getHeader("User-Agent"));
        return R.ok();
    }

    @Operation(summary = "关闭不合格单")
    @PostMapping("/{id}/close")
    @PreAuthorize("@perm.has('defect:close')")
    public R<Void> close(@PathVariable Long id,
                         @RequestBody(required = false) CaseCancelRequest request) {
        defectCaseService.close(id, request == null ? null : request.getComment());
        return R.ok();
    }

    @Operation(summary = "撤销不合格单（仅质量主管/管理员）")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("@perm.has('defect:close')")
    public R<Void> cancel(@PathVariable Long id,
                          @Valid @RequestBody CaseCancelRequest request) {
        defectCaseService.cancel(id, request);
        return R.ok();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
