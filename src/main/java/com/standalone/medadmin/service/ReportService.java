package com.standalone.medadmin.service;

import com.standalone.medadmin.model.Medicine;
import com.standalone.medadmin.model.StockMovement;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class ReportService {

    public void exportCurrentStockCsv(File file, List<Medicine> medicines) {
        writeCsv(file, medicines, false);
    }

    public void exportNearExpiryCsv(File file, List<Medicine> medicines) {
        writeCsv(file, medicines, true);
    }

    public void exportCurrentStockPdf(File file, List<Medicine> medicines) {
        exportMedicinesPdf(file, medicines, "Current Stock Report", false);
    }

    public void exportNearExpiryPdf(File file, List<Medicine> medicines) {
        exportMedicinesPdf(file, medicines, "Near Expiry Report", true);
    }

    public void exportStockMovementsCsv(File file, List<StockMovement> movements) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("id,medicine,type,quantity,reference,note,created_at");
            writer.newLine();
            for (StockMovement m : movements) {
                writer.write(String.format("%d,%s,%s,%d,%s,%s,%s",
                        m.id(),
                        sanitize(m.medicineName()),
                        sanitize(m.type()),
                        m.quantity(),
                        sanitize(m.referenceNo()),
                        sanitize(m.note()),
                        sanitize(m.createdAt())));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to export transactions report", e);
        }
    }

    public void exportStockMovementsPdf(File file, List<StockMovement> movements, String rangeLabel) {
        try (PDDocument document = new PDDocument()) {
            final float margin = 36f;
            final float rowHeight = 18f;
            final float[] colWidths = new float[]{34f, 84f, 150f, 34f, 34f, 84f, 112f};
            final String[] headers = new String[]{"ID", "Date", "Medicine", "Type", "Qty", "Reference", "Note"};

            int totalQty = movements.stream().mapToInt(StockMovement::quantity).sum();
            int totalIn = movements.stream().filter(m -> "IN".equalsIgnoreCase(m.type())).mapToInt(StockMovement::quantity).sum();
            int totalOut = movements.stream().filter(m -> "OUT".equalsIgnoreCase(m.type())).mapToInt(StockMovement::quantity).sum();

            int rowIndex = 0;
            int pageNumber = 1;
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDPageContentStream content = new PDPageContentStream(document, page);
            float y = drawPdfHeader(content, page, margin, rangeLabel, movements.size(), totalQty, totalIn, totalOut, pageNumber);
            y = drawTableHeader(content, margin, y, colWidths, headers, rowHeight);

            for (StockMovement movement : movements) {
                if (y - rowHeight < margin + 18f) {
                    drawPdfFooter(content, page, margin, pageNumber);
                    content.close();

                    pageNumber++;
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = drawPdfHeader(content, page, margin, rangeLabel, movements.size(), totalQty, totalIn, totalOut, pageNumber);
                    y = drawTableHeader(content, margin, y, colWidths, headers, rowHeight);
                }

                drawDataRow(content, margin, y, colWidths, rowHeight, rowIndex, movement);
                y -= rowHeight;
                rowIndex++;
            }

            drawPdfFooter(content, page, margin, pageNumber);
            content.close();
            document.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to export transactions PDF", e);
        }
    }

    private void exportMedicinesPdf(File file, List<Medicine> medicines, String title, boolean includeDaysLeft) {
        try (PDDocument document = new PDDocument()) {
            final float margin = 36f;
            final float rowHeight = 18f;
            final float[] colWidths = includeDaysLeft
                    ? new float[]{34f, 190f, 52f, 60f, 80f, 62f}
                    : new float[]{34f, 220f, 60f, 70f, 90f};
            final String[] headers = includeDaysLeft
                    ? new String[]{"ID", "Medicine", "Qty", "Price", "Expiry", "Days Left"}
                    : new String[]{"ID", "Medicine", "Qty", "Price", "Expiry"};

            int totalQty = medicines.stream().mapToInt(Medicine::quantity).sum();
            int pageNumber = 1;
            int rowIndex = 0;

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            float y = drawMedicinePdfHeader(content, page, margin, title, medicines.size(), totalQty, pageNumber);
            y = drawTableHeader(content, margin, y, colWidths, headers, rowHeight);

            for (Medicine m : medicines) {
                if (y - rowHeight < margin + 18f) {
                    drawPdfFooter(content, page, margin, pageNumber);
                    content.close();

                    pageNumber++;
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = drawMedicinePdfHeader(content, page, margin, title, medicines.size(), totalQty, pageNumber);
                    y = drawTableHeader(content, margin, y, colWidths, headers, rowHeight);
                }

                drawMedicineRow(content, margin, y, colWidths, rowHeight, rowIndex, m, includeDaysLeft);
                y -= rowHeight;
                rowIndex++;
            }

            drawPdfFooter(content, page, margin, pageNumber);
            content.close();
            document.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to export medicines PDF", e);
        }
    }

    private void writeCsv(File file, List<Medicine> medicines, boolean includeDaysLeft) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            if (includeDaysLeft) {
                writer.write("id,name,quantity,price,expiry_date,days_left");
            } else {
                writer.write("id,name,quantity,price,expiry_date");
            }
            writer.newLine();

            for (Medicine m : medicines) {
                if (includeDaysLeft) {
                    long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), m.expiryDate());
                    writer.write(String.format("%d,%s,%d,%.2f,%s,%d",
                            m.id(), sanitize(m.name()), m.quantity(), m.price(), m.expiryDate(), daysLeft));
                } else {
                    writer.write(String.format("%d,%s,%d,%.2f,%s",
                            m.id(), sanitize(m.name()), m.quantity(), m.price(), m.expiryDate()));
                }
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to export report", e);
        }
    }

    private String sanitize(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return '"' + escaped + '"';
    }

    private float drawPdfHeader(
            PDPageContentStream content,
            PDPage page,
            float margin,
            String rangeLabel,
            int totalRows,
            int totalQty,
            int totalIn,
            int totalOut,
            int pageNumber
    ) throws IOException {
        float y = page.getMediaBox().getHeight() - margin;
        float usableWidth = page.getMediaBox().getWidth() - (margin * 2);

        content.setNonStrokingColor(14, 165, 164);
        content.addRect(margin, y - 30, usableWidth, 24);
        content.fill();

        writeText(content, "Stock Transactions Report", margin + 10, y - 21, PDType1Font.HELVETICA_BOLD, 12, 255, 255, 255);
        writeText(content, "Page " + pageNumber, margin + usableWidth - 54, y - 21, PDType1Font.HELVETICA_BOLD, 10, 255, 255, 255);

        y -= 42;
        content.setNonStrokingColor(247, 250, 252);
        content.addRect(margin, y - 42, usableWidth, 38);
        content.fill();
        content.setStrokingColor(203, 213, 225);
        content.addRect(margin, y - 42, usableWidth, 38);
        content.stroke();

        writeText(content, "Range: " + safe(rangeLabel), margin + 8, y - 15, PDType1Font.HELVETICA_BOLD, 9, 15, 23, 42);
        writeText(content, "Rows: " + totalRows, margin + 130, y - 15, PDType1Font.HELVETICA, 9, 15, 23, 42);
        writeText(content, "Total Qty: " + totalQty, margin + 200, y - 15, PDType1Font.HELVETICA, 9, 15, 23, 42);
        writeText(content, "IN: " + totalIn, margin + 288, y - 15, PDType1Font.HELVETICA, 9, 15, 23, 42);
        writeText(content, "OUT: " + totalOut, margin + 338, y - 15, PDType1Font.HELVETICA, 9, 15, 23, 42);
        writeText(content, "Generated: " + LocalDate.now(), margin + 400, y - 15, PDType1Font.HELVETICA, 9, 15, 23, 42);
        return y - 52;
    }

    private float drawTableHeader(
            PDPageContentStream content,
            float x,
            float y,
            float[] colWidths,
            String[] headers,
            float rowHeight
    ) throws IOException {
        content.setNonStrokingColor(226, 232, 240);
        content.addRect(x, y - rowHeight, totalWidth(colWidths), rowHeight);
        content.fill();

        content.setStrokingColor(148, 163, 184);
        content.addRect(x, y - rowHeight, totalWidth(colWidths), rowHeight);
        content.stroke();

        float cursorX = x;
        for (int i = 0; i < headers.length; i++) {
            if (i > 0) {
                content.moveTo(cursorX, y);
                content.lineTo(cursorX, y - rowHeight);
                content.stroke();
            }
            writeText(content, headers[i], cursorX + 3, y - 12, PDType1Font.HELVETICA_BOLD, 9, 30, 41, 59);
            cursorX += colWidths[i];
        }
        return y - rowHeight;
    }

    private void drawDataRow(
            PDPageContentStream content,
            float x,
            float y,
            float[] colWidths,
            float rowHeight,
            int rowIndex,
            StockMovement movement
    ) throws IOException {
        if (rowIndex % 2 == 0) {
            content.setNonStrokingColor(248, 250, 252);
            content.addRect(x, y - rowHeight, totalWidth(colWidths), rowHeight);
            content.fill();
        }

        content.setStrokingColor(203, 213, 225);
        content.addRect(x, y - rowHeight, totalWidth(colWidths), rowHeight);
        content.stroke();

        String[] data = new String[]{
                String.valueOf(movement.id()),
                fitText(movement.createdAt(), 18),
                fitText(movement.medicineName(), 30),
                fitText(movement.type(), 3),
                String.valueOf(movement.quantity()),
                fitText(movement.referenceNo(), 16),
                fitText(movement.note(), 24)
        };

        float cursorX = x;
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                content.moveTo(cursorX, y);
                content.lineTo(cursorX, y - rowHeight);
                content.stroke();
            }
            writeText(content, data[i], cursorX + 3, y - 12, PDType1Font.HELVETICA, 8.5f, 15, 23, 42);
            cursorX += colWidths[i];
        }
    }

    private void drawPdfFooter(PDPageContentStream content, PDPage page, float margin, int pageNumber) throws IOException {
        float y = margin - 10;
        content.setStrokingColor(203, 213, 225);
        content.moveTo(margin, y + 8);
        content.lineTo(page.getMediaBox().getWidth() - margin, y + 8);
        content.stroke();
        writeText(content, "Standalone Medicine Admin", margin, y, PDType1Font.HELVETICA, 8, 100, 116, 139);
        writeText(content, "Page " + pageNumber, page.getMediaBox().getWidth() - margin - 34, y, PDType1Font.HELVETICA, 8, 100, 116, 139);
    }

    private void writeText(
            PDPageContentStream content,
            String text,
            float x,
            float y,
            PDType1Font font,
            float fontSize,
            int r,
            int g,
            int b
    ) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.setNonStrokingColor(r, g, b);
        content.newLineAtOffset(x, y);
        content.showText(safe(text));
        content.endText();
    }

    private float totalWidth(float[] widths) {
        float total = 0f;
        for (float width : widths) total += width;
        return total;
    }

    private String safe(String value) {
        if (value == null) return "";
        return value.replace("\n", " ").replace("\r", " ");
    }

    private float drawMedicinePdfHeader(
            PDPageContentStream content,
            PDPage page,
            float margin,
            String title,
            int totalRows,
            int totalQty,
            int pageNumber
    ) throws IOException {
        float y = page.getMediaBox().getHeight() - margin;
        float usableWidth = page.getMediaBox().getWidth() - (margin * 2);

        content.setNonStrokingColor(14, 165, 164);
        content.addRect(margin, y - 30, usableWidth, 24);
        content.fill();

        writeText(content, title, margin + 10, y - 21, PDType1Font.HELVETICA_BOLD, 12, 255, 255, 255);
        writeText(content, "Page " + pageNumber, margin + usableWidth - 54, y - 21, PDType1Font.HELVETICA_BOLD, 10, 255, 255, 255);

        y -= 42;
        content.setNonStrokingColor(247, 250, 252);
        content.addRect(margin, y - 30, usableWidth, 26);
        content.fill();
        content.setStrokingColor(203, 213, 225);
        content.addRect(margin, y - 30, usableWidth, 26);
        content.stroke();

        writeText(content, "Rows: " + totalRows, margin + 8, y - 15, PDType1Font.HELVETICA, 9, 15, 23, 42);
        writeText(content, "Total Qty: " + totalQty, margin + 92, y - 15, PDType1Font.HELVETICA, 9, 15, 23, 42);
        writeText(content, "Generated: " + LocalDate.now(), margin + 190, y - 15, PDType1Font.HELVETICA, 9, 15, 23, 42);
        return y - 40;
    }

    private void drawMedicineRow(
            PDPageContentStream content,
            float x,
            float y,
            float[] colWidths,
            float rowHeight,
            int rowIndex,
            Medicine medicine,
            boolean includeDaysLeft
    ) throws IOException {
        if (rowIndex % 2 == 0) {
            content.setNonStrokingColor(248, 250, 252);
            content.addRect(x, y - rowHeight, totalWidth(colWidths), rowHeight);
            content.fill();
        }

        content.setStrokingColor(203, 213, 225);
        content.addRect(x, y - rowHeight, totalWidth(colWidths), rowHeight);
        content.stroke();

        String[] data;
        if (includeDaysLeft) {
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), medicine.expiryDate());
            data = new String[]{
                    String.valueOf(medicine.id()),
                    fitText(medicine.name(), 34),
                    String.valueOf(medicine.quantity()),
                    String.format("%.2f", medicine.price()),
                    medicine.expiryDate().toString(),
                    String.valueOf(daysLeft)
            };
        } else {
            data = new String[]{
                    String.valueOf(medicine.id()),
                    fitText(medicine.name(), 40),
                    String.valueOf(medicine.quantity()),
                    String.format("%.2f", medicine.price()),
                    medicine.expiryDate().toString()
            };
        }

        float cursorX = x;
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                content.moveTo(cursorX, y);
                content.lineTo(cursorX, y - rowHeight);
                content.stroke();
            }
            writeText(content, data[i], cursorX + 3, y - 12, PDType1Font.HELVETICA, 8.5f, 15, 23, 42);
            cursorX += colWidths[i];
        }
    }

    private String fitText(String value, int maxLen) {
        String safe = value == null || value.isBlank() ? "-" : value.replace('\n', ' ').trim();
        if (safe.length() <= maxLen) return safe;
        return safe.substring(0, Math.max(maxLen - 3, 1)) + "...";
    }
}
