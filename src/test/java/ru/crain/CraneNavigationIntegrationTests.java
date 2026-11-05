package ru.crain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.crain.model.Crane;
import ru.crain.model.LoadProfile;
import ru.crain.model.MechanismWorkload;
import ru.crain.repository.CraneRepository;
import ru.crain.repository.EventRepository;
import ru.crain.repository.LoadProfileRepository;
import ru.crain.repository.MechanismWorkloadRepository;
import ru.crain.repository.MetricsRepository;
import ru.crain.repository.UploadRepository;
import ru.crain.repository.VisualInspectionRepository;
import ru.crain.service.AnalyticsService;
import ru.crain.service.TxtImporter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CraneNavigationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CraneRepository craneRepository;

    @Autowired
    private LoadProfileRepository loadProfileRepository;

    @Autowired
    private MechanismWorkloadRepository mechanismWorkloadRepository;

    @Autowired
    private VisualInspectionRepository visualInspectionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private MetricsRepository metricsRepository;

    @Autowired
    private UploadRepository uploadRepository;

    @Autowired
    private TxtImporter txtImporter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AnalyticsService analyticsService;

    @Test
    void uploadedFileCreatesCraneAndOpensItsDashboard() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "EDK-1000_2026-04.txt",
                "text/plain",
                structuredTxt().getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/uploads").file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/cranes/*"));

        Crane crane = craneRepository.findByInventoryNumber("ЕДК-1000-01").orElseThrow();
        assertThat(craneRepository.findAllWithUploads()).containsExactly(crane);

        LoadProfile profile = loadProfileRepository
                .findByCraneOrderByPeriodEndDesc(crane)
                .get(0);
        assertThat(profile.getCyclesTotal()).isEqualTo(100);
        assertThat(profile.getLoadLt40()).isEqualTo(40);
        assertThat(profile.getLoad40To80()).isEqualTo(30);
        assertThat(profile.getLoad80To100()).isEqualTo(20);
        assertThat(profile.getLoadGt100()).isEqualTo(10);

        MechanismWorkload workload = mechanismWorkloadRepository
                .findByCraneOrderByPeriodEndDesc(crane)
                .get(0);
        assertThat(workload.getHoistHours()).isEqualByComparingTo("27.94");
        assertThat(visualInspectionRepository.findByCraneOrderByInspectionDateDesc(crane)).hasSize(1);
        assertThat(eventRepository.findByCraneOrderByEventTimeDesc(crane, PageRequest.of(0, 8))).hasSize(1);
        assertThat(metricsRepository.findByCraneOrderByPeriodEndAsc(crane)).hasSize(1);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ЕДК-1000")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Открыть панель анализа")))
                .andExpect(content().string(containsString("data-default-label=\"Выбрать TXT\"")))
                .andExpect(content().string(containsString("/js/app.js")));

        mockMvc.perform(get("/cranes/{id}", crane.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ЕДК-1000")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("EDK-1000_2026-04.txt")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("История визуальных осмотров")))
                .andExpect(content().string(containsString("data-default-label=\"TXT\"")))
                .andExpect(content().string(containsString("target=\"_blank\"")));

        createReportsTable();
        mockMvc.perform(post("/cranes/{id}/reports", crane.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII)));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reports WHERE crane_id = ?",
                Integer.class,
                crane.getId()
        )).isEqualTo(1);
    }

    @Test
    void allProvidedFilesCreateSixIndependentCranes() throws Exception {
        Path uploadDirectory = Path.of("sample-data");
        List<Path> files;
        try (var paths = Files.list(uploadDirectory)) {
            files = paths
                    .filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .filter(path -> !path.getFileName().toString().equals("README.txt"))
                    .sorted()
                    .toList();
        }

        for (Path path : files) {
            txtImporter.importFile(new MockMultipartFile(
                    "file",
                    path.getFileName().toString(),
                    "text/plain",
                    Files.readAllBytes(path)
            ));
        }

        assertThat(files).hasSize(36);
        assertThat(craneRepository.findAllWithUploads()).hasSize(6);
        assertThat(uploadRepository.count()).isEqualTo(36);

        Crane crane = craneRepository.findAllWithUploads().get(0);
        Map<String, Object> dashboard = analyticsService.getDashboard(crane.getId());
        assertThat((List<?>) dashboard.get("wearTrend")).hasSize(5);
        assertThat((List<?>) dashboard.get("resourceTrend")).hasSize(5);
    }

    private String structuredTxt() {
        return """
                [CRANE]
                model=ЕДК-1000
                inventory_no=ЕДК-1000-01
                serial_no=692
                safety_device=ОНК-160С
                commissioning_date=1979-01-01
                rope_installation_date=2024-12-20

                [PERIOD]
                period_start=2026-04-01
                period_end=2026-04-30

                [LOAD_PROFILE]
                cycles_total=100
                load_lt_40_cycles=40
                load_40_80_cycles=30
                load_80_100_cycles=20
                load_gt_100_cycles=10
                overload_cycles=10

                [MECHANISM_WORKLOAD]
                hoist_time=27:56:10
                boom_angle_time=00:45:00
                boom_length_time=00:30:00
                rotation_time=01:00:00

                [VISUAL_INSPECTION_1]
                inspection_date=2026-04-24
                broken_wires_count=5
                diameter_loss_percent=3.17
                corrosion_score=2
                abrasion_score=2
                deformation_score=1
                lubrication_score=1

                [EVENT_1]
                event_time=2026-04-18 14:26:00
                event_type=OVERLOAD
                severity=WARNING
                description=Зафиксирована перегрузка
                """;
    }

    private void createReportsTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS reports (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    crane_id BIGINT NOT NULL,
                    upload_id BIGINT,
                    report_date TIMESTAMP NOT NULL,
                    report_path VARCHAR(512) NOT NULL
                )
                """);
    }
}
