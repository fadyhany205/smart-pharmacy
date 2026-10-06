package com.pharmacy.controller;

import com.pharmacy.entity.ReturnSettlement;
import com.pharmacy.entity.Sale;
import com.pharmacy.repository.SaleRepository;
import com.pharmacy.service.BusinessException;
import com.pharmacy.service.PdfService;
import com.pharmacy.service.ReturnService;
import com.pharmacy.service.SaleService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/sales")
public class SaleController {
    private final SaleRepository sales;
    private final SaleService service;
    private final ReturnService returns;
    private final PdfService pdf;
    private final Fmt fmt;

    public SaleController(SaleRepository sales, SaleService service, ReturnService returns, PdfService pdf, Fmt fmt) {
        this.sales = sales;
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
        model.addAttribute("page", sales.search(query, range.start(), range.end(),
                PageRequest.of(page, 25, Sort.by(Sort.Direction.DESC, "date"))));
        model.addAttribute("q", q);
        model.addAttribute("range", range);
        return "sales/list";
    }

    /** Find an invoice by number (typed or scanned from the receipt barcode). */
    @GetMapping("/find")
    public String find(@RequestParam String invoiceNo, @RequestParam(defaultValue = "false") boolean forReturn) {
        Sale s = sales.findByInvoiceNo(invoiceNo.trim()).orElseThrow(() -> new BusinessException("error.invoiceNotFound", invoiceNo));
        return "redirect:/sales/" + s.getId() + (forReturn ? "/return" : "");
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("sale", service.get(id));
        return "sales/view";
    }

    /** Printable invoice: format = thermal (80mm receipt) or a4. */
    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, @RequestParam(defaultValue = "thermal") String format,
                        @RequestParam(defaultValue = "false") boolean auto, Model model) {
        model.addAttribute("sale", service.get(id));
        model.addAttribute("auto", auto);
        return "a4".equals(format) ? "sales/print-a4" : "sales/print-thermal";
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        Sale s = service.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(s.getInvoiceNo() + ".pdf").build().toString())
                .body(pdf.salePdf(s));
    }

    @GetMapping("/{id}/return")
    public String returnForm(@PathVariable Long id, Model model) {
        Sale sale = service.get(id);
        model.addAttribute("sale", sale);
        model.addAttribute("outstanding", returns.outstanding(sale));
        return "sales/return";
    }

    /** Form fields: qty_{saleItemId}=quantity, settlement=AUTO|BALANCE|CASH */
    @PostMapping("/{id}/return")
    public String doReturn(@PathVariable Long id, @RequestParam Map<String, String> params,
                           @RequestParam(defaultValue = "AUTO") ReturnSettlement settlement,
                           @RequestParam(required = false) String reason, RedirectAttributes ra) {
        Map<Long, BigDecimal> qty = parseQuantities(params);
        var r = returns.returnSale(id, qty, settlement, reason);
        ra.addFlashAttribute("success", fmt.msg("common.saved"));
        return "redirect:/returns/" + r.getId();
    }

    static Map<Long, BigDecimal> parseQuantities(Map<String, String> params) {
        Map<Long, BigDecimal> qty = new HashMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("qty_") && StringUtils.hasText(v)) {
                try {
                    qty.put(Long.parseLong(k.substring(4)), new BigDecimal(v.trim()));
                } catch (NumberFormatException e) {
                    throw new BusinessException("error.invalidQty");
                }
            }
        });
        return qty;
    }
}
