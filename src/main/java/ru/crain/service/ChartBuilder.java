package ru.crain.service;

import org.springframework.stereotype.Service;
import ru.crain.model.LoadProfile;
import ru.crain.model.Metrics;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class ChartBuilder {

    private static final int TREND_PERIOD_COUNT = 5;

    private static final String[] MONTH_LABELS = {
            "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
            "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"
    };

    public List<Map<String, Object>> toLoadSegments(LoadProfile profile) {
        if (profile == null) {
            return List.of();
        }

        return List.of(
                segment("до 40 %", "лёгкий режим", profile.getLoadLt40(), "success"),
                segment("40-80 %", "средний режим", profile.getLoad40To80(), "info"),
                segment("80-100 %", "тяжёлый режим", profile.getLoad80To100(), "warning"),
                segment("более 100 %", "перегрузочный режим", profile.getLoadGt100(), "danger")
        );
    }

    public List<Map<String, Object>> toWearTrend(List<Metrics> metricsHistory) {
        return latestPeriods(metricsHistory).stream()
                .map(metrics -> point(label(metrics), metrics.getWearPercent()))
                .toList();
    }

    public List<Map<String, Object>> toResourceTrend(List<Metrics> metricsHistory) {
        return latestPeriods(metricsHistory).stream()
                .map(metrics -> point(label(metrics), metrics.getRemainingResource()))
                .toList();
    }

    private List<Metrics> latestPeriods(List<Metrics> metricsHistory) {
        int fromIndex = Math.max(0, metricsHistory.size() - TREND_PERIOD_COUNT);
        return metricsHistory.subList(fromIndex, metricsHistory.size());
    }

    private Map<String, Object> segment(String range, String interpretation, int percent, String tone) {
        return Map.of(
                "range", range,
                "interpretation", interpretation,
                "percent", percent,
                "tone", tone
        );
    }

    private Map<String, Object> point(String label, int value) {
        return Map.of("label", label, "value", value);
    }

    private String label(Metrics metrics) {
        LocalDate periodEnd = metrics.getUpload().getPeriodEnd();
        return MONTH_LABELS[periodEnd.getMonthValue() - 1];
    }
}
