package ru.crain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "predictions")
public class ForecastResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crane_id", nullable = false)
    private Crane crane;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_id", nullable = false, unique = true)
    private Upload upload;

    @Column(name = "forecast_date", nullable = false)
    private LocalDate forecastDate;

    @Column(name = "predicted_cycles_total", nullable = false)
    private int predictedCyclesTotal;

    @Column(name = "predicted_load_lt_40", nullable = false)
    private int predictedLoadLt40;

    @Column(name = "predicted_load_40_80", nullable = false)
    private int predictedLoad40To80;

    @Column(name = "predicted_load_80_100", nullable = false)
    private int predictedLoad80To100;

    @Column(name = "predicted_load_gt_100", nullable = false)
    private int predictedLoadGt100;

    @Column(name = "predicted_overload_cycles", nullable = false)
    private int predictedOverloadCycles;

    @Column(name = "predicted_hoist_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal predictedHoistHours;

    @Column(name = "predicted_boom_angle_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal predictedBoomAngleHours;

    @Column(name = "predicted_boom_length_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal predictedBoomLengthHours;

    @Column(name = "predicted_rotation_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal predictedRotationHours;

    @Column(name = "predicted_broken_wires_count", nullable = false)
    private int predictedBrokenWiresCount;

    @Column(name = "predicted_diameter_loss_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal predictedDiameterLossPercent;

    @Column(name = "predicted_corrosion_score", nullable = false)
    private int predictedCorrosionScore;

    @Column(name = "predicted_abrasion_score", nullable = false)
    private int predictedAbrasionScore;

    @Column(name = "predicted_deformation_score", nullable = false)
    private int predictedDeformationScore;

    @Column(name = "predicted_lubrication_score", nullable = false)
    private int predictedLubricationScore;

    @Column(name = "predicted_damage_index", nullable = false, precision = 8, scale = 4)
    private BigDecimal predictedDamageIndex;

    @Column(name = "predicted_wear_percent", nullable = false)
    private int predictedWearPercent;

    @Column(name = "predicted_remaining_resource", nullable = false)
    private int predictedRemainingResource;

    @Column(name = "predicted_status", nullable = false)
    private String predictedStatus;

    @Column(name = "prediction_source", nullable = false)
    private String predictionSource;

    protected ForecastResult() {
    }

    public ForecastResult(
            Crane crane,
            Upload upload,
            LocalDate forecastDate,
            int predictedCyclesTotal,
            int predictedLoadLt40,
            int predictedLoad40To80,
            int predictedLoad80To100,
            int predictedLoadGt100,
            int predictedOverloadCycles,
            BigDecimal predictedHoistHours,
            BigDecimal predictedBoomAngleHours,
            BigDecimal predictedBoomLengthHours,
            BigDecimal predictedRotationHours,
            int predictedBrokenWiresCount,
            BigDecimal predictedDiameterLossPercent,
            int predictedCorrosionScore,
            int predictedAbrasionScore,
            int predictedDeformationScore,
            int predictedLubricationScore,
            BigDecimal predictedDamageIndex,
            int predictedWearPercent,
            int predictedRemainingResource,
            String predictedStatus
    ) {
        this.crane = crane;
        this.upload = upload;
        this.forecastDate = forecastDate;
        this.predictedCyclesTotal = predictedCyclesTotal;
        this.predictedLoadLt40 = predictedLoadLt40;
        this.predictedLoad40To80 = predictedLoad40To80;
        this.predictedLoad80To100 = predictedLoad80To100;
        this.predictedLoadGt100 = predictedLoadGt100;
        this.predictedOverloadCycles = predictedOverloadCycles;
        this.predictedHoistHours = predictedHoistHours;
        this.predictedBoomAngleHours = predictedBoomAngleHours;
        this.predictedBoomLengthHours = predictedBoomLengthHours;
        this.predictedRotationHours = predictedRotationHours;
        this.predictedBrokenWiresCount = predictedBrokenWiresCount;
        this.predictedDiameterLossPercent = predictedDiameterLossPercent;
        this.predictedCorrosionScore = predictedCorrosionScore;
        this.predictedAbrasionScore = predictedAbrasionScore;
        this.predictedDeformationScore = predictedDeformationScore;
        this.predictedLubricationScore = predictedLubricationScore;
        this.predictedDamageIndex = predictedDamageIndex;
        this.predictedWearPercent = predictedWearPercent;
        this.predictedRemainingResource = predictedRemainingResource;
        this.predictedStatus = predictedStatus;
        this.predictionSource = "CNN_LSTM";
    }

    public Long getId() {
        return id;
    }

    public Crane getCrane() {
        return crane;
    }

    public Upload getUpload() {
        return upload;
    }

    public LocalDate getForecastDate() {
        return forecastDate;
    }

    public int getPredictedCyclesTotal() {
        return predictedCyclesTotal;
    }

    public int getPredictedLoadLt40() {
        return predictedLoadLt40;
    }

    public int getPredictedLoad40To80() {
        return predictedLoad40To80;
    }

    public int getPredictedLoad80To100() {
        return predictedLoad80To100;
    }

    public int getPredictedLoadGt100() {
        return predictedLoadGt100;
    }

    public int getPredictedOverloadCycles() {
        return predictedOverloadCycles;
    }

    public BigDecimal getPredictedHoistHours() {
        return predictedHoistHours;
    }

    public BigDecimal getPredictedBoomAngleHours() {
        return predictedBoomAngleHours;
    }

    public BigDecimal getPredictedBoomLengthHours() {
        return predictedBoomLengthHours;
    }

    public BigDecimal getPredictedRotationHours() {
        return predictedRotationHours;
    }

    public int getPredictedBrokenWiresCount() {
        return predictedBrokenWiresCount;
    }

    public BigDecimal getPredictedDiameterLossPercent() {
        return predictedDiameterLossPercent;
    }

    public int getPredictedCorrosionScore() {
        return predictedCorrosionScore;
    }

    public int getPredictedAbrasionScore() {
        return predictedAbrasionScore;
    }

    public int getPredictedDeformationScore() {
        return predictedDeformationScore;
    }

    public int getPredictedLubricationScore() {
        return predictedLubricationScore;
    }

    public BigDecimal getPredictedDamageIndex() {
        return predictedDamageIndex;
    }

    public int getPredictedWearPercent() {
        return predictedWearPercent;
    }

    public int getPredictedRemainingResource() {
        return predictedRemainingResource;
    }

    public String getPredictedStatus() {
        return predictedStatus;
    }

    public String getPredictionSource() {
        return predictionSource;
    }
}
