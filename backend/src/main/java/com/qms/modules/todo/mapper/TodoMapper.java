package com.qms.modules.todo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qms.modules.todo.entity.Todo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TodoMapper extends BaseMapper<Todo> {
}
