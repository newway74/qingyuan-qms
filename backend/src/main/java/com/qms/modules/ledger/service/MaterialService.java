package com.qms.modules.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.qms.common.exception.BizException;
import com.qms.common.result.ResultCode;
import com.qms.modules.ledger.LedgerConst;
import com.qms.modules.ledger.dto.MaterialItemUpsertRequest;
import com.qms.modules.ledger.entity.GoodsMaterial;
import com.qms.modules.ledger.entity.LedgerGoods;
import com.qms.modules.ledger.entity.MaterialItem;
import com.qms.modules.ledger.mapper.GoodsMaterialMapper;
import com.qms.modules.ledger.mapper.LedgerGoodsMapper;
import com.qms.modules.ledger.mapper.MaterialItemMapper;
import com.qms.modules.ledger.vo.MaterialItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 资料项定义与商品资料清单服务。
 * 职责：启用资料项列表、新建商品初始化清单、管理员维护资料项（新建/改名/启停/删除）、
 *      资料项变更幂等回填全部商品、商品资料状态更新。
 */
@Service
@RequiredArgsConstructor
public class MaterialService {

    private static final Set<String> MATERIAL_STATUS =
            Set.of(LedgerConst.MATERIAL_READY, LedgerConst.MATERIAL_MISSING, LedgerConst.MATERIAL_PENDING);

    private final MaterialItemMapper materialItemMapper;
    private final GoodsMaterialMapper goodsMaterialMapper;
    private final LedgerGoodsMapper goodsMapper;

    /** 当前启用的资料项（按排序） */
    public List<MaterialItem> listEnabledItems() {
        return materialItemMapper.selectList(new LambdaQueryWrapper<MaterialItem>()
                .eq(MaterialItem::getStatus, 1)
                .orderByAsc(MaterialItem::getSort)
                .orderByAsc(MaterialItem::getId));
    }

    /** 全部资料项（含停用，管理员维护页用） */
    public List<MaterialItemVO> listAllItems() {
        return materialItemMapper.selectList(new LambdaQueryWrapper<MaterialItem>()
                        .orderByAsc(MaterialItem::getSort)
                        .orderByAsc(MaterialItem::getId))
                .stream().map(this::toVO).toList();
    }

    /**
     * 新建商品时按启用资料项初始化资料清单（缺失状态，幂等：已存在则跳过）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void initForGoods(Long goodsId) {
        List<MaterialItem> items = listEnabledItems();
        for (MaterialItem item : items) {
            Long exists = goodsMaterialMapper.selectCount(new LambdaQueryWrapper<GoodsMaterial>()
                    .eq(GoodsMaterial::getGoodsId, goodsId)
                    .eq(GoodsMaterial::getItemId, item.getId()));
            if (exists != null && exists > 0) {
                continue;
            }
            GoodsMaterial gm = new GoodsMaterial();
            gm.setGoodsId(goodsId);
            gm.setItemId(item.getId());
            gm.setItemName(item.getItemName());
            gm.setStatus(LedgerConst.MATERIAL_MISSING);
            goodsMaterialMapper.insert(gm);
        }
    }

    /**
     * 新建/启用资料项后幂等回填全部商品：只补缺失行，不覆盖用户已维护的状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void backfillAllGoods() {
        List<Long> goodsIds = goodsMapper.selectList(new LambdaQueryWrapper<LedgerGoods>()
                        .select(LedgerGoods::getId))
                .stream().map(LedgerGoods::getId).toList();
        for (Long goodsId : goodsIds) {
            initForGoods(goodsId);
        }
    }

    /** 新建资料项：编码随雪花 id 生成，建成后同步到全部商品 */
    @Transactional(rollbackFor = Exception.class)
    public Long createItem(MaterialItemUpsertRequest request) {
        String name = request.getItemName().trim();
        assertNameUnique(name, null);
        Long id = IdWorker.getId();
        MaterialItem item = new MaterialItem();
        item.setId(id);
        item.setItemCode("MI" + id);
        item.setItemName(name);
        item.setIsPreset(0);
        item.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        item.setSort(request.getSort() != null ? request.getSort() : nextSort());
        materialItemMapper.insert(item);
        if (Integer.valueOf(1).equals(item.getStatus())) {
            backfillAllGoods();
        }
        return id;
    }

    /** 编辑资料项：改名同步刷新商品清单快照名；停用后重新启用则补挂全部商品 */
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(MaterialItemUpsertRequest request) {
        if (request.getId() == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "资料项 id 不能为空");
        }
        MaterialItem item = materialItemMapper.selectById(request.getId());
        if (item == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "资料项不存在或已被删除");
        }
        String name = request.getItemName().trim();
        assertNameUnique(name, item.getId());
        boolean wasEnabled = Integer.valueOf(1).equals(item.getStatus());
        boolean enableNow = request.getStatus() == null
                ? wasEnabled : Integer.valueOf(1).equals(request.getStatus());

        item.setItemName(name);
        if (request.getSort() != null) {
            item.setSort(request.getSort());
        }
        item.setStatus(enableNow ? 1 : 0);
        materialItemMapper.updateById(item);

        // 名称是商品清单里的快照列，管理员改名后同步，避免台账显示旧名
        GoodsMaterial snapshot = new GoodsMaterial();
        snapshot.setItemName(name);
        goodsMaterialMapper.update(snapshot, new LambdaQueryWrapper<GoodsMaterial>()
                .eq(GoodsMaterial::getItemId, item.getId()));

        if (!wasEnabled && enableNow) {
            backfillAllGoods();
        }
    }

    /** 删除资料项：预置项禁止删除（可停用）；删除后商品历史清单保留但不再计缺口 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        MaterialItem item = materialItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "资料项不存在或已被删除");
        }
        if (Integer.valueOf(1).equals(item.getIsPreset())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID,
                    "系统预置资料项不可删除，可改为停用");
        }
        materialItemMapper.deleteById(id);
    }

    /** 更新某商品某项资料的齐套状态与备注（附件走统一附件服务，不在此处理） */
    @Transactional(rollbackFor = Exception.class)
    public void updateGoodsMaterial(Long goodsId, Long materialId, String status, String remark) {
        if (!MATERIAL_STATUS.contains(status)) {
            throw new BizException(ResultCode.PARAM_INVALID, "资料状态不合法：" + status);
        }
        GoodsMaterial gm = goodsMaterialMapper.selectOne(new LambdaQueryWrapper<GoodsMaterial>()
                .eq(GoodsMaterial::getId, materialId)
                .eq(GoodsMaterial::getGoodsId, goodsId));
        if (gm == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "商品资料记录不存在");
        }
        gm.setStatus(status);
        gm.setRemark(remark == null ? null : (remark.trim().isEmpty() ? null : remark.trim()));
        // 显式 set：清空备注（置 null）时 updateById 默认策略会忽略，导致旧备注残留
        goodsMaterialMapper.update(null, new LambdaUpdateWrapper<GoodsMaterial>()
                .eq(GoodsMaterial::getId, gm.getId())
                .set(GoodsMaterial::getStatus, gm.getStatus())
                .set(GoodsMaterial::getRemark, gm.getRemark()));
    }

    private void assertNameUnique(String name, Long excludeId) {
        Long count = materialItemMapper.selectCount(new LambdaQueryWrapper<MaterialItem>()
                .eq(MaterialItem::getItemName, name)
                .ne(excludeId != null, MaterialItem::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.DATA_DUPLICATED, "资料项名称已存在：" + name);
        }
    }

    private Integer nextSort() {
        List<MaterialItem> all = materialItemMapper.selectList(
                new LambdaQueryWrapper<MaterialItem>()
                        .orderByDesc(MaterialItem::getSort)
                        .last("LIMIT 1"));
        return all.isEmpty() ? 10 : (all.get(0).getSort() == null ? 10 : all.get(0).getSort() + 10);
    }

    private MaterialItemVO toVO(MaterialItem item) {
        MaterialItemVO vo = new MaterialItemVO();
        vo.setId(item.getId());
        vo.setItemCode(item.getItemCode());
        vo.setItemName(item.getItemName());
        vo.setSort(item.getSort());
        vo.setIsPreset(item.getIsPreset());
        vo.setStatus(item.getStatus());
        return vo;
    }
}
