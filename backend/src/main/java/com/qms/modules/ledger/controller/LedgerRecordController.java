package com.qms.modules.ledger.controller;

import com.qms.common.result.R;
import com.qms.modules.ledger.dto.GoodsMaterialUpsertRequest;
import com.qms.modules.ledger.dto.NodeRecordUpsertRequest;
import com.qms.modules.ledger.service.LedgerFlowNodeService;
import com.qms.modules.ledger.service.MaterialService;
import com.qms.modules.ledger.vo.SupplierOptionVO;
import com.qms.modules.masterdata.entity.Supplier;
import com.qms.modules.masterdata.mapper.SupplierMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品全流程办理：节点记录录入、资料状态维护，以及节点关联供应商的选项查询。
 */
@Tag(name = "商品品控台账-流程办理")
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerRecordController {

    private final LedgerFlowNodeService flowNodeService;
    private final MaterialService materialService;
    private final SupplierMapper supplierMapper;

    @Operation(summary = "录入/更新商品某流程节点的办理结果（办结后自动推进当前节点）")
    @PutMapping("/goods/{goodsId}/nodes/{nodeCode}")
    @PreAuthorize("@perm.has('ledger:goods:edit')")
    public R<Void> updateNode(@PathVariable Long goodsId,
                              @PathVariable String nodeCode,
                              @Valid @RequestBody NodeRecordUpsertRequest request) {
        flowNodeService.updateNode(goodsId, nodeCode, request);
        return R.ok();
    }

    @Operation(summary = "更新商品某项资料的齐套状态/备注（佐证附件走附件接口）")
    @PutMapping("/goods/{goodsId}/materials/{materialId}")
    @PreAuthorize("@perm.has('ledger:goods:edit')")
    public R<Void> updateMaterial(@PathVariable Long goodsId,
                                  @PathVariable Long materialId,
                                  @Valid @RequestBody GoodsMaterialUpsertRequest request) {
        materialService.updateGoodsMaterial(goodsId, materialId,
                request.getStatus().trim(), request.getRemark());
        return R.ok();
    }

    @Operation(summary = "可关联供应商选项（停⽤供应商除外，供准入/验厂节点选择）")
    @GetMapping("/supplier-options")
    @PreAuthorize("@perm.has('ledger:goods:view')")
    public R<List<SupplierOptionVO>> supplierOptions() {
        List<Supplier> suppliers = supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .ne(Supplier::getStatus, "DISABLED")
                .orderByAsc(Supplier::getSupplierCode));
        return R.ok(suppliers.stream().map(s -> {
            SupplierOptionVO vo = new SupplierOptionVO();
            vo.setId(s.getId());
            vo.setSupplierCode(s.getSupplierCode());
            vo.setSupplierName(s.getSupplierName());
            vo.setStatus(s.getStatus());
            return vo;
        }).toList());
    }
}
