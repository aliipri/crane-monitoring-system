package ru.crain;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import ru.crain.model.ForecastResult;
import ru.crain.repository.ForecastResultRepository;
import ru.crain.repository.UploadRepository;
import ru.crain.service.NeuralForecastBackfillService;
import ru.crain.service.TxtImporter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "crane.neural.enabled=true",
        "crane.neural.python=${CRANE_NEURAL_PYTHON:disabled}",
        "crane.neural.model-dir=${CRANE_NEURAL_MODEL_DIR:disabled}",
        "crane.neural.backfill-on-startup=true"
})
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_NEURAL_INTEGRATION_TEST", matches = "true")
class NeuralPredictionIntegrationTests {

    @Autowired
    private TxtImporter txtImporter;

    @Autowired
    private UploadRepository uploadRepository;

    @Autowired
    private ForecastResultRepository forecastResultRepository;

    @Autowired
    private NeuralForecastBackfillService neuralForecastBackfillService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void fifthPeriodCreatesCnnLstmForecast() throws Exception {
        Path uploadDirectory = Path.of("sample-data");
        List<Path> files;
        try (var paths = Files.list(uploadDirectory)) {
            files = paths
                    .filter(path -> path.getFileName().toString().startsWith("EDK-1000_"))
                    .filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .sorted()
                    .limit(5)
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

        assertThat(uploadRepository.count()).isEqualTo(5);
        assertThat(forecastResultRepository.findAll()).hasSize(1);

        ForecastResult forecast = forecastResultRepository.findAll().get(0);
        assertThat(forecast.getPredictionSource()).isEqualTo("CNN_LSTM");
        assertThat(forecast.getPredictedCyclesTotal()).isPositive();
        assertThat(
                forecast.getPredictedLoadLt40()
                        + forecast.getPredictedLoad40To80()
                        + forecast.getPredictedLoad80To100()
                        + forecast.getPredictedLoadGt100()
        ).isEqualTo(100);
        assertThat(forecast.getPredictedDamageIndex()).isPositive();
        assertThat(forecast.getPredictedWearPercent()).isBetween(0, 100);
        assertThat(forecast.getPredictedRemainingResource()).isBetween(0, 100);

        entityManager.flush();
        jdbcTemplate.update(
                "update predictions set prediction_source = 'LEGACY_FORMULA' where id = ?",
                forecast.getId()
        );
        entityManager.clear();

        neuralForecastBackfillService.backfillMissingForecasts();
        entityManager.flush();
        entityManager.clear();

        assertThat(forecastResultRepository.findAll())
                .singleElement()
                .extracting(ForecastResult::getPredictionSource)
                .isEqualTo("CNN_LSTM");
    }
}
