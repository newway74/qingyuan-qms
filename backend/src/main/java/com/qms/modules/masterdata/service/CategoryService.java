package com.qms.modules.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.exception.BizException;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.ResultCode;
import com.qms.framework.audit.AuditContext;
import com.qms.framework.audit.AuditLog;
import com.qms.modules.masterdata.dto.CategoryUpsertRequest;
import com.qms.modules.masterdata.entity.Category;
import com.qms.modules.masterdata.mapper.CategoryMapper;
import com.qms.modules.masterdata.vo.CategoryTreeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 品类服务：阶段1作为「统一框架样例 CRUD」，走通 JSR303/逻辑删/审计/分页/权限。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final ObjectMapper objectMapper;

    public PageResult<Category> page(PageRequest request, String name, String code, Integer status) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                .like(name != null && !name.isBlank(), Category::getName, name)
                .like(code != null && !code.isBlank(), Category::getCode, code)
                .eq(status != null, Category::getStatus, status)
                .orderByAsc(Category::getSort)
                .orderByDesc(Category::getId);
        Page<Category> page = categoryMapper.selectPage(new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        return PageResult.of(page);
    }

    public List<CategoryTreeVO> tree() {
        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort));
        Map<Long, CategoryTreeVO> map = new LinkedHashMap<>();
        for (Category category : all) {
            map.put(category.getId(), toTreeVO(category));
        }
        List<CategoryTreeVO> roots = new ArrayList<>();
        for (CategoryTreeVO vo : map.values()) {
            Long parentId = vo.getParentId();
            if (parentId == null || parentId == 0L || !map.containsKey(parentId)) {
                roots.add(vo);
            } else {
                map.get(parentId).getChildren().add(vo);
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<CategoryTreeVO> nodes) {
        nodes.sort(Comparator.comparing(CategoryTreeVO::getSort, Comparator.nullsLast(Integer::compareTo)));
        nodes.forEach(n -> sortTree(n.getChildren()));
    }

    public Category getById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "品类不存在");
        }
        return category;
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "品类管理", action = "CREATE", bizType = "qc_category")
    public Long create(CategoryUpsertRequest request) {
        validateParent(request.getParentId(), null);
        ensureCodeUnique(request.getCode(), null);
        Category category = new Category();
        apply(category, request);
        categoryMapper.insert(category);
        return category.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "品类管理", action = "UPDATE", bizType = "qc_category", bizIdExpr = "#request.id")
    public void update(CategoryUpsertRequest request) {
        if (request.getId() == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "id不能为空");
        }
        Category existing = getById(request.getId());
        putBefore(existing);
        if (!existing.getParentId().equals(request.getParentId())) {
            validateParent(request.getParentId(), request.getId());
        }
        ensureCodeUnique(request.getCode(), request.getId());
        apply(existing, request);
        categoryMapper.updateById(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "品类管理", action = "DELETE_LOGIC", bizType = "qc_category", bizIdExpr = "#id")
    public void logicDelete(Long id) {
        Category existing = getById(id);
        putBefore(existing);
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "存在子品类，不能删除");
        }
        // 逻辑删除（@TableLogic），数据保留，可经审计还原变更链
        categoryMapper.deleteById(id);
    }

    private void apply(Category category, CategoryUpsertRequest request) {
        category.setParentId(request.getParentId());
        category.setCode(request.getCode().trim());
        category.setName(request.getName().trim());
        category.setSort(request.getSort() == null ? 0 : request.getSort());
        category.setStatus(request.getStatus());
    }

    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new BizException(ResultCode.PARAM_INVALID, "不能选择自身作为父节点");
        }
        if (categoryMapper.selectById(parentId) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "父品类不存在");
        }
    }

    private void ensureCodeUnique(String code, Long excludeId) {
        // 编码永久唯一：含已逻辑删除的记录，保证历史单据/审计中的编码不会指向歧义实体
        Long count = categoryMapper.countByCodeIncludeDeleted(code.trim(), excludeId);
        if (count != null && count > 0) {
            throw new BizException(ResultCode.DATA_DUPLICATED, "品类编码已存在（含已删除记录，编码不可复用）");
        }
    }

    private void putBefore(Category existing) {
        try {
            AuditContext.putBefore(objectMapper.writeValueAsString(existing));
        } catch (Exception e) {
            log.warn("品类 before 快照序列化失败: {}", e.getMessage());
        }
    }

    private CategoryTreeVO toTreeVO(Category category) {
        CategoryTreeVO vo = new CategoryTreeVO();
        vo.setId(category.getId());
        vo.setParentId(category.getParentId());
        vo.setCode(category.getCode());
        vo.setName(category.getName());
        vo.setSort(category.getSort());
        vo.setStatus(category.getStatus());
        return vo;
    }
}
