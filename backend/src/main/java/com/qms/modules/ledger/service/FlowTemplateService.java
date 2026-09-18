package com.qms.modules.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.exception.BizException;
import com.qms.common.result.ResultCode;
import com.qms.modules.ledger.LedgerConst;
import com.qms.modules.ledger.dto.FlowNodeBatchSaveRequest;
import com.qms.modules.ledger.dto.FlowTemplateUpsertRequest;
import com.qms.modules.ledger.entity.FlowNodeDef;
import com.qms.modules.ledger.entity.FlowNodeFieldDef;
import com.qms.modules.ledger.entity.FlowTemplate;
import com.qms.modules.ledger.entity.LedgerGoods;
import com.qms.modules.ledger.mapper.FlowNodeDefMapper;
import com.qms.modules.ledger.mapper.FlowNodeFieldDefMapper;
import com.qms.modules.ledger.mapper.FlowTemplateMapper;
import com.qms.modules.ledger.mapper.LedgerGoodsMapper;
import com.qms.modules.ledger.vo.FlowTemplateDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程模板服务：
 * 模板按“编码 + 版本”行存储；节点配置每次实际变更生成新版本行（旧版本保留，
 * 供已建商品继续沿用其快照），不强制追溯。
 */
@Service
@RequiredArgsConstructor
public class FlowTemplateService {

    private final FlowTemplateMapper templateMapper;
    private final FlowNodeDefMapper nodeDefMapper;
    private final FlowNodeFieldDefMapper fieldDefMapper;
    private final LedgerGoodsMapper goodsMapper;
    private final ObjectMapper objectMapper;

    // ---------------- 查询 ----------------

    /** 模板列表：每个逻辑模板取最新版本行 */
    public List<FlowTemplate> listLatest() {
        List<FlowTemplate> all = templateMapper.selectList(new LambdaQueryWrapper<FlowTemplate>()
                .orderByAsc(FlowTemplate::getTemplateCode)
                .orderByDesc(FlowTemplate::getVersion)
                .orderByDesc(FlowTemplate::getId));
        Map<String, FlowTemplate> latest = new LinkedHashMap<>();
        for (FlowTemplate t : all) {
            latest.putIfAbsent(t.getTemplateCode(), t);
        }
        return new ArrayList<>(latest.values());
    }

    /** 含节点与字段定义的模板详情 */
    public FlowTemplateDetailVO detail(Long id) {
        FlowTemplate template = getRequired(id);
        List<FlowNodeDef> nodes = nodeDefMapper.selectList(new LambdaQueryWrapper<FlowNodeDef>()
                .eq(FlowNodeDef::getTemplateId, id)
                .orderByAsc(FlowNodeDef::getNodeSort));
        List<FlowNodeFieldDef> fields = nodeDefsToFields(nodes);

        FlowTemplateDetailVO vo = new FlowTemplateDetailVO();
        vo.setId(template.getId());
        vo.setTemplateCode(template.getTemplateCode());
        vo.setTemplateName(template.getTemplateName());
        vo.setVersion(template.getVersion());
        vo.setIsPreset(template.getIsPreset());
        vo.setStatus(template.getStatus());
        vo.setRemark(template.getRemark());
        vo.setBoundGoodsCount(countBoundGoods(template.getTemplateCode()));

        Map<Long, List<FlowTemplateDetailVO.FieldDefVO>> fieldMap = toFieldVOMap(fields);
        List<FlowTemplateDetailVO.NodeDefVO> nodeVOs = new ArrayList<>();
        for (FlowNodeDef n : nodes) {
            FlowTemplateDetailVO.NodeDefVO nvo = new FlowTemplateDetailVO.NodeDefVO();
            nvo.setId(n.getId());
            nvo.setNodeCode(n.getNodeCode());
            nvo.setNodeName(n.getNodeName());
            nvo.setNodeSort(n.getNodeSort());
            nvo.setLinkSupplier(n.getLinkSupplier());
            nvo.setRemark(n.getRemark());
            nvo.setFields(fieldMap.getOrDefault(n.getId(), List.of()));
            nodeVOs.add(nvo);
        }
        vo.setNodes(nodeVOs);
        return vo;
    }

    /** 供商品建档选择：当前启用的最新版本模板 */
    public List<FlowTemplate> listEnabledLatest() {
        return listLatest().stream().filter(t -> t.getStatus() != null && t.getStatus() == 1).toList();
    }

    /** 商品建档默认模板：预置模板优先，其次任一启用模板 */
    public FlowTemplate resolveDefaultTemplate() {
        List<FlowTemplate> enabled = listEnabledLatest();
        return enabled.stream()
                .filter(t -> t.getIsPreset() != null && t.getIsPreset() == 1)
                .findFirst()
                .orElse(enabled.stream().findFirst()
                        .orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND,
                                "尚未配置启用的流程模板，请管理员先在“流程模板配置”中创建")));
    }

    public FlowTemplate getRequired(Long id) {
        FlowTemplate template = id == null ? null : templateMapper.selectById(id);
        if (template == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "流程模板不存在或已被删除");
        }
        return template;
    }

    // ---------------- 增改 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Long create(FlowTemplateUpsertRequest request) {
        FlowTemplate template = new FlowTemplate();
        // 预生成雪花 id，以便一次性写入基于 id 的稳定模板编码（template_code 非空）
        long id = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        template.setId(id);
        template.setTemplateCode("FT" + id);
        template.setTemplateName(request.getTemplateName().trim());
        template.setRemark(request.getRemark());
        template.setVersion(1);
        template.setIsPreset(0);
        template.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        templateMapper.insert(template);
        return template.getId();
    }

    /** 编辑模板基本信息（名称/备注同步到该模板全部版本；启停仅作用于目标版本行） */
    @Transactional(rollbackFor = Exception.class)
    public void updateBasic(FlowTemplateUpsertRequest request) {
        FlowTemplate current = getRequired(request.getId());
        List<FlowTemplate> versions = templateMapper.selectList(new LambdaQueryWrapper<FlowTemplate>()
                .eq(FlowTemplate::getTemplateCode, current.getTemplateCode()));
        for (FlowTemplate row : versions) {
            row.setTemplateName(request.getTemplateName().trim());
            row.setRemark(request.getRemark());
            templateMapper.updateById(row);
        }
        if (request.getStatus() != null && !request.getStatus().equals(current.getStatus())) {
            current.setStatus(request.getStatus());
            templateMapper.updateById(current);
        }
    }

    /**
     * 整体替换节点编排：内容有变化则生成新版本行并克隆/重建节点与字段，
     * 旧版本保留给已建商品；内容无变化直接返回当前版本 id。
     *
     * @return 生效的模板版本行 id（可能是新版本）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveNodes(Long templateId, FlowNodeBatchSaveRequest request) {
        FlowTemplate current = getRequired(templateId);
        List<FlowNodeBatchSaveRequest.FlowNodeInput> inputs = normalized(request.getNodes());
        validateNodes(inputs);

        String incomingFingerprint = fingerprint(inputs);
        String currentFingerprint = fingerprintOfExisting(current.getId());
        if (incomingFingerprint.equals(currentFingerprint)) {
            return current.getId();
        }

        // 创建新版本行
        FlowTemplate next = new FlowTemplate();
        next.setTemplateCode(current.getTemplateCode());
        next.setTemplateName(current.getTemplateName());
        next.setVersion(current.getVersion() + 1);
        next.setIsPreset(current.getIsPreset());
        next.setStatus(1);
        next.setRemark(current.getRemark());
        templateMapper.insert(next);

        int index = 0;
        for (FlowNodeBatchSaveRequest.FlowNodeInput nodeIn : inputs) {
            FlowNodeDef node = new FlowNodeDef();
            node.setTemplateId(next.getId());
            node.setNodeCode(generateNodeCode(nodeIn, index));
            node.setNodeName(nodeIn.getNodeName().trim());
            node.setNodeSort(nodeIn.getNodeSort());
            node.setLinkSupplier(nodeIn.getLinkSupplier() == null ? 0 : nodeIn.getLinkSupplier());
            node.setRemark(nodeIn.getRemark());
            nodeDefMapper.insert(node);

            if (nodeIn.getFields() != null) {
                int fIndex = 0;
                for (FlowNodeBatchSaveRequest.FlowNodeFieldInput fieldIn : nodeIn.getFields()) {
                    FlowNodeFieldDef field = new FlowNodeFieldDef();
                    field.setNodeDefId(node.getId());
                    field.setFieldCode(generateFieldCode(fieldIn, fIndex));
                    field.setFieldName(fieldIn.getFieldName().trim());
                    field.setFieldType(fieldIn.getFieldType());
                    field.setRequired(fieldIn.getRequired() == null ? 0 : fieldIn.getRequired());
                    field.setSort(fieldIn.getSort() == null ? fIndex * 10 : fieldIn.getSort());
                    if ("SELECT".equals(fieldIn.getFieldType())) {
                        field.setOptionsJson(writeOptions(fieldIn.getOptions()));
                    }
                    fieldDefMapper.insert(field);
                    fIndex++;
                }
            }
            index++;
        }

        // 旧版本停用（已建商品绑定的节点数据不受影响）
        current.setStatus(0);
        templateMapper.updateById(current);
        return next.getId();
    }

    /** 整体复制模板（含节点与字段），生成新的自建模板 v1 */
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long sourceId, String newName) {
        FlowTemplate source = getRequired(sourceId);
        FlowTemplateUpsertRequest createReq = new FlowTemplateUpsertRequest();
        createReq.setTemplateName(newName == null || newName.isBlank()
                ? source.getTemplateName() + "-副本" : newName.trim());
        createReq.setRemark("复制自模板：" + source.getTemplateName());
        createReq.setStatus(1);
        Long newId = create(createReq);
        FlowTemplate fresh = getRequired(newId);

        List<FlowNodeDef> nodes = nodeDefMapper.selectList(new LambdaQueryWrapper<FlowNodeDef>()
                .eq(FlowNodeDef::getTemplateId, sourceId)
                .orderByAsc(FlowNodeDef::getNodeSort));
        List<FlowNodeFieldDef> allFields = nodeDefsToFields(nodes);
        Map<Long, List<FlowNodeFieldDef>> fieldMap = new HashMap<>();
        for (FlowNodeFieldDef f : allFields) {
            fieldMap.computeIfAbsent(f.getNodeDefId(), k -> new ArrayList<>()).add(f);
        }
        for (FlowNodeDef srcNode : nodes) {
            FlowNodeDef node = new FlowNodeDef();
            node.setTemplateId(fresh.getId());
            node.setNodeCode(srcNode.getNodeCode());
            node.setNodeName(srcNode.getNodeName());
            node.setNodeSort(srcNode.getNodeSort());
            node.setLinkSupplier(srcNode.getLinkSupplier());
            node.setRemark(srcNode.getRemark());
            nodeDefMapper.insert(node);
            for (FlowNodeFieldDef srcField : fieldMap.getOrDefault(srcNode.getId(), List.of())) {
                FlowNodeFieldDef field = new FlowNodeFieldDef();
                field.setNodeDefId(node.getId());
                field.setFieldCode(srcField.getFieldCode());
                field.setFieldName(srcField.getFieldName());
                field.setFieldType(srcField.getFieldType());
                field.setOptionsJson(srcField.getOptionsJson());
                field.setRequired(srcField.getRequired());
                field.setSort(srcField.getSort());
                fieldDefMapper.insert(field);
            }
        }
        return fresh.getId();
    }

    /** 删除模板：预置模板禁删；已有商品使用（任意版本）禁删，可停用 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FlowTemplate current = getRequired(id);
        if (current.getIsPreset() != null && current.getIsPreset() == 1) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "系统预置模板不可删除，可停用或复制后修改");
        }
        Long bound = countBoundGoods(current.getTemplateCode());
        if (bound != null && bound > 0) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID,
                    "已有 " + bound + " 个商品使用该模板，不能删除（可停用，不影响已建商品）");
        }
        List<FlowTemplate> versions = templateMapper.selectList(new LambdaQueryWrapper<FlowTemplate>()
                .eq(FlowTemplate::getTemplateCode, current.getTemplateCode()));
        for (FlowTemplate v : versions) {
            List<FlowNodeDef> nodes = nodeDefMapper.selectList(new LambdaQueryWrapper<FlowNodeDef>()
                    .eq(FlowNodeDef::getTemplateId, v.getId()));
            for (FlowNodeDef n : nodes) {
                fieldDefMapper.delete(new LambdaQueryWrapper<FlowNodeFieldDef>()
                        .eq(FlowNodeFieldDef::getNodeDefId, n.getId()));
                nodeDefMapper.deleteById(n.getId());
            }
            templateMapper.deleteById(v.getId());
        }
    }

    // ---------------- 内部工具 ----------------

    private Long countBoundGoods(String templateCode) {
        List<FlowTemplate> versions = templateMapper.selectList(new LambdaQueryWrapper<FlowTemplate>()
                .select(FlowTemplate::getId)
                .eq(FlowTemplate::getTemplateCode, templateCode));
        if (versions.isEmpty()) {
            return 0L;
        }
        List<Long> ids = versions.stream().map(FlowTemplate::getId).toList();
        return goodsMapper.selectCount(new LambdaQueryWrapper<LedgerGoods>()
                .in(LedgerGoods::getTemplateId, ids));
    }

    private List<FlowNodeFieldDef> nodeDefsToFields(List<FlowNodeDef> nodes) {
        if (nodes.isEmpty()) {
            return List.of();
        }
        List<Long> nodeIds = nodes.stream().map(FlowNodeDef::getId).toList();
        return fieldDefMapper.selectList(new LambdaQueryWrapper<FlowNodeFieldDef>()
                .in(FlowNodeFieldDef::getNodeDefId, nodeIds)
                .orderByAsc(FlowNodeFieldDef::getSort));
    }

    private Map<Long, List<FlowTemplateDetailVO.FieldDefVO>> toFieldVOMap(List<FlowNodeFieldDef> fields) {
        Map<Long, List<FlowTemplateDetailVO.FieldDefVO>> map = new HashMap<>();
        for (FlowNodeFieldDef f : fields) {
            FlowTemplateDetailVO.FieldDefVO fvo = new FlowTemplateDetailVO.FieldDefVO();
            fvo.setId(f.getId());
            fvo.setFieldCode(f.getFieldCode());
            fvo.setFieldName(f.getFieldName());
            fvo.setFieldType(f.getFieldType());
            fvo.setRequired(f.getRequired());
            fvo.setSort(f.getSort());
            fvo.setOptions(readOptions(f.getOptionsJson()));
            map.computeIfAbsent(f.getNodeDefId(), k -> new ArrayList<>()).add(fvo);
        }
        map.values().forEach(list -> list.sort(Comparator.comparing(
                FlowTemplateDetailVO.FieldDefVO::getSort, Comparator.nullsLast(Integer::compareTo))));
        return map;
    }

    /** 排序归一 + 基础校验 */
    private List<FlowNodeBatchSaveRequest.FlowNodeInput> normalized(
            List<FlowNodeBatchSaveRequest.FlowNodeInput> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "流程至少需要一个节点");
        }
        List<FlowNodeBatchSaveRequest.FlowNodeInput> sorted = new ArrayList<>(nodes);
        sorted.sort(Comparator.comparing(n -> n.getNodeSort() == null ? 0 : n.getNodeSort()));
        return sorted;
    }

    private void validateNodes(List<FlowNodeBatchSaveRequest.FlowNodeInput> nodes) {
        Set<String> nodeNames = new HashSet<>();
        int idx = 0;
        for (FlowNodeBatchSaveRequest.FlowNodeInput n : nodes) {
            String name = n.getNodeName().trim();
            if (!nodeNames.add(name)) {
                throw new BizException(ResultCode.PARAM_INVALID, "节点名称重复：" + name);
            }
            if (n.getNodeSort() == null) {
                n.setNodeSort((idx + 1) * 10);
            }
            if (n.getFields() != null) {
                Set<String> fieldNames = new HashSet<>();
                for (FlowNodeBatchSaveRequest.FlowNodeFieldInput f : n.getFields()) {
                    if (!LedgerConst.FIELD_TYPES.contains(f.getFieldType())) {
                        throw new BizException(ResultCode.PARAM_INVALID,
                                "字段类型不合法：" + f.getFieldName() + "（" + f.getFieldType() + "）");
                    }
                    if ("SELECT".equals(f.getFieldType())
                            && (f.getOptions() == null || f.getOptions().isEmpty())) {
                        throw new BizException(ResultCode.PARAM_INVALID,
                                "下拉字段必须配置选项：" + f.getFieldName());
                    }
                    if (!fieldNames.add(f.getFieldName().trim())) {
                        throw new BizException(ResultCode.PARAM_INVALID,
                                "节点【" + name + "】字段名重复：" + f.getFieldName());
                    }
                }
            }
            idx++;
        }
    }

    /** 结构指纹：忽略编码/id，仅比较名称、顺序、类型、选项、必填、供应商关联 */
    private String fingerprint(List<FlowNodeBatchSaveRequest.FlowNodeInput> nodes) {
        List<Object> dump = new ArrayList<>();
        for (FlowNodeBatchSaveRequest.FlowNodeInput n : nodes) {
            List<Object> nodeDump = new ArrayList<>();
            // 节点说明同样属于节点配置，仅改说明也需生成新版本，避免静默丢失
            nodeDump.add(List.of(n.getNodeName().trim(), n.getNodeSort(),
                    n.getLinkSupplier() == null ? 0 : n.getLinkSupplier(),
                    n.getRemark() == null ? "" : n.getRemark().trim()));
            List<Object> fieldsDump = new ArrayList<>();
            if (n.getFields() != null) {
                for (FlowNodeBatchSaveRequest.FlowNodeFieldInput f : n.getFields()) {
                    fieldsDump.add(List.of(f.getFieldName().trim(), f.getFieldType(),
                            f.getRequired() == null ? 0 : f.getRequired(),
                            f.getOptions() == null ? List.of() : f.getOptions(),
                            f.getSort() == null ? 0 : f.getSort()));
                }
            }
            nodeDump.add(fieldsDump);
            dump.add(nodeDump);
        }
        return writeJsonSilent(dump);
    }

    private String fingerprintOfExisting(Long templateId) {
        List<FlowNodeDef> nodes = nodeDefMapper.selectList(new LambdaQueryWrapper<FlowNodeDef>()
                .eq(FlowNodeDef::getTemplateId, templateId)
                .orderByAsc(FlowNodeDef::getNodeSort));
        List<FlowNodeFieldDef> fields = nodeDefsToFields(nodes);
        Map<Long, List<FlowNodeFieldDef>> fieldMap = new HashMap<>();
        for (FlowNodeFieldDef f : fields) {
            fieldMap.computeIfAbsent(f.getNodeDefId(), k -> new ArrayList<>()).add(f);
        }
        List<Object> dump = new ArrayList<>();
        for (FlowNodeDef n : nodes) {
            List<Object> nodeDump = new ArrayList<>();
            nodeDump.add(List.of(n.getNodeName(), n.getNodeSort(), n.getLinkSupplier(),
                    n.getRemark() == null ? "" : n.getRemark()));
            List<Object> fieldsDump = new ArrayList<>();
            for (FlowNodeFieldDef f : fieldMap.getOrDefault(n.getId(), List.of())) {
                fieldsDump.add(List.of(f.getFieldName(), f.getFieldType(), f.getRequired(),
                        readOptions(f.getOptionsJson()), f.getSort()));
            }
            nodeDump.add(fieldsDump);
            dump.add(nodeDump);
        }
        return writeJsonSilent(dump);
    }

    private String generateNodeCode(FlowNodeBatchSaveRequest.FlowNodeInput input, int index) {
        if (input.getNodeCode() != null && !input.getNodeCode().isBlank()) {
            return input.getNodeCode().trim().toUpperCase();
        }
        return "NODE_" + (index + 1);
    }

    private String generateFieldCode(FlowNodeBatchSaveRequest.FlowNodeFieldInput input, int index) {
        if (input.getFieldCode() != null && !input.getFieldCode().isBlank()) {
            return input.getFieldCode().trim();
        }
        return "FIELD_" + (index + 1);
    }

    private String writeOptions(List<String> options) {
        List<String> safe = options == null ? List.of()
                : options.stream().filter(o -> o != null && !o.isBlank()).map(String::trim).toList();
        return writeJsonSilent(safe);
    }

    private List<String> readOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String writeJsonSilent(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "模板内容序列化失败");
        }
    }
}
