package com.qms.modules.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qms.modules.masterdata.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    /** SKU 编码永久唯一（含逻辑删除记录）。 */
    @Select("SELECT COUNT(1) FROM qc_product_sku WHERE tenant_id = #{tenantId} AND sku_code = #{code} "
            + "AND (#{excludeId} IS NULL OR id != #{excludeId})")
    Long countByCodeIncludeDeleted(@Param("tenantId") Long tenantId,
                                   @Param("code") String code,
                                   @Param("excludeId") Long excludeId);
}
