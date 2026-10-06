package com.pharmacy.controller;

import com.pharmacy.dto.ReportTable;
import com.pharmacy.service.BusinessException;
import com.pharmacy.service.ExcelService;
import com.pharmacy.service.PdfService;
import com.pharmacy.service.ReportService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/reports")
public class ReportController {
    private final ReportService reports;
    private final PdfService pdf;
    private final ExcelService excel;

    public ReportController(ReportService reports, PdfService pdf, ExcelService excel) {
        this.reports = reports;
        this.pdf = pdf;
        this.excel = excel;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("reports", ReportService.REPORTS);
        return "reports/index";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable String id,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        var def = reports.find(id).orElseThrow(() -> new BusinessException("error.notFound"));
        DateRange range = DateRange.of(from, to);
        ReportTable report = reports.build(id, range.from(), range.to());
        model.addAttribute("def", def);
        model.addAttribute("range", range);
        model.addAttribute("report", report);
        // Screen shows PAGE_SIZE rows per page; totals/summary still cover all rows; PDF/Excel export everything.
        List<List<Object>> rows = report.getRows();
        int size = PAGE_SIZE;
        int last = Math.max(0, (rows.size() - 1) / size);
        int p = Math.min(Math.max(page, 0), last);
        List<List<Object>> slice = rows.subList(Math.min(p * size, rows.size()), Math.min((p + 1) * size, rows.size()));
        model.addAttribute("rowsPage", new PageImpl<>(slice, PageRequest.of(p, size), rows.size()));
        model.addAttribute("pageSize", size);
        return "reports/view";
    }

    private static final int PAGE_SIZE = 100;

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String id,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var def = reports.find(id).orElseThrow(() -> new BusinessException("error.notFound"));
        DateRange range = DateRange.of(from, to);
        ReportTable t = reports.build(id, range.from(), range.to());
        byte[] body = pdf.reportPdf(t, def.dated() ? range.from() : null, range.to());
        return file(body, id + ".pdf", MediaType.APPLICATION_PDF, true);
    }

    @GetMapping("/{id}/excel")
    public ResponseEntity<byte[]> excel(@PathVariable String id,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var def = reports.find(id).orElseThrow(() -> new BusinessException("error.notFound"));
        DateRange range = DateRange.of(from, to);
        ReportTable t = reports.build(id, range.from(), range.to());
        byte[] body = excel.report(t, def.dated() ? range.from() : null, range.to());
        return file(body, id + ".xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), false);
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name, MediaType type, boolean inline) {
        ContentDisposition cd = (inline ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(name).build();
        return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION, cd.toString()).body(body);
    }
}
