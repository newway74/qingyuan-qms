package com.qms.modules.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qms.common.result.R;
import com.qms.modules.system.entity.SysDictItem;
import com.qms.modules.system.mapper.SysDictItemMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "数据字典")
@RestController
@RequestMapping("/api/v1/system/dicts")
@RequiredArgsConstructor
public class DictController {

    private final SysDictItemMapper dictItemMapper;

    @Operation(summary = "按字典类型获取启用字典项")
    @GetMapping("/{type}")
    public R<List<SysDictItem>> items(@PathVariable String type) {
        return R.ok(dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, type)
                .eq(SysDictItem::getStatus, 1)
                .orderByAsc(SysDictItem::getSort)));
    }
}
