package com.sanproject.service_planner.service;

import com.sanproject.service_planner.domain.Customer;
import com.sanproject.service_planner.domain.ServiceBooking;
import com.sanproject.service_planner.domain.Vehicle;
import com.sanproject.service_planner.email.CustomerDateTimeFormatter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Branded PDF invoice (completion email + client download).
 * Visual language matches the web app and HTML emails: white page, #1d1d1f type,
 * #6e6e73 secondary text, #f5f5f7 rounded panels and hairline #d2d2d7 separators.
 */
@Service
public class InvoicePdfService {

    private static final String BRAND_NAME = "myCarPlanner";
    private static final String WORKSHOP_NAME = "Hussarya Service";
    /** Helvetica/WinAnsi cannot render Ł or ź — keep ASCII for PDF Type1 fonts. */
    private static final String WORKSHOP_ADDRESS = "Sportowa 31, Lodz, Poland";
    private static final String WORKSHOP_HOURS = "06:00-20:00";
    private static final String CURRENCY = "EUR";

    private static final Color TEXT = new Color(0x1d, 0x1d, 0x1f);
    private static final Color TEXT_SECONDARY = new Color(0x6e, 0x6e, 0x73);
    private static final Color SEPARATOR = new Color(0xd2, 0xd2, 0xd7);
    private static final Color PANEL = new Color(0xf5, 0xf5, 0xf7);
    private static final ClassPathResource LOGO =
            new ClassPathResource("email/logo.png");

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final DateTimeFormatter ISSUE_DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private static final float MARGIN = 56f;
    private static final float PANEL_RADIUS = 12f;
    private static final float PANEL_PADDING = 20f;
    private static final int MAX_ISSUE_LINES = 6;

    private final String sellerName;
    private final String sellerNip;
    private final BigDecimal vatRate;

    public InvoicePdfService(
            @Value("${app.invoice.seller-name}") String sellerName,
            @Value("${app.invoice.seller-nip}") String sellerNip,
            @Value("${app.invoice.vat-rate}") BigDecimal vatRate) {
        this.sellerName = sellerName;
        this.sellerNip = sellerNip;
        this.vatRate = vatRate;
    }

    public byte[] createInvoicePdf(ServiceBooking booking, Customer customer) {
        BigDecimal gross = booking.getFinalCost() != null
                ? booking.getFinalCost()
                : booking.getEstimatedCost();
        VatBreakdown vat = gross != null ? VatBreakdown.fromGross(gross, vatRate) : null;
        LocalDate issued = booking.getInvoiceIssuedAt() != null
                ? booking.getInvoiceIssuedAt().toLocalDate()
                : LocalDate.now();
        String rateText = vatRate.stripTrailingZeros().toPlainString() + "%";

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("VAT invoice " + invoiceNumber(booking));
            info.setAuthor(WORKSHOP_NAME);
            info.setCreator(BRAND_NAME);

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDRectangle box = page.getMediaBox();
            float pageW = box.getWidth();
            float pageH = box.getHeight();
            float contentW = pageW - MARGIN * 2;
            float right = pageW - MARGIN;

            PDImageXObject logo = loadLogo(doc);

            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                float top = pageH - MARGIN;

                if (logo != null) {
                    float logoH = 48f;
                    float logoW = logo.getWidth() * (logoH / logo.getHeight());
                    content.drawImage(logo, MARGIN, top - logoH, logoW, logoH);
                }
                drawTextRight(content, FONT_BOLD, 10, right, top - 10, WORKSHOP_NAME, TEXT);
                drawTextRight(content, FONT, 9, right, top - 24, WORKSHOP_ADDRESS, TEXT_SECONDARY);
                drawTextRight(content, FONT, 9, right, top - 37,
                        "Open daily " + WORKSHOP_HOURS, TEXT_SECONDARY);

                float y = top - 104f;
                drawText(content, FONT_BOLD, 32, MARGIN, y, "VAT invoice", TEXT);

                y -= 36f;
                float colW = contentW / 4f;
                drawLabelValue(content, MARGIN, y, "Invoice number", invoiceNumber(booking));
                drawLabelValue(content, MARGIN + colW, y, "Date issued", ISSUE_DATE.format(issued));
                drawLabelValue(content, MARGIN + colW * 2, y, "Date of service", ISSUE_DATE.format(issued));
                drawLabelValue(content, MARGIN + colW * 3, y, "Booking", "#" + booking.getId());

                y -= 34f;
                hairline(content, MARGIN, right, y);

                y -= 28f;
                float thirdW = contentW / 3f;
                Vehicle v = booking.getVehicle();
                String vin = v != null && v.getVin() != null && !v.getVin().isBlank() ? v.getVin() : null;
                float sellerEnd = drawBlock(content, MARGIN, y, thirdW - 16f, "Seller",
                        sellerName, WORKSHOP_ADDRESS, "NIP " + sellerNip);
                float buyerEnd = drawBlock(content, MARGIN + thirdW, y, thirdW - 16f, "Buyer",
                        booking.getCustomerName(), customer.getEmail());
                float vehicleEnd = drawBlock(content, MARGIN + thirdW * 2, y, thirdW - 16f, "Vehicle",
                        booking.getCarModel(), vin != null ? "VIN " + vin : null);
                y = Math.min(sellerEnd, Math.min(buyerEnd, vehicleEnd)) - 20f;

                List<String[]> details = buildDetailRows(booking);
                if (!details.isEmpty()) {
                    y = drawDetailsPanel(content, MARGIN, y, contentW, details) - 32f;
                }

                drawText(content, FONT, 9, MARGIN, y, "Description", TEXT_SECONDARY);
                drawTextRight(content, FONT, 9, right, y, "Amount", TEXT_SECONDARY);
                y -= 10f;
                hairline(content, MARGIN, right, y);

                List<String> services = booking.getServiceTypes() != null
                        ? booking.getServiceTypes()
                        : List.of();
                if (services.isEmpty()) {
                    y = drawLineItem(content, MARGIN, right, y, "Workshop service", "");
                } else {
                    for (String service : services) {
                        y = drawLineItem(content, MARGIN, right, y, service, "Included");
                    }
                }

                y -= 26f;
                drawText(content, FONT, 11, MARGIN, y, "Net amount", TEXT_SECONDARY);
                drawTextRight(content, FONT, 11, right, y, money(vat == null ? null : vat.net()), TEXT);
                y -= 18f;
                drawText(content, FONT, 11, MARGIN, y, "VAT " + rateText, TEXT_SECONDARY);
                drawTextRight(content, FONT, 11, right, y, money(vat == null ? null : vat.tax()), TEXT);
                y -= 12f;
                hairline(content, MARGIN, right, y);

                y -= 26f;
                drawText(content, FONT_BOLD, 13, MARGIN, y, "Total due", TEXT);
                drawTextRight(content, FONT_BOLD, 20, right, y - 2f, money(gross), TEXT);
                y -= 18f;
                drawText(content, FONT, 9, MARGIN, y,
                        "All amounts in " + CURRENCY + ". The total includes " + rateText + " VAT.",
                        TEXT_SECONDARY);

                float footerY = MARGIN + 30f;
                hairline(content, MARGIN, right, footerY + 16f);
                drawText(content, FONT, 9, MARGIN, footerY,
                        "Thank you for choosing " + WORKSHOP_NAME + ". Your vehicle can be collected during "
                                + "workshop hours, " + WORKSHOP_HOURS + ".",
                        TEXT_SECONDARY);
                drawText(content, FONT, 9, MARGIN, footerY - 14f,
                        BRAND_NAME + "  |  " + WORKSHOP_ADDRESS, TEXT_SECONDARY);
            }

            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate invoice PDF for booking " + booking.getId(), e);
        }
    }

    private String invoiceNumber(ServiceBooking booking) {
        if (booking.getInvoiceNumber() != null) {
            return booking.getInvoiceNumber();
        }
        return "Pending (booking #" + booking.getId() + ")";
    }

    private static String money(BigDecimal amount) {
        return amount != null ? amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + CURRENCY : "N/A";
    }

    /** Workshop prices are VAT-inclusive, so net and tax are derived from the gross total. */
    record VatBreakdown(BigDecimal net, BigDecimal tax, BigDecimal gross) {

        static VatBreakdown fromGross(BigDecimal gross, BigDecimal ratePercent) {
            BigDecimal grossRounded = gross.setScale(2, RoundingMode.HALF_UP);
            BigDecimal divisor = BigDecimal.ONE.add(ratePercent.movePointLeft(2));
            BigDecimal net = grossRounded.divide(divisor, 2, RoundingMode.HALF_UP);
            return new VatBreakdown(net, grossRounded.subtract(net), grossRounded);
        }
    }

    private List<String[]> buildDetailRows(ServiceBooking booking) {
        List<String[]> rows = new ArrayList<>();
        if (booking.getScheduledDateTime() != null) {
            rows.add(new String[]{"Appointment", formatDateTime(booking.getScheduledDateTime())});
        }
        if (booking.getCustomerDescription() != null && !booking.getCustomerDescription().isBlank()) {
            rows.add(new String[]{"You reported", booking.getCustomerDescription()});
        }
        return rows;
    }

    private void drawLabelValue(PDPageContentStream content, float x, float y, String label, String value)
            throws IOException {
        drawText(content, FONT, 9, x, y, label, TEXT_SECONDARY);
        drawText(content, FONT, 11, x, y - 15f, value, TEXT);
    }

    /** Label + up to two lines; returns the baseline below the block. */
    private float drawBlock(PDPageContentStream content, float x, float y, float width,
                            String label, String primary, String... secondary) throws IOException {
        drawText(content, FONT, 9, x, y, label, TEXT_SECONDARY);
        float lineY = y - 16f;
        for (String line : wrap(primary, FONT_BOLD, 11, width)) {
            drawText(content, FONT_BOLD, 11, x, lineY, line, TEXT);
            lineY -= 15f;
        }
        for (String text : secondary) {
            if (text == null || text.isBlank()) {
                continue;
            }
            for (String line : wrap(text, FONT, 10, width)) {
                drawText(content, FONT, 10, x, lineY, line, TEXT_SECONDARY);
                lineY -= 14f;
            }
        }
        return lineY;
    }

    /** Grey rounded panel with stacked label/value rows; returns its bottom edge. */
    private float drawDetailsPanel(PDPageContentStream content, float x, float top, float width,
                                   List<String[]> rows) throws IOException {
        float innerW = width - PANEL_PADDING * 2;
        List<List<String>> wrapped = new ArrayList<>();
        float height = PANEL_PADDING * 2;
        for (int i = 0; i < rows.size(); i++) {
            List<String> lines = wrap(rows.get(i)[1], FONT, 11, innerW);
            if (lines.size() > MAX_ISSUE_LINES) {
                lines = new ArrayList<>(lines.subList(0, MAX_ISSUE_LINES));
                lines.set(MAX_ISSUE_LINES - 1, lines.get(MAX_ISSUE_LINES - 1) + " ...");
            }
            wrapped.add(lines);
            height += 14f + lines.size() * 15f;
            if (i > 0) {
                height += 20f;
            }
        }

        float bottom = top - height;
        fillRoundRect(content, x, bottom, width, height, PANEL_RADIUS, PANEL);

        float y = top - PANEL_PADDING - 9f;
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                y -= 4f;
                hairline(content, x + PANEL_PADDING, x + width - PANEL_PADDING, y + 10f);
                y -= 16f;
            }
            drawText(content, FONT, 9, x + PANEL_PADDING, y, rows.get(i)[0], TEXT_SECONDARY);
            y -= 15f;
            for (String line : wrapped.get(i)) {
                drawText(content, FONT, 11, x + PANEL_PADDING, y, line, TEXT);
                y -= 15f;
            }
        }
        return bottom;
    }

    private float drawLineItem(PDPageContentStream content, float left, float right, float y,
                               String description, String amount) throws IOException {
        float rowY = y - 20f;
        float amountW = amount.isEmpty() ? 0f : textWidth(FONT, 11, amount) + 24f;
        List<String> lines = wrap(description, FONT, 11, right - left - amountW);
        drawTextRight(content, FONT, 11, right, rowY, amount, TEXT_SECONDARY);
        for (String line : lines) {
            drawText(content, FONT, 11, left, rowY, line, TEXT);
            rowY -= 15f;
        }
        float rowBottom = rowY + 15f - 12f;
        hairline(content, left, right, rowBottom);
        return rowBottom;
    }

    private PDImageXObject loadLogo(PDDocument doc) {
        try (InputStream in = LOGO.getInputStream()) {
            return PDImageXObject.createFromByteArray(doc, in.readAllBytes(), "brand-logo");
        } catch (IOException ex) {
            return null;
        }
    }

    private void hairline(PDPageContentStream content, float x1, float x2, float y) throws IOException {
        content.setStrokingColor(SEPARATOR);
        content.setLineWidth(0.75f);
        content.moveTo(x1, y);
        content.lineTo(x2, y);
        content.stroke();
    }

    private void fillRoundRect(PDPageContentStream content, float x, float y, float w, float h,
                               float r, Color color) throws IOException {
        float k = 0.5523f * r;
        content.setNonStrokingColor(color);
        content.moveTo(x + r, y);
        content.lineTo(x + w - r, y);
        content.curveTo(x + w - r + k, y, x + w, y + r - k, x + w, y + r);
        content.lineTo(x + w, y + h - r);
        content.curveTo(x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h);
        content.lineTo(x + r, y + h);
        content.curveTo(x + r - k, y + h, x, y + h - r + k, x, y + h - r);
        content.lineTo(x, y + r);
        content.curveTo(x, y + r - k, x + r - k, y, x + r, y);
        content.closePath();
        content.fill();
    }

    private void drawText(PDPageContentStream content, PDType1Font font, float size,
                          float x, float y, String text, Color color) throws IOException {
        content.setNonStrokingColor(color);
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(pdfSafe(text));
        content.endText();
    }

    private void drawTextRight(PDPageContentStream content, PDType1Font font, float size,
                               float right, float y, String text, Color color) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }
        drawText(content, font, size, right - textWidth(font, size, text), y, text, color);
    }

    private float textWidth(PDType1Font font, float size, String text) throws IOException {
        return font.getStringWidth(pdfSafe(text)) / 1000f * size;
    }

    private List<String> wrap(String text, PDType1Font font, float fontSize, float maxWidth)
            throws IOException {
        String safe = pdfSafe(text);
        List<String> lines = new ArrayList<>();
        if (safe.isBlank()) {
            lines.add("");
            return lines;
        }
        String[] words = safe.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            float width = font.getStringWidth(candidate) / 1000f * fontSize;
            if (width <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
                current.setLength(0);
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return CustomerDateTimeFormatter.format(dateTime);
    }

    /**
     * Keeps characters the WinAnsi-encoded Helvetica can show; others lose their
     * diacritics (e.g. "Łęcki" becomes "Lecki") or fall back to '?'.
     */
    private String pdfSafe(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            if (Character.isWhitespace(ch)) {
                sanitized.append(' ');
            } else if (canEncode(ch)) {
                sanitized.append(ch);
            } else if (ch == 'Ł') {
                sanitized.append('L');
            } else if (ch == 'ł') {
                sanitized.append('l');
            } else {
                String base = Normalizer.normalize(String.valueOf(ch), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "");
                sanitized.append(base.length() == 1 && canEncode(base.charAt(0)) ? base : "?");
            }
        }
        return sanitized.toString();
    }

    private boolean canEncode(char ch) {
        try {
            FONT.encode(String.valueOf(ch));
            return true;
        } catch (IOException | IllegalArgumentException ex) {
            return false;
        }
    }
}
