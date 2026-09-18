package com.qms.modules.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 质量看板聚合口径（统一在 SQL 层定义，禁止前端/多服务各自统计）。
 * 判定口径：双签 SIGNED 报告；合格=conclusion QUALIFIED；统计周期=复核签署时间。
 */
@Mapper
public interface DashboardMapper {

    String PRODUCT_JOIN = """
            FROM qc_inspection_report r
            JOIN qc_inspection_task t ON t.id = r.task_id AND t.deleted = 0
            JOIN qc_sample s ON s.id = t.sample_id AND s.deleted = 0
            JOIN qc_product_sku sku ON sku.id = s.sku_id AND sku.deleted = 0
            JOIN qc_product p ON p.id = sku.product_id AND p.deleted = 0
            """;

    String PRODUCT_WHERE = """
            WHERE r.deleted = 0 AND r.status = 'SIGNED' AND r.reviewer_signed_at IS NOT NULL
            AND (#{from} IS NULL OR r.reviewer_signed_at >= #{from})
            AND (#{to} IS NULL OR r.reviewer_signed_at <= #{to})
            AND (#{categoryId} IS NULL OR p.category_id = #{categoryId})
            AND (#{supplierId} IS NULL OR p.supplier_id = #{supplierId})
            """;

    /** 1. 合格率趋势（按月） */
    @Select("""
            SELECT DATE_FORMAT(r.reviewer_signed_at, '%Y%m') AS period,
                   COUNT(*) AS total,
                   SUM(CASE WHEN r.conclusion = 'QUALIFIED' THEN 1 ELSE 0 END) AS qualified
            """ + PRODUCT_JOIN + PRODUCT_WHERE + """
            GROUP BY DATE_FORMAT(r.reviewer_signed_at, '%Y%m')
            ORDER BY period
            """)
    List<Map<String, Object>> passRateTrend(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to,
                                            @Param("categoryId") Long categoryId,
                                            @Param("supplierId") Long supplierId);

    /** 3a. 品类合格率排行 */
    @Select("""
            SELECT c.name AS name, COUNT(*) AS total,
                   SUM(CASE WHEN r.conclusion = 'QUALIFIED' THEN 1 ELSE 0 END) AS qualified
            """ + PRODUCT_JOIN + """
            JOIN qc_category c ON c.id = p.category_id AND c.deleted = 0
            """ + PRODUCT_WHERE + """
            GROUP BY c.id, c.name
            ORDER BY (SUM(CASE WHEN r.conclusion = 'QUALIFIED' THEN 1 ELSE 0 END) / COUNT(*)) DESC, total DESC
            """)
    List<Map<String, Object>> categoryRanking(@Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to,
                                               @Param("categoryId") Long categoryId,
                                               @Param("supplierId") Long supplierId);

    /** 3b. 品牌合格率排行 */
    @Select("""
            SELECT COALESCE(NULLIF(p.brand, ''), '未填品牌') AS name, COUNT(*) AS total,
                   SUM(CASE WHEN r.conclusion = 'QUALIFIED' THEN 1 ELSE 0 END) AS qualified
            """ + PRODUCT_JOIN + PRODUCT_WHERE + """
            GROUP BY name
            ORDER BY (SUM(CASE WHEN r.conclusion = 'QUALIFIED' THEN 1 ELSE 0 END) / COUNT(*)) DESC, total DESC
            """)
    List<Map<String, Object>> brandRanking(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to,
                                            @Param("categoryId") Long categoryId,
                                            @Param("supplierId") Long supplierId);

    /** 3c. 供应商合格率排行 */
    @Select("""
            SELECT sp.supplier_name AS name, COUNT(*) AS total,
                   SUM(CASE WHEN r.conclusion = 'QUALIFIED' THEN 1 ELSE 0 END) AS qualified
            """ + PRODUCT_JOIN + """
            JOIN qc_supplier sp ON sp.id = p.supplier_id AND sp.deleted = 0
            """ + PRODUCT_WHERE + """
            GROUP BY sp.id, sp.supplier_name
            ORDER BY (SUM(CASE WHEN r.conclusion = 'QUALIFIED' THEN 1 ELSE 0 END) / COUNT(*)) DESC, total DESC
            """)
    List<Map<String, Object>> supplierRanking(@Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to,
                                               @Param("categoryId") Long categoryId,
                                               @Param("supplierId") Long supplierId);

    /** 2. 缺陷帕累托（按缺陷项名分组 Top10） */
    @Select("""
            <script>
            SELECT di.item_name AS name, COUNT(*) AS cnt
            FROM qc_defect_item di
            WHERE di.deleted = 0
            <if test="from != null"> AND di.created_at &gt;= #{from}</if>
            <if test="to != null"> AND di.created_at &lt;= #{to}</if>
            GROUP BY di.item_name
            ORDER BY cnt DESC
            LIMIT 10
            </script>
            """)
    List<Map<String, Object>> defectPareto(@Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /** 2-总数：用于计算帕累托累计占比 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM qc_defect_item WHERE deleted = 0
            <if test="from != null"> AND created_at &gt;= #{from}</if>
            <if test="to != null"> AND created_at &lt;= #{to}</if>
            </script>
            """)
    long defectParetoTotal(@Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to);

    /** 4. 抽检来源分布（抽样单创建时间口径） */
    @Select("""
            <script>
            SELECT sm.source AS name, COUNT(*) AS value
            FROM qc_sampling sm
            JOIN qc_product_sku sku ON sku.id = sm.sku_id AND sku.deleted = 0
            JOIN qc_product p ON p.id = sku.product_id AND p.deleted = 0
            WHERE sm.deleted = 0
            <if test="from != null"> AND sm.created_at &gt;= #{from}</if>
            <if test="to != null"> AND sm.created_at &lt;= #{to}</if>
            <if test="categoryId != null"> AND p.category_id = #{categoryId}</if>
            <if test="supplierId != null"> AND p.supplier_id = #{supplierId}</if>
            GROUP BY sm.source
            ORDER BY value DESC
            </script>
            """)
    List<Map<String, Object>> sourcePie(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        @Param("categoryId") Long categoryId,
                                        @Param("supplierId") Long supplierId);

    /** 5. 处理时效（按月，分配到复核完成平均小时） */
    @Select("""
            SELECT DATE_FORMAT(t.reviewed_at, '%Y%m') AS period,
                   COUNT(*) AS finished,
                   ROUND(AVG(TIMESTAMPDIFF(MINUTE, t.assigned_at, t.reviewed_at) / 60), 1) AS avgHours
            FROM qc_inspection_task t
            JOIN qc_sample s ON s.id = t.sample_id AND s.deleted = 0
            JOIN qc_product_sku sku ON sku.id = s.sku_id AND sku.deleted = 0
            JOIN qc_product p ON p.id = sku.product_id AND p.deleted = 0
            WHERE t.deleted = 0 AND t.assigned_at IS NOT NULL AND t.reviewed_at IS NOT NULL
            AND (#{from} IS NULL OR t.reviewed_at >= #{from})
            AND (#{to} IS NULL OR t.reviewed_at <= #{to})
            AND (#{categoryId} IS NULL OR p.category_id = #{categoryId})
            AND (#{supplierId} IS NULL OR p.supplier_id = #{supplierId})
            GROUP BY DATE_FORMAT(t.reviewed_at, '%Y%m')
            ORDER BY period
            """)
    List<Map<String, Object>> efficiency(@Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         @Param("categoryId") Long categoryId,
                                         @Param("supplierId") Long supplierId);

    /** 6a. 留样效期分桶（在库留样） */
    @Select("""
            SELECT
              COALESCE(SUM(CASE WHEN retain_until < CURDATE() THEN 1 ELSE 0 END), 0) AS expired,
              COALESCE(SUM(CASE WHEN retain_until BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY) THEN 1 ELSE 0 END), 0) AS d30,
              COALESCE(SUM(CASE WHEN retain_until BETWEEN DATE_ADD(CURDATE(), INTERVAL 31 DAY) AND DATE_ADD(CURDATE(), INTERVAL 90 DAY) THEN 1 ELSE 0 END), 0) AS d90,
              COALESCE(SUM(CASE WHEN retain_until > DATE_ADD(CURDATE(), INTERVAL 90 DAY) THEN 1 ELSE 0 END), 0) AS d90plus
            FROM qc_sample
            WHERE deleted = 0 AND sample_type = 'RETAIN' AND status = 'RETAINING' AND retain_until IS NOT NULL
            """)
    Map<String, Object> retainBuckets();

    /** 6b. 批次效期分桶 */
    @Select("""
            SELECT status AS name, COUNT(*) AS value
            FROM qc_batch
            WHERE deleted = 0 AND expiry_date IS NOT NULL
            GROUP BY status
            """)
    List<Map<String, Object>> batchBuckets();

    // ============ 质量月报（口径与看板一致：双签报告，复核签署时间归属月份） ============

    /** 月报-供应商判定明细 + 当月质量评级（无评级记录显示 -） */
    @Select("""
            SELECT sp.supplier_name AS name, COUNT(*) AS total,
                   SUM(CASE WHEN r.conclusion = 'QUALIFIED' THEN 1 ELSE 0 END) AS qualified,
                   COALESCE(q.grade, '-') AS grade
            """ + PRODUCT_JOIN + """
            JOIN qc_supplier sp ON sp.id = p.supplier_id AND sp.deleted = 0
            LEFT JOIN qc_supplier_quality q
                   ON q.supplier_id = sp.id AND q.period = #{period} AND q.deleted = 0
            """ + PRODUCT_WHERE + """
            GROUP BY sp.id, sp.supplier_name, q.grade
            ORDER BY total DESC
            """)
    List<Map<String, Object>> monthlySuppliers(@Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to,
                                                @Param("categoryId") Long categoryId,
                                                @Param("supplierId") Long supplierId,
                                                @Param("period") String period);

    /** 月报-缺陷项按等级分布 */
    @Select("""
            SELECT COALESCE(NULLIF(di.item_name, ''), '（未填项目）') AS name,
                   SUM(CASE WHEN di.defect_level = 'A' THEN 1 ELSE 0 END) AS aCount,
                   SUM(CASE WHEN di.defect_level = 'B' THEN 1 ELSE 0 END) AS bCount,
                   SUM(CASE WHEN di.defect_level = 'C' THEN 1 ELSE 0 END) AS cCount,
                   COUNT(*) AS total
            FROM qc_defect_item di
            WHERE di.deleted = 0 AND di.created_at >= #{from} AND di.created_at <= #{to}
            GROUP BY di.item_name
            ORDER BY total DESC
            """)
    List<Map<String, Object>> monthlyDefects(@Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    /** 月报-预警处置（本月新增/本月处置/期末未处理），按类型分组 */
    @Select("""
            SELECT alert_type AS name,
                   SUM(CASE WHEN triggered_at >= #{from} AND triggered_at <= #{to} THEN 1 ELSE 0 END) AS raised,
                   SUM(CASE WHEN handled_at >= #{from} AND handled_at <= #{to} THEN 1 ELSE 0 END) AS handled,
                   SUM(CASE WHEN status = 0 AND triggered_at <= #{to} THEN 1 ELSE 0 END) AS openAtEnd
            FROM qc_alert
            GROUP BY alert_type
            ORDER BY raised DESC
            """)
    List<Map<String, Object>> monthlyAlerts(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);
}
