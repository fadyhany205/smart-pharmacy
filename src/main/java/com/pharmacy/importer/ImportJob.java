package com.pharmacy.importer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Progress and result of one import run (kept in memory, polled by the import page). */
public class ImportJob {

    public enum Status { RUNNING, DONE, FAILED }

    /** User choices on the import page. */
    public record Options(boolean updateExisting, boolean updatePrices, boolean skipNoPrice,
                          java.math.BigDecimal costPercent, Set<DrugMapper.Group> taxableGroups,
                          boolean guessStrips, boolean dryRun) {}

    public final String id = UUID.randomUUID().toString().substring(0, 8);
    public final String fileName;
    public final Options options;
    public final LocalDateTime startedAt = LocalDateTime.now();
    public volatile LocalDateTime finishedAt;
    public volatile Status status = Status.RUNNING;
    public volatile String format;
    public volatile String message;

    public volatile long total, processed, created, updated, skippedExisting, skippedNoPrice, skippedInvalid,
            barcodesFromFile, barcodesGenerated, duplicateBarcodes, categoriesCreated, unitsCreated;

    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

    public ImportJob(String fileName, Options options) {
        this.fileName = fileName;
        this.options = options;
    }

    public void error(String e) {
        if (errors.size() < 50) errors.add(e);
    }

    public List<String> getErrors() {
        synchronized (errors) { return new ArrayList<>(errors); }
    }

    public int percent() {
        return total <= 0 ? 0 : (int) Math.min(100, processed * 100 / total);
    }
}
