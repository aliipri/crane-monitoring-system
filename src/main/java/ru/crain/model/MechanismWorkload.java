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
@Table(name = "mechanism_workloads")
public class MechanismWorkload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_id", nullable = false, unique = true)
    private Upload upload;

    @Column(name = "hoist_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal hoistHours;

    @Column(name = "boom_angle_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal boomAngleHours;

    @Column(name = "boom_length_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal boomLengthHours;

    @Column(name = "rotation_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal rotationHours;

    protected MechanismWorkload() {
    }

    public MechanismWorkload(
            Upload upload,
            BigDecimal hoistHours,
            BigDecimal boomAngleHours,
            BigDecimal boomLengthHours,
            BigDecimal rotationHours
    ) {
        this.upload = upload;
        this.hoistHours = hoistHours;
        this.boomAngleHours = boomAngleHours;
        this.boomLengthHours = boomLengthHours;
        this.rotationHours = rotationHours;
    }

    public Long getId() {
        return id;
    }

    public Upload getUpload() {
        return upload;
    }

    public BigDecimal getHoistHours() {
        return hoistHours;
    }

    public BigDecimal getBoomAngleHours() {
        return boomAngleHours;
    }

    public BigDecimal getBoomLengthHours() {
        return boomLengthHours;
    }

    public BigDecimal getRotationHours() {
        return rotationHours;
    }
}
