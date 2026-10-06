package com.pharmacy.controller;

import com.pharmacy.service.BarcodeService;
import com.pharmacy.service.DashboardService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.concurrent.TimeUnit;

@Controller
public class HomeController {
    private final DashboardService dashboard;
    private final BarcodeService barcodes;

    public HomeController(DashboardService dashboard, BarcodeService barcodes) {
        this.dashboard = dashboard;
        this.barcodes = barcodes;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAllAttributes(dashboard.stats());
        return "dashboard";
    }

    @GetMapping("/pos")
    public String pos() {
        return "pos";   // customers are searched through /api/customers/search
    }

    /** Barcode image, e.g. /barcode/2000000000015.png?w=300&amp;h=80 */
    @GetMapping("/barcode/{code}.png")
    public ResponseEntity<byte[]> barcode(@PathVariable String code,
                                          @RequestParam(defaultValue = "300") int w,
                                          @RequestParam(defaultValue = "80") int h) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS))
                .body(barcodes.png(code, Math.min(w, 1200), Math.min(h, 600)));
    }
}
