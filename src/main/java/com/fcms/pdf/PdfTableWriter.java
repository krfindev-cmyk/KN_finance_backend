package com.fcms.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

/**
 * Small reusable helper for drawing simple tabular reports (header, colored rows,
 * footer) into a PDFBox document. Handles page breaks automatically. Entirely
 * in-memory - callers own the PDDocument and are responsible for saving it to a
 * ByteArrayOutputStream and closing it.
 */
public class PdfTableWriter {

    private static final float MARGIN = 40f;
    private static final float ROW_HEIGHT = 18f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

    public static final Color PAID_COLOR = new Color(22, 163, 74);
    public static final Color PENDING_COLOR = new Color(220, 38, 38);
    public static final Color PARTIAL_COLOR = new Color(217, 119, 6);
    public static final Color ADVANCE_COLOR = new Color(37, 99, 235);
    /** Dark slate banner used for table headers — brand-consistent with the app's premium look. */
    public static final Color HEADER_COLOR = new Color(30, 41, 59);
    public static final Color BRAND_COLOR = new Color(37, 99, 235);
    public static final Color ZEBRA_COLOR = new Color(248, 250, 252);
    public static final Color WHITE = Color.WHITE;

    private final PDDocument document;
    private final int[] colWidths; // relative widths, will be scaled to fit page
    private final String[] headers;
    private float[] absColWidths;
    private PDPage page;
    private PDPageContentStream stream;
    private float cursorY;
    private String title;
    private int rowIndex = 0;

    public PdfTableWriter(PDDocument document, String title, String[] headers, int[] colWidths) throws IOException {
        this.document = document;
        this.title = title;
        this.headers = headers;
        this.colWidths = colWidths;
        int totalUnits = 0;
        for (int w : colWidths) totalUnits += w;
        float usableWidth = PAGE_WIDTH - 2 * MARGIN;
        this.absColWidths = new float[colWidths.length];
        for (int i = 0; i < colWidths.length; i++) {
            absColWidths[i] = usableWidth * colWidths[i] / (float) totalUnits;
        }
        newPage();
    }

    private void newPage() throws IOException {
        if (stream != null) stream.close();
        page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        cursorY = PAGE_HEIGHT - MARGIN;

        if (title != null) {
            // Brand banner behind the report title, like the app's own header bar.
            stream.setNonStrokingColor(BRAND_COLOR);
            stream.addRect(MARGIN - 10, cursorY - 6, sum(absColWidths) + 20, 34);
            stream.fill();
            drawText(stream, MARGIN, cursorY + 6, "KR Finance", PDType1Font.HELVETICA_BOLD, 15, Color.WHITE);
            drawText(stream, MARGIN, cursorY - 10, title, PDType1Font.HELVETICA, 10, new Color(219, 234, 254));
            cursorY -= 46;
            title = null; // only print title on first page
        }
        rowIndex = 0;
        drawRow(headers, PDType1Font.HELVETICA_BOLD, HEADER_COLOR, Color.WHITE);
    }

    public void addRow(String[] values, Color bgColor, Color textColor) throws IOException {
        if (cursorY - ROW_HEIGHT < MARGIN) {
            newPage();
        }
        drawRow(values, PDType1Font.HELVETICA, bgColor, textColor);
    }

    private void drawRow(String[] values, PDFont font, Color bgColor, Color textColor) throws IOException {
        float x = MARGIN;
        float rowTop = cursorY;
        boolean isHeaderRow = bgColor != null && bgColor.equals(HEADER_COLOR);
        boolean isStatusRow = bgColor != null && !isHeaderRow && !bgColor.equals(WHITE);

        Color rowBg = bgColor;
        Color rowText = textColor;
        if (isStatusRow) {
            // Softer "badge tint" background instead of a solid saturated fill, with the
            // original status color kept for the text and a small accent bar on the left —
            // reads like a dashboard status pill rather than a highlighter-block row.
            rowBg = tint(bgColor, 0.88f);
            rowText = bgColor;
        } else if (!isHeaderRow) {
            // Subtle zebra striping for plain rows so long tables stay easy to scan.
            rowBg = (rowIndex % 2 == 1) ? ZEBRA_COLOR : WHITE;
        }

        if (rowBg != null) {
            stream.setNonStrokingColor(rowBg);
            stream.addRect(MARGIN, rowTop - ROW_HEIGHT + 4, sum(absColWidths), ROW_HEIGHT - 2);
            stream.fill();
        }
        if (isStatusRow) {
            stream.setNonStrokingColor(bgColor);
            stream.addRect(MARGIN, rowTop - ROW_HEIGHT + 4, 3, ROW_HEIGHT - 2);
            stream.fill();
        }
        float fontSize = 9f;
        for (int i = 0; i < values.length && i < absColWidths.length; i++) {
            String v = truncate(values[i], absColWidths[i], font);
            float textX = x + 4;
            // Numeric-looking cells (amounts, percentages) are right-aligned so the digits line
            // up by place value column to column, instead of every value starting flush left.
            if (!isHeaderRow && looksNumeric(v)) {
                float textWidth = font.getStringWidth(v) / 1000f * fontSize;
                textX = x + absColWidths[i] - textWidth - 6;
                if (textX < x + 2) textX = x + 2;
            }
            drawText(stream, textX, rowTop - ROW_HEIGHT + 8, v, font, fontSize, rowText);
            x += absColWidths[i];
        }
        cursorY -= ROW_HEIGHT;
        if (!isHeaderRow) rowIndex++;
    }

    /** True for values that are basically a formatted number (amounts, percentages) — e.g. "4,00,000.00", "-1,200", "45.5%". */
    private boolean looksNumeric(String v) {
        if (v == null || v.isBlank()) return false;
        return v.matches("-?[0-9,]+(\\.[0-9]+)?%?");
    }

    private Color tint(Color c, float towardsWhite) {
        int r = (int) (c.getRed() + (255 - c.getRed()) * towardsWhite);
        int g = (int) (c.getGreen() + (255 - c.getGreen()) * towardsWhite);
        int b = (int) (c.getBlue() + (255 - c.getBlue()) * towardsWhite);
        return new Color(r, g, b);
    }

    private String truncate(String value, float width, PDFont font) {
        if (value == null) return "";
        try {
            int maxChars = Math.max(3, (int) (width / 5.0));
            if (value.length() > maxChars) {
                return value.substring(0, maxChars - 1) + "...";
            }
            return value;
        } catch (Exception e) {
            return value;
        }
    }

    private float sum(float[] arr) {
        float s = 0;
        for (float v : arr) s += v;
        return s;
    }

    private void drawText(PDPageContentStream cs, float x, float y, String text, PDFont font, float size, Color color) throws IOException {
        if (text == null) text = "";
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(text));
        cs.endText();
    }

    private String sanitize(String s) {
        // PDFBox standard fonts (WinAnsiEncoding) can't render arbitrary unicode; strip to be safe.
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(c < 256 ? c : '?');
        }
        return sb.toString();
    }

    public void addFooter(String text) throws IOException {
        if (cursorY - ROW_HEIGHT < MARGIN) {
            newPage();
        }
        cursorY -= 8;
        drawText(stream, MARGIN, cursorY, text, PDType1Font.HELVETICA_BOLD, 11, Color.BLACK);
        cursorY -= ROW_HEIGHT;
    }

    public void close() throws IOException {
        if (stream != null) stream.close();
    }
}
