package com.qms.modules.masterdata.vo;

import com.qms.modules.masterdata.entity.Product;
import com.qms.modules.masterdata.entity.ProductLicense;
import com.qms.modules.masterdata.entity.ProductSku;
import lombok.Data;

import java.util.List;

@Data
public class ProductDetailVO {

    private Product product;

    private String categoryName;

    private String supplierName;

    private List<ProductSku> skus;

    private List<ProductLicense> licenses;
}
