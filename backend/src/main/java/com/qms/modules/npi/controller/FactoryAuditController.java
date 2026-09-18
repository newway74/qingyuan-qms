package com.qms.modules.npi.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.npi.dto.AuditUpsertRequest;
import com.qms.modules.npi.entity.FactoryAuditItem;
import com.qms.modules.npi.service.FactoryAuditService;
import com.qms.modules.npi.vo.AuditDetailVO;
import com.qms.modules.npi.vo.AuditListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "新品实地验厂")
@RestController
@RequestMapping("/api/v1/npi/audits")
@RequiredArgsConstructor
public class FactoryAuditController {

    private final FactoryAuditService auditService;

    @Operation(summary = "验厂单分页（全局/按项目）")
    @GetMapping
    @PreAuthorize("@perm.has('npi:audit:list')")
    public R<PageResult<AuditListVO>> page(PageRequest request,
                                           @RequestParam(required = false) Long projectId,
                                           @RequestParam(required = false) String status) {
        return R.ok(auditService.page(request, projectId, status));
    }

    @Operation(summary = "验厂默认检查表（五大类）")
    @GetMapping("/default-items")
    @PreAuthorize("@perm.has('npi:audit:list')")
    public R<List<FactoryAuditItem>> defaultItems() {
        return R.ok(auditService.defaultItems());
    }

    @Operation(summary = "验厂单详情（含检查项）")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('npi:audit:list')")
    public R<AuditDetailVO> detail(@PathVariable Long id) {
        return R.ok(auditService.detail(id));
    }

    @Operation(summary = "新建/评分/结论确认（confirmConclusion=PASS/CONDITIONAL/FAIL）")
    @PostMapping
    @PreAuthorize("@perm.has('npi:audit:edit')")
    public R<Long> save(@Valid @RequestBody AuditUpsertRequest request) {
        return R.ok(auditService.save(request));
    }
}
