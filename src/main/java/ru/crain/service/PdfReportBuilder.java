package ru.crain.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.crain.model.Crane;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PdfReportBuilder {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final String configuredFontPath;
    private final String configuredBoldFontPath;

    public PdfReportBuilder(
            @Value("${crane.report.font-path:}") String configuredFontPath,
            @Value("${crane.report.bold-font-path:}") String configuredBoldFontPath
    ) {
        this.configuredFontPath = configuredFontPath;
        this.configuredBoldFontPath = configuredBoldFontPath;
    }

    public byte[] build(Map<String, Object> dashboard, LocalDateTime generatedAt) {
        Crane crane = (Crane) dashboard.get("crane");
        Path regularFontPath = resolveRegularFont();
        Path boldFontPath = resolveBoldFont(regularFontPath);

        try (PDDocument document = new PDDocument()) {
            PDFont regularFont = PDType0Font.load(document, regularFontPath.toFile());
            PDFont boldFont = PDType0Font.load(document, boldFontPath.toFile());
            document.getDocumentInformation().setTitle("Отчёт по крану " + crane.getModel());
            document.getDocumentInformation().setAuthor("Crane Monitor");

            try (PdfCanvas canvas = new PdfCanvas(document, regularFont, boldFont)) {
                writeReport(canvas, dashboard, crane, generatedAt);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сформировать PDF-отчёт.", exception);
        }
    }

    private void writeReport(
            PdfCanvas canvas,
            Map<String, Object> dashboard,
            Crane crane,
            LocalDateTime generatedAt
    ) throws IOException {
        canvas.title("Отчёт по техническому состоянию крана");
        canvas.muted("Сформирован " + DATE_TIME_FORMAT.format(generatedAt));

        canvas.section("Сведения о кране");
        canvas.field("Модель", crane.getModel());
        canvas.field("Инвентарный номер", crane.getInventoryNumber());
        canvas.field("Заводской номер", crane.getSerialNumber());
        canvas.field("Прибор безопасности", crane.getSafetyDevice());
        canvas.field("Ввод в эксплуатацию", formatDate(crane.getCommissioningDate()));
        canvas.field("Установка троса", formatDate(crane.getRopeInstallationDate()));
        canvas.field("Нормативный ресурс", crane.getNormativeResource());
        canvas.field("Последний период", value(dashboard, "latestPeriod"));
        canvas.field("Загружено файлов", value(dashboard, "uploadCount"));

        canvas.section("Расчётные показатели");
        List<Map<String, Object>> metrics = rows(dashboard.get("metrics"));
        if (metrics.isEmpty()) {
            canvas.paragraph("Расчётные показатели отсутствуют.");
        } else {
            for (Map<String, Object> metric : metrics) {
                canvas.field(
                        value(metric, "title"),
                        value(metric, "value") + value(metric, "unit")
                                + " - " + value(metric, "description")
                );
            }
        }

        canvas.section("Нагрузочный профиль");
        List<Map<String, Object>> loadProfile = rows(dashboard.get("loadProfile"));
        if (loadProfile.isEmpty()) {
            canvas.paragraph("Данные нагрузочного профиля отсутствуют.");
        } else {
            for (Map<String, Object> segment : loadProfile) {
                canvas.bar(
                        value(segment, "range") + " (" + value(segment, "interpretation") + ")",
                        number(segment.get("percent"))
                );
            }
        }

        canvas.section("Интеллектуальный прогноз");
        Map<String, Object> prediction = row(dashboard.get("prediction"));
        canvas.field("Модель", value(prediction, "modelName"));
        if (Boolean.TRUE.equals(prediction.get("available"))) {
            canvas.field("Прогноз износа", value(prediction, "predictedWear") + "%");
            canvas.field("Прогноз остаточного ресурса", value(prediction, "predictedResource") + "%");
            canvas.field("Период прогноза", value(prediction, "period"));
        }
        canvas.field("Статус", value(prediction, "predictedStatus"));

        writeTrend(canvas, "Динамика износа", rows(dashboard.get("wearTrend")));
        writeTrend(canvas, "Динамика остаточного ресурса", rows(dashboard.get("resourceTrend")));

        canvas.section("Загруженные TXT-файлы");
        List<Map<String, Object>> uploads = rows(dashboard.get("uploads"));
        if (uploads.isEmpty()) {
            canvas.paragraph("Загруженные файлы отсутствуют.");
        } else {
            int index = 1;
            for (Map<String, Object> upload : uploads) {
                canvas.listItem(index++ + ". " + value(upload, "fileName")
                        + "; период " + period(upload)
                        + "; циклы: " + value(upload, "cycles")
                        + "; перегрузки: " + value(upload, "overloads"));
            }
        }

        canvas.section("Наработка механизмов");
        List<Map<String, Object>> workloads = rows(dashboard.get("workloads"));
        if (workloads.isEmpty()) {
            canvas.paragraph("Данные наработки отсутствуют.");
        } else {
            for (Map<String, Object> workload : workloads) {
                canvas.listItem(period(workload)
                        + "; лебёдка: " + value(workload, "hoistHours") + " ч"
                        + "; угол стрелы: " + value(workload, "boomAngleHours") + " ч"
                        + "; длина стрелы: " + value(workload, "boomLengthHours") + " ч"
                        + "; поворот: " + value(workload, "rotationHours") + " ч");
            }
        }

        canvas.section("События");
        List<Map<String, Object>> events = rows(dashboard.get("events"));
        if (events.isEmpty()) {
            canvas.paragraph("События за анализируемые периоды отсутствуют.");
        } else {
            for (Map<String, Object> event : events) {
                canvas.listItem(formatDateTime(event.get("eventTime"))
                        + "; " + value(event, "eventType")
                        + "; " + value(event, "description")
                        + "; важность: " + value(event, "severity"));
            }
        }

        canvas.section("Визуальные осмотры троса");
        List<Map<String, Object>> inspections = rows(dashboard.get("inspections"));
        if (inspections.isEmpty()) {
            canvas.paragraph("Данные визуальных осмотров отсутствуют.");
        } else {
            for (Map<String, Object> inspection : inspections) {
                canvas.listItem(formatDate(inspection.get("inspectionDate"))
                        + "; обрывы проволок: " + value(inspection, "brokenWiresCount")
                        + "; потеря диаметра: " + value(inspection, "diameterLossPercent") + "%"
                        + "; коррозия/истирание/деформация/смазка: "
                        + value(inspection, "corrosionScore") + "/"
                        + value(inspection, "abrasionScore") + "/"
                        + value(inspection, "deformationScore") + "/"
                        + value(inspection, "lubricationScore"));
            }
        }

        canvas.section("Заключение");
        canvas.paragraph(
                "Документ сформирован автоматически по данным TXT-файлов, загруженных пользователем. "
                        + "Результаты расчёта и нейросетевой прогноз используются как вспомогательная "
                        + "информация для инженерной оценки."
        );
    }

    private void writeTrend(PdfCanvas canvas, String title, List<Map<String, Object>> trend) throws IOException {
        canvas.section(title);
        if (trend.isEmpty()) {
            canvas.paragraph("Данные динамики отсутствуют.");
            return;
        }
        for (Map<String, Object> point : trend) {
            canvas.bar(value(point, "label"), number(point.get("value")));
        }
    }

    private Path resolveRegularFont() {
        return resolveFont(
                configuredFontPath,
                List.of(
                        "C:/Windows/Fonts/arial.ttf",
                        "C:/Windows/Fonts/calibri.ttf",
                        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                        "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
                        "/System/Library/Fonts/Supplemental/Arial.ttf"
                )
        );
    }

    private Path resolveBoldFont(Path regularFontPath) {
        if (!configuredBoldFontPath.isBlank()) {
            Path configured = Path.of(configuredBoldFontPath);
            if (Files.isRegularFile(configured)) {
                return configured;
            }
        }
        for (String candidate : List.of(
                "C:/Windows/Fonts/arialbd.ttf",
                "C:/Windows/Fonts/calibrib.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf",
                "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
        )) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return regularFontPath;
    }

    private Path resolveFont(String configuredPath, List<String> candidates) {
        if (!configuredPath.isBlank()) {
            Path configured = Path.of(configuredPath);
            if (Files.isRegularFile(configured)) {
                return configured;
            }
        }
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        throw new IllegalStateException(
                "Не найден шрифт для PDF. Укажите путь в CRANE_REPORT_FONT."
        );
    }

    private List<Map<String, Object>> rows(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(toStringKeyMap(map));
            }
        }
        return result;
    }

    private Map<String, Object> row(Object value) {
        if (value instanceof Map<?, ?> map) {
            return toStringKeyMap(map);
        }
        return Map.of();
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private String period(Map<String, Object> row) {
        return formatDate(row.get("periodStart")) + " - " + formatDate(row.get("periodEnd"));
    }

    private String value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "нет данных" : value.toString();
    }

    private int number(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, Math.min(100, number.intValue()));
        }
        return 0;
    }

    private String formatDate(Object value) {
        if (value instanceof LocalDate date) {
            return DATE_FORMAT.format(date);
        }
        return value == null ? "нет данных" : value.toString();
    }

    private String formatDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return DATE_TIME_FORMAT.format(dateTime);
        }
        return value == null ? "нет данных" : value.toString();
    }

    private static final class PdfCanvas implements AutoCloseable {

        private static final float MARGIN = 48;
        private static final float CONTENT_WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;

        private final PDDocument document;
        private final PDFont regularFont;
        private final PDFont boldFont;

        private PDPageContentStream stream;
        private float y;

        private PdfCanvas(PDDocument document, PDFont regularFont, PDFont boldFont) throws IOException {
            this.document = document;
            this.regularFont = regularFont;
            this.boldFont = boldFont;
            addPage();
        }

        private void title(String text) throws IOException {
            ensureSpace(50);
            text(text, boldFont, 18, 23, 0, 45, 86, 104);
            rule();
        }

        private void muted(String text) throws IOException {
            text(text, regularFont, 9, 13, 0, 100, 116, 124);
            y -= 5;
        }

        private void section(String text) throws IOException {
            ensureSpace(38);
            y -= 10;
            text(text, boldFont, 13, 18, 0, 28, 70, 82);
            rule();
        }

        private void field(String label, String value) throws IOException {
            paragraph(label + ": " + value);
        }

        private void paragraph(String text) throws IOException {
            text(text, regularFont, 10, 15, 0, 34, 45, 50);
            y -= 2;
        }

        private void listItem(String text) throws IOException {
            text("- " + text, regularFont, 9, 14, 10, 34, 45, 50);
            y -= 2;
        }

        private void bar(String label, int percent) throws IOException {
            ensureSpace(34);
            text(label + " - " + percent + "%", regularFont, 9, 13, 0, 34, 45, 50);
            ensureSpace(14);
            stream.setNonStrokingColor(new Color(224, 231, 234));
            stream.addRect(MARGIN, y - 7, 330, 7);
            stream.fill();
            stream.setNonStrokingColor(new Color(38, 132, 128));
            stream.addRect(MARGIN, y - 7, 330 * percent / 100f, 7);
            stream.fill();
            y -= 15;
        }

        private void text(
                String text,
                PDFont font,
                float fontSize,
                float leading,
                float indent,
                int red,
                int green,
                int blue
        ) throws IOException {
            for (String line : wrap(text, font, fontSize, CONTENT_WIDTH - indent)) {
                ensureSpace(leading);
                stream.setNonStrokingColor(new Color(red, green, blue));
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(MARGIN + indent, y);
                stream.showText(line);
                stream.endText();
                y -= leading;
            }
        }

        private List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
            String normalized = text == null ? "" : text.replaceAll("[\\r\\n\\t]+", " ").trim();
            if (normalized.isEmpty()) {
                return List.of("");
            }

            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : normalized.split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (width(candidate, font, fontSize) <= maxWidth || current.isEmpty()) {
                    current.setLength(0);
                    current.append(candidate);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines;
        }

        private float width(String text, PDFont font, float fontSize) throws IOException {
            return font.getStringWidth(text) / 1000f * fontSize;
        }

        private void rule() throws IOException {
            ensureSpace(8);
            stream.setStrokingColor(new Color(205, 216, 220));
            stream.moveTo(MARGIN, y);
            stream.lineTo(PDRectangle.A4.getWidth() - MARGIN, y);
            stream.stroke();
            y -= 8;
        }

        private void ensureSpace(float height) throws IOException {
            if (y - height < MARGIN) {
                addPage();
            }
        }

        private void addPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
