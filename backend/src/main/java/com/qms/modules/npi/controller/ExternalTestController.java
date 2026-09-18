package com.qms.modules.npi.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.npi.dto.ExtTestUpsertRequest;
import com.qms.modules.npi.entity.ExternalTest;
import com.qms.modules.npi.service.ExternalTestService;
import com.qms.modules.npi.vo.ExtTestListVO;
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

@Tag(name = "新品国家检测送检")
@RestController
@RequestMapping("/api/v1/npi/ext-tests")
@RequiredArgsConstructor
public class ExternalTestController {

    private final ExternalTestService extTestService;

    @Operation(summary = "外检单分页（全局/按项目）")
    @GetMapping
    @PreAuthorize("@perm.has('npi:exttest:list')")
    public R<PageResult<ExtTestListVO>> page(PageRequest request,
                                             @RequestParam(required = false) Long projectId,
                                             @RequestParam(required = false) String status) {
        return R.ok(extTestService.page(request, projectId, status));
    }

    @Operation(summary = "外检单详情")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('npi:exttest:list')")
    public R<ExternalTest> detail(@PathVariable Long id) {
        return R.ok(extTestService.detail(id));
    }

    @Operation(summary = "登记送检/回填报告（报告号+结论齐全自动置已出报告）")
    @PostMapping
    @PreAuthorize("@perm.has('npi:exttest:edit')")
    public R<Long> save(@Valid @RequestBody ExtTestUpsertRequest request) {
        return R.ok(extTestService.save(request));
    }
}
