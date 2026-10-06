package com.pharmacy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/** Values from the "app" section of application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Store store,
        String currency,
        Tax tax,
        String defaultLanguage,
        boolean allowNegativeStock,
        String uploadDir,
        Backup backup,
        Pdf pdf,
        boolean seedDemoData,
        Vendor vendor) {

    /** The company that built the system, shown in the footer and on report printouts. */
    public record Vendor(String name, String phone) {}

    /** showTaxFields: show "tax number" and "commercial register" on the Settings page. */
    public record Store(String nameAr, String nameEn, boolean showTaxFields) {}

    public record Tax(boolean enabled, BigDecimal rate) {}

    public record Backup(String mysqldumpPath) {}

    public record Pdf(List<String> fonts) {}
}
