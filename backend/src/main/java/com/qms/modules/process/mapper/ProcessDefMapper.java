package com.qms.modules.process.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qms.modules.process.entity.ProcessDef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProcessDefMapper extends BaseMapper<ProcessDef> {

    /** 流程编码永久唯一（含逻辑删除、所有版本）。 */
    @Select("SELECT COUNT(1) FROM qc_process_def WHERE tenant_id = #{tenantId} AND process_code = #{code}")
    Long countByCodeIncludeDeleted(@Param("tenantId") Long tenantId, @Param("code") String code);
}
