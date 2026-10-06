package com.pharmacy.importer;

import java.util.Map;
import java.util.Set;

/**
 * Column mapping of one kind of drug file. To support another file, add a new implementation
 * and register it in {@link DrugFileReader#FORMATS}; nothing else in the import changes.
 */
public interface SourceFormat {
    /** Short code stored in externalRef, e.g. "EGD". */
    String code();

    /** Display name shown on the import page. */
    String displayName();

    /** True when the file columns (lower-case) look like this format. */
    boolean matches(Set<String> columns);

    /** Converts one raw row (column -> text value, never null) to a normalized record. */
    DrugRecord map(Map<String, String> row);
}
