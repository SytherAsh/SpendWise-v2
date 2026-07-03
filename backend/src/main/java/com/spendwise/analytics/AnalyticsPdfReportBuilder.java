package com.spendwise.analytics;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Builds the formatted report for `GET /analytics/export/pdf` (E7-S2-T2) with OpenPDF
 * (LGPL/MPL — see build.gradle.kts). A totals summary plus a per-category table satisfies the
 * DoD's "simple chart or table" — no charting library needed.
 */
final class AnalyticsPdfReportBuilder {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneOffset.UTC);

    private AnalyticsPdfReportBuilder() {}

    static byte[] build(AnalyticsSummary summary, Instant from, Instant to) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);

            document.add(new Paragraph("SpendWise Financial Report", titleFont));
            document.add(new Paragraph("Period: " + DATE_FORMAT.format(from) + " - " + DATE_FORMAT.format(to), bodyFont));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Total Spend: " + summary.overall().totalSpend().toPlainString(), bodyFont));
            document.add(new Paragraph("Total Income: " + summary.overall().totalIncome().toPlainString(), bodyFont));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Category Breakdown", headingFont));
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.addCell(new PdfPCell(new Phrase("Category", tableHeaderFont)));
            table.addCell(new PdfPCell(new Phrase("Spend", tableHeaderFont)));
            table.addCell(new PdfPCell(new Phrase("Income", tableHeaderFont)));
            table.addCell(new PdfPCell(new Phrase("Transactions", tableHeaderFont)));
            for (CategoryTotal category : summary.categories()) {
                table.addCell(new Phrase(category.categoryName(), bodyFont));
                table.addCell(new Phrase(category.totalSpend().toPlainString(), bodyFont));
                table.addCell(new Phrase(category.totalIncome().toPlainString(), bodyFont));
                table.addCell(new Phrase(String.valueOf(category.transactionCount()), bodyFont));
            }
            document.add(table);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate PDF report", e);
        }
    }
}
