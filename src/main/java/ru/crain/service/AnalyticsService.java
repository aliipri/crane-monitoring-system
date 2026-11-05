package ru.crain.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.crain.model.Crane;
import ru.crain.model.Event;
import ru.crain.model.ForecastResult;
import ru.crain.model.LoadProfile;
import ru.crain.model.MechanismWorkload;
import ru.crain.model.Metrics;
import ru.crain.model.Upload;
import ru.crain.model.VisualInspection;
import ru.crain.repository.CraneRepository;
import ru.crain.repository.EventRepository;
import ru.crain.repository.ForecastResultRepository;
import ru.crain.repository.LoadProfileRepository;
import ru.crain.repository.MechanismWorkloadRepository;
import ru.crain.repository.MetricsRepository;
import ru.crain.repository.UploadRepository;
import ru.crain.repository.VisualInspectionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final CraneRepository craneRepository;
    private final UploadRepository uploadRepository;
    private final LoadProfileRepository loadProfileRepository;
    private final EventRepository eventRepository;
    private final MetricsRepository metricsRepository;
    private final ForecastResultRepository forecastResultRepository;
    private final MechanismWorkloadRepository mechanismWorkloadRepository;
    private final VisualInspectionRepository visualInspectionRepository;
    private final MinerDamageCalculator minerDamageCalculator;
    private final WearCalculator wearCalculator;
    private final ChartBuilder chartBuilder;
    private final NeuralInferenceClient neuralInferenceClient;

    public AnalyticsService(
            CraneRepository craneRepository,
            UploadRepository uploadRepository,
            LoadProfileRepository loadProfileRepository,
            EventRepository eventRepository,
            MetricsRepository metricsRepository,
            ForecastResultRepository forecastResultRepository,
            MechanismWorkloadRepository mechanismWorkloadRepository,
            VisualInspectionRepository visualInspectionRepository,
            MinerDamageCalculator minerDamageCalculator,
            WearCalculator wearCalculator,
            ChartBuilder chartBuilder,
            NeuralInferenceClient neuralInferenceClient
    ) {
        this.craneRepository = craneRepository;
        this.uploadRepository = uploadRepository;
        this.loadProfileRepository = loadProfileRepository;
        this.eventRepository = eventRepository;
        this.metricsRepository = metricsRepository;
        this.forecastResultRepository = forecastResultRepository;
        this.mechanismWorkloadRepository = mechanismWorkloadRepository;
        this.visualInspectionRepository = visualInspectionRepository;
        this.minerDamageCalculator = minerDamageCalculator;
        this.wearCalculator = wearCalculator;
        this.chartBuilder = chartBuilder;
        this.neuralInferenceClient = neuralInferenceClient;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCraneList() {
        return craneRepository.findAllWithUploads().stream()
                .map(this::craneListRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard(Long craneId) {
        Crane crane = craneRepository.findById(craneId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Кран с идентификатором " + craneId + " не найден."
                ));
        List<Metrics> metricsHistory = metricsRepository.findByCraneOrderByPeriodEndAsc(crane);
        Metrics latestMetrics = metricsHistory.isEmpty() ? null : metricsHistory.get(metricsHistory.size() - 1);
        List<LoadProfile> profiles = loadProfileRepository.findByCraneOrderByPeriodEndDesc(crane);
        LoadProfile latestProfile = profiles.stream().findFirst().orElse(null);
        List<Upload> uploadHistory = uploadRepository.findByCraneOrderByPeriodEndDesc(crane);
        Upload latestUpload = uploadHistory.stream().findFirst().orElse(null);
        ForecastResult forecast = forecastResultRepository.findLatestByCrane(crane, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);

        Map<Long, LoadProfile> profilesByUpload = profiles.stream()
                .collect(Collectors.toMap(profile -> profile.getUpload().getId(), Function.identity()));
        List<Map<String, Object>> uploads = uploadHistory
                .stream()
                .map(upload -> uploadRow(upload, profilesByUpload.get(upload.getId())))
                .toList();
        List<Map<String, Object>> events = eventRepository
                .findByCraneOrderByEventTimeDesc(crane, PageRequest.of(0, 8))
                .stream()
                .sorted(Comparator.comparing(Event::getEventTime))
                .map(this::eventRow)
                .toList();
        List<Map<String, Object>> workloads = mechanismWorkloadRepository
                .findByCraneOrderByPeriodEndDesc(crane)
                .stream()
                .map(this::workloadRow)
                .toList();
        List<Map<String, Object>> inspections = visualInspectionRepository
                .findByCraneOrderByInspectionDateDesc(crane)
                .stream()
                .map(this::inspectionRow)
                .toList();

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("crane", crane);
        dashboard.put("metrics", wearCalculator.toAnalyticsMetrics(latestMetrics));
        dashboard.put("loadProfile", chartBuilder.toLoadSegments(latestProfile));
        dashboard.put("wearTrend", chartBuilder.toWearTrend(metricsHistory));
        dashboard.put("resourceTrend", chartBuilder.toResourceTrend(metricsHistory));
        dashboard.put("uploads", uploads);
        dashboard.put("events", events);
        dashboard.put("workloads", workloads);
        dashboard.put("inspections", inspections);
        dashboard.put("prediction", predictionSummary(forecast, uploads.size()));
        dashboard.put("uploadCount", uploads.size());
        dashboard.put("latestPeriod", latestUpload == null ? "Нет данных" : periodLabel(latestUpload));
        return dashboard;
    }

    public Metrics calculateMetrics(Upload upload, LoadProfile profile, VisualInspection inspection) {
        BigDecimal intensityIndex = BigDecimal.valueOf(
                        profile.getLoad40To80() * 0.45
                                + profile.getLoad80To100() * 0.85
                                + profile.getLoadGt100() * 1.15
                )
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        List<Metrics> history = metricsRepository.findByCraneOrderByPeriodEndAsc(upload.getCrane());
        BigDecimal previousDamage = history.isEmpty()
                ? BigDecimal.ZERO
                : history.get(history.size() - 1).getDamageIndex();
        BigDecimal damageIndex = minerDamageCalculator.accumulate(
                previousDamage,
                minerDamageCalculator.calculatePeriodDamage(profile, inspection)
        );
        return wearCalculator.createMetrics(upload, intensityIndex, damageIndex);
    }

    private Map<String, Object> uploadRow(Upload upload, LoadProfile profile) {
        return Map.of(
                "fileName", upload.getFileName(),
                "uploadedAt", upload.getUploadedAt(),
                "periodStart", upload.getPeriodStart(),
                "periodEnd", upload.getPeriodEnd(),
                "cycles", profile == null ? 0 : profile.getCyclesTotal(),
                "overloads", profile == null ? 0 : profile.getOverloadCycles(),
                "status", "Обработан"
        );
    }

    private Map<String, Object> craneListRow(Crane crane) {
        Upload latestUpload = uploadRepository.findFirstByCraneOrderByPeriodEndDesc(crane).orElse(null);
        List<Metrics> metricsHistory = metricsRepository.findByCraneOrderByPeriodEndAsc(crane);
        Metrics latestMetrics = metricsHistory.isEmpty() ? null : metricsHistory.get(metricsHistory.size() - 1);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", crane.getId());
        row.put("model", crane.getModel());
        row.put("inventoryNumber", crane.getInventoryNumber());
        row.put("serialNumber", crane.getSerialNumber());
        row.put("safetyDevice", crane.getSafetyDevice());
        row.put("uploadCount", uploadRepository.countByCrane(crane));
        row.put("latestPeriod", latestUpload == null ? "Нет данных" : periodLabel(latestUpload));
        row.put("wearPercent", latestMetrics == null ? null : latestMetrics.getWearPercent());
        row.put("remainingResource", latestMetrics == null ? null : latestMetrics.getRemainingResource());
        row.put("status", latestMetrics == null ? "Аналитика ещё не рассчитана" : latestMetrics.getStatus());
        return row;
    }

    private Map<String, Object> eventRow(Event event) {
        return Map.of(
                "eventTime", event.getEventTime(),
                "eventType", event.getEventType(),
                "description", event.getDescription(),
                "severity", event.getSeverity()
        );
    }

    private Map<String, Object> workloadRow(MechanismWorkload workload) {
        return Map.of(
                "periodStart", workload.getUpload().getPeriodStart(),
                "periodEnd", workload.getUpload().getPeriodEnd(),
                "hoistHours", workload.getHoistHours(),
                "boomAngleHours", workload.getBoomAngleHours(),
                "boomLengthHours", workload.getBoomLengthHours(),
                "rotationHours", workload.getRotationHours()
        );
    }

    private Map<String, Object> inspectionRow(VisualInspection inspection) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("inspectionDate", inspection.getInspectionDate());
        row.put("brokenWiresCount", inspection.getBrokenWiresCount());
        row.put("diameterLossPercent", inspection.getDiameterLossPercent());
        row.put("corrosionScore", inspection.getCorrosionScore());
        row.put("abrasionScore", inspection.getAbrasionScore());
        row.put("deformationScore", inspection.getDeformationScore());
        row.put("lubricationScore", inspection.getLubricationScore());
        row.put("comment", inspection.getComment());
        return row;
    }

    private String periodLabel(Upload upload) {
        String month = upload.getPeriodEnd()
                .getMonth()
                .getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru"));
        return month + " " + upload.getPeriodEnd().getYear();
    }

    private Map<String, Object> predictionSummary(ForecastResult forecast, int periodCount) {
        if (forecast == null) {
            return Map.of(
                    "available", false,
                    "predictedWear", 0,
                    "predictedResource", 0,
                    "predictedStatus", neuralInferenceClient.unavailableReason(periodCount),
                    "modelName", neuralInferenceClient.modelLabel(periodCount),
                    "period", "следующий период"
            );
        }
        return Map.of(
                "available", true,
                "predictedWear", forecast.getPredictedWearPercent(),
                "predictedResource", forecast.getPredictedRemainingResource(),
                "predictedStatus", forecast.getPredictedStatus(),
                "modelName", neuralInferenceClient.modelLabel(periodCount),
                "period", forecast.getForecastDate()
        );
    }
}
