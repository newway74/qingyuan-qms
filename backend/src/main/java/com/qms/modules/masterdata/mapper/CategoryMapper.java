package com.qms.modules.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qms.modules.masterdata.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 按编码统计（含逻辑删除记录）。
     * 业务编码一经使用永久不可复用，避免历史单据/审计记录中的编码指向歧义实体。
     */
    @Select("SELECT COUNT(1) FROM qc_category WHERE code = #{code} "
            + "AND (#{excludeId} IS NULL OR id != #{excludeId})")
    Long countByCodeIncludeDeleted(@Param("code") String code, @Param("excludeId") Long excludeId);
}
