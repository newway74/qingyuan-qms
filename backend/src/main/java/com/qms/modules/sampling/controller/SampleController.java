package com.qms.modules.sampling.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.sampling.dto.SampleDisposeRequest;
import com.qms.modules.sampling.dto.SampleReceiveRequest;
import com.qms.modules.sampling.dto.SampleRetainRequest;
import com.qms.modules.sampling.service.BarcodeService;
import com.qms.modules.sampling.service.SampleService;
import com.qms.modules.sampling.vo.SampleVO;
import com.qms.modules.sampling.vo.SamplingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "样品管理")
@RestController
@RequestMapping("/api/v1/samples")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;
    private final BarcodeService barcodeService;

    @Operation(summary = "样品分页（样品编号/批号/状态/类型/SKU 多维搜索）")
    @GetMapping
    @PreAuthorize("@perm.has('sample:list')")
    public R<PageResult<SampleVO>> page(PageRequest request,
                                        @RequestParam(required = false) String sampleNo,
                                        @RequestParam(required = false) String batchNo,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String sampleType,
                                        @RequestParam(required = false) Long skuId) {
        return R.ok(sampleService.page(request, sampleNo, batchNo, status, sampleType, skuId));
    }

    @Operation(summary = "收样登记（喷码核对、生成样品、自动建检验任务、批次台账）")
    @PostMapping("/receive")
    @PreAuthorize("@perm.has('sample:receive')")
    public R<SamplingVO> receive(@Valid @RequestBody SampleReceiveRequest request) {
        return R.ok(sampleService.receive(request));
    }

    @Operation(summary = "样品详情")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('sample:view')")
    public R<SampleVO> detail(@PathVariable Long id) {
        return R.ok(sampleService.detail(id));
    }

    @Operation(summary = "扫码查询（按样品条码精确匹配）")
    @GetMapping("/barcode/{code}")
    @PreAuthorize("@perm.has('sample:view')")
    public R<SampleVO> barcode(@PathVariable String code) {
        return R.ok(sampleService.barcode(code));
    }

    @Operation(summary = "样品 Code128 条码图片（PNG，供打印）")
    @GetMapping(value = "/{id}/barcode", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("@perm.has('sample:view')")
    public ResponseEntity<byte[]> barcodeImage(@PathVariable Long id,
                                               @RequestParam(required = false, defaultValue = "480") Integer width,
                                               @RequestParam(required = false, defaultValue = "120") Integer height) {
        SampleVO sample = sampleService.detail(id);
        byte[] png = barcodeService.code128Png(sample.getSampleNo(), width, height);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @Operation(summary = "留样登记（留样位置+到期日）")
    @PostMapping("/retain")
    @PreAuthorize("@perm.has('sample:retain')")
    public R<Void> retain(@Valid @RequestBody SampleRetainRequest request) {
        sampleService.retain(request);
        return R.ok();
    }

    @Operation(summary = "样品处置（销毁/退还，留痕）")
    @PostMapping("/dispose")
    @PreAuthorize("@perm.has('sample:dispose')")
    public R<Void> dispose(@Valid @RequestBody SampleDisposeRequest request) {
        sampleService.dispose(request);
        return R.ok();
    }
}
