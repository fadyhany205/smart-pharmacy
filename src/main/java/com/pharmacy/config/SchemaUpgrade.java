package com.pharmacy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hibernate's ddl-auto=update adds new columns but never widens existing ones.
 * This widens columns whose size grew in a newer version (MySQL / MariaDB). Safe to run on every start:
 * it only changes a column that is still smaller than required.
 */
@Component
@Order(0)
public class SchemaUpgrade implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaUpgrade.class);

    private record Widen(String table, String column, int length, String definition) {}

    private static final List<Widen> COLUMNS = List.of(
            new Widen("products", "scientific_name", 500, "VARCHAR(500) NULL"),
            new Widen("products", "manufacturer", 150, "VARCHAR(150) NULL"));

    private final JdbcTemplate jdbc;

    public SchemaUpgrade(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Widen w : COLUMNS) {
            try {
                Integer current = jdbc.queryForObject("""
                        SELECT character_maximum_length FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                        """, Integer.class, w.table(), w.column());
                if (current != null && current < w.length()) {
                    jdbc.execute("ALTER TABLE " + w.table() + " MODIFY " + w.column() + " " + w.definition());
                    log.info("Schema upgrade: {}.{} widened from {} to {}", w.table(), w.column(), current, w.length());
                }
            } catch (Exception e) {
                log.warn("Schema upgrade of {}.{} skipped: {}", w.table(), w.column(), e.getMessage());
            }
        }
    }
}
