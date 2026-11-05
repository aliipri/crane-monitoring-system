package ru.crain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.crain.model.Crane;
import ru.crain.model.ForecastResult;
import ru.crain.model.Upload;
import ru.crain.repository.CraneRepository;
import ru.crain.repository.ForecastResultRepository;
import ru.crain.repository.UploadRepository;

@Service
public class NeuralForecastBackfillService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NeuralForecastBackfillService.class);

    private final CraneRepository craneRepository;
    private final UploadRepository uploadRepository;
    private final ForecastResultRepository forecastResultRepository;
    private final PredictionModel predictionModel;
    private final NeuralInferenceClient neuralInferenceClient;
    private final boolean enabled;

    public NeuralForecastBackfillService(
            CraneRepository craneRepository,
            UploadRepository uploadRepository,
            ForecastResultRepository forecastResultRepository,
            PredictionModel predictionModel,
            NeuralInferenceClient neuralInferenceClient,
            @Value("${crane.neural.backfill-on-startup:true}") boolean enabled
    ) {
        this.craneRepository = craneRepository;
        this.uploadRepository = uploadRepository;
        this.forecastResultRepository = forecastResultRepository;
        this.predictionModel = predictionModel;
        this.neuralInferenceClient = neuralInferenceClient;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillMissingForecasts() {
        if (!enabled || !neuralInferenceClient.isAvailable()) {
            return;
        }

        for (Crane crane : craneRepository.findAllWithUploads()) {
            try {
                backfillCrane(crane);
            } catch (RuntimeException exception) {
                LOGGER.warn("CNN-LSTM backfill failed for crane {}", crane.getId(), exception);
            }
        }
    }

    private void backfillCrane(Crane crane) {
        if (uploadRepository.countByCrane(crane) < NeuralInferenceClient.WINDOW_SIZE) {
            return;
        }

        Upload latestUpload = uploadRepository.findFirstByCraneOrderByPeriodEndDesc(crane)
                .orElse(null);
        if (latestUpload == null) {
            return;
        }

        ForecastResult existingForecast = forecastResultRepository.findByUpload(latestUpload)
                .orElse(null);
        if (existingForecast != null && "CNN_LSTM".equals(existingForecast.getPredictionSource())) {
            return;
        }

        predictionModel.createForecast(latestUpload).ifPresent(forecast -> {
            if (existingForecast != null) {
                forecastResultRepository.delete(existingForecast);
                forecastResultRepository.flush();
            }
            forecastResultRepository.save(forecast);
            LOGGER.info(
                    "CNN-LSTM forecast backfilled for crane {}, upload {}",
                    crane.getId(),
                    latestUpload.getId()
            );
        });
    }
}
