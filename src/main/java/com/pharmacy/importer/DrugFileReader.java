package com.pharmacy.importer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.service.BusinessException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * Streams drug rows from a CSV or JSON (array of objects) file, one at a time, so large files
 * do not have to fit in memory. Detects the file type from its first character and the column format
 * from the column names.
 */
public class DrugFileReader {

    /** Supported column formats. Add new ones here. */
    public static final List<SourceFormat> FORMATS = List.of(new EgyptianDrugDbFormat(), new EgDrugsFormat());

    public record Row(long number, Map<String, String> values) {}

    private final Path file;
    private final boolean json;

    public DrugFileReader(Path file) throws IOException {
        this.file = file;
        this.json = firstChar(file) == '[';
    }

    /** Detects which known format the file uses (throws when unknown). */
    public SourceFormat detectFormat() throws IOException {
        Set<String> columns = new HashSet<>();
        forEach(row -> columns.addAll(row.values().keySet()), 1);
        for (SourceFormat f : FORMATS) if (f.matches(columns)) return f;
        throw new BusinessException("import.error.unknownFormat", String.join(", ", new TreeSet<>(columns)));
    }

    public long count() throws IOException {
        long[] n = {0};
        forEach(row -> n[0]++, Long.MAX_VALUE);
        return n[0];
    }

    /** Calls the consumer for each row (at most limit rows). Column names are lower-case. */
    public void forEach(Consumer<Row> consumer, long limit) throws IOException {
        if (json) readJson(consumer, limit);
        else readCsv(consumer, limit);
    }

    private void readCsv(Consumer<Row> consumer, long limit) throws IOException {
        try (Reader in = new BufferedReader(new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true).setTrim(true).build().parse(skipBom(in))) {
            List<String> headers = parser.getHeaderNames().stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
            long n = 0;
            for (CSVRecord rec : parser) {
                if (n >= limit) break;
                Map<String, String> m = new HashMap<>();
                for (int i = 0; i < headers.size() && i < rec.size(); i++) m.put(headers.get(i), rec.get(i));
                consumer.accept(new Row(rec.getRecordNumber() + 1, m));   // +1: the header is line 1
                n++;
            }
        }
    }

    private void readJson(Consumer<Row> consumer, long limit) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = new BufferedInputStream(new FileInputStream(file.toFile()));
             JsonParser p = mapper.getFactory().createParser(in)) {
            if (p.nextToken() != JsonToken.START_ARRAY) throw new BusinessException("import.error.badJson");
            long n = 0;
            while (p.nextToken() == JsonToken.START_OBJECT && n < limit) {
                JsonNode node = mapper.readTree(p);
                Map<String, String> m = new HashMap<>();
                node.fields().forEachRemaining(e -> m.put(e.getKey().toLowerCase(Locale.ROOT),
                        e.getValue() == null || e.getValue().isNull() ? "" : e.getValue().asText()));
                consumer.accept(new Row(++n, m));
            }
        }
    }

    private static Reader skipBom(Reader r) throws IOException {
        PushbackReader pr = new PushbackReader(r, 1);
        int c = pr.read();
        if (c != 0xFEFF && c != -1) pr.unread(c);
        return pr;
    }

    private static int firstChar(Path file) throws IOException {
        try (Reader r = new BufferedReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            int c;
            while ((c = r.read()) != -1) {
                if (c == 0xFEFF || Character.isWhitespace(c)) continue;
                return c;
            }
        }
        return -1;
    }
}
