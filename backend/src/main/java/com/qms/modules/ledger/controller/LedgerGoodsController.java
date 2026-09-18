package com.qms.modules.ledger.controller;

import com.qms.common.exception.BizException;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.common.result.ResultCode;
import com.qms.modules.ledger.dto.GoodsUpsertRequest;
import com.qms.modules.ledger.service.GoodsImportService;
import com.qms.modules.ledger.service.LedgerGoodsService;
import com.qms.modules.ledger.vo.GoodsDetailVO;
import com.qms.modules.ledger.vo.GoodsImportResultVO;
import com.qms.modules.ledger.vo.GoodsListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "商品品控台账")
@RestController
@RequestMapping("/api/v1/ledger/goods")
@RequiredArgsConstructor
public class LedgerGoodsController {

    private final LedgerGoodsService goodsService;
    private final GoodsImportService importService;

    @Operation(summary = "商品台账分页（SKU/名称/品牌/企业关键字 + 品类/结论/节点筛选）")
    @GetMapping
    @PreAuthorize("@perm.has('ledger:goods:list')")
    public R<PageResult<GoodsListVO>> page(PageRequest request,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Long categoryL1Id,
                                           @RequestParam(required = false) Long categoryL2Id,
                                           @RequestParam(required = false) String cooperateResult,
                                           @RequestParam(required = false) String currentNodeCode) {
        return R.ok(goodsService.page(request, keyword, categoryL1Id, categoryL2Id,
                cooperateResult, currentNodeCode));
    }

    @Operation(summary = "商品详情（基础信息 + 流程线 + 资料清单）")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('ledger:goods:view')")
    public R<GoodsDetailVO> detail(@PathVariable Long id) {
        return R.ok(goodsService.detail(id));
    }

    @Operation(summary = "商品建档（自动按模板快照流程节点并初始化资料清单）")
    @PostMapping
    @PreAuthorize("@perm.has('ledger:goods:create')")
    public R<Long> create(@Valid @RequestBody GoodsUpsertRequest request) {
        return R.ok(goodsService.create(request));
    }

    @Operation(summary = "编辑商品基础信息")
    @PutMapping
    @PreAuthorize("@perm.has('ledger:goods:edit')")
    public R<Void> update(@Valid @RequestBody GoodsUpsertRequest request) {
        goodsService.update(request);
        return R.ok();
    }

    @Operation(summary = "删除商品（逻辑删除，需前端二次确认）")
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('ledger:goods:delete')")
    public R<Void> delete(@PathVariable Long id) {
        goodsService.delete(id);
        return R.ok();
    }

    @Operation(summary = "下载商品批量导入标准模板（含填写说明工作表与示例行）")
    @GetMapping("/import-template")
    @PreAuthorize("@perm.has('ledger:goods:import')")
    public ResponseEntity<byte[]> importTemplate() {
        byte[] bytes = importService.templateBytes();
        String fileName = URLEncoder.encode("商品批量导入模板.xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @Operation(summary = "Excel 批量导入（按 SKU 匹配，UPDATE 更新已有/SKIP 跳过已有；供应商按名称匹配缺失自动新建）")
    @PostMapping("/import")
    @PreAuthorize("@perm.has('ledger:goods:import')")
    public R<GoodsImportResultVO> importGoods(@RequestParam("file") MultipartFile file,
                                              @RequestParam(required = false, defaultValue = "UPDATE")
                                              String strategy) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "请选择要导入的 Excel 文件（.xlsx）");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!originalName.toLowerCase().endsWith(".xlsx")) {
            throw new BizException(ResultCode.PARAM_INVALID, "仅支持 .xlsx 格式，请使用标准模板填写后导入");
        }
        return R.ok(importService.importExcel(file.getInputStream(), strategy));
    }
}
