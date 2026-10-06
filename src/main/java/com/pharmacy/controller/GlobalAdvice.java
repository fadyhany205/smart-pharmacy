package com.pharmacy.controller;

import com.pharmacy.config.AppProperties;
import com.pharmacy.entity.StoreSettings;
import com.pharmacy.service.BusinessException;
import com.pharmacy.service.SettingsService;
import com.pharmacy.util.Fmt;
import com.pharmacy.util.L10n;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Map;

/** Adds common model attributes to every page and turns BusinessException into a friendly message. */
@ControllerAdvice
public class GlobalAdvice {
    private final SettingsService settings;
    private final Fmt fmt;
    private final AppProperties props;

    public GlobalAdvice(SettingsService settings, Fmt fmt, AppProperties props) {
        this.settings = settings;
        this.fmt = fmt;
        this.props = props;
    }

    @ModelAttribute("settings")
    public StoreSettings settings() {
        return settings.get();
    }

    @ModelAttribute("vendor")
    public AppProperties.Vendor vendor() {
        return props.vendor();
    }

    @ModelAttribute("rtl")
    public boolean rtl() {
        return L10n.isArabic();
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ExceptionHandler(BusinessException.class)
    public Object handleBusiness(BusinessException e, HttpServletRequest request) {
        String message = fmt.msg(e.getMessage(), e.getArgs());
        if (request.getRequestURI().startsWith("/api/")) {
            return ResponseEntity.badRequest().body(Map.of("error", message));
        }
        RequestContextUtils.getOutputFlashMap(request).put("error", message);
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/");
    }
}
