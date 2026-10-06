package com.pharmacy.controller;

import com.pharmacy.config.AppProperties;
import com.pharmacy.entity.StoreSettings;
import com.pharmacy.service.BackupService;
import com.pharmacy.service.FileStorageService;
import com.pharmacy.service.SettingsService;
import com.pharmacy.util.Fmt;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Store details (name, logo, address, mobiles...) and configuration (currency, VAT on/off, language...). */
@Controller
@RequestMapping("/settings")
public class SettingsController {
    private final SettingsService settings;
    private final FileStorageService files;
    private final BackupService backup;
    private final Fmt fmt;
    private final AppProperties props;

    public SettingsController(SettingsService settings, FileStorageService files, BackupService backup, Fmt fmt,
                              AppProperties props) {
        this.settings = settings;
        this.files = files;
        this.backup = backup;
        this.fmt = fmt;
        this.props = props;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("showTaxFields", props.store().showTaxFields());
        return "settings";
    }

    @PostMapping
    public String save(@ModelAttribute("form") StoreSettings form,
                       @RequestParam(required = false) MultipartFile logo,
                       @RequestParam(defaultValue = "false") boolean removeLogo,
                       RedirectAttributes ra) {
        StoreSettings current = settings.get();
        String logoPath = files.storeImage(logo, "logo");
        if (logoPath != null) form.setLogoPath(logoPath);
        else if (removeLogo) form.setLogoPath(null);
        else form.setLogoPath(current.getLogoPath());
        if (!props.store().showTaxFields()) {
            // Fields are hidden, so they are not submitted: keep the stored values.
            form.setTaxNumber(current.getTaxNumber());
            form.setCommercialRegister(current.getCommercialRegister());
        }
        if (form.getTaxRate() == null) form.setTaxRate(current.getTaxRate());
        if (form.getCurrency() == null || form.getCurrency().isBlank()) form.setCurrency("EGP");
        settings.save(form);
        ra.addFlashAttribute("success", fmt.msg("common.saved"));
        return "redirect:/settings";
    }

    @GetMapping("/backup")
    public ResponseEntity<byte[]> backup() {
        String name = "store_backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".sql";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(backup.dump());
    }
}
