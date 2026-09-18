package com.qms.modules.inspection.service;

import com.qms.modules.inspection.vo.ReportPdfData;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 质检报告 PDF 渲染（OpenHTML to PDF，内嵌 SimHei 中文字体，Linux/Windows 一致）。
 */
@Component
public class PdfReportRenderer {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Map<String, String> CONCLUSION_LABEL = Map.of(
            "QUALIFIED", "合格",
            "UNQUALIFIED", "不合格",
            "CONCESSION", "让步接收",
            "PENDING_RECHECK", "待复检");

    private static final Map<String, String> GROUP_LABEL = Map.of(
            "SENSORY", "感官指标",
            "PACKAGE_LABEL", "包装与标签标识",
            "NET_CONTENT", "净含量",
            "PHYSICO", "理化指标",
            "MICRO", "微生物",
            "CERT_DOC", "资质与外检报告");

    private static final Map<String, String> JUDGE_LABEL = Map.of(
            "PASS", "合格",
            "FAIL", "不合格",
            "NONE", "未判定");

    public byte[] render(ReportPdfData data) {
        String html = buildHtml(data);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.useFont(() -> PdfReportRenderer.class.getResourceAsStream("/fonts/SimHei.ttf"), "SimHei");
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF渲染失败: " + e.getMessage(), e);
        }
    }

    private String buildHtml(ReportPdfData d) {
        StringBuilder rows = new StringBuilder();
        for (ReportPdfData.ResultRow row : d.getRows()) {
            String group = GROUP_LABEL.getOrDefault(row.getGroupCode(), row.getGroupCode());
            rows.append("<tr>")
                    .append("<td>").append(esc(group)).append("</td>")
                    .append("<td>").append(esc(row.getItemName())).append("</td>")
                    .append("<td>").append(esc(row.getStandard())).append("</td>")
                    .append("<td>").append(esc(row.getMeasured())).append("</td>")
                    .append("<td class='judge ").append(judgeClass(row.getFinalJudgement()))
                    .append("'>").append(esc(JUDGE_LABEL.getOrDefault(row.getFinalJudgement(),
                            row.getFinalJudgement()))).append("</td>")
                    .append("<td>").append(esc(nullToDash(row.getReviewNote()))).append("</td>")
                    .append("</tr>");
        }

        String conclusionText = CONCLUSION_LABEL.getOrDefault(d.getConclusion(), d.getConclusion());
        String issueTime = d.getIssuedAt() != null ? d.getIssuedAt()
                : LocalDateTime.now().format(DT_FMT);
        String statusText = "ISSUED".equals(d.getReportStatus()) ? "已签发" : "已签署（未签发）";

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                <meta charset="UTF-8"/>
                <style>
                    @page { size: A4; margin: 18mm 16mm; }
                    * { font-family: 'SimHei', sans-serif; }
                    body { color: #1f1f1f; font-size: 11px; }
                    .title { text-align: center; font-size: 22px; font-weight: bold; margin: 6px 0 2px; }
                    .subtitle { text-align: center; font-size: 12px; color: #555; margin-bottom: 14px; }
                    .meta { width: 100%%; border-collapse: collapse; margin-bottom: 10px; }
                    .meta td { padding: 4px 6px; border: 1px solid #bbb; }
                    .meta .label { background: #f5f5f5; width: 15%%; font-weight: bold; }
                    table.data { width: 100%%; border-collapse: collapse; margin-top: 6px; }
                    table.data th, table.data td { border: 1px solid #999; padding: 4px 6px; vertical-align: top; }
                    table.data th { background: #f0f3f8; }
                    .conclusion-box { margin: 14px 0; padding: 10px 12px; border: 1.5px solid #333;
                        font-size: 14px; font-weight: bold; }
                    .judge-FAIL { color: #cf1322; font-weight: bold; }
                    .judge-PASS { color: #389e0d; }
                    .sign { width: 100%%; margin-top: 26px; border-collapse: collapse; }
                    .sign td { padding: 8px 6px; font-size: 11px; }
                    .footer { margin-top: 18px; color: #777; font-size: 9px; line-height: 1.7; }
                    .hash { word-break: break-all; }
                </style>
                </head>
                <body>
                    <div class="title">清源 QMS · 产品质量检验报告</div>
                    <div class="subtitle">QUALITY INSPECTION REPORT（报告编号：%s）</div>

                    <table class="meta">
                        <tr>
                            <td class="label">产品名称</td><td>%s</td>
                            <td class="label">规格</td><td>%s</td>
                        </tr>
                        <tr>
                            <td class="label">SKU编码</td><td>%s</td>
                            <td class="label">批号</td><td>%s</td>
                        </tr>
                        <tr>
                            <td class="label">生产日期</td><td>%s</td>
                            <td class="label">保质期至</td><td>%s</td>
                        </tr>
                        <tr>
                            <td class="label">样品编号</td><td>%s</td>
                            <td class="label">任务编号</td><td>%s（第%s轮）</td>
                        </tr>
                        <tr>
                            <td class="label">检验依据</td><td colspan="3">%s（版本 v%d）</td>
                        </tr>
                    </table>

                    <table class="data">
                        <thead>
                        <tr>
                            <th style="width:14%%">检验分组</th>
                            <th style="width:24%%">检验项目</th>
                            <th style="width:24%%">标准要求</th>
                            <th style="width:16%%">实测/结果</th>
                            <th style="width:9%%">判定</th>
                            <th style="width:13%%">复核备注</th>
                        </tr>
                        </thead>
                        <tbody>
                        %s
                        </tbody>
                    </table>

                    <div class="conclusion-box">
                        综合判定：<span class="%s">%s</span>
                        <span style="font-weight:normal;font-size:11px;margin-left:18px;">
                            严重A类不合格 %d 项 ／ 主要B类不合格 %d 项 ／ 次要C类不合格 %d 项
                        </span>
                    </div>

                    <table class="sign">
                        <tr>
                            <td style="width:50%%">检验员（电子签名）：%s<br/>签名时间：%s</td>
                            <td>复核判定人（电子签名）：%s<br/>签名时间：%s</td>
                        </tr>
                        <tr>
                            <td class="hash">检验签名哈希：%s</td>
                            <td class="hash">复核签名哈希：%s</td>
                        </tr>
                    </table>

                    <div class="footer">
                        签发时间：%s　报告状态：%s<br/>
                        1. 本报告由品控系统依据已发布检验标准模板自动汇总，检验/复核均经密码二次认证电子签名，签名记录只增不可删除；<br/>
                        2. 对本报告有异议，应于收到报告之日起按复检流程申请复检。
                    </div>
                </body>
                </html>
                """.formatted(
                esc(d.getReportNo()),
                esc(nullToDash(d.getProductName())),
                esc(nullToDash(d.getSpec())),
                esc(nullToDash(d.getSkuCode())),
                esc(nullToDash(d.getBatchNo())),
                esc(d.getProductionDate()),
                esc(d.getExpiryDate()),
                esc(nullToDash(d.getSampleNo())),
                esc(nullToDash(d.getTaskNo())), esc(nullToDash(d.getRoundNo())),
                esc(nullToDash(d.getTemplateName())), d.getTemplateVersion() == null ? 1 : d.getTemplateVersion(),
                rows,
                judgeClass(d.getConclusion()), conclusionText,
                d.getAFailCount() == null ? 0 : d.getAFailCount(),
                d.getBFailCount() == null ? 0 : d.getBFailCount(),
                d.getCFailCount() == null ? 0 : d.getCFailCount(),
                esc(nullToDash(d.getInspectorName())), esc(d.getInspectorSignedAt()),
                esc(nullToDash(d.getReviewerName())), esc(d.getReviewerSignedAt()),
                shortHash(d.getInspectorHash()),
                shortHash(d.getReviewerHash()),
                esc(issueTime),
                esc(statusText)
        );
    }

    private String judgeClass(String judgement) {
        if ("FAIL".equals(judgement) || "UNQUALIFIED".equals(judgement)) {
            return "judge-FAIL";
        }
        if ("PASS".equals(judgement) || "QUALIFIED".equals(judgement)) {
            return "judge-PASS";
        }
        return "";
    }

    private String shortHash(String hash) {
        if (hash == null) {
            return "-";
        }
        return hash.substring(0, Math.min(24, hash.length())) + "…";
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
