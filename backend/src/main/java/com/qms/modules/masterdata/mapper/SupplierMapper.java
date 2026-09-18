package com.qms.modules.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qms.modules.masterdata.entity.Supplier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SupplierMapper extends BaseMapper<Supplier> {

    /**
     * 供应商编码永久唯一（含逻辑删除记录），保证历史单据/审计中的编码不产生歧义。
     * 注意：注解 SQL 中不等号必须写 !=，写 XML 实体转义会原样下发导致语法错误。
     */
    @Select("SELECT COUNT(1) FROM qc_supplier WHERE tenant_id = #{tenantId} AND supplier_code = #{code} "
            + "AND (#{excludeId} IS NULL OR id != #{excludeId})")
    Long countByCodeIncludeDeleted(@Param("tenantId") Long tenantId,
                                   @Param("code") String code,
                                   @Param("excludeId") Long excludeId);
}
