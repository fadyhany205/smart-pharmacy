package com.pharmacy.controller;

import com.pharmacy.entity.Category;
import com.pharmacy.entity.Product;
import com.pharmacy.entity.Unit;
import com.pharmacy.repository.CategoryRepository;
import com.pharmacy.repository.ProductRepository;
import com.pharmacy.repository.UnitRepository;
import com.pharmacy.service.ProductService;
import com.pharmacy.util.Fmt;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/products")
public class ProductController {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final UnitRepository units;
    private final ProductService service;
    private final Fmt fmt;

    public ProductController(ProductRepository products, CategoryRepository categories, UnitRepository units,
                             ProductService service, Fmt fmt) {
        this.products = products;
        this.categories = categories;
        this.units = units;
        this.service = service;
        this.fmt = fmt;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(defaultValue = "false") boolean low,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        String query = StringUtils.hasText(q) ? q.trim() : null;
        model.addAttribute("page", products.search(query, categoryId, low, PageRequest.of(page, 25, Sort.by("nameAr"))));
        model.addAttribute("categories", categories.findAll());
        model.addAttribute("q", q);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("low", low);
        return "products/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        Product p = new Product();
        return form(p, model);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        return form(service.get(id), model);
    }

    private String form(Product p, Model model) {
        // Empty placeholders so th:field="*{category.id}" works for products without category/unit.
        // (Not flushed: no transaction is open while rendering.)
        if (p.getCategory() == null) p.setCategory(new Category());
        if (p.getUnit() == null) p.setUnit(new Unit());
        model.addAttribute("product", p);
        model.addAttribute("categories", categories.findAll());
        model.addAttribute("units", units.findAll());
        return "products/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Product product,
                       @RequestParam(required = false) BigDecimal openingQty,
                       @RequestParam(required = false) String openingBatch,
                       @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate openingExpiry,
                       @RequestParam(required = false) MultipartFile image,
                       @RequestParam(defaultValue = "false") boolean saveAndNew,
                       RedirectAttributes ra) {
        Product saved = service.save(product, openingQty, openingBatch, openingExpiry, image);
        ra.addFlashAttribute("success", fmt.msg("common.saved"));
        return saveAndNew ? "redirect:/products/new" : "redirect:/products?q=" + saved.getBarcode();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        boolean deleted = service.deleteOrDeactivate(id);
        ra.addFlashAttribute("success", fmt.msg(deleted ? "common.deleted" : "product.deactivated"));
        return "redirect:/products";
    }

    /** Label selection page. */
    @GetMapping("/labels")
    public String labels(@RequestParam(required = false) String q,
                         @RequestParam(required = false) Long categoryId,
                         @RequestParam(defaultValue = "0") int page, Model model) {
        String query = StringUtils.hasText(q) ? q.trim() : null;
        model.addAttribute("page", products.search(query, categoryId, false, PageRequest.of(page, 50, Sort.by("nameAr"))));
        model.addAttribute("categories", categories.findAll());
        model.addAttribute("q", q);
        model.addAttribute("categoryId", categoryId);
        return "products/labels";
    }

    /** Printable labels. productId/copies are parallel lists; a single product can be printed with ?productId=1&amp;copies=10. */
    @RequestMapping(value = "/labels/print", method = {RequestMethod.GET, RequestMethod.POST})
    public String printLabels(@RequestParam List<Long> productId,
                              @RequestParam List<Integer> copies,
                              @RequestParam(defaultValue = "38x25") String size,
                              @RequestParam(required = false) Boolean showPrice,
                              @RequestParam(required = false) Boolean showName,
                              @RequestParam(defaultValue = "false") boolean fromForm,
                              Model model) {
        // From the label form an unchecked box is simply missing; direct links show everything.
        List<Product> labels = new ArrayList<>();
        for (int i = 0; i < productId.size() && i < copies.size(); i++) {
            Integer n = copies.get(i);
            if (n == null || n <= 0) continue;
            Product p = service.get(productId.get(i));
            for (int c = 0; c < Math.min(n, 500); c++) labels.add(p);
        }
        model.addAttribute("labels", labels);
        model.addAttribute("size", size);
        model.addAttribute("showPrice", fromForm ? Boolean.TRUE.equals(showPrice) : showPrice == null || showPrice);
        model.addAttribute("showName", fromForm ? Boolean.TRUE.equals(showName) : showName == null || showName);
        return "products/labels-print";
    }
}
