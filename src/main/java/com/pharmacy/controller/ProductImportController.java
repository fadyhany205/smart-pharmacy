package com.pharmacy.controller;

import com.pharmacy.importer.DrugFileReader;
import com.pharmacy.importer.DrugImportService;
import com.pharmacy.importer.DrugMapper;
import com.pharmacy.importer.ImportJob;
import com.pharmacy.importer.SourceFormat;
import com.pharmacy.service.BusinessException;
import com.pharmacy.util.Fmt;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/** Import medicines from a drug list file (managers only, see SecurityConfig). */
@Controller
@RequestMapping("/products/import")
public class ProductImportController {
    private final DrugImportService imports;
    private final Fmt fmt;

    public ProductImportController(DrugImportService imports, Fmt fmt) {
        this.imports = imports;
        this.fmt = fmt;
    }

    @GetMapping
    public String page(@RequestParam(required = false) String job, Model model) {
        model.addAttribute("formats", DrugFileReader.FORMATS.stream().map(SourceFormat::displayName).toList());
        model.addAttribute("groups", DrugMapper.Group.values());
        ImportJob j = job != null ? imports.job(job) : imports.running();
        model.addAttribute("job", j);
        return "products/import";
    }

    @PostMapping
    public String start(@RequestParam("file") MultipartFile file,
                        @RequestParam(defaultValue = "false") boolean updateExisting,
                        @RequestParam(defaultValue = "false") boolean updatePrices,
                        @RequestParam(defaultValue = "false") boolean skipNoPrice,
                        @RequestParam(required = false) BigDecimal costPercent,
                        @RequestParam(required = false) List<DrugMapper.Group> taxableGroups,
                        @RequestParam(defaultValue = "false") boolean guessStrips,
                        @RequestParam(defaultValue = "false") boolean dryRun) throws Exception {
        if (file == null || file.isEmpty()) throw new BusinessException("import.error.noFile");
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "file");
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".csv") && !lower.endsWith(".json")) throw new BusinessException("import.error.fileType");
        if (costPercent != null && (costPercent.signum() < 0 || costPercent.compareTo(BigDecimal.valueOf(100)) > 0))
            throw new BusinessException("error.invalidPercent");

        Path tmp = Files.createTempFile("drug-import-", lower.endsWith(".json") ? ".json" : ".csv");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        Set<DrugMapper.Group> taxable = taxableGroups == null ? EnumSet.noneOf(DrugMapper.Group.class) : EnumSet.copyOf(taxableGroups);
        ImportJob job = imports.start(tmp, name, new ImportJob.Options(updateExisting, updatePrices, skipNoPrice,
                costPercent, taxable, guessStrips, dryRun));
        return "redirect:/products/import?job=" + job.id;
    }

    /** Progress of a running / finished import (JSON, polled every second by the page). */
    @GetMapping("/status/{id}")
    @ResponseBody
    public ResponseEntity<?> status(@PathVariable String id) {
        ImportJob j = imports.job(id);
        if (j == null) return ResponseEntity.notFound().build();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", j.status.name());
        m.put("format", j.format);
        m.put("file", j.fileName);
        m.put("dryRun", j.options.dryRun());
        m.put("percent", j.percent());
        m.put("total", j.total);
        m.put("processed", j.processed);
        m.put("created", j.created);
        m.put("updated", j.updated);
        m.put("skippedExisting", j.skippedExisting);
        m.put("skippedNoPrice", j.skippedNoPrice);
        m.put("skippedInvalid", j.skippedInvalid);
        m.put("barcodesFromFile", j.barcodesFromFile);
        m.put("barcodesGenerated", j.barcodesGenerated);
        m.put("duplicateBarcodes", j.duplicateBarcodes);
        m.put("categoriesCreated", j.categoriesCreated);
        m.put("unitsCreated", j.unitsCreated);
        m.put("message", j.message == null ? null : fmt.msg(j.message));
        m.put("errors", j.getErrors());
        m.put("seconds", java.time.Duration.between(j.startedAt,
                j.finishedAt == null ? java.time.LocalDateTime.now() : j.finishedAt).toSeconds());
        return ResponseEntity.ok(m);
    }
}
