package ru.crain.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DataPreprocessor {

    private static final Pattern NUMBERED_SECTION_PATTERN =
            Pattern.compile("^(VISUAL_INSPECTION|EVENT)_\\d+$");
    private static final Pattern DURATION_PATTERN =
            Pattern.compile("^(\\d+):([0-5]\\d):([0-5]\\d)$");
    private static final DateTimeFormatter EVENT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ProcessedTxtData preprocess(MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<String> lines = content.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();

        Map<String, Map<String, String>> sections = parseSections(lines);
        if (!sections.containsKey("CRANE")) {
            throw new IllegalArgumentException("В TXT-файле отсутствует обязательная секция [CRANE].");
        }

        return preprocessStructured(file.getOriginalFilename(), sections);
    }

    private ProcessedTxtData preprocessStructured(
            String fileName,
            Map<String, Map<String, String>> sections
    ) {
        Map<String, String> craneSection = requiredSection(sections, "CRANE");
        Map<String, String> periodSection = requiredSection(sections, "PERIOD");
        Map<String, String> loadSection = requiredSection(sections, "LOAD_PROFILE");
        Map<String, String> workloadSection = requiredSection(sections, "MECHANISM_WORKLOAD");

        CraneData crane = new CraneData(
                requiredValue(craneSection, "model", "CRANE"),
                requiredValue(craneSection, "inventory_no", "CRANE"),
                requiredValue(craneSection, "serial_no", "CRANE"),
                requiredValue(craneSection, "safety_device", "CRANE"),
                dateValue(craneSection, "commissioning_date", "CRANE"),
                dateValue(craneSection, "rope_installation_date", "CRANE"),
                craneSection.getOrDefault("normative_resource", "100 % расчётного ресурса")
        );

        LocalDate periodStart = dateValue(periodSection, "period_start", "PERIOD");
        LocalDate periodEnd = dateValue(periodSection, "period_end", "PERIOD");
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("Дата окончания периода раньше даты начала.");
        }

        int cycles = intValue(loadSection, "cycles_total", "LOAD_PROFILE");
        int loadLt40Cycles = intValue(loadSection, "load_lt_40_cycles", "LOAD_PROFILE");
        int load40To80Cycles = intValue(loadSection, "load_40_80_cycles", "LOAD_PROFILE");
        int load80To100Cycles = intValue(loadSection, "load_80_100_cycles", "LOAD_PROFILE");
        int loadGt100Cycles = intValue(loadSection, "load_gt_100_cycles", "LOAD_PROFILE");
        int[] loadPercentages = toPercentages(
                cycles,
                loadLt40Cycles,
                load40To80Cycles,
                load80To100Cycles,
                loadGt100Cycles
        );

        List<ProcessedInspection> inspections = sections.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("VISUAL_INSPECTION_"))
                .map(entry -> inspection(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ProcessedInspection::inspectionDate))
                .toList();
        List<ProcessedEvent> events = sections.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("EVENT_"))
                .map(entry -> event(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ProcessedEvent::eventTime))
                .toList();

        return new ProcessedTxtData(
                crane,
                periodStart,
                periodEnd,
                cycles,
                intValue(loadSection, "overload_cycles", "LOAD_PROFILE"),
                loadPercentages[0],
                loadPercentages[1],
                loadPercentages[2],
                loadPercentages[3],
                durationHours(workloadSection, "hoist_time"),
                durationHours(workloadSection, "boom_angle_time"),
                durationHours(workloadSection, "boom_length_time"),
                durationHours(workloadSection, "rotation_time"),
                inspections,
                events
        );
    }

    private Map<String, Map<String, String>> parseSections(List<String> lines) {
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        Map<String, String> currentSection = null;

        for (String line : lines) {
            if (line.startsWith("[") && line.endsWith("]")) {
                String sectionName = line.substring(1, line.length() - 1).trim().toUpperCase(Locale.ROOT);
                if (!isSupportedSection(sectionName)) {
                    currentSection = null;
                    continue;
                }
                currentSection = sections.computeIfAbsent(sectionName, ignored -> new LinkedHashMap<>());
                continue;
            }

            if (currentSection == null || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }

            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            currentSection.put(key, value);
        }
        return sections;
    }

    private boolean isSupportedSection(String sectionName) {
        return "CRANE".equals(sectionName)
                || "PERIOD".equals(sectionName)
                || "LOAD_PROFILE".equals(sectionName)
                || "MECHANISM_WORKLOAD".equals(sectionName)
                || NUMBERED_SECTION_PATTERN.matcher(sectionName).matches();
    }

    private ProcessedInspection inspection(String sectionName, Map<String, String> values) {
        return new ProcessedInspection(
                dateValue(values, "inspection_date", sectionName),
                intValue(values, "broken_wires_count", sectionName),
                decimalValue(values, "diameter_loss_percent", sectionName),
                intValue(values, "corrosion_score", sectionName),
                intValue(values, "abrasion_score", sectionName),
                intValue(values, "deformation_score", sectionName),
                intValue(values, "lubrication_score", sectionName),
                values.get("comment")
        );
    }

    private ProcessedEvent event(String sectionName, Map<String, String> values) {
        String rawType = requiredValue(values, "event_type", sectionName);
        String rawSeverity = requiredValue(values, "severity", sectionName);
        return new ProcessedEvent(
                dateTimeValue(values, "event_time", sectionName),
                normalizeEventType(rawType),
                requiredValue(values, "description", sectionName),
                normalizeSeverity(rawSeverity)
        );
    }

    private int[] toPercentages(int total, int... counts) {
        if (total <= 0) {
            throw new IllegalArgumentException("cycles_total должен быть больше нуля.");
        }

        int sum = 0;
        for (int count : counts) {
            if (count < 0) {
                throw new IllegalArgumentException("Количество циклов не может быть отрицательным.");
            }
            sum += count;
        }
        if (sum != total) {
            throw new IllegalArgumentException("Сумма циклов по диапазонам должна совпадать с cycles_total.");
        }

        int[] percentages = new int[counts.length];
        int assigned = 0;
        for (int index = 0; index < counts.length - 1; index++) {
            percentages[index] = (int) Math.round(counts[index] * 100.0 / total);
            assigned += percentages[index];
        }
        percentages[counts.length - 1] = Math.max(0, 100 - assigned);
        return percentages;
    }

    private BigDecimal durationHours(Map<String, String> values, String key) {
        String rawValue = requiredValue(values, key, "MECHANISM_WORKLOAD");
        java.util.regex.Matcher matcher = DURATION_PATTERN.matcher(rawValue);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Некорректное время " + key + ": " + rawValue);
        }
        long hours = Long.parseLong(matcher.group(1));
        long minutes = Long.parseLong(matcher.group(2));
        long seconds = Long.parseLong(matcher.group(3));
        long totalSeconds = hours * 3600 + minutes * 60 + seconds;
        return BigDecimal.valueOf(totalSeconds)
                .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }

    private Map<String, String> requiredSection(
            Map<String, Map<String, String>> sections,
            String sectionName
    ) {
        Map<String, String> section = sections.get(sectionName);
        if (section == null) {
            throw new IllegalArgumentException("В TXT-файле отсутствует секция [" + sectionName + "].");
        }
        return section;
    }

    private String requiredValue(Map<String, String> values, String key, String sectionName) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "В секции [" + sectionName + "] отсутствует поле " + key + "."
            );
        }
        return value;
    }

    private int intValue(Map<String, String> values, String key, String sectionName) {
        String rawValue = requiredValue(values, key, sectionName);
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Некорректное целое значение " + key + ": " + rawValue);
        }
    }

    private BigDecimal decimalValue(Map<String, String> values, String key, String sectionName) {
        String rawValue = requiredValue(values, key, sectionName).replace(',', '.');
        try {
            return new BigDecimal(rawValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Некорректное число " + key + ": " + rawValue);
        }
    }

    private LocalDate dateValue(Map<String, String> values, String key, String sectionName) {
        String rawValue = requiredValue(values, key, sectionName);
        try {
            return LocalDate.parse(rawValue);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Некорректная дата " + key + ": " + rawValue);
        }
    }

    private LocalDateTime dateTimeValue(Map<String, String> values, String key, String sectionName) {
        String rawValue = requiredValue(values, key, sectionName);
        try {
            return LocalDateTime.parse(rawValue, EVENT_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Некорректные дата и время " + key + ": " + rawValue);
        }
    }

    private String normalizeEventType(String eventType) {
        if ("OVERLOAD".equalsIgnoreCase(eventType)) {
            return "Перегрузка";
        }
        return eventType;
    }

    private String normalizeSeverity(String severity) {
        if ("HIGH".equalsIgnoreCase(severity) || "CRITICAL".equalsIgnoreCase(severity)) {
            return "Высокая";
        }
        if ("WARNING".equalsIgnoreCase(severity) || "MEDIUM".equalsIgnoreCase(severity)) {
            return "Средняя";
        }
        return severity;
    }

    public record CraneData(
            String model,
            String inventoryNumber,
            String serialNumber,
            String safetyDevice,
            LocalDate commissioningDate,
            LocalDate ropeInstallationDate,
            String normativeResource
    ) {
    }

    public record ProcessedInspection(
            LocalDate inspectionDate,
            int brokenWiresCount,
            BigDecimal diameterLossPercent,
            int corrosionScore,
            int abrasionScore,
            int deformationScore,
            int lubricationScore,
            String comment
    ) {
    }

    public record ProcessedTxtData(
            CraneData crane,
            LocalDate periodStart,
            LocalDate periodEnd,
            int cycles,
            int overloads,
            int loadLt40,
            int load40To80,
            int load80To100,
            int loadGt100,
            BigDecimal hoistHours,
            BigDecimal boomAngleHours,
            BigDecimal boomLengthHours,
            BigDecimal rotationHours,
            List<ProcessedInspection> inspections,
            List<ProcessedEvent> events
    ) {
    }

    public record ProcessedEvent(
            LocalDateTime eventTime,
            String eventType,
            String description,
            String severity
    ) {
    }
}
