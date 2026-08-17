package com.fcms.naveen.service;

import com.fcms.naveen.dto.BillDetail;
import com.fcms.naveen.model.NaveenBillItem;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Renders a NaveenBill as a PDF invoice styled after the physical "SRM Naveen Vegetables" bill
 * book: a bordered name/phone header box, S.No / Item / Qty / Rate / Amount line items, and a
 * TOTAL row at the bottom.
 */
@Service
public class NaveenBillPdfService {

    private static final String BUSINESS_NAME = "SRM NAVEEN VEGETABLES";
    private static final String BUSINESS_PHONE = "Ph: 7094029304";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    public byte[] generate(BillDetail detail) {
        float margin = 40f;
        float pageWidth = PDRectangle.A4.getWidth();
        float pageHeight = PDRectangle.A4.getHeight();
        float usableWidth = pageWidth - 2 * margin;
        float rowHeight = 20f;

        // S.No | Item | Qty | Rate | Amount
        float[] colWidths = {usableWidth * 0.08f, usableWidth * 0.40f, usableWidth * 0.16f, usableWidth * 0.16f, usableWidth * 0.20f};
        String[] headers = {"S.No", "Item", "Qty (kg)", "Rate/kg", "Amount"};
        boolean[] rightAlign = {false, false, true, true, true};

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PDDocument document = new PDDocument()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            float y = pageHeight - margin;

            // ---- Header box: business name + phone, boxed like the physical invoice book ----
            float headerHeight = 54f;
            stream.setStrokingColor(Color.BLACK);
            stream.setLineWidth(1.2f);
            stream.addRect(margin, y - headerHeight, usableWidth, headerHeight);
            stream.stroke();
            y = text(stream, margin, y - 22, BUSINESS_NAME, PDType1Font.HELVETICA_BOLD, 16, Color.BLACK, usableWidth, true);
            y = text(stream, margin, y - 4, BUSINESS_PHONE, PDType1Font.HELVETICA, 11, Color.DARK_GRAY, usableWidth, true);
            y = pageHeight - margin - headerHeight - 20;

            // ---- Invoice meta ----
            String invoiceNo = "INV-" + String.format("%04d", detail.getBill().getId());
            String billedTo = detail.getSupplierName() != null ? "Supplier: " + detail.getSupplierName()
                    : (detail.getBill().getCustomerName() != null && !detail.getBill().getCustomerName().isBlank()
                        ? "Customer: " + detail.getBill().getCustomerName() : "");
            line(stream, margin, y, "Invoice No: " + invoiceNo, PDType1Font.HELVETICA_BOLD, 11, Color.BLACK);
            line(stream, pageWidth - margin - 160, y, "Date: " + (detail.getBill().getDate() != null ? detail.getBill().getDate().format(DATE_FMT) : "-"),
                    PDType1Font.HELVETICA, 11, Color.BLACK);
            y -= 18;
            if (!billedTo.isBlank()) {
                line(stream, margin, y, billedTo, PDType1Font.HELVETICA, 11, Color.BLACK);
                y -= 18;
            }
            y -= 6;

            // ---- Table header ----
            y = tableRow(stream, margin, y, rowHeight, colWidths, headers, PDType1Font.HELVETICA_BOLD, new Color(30, 41, 59), Color.WHITE, rightAlign);

            List<NaveenBillItem> items = detail.getItems();
            int sno = 1;
            for (NaveenBillItem item : items) {
                if (y - rowHeight < margin + 60) {
                    stream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    y = pageHeight - margin;
                    y = tableRow(stream, margin, y, rowHeight, colWidths, headers, PDType1Font.HELVETICA_BOLD, new Color(30, 41, 59), Color.WHITE, rightAlign);
                }
                String[] values = {
                        String.valueOf(sno++),
                        item.getItem() == null ? "-" : item.getItem(),
                        item.getQty() == null ? "-" : fmt(item.getQty()),
                        item.getRate() == null ? "-" : fmt(item.getRate()),
                        fmt(item.getAmount())
                };
                y = tableRow(stream, margin, y, rowHeight, colWidths, values, PDType1Font.HELVETICA, Color.WHITE, Color.BLACK, rightAlign);
            }

            // ---- TOTAL row ----
            String[] totalRow = {"", "", "", "TOTAL", fmt(detail.getBill().getTotalAmount())};
            y = tableRow(stream, margin, y, rowHeight, colWidths, totalRow, PDType1Font.HELVETICA_BOLD, new Color(226, 232, 240), Color.BLACK, rightAlign);

            y -= 30;
            line(stream, margin, y, "Thank You", PDType1Font.HELVETICA_OBLIQUE, 11, Color.DARK_GRAY);
            stream.close();

            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate bill PDF", e);
        }
    }

    private float text(PDPageContentStream stream, float x, float y, String value, PDType1Font font, float size, Color color, float width, boolean center) throws IOException {
        stream.setNonStrokingColor(color);
        stream.beginText();
        float textWidth = font.getStringWidth(sanitize(value)) / 1000 * size;
        float drawX = center ? x + (width - textWidth) / 2 : x;
        stream.newLineAtOffset(drawX, y);
        stream.setFont(font, size);
        stream.showText(sanitize(value));
        stream.endText();
        return y;
    }

    private void line(PDPageContentStream stream, float x, float y, String value, PDType1Font font, float size, Color color) throws IOException {
        stream.setNonStrokingColor(color);
        stream.beginText();
        stream.newLineAtOffset(x, y);
        stream.setFont(font, size);
        stream.showText(sanitize(value));
        stream.endText();
    }

    private float tableRow(PDPageContentStream stream, float x, float y, float rowHeight, float[] colWidths,
                            String[] values, PDType1Font font, Color bg, Color textColor, boolean[] rightAlign) throws IOException {
        if (bg != Color.WHITE) {
            stream.setNonStrokingColor(bg);
            stream.addRect(x, y - rowHeight, sum(colWidths), rowHeight);
            stream.fill();
        }
        stream.setStrokingColor(new Color(203, 213, 225));
        stream.setLineWidth(0.5f);
        stream.addRect(x, y - rowHeight, sum(colWidths), rowHeight);
        stream.stroke();

        float cx = x;
        float fontSize = 9.5f;
        for (int i = 0; i < values.length; i++) {
            float colW = colWidths[i];
            stream.setStrokingColor(new Color(203, 213, 225));
            stream.moveTo(cx, y);
            stream.lineTo(cx, y - rowHeight);
            stream.stroke();

            String val = sanitize(values[i] == null ? "" : values[i]);
            stream.setNonStrokingColor(textColor);
            stream.beginText();
            stream.setFont(font, fontSize);
            float textWidth = font.getStringWidth(val) / 1000 * fontSize;
            float pad = 6f;
            float textX = rightAlign[i] ? cx + colW - textWidth - pad : cx + pad;
            stream.newLineAtOffset(textX, y - rowHeight + (rowHeight - fontSize) / 2 + 2);
            stream.showText(val);
            stream.endText();
            cx += colW;
        }
        return y - rowHeight;
    }

    private float sum(float[] widths) {
        float total = 0;
        for (float w : widths) total += w;
        return total;
    }

    private String fmt(Double d) {
        return d == null ? "0.00" : String.format(Locale.US, "%.2f", d);
    }

    private String sanitize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) sb.append(c < 256 ? c : '?');
        return sb.toString();
    }
}
