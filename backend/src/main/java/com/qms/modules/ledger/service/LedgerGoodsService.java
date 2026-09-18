package com.qms.modules.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.exception.BizException;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.ResultCode;
import com.qms.common.utils.SecurityUtils;
import com.qms.modules.ledger.LedgerConst;
import com.qms.modules.ledger.dto.GoodsUpsertRequest;
import com.qms.modules.ledger.entity.FlowNodeDef;
import com.qms.modules.ledger.entity.FlowNodeFieldDef;
import com.qms.modules.ledger.entity.FlowTemplate;
import com.qms.modules.ledger.entity.GoodsMaterial;
import com.qms.modules.ledger.entity.LedgerFlow;
import com.qms.modules.ledger.entity.LedgerFlowNode;
import com.qms.modules.ledger.entity.LedgerGoods;
import com.qms.modules.ledger.entity.MaterialItem;
import com.qms.modules.ledger.mapper.FlowNodeDefMapper;
import com.qms.modules.ledger.mapper.FlowNodeFieldDefMapper;
import com.qms.modules.ledger.mapper.FlowTemplateMapper;
import com.qms.modules.ledger.mapper.GoodsMaterialMapper;
import com.qms.modules.ledger.mapper.LedgerFlowMapper;
import com.qms.modules.ledger.mapper.LedgerFlowNodeMapper;
import com.qms.modules.ledger.mapper.LedgerGoodsMapper;
import com.qms.modules.ledger.mapper.MaterialItemMapper;
import com.qms.modules.ledger.vo.FlowTemplateDetailVO;
import com.qms.modules.ledger.vo.GoodsDetailVO;
import com.qms.modules.ledger.vo.GoodsListVO;
import com.qms.modules.masterdata.entity.Category;
import com.qms.modules.masterdata.entity.Supplier;
import com.qms.modules.masterdata.mapper.CategoryMapper;
import com.qms.modules.masterdata.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品台账服务：商品 CRUD、建档时流程节点快照与资料清单初始化、详情聚合。
 * 事务边界统一在本服务：商品主表 + 流程实例/节点 + 资料清单必须同生共死。
 */
@Service
@RequiredArgsConstructor
public class LedgerGoodsService {

    private final LedgerGoodsMapper goodsMapper;
    private final LedgerFlowMapper flowMapper;
    private final LedgerFlowNodeMapper flowNodeMapper;
    private final GoodsMaterialMapper goodsMaterialMapper;
    private final MaterialItemMapper materialItemMapper;
    private final FlowTemplateMapper templateMapper;
    private final FlowNodeDefMapper nodeDefMapper;
    private final FlowNodeFieldDefMapper fieldDefMapper;
    private final FlowTemplateService flowTemplateService;
    private final MaterialService materialService;
    private final CategoryMapper categoryMapper;
    private final SupplierMapper supplierMapper;
    private final ObjectMapper objectMapper;

    // ---------------- 查询 ----------------

    public PageResult<GoodsListVO> page(PageRequest request, String keyword, Long categoryL1Id,
                                        Long categoryL2Id, String cooperateResult,
                                        String currentNodeCode) {
        Page<GoodsListVO> page = new Page<>(request.getPageNo(), request.getPageSize());
        String kw = keyword == null ? null : keyword.trim();
        return PageResult.of(goodsMapper.selectGoodsPage(page, kw, categoryL1Id, categoryL2Id,
                cooperateResult, currentNodeCode));
    }

    public GoodsDetailVO detail(Long id) {
        LedgerGoods goods = getRequired(id);
        GoodsDetailVO vo = new GoodsDetailVO();
        vo.setId(goods.getId());
        vo.setSku(goods.getSku());
        vo.setCommonName(goods.getCommonName());
        vo.setSpec(goods.getSpec());
        vo.setManufacturer(goods.getManufacturer());
        vo.setApprovalNo(goods.getApprovalNo());
        vo.setUpc(goods.getUpc());
        vo.setBrand(goods.getBrand());
        vo.setCategoryL1Id(goods.getCategoryL1Id());
        vo.setCategoryL2Id(goods.getCategoryL2Id());
        vo.setMeetingDate(goods.getMeetingDate());
        vo.setCooperateResult(goods.getCooperateResult());
        vo.setLaunchDate(goods.getLaunchDate());
        vo.setCurrentNodeCode(goods.getCurrentNodeCode());
        vo.setCurrentNodeName(goods.getCurrentNodeName());
        vo.setTemplateId(goods.getTemplateId());
        vo.setTemplateVersion(goods.getTemplateVersion());
        vo.setNpiProjectId(goods.getNpiProjectId());
        vo.setDataSource(goods.getDataSource());
        vo.setRemark(goods.getRemark());

        fillCategoryNames(vo, goods);
        fillFlowPart(vo, goods);
        fillMaterials(vo, goods);
        return vo;
    }

    private void fillCategoryNames(GoodsDetailVO vo, LedgerGoods goods) {
        List<Long> ids = new ArrayList<>(2);
        if (goods.getCategoryL1Id() != null) {
            ids.add(goods.getCategoryL1Id());
        }
        if (goods.getCategoryL2Id() != null) {
            ids.add(goods.getCategoryL2Id());
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> names = categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        vo.setCategoryL1Name(names.get(goods.getCategoryL1Id()));
        vo.setCategoryL2Name(names.get(goods.getCategoryL2Id()));
    }

    private void fillFlowPart(GoodsDetailVO vo, LedgerGoods goods) {
        if (goods.getTemplateId() == null) {
            vo.setNodes(List.of());
            vo.setGapCount(0L);
            return;
        }
        FlowTemplate bound = templateMapper.selectById(goods.getTemplateId());
        if (bound != null) {
            vo.setTemplateName(bound.getTemplateName());
            templateMapper.selectList(new LambdaQueryWrapper<FlowTemplate>()
                            .eq(FlowTemplate::getTemplateCode, bound.getTemplateCode())
                            .orderByDesc(FlowTemplate::getVersion))
                    .stream().findFirst()
                    .ifPresent(latest -> {
                        vo.setLatestTemplateVersion(latest.getVersion());
                        vo.setTemplateOutdated(!Objects.equals(latest.getVersion(), goods.getTemplateVersion()));
                    });
        }

        // 节点字段定义（取商品绑定版本，保持快照一致）
        List<FlowNodeDef> defs = nodeDefMapper.selectList(new LambdaQueryWrapper<FlowNodeDef>()
                .eq(FlowNodeDef::getTemplateId, goods.getTemplateId())
                .orderByAsc(FlowNodeDef::getNodeSort));
        Map<String, FlowNodeDef> defByCode = defs.stream()
                .collect(Collectors.toMap(FlowNodeDef::getNodeCode, d -> d, (a, b) -> a));
        List<Long> defIds = defs.stream().map(FlowNodeDef::getId).toList();
        Map<Long, List<FlowNodeFieldDef>> fieldsByNode = new HashMap<>();
        if (!defIds.isEmpty()) {
            fieldDefMapper.selectList(new LambdaQueryWrapper<FlowNodeFieldDef>()
                            .in(FlowNodeFieldDef::getNodeDefId, defIds)
                            .orderByAsc(FlowNodeFieldDef::getSort))
                    .forEach(f -> fieldsByNode.computeIfAbsent(f.getNodeDefId(), k -> new ArrayList<>()).add(f));
        }

        List<LedgerFlowNode> records = flowNodeMapper.selectList(new LambdaQueryWrapper<LedgerFlowNode>()
                .eq(LedgerFlowNode::getGoodsId, goods.getId())
                .orderByAsc(LedgerFlowNode::getNodeSort));
        Set<Long> supplierIds = records.stream().map(LedgerFlowNode::getSupplierId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> supplierNames = supplierIds.isEmpty() ? Map.of()
                : supplierMapper.selectBatchIds(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getSupplierName, (a, b) -> a));

        List<GoodsDetailVO.FlowNodeVO> nodeVOs = new ArrayList<>();
        for (LedgerFlowNode r : records) {
            GoodsDetailVO.FlowNodeVO nvo = new GoodsDetailVO.FlowNodeVO();
            nvo.setId(r.getId());
            nvo.setNodeCode(r.getNodeCode());
            nvo.setNodeName(r.getNodeName());
            nvo.setNodeSort(r.getNodeSort());
            nvo.setLinkSupplier(r.getLinkSupplier());
            nvo.setSupplierId(r.getSupplierId());
            nvo.setSupplierName(r.getSupplierId() == null ? null : supplierNames.get(r.getSupplierId()));
            nvo.setStatus(r.getStatus());
            nvo.setFinishDate(r.getFinishDate());
            nvo.setOwnerName(r.getOwnerName());
            nvo.setConclusion(r.getConclusion());
            nvo.setRemark(r.getRemark());
            nvo.setFieldValues(parseFieldValues(r.getFieldValues()));
            FlowNodeDef def = defByCode.get(r.getNodeCode());
            if (def != null) {
                nvo.setFieldDefs(toFieldVOs(fieldsByNode.getOrDefault(def.getId(), List.of())));
            } else {
                nvo.setFieldDefs(List.of());
            }
            nodeVOs.add(nvo);
        }
        vo.setNodes(nodeVOs);
    }

    private void fillMaterials(GoodsDetailVO vo, LedgerGoods goods) {
        List<GoodsMaterial> rows = goodsMaterialMapper.selectList(new LambdaQueryWrapper<GoodsMaterial>()
                .eq(GoodsMaterial::getGoodsId, goods.getId())
                .orderByAsc(GoodsMaterial::getId));
        Set<Long> itemIds = rows.stream().map(GoodsMaterial::getItemId).collect(Collectors.toSet());
        Map<Long, MaterialItem> itemMap = itemIds.isEmpty() ? Map.of()
                : materialItemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(MaterialItem::getId, i -> i, (a, b) -> a));

        List<GoodsDetailVO.MaterialVO> list = new ArrayList<>();
        long gap = 0;
        for (GoodsMaterial m : rows) {
            GoodsDetailVO.MaterialVO mvo = new GoodsDetailVO.MaterialVO();
            mvo.setId(m.getId());
            mvo.setItemId(m.getItemId());
            MaterialItem itemDef = itemMap.get(m.getItemId());
            mvo.setItemCode(itemDef == null ? null : itemDef.getItemCode());
            mvo.setItemName(m.getItemName());
            mvo.setStatus(m.getStatus());
            mvo.setRemark(m.getRemark());
            // 资料项被停用或删除后，历史清单保留展示，但不再算作重点缺口
            boolean enabled = itemDef != null && Integer.valueOf(1).equals(itemDef.getStatus());
            mvo.setEnabled(enabled);
            list.add(mvo);
            if (enabled && (LedgerConst.MATERIAL_MISSING.equals(m.getStatus())
                    || LedgerConst.MATERIAL_PENDING.equals(m.getStatus()))) {
                gap++;
            }
        }
        vo.setMaterials(list);
        vo.setGapCount(gap);
    }

    private List<FlowTemplateDetailVO.FieldDefVO> toFieldVOs(List<FlowNodeFieldDef> defs) {
        List<FlowTemplateDetailVO.FieldDefVO> result = new ArrayList<>();
        for (FlowNodeFieldDef f : defs) {
            FlowTemplateDetailVO.FieldDefVO fvo = new FlowTemplateDetailVO.FieldDefVO();
            fvo.setId(f.getId());
            fvo.setFieldCode(f.getFieldCode());
            fvo.setFieldName(f.getFieldName());
            fvo.setFieldType(f.getFieldType());
            fvo.setRequired(f.getRequired());
            fvo.setSort(f.getSort());
            fvo.setOptions(parseOptions(f.getOptionsJson()));
            result.add(fvo);
        }
        return result;
    }

    private Map<String, Object> parseFieldValues(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private List<String> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    // ---------------- 增改删 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Long create(GoodsUpsertRequest request) {
        return createInternal(request, LedgerConst.SOURCE_USER);
    }

    /**
     * 建档内部实现（用户建档/Excel 导入/演示数据共用）：
     * 校验品类与 SKU → 确定模板版本 → 写主表 → 快照流程节点 → 初始化资料清单 → 回填当前节点。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createInternal(GoodsUpsertRequest request, String dataSource) {
        validateCategories(request.getCategoryL1Id(), request.getCategoryL2Id());
        Long tenantId = SecurityUtils.getTenantId();
        Long dup = goodsMapper.countBySkuIncludeDeleted(tenantId, request.getSku().trim(), null);
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.DATA_DUPLICATED,
                    "SKU 已存在（含已删除记录，SKU 不可复用）：" + request.getSku());
        }

        FlowTemplate template = request.getTemplateId() == null
                ? flowTemplateService.resolveDefaultTemplate()
                : flowTemplateService.getRequired(request.getTemplateId());

        LedgerGoods goods = new LedgerGoods();
        applyEditableFields(goods, request);
        goods.setDataSource(dataSource);
        goods.setCooperateResult(request.getCooperateResult() == null
                || request.getCooperateResult().isBlank()
                ? LedgerConst.RESULT_PENDING : request.getCooperateResult());
        goods.setTemplateId(template.getId());
        goods.setTemplateVersion(template.getVersion());

        List<FlowNodeDef> defs = nodeDefMapper.selectList(new LambdaQueryWrapper<FlowNodeDef>()
                .eq(FlowNodeDef::getTemplateId, template.getId())
                .orderByAsc(FlowNodeDef::getNodeSort));
        if (defs.isEmpty()) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID,
                    "所选流程模板尚未配置节点，请先在“流程模板配置”中编排节点");
        }
        FlowNodeDef first = defs.get(0);
        goods.setCurrentNodeCode(first.getNodeCode());
        goods.setCurrentNodeName(first.getNodeName());
        goodsMapper.insert(goods);

        LedgerFlow flow = new LedgerFlow();
        flow.setGoodsId(goods.getId());
        flow.setTemplateId(template.getId());
        flow.setTemplateVersion(template.getVersion());
        flowMapper.insert(flow);

        for (FlowNodeDef def : defs) {
            LedgerFlowNode node = new LedgerFlowNode();
            node.setFlowId(flow.getId());
            node.setGoodsId(goods.getId());
            node.setNodeCode(def.getNodeCode());
            node.setNodeName(def.getNodeName());
            node.setNodeSort(def.getNodeSort());
            node.setLinkSupplier(def.getLinkSupplier());
            node.setStatus(LedgerConst.NODE_NOT_STARTED);
            flowNodeMapper.insert(node);
        }

        materialService.initForGoods(goods.getId());
        return goods.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(GoodsUpsertRequest request) {
        if (request.getId() == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "商品 id 不能为空");
        }
        LedgerGoods goods = getRequired(request.getId());
        validateCategories(request.getCategoryL1Id(), request.getCategoryL2Id());
        Long dup = goodsMapper.countBySkuIncludeDeleted(
                SecurityUtils.getTenantId(), request.getSku().trim(), goods.getId());
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.DATA_DUPLICATED, "SKU 已被其他商品占用：" + request.getSku());
        }
        applyEditableFields(goods, request);
        if (request.getCooperateResult() != null && !request.getCooperateResult().isBlank()) {
            goods.setCooperateResult(request.getCooperateResult());
        }
        // 模板绑定在建档时快照确定，不允许事后更换（更换会导致流程线与历史记录错位）
        goodsMapper.updateById(goods);
    }

    /** 删除商品：主表/流程实例/节点记录/资料清单一并逻辑删除（附件只增保留留痕） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getRequired(id);
        LedgerFlow flow = flowMapper.selectOne(new LambdaQueryWrapper<LedgerFlow>()
                .eq(LedgerFlow::getGoodsId, id));
        if (flow != null) {
            flowNodeMapper.delete(new LambdaQueryWrapper<LedgerFlowNode>()
                    .eq(LedgerFlowNode::getGoodsId, id));
            flowMapper.deleteById(flow.getId());
        }
        goodsMaterialMapper.delete(new LambdaQueryWrapper<GoodsMaterial>()
                .eq(GoodsMaterial::getGoodsId, id));
        goodsMapper.deleteById(id);
    }

    public LedgerGoods getRequired(Long id) {
        LedgerGoods goods = id == null ? null : goodsMapper.selectById(id);
        if (goods == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "商品不存在或已被删除");
        }
        return goods;
    }

    /**
     * 节点办结后回写当前节点（全部完成时需置空，不能用 updateById 的非空策略，
     * 必须显式 set，否则 current_node_code 置不了 NULL）。
     */
    public void updateCurrentNode(LedgerGoods goods) {
        goodsMapper.update(null, new LambdaUpdateWrapper<LedgerGoods>()
                .eq(LedgerGoods::getId, goods.getId())
                .set(LedgerGoods::getCurrentNodeCode, goods.getCurrentNodeCode())
                .set(LedgerGoods::getCurrentNodeName, goods.getCurrentNodeName()));
    }

    private void applyEditableFields(LedgerGoods goods, GoodsUpsertRequest request) {
        goods.setSku(request.getSku().trim());
        goods.setCommonName(request.getCommonName().trim());
        goods.setSpec(trimToNull(request.getSpec()));
        goods.setManufacturer(trimToNull(request.getManufacturer()));
        goods.setApprovalNo(trimToNull(request.getApprovalNo()));
        goods.setUpc(trimToNull(request.getUpc()));
        goods.setBrand(trimToNull(request.getBrand()));
        goods.setCategoryL1Id(request.getCategoryL1Id());
        goods.setCategoryL2Id(request.getCategoryL2Id());
        goods.setMeetingDate(request.getMeetingDate());
        goods.setLaunchDate(request.getLaunchDate());
        goods.setNpiProjectId(request.getNpiProjectId());
        goods.setRemark(trimToNull(request.getRemark()));
    }

    private void validateCategories(Long l1, Long l2) {
        List<Long> ids = new ArrayList<>(2);
        if (l1 != null) {
            ids.add(l1);
        }
        if (l2 != null) {
            ids.add(l2);
        }
        if (ids.isEmpty()) {
            return;
        }
        Long found = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .in(Category::getId, ids)
                .eq(Category::getStatus, 1));
        if (found == null || found != ids.size()) {
            throw new BizException(ResultCode.PARAM_INVALID, "所选品类不存在或已停用");
        }
        if (l1 != null && l2 != null) {
            Category second = categoryMapper.selectById(l2);
            if (second == null || !Objects.equals(second.getParentId(), l1)) {
                throw new BizException(ResultCode.PARAM_INVALID, "二级品类不属于所选一级品类，请重新选择");
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    /** 供其他服务（节点记录/统计/导入）复用的空值安全 JSON 序列化 */
    String writeFieldValues(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "节点字段内容序列化失败");
        }
    }

    Map<String, Object> readFieldValues(String json) {
        return Collections.unmodifiableMap(parseFieldValues(json));
    }
}
