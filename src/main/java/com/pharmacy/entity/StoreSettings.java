package com.pharmacy.entity;

import com.pharmacy.util.L10n;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Single-row table (id = 1) holding the store details and configuration. */
@Entity
@Table(name = "store_settings")
@Getter @Setter
public class StoreSettings {
    @Id
    private Long id = 1L;

    // Store details
    @Column(length = 150) private String storeNameAr;
    @Column(length = 150) private String storeNameEn;
    @Column(length = 255) private String addressAr;
    @Column(length = 255) private String addressEn;
    @Column(length = 30) private String mobile1;
    @Column(length = 30) private String mobile2;
    @Column(length = 30) private String phone;
    @Column(length = 100) private String email;
    @Column(length = 50) private String taxNumber;
    @Column(length = 50) private String commercialRegister;
    @Column(length = 255) private String logoPath;

    // Configuration
    @Column(nullable = false, length = 10) private String currency = "EGP";
    private boolean taxEnabled = true;
    @Column(nullable = false, precision = 5, scale = 2) private BigDecimal taxRate = new BigDecimal("14");
    @Column(nullable = false, length = 5) private String defaultLanguage = "ar";
    private boolean allowNegativeStock = false;
    /** Nullable so existing databases upgrade cleanly; null means WEIGHTED_AVERAGE. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20) private CostMethod costMethod = CostMethod.WEIGHTED_AVERAGE;
    @Column(length = 255) private String invoiceFooterAr;
    @Column(length = 255) private String invoiceFooterEn;

    public CostMethod getCostMethod() { return costMethod == null ? CostMethod.WEIGHTED_AVERAGE : costMethod; }

    @Transient public String getStoreName() { return L10n.pick(storeNameAr, storeNameEn); }
    @Transient public String getAddress() { return L10n.pick(addressAr, addressEn); }
    @Transient public String getInvoiceFooter() { return L10n.pick(invoiceFooterAr, invoiceFooterEn); }

    /** Mobiles and phone joined for printing, e.g. "0100... - 0122... - 02...". */
    @Transient
    public String getPhones() {
        StringBuilder sb = new StringBuilder();
        for (String p : new String[]{mobile1, mobile2, phone}) {
            if (p != null && !p.isBlank()) {
                if (!sb.isEmpty()) sb.append(" - ");
                sb.append(p);
            }
        }
        return sb.toString();
    }
}
