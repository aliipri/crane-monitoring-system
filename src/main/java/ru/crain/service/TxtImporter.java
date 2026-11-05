package ru.crain.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.crain.model.Crane;
import ru.crain.model.Event;
import ru.crain.model.LoadProfile;
import ru.crain.model.MechanismWorkload;
import ru.crain.model.Upload;
import ru.crain.model.VisualInspection;
import ru.crain.repository.CraneRepository;
import ru.crain.repository.EventRepository;
import ru.crain.repository.ForecastResultRepository;
import ru.crain.repository.LoadProfileRepository;
import ru.crain.repository.MechanismWorkloadRepository;
import ru.crain.repository.MetricsRepository;
import ru.crain.repository.UploadRepository;
import ru.crain.repository.VisualInspectionRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class TxtImporter {

    private final CraneRepository craneRepository;
    private final UploadRepository uploadRepository;
    private final LoadProfileRepository loadProfileRepository;
    private final MechanismWorkloadRepository mechanismWorkloadRepository;
    private final VisualInspectionRepository visualInspectionRepository;
    private final EventRepository eventRepository;
    private final MetricsRepository metricsRepository;
    private final ForecastResultRepository forecastResultRepository;
    private final DataPreprocessor dataPreprocessor;
    private final AnalyticsService analyticsService;
    private final PredictionModel predictionModel;

    public TxtImporter(
            CraneRepository craneRepository,
            UploadRepository uploadRepository,
            LoadProfileRepository loadProfileRepository,
            MechanismWorkloadRepository mechanismWorkloadRepository,
            VisualInspectionRepository visualInspectionRepository,
            EventRepository eventRepository,
            MetricsRepository metricsRepository,
            ForecastResultRepository forecastResultRepository,
            DataPreprocessor dataPreprocessor,
            AnalyticsService analyticsService,
            PredictionModel predictionModel
    ) {
        this.craneRepository = craneRepository;
        this.uploadRepository = uploadRepository;
        this.loadProfileRepository = loadProfileRepository;
        this.mechanismWorkloadRepository = mechanismWorkloadRepository;
        this.visualInspectionRepository = visualInspectionRepository;
        this.eventRepository = eventRepository;
        this.metricsRepository = metricsRepository;
        this.forecastResultRepository = forecastResultRepository;
        this.dataPreprocessor = dataPreprocessor;
        this.analyticsService = analyticsService;
        this.predictionModel = predictionModel;
    }

    @Transactional
    public Upload importFile(MultipartFile file) throws IOException {
        DataPreprocessor.ProcessedTxtData data = dataPreprocessor.preprocess(file);
        Crane crane = resolveCrane(data.crane());
        String originalName = file.getOriginalFilename() == null ? "upload.txt" : file.getOriginalFilename();

        Upload upload = uploadRepository.save(new Upload(
                crane,
                originalName,
                LocalDateTime.now(),
                data.periodStart(),
                data.periodEnd(),
                "uploads/" + originalName
        ));
        LoadProfile loadProfile = loadProfileRepository.save(new LoadProfile(
                upload,
                data.loadLt40(),
                data.load40To80(),
                data.load80To100(),
                data.loadGt100(),
                data.cycles(),
                data.overloads()
        ));
        MechanismWorkload workload = mechanismWorkloadRepository.save(new MechanismWorkload(
                upload,
                data.hoistHours(),
                data.boomAngleHours(),
                data.boomLengthHours(),
                data.rotationHours()
        ));
        List<VisualInspection> importedInspections = data.inspections().stream()
                .map(inspection -> new VisualInspection(
                        crane,
                        upload,
                        inspection.inspectionDate(),
                        inspection.brokenWiresCount(),
                        inspection.diameterLossPercent(),
                        inspection.corrosionScore(),
                        inspection.abrasionScore(),
                        inspection.deformationScore(),
                        inspection.lubricationScore(),
                        inspection.comment()
                ))
                .map(visualInspectionRepository::save)
                .toList();
        VisualInspection inspection = importedInspections.stream()
                .max(Comparator.comparing(VisualInspection::getInspectionDate))
                .orElseGet(() -> visualInspectionRepository
                        .findLatestByCrane(crane, PageRequest.of(0, 1))
                        .stream()
                        .findFirst()
                        .orElse(null));
        metricsRepository.save(analyticsService.calculateMetrics(upload, loadProfile, inspection));
        predictionModel.createForecast(upload).ifPresent(forecastResultRepository::save);

        data.events().stream()
                .map(event -> new Event(
                        upload,
                        event.eventTime(),
                        event.eventType(),
                        event.description(),
                        event.severity()
                ))
                .forEach(eventRepository::save);

        return upload;
    }

    private Crane resolveCrane(DataPreprocessor.CraneData data) {
        return craneRepository.findByInventoryNumber(data.inventoryNumber())
                .orElseGet(() -> craneRepository.save(new Crane(
                        data.model(),
                        data.inventoryNumber(),
                        data.serialNumber(),
                        data.safetyDevice(),
                        data.commissioningDate(),
                        data.ropeInstallationDate(),
                        data.normativeResource()
                )));
    }
}
