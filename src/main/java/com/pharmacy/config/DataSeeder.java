package com.pharmacy.config;

import com.pharmacy.entity.*;
import com.pharmacy.repository.*;
import com.pharmacy.service.ProductService;
import com.pharmacy.service.SettingsService;
import com.pharmacy.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Creates the admin account, settings and basic lookup data on the first run. */
@Component
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final UnitRepository units;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final ProductService productService;
    private final SettingsService settings;
    private final PasswordEncoder encoder;
    private final AppProperties props;
    private final StockService stock;

    public DataSeeder(UserRepository users, UnitRepository units, CategoryRepository categories,
                      ProductRepository products, CustomerRepository customers, SupplierRepository suppliers,
                      ProductService productService, SettingsService settings, PasswordEncoder encoder,
                      AppProperties props, StockService stock) {
        this.users = users;
        this.units = units;
        this.categories = categories;
        this.products = products;
        this.customers = customers;
        this.suppliers = suppliers;
        this.productService = productService;
        this.settings = settings;
        this.encoder = encoder;
        this.props = props;
        this.stock = stock;
    }

    @Override
    public void run(String... args) {
        settings.get();

        if (users.count() == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(encoder.encode("admin123"));
            admin.setFullName("مدير النظام");
            admin.setRole(Role.ADMIN);
            users.save(admin);
            log.warn("Created default user admin / admin123 - change the password from the Users page.");
        }

        if (units.count() == 0) {
            // Smallest units of pharmacy items (the pack is always "box" / "strip").
            unit("قرص", "Tablet");
            unit("كبسولة", "Capsule");
            unit("أمبول", "Ampoule");
            unit("زجاجة", "Bottle");
            unit("كيس", "Sachet");
            unit("أنبوب", "Tube");
            unit("قطعة", "Piece");
        }

        if (categories.count() == 0) {
            category("أدوية", "Medicines");
            category("مستحضرات تجميل", "Cosmetics");
            category("مستلزمات طبية", "Medical supplies");
        }

        if (props.seedDemoData() && products.count() == 0) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null, List.of()));
            List<Category> cats = categories.findAll();
            Category meds = cats.get(0), cosmetics = cats.get(1), supplies = cats.get(2);
            List<Unit> us = units.findAll();
            Unit tablet = us.get(0), capsule = us.get(1), bottle = us.get(3), piece = us.get(6);
            LocalDate today = LocalDate.now();

            // name, en, scientific, strength, form, maker, category, unit, strips, units/strip, cost/box, price/box,
            // opening boxes, batch, expiry, min boxes, rx, fridge, taxable
            medicine("بانادول 500 مجم", "Panadol 500mg", "Paracetamol", "500mg", "أقراص", "GSK", meds, tablet,
                    2, 12, "30.00", "40.00", "20", "PN2407", today.plusMonths(18), "5", false, false, false);
            medicine("بارامول 500 مجم", "Paramol 500mg", "Paracetamol", "500mg", "أقراص", "Pharco", meds, tablet,
                    2, 10, "14.00", "19.50", "30", "PR1123", today.plusMonths(14), "5", false, false, false);
            medicine("بروفين 400 مجم", "Brufen 400mg", "Ibuprofen", "400mg", "أقراص", "Abbott", meds, tablet,
                    3, 10, "33.00", "45.00", "10", "BR0551", today.plusDays(45), "3", false, false, false);
            medicine("أوجمنتين 1 جم", "Augmentin 1g", "Amoxicillin + Clavulanic acid", "1g", "أقراص", "GSK", meds, tablet,
                    2, 7, "135.00", "178.00", "6", "AU7710", today.plusMonths(10), "2", true, false, false);
            medicine("كتافلام 50 مجم", "Cataflam 50mg", "Diclofenac potassium", "50mg", "أقراص", "Novartis", meds, tablet,
                    2, 10, "38.00", "51.00", "8", "CT3302", today.plusMonths(20), "2", false, false, false);
            medicine("نيكسيوم 40 مجم", "Nexium 40mg", "Esomeprazole", "40mg", "كبسولات", "AstraZeneca", meds, capsule,
                    2, 7, "120.00", "159.00", "4", "NX1801", today.plusMonths(8), "2", false, false, false);
            medicine("أنسولين ميكستارد", "Mixtard insulin", "Insulin human", "100 IU/ml", "حقن", "Novo Nordisk", meds, piece,
                    1, 1, "95.00", "120.00", "5", "MX0921", today.plusMonths(6), "2", true, true, false);
            medicine("كونجستال شراب", "Congestal syrup", "Paracetamol + Pseudoephedrine + Chlorpheniramine", "120ml", "شراب", "Sigma", meds, bottle,
                    1, 1, "22.00", "31.00", "0", null, null, "3", false, false, false);   // out of stock -> substitutes
            medicine("واقي شمس SPF50", "Sunscreen SPF50", null, "50ml", "كريم", "La Roche-Posay", cosmetics, piece,
                    1, 1, "420.00", "560.00", "4", null, null, "1", false, false, true);   // taxable cosmetic
            medicine("جهاز قياس حرارة", "Digital thermometer", null, null, null, "Omron", supplies, piece,
                    1, 1, "85.00", "120.00", "3", null, null, "1", false, false, true);

            // An expired batch (to show the expiry screen and write-off).
            Product brufen = products.findAll().stream().filter(x -> "Brufen 400mg".equals(x.getNameEn())).findFirst().orElseThrow();
            stock.receive(brufen, "BR0199", today.minusDays(20), BigDecimal.valueOf(2L * brufen.getFactor()),
                    brufen.getCostPrice(), MovementType.OPENING, null, null, null);

            Customer c = new Customer();
            c.setName("عميل تجريبي");
            c.setPhone("01000000000");
            customers.save(c);
            Supplier s = new Supplier();
            s.setName("شركة الأدوية التجريبية");
            s.setPhone("01100000000");
            suppliers.save(s);
            SecurityContextHolder.clearContext();
            log.info("Demo data created.");
        }
    }

    private void unit(String ar, String en) {
        Unit u = new Unit();
        u.setNameAr(ar);
        u.setNameEn(en);
        units.save(u);
    }

    private Category category(String ar, String en) {
        Category c = new Category();
        c.setNameAr(ar);
        c.setNameEn(en);
        return categories.save(c);
    }

    private void medicine(String ar, String en, String scientific, String strength, String form, String maker,
                          Category cat, Unit unit, int strips, int perStrip, String cost, String price,
                          String boxes, String batchNo, LocalDate expiry, String min,
                          boolean rx, boolean fridge, boolean taxable) {
        Product p = new Product();
        p.setNameAr(ar);
        p.setNameEn(en);
        p.setScientificName(scientific);
        p.setStrength(strength);
        p.setDosageForm(form);
        p.setManufacturer(maker);
        p.setCategory(cat);
        p.setUnit(unit);
        p.setStripsPerBox(strips);
        p.setUnitsPerStrip(perStrip);
        p.setCostPrice(new BigDecimal(cost));
        p.setSalePrice(new BigDecimal(price));
        p.setMinStock(new BigDecimal(min));
        p.setPrescriptionRequired(rx);
        p.setNeedsFridge(fridge);
        p.setTaxable(taxable);
        p.setShelfLocation(fridge ? "ثلاجة" : null);
        p.setActive(true);
        productService.save(p, new BigDecimal(boxes), batchNo, expiry, null);
    }
}
