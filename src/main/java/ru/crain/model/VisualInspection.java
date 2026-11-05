package ru.crain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "visual_inspections")
public class VisualInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crane_id", nullable = false)
    private Crane crane;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_id")
    private Upload upload;

    @Column(name = "inspection_date", nullable = false)
    private LocalDate inspectionDate;

    @Column(name = "broken_wires_count", nullable = false)
    private int brokenWiresCount;

    @Column(name = "diameter_loss_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal diameterLossPercent;

    @Column(name = "corrosion_score", nullable = false)
    private int corrosionScore;

    @Column(name = "abrasion_score", nullable = false)
    private int abrasionScore;

    @Column(name = "deformation_score", nullable = false)
    private int deformationScore;

    @Column(name = "lubrication_score", nullable = false)
    private int lubricationScore;

    @Column(columnDefinition = "TEXT")
    private String comment;

    protected VisualInspection() {
    }

    public VisualInspection(
            Crane crane,
            Upload upload,
            LocalDate inspectionDate,
            int brokenWiresCount,
            BigDecimal diameterLossPercent,
            int corrosionScore,
            int abrasionScore,
            int deformationScore,
            int lubricationScore,
            String comment
    ) {
        this.crane = crane;
        this.upload = upload;
        this.inspectionDate = inspectionDate;
        this.brokenWiresCount = brokenWiresCount;
        this.diameterLossPercent = diameterLossPercent;
        this.corrosionScore = corrosionScore;
        this.abrasionScore = abrasionScore;
        this.deformationScore = deformationScore;
        this.lubricationScore = lubricationScore;
        this.comment = comment;
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

    public LocalDate getInspectionDate() {
        return inspectionDate;
    }

    public int getBrokenWiresCount() {
        return brokenWiresCount;
    }

    public BigDecimal getDiameterLossPercent() {
        return diameterLossPercent;
    }

    public int getCorrosionScore() {
        return corrosionScore;
    }

    public int getAbrasionScore() {
        return abrasionScore;
    }

    public int getDeformationScore() {
        return deformationScore;
    }

    public int getLubricationScore() {
        return lubricationScore;
    }

    public String getComment() {
        return comment;
    }
}
