package ru.crain.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class NeuralInferenceClient {

    public static final int WINDOW_SIZE = 5;

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Path pythonExecutable;
    private final Path modelDirectory;
    private final long timeoutSeconds;
    private Path inferenceScript;
    private volatile String lastError;

    public NeuralInferenceClient(
            ObjectMapper objectMapper,
            @Value("${crane.neural.enabled:true}") boolean enabled,
            @Value("${crane.neural.python}") String pythonExecutable,
            @Value("${crane.neural.model-dir}") String modelDirectory,
            @Value("${crane.neural.timeout-seconds:30}") long timeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.pythonExecutable = Path.of(pythonExecutable);
        this.modelDirectory = Path.of(modelDirectory);
        this.timeoutSeconds = timeoutSeconds;
    }

    @PostConstruct
    void initialize() throws IOException {
        if (!enabled) {
            return;
        }
        inferenceScript = Files.createTempFile("crane-cnn-lstm-", ".py");
        try (var source = new ClassPathResource("ml/cnn_lstm_inference.py").getInputStream()) {
            Files.copy(source, inferenceScript, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @PreDestroy
    void cleanup() throws IOException {
        if (inferenceScript != null) {
            Files.deleteIfExists(inferenceScript);
        }
    }

    public boolean isAvailable() {
        return enabled
                && inferenceScript != null
                && Files.isRegularFile(pythonExecutable)
                && Files.isRegularFile(modelDirectory.resolve("model_config.json"))
                && Files.isRegularFile(modelDirectory.resolve("cnn_lstm_model.pt"))
                && Files.isRegularFile(modelDirectory.resolve("scaler_x.pkl"))
                && Files.isRegularFile(modelDirectory.resolve("scaler_y.pkl"));
    }

    public String modelLabel(int periodCount) {
        if (!enabled) {
            return "CNN-LSTM отключена";
        }
        if (!isAvailable()) {
            return "CNN-LSTM недоступна";
        }
        if (lastError != null) {
            return "CNN-LSTM: ошибка";
        }
        if (periodCount < WINDOW_SIZE) {
            return "CNN-LSTM: " + periodCount + "/" + WINDOW_SIZE;
        }
        return "CNN-LSTM";
    }

    public String unavailableReason(int periodCount) {
        if (!enabled) {
            return "Нейросетевая модель отключена в конфигурации.";
        }
        if (!isAvailable()) {
            return "Не найдены Python или файлы обученной модели.";
        }
        if (periodCount < WINDOW_SIZE) {
            return "Для прогноза требуется минимум " + WINDOW_SIZE + " периодов.";
        }
        if (lastError != null) {
            return "Ошибка нейросетевого прогноза: " + lastError;
        }
        return "Прогноз еще не рассчитан.";
    }

    public NeuralPrediction predict(List<Map<String, Double>> history, double previousDamage) {
        if (!isAvailable()) {
            throw new IllegalStateException("CNN-LSTM model is not available");
        }
        if (history.size() != WINDOW_SIZE) {
            throw new IllegalArgumentException("CNN-LSTM requires exactly " + WINDOW_SIZE + " periods");
        }

        Process process = null;
        try {
            process = new ProcessBuilder(
                    pythonExecutable.toString(),
                    inferenceScript.toString(),
                    "--model-dir",
                    modelDirectory.toString()
            ).start();
            objectMapper.writeValue(
                    process.getOutputStream(),
                    new InferenceRequest(history, previousDamage)
            );
            process.getOutputStream().close();

            Process runningProcess = process;
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(
                    () -> readStream(runningProcess.getInputStream())
            );
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(
                    () -> readStream(runningProcess.getErrorStream())
            );

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("CNN-LSTM inference timed out");
            }

            String output = stdout.get(5, TimeUnit.SECONDS);
            String error = stderr.get(5, TimeUnit.SECONDS).trim();
            if (process.exitValue() != 0) {
                throw new IllegalStateException(error.isBlank() ? "CNN-LSTM inference failed" : error);
            }

            NeuralPrediction prediction = objectMapper.readValue(output, NeuralPrediction.class);
            lastError = null;
            return prediction;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            lastError = "выполнение прервано";
            throw new IllegalStateException("CNN-LSTM inference was interrupted", exception);
        } catch (Exception exception) {
            lastError = conciseMessage(exception);
            throw new IllegalStateException("CNN-LSTM inference failed: " + lastError, exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String readStream(java.io.InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read CNN-LSTM process output", exception);
        }
    }

    private String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private record InferenceRequest(
            List<Map<String, Double>> history,
            @JsonProperty("previous_damage") double previousDamage
    ) {
    }

    public record NeuralPrediction(
            @JsonProperty("predicted_features") Map<String, Double> predictedFeatures,
            @JsonProperty("predicted_damage_index") double predictedDamageIndex,
            @JsonProperty("predicted_wear_percent") double predictedWearPercent,
            @JsonProperty("predicted_remaining_resource") double predictedRemainingResource
    ) {
    }
}
