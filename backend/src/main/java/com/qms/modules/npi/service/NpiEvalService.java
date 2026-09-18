package com.qms.modules.npi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qms.common.exception.BizException;
import com.qms.common.result.ResultCode;
import com.qms.framework.audit.AuditLog;
import com.qms.framework.biz.BizNoGenerator;
import com.qms.modules.masterdata.entity.Supplier;
import com.qms.modules.masterdata.mapper.SupplierMapper;
import com.qms.modules.npi.dto.EvalItemInput;
import com.qms.modules.npi.dto.EvalUpsertRequest;
import com.qms.modules.npi.entity.NpiEval;
import com.qms.modules.npi.entity.NpiEvalItem;
import com.qms.modules.npi.mapper.NpiEvalItemMapper;
import com.qms.modules.npi.mapper.NpiEvalMapper;
import com.qms.modules.npi.vo.EvalDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/** 新品送样评估：评分维度整单保存、加权总分、提交后同项目横向排名。 */
@Service
@RequiredArgsConstructor
public class NpiEvalService {

    private final NpiEvalMapper evalMapper;
    private final NpiEvalItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final NpiProjectService projectService;
    private final BizNoGenerator bizNoGenerator;

    public EvalDetailVO detail(Long id) {
        NpiEval eval = getRequired(id);
        EvalDetailVO vo = new EvalDetailVO();
        vo.setEval(eval);
        Supplier supplier = supplierMapper.selectById(eval.getSupplierId());
        vo.setSupplierName(supplier == null ? null : supplier.getSupplierName());
        vo.setItems(listItems(id));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "送样评估", action = "SAVE", bizType = "qc_npi_eval")
    public Long save(EvalUpsertRequest request) {
        projectService.getRequired(request.getProjectId());
        if (supplierMapper.selectById(request.getSupplierId()) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "送样供应商不存在");
        }
        NpiEval eval = request.getId() == null ? new NpiEval() : getRequired(request.getId());
        if (request.getId() != null && "SUBMITTED".equals(eval.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "已提交的评估单不可修改");
        }
        eval.setProjectId(request.getProjectId());
        eval.setSupplierId(request.getSupplierId());
        eval.setRoundNo(request.getRoundNo() == null || request.getRoundNo() < 1 ? 1 : request.getRoundNo());
        eval.setSampleDesc(request.getSampleDesc());
        eval.setReceivedAt(request.getReceivedAt() == null ? LocalDateTime.now() : request.getReceivedAt());
        eval.setQualityConclusion(request.getQualityConclusion() == null ? "PENDING" : request.getQualityConclusion());
        eval.setRemark(request.getRemark());
        if (eval.getStatus() == null) {
            eval.setStatus("DRAFT");
        }
        if (request.getId() == null) {
            eval.setEvalNo(bizNoGenerator.next("PG"));
            eval.setSelectedFlag(0);
            evalMapper.insert(eval);
        } else {
            evalMapper.updateById(eval);
            itemMapper.delete(new LambdaQueryWrapper<NpiEvalItem>().eq(NpiEvalItem::getEvalId, eval.getId()));
        }
        saveItems(eval.getId(), request.getItems());
        return eval.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "送样评估", action = "SUBMIT", bizType = "qc_npi_eval", bizIdExpr = "#id")
    public void submit(Long id) {
        NpiEval eval = getRequired(id);
        if (!"DRAFT".equals(eval.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "仅草稿评估单允许提交");
        }
        List<NpiEvalItem> items = listItems(id);
        if (items.isEmpty()) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "请先完成各维度评分");
        }
        eval.setTotalScore(weightedTotal(items));
        eval.setStatus("SUBMITTED");
        evalMapper.updateById(eval);
        rerank(eval.getProjectId());
        Supplier supplier = supplierMapper.selectById(eval.getSupplierId());
        projectService.addTimeline(eval.getProjectId(), "SOURCING", "EVAL_SUBMIT",
                "供应商送样评估提交：" + (supplier == null ? "" : supplier.getSupplierName())
                        + "，加权总分 " + eval.getTotalScore(), eval.getQualityConclusion());
    }

    // ------------------------------------------------------------------

    private void saveItems(Long evalId, List<EvalItemInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return;
        }
        int sort = 0;
        for (EvalItemInput in : inputs) {
            NpiEvalItem item = new NpiEvalItem();
            item.setEvalId(evalId);
            item.setDimensionCode(in.getDimensionCode().trim());
            item.setDimensionName(in.getDimensionName().trim());
            BigDecimal score = in.getScore();
            if (score != null && (score.signum() < 0 || score.compareTo(new BigDecimal("100")) > 0)) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "维度[" + in.getDimensionName() + "]得分须在 0~100 之间");
            }
            item.setScore(score);
            item.setWeight(in.getWeight() == null ? BigDecimal.valueOf(20) : in.getWeight());
            item.setNote(in.getNote());
            itemMapper.insert(item);
            sort++;
        }
    }

    private BigDecimal weightedTotal(List<NpiEvalItem> items) {
        BigDecimal weightSum = BigDecimal.ZERO;
        BigDecimal scoreSum = BigDecimal.ZERO;
        for (NpiEvalItem item : items) {
            if (item.getScore() == null) {
                continue;
            }
            BigDecimal w = item.getWeight() == null ? BigDecimal.ZERO : item.getWeight();
            weightSum = weightSum.add(w);
            scoreSum = scoreSum.add(item.getScore().multiply(w));
        }
        if (weightSum.signum() == 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "评分维度权重合计不能为 0");
        }
        return scoreSum.divide(weightSum, 2, RoundingMode.HALF_UP);
    }

    /** 同项目已提交评估按加权总分排名（同分同名次顺延，简单实现按序号）。 */
    private void rerank(Long projectId) {
        List<NpiEval> submitted = evalMapper.selectList(new LambdaQueryWrapper<NpiEval>()
                .eq(NpiEval::getProjectId, projectId)
                .eq(NpiEval::getStatus, "SUBMITTED")
                .orderByDesc(NpiEval::getTotalScore));
        int rank = 1;
        for (NpiEval e : submitted) {
            e.setRankNo(rank++);
            evalMapper.updateById(e);
        }
    }

    private List<NpiEvalItem> listItems(Long evalId) {
        return itemMapper.selectList(new LambdaQueryWrapper<NpiEvalItem>()
                .eq(NpiEvalItem::getEvalId, evalId).orderByAsc(NpiEvalItem::getId));
    }

    private NpiEval getRequired(Long id) {
        NpiEval eval = evalMapper.selectById(id);
        if (eval == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "送样评估单不存在");
        }
        return eval;
    }
}
