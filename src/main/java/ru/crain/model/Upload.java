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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "uploads",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_uploads_crane_file",
                columnNames = {"crane_id", "file_name", "period_start", "period_end"}
        )
)
public class Upload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crane_id", nullable = false)
    private Crane crane;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "upload_date", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "file_path")
    private String filePath;

    protected Upload() {
    }

    public Upload(
            Crane crane,
            String fileName,
            LocalDateTime uploadedAt,
            LocalDate periodStart,
            LocalDate periodEnd,
            String filePath
    ) {
        this.crane = crane;
        this.fileName = fileName;
        this.uploadedAt = uploadedAt;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.filePath = filePath;
    }

    public Long getId() {
        return id;
    }

    public Crane getCrane() {
        return crane;
    }

    public String getFileName() {
        return fileName;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public String getFilePath() {
        return filePath;
    }
}
