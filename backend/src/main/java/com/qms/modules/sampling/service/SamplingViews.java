package com.qms.modules.sampling.service;

import com.qms.modules.masterdata.entity.Category;
import com.qms.modules.masterdata.entity.Product;
import com.qms.modules.masterdata.entity.ProductSku;
import com.qms.modules.masterdata.entity.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qms.modules.inspection.entity.InspectionTask;
import com.qms.modules.inspection.mapper.InspectionTaskMapper;
import com.qms.modules.masterdata.mapper.CategoryMapper;
import com.qms.modules.masterdata.mapper.ProductMapper;
import com.qms.modules.masterdata.mapper.ProductSkuMapper;
import com.qms.modules.masterdata.mapper.SupplierMapper;
import com.qms.modules.sampling.entity.Sample;
import com.qms.modules.sampling.entity.Sampling;
import com.qms.modules.sampling.vo.SampleVO;
import com.qms.modules.sampling.vo.SamplingVO;
import com.qms.modules.system.entity.SysUser;
import com.qms.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 抽样/样品视图组装（主数据冗余信息）。
 */
@Component
@RequiredArgsConstructor
public class SamplingViews {

    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final SupplierMapper supplierMapper;
    private final SysUserMapper sysUserMapper;
    private final InspectionTaskMapper inspectionTaskMapper;

    public SamplingVO toSamplingVO(Sampling s, List<Sample> samples) {
        SamplingVO vo = new SamplingVO();
        BeanUtils.copyProperties(s, vo);
        ProductSku sku = productSkuMapper.selectById(s.getSkuId());
        if (sku != null) {
            vo.setSkuCode(sku.getSkuCode());
            vo.setSpec(sku.getSpec());
            vo.setPackageForm(sku.getPackageForm());
            vo.setProductId(sku.getProductId());
            Product product = productMapper.selectById(sku.getProductId());
            if (product != null) {
                vo.setProductName(product.getProductName());
                vo.setBrand(product.getBrand());
                Category category = categoryMapper.selectById(product.getCategoryId());
                vo.setCategoryName(category == null ? null : category.getName());
                if (product.getSupplierId() != null) {
                    Supplier supplier = supplierMapper.selectById(product.getSupplierId());
                    vo.setSupplierName(supplier == null ? null : supplier.getSupplierName());
                }
            }
        }
        if (s.getSamplerId() != null) {
            SysUser sampler = sysUserMapper.selectById(s.getSamplerId());
            vo.setSamplerName(sampler == null ? null : sampler.getRealName());
        }
        if (samples != null) {
            List<SampleVO> sampleVOs = samples.stream().map(this::toSampleVO).toList();
            enrichTask(sampleVOs);
            vo.setSamples(sampleVOs);
        }
        return vo;
    }

    /** 回填每个样品最新轮次的检验任务（抽样详情聚合视图） */
    private void enrichTask(List<SampleVO> sampleVOs) {
        if (sampleVOs == null || sampleVOs.isEmpty()) {
            return;
        }
        List<Long> sampleIds = sampleVOs.stream().map(SampleVO::getId).toList();
        List<InspectionTask> tasks = inspectionTaskMapper.selectList(new LambdaQueryWrapper<InspectionTask>()
                .in(InspectionTask::getSampleId, sampleIds)
                .orderByDesc(InspectionTask::getRoundNo));
        Map<Long, InspectionTask> latestBySample = new HashMap<>();
        for (InspectionTask t : tasks) {
            latestBySample.putIfAbsent(t.getSampleId(), t);
        }
        sampleVOs.forEach(vo -> {
            InspectionTask task = latestBySample.get(vo.getId());
            if (task != null) {
                vo.setTaskId(task.getId());
                vo.setTaskNo(task.getTaskNo());
                vo.setTaskStatus(task.getStatus());
            }
        });
    }

    public SampleVO toSampleVO(Sample sample) {
        SampleVO vo = new SampleVO();
        BeanUtils.copyProperties(sample, vo);
        ProductSku sku = productSkuMapper.selectById(sample.getSkuId());
        if (sku != null) {
            vo.setSkuCode(sku.getSkuCode());
            vo.setSpec(sku.getSpec());
            vo.setPackageForm(sku.getPackageForm());
            Product product = productMapper.selectById(sku.getProductId());
            if (product != null) {
                vo.setProductName(product.getProductName());
            }
        }
        if (sample.getReceiverId() != null) {
            SysUser receiver = sysUserMapper.selectById(sample.getReceiverId());
            vo.setReceiverName(receiver == null ? null : receiver.getRealName());
        }
        return vo;
    }

    /** 批量组装样品视图，避免 N+1 */
    public List<SampleVO> toSampleVOList(List<Sample> samples) {
        if (samples.isEmpty()) {
            return List.of();
        }
        Map<Long, ProductSku> skuCache = new HashMap<>();
        Map<Long, Product> productCache = new HashMap<>();
        Map<Long, SysUser> userCache = new HashMap<>();
        return samples.stream().map(sample -> {
            SampleVO vo = new SampleVO();
            BeanUtils.copyProperties(sample, vo);
            ProductSku sku = skuCache.computeIfAbsent(sample.getSkuId(), productSkuMapper::selectById);
            if (sku != null) {
                vo.setSkuCode(sku.getSkuCode());
                vo.setSpec(sku.getSpec());
                vo.setPackageForm(sku.getPackageForm());
                Product product = productCache.computeIfAbsent(sku.getProductId(), productMapper::selectById);
                if (product != null) {
                    vo.setProductName(product.getProductName());
                }
            }
            if (sample.getReceiverId() != null) {
                SysUser receiver = userCache.computeIfAbsent(sample.getReceiverId(), sysUserMapper::selectById);
                vo.setReceiverName(receiver == null ? null : receiver.getRealName());
            }
            return vo;
        }).toList();
    }
}
