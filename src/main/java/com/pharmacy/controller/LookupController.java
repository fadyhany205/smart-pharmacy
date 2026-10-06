package com.pharmacy.controller;

import com.pharmacy.entity.Category;
import com.pharmacy.entity.Unit;
import com.pharmacy.repository.CategoryRepository;
import com.pharmacy.repository.UnitRepository;
import com.pharmacy.util.Fmt;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Categories and units: simple bilingual lookup tables sharing one template. */
@Controller
public class LookupController {
    private final CategoryRepository categories;
    private final UnitRepository units;
    private final Fmt fmt;

    public LookupController(CategoryRepository categories, UnitRepository units, Fmt fmt) {
        this.categories = categories;
        this.units = units;
        this.fmt = fmt;
    }

    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("items", categories.findAll());
        model.addAttribute("base", "/categories");
        model.addAttribute("titleKey", "nav.categories");
        return "lookups";
    }

    @GetMapping("/units")
    public String units(Model model) {
        model.addAttribute("items", units.findAll());
        model.addAttribute("base", "/units");
        model.addAttribute("titleKey", "nav.units");
        return "lookups";
    }

    @PostMapping("/categories/save")
    public String saveCategory(@RequestParam(required = false) Long id, @RequestParam String nameAr,
                               @RequestParam(required = false) String nameEn, RedirectAttributes ra) {
        Category c = id == null ? new Category() : categories.findById(id).orElseThrow();
        c.setNameAr(nameAr);
        c.setNameEn(nameEn);
        categories.save(c);
        ra.addFlashAttribute("success", fmt.msg("common.saved"));
        return "redirect:/categories";
    }

    @PostMapping("/units/save")
    public String saveUnit(@RequestParam(required = false) Long id, @RequestParam String nameAr,
                           @RequestParam(required = false) String nameEn, RedirectAttributes ra) {
        Unit u = id == null ? new Unit() : units.findById(id).orElseThrow();
        u.setNameAr(nameAr);
        u.setNameEn(nameEn);
        units.save(u);
        ra.addFlashAttribute("success", fmt.msg("common.saved"));
        return "redirect:/units";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes ra) {
        try {
            categories.deleteById(id);
            categories.flush();
            ra.addFlashAttribute("success", fmt.msg("common.deleted"));
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", fmt.msg("error.inUse"));
        }
        return "redirect:/categories";
    }

    @PostMapping("/units/{id}/delete")
    public String deleteUnit(@PathVariable Long id, RedirectAttributes ra) {
        try {
            units.deleteById(id);
            units.flush();
            ra.addFlashAttribute("success", fmt.msg("common.deleted"));
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", fmt.msg("error.inUse"));
        }
        return "redirect:/units";
    }
}
