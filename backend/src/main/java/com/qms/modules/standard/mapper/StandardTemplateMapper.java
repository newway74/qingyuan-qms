package com.qms.modules.standard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qms.modules.standard.entity.StandardTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StandardTemplateMapper extends BaseMapper<StandardTemplate> {

    /** 模板业务编码占用统计（含逻辑删除、所有版本）；编码一经使用永久不可复用。 */
    @Select("SELECT COUNT(1) FROM qc_standard_template WHERE tenant_id = #{tenantId} AND template_code = #{code}")
    Long countByCodeIncludeDeleted(@Param("tenantId") Long tenantId, @Param("code") String code);
}
