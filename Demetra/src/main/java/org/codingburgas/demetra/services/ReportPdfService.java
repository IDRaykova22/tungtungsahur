package org.codingburgas.demetra.services;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import org.codingburgas.demetra.models.reports.EventReport;
import org.codingburgas.demetra.models.reports.EventReportRow;
import org.codingburgas.demetra.models.reports.ReportGroupRow;
import org.codingburgas.demetra.models.reports.ReportGrouping;
import org.codingburgas.demetra.models.reports.ReportPeriod;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Renders an {@link EventReport} as a printable A4 PDF with the same sections as the web page. */
@Service
public class ReportPdfService {
    private static final Color NAVY = new Color(0x16, 0x2a, 0x43);
    private static final Color NAVY_LIGHT = new Color(0x2d, 0x4a, 0x7a);
    private static final Color GOLD = new Color(167, 154, 14);
    private static final Color CREAM = new Color(0xfd, 0xfb, 0xf7);
    private static final Color MUTED = new Color(0x5c, 0x6e, 0x85);
    private static final Color BORDER = new Color(0xdc, 0xdf, 0xe3);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final Clock clock;
    private final BaseFont regular;
    private final BaseFont bold;

    public ReportPdfService(Clock clock) {
        this.clock = clock;
        // Bundled by openpdf-fonts-extra; embedded so Cyrillic renders on any PDF viewer.
        this.regular = loadFont("liberation/LiberationSans-Regular.ttf");
        this.bold = loadFont("liberation/LiberationSans-Bold.ttf");
    }

    public byte[] render(EventReport report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 44, 56);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        String period = periodLabel(report.getPeriod());
        writer.setPageEvent(new Footer(period));
        document.addTitle("Отчет за събитията " + period);
        document.addAuthor("Demetra");
        document.addCreator("Demetra");
        document.open();

        addHeader(document, report, period);
        addSummary(document, report);
        addGroupChart(document, writer, report);
        if (report.getEvents().isEmpty()) {
            document.add(note("Няма публикувани или приключили събития, създадени през избрания период.", 18));
            addMethodology(document);
        } else {
            addGroupTable(document, report);
            addMethodology(document);
            // Eight columns with full timestamps only fit without wrapping on landscape pages.
            document.setPageSize(PageSize.A4.rotate());
            document.newPage();
            addEventsTable(document, report);
        }

        document.close();
        return out.toByteArray();
    }

    public String fileName(ReportPeriod period) {
        String range = period.getStartDate().equals(period.getEndDate())
                ? period.getStartDate().toString()
                : period.getStartDate() + "_" + period.getEndDate();
        return "demetra-otchet-" + range + ".pdf";
    }

    private void addHeader(Document document, EventReport report, String period) {
        document.add(new Paragraph("DEMETRA", font(bold, 9, GOLD)));
        Paragraph title = new Paragraph("Отчет за събитията", font(bold, 20, NAVY));
        title.setSpacingAfter(4);
        document.add(title);
        long days = report.getPeriod().getDays();
        document.add(new Paragraph("Период: " + period + "  ·  " + days + (days == 1 ? " ден" : " дни")
                + "  ·  групиране " + report.getGrouping().getPhrase(), font(bold, 11, NAVY)));
        Paragraph meta = new Paragraph("Общ отчет за всички организатори, по дата на създаване на събитието. "
                + "Генериран на " + LocalDateTime.now(clock).format(DATE_TIME) + ".", font(regular, 9, MUTED));
        meta.setSpacingBefore(2);
        document.add(meta);
        if (report.isTodayIncluded()) {
            document.add(note("Периодът включва днешния ден, който още не е приключил. Данните отразяват "
                    + "наличните записи към момента на справката.", 2));
        }
    }

    private void addSummary(Document document, EventReport report) {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setSpacingBefore(14);
        table.addCell(summaryCell("Брой събития", String.valueOf(report.getEventCount())));
        table.addCell(summaryCell("Потвърдени записвания", String.valueOf(report.getConfirmedCount())));
        table.addCell(summaryCell("Изчислена сума (USD)", money(report.getEstimatedAmount()) + " $"));
        document.add(table);
    }

    private void addGroupChart(Document document, PdfWriter writer, EventReport report) {
        float width = document.right() - document.left();
        PdfPCell chart = new PdfPCell(Image.getInstance(drawGroupChart(writer, report.getGroupRows(), width, 190)), false);
        document.add(withHeading("Изчислена сума " + report.getGrouping().getPhrase() + " (USD)", chart, 14));
    }

    private PdfTemplate drawGroupChart(PdfWriter writer, List<ReportGroupRow> rows, float width, float height) {
        PdfTemplate t = writer.getDirectContent().createTemplate(width, height);
        float left = 58, right = 6, bottom = 22, top = 16;
        float plotWidth = width - left - right;
        float plotHeight = height - bottom - top;
        BigDecimal max = rows.stream().map(ReportGroupRow::getEstimatedAmount)
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        if (max.signum() == 0) {
            line(t, left, bottom, width - right, bottom, MUTED);
            text(t, regular, 9, MUTED, PdfContentByte.ALIGN_CENTER, "Няма изчислена сума за периода.",
                    left + plotWidth / 2, bottom + plotHeight / 2);
            return t;
        }
        BigDecimal scaleMax = niceCeiling(max);
        for (int i = 0; i <= 4; i++) {
            float y = bottom + plotHeight * i / 4;
            line(t, left, y, width - right, y, i == 0 ? MUTED : BORDER);
            BigDecimal tick = scaleMax.multiply(BigDecimal.valueOf(i)).divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
            text(t, regular, 7, MUTED, PdfContentByte.ALIGN_RIGHT, money(tick), left - 6, y - 2.5f);
        }
        float slot = plotWidth / rows.size();
        float barWidth = Math.min(slot * 0.6f, 40);
        // Long names (categories, organizers) are shortened so that neighbouring labels do not collide.
        int maxChars = Math.max(4, (int) (slot / 4.2f));
        for (int i = 0; i < rows.size(); i++) {
            BigDecimal amount = rows.get(i).getEstimatedAmount();
            float x = left + slot * i + (slot - barWidth) / 2;
            if (amount.signum() > 0) {
                float barHeight = amount.divide(scaleMax, 6, RoundingMode.HALF_UP).floatValue() * plotHeight;
                t.setColorFill(NAVY_LIGHT);
                t.rectangle(x, bottom, barWidth, barHeight);
                t.fill();
                text(t, bold, rows.size() > 6 ? 6 : 7.5f, NAVY, PdfContentByte.ALIGN_CENTER, money(amount),
                        x + barWidth / 2, bottom + barHeight + 3);
            }
            String label = rows.get(i).getLabel();
            text(t, regular, 7, MUTED, PdfContentByte.ALIGN_CENTER,
                    label.length() > maxChars ? label.substring(0, maxChars - 1) + "…" : label,
                    left + slot * i + slot / 2, bottom - 12);
        }
        return t;
    }

    private void addGroupTable(Document document, EventReport report) {
        ReportGrouping grouping = report.getGrouping();
        ReportTable table = new ReportTable("Групиране " + grouping.getPhrase(), new float[]{3, 1.2f, 2, 2, 1.4f}, 1,
                grouping.getLabel(), "Събития", "Потвърдени записвания", "Изчислена сума (USD)", "Дял от сумата");
        for (ReportGroupRow row : report.getGroupRows()) {
            table.row(row.getLabel(), String.valueOf(row.getEventCount()), String.valueOf(row.getConfirmedCount()),
                    money(row.getEstimatedAmount()) + " $", share(row.getEstimatedAmount(), report.getEstimatedAmount()));
        }
        document.add(table.finish("Общо", String.valueOf(report.getEventCount()),
                String.valueOf(report.getConfirmedCount()), money(report.getEstimatedAmount()) + " $",
                report.getEstimatedAmount().signum() == 0 ? "—" : "100,0 %"));
    }

    private void addEventsTable(Document document, EventReport report) {
        ReportTable table = new ReportTable("Включени събития",
                new float[]{3.4f, 1.8f, 1.3f, 1.5f, 1.5f, 1.2f, 1.1f, 1.5f}, 5,
                "Събитие", "Категория", "Статус", "Създадено на", "Провеждане", "Цена (USD)", "Потвърдени",
                "Изчислена сума (USD)");
        for (EventReportRow row : report.getEvents()) {
            table.row(row.getTitle(), row.getCategory(), row.getStatusLabel(), row.getCreatedAt().format(DATE_TIME),
                    row.getStartsAt() != null ? row.getStartsAt().format(DATE_TIME) : "Не е определено",
                    money(row.getPrice()) + " $", String.valueOf(row.getConfirmedCount()),
                    money(row.getEstimatedAmount()) + " $");
        }
        document.add(table.finish("Общо", "", "", "", "", "", String.valueOf(report.getConfirmedCount()),
                money(report.getEstimatedAmount()) + " $"));
    }

    private void addMethodology(Document document) {
        PdfPCell text = new PdfPCell();
        // addElement (not the constructor) keeps the paragraph's own line spacing.
        text.addElement(note("Сумата е цената на всяко събитие × текущия брой потвърдени записвания. "
                + "Няма регистрирани реални плащания. Изчакващи и отказани записвания не участват. "
                + "Включват се публикувани и приключили събития; чернови и отменени събития се изключват. "
                + "Валутата USD следва съществуващите цени в приложението.", 0));
        document.add(withHeading("Как е изчислена сумата", text, 18));
    }

    /** Heading and content share one unsplittable table so the heading never ends a page alone. */
    private PdfPTable withHeading(String heading, PdfPCell content, float spacingBefore) {
        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);
        wrapper.setKeepTogether(true);
        wrapper.setSpacingBefore(spacingBefore);
        wrapper.addCell(headingCell(heading, 1));
        content.setBorder(Rectangle.NO_BORDER);
        content.setPadding(0);
        wrapper.addCell(content);
        return wrapper;
    }

    private PdfPCell headingCell(String text, int colspan) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font(bold, 13, NAVY)));
        cell.setColspan(colspan);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        cell.setPaddingBottom(9);
        return cell;
    }

    /**
     * A styled table whose heading and column titles repeat on every page. Rows are buffered so that the last
     * data row and the totals row are written as one unbreakable block: the totals never sit alone on a page.
     */
    private final class ReportTable {
        private final float[] widths;
        private final int firstNumericColumn;
        private final PdfPTable table;
        private final List<PdfPCell[]> rows = new ArrayList<>();

        private ReportTable(String heading, float[] widths, int firstNumericColumn, String... headers) {
            this.widths = widths;
            this.firstNumericColumn = firstNumericColumn;
            this.table = new PdfPTable(widths);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            table.setHeaderRows(2);
            table.addCell(headingCell(heading, widths.length));
            for (int i = 0; i < headers.length; i++) {
                PdfPCell cell = new PdfPCell(new Paragraph(headers[i], font(bold, 8, CREAM)));
                cell.setHorizontalAlignment(align(i));
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setBackgroundColor(NAVY);
                cell.setBorderColor(NAVY);
                cell.setPadding(6);
                table.addCell(cell);
            }
        }

        private void row(String... values) {
            Color background = rows.size() % 2 == 1 ? CREAM : Color.WHITE;
            PdfPCell[] cells = new PdfPCell[values.length];
            for (int i = 0; i < values.length; i++) {
                cells[i] = cell(values[i], align(i), background);
            }
            rows.add(cells);
        }

        private PdfPTable finish(String... totals) {
            for (int i = 0; i < rows.size() - 1; i++) {
                for (PdfPCell cell : rows.get(i)) {
                    table.addCell(cell);
                }
            }
            PdfPTable tail = new PdfPTable(widths);
            tail.setWidthPercentage(100);
            if (!rows.isEmpty()) {
                for (PdfPCell cell : rows.get(rows.size() - 1)) {
                    tail.addCell(cell);
                }
            }
            for (int i = 0; i < totals.length; i++) {
                tail.addCell(totalCell(totals[i], align(i)));
            }
            PdfPCell holder = new PdfPCell(tail);
            holder.setColspan(widths.length);
            holder.setBorder(Rectangle.NO_BORDER);
            holder.setPadding(0);
            table.addCell(holder);
            return table;
        }

        private int align(int column) {
            return column >= firstNumericColumn ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
        }
    }

    private PdfPCell cell(String text, int align, Color background) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font(regular, 8, NAVY)));
        cell.setHorizontalAlignment(align);
        cell.setBackgroundColor(background);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER);
        cell.setPadding(5);
        cell.setPaddingBottom(6);
        return cell;
    }

    private PdfPCell totalCell(String text, int align) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font(bold, 8, NAVY)));
        cell.setHorizontalAlignment(align);
        cell.setBorder(Rectangle.TOP);
        cell.setBorderColorTop(NAVY);
        cell.setBorderWidthTop(1);
        cell.setPadding(5);
        cell.setPaddingBottom(6);
        return cell;
    }

    private PdfPCell summaryCell(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(CREAM);
        cell.setBorderColor(BORDER);
        cell.setPadding(10);
        cell.setPaddingTop(6);
        cell.addElement(new Paragraph(label, font(regular, 9, MUTED)));
        Paragraph number = new Paragraph(value, font(bold, 17, NAVY));
        number.setSpacingBefore(2);
        cell.addElement(number);
        return cell;
    }

    private Paragraph note(String text, float spacingBefore) {
        Paragraph note = new Paragraph(text, font(regular, 9, MUTED));
        note.setSpacingBefore(spacingBefore);
        note.setLeading(12);
        return note;
    }

    private Font font(BaseFont base, float size, Color color) {
        return new Font(base, size, Font.NORMAL, color);
    }

    private void line(PdfContentByte canvas, float x1, float y1, float x2, float y2, Color color) {
        canvas.setColorStroke(color);
        canvas.setLineWidth(0.5f);
        canvas.moveTo(x1, y1);
        canvas.lineTo(x2, y2);
        canvas.stroke();
    }

    private void text(PdfContentByte canvas, BaseFont font, float size, Color color, int align,
                      String text, float x, float y) {
        canvas.beginText();
        canvas.setFontAndSize(font, size);
        canvas.setColorFill(color);
        canvas.showTextAligned(align, text, x, y, 0);
        canvas.endText();
    }

    /** Rounds up to 1, 2, 2.5 or 5 × 10ⁿ so the four grid steps get readable labels. */
    static BigDecimal niceCeiling(BigDecimal value) {
        BigDecimal magnitude = BigDecimal.TEN.pow(value.precision() - value.scale() - 1, MathContext.DECIMAL64);
        for (String step : List.of("1", "2", "2.5", "5", "10")) {
            BigDecimal candidate = magnitude.multiply(new BigDecimal(step));
            if (candidate.compareTo(value) >= 0) {
                return candidate;
            }
        }
        return magnitude.multiply(BigDecimal.TEN);
    }

    private static String periodLabel(ReportPeriod period) {
        return period.getStartDate().format(DATE) + " – " + period.getEndDate().format(DATE);
    }

    /** Same format as the web page: "1 234,56". */
    private static String money(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", symbols).format(amount);
    }

    private static String share(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return "—";
        }
        BigDecimal percent = part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
        return percent.toPlainString().replace('.', ',') + " %";
    }

    private static BaseFont loadFont(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException ex) {
            throw new UncheckedIOException("Шрифтът " + path + " липсва в classpath.", ex);
        }
    }

    private final class Footer extends PdfPageEventHelper {
        private final String period;

        private Footer(String period) {
            this.period = period;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            float y = document.bottom() - 22;
            line(canvas, document.left(), y + 12, document.right(), y + 12, BORDER);
            text(canvas, regular, 8, MUTED, PdfContentByte.ALIGN_LEFT, "Demetra · Отчет за " + period,
                    document.left(), y);
            text(canvas, regular, 8, MUTED, PdfContentByte.ALIGN_RIGHT, "Страница " + writer.getPageNumber(),
                    document.right(), y);
        }
    }
}
