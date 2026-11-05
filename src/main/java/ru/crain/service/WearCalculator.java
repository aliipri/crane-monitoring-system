package ru.crain.service;

import org.springframework.stereotype.Service;
import ru.crain.model.Metrics;
import ru.crain.model.Upload;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class WearCalculator {

    public Metrics createMetrics(
            Upload upload,
            BigDecimal intensityIndex,
            BigDecimal damageIndex
    ) {
        int wearPercent = clamp(
                (int) Math.round(damageIndex.doubleValue() * 100.0),
                0,
                100
        );
        int remainingResource = Math.max(0, 100 - wearPercent);

        return new Metrics(
                upload,
                intensityIndex,
                damageIndex,
                intensityClass(intensityIndex),
                wearPercent,
                remainingResource,
                statusForResource(remainingResource)
        );
    }

    public String statusForResource(int remainingResource) {
        if (remainingResource < 25) {
            return "Требуется замена троса";
        }
        if (remainingResource < 40) {
            return "Требуется внеплановый контроль";
        }
        return "Контроль в плановом режиме";
    }

    public List<Map<String, Object>> toAnalyticsMetrics(Metrics metrics) {
        if (metrics == null) {
            return List.of();
        }

        return List.of(
                metric(
                        "Индекс интенсивности",
                        metrics.getIntensityIndex().toPlainString(),
                        "",
                        "Оценка эксплуатационной нагрузки",
                        toneForIntensity(metrics)
                ),
                metric(
                        "Индекс повреждения",
                        metrics.getDamageIndex().setScale(3, RoundingMode.HALF_UP).toPlainString(),
                        "",
                        "Накопленное повреждение по правилу Минера",
                        toneForWear(metrics.getWearPercent())
                ),
                metric(
                        "Класс эксплуатации",
                        metrics.getIntensityClass(),
                        "",
                        "Доля тяжёлых режимов работы",
                        toneForIntensity(metrics)
                ),
                metric(
                        "Износ троса",
                        Integer.toString(metrics.getWearPercent()),
                        "%",
                        "Расчёт с учётом осмотра",
                        toneForWear(metrics.getWearPercent())
                ),
                metric(
                        "Остаточный ресурс",
                        Integer.toString(metrics.getRemainingResource()),
                        "%",
                        metrics.getStatus(),
                        toneForResource(metrics.getRemainingResource())
                )
        );
    }

    private Map<String, Object> metric(
            String title,
            String value,
            String unit,
            String description,
            String tone
    ) {
        return Map.of(
                "title", title,
                "value", value,
                "unit", unit,
                "description", description,
                "tone", tone
        );
    }

    private String intensityClass(BigDecimal intensityIndex) {
        if (intensityIndex.compareTo(BigDecimal.valueOf(0.65)) >= 0) {
            return "Тяжёлая";
        }
        if (intensityIndex.compareTo(BigDecimal.valueOf(0.45)) >= 0) {
            return "Средняя";
        }
        return "Лёгкая";
    }

    private String toneForIntensity(Metrics metrics) {
        if ("Тяжёлая".equalsIgnoreCase(metrics.getIntensityClass())) {
            return "danger";
        }
        if ("Средняя".equalsIgnoreCase(metrics.getIntensityClass())) {
            return "warning";
        }
        return "success";
    }

    private String toneForWear(int wearPercent) {
        if (wearPercent >= 60) {
            return "danger";
        }
        if (wearPercent >= 30) {
            return "warning";
        }
        return "success";
    }

    private String toneForResource(int remainingResource) {
        if (remainingResource < 40) {
            return "danger";
        }
        if (remainingResource < 70) {
            return "warning";
        }
        return "success";
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
