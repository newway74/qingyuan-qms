package com.qms.modules.ledger.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

/**
 * 资料重点缺口明细行：一个商品一行，内含其全部缺失/待确认资料项。
 */
@Data
public class MaterialGapVO {

    private Long goodsId;
    private String sku;
    private String commonName;
    private String brand;
    private String categoryL1Name;
    private String currentNodeName;
    /** 缺口项数量 */
    private Integer gapCount;
    /** 缺口明细（资料项 + 缺失/待确认状态） */
    private List<GapItem> gapItems;

    /** GROUP_CONCAT 聚合串（itemName|status 以 ;; 分隔），仅服务端拆分使用，不下发前端 */
    @JsonIgnore
    private String gapItemsRaw;

    @Data
    public static class GapItem {
        private String itemName;
        /** MISSING 缺失 / PENDING 待确认 */
        private String status;

        public GapItem() {
        }

        public GapItem(String itemName, String status) {
            this.itemName = itemName;
            this.status = status;
        }
    }
}
