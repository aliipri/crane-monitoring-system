package ru.crain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.crain.model.Crane;
import ru.crain.model.ForecastResult;
import ru.crain.model.LoadProfile;
import ru.crain.model.MechanismWorkload;
import ru.crain.model.Upload;
import ru.crain.model.VisualInspection;
import ru.crain.repository.LoadProfileRepository;
import ru.crain.repository.MechanismWorkloadRepository;
import ru.crain.repository.VisualInspectionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PredictionModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(PredictionModel.class);

    private final LoadProfileRepository loadProfileRepository;
    private final MechanismWorkloadRepository mechanismWorkloadRepository;
    private final VisualInspectionRepository visualInspectionRepository;
    private final NeuralInferenceClient neuralInferenceClient;
    private final MinerDamageCalculator minerDamageCalculator;

    public PredictionModel(
            LoadProfileRepository loadProfileRepository,
            MechanismWorkloadRepository mechanismWorkloadRepository,
            VisualInspectionRepository visualInspectionRepository,
            NeuralInferenceClient neuralInferenceClient,
            MinerDamageCalculator minerDamageCalculator
    ) {
        this.loadProfileRepository = loadProfileRepository;
        this.mechanismWorkloadRepository = mechanismWorkloadRepository;
        this.visualInspectionRepository = visualInspectionRepository;
        this.neuralInferenceClient = neuralInferenceClient;
        this.minerDamageCalculator = minerDamageCalculator;
    }

    public Optional<ForecastResult> createForecast(Upload upload) {
        if (!neuralInferenceClient.isAvailable()) {
            return Optional.empty();
        }

        Crane crane = upload.getCrane();
        List<LoadProfile> profiles = loadProfileRepository.findByCraneOrderByPeriodEndDesc(crane)
                .stream()
                .sorted(Comparator.comparing(profile -> profile.getUpload().getPeriodEnd()))
                .toList();
        if (profiles.size() < NeuralInferenceClient.WINDOW_SIZE) {
            return Optional.empty();
        }

        List<LoadProfile> window = profiles.subList(
                profiles.size() - NeuralInferenceClient.WINDOW_SIZE,
                profiles.size()
        );
        Map<Long, MechanismWorkload> workloads = mechanismWorkloadRepository
                .findByCraneOrderByPeriodEndDesc(crane)
                .stream()
                .collect(Collectors.toMap(
                        workload -> workload.getUpload().getId(),
                        Function.identity()
                ));
        List<VisualInspection> inspections = visualInspectionRepository
                .findByCraneOrderByInspectionDateDesc(crane)
                .stream()
                .sorted(Comparator.comparing(VisualInspection::getInspectionDate))
                .toList();

        List<Map<String, Double>> history = new ArrayList<>();
        for (LoadProfile profile : window) {
            MechanismWorkload workload = workloads.get(profile.getUpload().getId());
            if (workload == null) {
                LOGGER.warn(
                        "CNN-LSTM skipped: no mechanism workload for upload {}",
                        profile.getUpload().getId()
                );
                return Optional.empty();
            }
            history.add(featureRow(
                    profile,
                    workload,
                    latestInspection(inspections, profile.getUpload())
            ));
        }

        try {
            NeuralInferenceClient.NeuralPrediction prediction = neuralInferenceClient.predict(
                    history,
                    accumulatedDamage(profiles, inspections).doubleValue()
            );
            Map<String, Double> features = prediction.predictedFeatures();
            int[] loadPercentages = toPercentages(
                    value(features, "load_lt_40"),
                    value(features, "load_40_80"),
                    value(features, "load_80_100"),
                    value(features, "load_gt_100")
            );
            int predictedWear = clamp(
                    (int) Math.round(prediction.predictedWearPercent()),
                    0,
                    100
            );
            int predictedResource = 100 - predictedWear;

            return Optional.of(new ForecastResult(
                    upload.getCrane(),
                    upload,
                    upload.getPeriodEnd().plusMonths(1),
                    nonNegativeInt(value(features, "cycles_total")),
                    loadPercentages[0],
                    loadPercentages[1],
                    loadPercentages[2],
                    loadPercentages[3],
                    nonNegativeInt(value(features, "overload_cycles")),
                    decimal(features, "hoist_hours"),
                    decimal(features, "boom_angle_hours"),
                    decimal(features, "boom_length_hours"),
                    decimal(features, "rotation_hours"),
                    nonNegativeInt(value(features, "broken_wires_count")),
                    decimal(features, "diameter_loss_percent"),
                    score(features, "corrosion_score"),
                    score(features, "abrasion_score"),
                    score(features, "deformation_score"),
                    score(features, "lubrication_score"),
                    BigDecimal.valueOf(prediction.predictedDamageIndex())
                            .setScale(4, RoundingMode.HALF_UP),
                    predictedWear,
                    predictedResource,
                    statusForResource(predictedResource)
            ));
        } catch (RuntimeException exception) {
            LOGGER.warn("CNN-LSTM forecast failed for crane {}", crane.getId(), exception);
            return Optional.empty();
        }
    }

    private BigDecimal accumulatedDamage(
            List<LoadProfile> profiles,
            List<VisualInspection> inspections
    ) {
        BigDecimal damage = BigDecimal.ZERO;
        for (LoadProfile profile : profiles) {
            damage = minerDamageCalculator.accumulate(
                    damage,
                    minerDamageCalculator.calculatePeriodDamage(
                            profile,
                            latestInspection(inspections, profile.getUpload())
                    )
            );
        }
        return damage;
    }

    private Map<String, Double> featureRow(
            LoadProfile profile,
            MechanismWorkload workload,
            VisualInspection inspection
    ) {
        double loadLt40 = profile.getLoadLt40() / 100.0;
        double load40To80 = profile.getLoad40To80() / 100.0;
        double load80To100 = profile.getLoad80To100() / 100.0;
        double loadGt100 = profile.getLoadGt100() / 100.0;

        Map<String, Double> row = new LinkedHashMap<>();
        row.put("cycles_total", (double) profile.getCyclesTotal());
        row.put("load_lt_40", loadLt40);
        row.put("load_40_80", load40To80);
        row.put("load_80_100", load80To100);
        row.put("load_gt_100", loadGt100);
        row.put("overload_cycles", (double) profile.getOverloadCycles());
        row.put("load_distribution_coef", loadDistribution(
                loadLt40,
                load40To80,
                load80To100,
                loadGt100
        ));
        row.put("hoist_hours", workload.getHoistHours().doubleValue());
        row.put("boom_angle_hours", workload.getBoomAngleHours().doubleValue());
        row.put("boom_length_hours", workload.getBoomLengthHours().doubleValue());
        row.put("rotation_hours", workload.getRotationHours().doubleValue());
        row.put(
                "broken_wires_count",
                inspection == null ? 0.0 : (double) inspection.getBrokenWiresCount()
        );
        row.put(
                "diameter_loss_percent",
                inspection == null ? 0.0 : inspection.getDiameterLossPercent().doubleValue()
        );
        row.put(
                "corrosion_score",
                inspection == null ? 0.0 : (double) inspection.getCorrosionScore()
        );
        row.put(
                "abrasion_score",
                inspection == null ? 0.0 : (double) inspection.getAbrasionScore()
        );
        row.put(
                "deformation_score",
                inspection == null ? 0.0 : (double) inspection.getDeformationScore()
        );
        row.put(
                "lubrication_score",
                inspection == null ? 0.0 : (double) inspection.getLubricationScore()
        );
        return row;
    }

    private VisualInspection latestInspection(List<VisualInspection> inspections, Upload upload) {
        return inspections.stream()
                .filter(inspection -> !inspection.getInspectionDate().isAfter(upload.getPeriodEnd()))
                .max(Comparator.comparing(VisualInspection::getInspectionDate))
                .orElse(null);
    }

    private double loadDistribution(
            double loadLt40,
            double load40To80,
            double load80To100,
            double loadGt100
    ) {
        return 0.25 * loadLt40
                + 0.60 * load40To80
                + 0.90 * load80To100
                + 1.10 * loadGt100;
    }

    private int[] toPercentages(double... shares) {
        int[] percentages = new int[shares.length];
        double[] fractions = new double[shares.length];
        double sum = 0.0;
        for (double share : shares) {
            sum += Math.max(0.0, share);
        }
        if (sum <= 0.0) {
            return new int[]{100, 0, 0, 0};
        }

        int assigned = 0;
        for (int index = 0; index < shares.length; index++) {
            double exact = Math.max(0.0, shares[index]) * 100.0 / sum;
            percentages[index] = (int) Math.floor(exact);
            fractions[index] = exact - percentages[index];
            assigned += percentages[index];
        }

        while (assigned < 100) {
            int largestFractionIndex = 0;
            for (int index = 1; index < fractions.length; index++) {
                if (fractions[index] > fractions[largestFractionIndex]) {
                    largestFractionIndex = index;
                }
            }
            percentages[largestFractionIndex]++;
            fractions[largestFractionIndex] = -1.0;
            assigned++;
        }
        return percentages;
    }

    private double value(Map<String, Double> features, String name) {
        Double value = features.get(name);
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException("CNN-LSTM response has no valid " + name);
        }
        return value;
    }

    private BigDecimal decimal(Map<String, Double> features, String name) {
        return BigDecimal.valueOf(Math.max(0.0, value(features, name)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private int nonNegativeInt(double value) {
        return Math.max(0, (int) Math.round(value));
    }

    private int score(Map<String, Double> features, String name) {
        return clamp((int) Math.round(value(features, name)), 0, 5);
    }

    private String statusForResource(int remainingResource) {
        if (remainingResource > 60) {
            return "Ресурс достаточный";
        }
        if (remainingResource > 40) {
            return "Требуется плановый контроль";
        }
        if (remainingResource > 20) {
            return "Требуется усиленный контроль";
        }
        return "Приближение к замене";
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
