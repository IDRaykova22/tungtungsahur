package org.codingburgas.demetra.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.codingburgas.demetra.controllers.forms.ReportFilterForm;
import org.codingburgas.demetra.models.reports.EventReport;
import org.codingburgas.demetra.models.reports.ReportGrouping;
import org.codingburgas.demetra.services.ReportPdfService;
import org.codingburgas.demetra.services.ReportService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Controller
public class ReportController {
    private final ReportService reportService;
    private final ReportPdfService reportPdfService;

    public ReportController(ReportService reportService, ReportPdfService reportPdfService) {
        this.reportService = reportService;
        this.reportPdfService = reportPdfService;
    }

    /** Shortcut links above the filter. */
    public record Preset(String label, LocalDate from, LocalDate to) {
    }

    @GetMapping("/reports")
    public String report(@ModelAttribute("filter") ReportFilterForm filter, BindingResult bindingResult,
                         Model model, HttpServletResponse response) {
        LocalDate today = reportService.today();
        model.addAttribute("maxDate", today);
        model.addAttribute("groupings", ReportGrouping.values());
        model.addAttribute("presets", presets(today));
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", "Enter valid dates in YYYY-MM-DD format.");
            return "reports/index";
        }
        ReportGrouping grouping = applyDefaults(filter, today);
        try {
            model.addAttribute("report", reportService.generate(filter.getFrom(), filter.getTo(), grouping));
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", ex.getMessage());
        }
        return "reports/index";
    }

    /** The same report as {@link #report} as a PDF: shown in the browser, or saved when {@code download=true}. */
    @GetMapping("/reports/pdf")
    public ResponseEntity<byte[]> pdf(@ModelAttribute("filter") ReportFilterForm filter, BindingResult bindingResult,
                                      @RequestParam(defaultValue = "false") boolean download,
                                      HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            return backToReportPage(request);
        }
        ReportGrouping grouping = applyDefaults(filter, reportService.today());
        EventReport report;
        try {
            report = reportService.generate(filter.getFrom(), filter.getTo(), grouping);
        } catch (IllegalArgumentException ex) {
            return backToReportPage(request);
        }
        ContentDisposition disposition = ContentDisposition.builder(download ? "attachment" : "inline")
                .filename(reportPdfService.fileName(report.getPeriod()), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore())
                .body(reportPdfService.render(report));
    }

    /** Without dates the report covers the current month up to today; an unknown grouping becomes "month". */
    private static ReportGrouping applyDefaults(ReportFilterForm filter, LocalDate today) {
        if (filter.getTo() == null) {
            filter.setTo(today);
        }
        if (filter.getFrom() == null) {
            filter.setFrom(filter.getTo().withDayOfMonth(1));
        }
        ReportGrouping grouping = ReportGrouping.fromKey(filter.getGroupBy());
        filter.setGroupBy(grouping.getKey());
        return grouping;
    }

    private static List<Preset> presets(LocalDate today) {
        LocalDate lastYear = today.minusYears(1);
        return List.of(
                new Preset("This month", today.withDayOfMonth(1), today),
                new Preset("Last 30 days", today.minusDays(29), today),
                new Preset("This year", today.withDayOfYear(1), today),
                new Preset("Last year", lastYear.withDayOfYear(1), lastYear.withDayOfYear(lastYear.lengthOfYear())));
    }

    /** Invalid filters: the HTML report page already explains the error, so send the same parameters there. */
    private ResponseEntity<byte[]> backToReportPage(HttpServletRequest request) {
        UriComponentsBuilder location = UriComponentsBuilder.fromPath(request.getContextPath() + "/reports");
        request.getParameterMap().forEach((name, values) -> {
            if (!name.equals("download")) {
                location.queryParam(name, (Object[]) values);
            }
        });
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, location.encode().build().toUriString())
                .build();
    }
}
