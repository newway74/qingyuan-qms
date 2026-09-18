package com.qms.modules.process.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.exception.BizException;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.ResultCode;
import com.qms.common.utils.SecurityUtils;
import com.qms.framework.audit.AuditContext;
import com.qms.framework.audit.AuditLog;
import com.qms.modules.process.dto.NodeBatchSaveRequest;
import com.qms.modules.process.dto.ProcessDefUpsertRequest;
import com.qms.modules.process.dto.ProcessNodeRequest;
import com.qms.modules.process.entity.ProcessChangeLog;
import com.qms.modules.process.entity.ProcessDef;
import com.qms.modules.process.entity.ProcessNode;
import com.qms.modules.process.mapper.ProcessChangeLogMapper;
import com.qms.modules.process.mapper.ProcessDefMapper;
import com.qms.modules.process.mapper.ProcessNodeMapper;
import com.qms.modules.process.vo.ProcessDefDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 品控流程定义版本化服务。
 * 不变量：
 * 1) 一行=一个版本；DRAFT 可编排节点，PUBLISHED 不可变，旧版发布后自动 ARCHIVED；
 * 2) 九节点（SAMPLING/RECEIVE/ASSIGN/INSPECT/REVIEW/JUDGE/DEFECT/RECHECK/ARCHIVE）必须齐全不重复；
 * 3) 每次发布写 qc_process_change_log（只增），diff 含新增/移除/变更节点；
 * 4) process_code 一经使用永久不可复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDefService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 九节点固定模型（顺序仅展示用，实际顺序以 sort 为准） */
    public static final List<String> NODE_CODES = List.of(
            "SAMPLING", "RECEIVE", "ASSIGN", "INSPECT", "REVIEW", "JUDGE", "DEFECT", "RECHECK", "ARCHIVE");

    private static final Set<String> ROLE_CODES = Set.of("SAMPLER", "INSPECTOR", "REVIEWER", "QA_MANAGER", "ADMIN");

    private final ProcessDefMapper defMapper;
    private final ProcessNodeMapper nodeMapper;
    private final ProcessChangeLogMapper changeLogMapper;
    private final ObjectMapper objectMapper;

    // ---------------- 查询 ----------------

    public PageResult<ProcessDef> page(PageRequest request, String processName, String processCode, String status) {
        LambdaQueryWrapper<ProcessDef> wrapper = new LambdaQueryWrapper<ProcessDef>()
                .like(processName != null && !processName.isBlank(), ProcessDef::getProcessName, processName)
                .like(processCode != null && !processCode.isBlank(), ProcessDef::getProcessCode, processCode)
                .eq(status != null && !status.isBlank(), ProcessDef::getStatus, status)
                .orderByDesc(ProcessDef::getProcessCode)
                .orderByDesc(ProcessDef::getVersion);
        Page<ProcessDef> page = defMapper.selectPage(new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        return PageResult.of(page);
    }

    public ProcessDefDetailVO detail(Long id) {
        ProcessDef def = getRequired(id);
        ProcessDefDetailVO vo = new ProcessDefDetailVO();
        vo.setProcessDef(def);
        vo.setNodes(listNodes(id));
        return vo;
    }

    public List<ProcessDef> versions(Long id) {
        ProcessDef def = getRequired(id);
        return defMapper.selectList(new LambdaQueryWrapper<ProcessDef>()
                .eq(ProcessDef::getProcessCode, def.getProcessCode())
                .orderByDesc(ProcessDef::getVersion));
    }

    /** 变更记录（只增，按版本倒序） */
    public List<ProcessChangeLog> changelog(Long id) {
        ProcessDef def = getRequired(id);
        return changeLogMapper.selectList(new LambdaQueryWrapper<ProcessChangeLog>()
                .eq(ProcessChangeLog::getProcessCode, def.getProcessCode())
                .orderByDesc(ProcessChangeLog::getToVersion)
                .orderByDesc(ProcessChangeLog::getId));
    }

    /** 当前生效流程（阶段3在途单据引用） */
    public ProcessDefDetailVO effective() {
        ProcessDef def = defMapper.selectOne(new LambdaQueryWrapper<ProcessDef>()
                .eq(ProcessDef::getStatus, STATUS_PUBLISHED)
                .orderByDesc(ProcessDef::getPublishedAt)
                .last("LIMIT 1"));
        if (def == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "尚无已发布的品控流程定义");
        }
        ProcessDefDetailVO vo = new ProcessDefDetailVO();
        vo.setProcessDef(def);
        vo.setNodes(listNodes(def.getId()));
        return vo;
    }

    // ---------------- 草稿 ----------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "品控流程定义", action = "CREATE", bizType = "qc_process_def")
    public Long create(ProcessDefUpsertRequest request) {
        if (request.getProcessCode() == null || request.getProcessCode().isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "流程编码不能为空");
        }
        ensureCodeUnused(request.getProcessCode().trim());
        ProcessDef def = new ProcessDef();
        apply(def, request);
        def.setProcessCode(request.getProcessCode().trim());
        def.setVersion(1);
        def.setStatus(STATUS_DRAFT);
        defMapper.insert(def);
        return def.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "品控流程定义", action = "UPDATE", bizType = "qc_process_def", bizIdExpr = "#request.id")
    public void update(ProcessDefUpsertRequest request) {
        if (request.getId() == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "id不能为空");
        }
        ProcessDef existing = getRequired(request.getId());
        requireDraft(existing);
        putBefore(existing);
        apply(existing, request);
        existing.setProcessCode(existing.getProcessCode());
        int rows = defMapper.updateById(existing);
        if (rows == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "品控流程定义", action = "CREATE", bizType = "qc_process_def", bizIdExpr = "#id")
    public Long revise(Long id) {
        ProcessDef base = getRequired(id);
        Long openDraft = defMapper.selectCount(new LambdaQueryWrapper<ProcessDef>()
                .eq(ProcessDef::getProcessCode, base.getProcessCode())
                .eq(ProcessDef::getStatus, STATUS_DRAFT));
        if (openDraft != null && openDraft > 0) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "该流程已存在未发布的草稿版本，请先发布后再修订");
        }
        int nextVersion = maxVersion(base.getProcessCode()) + 1;
        ProcessDef draft = new ProcessDef();
        draft.setProcessCode(base.getProcessCode());
        draft.setProcessName(base.getProcessName());
        draft.setVersion(nextVersion);
        draft.setStatus(STATUS_DRAFT);
        draft.setRemark(base.getRemark());
        defMapper.insert(draft);
        copyNodes(base.getId(), draft.getId());
        return draft.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "品控流程节点", action = "UPDATE", bizType = "qc_process_node", bizIdExpr = "#defId")
    public void saveNodes(Long defId, NodeBatchSaveRequest request) {
        ProcessDef def = getRequired(defId);
        requireDraft(def);
        validateNodes(request.getNodes());
        List<ProcessNode> oldNodes = listNodes(defId);
        putBefore(oldNodes);

        for (ProcessNode old : oldNodes) {
            nodeMapper.deleteById(old.getId());
        }
        int index = 0;
        for (ProcessNodeRequest req : request.getNodes()) {
            ProcessNode node = new ProcessNode();
            node.setProcessDefId(defId);
            node.setNodeCode(req.getNodeCode().trim());
            node.setNodeName(req.getNodeName().trim());
            node.setResponsibleRole(req.getResponsibleRole().trim());
            node.setSlaHours(req.getSlaHours() == null ? 0 : req.getSlaHours());
            node.setCalendarType(req.getCalendarType() == null || req.getCalendarType().isBlank()
                    ? "NATURAL" : req.getCalendarType().trim());
            node.setRequiredFields(req.getRequiredFields() == null ? List.of() : req.getRequiredFields());
            node.setTransitionRules(req.getTransitionRules());
            node.setSort(req.getSort() == null ? index : req.getSort());
            nodeMapper.insert(node);
            index++;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "品控流程定义", action = "APPROVE", bizType = "qc_process_def", bizIdExpr = "#id")
    public void publish(Long id) {
        ProcessDef def = getRequired(id);
        requireDraft(def);
        List<ProcessNode> newNodes = listNodes(id);
        validateNodes(newNodes.stream().map(this::toRequest).toList());
        putBefore(def);

        // 找同编码当前发布版（用于 diff 与自动归档）
        ProcessDef oldPublished = defMapper.selectOne(new LambdaQueryWrapper<ProcessDef>()
                .eq(ProcessDef::getProcessCode, def.getProcessCode())
                .eq(ProcessDef::getStatus, STATUS_PUBLISHED)
                .last("LIMIT 1"));
        List<ProcessNode> oldNodes = oldPublished == null ? List.of() : listNodes(oldPublished.getId());

        Map<String, Object> diff = buildDiff(oldNodes, newNodes);

        if (oldPublished != null) {
            List<ProcessDef> allPublished = defMapper.selectList(new LambdaQueryWrapper<ProcessDef>()
                    .eq(ProcessDef::getProcessCode, def.getProcessCode())
                    .eq(ProcessDef::getStatus, STATUS_PUBLISHED));
            for (ProcessDef old : allPublished) {
                old.setStatus(STATUS_ARCHIVED);
                defMapper.updateById(old);
            }
        }

        def.setStatus(STATUS_PUBLISHED);
        def.setPublishedBy(SecurityUtils.getCurrentUserId());
        def.setPublishedAt(LocalDateTime.now());
        int rows = defMapper.updateById(def);
        if (rows == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }

        // 只增变更记录
        ProcessChangeLog changeLog = new ProcessChangeLog();
        changeLog.setProcessDefId(def.getId());
        changeLog.setProcessCode(def.getProcessCode());
        changeLog.setFromVersion(oldPublished == null ? null : oldPublished.getVersion());
        changeLog.setToVersion(def.getVersion());
        changeLog.setChangeDiff(diff);
        changeLog.setChangedBy(SecurityUtils.getCurrentUserId());
        changeLog.setChangedAt(LocalDateTime.now());
        changeLog.setTenantId(SecurityUtils.getTenantId());
        changeLogMapper.insert(changeLog);
    }

    // ------------------------------------------------------------------

    /**
     * 节点发布 diff：added/removed/changed。changed 记录字段级前后值。
     */
    private Map<String, Object> buildDiff(List<ProcessNode> oldNodes, List<ProcessNode> newNodes) {
        Map<String, ProcessNode> oldMap = oldNodes.stream()
                .collect(Collectors.toMap(ProcessNode::getNodeCode, n -> n, (a, b) -> a, LinkedHashMap::new));
        Map<String, ProcessNode> newMap = newNodes.stream()
                .collect(Collectors.toMap(ProcessNode::getNodeCode, n -> n, (a, b) -> a, LinkedHashMap::new));

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<Map<String, Object>> changed = new ArrayList<>();

        for (String code : newMap.keySet()) {
            if (!oldMap.containsKey(code)) {
                added.add(code);
            } else {
                Map<String, Object[]> fieldDiff = diffNodeFields(oldMap.get(code), newMap.get(code));
                if (!fieldDiff.isEmpty()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("nodeCode", code);
                    Map<String, Object> before = new LinkedHashMap<>();
                    Map<String, Object> after = new LinkedHashMap<>();
                    fieldDiff.forEach((field, pair) -> {
                        before.put(field, pair[0]);
                        after.put(field, pair[1]);
                    });
                    item.put("before", before);
                    item.put("after", after);
                    changed.add(item);
                }
            }
        }
        for (String code : oldMap.keySet()) {
            if (!newMap.containsKey(code)) {
                removed.add(code);
            }
        }

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("added", added);
        diff.put("removed", removed);
        diff.put("changed", changed);
        return diff;
    }

    private Map<String, Object[]> diffNodeFields(ProcessNode o, ProcessNode n) {
        Map<String, Object[]> diff = new LinkedHashMap<>();
        putIfDifferent(diff, "nodeName", o.getNodeName(), n.getNodeName());
        putIfDifferent(diff, "responsibleRole", o.getResponsibleRole(), n.getResponsibleRole());
        putIfDifferent(diff, "slaHours", o.getSlaHours(), n.getSlaHours());
        putIfDifferent(diff, "calendarType", o.getCalendarType(), n.getCalendarType());
        putIfDifferent(diff, "requiredFields", o.getRequiredFields(), n.getRequiredFields());
        putIfDifferent(diff, "transitionRules", o.getTransitionRules(), n.getTransitionRules());
        putIfDifferent(diff, "sort", o.getSort(), n.getSort());
        return diff;
    }

    private void putIfDifferent(Map<String, Object[]> diff, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            diff.put(field, new Object[]{before, after});
        }
    }

    private void validateNodes(List<ProcessNodeRequest> nodes) {
        Set<String> codes = new java.util.HashSet<>();
        for (ProcessNodeRequest node : nodes) {
            String code = node.getNodeCode().trim();
            if (!NODE_CODES.contains(code)) {
                throw new BizException(ResultCode.PARAM_INVALID, "非法流程节点：" + code);
            }
            if (!codes.add(code)) {
                throw new BizException(ResultCode.PARAM_INVALID, "流程节点重复：" + code);
            }
            if (!ROLE_CODES.contains(node.getResponsibleRole().trim())) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "节点[" + node.getNodeName() + "]责任角色非法：" + node.getResponsibleRole());
            }
            if (node.getCalendarType() != null && !node.getCalendarType().isBlank()
                    && !Set.of("NATURAL", "WORKDAY").contains(node.getCalendarType().trim())) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "节点[" + node.getNodeName() + "]日历类型仅支持 NATURAL/WORKDAY");
            }
        }
        if (!codes.containsAll(NODE_CODES)) {
            List<String> missing = NODE_CODES.stream().filter(c -> !codes.contains(c)).toList();
            throw new BizException(ResultCode.PARAM_INVALID, "九节点不齐全，缺少：" + String.join(",", missing));
        }
    }

    private ProcessNodeRequest toRequest(ProcessNode node) {
        ProcessNodeRequest req = new ProcessNodeRequest();
        req.setNodeCode(node.getNodeCode());
        req.setNodeName(node.getNodeName());
        req.setResponsibleRole(node.getResponsibleRole());
        req.setSlaHours(node.getSlaHours());
        req.setCalendarType(node.getCalendarType());
        req.setRequiredFields(node.getRequiredFields());
        req.setTransitionRules(node.getTransitionRules());
        req.setSort(node.getSort());
        return req;
    }

    private void apply(ProcessDef def, ProcessDefUpsertRequest request) {
        def.setProcessName(request.getProcessName().trim());
        def.setRemark(request.getRemark() == null ? null : request.getRemark().trim());
    }

    private List<ProcessNode> listNodes(Long defId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<ProcessNode>()
                .eq(ProcessNode::getProcessDefId, defId)
                .orderByAsc(ProcessNode::getSort)
                .orderByAsc(ProcessNode::getId));
    }

    private void copyNodes(Long fromDefId, Long toDefId) {
        for (ProcessNode src : listNodes(fromDefId)) {
            ProcessNode node = new ProcessNode();
            node.setProcessDefId(toDefId);
            node.setNodeCode(src.getNodeCode());
            node.setNodeName(src.getNodeName());
            node.setResponsibleRole(src.getResponsibleRole());
            node.setSlaHours(src.getSlaHours());
            node.setCalendarType(src.getCalendarType());
            node.setRequiredFields(src.getRequiredFields());
            node.setTransitionRules(src.getTransitionRules());
            node.setSort(src.getSort());
            nodeMapper.insert(node);
        }
    }

    private int maxVersion(String processCode) {
        return defMapper.selectList(new LambdaQueryWrapper<ProcessDef>()
                        .eq(ProcessDef::getProcessCode, processCode))
                .stream().mapToInt(ProcessDef::getVersion).max().orElse(0);
    }

    private void ensureCodeUnused(String code) {
        Long count = defMapper.countByCodeIncludeDeleted(SecurityUtils.getTenantId(), code);
        if (count != null && count > 0) {
            throw new BizException(ResultCode.DATA_DUPLICATED, "流程编码已被占用（编码一经使用不可复用）");
        }
    }

    private void requireDraft(ProcessDef def) {
        if (!STATUS_DRAFT.equals(def.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "仅草稿状态的流程定义允许该操作（已发布版本不可变）");
        }
    }

    private ProcessDef getRequired(Long id) {
        ProcessDef def = defMapper.selectById(id);
        if (def == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "流程定义不存在");
        }
        return def;
    }

    private void putBefore(Object entity) {
        try {
            AuditContext.putBefore(objectMapper.writeValueAsString(entity));
        } catch (Exception e) {
            log.warn("流程 before 快照序列化失败: {}", e.getMessage());
        }
    }
}
