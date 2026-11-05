package ru.crain.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import ru.crain.model.Upload;
import ru.crain.service.AnalyticsService;
import ru.crain.service.ReportGenerator;
import ru.crain.service.TxtImporter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Controller
public class DashboardController {

    private final AnalyticsService analyticsService;
    private final TxtImporter txtImporter;
    private final ReportGenerator reportGenerator;

    public DashboardController(
            AnalyticsService analyticsService,
            TxtImporter txtImporter,
            ReportGenerator reportGenerator
    ) {
        this.analyticsService = analyticsService;
        this.txtImporter = txtImporter;
        this.reportGenerator = reportGenerator;
    }

    @GetMapping("/")
    public String craneList(Model model) {
        model.addAttribute("cranes", analyticsService.getCraneList());
        return "cranes";
    }

    @GetMapping("/cranes/{craneId}")
    public String dashboard(@PathVariable Long craneId, Model model) {
        try {
            model.addAttribute("dashboard", analyticsService.getDashboard(craneId));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
        return "dashboard";
    }

    @PostMapping("/uploads")
    public String uploadTxt(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Выберите TXT-файл с данными регистратора.");
            return "redirect:/";
        }

        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            redirectAttributes.addFlashAttribute("error", "Файл должен иметь расширение .txt.");
            return "redirect:/";
        }

        try {
            Upload upload = txtImporter.importFile(file);
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Файл " + upload.getFileName() + " загружен в PostgreSQL. Запись: #" + upload.getId() + "."
            );
            return "redirect:/cranes/" + upload.getCrane().getId();
        } catch (IOException exception) {
            redirectAttributes.addFlashAttribute("error", "Не удалось прочитать TXT-файл.");
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("error", "Не удалось сохранить данные: " + exception.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping(value = "/cranes/{craneId}/reports", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generateReport(@PathVariable Long craneId) {
        try {
            ReportGenerator.GeneratedReport report = reportGenerator.generateReport(craneId);
            ContentDisposition disposition = ContentDisposition.inline()
                    .filename(report.fileName(), StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(report.content().length)
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(report.content());
        } catch (ReportGenerator.CraneNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }
}
