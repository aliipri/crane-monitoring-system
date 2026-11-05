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

@Entity
@Table(name = "load_profiles")
public class LoadProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_id", nullable = false, unique = true)
    private Upload upload;

    @Column(name = "load_lt_40", nullable = false)
    private int loadLt40;

    @Column(name = "load_40_80", nullable = false)
    private int load40To80;

    @Column(name = "load_80_100", nullable = false)
    private int load80To100;

    @Column(name = "load_gt_100", nullable = false)
    private int loadGt100;

    @Column(name = "cycles_total", nullable = false)
    private int cyclesTotal;

    @Column(name = "overload_cycles", nullable = false)
    private int overloadCycles;

    protected LoadProfile() {
    }

    public LoadProfile(
            Upload upload,
            int loadLt40,
            int load40To80,
            int load80To100,
            int loadGt100,
            int cyclesTotal,
            int overloadCycles
    ) {
        this.upload = upload;
        this.loadLt40 = loadLt40;
        this.load40To80 = load40To80;
        this.load80To100 = load80To100;
        this.loadGt100 = loadGt100;
        this.cyclesTotal = cyclesTotal;
        this.overloadCycles = overloadCycles;
    }

    public Long getId() {
        return id;
    }

    public Upload getUpload() {
        return upload;
    }

    public int getLoadLt40() {
        return loadLt40;
    }

    public int getLoad40To80() {
        return load40To80;
    }

    public int getLoad80To100() {
        return load80To100;
    }

    public int getLoadGt100() {
        return loadGt100;
    }

    public int getCyclesTotal() {
        return cyclesTotal;
    }

    public int getOverloadCycles() {
        return overloadCycles;
    }
}
