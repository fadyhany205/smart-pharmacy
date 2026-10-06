package com.pharmacy.controller;

import com.pharmacy.dto.PartyLookup;
import com.pharmacy.dto.ProductLookup;
import com.pharmacy.dto.PurchaseRequest;
import com.pharmacy.dto.SaleRequest;
import com.pharmacy.entity.Purchase;
import com.pharmacy.entity.Sale;
import com.pharmacy.repository.BatchRepository;
import com.pharmacy.repository.CustomerRepository;
import com.pharmacy.repository.ProductRepository;
import com.pharmacy.repository.SupplierRepository;
import com.pharmacy.service.PurchaseService;
import com.pharmacy.service.SaleService;
import com.pharmacy.util.Fmt;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** JSON endpoints used by the POS and purchase screens. */
@RestController
@RequestMapping("/api")
public class ApiController {
    private final ProductRepository products;
    private final SaleService sales;
    private final PurchaseService purchases;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final Fmt fmt;
    private final BatchRepository batches;

    public ApiController(ProductRepository products, SaleService sales, PurchaseService purchases,
                         CustomerRepository customers, SupplierRepository suppliers, Fmt fmt, BatchRepository batches) {
        this.batches = batches;
        this.products = products;
        this.sales = sales;
        this.purchases = purchases;
        this.customers = customers;
        this.suppliers = suppliers;
        this.fmt = fmt;
    }

    @GetMapping("/products/barcode/{code}")
    public ResponseEntity<?> byBarcode(@PathVariable String code) {
        return products.findByBarcode(code.trim())
                .filter(p -> p.isActive())
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(lookup(p)))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", fmt.msg("pos.notFound", code))));
    }

    /** Search by trade name, scientific name (active ingredient), barcode or code. */
    @GetMapping("/products/search")
    public List<ProductLookup> search(@RequestParam String q) {
        if (q.isBlank()) return List.of();
        return products.quickSearch(q.trim(), PageRequest.of(0, 15)).stream().map(this::lookup).toList();
    }

    /** Other products with the same active ingredient that have sellable stock (for out-of-stock items). */
    @GetMapping("/products/{id}/substitutes")
    public List<ProductLookup> substitutes(@PathVariable Long id) {
        var p = products.findById(id).orElse(null);
        if (p == null || p.getScientificName() == null || p.getScientificName().isBlank()) return List.of();
        return products.findSubstitutes(p.getScientificName().trim(), id, PageRequest.of(0, 20)).stream()
                .map(this::lookup).filter(l -> l.sellableQty().signum() > 0).toList();
    }

    private ProductLookup lookup(com.pharmacy.entity.Product p) {
        LocalDate today = LocalDate.now();
        return ProductLookup.of(p, batches.sellableQty(p.getId(), today), batches.nearestExpiry(p.getId(), today), fmt);
    }

    /** Up to 15 customers matching name or phone. */
    @GetMapping("/customers/search")
    public List<PartyLookup> searchCustomers(@RequestParam String q) {
        if (q.isBlank()) return List.of();
        return customers.search(q.trim(), PageRequest.of(0, 15, Sort.by("name"))).map(PartyLookup::of).getContent();
    }

    /** Up to 15 suppliers matching name or phone (managers only, see SecurityConfig). */
    @GetMapping("/suppliers/search")
    public List<PartyLookup> searchSuppliers(@RequestParam String q) {
        if (q.isBlank()) return List.of();
        return suppliers.search(q.trim(), PageRequest.of(0, 15, Sort.by("name"))).map(PartyLookup::of).getContent();
    }

    @PostMapping("/sales")
    public Map<String, Object> createSale(@RequestBody SaleRequest request) {
        Sale s = sales.create(request);
        return Map.of("id", s.getId(), "invoiceNo", s.getInvoiceNo(), "total", s.getTotal(), "change", s.getChange());
    }

    @PostMapping("/purchases")
    public Map<String, Object> createPurchase(@RequestBody PurchaseRequest request) {
        Purchase p = purchases.create(request);
        return Map.of("id", p.getId(), "invoiceNo", p.getInvoiceNo());
    }
}
