package com.qms.modules.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qms.modules.ledger.entity.LedgerGoods;
import com.qms.modules.ledger.vo.GoodsListVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LedgerGoodsMapper extends BaseMapper<LedgerGoods> {

    /**
     * 商品台账分页：关联一/二级品类名称，支持关键字与多条件筛选。
     */
    IPage<GoodsListVO> selectGoodsPage(IPage<GoodsListVO> page,
                                       @Param("keyword") String keyword,
                                       @Param("categoryL1Id") Long categoryL1Id,
                                       @Param("categoryL2Id") Long categoryL2Id,
                                       @Param("cooperateResult") String cooperateResult,
                                       @Param("currentNodeCode") String currentNodeCode);

    /**
     * SKU 租户内永久唯一（含逻辑删除记录）：删除后 SKU 不可复用，避免历史单据/审计歧义。
     */
    @org.apache.ibatis.annotations.Select("SELECT COUNT(1) FROM qc_ledger_goods "
            + "WHERE tenant_id = #{tenantId} AND sku = #{sku} "
            + "AND (#{excludeId} IS NULL OR id != #{excludeId})")
    Long countBySkuIncludeDeleted(@Param("tenantId") Long tenantId,
                                  @Param("sku") String sku,
                                  @Param("excludeId") Long excludeId);
}
