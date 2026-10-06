package com.pharmacy.controller;

import com.pharmacy.entity.Purchase;
import com.pharmacy.entity.ReturnSettlement;
import com.pharmacy.repository.PurchaseRepository;
import com.pharmacy.repository.SupplierRepository;
import com.pharmacy.service.PdfService;
import com.pharmacy.service.PurchaseService;
import com.pharmacy.service.ReturnService;
import com.pharmacy.util.Fmt;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Map;

@Controller
@RequestMapping("/purchases")
public class PurchaseController {
    private final PurchaseRepository purchases;
    private final SupplierRepository suppliers;
    private final PurchaseService service;
    private final ReturnService returns;
    private final PdfService pdf;
    private final Fmt fmt;

    public PurchaseController(PurchaseRepository purchases, SupplierRepository suppliers, PurchaseService service,
                              ReturnService returns, PdfService pdf, Fmt fmt) {
        this.purchases = purchases;
        this.suppliers = suppliers;
        this.service = service;
        this.returns = returns;
        this.pdf = pdf;
        this.fmt = fmt;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        DateRange range = DateRange.of(from, to);
        String query = StringUtils.hasText(q) ? q.trim() : null;
        model.addAttribute("page", purchases.search(query, range.start(), range.end(),
                PageRequest.of(page, 25, Sort.by(Sort.Direction.DESC, "date"))));
        model.addAttribute("q", q);
        model.addAttribute("range", range);
        return "purchases/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("today", LocalDate.now());   // suppliers are searched through /api/suppliers/search
        return "purchases/form";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("purchase", service.get(id));
        return "purchases/view";
    }

    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, Model model) {
        model.addAttribute("purchase", service.get(id));
        return "purchases/print";
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        Purchase p = service.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(p.getInvoiceNo() + ".pdf").build().toString())
                .body(pdf.purchasePdf(p));
    }

    @GetMapping("/{id}/return")
    public String returnForm(@PathVariable Long id, Model model) {
        Purchase purchase = service.get(id);
        model.addAttribute("purchase", purchase);
        model.addAttribute("outstanding", returns.outstanding(purchase));
        return "purchases/return";
    }

    @PostMapping("/{id}/return")
    public String doReturn(@PathVariable Long id, @RequestParam Map<String, String> params,
                           @RequestParam(defaultValue = "AUTO") ReturnSettlement settlement,
                           @RequestParam(required = false) String reason, RedirectAttributes ra) {
        var r = returns.returnPurchase(id, SaleController.parseQuantities(params), settlement, reason);
        ra.addFlashAttribute("success", fmt.msg("common.saved"));
        return "redirect:/returns/" + r.getId();
    }
}
