package com.gzly.service;

import com.gzly.common.ComplianceConstants;
import com.gzly.common.exception.BizException;
import com.gzly.compliance.ComplianceTextGuard;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VolunteerExportService {

    private final VolunteerService volunteerService;
    private final ComplianceTextGuard complianceTextGuard;

    public byte[] exportLongImage(Long planId, String accessKey) {
        VolunteerService.PlanResult plan = requirePlan(planId, accessKey);
        List<VolunteerService.VolunteerItem> items = plan.getItems() == null ? List.of() : plan.getItems();
        int width = 1120;
        int rowHeight = 48;
        int headerHeight = 250;
        int height = Math.max(760, headerHeight + items.size() * rowHeight + 160);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(248, 244, 235));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(27, 38, 49));
            g.setFont(new Font("SansSerif", Font.BOLD, 30));
            g.drawString("贵州高考志愿辅助方案", 48, 62);
            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g.drawString(String.format("分数 %d / 位次 %,d / 科目 %s / 策略 %s",
                    plan.getTotalScore(), plan.getProvinceRank(), safe(plan.getFirstSubject()), safe(plan.getStrategyMode())), 48, 100);
            g.drawString("梯度统计：" + gradientSummary(items), 48, 132);
            g.drawString("字段口径：机会指数 / 风险等级 / 数据参考度，均为辅助参考。", 48, 164);
            // VolunteerDiagnosisEngine 诊断摘要（不超过 60 中文字符，超出截断）
            String diagSummary = readDiagnosisSummary(plan);
            if (diagSummary != null && !diagSummary.isBlank()) {
                String safeText = complianceTextGuard.sanitizeText("export_long_image_diagnosis",
                        String.valueOf(planId), clipText(diagSummary, 60));
                g.drawString("诊断摘要：" + safeText, 48, 196);
            }

            int y = 216;
            drawHeader(g, y);
            y += rowHeight;
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            for (VolunteerService.VolunteerItem item : items) {
                if (y + rowHeight > height - 120) break;
                g.setColor(Color.WHITE);
                g.fillRoundRect(36, y - 30, width - 72, 38, 10, 10);
                g.setColor(new Color(45, 55, 72));
                g.drawString(String.valueOf(item.getIndex()), 56, y - 6);
                g.drawString(safe(item.getGradient()), 104, y - 6);
                g.drawString(clip(g, safe(item.getUniversityName()), 210), 154, y - 6);
                g.drawString(clip(g, safe(item.getMajorName()), 280), 380, y - 6);
                g.drawString(String.valueOf(item.getChanceScore()), 690, y - 6);
                g.drawString(safe(item.getRiskLevel()), 790, y - 6);
                g.drawString(String.format("%.0f", item.getDataConfidence()), 900, y - 6);
                y += rowHeight;
            }
            g.setColor(new Color(91, 103, 121));
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            drawWrapped(g, complianceTextGuard.sanitizeText("export_long_image", String.valueOf(planId),
                    ComplianceConstants.SAFE_ASSISTANT_NOTICE), 48, height - 86, width - 96, 22);
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException("导出长图失败");
        }
    }

    public byte[] exportExcel(Long planId, String accessKey) {
        VolunteerService.PlanResult plan = requirePlan(planId, accessKey);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("志愿辅助方案");
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            int rowIndex = 0;
            Row title = sheet.createRow(rowIndex++);
            title.createCell(0).setCellValue("贵州高考志愿辅助方案");
            Row profile = sheet.createRow(rowIndex++);
            profile.createCell(0).setCellValue("分数");
            profile.createCell(1).setCellValue(plan.getTotalScore());
            profile.createCell(2).setCellValue("位次");
            profile.createCell(3).setCellValue(plan.getProvinceRank());
            profile.createCell(4).setCellValue("策略");
            profile.createCell(5).setCellValue(safe(plan.getStrategyMode()));
            // VolunteerDiagnosisEngine 诊断摘要行（过合规检查，不出现任何承诺性措辞）
            String diagSummary = readDiagnosisSummary(plan);
            if (diagSummary != null && !diagSummary.isBlank()) {
                Row diagRow = sheet.createRow(rowIndex++);
                diagRow.createCell(0).setCellValue("诊断摘要");
                diagRow.createCell(1).setCellValue(safeText("export_excel_diagnosis", planId, diagSummary));
            }
            // VolunteerDiagnosisEngine warnings 主要警报，最多 5 条
            List<String> warnings = readDiagnosisWarnings(plan);
            if (!warnings.isEmpty()) {
                Row warnRow = sheet.createRow(rowIndex++);
                warnRow.createCell(0).setCellValue("主要警报");
                int limit = Math.min(5, warnings.size());
                for (int i = 0; i < limit; i++) {
                    warnRow.createCell(i + 1).setCellValue(safeText("export_excel_diagnosis_warning", planId, warnings.get(i)));
                }
            }
            rowIndex++;
            String[] headers = {"序号", "梯度", "院校", "专业/专业组", "地区", "预测参考位次", "位次差", "计划趋势", "扩招指数", "招生指数", "机会指数", "风险等级", "数据参考度", "推荐理由", "风险提醒"};
            Row header = sheet.createRow(rowIndex++);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            for (VolunteerService.VolunteerItem item : plan.getItems() == null ? List.<VolunteerService.VolunteerItem>of() : plan.getItems()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(item.getIndex());
                row.createCell(1).setCellValue(safe(item.getGradient()));
                row.createCell(2).setCellValue(safe(item.getUniversityName()));
                row.createCell(3).setCellValue(safe(item.getMajorName()));
                row.createCell(4).setCellValue(safe(item.getCity()));
                row.createCell(5).setCellValue(item.getPredictedMinRank());
                row.createCell(6).setCellValue(item.getRankDiff());
                row.createCell(7).setCellValue(safe(item.getPlanTrend()));
                row.createCell(8).setCellValue(item.getPlanExpansionIndex());
                row.createCell(9).setCellValue(item.getSchoolEnrollmentIndex());
                row.createCell(10).setCellValue(item.getChanceScore());
                row.createCell(11).setCellValue(safe(item.getRiskLevel()));
                row.createCell(12).setCellValue(item.getDataConfidence());
                row.createCell(13).setCellValue(safeText("export_excel_reason", planId, item.getRecommendReason()));
                row.createCell(14).setCellValue(safeText("export_excel_risk", planId, item.getRiskReason()));
            }
            rowIndex++;
            Row disclaimer = sheet.createRow(rowIndex);
            disclaimer.createCell(0).setCellValue(safeText("export_excel_disclaimer", planId, ComplianceConstants.SAFE_ASSISTANT_NOTICE));
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException("导出 Excel 失败");
        }
    }

    private VolunteerService.PlanResult requirePlan(Long planId, String accessKey) {
        VolunteerService.PlanResult plan = volunteerService.getPlanResult(planId, accessKey);
        if (plan == null) {
            throw new BizException("方案不存在或访问密钥无效");
        }
        return plan;
    }

    private void drawHeader(Graphics2D g, int y) {
        g.setColor(new Color(31, 41, 55));
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString("序号", 56, y);
        g.drawString("梯度", 104, y);
        g.drawString("院校", 154, y);
        g.drawString("专业/专业组", 380, y);
        g.drawString("机会指数", 690, y);
        g.drawString("风险等级", 790, y);
        g.drawString("数据参考度", 900, y);
    }

    private String gradientSummary(List<VolunteerService.VolunteerItem> items) {
        return String.format("冲 %d / 稳 %d / 保 %d / 垫 %d",
                count(items, "冲"), count(items, "稳"), count(items, "保"), count(items, "垫"));
    }

    private long count(List<VolunteerService.VolunteerItem> items, String gradient) {
        return items.stream().filter(item -> gradient.equals(item.getGradient())).count();
    }

    private String safeText(String type, Long id, String text) {
        return complianceTextGuard.sanitizeText(type, String.valueOf(id), safe(text));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /** 从 PlanResult.diagnosis 读 summary 字段，缺失时返回空串。 */
    private String readDiagnosisSummary(VolunteerService.PlanResult plan) {
        if (plan == null || plan.getDiagnosis() == null) return "";
        Object value = plan.getDiagnosis().get("summary");
        return value instanceof String s ? s : "";
    }

    /** 从 PlanResult.diagnosis 读 warnings 列表，缺失时返回空。 */
    private List<String> readDiagnosisWarnings(VolunteerService.PlanResult plan) {
        if (plan == null || plan.getDiagnosis() == null) return List.of();
        Object value = plan.getDiagnosis().get("warnings");
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream()
                .filter(o -> o instanceof String)
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .toList();
    }

    /** 长图中文截断辅助：超出 max 字符加〈…〉。 */
    private String clipText(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private String clip(Graphics2D g, String text, int maxWidth) {
        FontMetrics metrics = g.getFontMetrics();
        if (metrics.stringWidth(text) <= maxWidth) return text;
        String value = text;
        while (!value.isEmpty() && metrics.stringWidth(value + "...") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "...";
    }

    private void drawWrapped(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics metrics = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        int cy = y;
        for (char ch : text.toCharArray()) {
            if (metrics.stringWidth(line + String.valueOf(ch)) > maxWidth) {
                g.drawString(line.toString(), x, cy);
                line = new StringBuilder();
                cy += lineHeight;
            }
            line.append(ch);
        }
        if (!line.isEmpty()) {
            g.drawString(line.toString(), x, cy);
        }
    }
}
