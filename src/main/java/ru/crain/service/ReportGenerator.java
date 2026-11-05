package ru.crain.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.crain.model.Crane;
import ru.crain.model.Upload;
import ru.crain.repository.UploadRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class ReportGenerator {

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AnalyticsService analyticsService;
    private final UploadRepository uploadRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PdfReportBuilder pdfReportBuilder;

    public ReportGenerator(
            AnalyticsService analyticsService,
            UploadRepository uploadRepository,
            JdbcTemplate jdbcTemplate,
            PdfReportBuilder pdfReportBuilder
    ) {
        this.analyticsService = analyticsService;
        this.uploadRepository = uploadRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.pdfReportBuilder = pdfReportBuilder;
    }

    @Transactional
    public GeneratedReport generateReport(Long craneId) {
        Map<String, Object> dashboard;
        try {
            dashboard = analyticsService.getDashboard(craneId);
        } catch (IllegalArgumentException exception) {
            throw new CraneNotFoundException(exception.getMessage(), exception);
        }
        Crane crane = (Crane) dashboard.get("crane");
        Upload upload = uploadRepository.findFirstByCraneOrderByPeriodEndDesc(crane).orElse(null);
        LocalDateTime generatedAt = LocalDateTime.now();
        String fileName = "crane-" + crane.getId() + "-report-" + FILE_TIMESTAMP.format(generatedAt) + ".pdf";
        byte[] content = pdfReportBuilder.build(dashboard, generatedAt);
        jdbcTemplate.update(
                "INSERT INTO reports (crane_id, upload_id, report_date, report_path) VALUES (?, ?, ?, ?)",
                crane.getId(),
                upload == null ? null : upload.getId(),
                generatedAt,
                "generated/" + fileName
        );
        return new GeneratedReport(fileName, content);
    }

    public record GeneratedReport(String fileName, byte[] content) {
    }

    public static final class CraneNotFoundException extends RuntimeException {

        private CraneNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
