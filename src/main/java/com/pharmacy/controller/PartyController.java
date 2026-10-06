package com.pharmacy.controller;

import com.pharmacy.entity.*;
import com.pharmacy.repository.CustomerRepository;
import com.pharmacy.repository.SupplierRepository;
import com.pharmacy.service.PartyService;
import com.pharmacy.util.Fmt;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/** Customers (/customers) and suppliers (/suppliers) share the same pages. */
@Controller
@RequestMapping("/{kind:customers|suppliers}")
public class PartyController {
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final PartyService service;
    private final Fmt fmt;

    public PartyController(CustomerRepository customers, SupplierRepository suppliers, PartyService service, Fmt fmt) {
        this.customers = customers;
        this.suppliers = suppliers;
        this.service = service;
        this.fmt = fmt;
    }

    private static PartyType type(String kind) {
        return "customers".equals(kind) ? PartyType.CUSTOMER : PartyType.SUPPLIER;
    }

    private void common(String kind, Model model) {
        model.addAttribute("kind", kind);
        model.addAttribute("isCustomer", "customers".equals(kind));
        model.addAttribute("base", "/" + kind);
    }

    @GetMapping
    public String list(@PathVariable String kind, @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        common(kind, model);
        String query = StringUtils.hasText(q) ? q.trim() : null;
        PageRequest pr = PageRequest.of(page, 25, Sort.by("name"));
        model.addAttribute("page", type(kind) == PartyType.CUSTOMER ? customers.search(query, pr) : suppliers.search(query, pr));
        model.addAttribute("q", q);
        return "parties/list";
    }

    @GetMapping("/new")
    public String create(@PathVariable String kind, Model model) {
        common(kind, model);
        model.addAttribute("party", type(kind) == PartyType.CUSTOMER ? new Customer() : new Supplier());
        return "parties/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable String kind, @PathVariable Long id, Model model) {
        common(kind, model);
        model.addAttribute("party", service.get(type(kind), id));
        return "parties/form";
    }

    @PostMapping("/save")
    public String save(@PathVariable String kind, @RequestParam(required = false) Long id, @RequestParam String name,
                       @RequestParam(required = false) String phone, @RequestParam(required = false) String address,
                       @RequestParam(required = false) String taxNumber, @RequestParam(required = false) String notes,
                       @RequestParam(required = false) BigDecimal balance, RedirectAttributes ra) {
        Party form = type(kind) == PartyType.CUSTOMER ? new Customer() : new Supplier();
        form.setId(id);
        form.setName(name);
        form.setPhone(phone);
        form.setAddress(address);
        form.setTaxNumber(taxNumber);
        form.setNotes(notes);
        form.setBalance(balance == null ? BigDecimal.ZERO : balance);
        service.save(type(kind), form);
        ra.addFlashAttribute("success", fmt.msg("common.saved"));
        return "redirect:/" + kind;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String kind, @PathVariable Long id, RedirectAttributes ra) {
        service.delete(type(kind), id);
        ra.addFlashAttribute("success", fmt.msg("common.deleted"));
        return "redirect:/" + kind;
    }

    @GetMapping("/{id}/statement")
    public String statement(@PathVariable String kind, @PathVariable Long id, Model model) {
        common(kind, model);
        model.addAttribute("party", service.get(type(kind), id));
        model.addAttribute("lines", service.statement(type(kind), id));
        return "parties/statement";
    }

    @PostMapping("/{id}/payment")
    public String payment(@PathVariable String kind, @PathVariable Long id, @RequestParam BigDecimal amount,
                          @RequestParam(defaultValue = "CASH") PaymentMethod method,
                          @RequestParam(required = false) String note,
                          @RequestParam(defaultValue = "false") boolean refund, RedirectAttributes ra) {
        service.addPayment(type(kind), id, amount, method, note, refund);
        ra.addFlashAttribute("success", fmt.msg("common.saved"));
        return "redirect:/" + kind + "/" + id + "/statement";
    }
}
