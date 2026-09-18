package com.qms.modules.ledger.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qms.modules.ledger.dto.FlowRecordExportRow;
import com.qms.modules.ledger.dto.GoodsLedgerExportRow;
import com.qms.modules.ledger.dto.MaterialGapExportRow;
import com.qms.modules.ledger.vo.MaterialGapVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 商品品控工作台统计与报表 Mapper（只读）。
 */
@Mapper
public interface LedgerDashboardMapper {

    /** 顶部四张卡片的计数（单行：goodsTotal/supplierTotal/flowRecordTotal/gapItemTotal） */
    Map<String, Object> selectCards();

    /** 过会状态分布：cooperate_result 编码 → 数量 */
    List<Map<String, Object>> selectCooperateResultDist();

    /** 当前节点分布（current_node_code 为空的归入“已完成全流程”） */
    List<Map<String, Object>> selectCurrentNodeDist();

    /** 一级品类分布 */
    List<Map<String, Object>> selectCategoryDist();

    /** 资料重点缺口明细分页（gapItemsRaw 为 GROUP_CONCAT 聚合串，由 Service 拆分） */
    IPage<MaterialGapVO> selectGapPage(IPage<MaterialGapVO> page,
                                       @Param("keyword") String keyword);

    /** 商品台账导出（筛选条件与列表页一致，全量） */
    List<GoodsLedgerExportRow> selectGoodsForExport(@Param("keyword") String keyword,
                                                    @Param("categoryL1Id") Long categoryL1Id,
                                                    @Param("categoryL2Id") Long categoryL2Id,
                                                    @Param("cooperateResult") String cooperateResult,
                                                    @Param("currentNodeCode") String currentNodeCode);

    /** 全流程记录导出：商品 × 节点 */
    List<FlowRecordExportRow> selectFlowRecordsForExport();

    /** 资料缺口清单导出：商品 × 缺口资料项（平铺） */
    List<MaterialGapExportRow> selectGapsForExport();
}
