package ru.crain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "metrics")
public class Metrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_id", nullable = false, unique = true)
    private Upload upload;

    @Column(name = "intensity_index", nullable = false, precision = 5, scale = 2)
    private BigDecimal intensityIndex;

    @Column(name = "damage_index", nullable = false, precision = 8, scale = 4)
    private BigDecimal damageIndex;

    @Column(name = "intensity_class", nullable = false)
    private String intensityClass;

    @Column(name = "wear_percent", nullable = false)
    private int wearPercent;

    @Column(name = "remaining_resource", nullable = false)
    private int remainingResource;

    @Column(nullable = false)
    private String status;

    protected Metrics() {
    }

    public Metrics(
            Upload upload,
            BigDecimal intensityIndex,
            BigDecimal damageIndex,
            String intensityClass,
            int wearPercent,
            int remainingResource,
            String status
    ) {
        this.upload = upload;
        this.intensityIndex = intensityIndex;
        this.damageIndex = damageIndex;
        this.intensityClass = intensityClass;
        this.wearPercent = wearPercent;
        this.remainingResource = remainingResource;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Upload getUpload() {
        return upload;
    }

    public BigDecimal getIntensityIndex() {
        return intensityIndex;
    }

    public BigDecimal getDamageIndex() {
        return damageIndex;
    }

    public String getIntensityClass() {
        return intensityClass;
    }

    public int getWearPercent() {
        return wearPercent;
    }

    public int getRemainingResource() {
        return remainingResource;
    }

    public String getStatus() {
        return status;
    }
}
