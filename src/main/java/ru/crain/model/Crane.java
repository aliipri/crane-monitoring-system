package ru.crain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "cranes")
public class Crane {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String model;

    @Column(name = "inventory_no", nullable = false, unique = true)
    private String inventoryNumber;

    @Column(name = "serial_no", nullable = false)
    private String serialNumber;

    @Column(name = "safety_device", nullable = false)
    private String safetyDevice;

    @Column(name = "commissioning_date", nullable = false)
    private LocalDate commissioningDate;

    @Column(name = "rope_installation_date", nullable = false)
    private LocalDate ropeInstallationDate;

    @Column(name = "normative_resource", nullable = false)
    private String normativeResource;

    protected Crane() {
    }

    public Crane(
            String model,
            String inventoryNumber,
            String serialNumber,
            String safetyDevice,
            LocalDate commissioningDate,
            LocalDate ropeInstallationDate,
            String normativeResource
    ) {
        this.model = model;
        this.inventoryNumber = inventoryNumber;
        this.serialNumber = serialNumber;
        this.safetyDevice = safetyDevice;
        this.commissioningDate = commissioningDate;
        this.ropeInstallationDate = ropeInstallationDate;
        this.normativeResource = normativeResource;
    }

    public Long getId() {
        return id;
    }

    public String getModel() {
        return model;
    }

    public String getInventoryNumber() {
        return inventoryNumber;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getSafetyDevice() {
        return safetyDevice;
    }

    public LocalDate getCommissioningDate() {
        return commissioningDate;
    }

    public LocalDate getRopeInstallationDate() {
        return ropeInstallationDate;
    }

    public String getNormativeResource() {
        return normativeResource;
    }
}
