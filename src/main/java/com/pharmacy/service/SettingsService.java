package com.pharmacy.service;

import com.pharmacy.config.AppProperties;
import com.pharmacy.entity.StoreSettings;
import com.pharmacy.repository.StoreSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class SettingsService {
    private final StoreSettingsRepository repository;
    private final AppProperties props;
    private volatile StoreSettings cached;

    public SettingsService(StoreSettingsRepository repository, AppProperties props) {
        this.repository = repository;
        this.props = props;
    }

    public StoreSettings get() {
        StoreSettings s = cached;
        if (s == null) {
            s = repository.findById(1L).orElseGet(this::createDefaults);
            cached = s;
        }
        return s;
    }

    @Transactional
    public StoreSettings save(StoreSettings s) {
        s.setId(1L);
        cached = repository.save(s);
        return cached;
    }

    public BigDecimal effectiveTaxRate() {
        StoreSettings s = get();
        return s.isTaxEnabled() ? s.getTaxRate() : BigDecimal.ZERO;
    }

    private StoreSettings createDefaults() {
        StoreSettings s = new StoreSettings();
        s.setStoreNameAr(props.store().nameAr());
        s.setStoreNameEn(props.store().nameEn());
        s.setCurrency(props.currency());
        s.setTaxEnabled(props.tax().enabled());
        s.setTaxRate(props.tax().rate());
        s.setDefaultLanguage(props.defaultLanguage());
        s.setAllowNegativeStock(props.allowNegativeStock());
        s.setInvoiceFooterAr("شكراً لزيارتكم");
        s.setInvoiceFooterEn("Thank you for your visit");
        return repository.save(s);
    }
}
