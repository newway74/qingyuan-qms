package com.qms.modules.ledger;

/**
 * 商品全流程台账模块常量：状态码、数据来源、附件业务类型等。
 * 全模块统一引用，禁止散落魔法字符串。
 */
public final class LedgerConst {

    private LedgerConst() {
    }

    /** 合作结论：是 */
    public static final String RESULT_YES = "YES";
    /** 合作结论：待定 */
    public static final String RESULT_PENDING = "PENDING";
    /** 合作结论：否 */
    public static final String RESULT_NO = "NO";

    /** 节点状态：未开始 */
    public static final String NODE_NOT_STARTED = "NOT_STARTED";
    /** 节点状态：进行中 */
    public static final String NODE_IN_PROGRESS = "IN_PROGRESS";
    /** 节点状态：已完成 */
    public static final String NODE_DONE = "DONE";
    /** 节点状态：不通过 */
    public static final String NODE_REJECTED = "REJECTED";

    /** 资料状态：齐套 */
    public static final String MATERIAL_READY = "READY";
    /** 资料状态：缺失 */
    public static final String MATERIAL_MISSING = "MISSING";
    /** 资料状态：待确认 */
    public static final String MATERIAL_PENDING = "PENDING";

    /** 数据来源：用户建档/导入 */
    public static final String SOURCE_USER = "USER";
    /** 数据来源：脱敏演示数据 */
    public static final String SOURCE_DEMO = "DEMO";

    /** 预置默认全流程模板编码 */
    public static final String DEFAULT_TEMPLATE_CODE = "DEFAULT_GOODS_FLOW";

    /** 附件业务类型：流程节点附件 */
    public static final String BIZ_NODE_ATTACHMENT = "LEDGER_NODE";
    /** 附件业务类型：资料佐证附件 */
    public static final String BIZ_MATERIAL_ATTACHMENT = "LEDGER_MATERIAL";

    /** 自定义字段类型集合 */
    public static final java.util.List<String> FIELD_TYPES =
            java.util.List.of("TEXT", "DATE", "SELECT", "FILE", "CONCLUSION", "TEXTAREA");
}
