package com.pharmacy.importer;

import com.pharmacy.entity.Category;
import com.pharmacy.entity.Product;
import com.pharmacy.entity.Unit;
import com.pharmacy.repository.CategoryRepository;
import com.pharmacy.repository.ProductRepository;
import com.pharmacy.repository.UnitRepository;
import com.pharmacy.service.BarcodeService;
import com.pharmacy.service.BusinessException;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Imports a drug list into products in the background (one import at a time).
 * Existing products are matched by externalRef (source + id), then by barcode.
 * Saves in chunks of 300 rows, each in its own transaction, so memory stays low and a bad row
 * cannot roll back the whole file.
 */
@Service
public class DrugImportService {
    private static final Logger log = LoggerFactory.getLogger(DrugImportService.class);
    private static final int CHUNK = 300;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final UnitRepository units;
    private final EntityManager em;
    private final TransactionTemplate tx;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "drug-import");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, ImportJob> jobs = new ConcurrentHashMap<>();
    private volatile ImportJob current;

    public DrugImportService(ProductRepository products, CategoryRepository categories, UnitRepository units,
                             EntityManager em, PlatformTransactionManager txManager) {
        this.products = products;
        this.categories = categories;
        this.units = units;
        this.em = em;
        this.tx = new TransactionTemplate(txManager);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public ImportJob job(String id) {
        return id == null ? null : jobs.get(id);
    }

    public ImportJob running() {
        ImportJob c = current;
        return c != null && c.status == ImportJob.Status.RUNNING ? c : null;
    }

    /** Starts the import of an uploaded file (the file is deleted when done). */
    public synchronized ImportJob start(Path file, String fileName, ImportJob.Options options) {
        if (running() != null) throw new BusinessException("import.error.alreadyRunning");
        ImportJob job = new ImportJob(fileName, options);
        jobs.put(job.id, job);
        current = job;
        executor.submit(() -> {
            try {
                run(file, job);
                job.status = ImportJob.Status.DONE;
            } catch (BusinessException e) {
                job.status = ImportJob.Status.FAILED;
                job.message = e.getMessage();
                job.error(e.getMessage() + (e.getArgs().length > 0 ? ": " + e.getArgs()[0] : ""));
            } catch (Throwable e) {
                log.error("Drug import failed", e);
                job.status = ImportJob.Status.FAILED;
                job.message = "import.error.failed";
                job.error(String.valueOf(e.getMessage()));
            } finally {
                job.finishedAt = java.time.LocalDateTime.now();
                try { Files.deleteIfExists(file); } catch (Exception ignored) { }
            }
        });
        return job;
    }

    // ------------------------------------------------------------------ the import

    private void run(Path file, ImportJob job) throws Exception {
        DrugFileReader reader = new DrugFileReader(file);
        SourceFormat format = reader.detectFormat();
        job.format = format.displayName();
        job.total = reader.count();
        ImportJob.Options o = job.options;
        DrugMapper mapper = new DrugMapper(o.guessStrips());

        // Everything needed for matching, loaded once.
        Map<String, Long> byRef = new HashMap<>();
        for (Object[] r : products.allExternalRefs()) byRef.put((String) r[0], (Long) r[1]);
        Map<String, Long> byBarcode = new HashMap<>();
        for (Object[] r : products.allBarcodes()) byBarcode.put((String) r[0], (Long) r[1]);
        Set<String> usedBarcodes = new HashSet<>(byBarcode.keySet());
        Set<String> seenRefs = new HashSet<>();
        Map<DrugMapper.Group, Long> groupIds = o.dryRun() ? new EnumMap<>(DrugMapper.Group.class) : groups(job);
        Map<DrugMapper.UnitKind, Long> unitIds = o.dryRun() ? new EnumMap<>(DrugMapper.UnitKind.class) : unitKinds(job);
        long[] barcodeSeq = {1};

        List<DrugFileReader.Row> chunk = new ArrayList<>(CHUNK);
        reader.forEach(row -> {
            chunk.add(row);
            if (chunk.size() >= CHUNK) {
                processChunk(chunk, format, mapper, job, byRef, byBarcode, usedBarcodes, seenRefs, groupIds, unitIds, barcodeSeq);
                chunk.clear();
            }
        }, Long.MAX_VALUE);
        if (!chunk.isEmpty()) processChunk(chunk, format, mapper, job, byRef, byBarcode, usedBarcodes, seenRefs, groupIds, unitIds, barcodeSeq);
        log.info("Drug import {} done: created={} updated={} skipped={}", job.id, job.created, job.updated,
                job.skippedExisting + job.skippedNoPrice + job.skippedInvalid);
    }

    private void processChunk(List<DrugFileReader.Row> rows, SourceFormat format, DrugMapper mapper, ImportJob job,
                              Map<String, Long> byRef, Map<String, Long> byBarcode, Set<String> usedBarcodes,
                              Set<String> seenRefs, Map<DrugMapper.Group, Long> groupIds,
                              Map<DrugMapper.UnitKind, Long> unitIds, long[] barcodeSeq) {
        ImportJob.Options o = job.options;
        List<Runnable> writes = new ArrayList<>();
        Map<String, Long> newRefs = new HashMap<>();
        for (DrugFileReader.Row row : rows) {
            job.processed++;
            DrugRecord r;
            try {
                r = format.map(row.values());
            } catch (Exception e) {
                job.skippedInvalid++;
                job.error("#" + row.number() + ": " + e.getMessage());
                continue;
            }
            if (r.sourceId() == null || (r.nameEn() == null && r.nameAr() == null)) {
                job.skippedInvalid++;
                continue;
            }
            if (r.price() == null && o.skipNoPrice()) {
                job.skippedNoPrice++;
                continue;
            }
            String ref = format.code() + "-" + r.sourceId();
            if (!seenRefs.add(ref)) ref = ref + "-" + row.number();     // duplicate in the same file
            String barcode = validBarcode(r.barcode());

            Long existing = byRef.get(ref);
            if (existing == null && barcode != null) existing = byBarcode.get(barcode);
            DrugMapper.Mapped m = mapper.map(r);

            if (existing != null) {
                if (!o.updateExisting()) { job.skippedExisting++; continue; }
                job.updated++;
                if (o.dryRun()) continue;
                final Long id = existing;
                final String fRef = ref;
                writes.add(() -> update(em.find(Product.class, id), r, m, fRef, o));
                continue;
            }

            // New product: file barcode when free, otherwise a generated one.
            if (barcode != null && usedBarcodes.add(barcode)) {
                job.barcodesFromFile++;
            } else {
                if (barcode != null) job.duplicateBarcodes++;
                barcode = nextBarcode(usedBarcodes, barcodeSeq);
                job.barcodesGenerated++;
            }
            job.created++;
            newRefs.put(ref, -1L);
            if (o.dryRun()) continue;
            final String fBarcode = barcode;
            final String fRef = ref;
            writes.add(() -> em.persist(create(r, m, fRef, fBarcode, o, groupIds, unitIds)));
        }
        if (o.dryRun() || writes.isEmpty()) return;
        try {
            tx.executeWithoutResult(s -> {
                writes.forEach(Runnable::run);
                em.flush();
                em.clear();
            });
            byRef.putAll(newRefs);
        } catch (Exception e) {
            log.warn("Import chunk failed", e);
            job.error(rows.get(0).number() + "-" + rows.get(rows.size() - 1).number() + ": " + rootMessage(e));
            job.created -= newRefs.size();
            job.skippedInvalid += newRefs.size();
        }
    }

    private Product create(DrugRecord r, DrugMapper.Mapped m, String ref, String barcode, ImportJob.Options o,
                           Map<DrugMapper.Group, Long> groupIds, Map<DrugMapper.UnitKind, Long> unitIds) {
        Product p = new Product();
        p.setExternalRef(ref);
        p.setBarcode(barcode);
        p.setNameEn(m.nameEn());
        p.setNameAr(m.nameAr() != null ? m.nameAr() : (m.nameEn() != null ? m.nameEn() : "-"));
        p.setScientificName(DrugMapper.cut(r.activeIngredient(), 500));
        p.setStrength(m.strength());
        p.setDosageForm(m.dosageForm());
        p.setManufacturer(DrugMapper.cut(r.manufacturer(), 150));
        p.setTherapeuticClass(DrugMapper.cut(r.therapeuticClass(), 260));
        p.setCategory(em.getReference(Category.class, groupIds.get(m.group())));
        p.setUnit(em.getReference(Unit.class, unitIds.get(m.unit())));
        p.setStripsPerBox(m.stripsPerBox());
        p.setUnitsPerStrip(m.unitsPerStrip());
        BigDecimal price = r.price() == null ? BigDecimal.ZERO : r.price();
        p.setSalePrice(price);
        p.setCostPrice(estimatedCost(price, o));
        p.setTaxable(o.taxableGroups().contains(m.group()));
        p.setUsesSummary(r.usesAr());
        p.setUsesSummaryEn(r.usesEn());
        p.setWarningsSummary(r.warningsAr());
        p.setWarningsSummaryEn(r.warningsEn());
        p.setWarnFlags(r.warnFlags());
        p.setActive(true);
        return p;
    }

    /** Refreshes the imported information; the user's own settings (names, category, pack, VAT) are kept. */
    private void update(Product p, DrugRecord r, DrugMapper.Mapped m, String ref, ImportJob.Options o) {
        if (p == null) return;
        if (p.getExternalRef() == null) p.setExternalRef(ref);
        if (r.activeIngredient() != null) p.setScientificName(DrugMapper.cut(r.activeIngredient(), 500));
        if (r.manufacturer() != null) p.setManufacturer(DrugMapper.cut(r.manufacturer(), 150));
        if (r.therapeuticClass() != null) p.setTherapeuticClass(DrugMapper.cut(r.therapeuticClass(), 260));
        if (p.getStrength() == null) p.setStrength(m.strength());
        if (p.getDosageForm() == null) p.setDosageForm(m.dosageForm());
        if (r.usesAr() != null) p.setUsesSummary(r.usesAr());
        if (r.usesEn() != null) p.setUsesSummaryEn(r.usesEn());
        if (r.warningsAr() != null) p.setWarningsSummary(r.warningsAr());
        if (r.warningsEn() != null) p.setWarningsSummaryEn(r.warningsEn());
        if (r.warnFlags() != null) p.setWarnFlags(r.warnFlags());
        if (o.updatePrices() && r.price() != null) p.setSalePrice(r.price());
        if (p.getCostPrice().signum() == 0 && r.price() != null) p.setCostPrice(estimatedCost(r.price(), o));
    }

    private static BigDecimal estimatedCost(BigDecimal price, ImportJob.Options o) {
        if (o.costPercent() == null || o.costPercent().signum() <= 0) return BigDecimal.ZERO;
        return price.multiply(o.costPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ helpers

    private Map<DrugMapper.Group, Long> groups(ImportJob job) {
        Map<DrugMapper.Group, Long> ids = new EnumMap<>(DrugMapper.Group.class);
        tx.executeWithoutResult(s -> {
            List<Category> all = categories.findAll();
            for (DrugMapper.Group g : DrugMapper.Group.values()) {
                Category c = all.stream().filter(x -> g.en.equalsIgnoreCase(x.getNameEn()) || g.ar.equals(x.getNameAr()))
                        .findFirst().orElse(null);
                if (c == null) {
                    c = new Category();
                    c.setNameAr(g.ar);
                    c.setNameEn(g.en);
                    c = categories.save(c);
                    job.categoriesCreated++;
                }
                ids.put(g, c.getId());
            }
        });
        return ids;
    }

    private Map<DrugMapper.UnitKind, Long> unitKinds(ImportJob job) {
        Map<DrugMapper.UnitKind, Long> ids = new EnumMap<>(DrugMapper.UnitKind.class);
        tx.executeWithoutResult(s -> {
            List<Unit> all = units.findAll();
            for (DrugMapper.UnitKind k : DrugMapper.UnitKind.values()) {
                Unit u = all.stream().filter(x -> k.en.equalsIgnoreCase(x.getNameEn()) || k.ar.equals(x.getNameAr()))
                        .findFirst().orElse(null);
                if (u == null) {
                    u = new Unit();
                    u.setNameAr(k.ar);
                    u.setNameEn(k.en);
                    u = units.save(u);
                    job.unitsCreated++;
                }
                ids.put(k, u.getId());
            }
        });
        return ids;
    }

    /** Digits only, 8-14 long; anything else is ignored. */
    private static String validBarcode(String b) {
        if (b == null) return null;
        String t = b.trim();
        if (t.endsWith(".0")) t = t.substring(0, t.length() - 2);
        return t.matches("\\d{8,14}") ? t : null;
    }

    /** EAN-13 in the "299" internal range (manual products use "200"), unique among all barcodes. */
    private static String nextBarcode(Set<String> used, long[] seq) {
        while (true) {
            String base = "299" + String.format("%09d", seq[0]++);
            String code = base + BarcodeService.ean13CheckDigit(base);
            if (used.add(code)) return code;
        }
    }

    private static String rootMessage(Throwable e) {
        while (e.getCause() != null) e = e.getCause();
        return e.getMessage();
    }
}
