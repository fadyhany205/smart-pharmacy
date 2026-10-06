package com.pharmacy.controller;

import com.pharmacy.entity.PartyType;
import com.pharmacy.entity.ReturnInvoice;
import com.pharmacy.repository.ReturnInvoiceRepository;
import com.pharmacy.service.ReturnService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/returns")
public class ReturnController {
    private final ReturnInvoiceRepository returns;
    private final ReturnService service;

    public ReturnController(ReturnInvoiceRepository returns, ReturnService service) {
        this.returns = returns;
        this.service = service;
    }

    /** type = sale (default) or purchase. */
    @GetMapping
    public String list(@RequestParam(defaultValue = "sale") String type,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page, Model model, Authentication auth) {
        boolean purchase = "purchase".equals(type);
        if (purchase && !isManager(auth)) throw new AccessDeniedException("forbidden");
        DateRange range = DateRange.of(from, to);
        model.addAttribute("page", returns.search(purchase ? PartyType.SUPPLIER : PartyType.CUSTOMER,
                range.start(), range.end(), PageRequest.of(page, 25, Sort.by(Sort.Direction.DESC, "date"))));
        model.addAttribute("type", purchase ? "purchase" : "sale");
        model.addAttribute("range", range);
        return "returns/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean print, Model model, Authentication auth) {
        ReturnInvoice r = service.get(id);
        if (!r.isSalesReturn() && !isManager(auth)) throw new AccessDeniedException("forbidden");
        model.addAttribute("ret", r);
        return print ? "returns/print" : "returns/view";
    }

    private static boolean isManager(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
    }
}
