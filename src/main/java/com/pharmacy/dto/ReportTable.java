package com.pharmacy.dto;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generic tabular report rendered by reports/view.html and exported to PDF / Excel.
 * Header and summary labels are message keys. Cells are raw values (String, Number, LocalDate...).
 */
@Getter
public class ReportTable {
    private final String id;
    private final String titleKey;
    private final List<String> headerKeys;
    private final List<List<Object>> rows = new ArrayList<>();
    private List<Object> totals;
    private final List<Summary> summary = new ArrayList<>();

    public record Summary(String labelKey, Object value, boolean highlight) {}

    /** A cell whose text is a message key to translate. The first cell of the totals row is always translated. */
    public record Msg(String key) {}

    public ReportTable(String id, String titleKey, String... headerKeys) {
        this.id = id;
        this.titleKey = titleKey;
        this.headerKeys = Arrays.asList(headerKeys);
    }

    public ReportTable row(Object... cells) {
        rows.add(Arrays.asList(cells));
        return this;
    }

    public ReportTable totals(Object... cells) {
        this.totals = Arrays.asList(cells);
        return this;
    }

    public ReportTable summary(String labelKey, Object value) {
        summary.add(new Summary(labelKey, value, false));
        return this;
    }

    public ReportTable highlight(String labelKey, Object value) {
        summary.add(new Summary(labelKey, value, true));
        return this;
    }
}
