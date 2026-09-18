package com.qms.modules.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qms.framework.audit.AuditLog;
import com.qms.modules.ledger.LedgerConst;
import com.qms.modules.ledger.entity.GoodsMaterial;
import com.qms.modules.ledger.entity.LedgerFlow;
import com.qms.modules.ledger.entity.LedgerFlowNode;
import com.qms.modules.ledger.entity.LedgerGoods;
import com.qms.modules.ledger.mapper.GoodsMaterialMapper;
import com.qms.modules.ledger.mapper.LedgerFlowMapper;
import com.qms.modules.ledger.mapper.LedgerFlowNodeMapper;
import com.qms.modules.ledger.mapper.LedgerGoodsMapper;
import com.qms.modules.ledger.vo.DemoDataStatsVO;
import com.qms.modules.masterdata.entity.Supplier;
import com.qms.modules.masterdata.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 演示数据服务：统计 DEMO 数据规模、一键清除全部 DEMO 数据。
 * 清除范围：演示商品及其流程实例/节点记录/资料清单 + 演示供应商（均为逻辑删除，留痕可审计）。
 * 保留范围：账号、品类主数据、流程模板/资料项定义、系统配置、data_source=USER 的全部业务数据。
 */
@Service
@RequiredArgsConstructor
public class DemoDataService {

    private final LedgerGoodsMapper goodsMapper;
    private final LedgerFlowMapper flowMapper;
    private final LedgerFlowNodeMapper flowNodeMapper;
    private final GoodsMaterialMapper goodsMaterialMapper;
    private final SupplierMapper supplierMapper;

    /** 统计当前 DEMO 数据量（设置页展示，让管理员二次确认前清楚影响范围） */
    public DemoDataStatsVO stats() {
        DemoDataStatsVO vo = new DemoDataStatsVO();
        List<Long> demoGoodsIds = demoGoodsIds();
        vo.setGoodsCount(demoGoodsIds.size());
        vo.setSupplierCount(supplierMapper.selectCount(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getDataSource, LedgerConst.SOURCE_DEMO)));
        // 演示商品清空后 id 列表为空：必须直接归零，若拼接空 IN 条件会退化为全表计数，误把 USER 数据算进来
        vo.setFlowCount(demoGoodsIds.isEmpty() ? 0L : flowMapper.selectCount(
                new LambdaQueryWrapper<LedgerFlow>().in(LedgerFlow::getGoodsId, demoGoodsIds)));
        vo.setFlowNodeCount(demoGoodsIds.isEmpty() ? 0L : flowNodeMapper.selectCount(
                new LambdaQueryWrapper<LedgerFlowNode>().in(LedgerFlowNode::getGoodsId, demoGoodsIds)));
        vo.setMaterialCount(demoGoodsIds.isEmpty() ? 0L : goodsMaterialMapper.selectCount(
                new LambdaQueryWrapper<GoodsMaterial>().in(GoodsMaterial::getGoodsId, demoGoodsIds)));
        vo.setCleared(false);
        return vo;
    }

    /**
     * 一键清除：只删 data_source=DEMO 的数据。
     * 一个事务内按“子表 → 主表 → 演示供应商”顺序逻辑删除，任何一步失败整体回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "演示数据管理", action = "CLEAR_DEMO", bizType = "ledger_demo")
    public DemoDataStatsVO clear() {
        DemoDataStatsVO before = stats();
        List<Long> demoGoodsIds = demoGoodsIds();
        if (!demoGoodsIds.isEmpty()) {
            goodsMaterialMapper.delete(new LambdaQueryWrapper<GoodsMaterial>()
                    .in(GoodsMaterial::getGoodsId, demoGoodsIds));
            flowNodeMapper.delete(new LambdaQueryWrapper<LedgerFlowNode>()
                    .in(LedgerFlowNode::getGoodsId, demoGoodsIds));
            flowMapper.delete(new LambdaQueryWrapper<LedgerFlow>()
                    .in(LedgerFlow::getGoodsId, demoGoodsIds));
            goodsMapper.delete(new LambdaQueryWrapper<LedgerGoods>()
                    .eq(LedgerGoods::getDataSource, LedgerConst.SOURCE_DEMO));
        }
        supplierMapper.delete(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getDataSource, LedgerConst.SOURCE_DEMO));
        before.setCleared(true);
        return before;
    }

    private List<Long> demoGoodsIds() {
        return goodsMapper.selectList(new LambdaQueryWrapper<LedgerGoods>()
                        .select(LedgerGoods::getId)
                        .eq(LedgerGoods::getDataSource, LedgerConst.SOURCE_DEMO))
                .stream().map(LedgerGoods::getId).toList();
    }
}
