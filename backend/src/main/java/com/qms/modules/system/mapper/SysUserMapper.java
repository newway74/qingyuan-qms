package com.qms.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qms.modules.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 按用户名查角色 code 列表 */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /** 按用户名查权限码列表（ADMIN 返回 *） */
    List<String> selectPermCodesByUserId(@Param("userId") Long userId);

    /** 数据范围（取用户角色中最宽范围 ALL > DEPT > BRAND > CATEGORY） */
    String selectDataScopeByUserId(@Param("userId") Long userId);

    /** 按角色编码查启用用户 */
    List<SysUser> selectActiveUsersByRoleCode(@Param("roleCode") String roleCode);
}
