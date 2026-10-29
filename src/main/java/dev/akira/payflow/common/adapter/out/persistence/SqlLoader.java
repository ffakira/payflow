package dev.akira.payflow.common.adapter.out.persistence;

import org.jspecify.annotations.NonNull;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and parses named SQL queries from classpath resources.
 *
 * <p>The loader resolves a SQL resource using the package and simple name
 * of the supplied class. The resource must have the same simple name as
 * the class, with the {@code .sql} extension, and reside in the
 * corresponding package directory on the classpath.
 *
 * <p>SQL resources must contain named query blocks introduced by comments
 * using the {@code -- name:} convention. Each block consists of a query
 * name followed by its SQL statement on subsequent lines.
 *
 * <p>The loader rejects resources containing no named queries, missing query
 * bodies, blank query names of bodies, or duplicate query names. Successfully
 * parsed queries are returned as an unmodifiable map.
 *
 * <p>This utility centralizes SQL resource loading and parsing for persistence
 * adapters, keeping SQL statements external to Java source files.
 *
 * @author akira
 * @since 0.0.1-SNAPSHOT
 *
 * @see org.springframework.core.io.ClassPathResource
 */
public final class SqlLoader {

    private SqlLoader() {
        // Utility class
    }

    /**
     * Loads and parses the named SQL queries associated with the given class.
     *
     * <p>The resource path is derived from the class package and simple name,
     * with the {@code .sql} extension appended. The resource is read as UTF-8,
     * and it's named queries are returned in an unmodifiable map.
     *
     * @param clazz the class used to resolve the SQL resource path
     * @return an unmodifiable map of query names to SQL statements
     * @throws java.lang.NullPointerException if {@code clazz} is {@code null}
     * @throws java.io.UncheckedIOException if the SQL resource cannot be read
     * @throws java.lang.IllegalArgumentException if the resource contains no valid
     *         named queries or contains malformed or duplicate query blocks
     */
    public static Map<String, String> loadQueries(Class<?> clazz) {
        String path = clazz.getPackageName().replace('.', '/')
                + "/" + clazz.getSimpleName() + ".sql";

        try (var inputStream = new ClassPathResource(path).getInputStream()) {
            String content = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            return Collections.unmodifiableMap(createQueries(content));
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to load SQL file: " + path,
                    e
            );
        }
    }

    /**
     * Parses named SQL query blocks from the supplied resource content.
     *
     * <p>Each block begins with a {@code -- name:} marker followed by a query
     * name on the same line. The remaining lines in that block form the SQL
     * statement, up to the next named query marker or the end of the content.
     *
     * <p>Query names and statements must be nonblank, and query names must
     * be unique within the resource.
     *
     * @param content the complete contents of the SQL resource
     * @return a map of query names to SQL statements
     * @throws java.lang.IllegalArgumentException if no named queries are found, a
     *         query block has no body, a query name or body is blank, or
     *         a duplicate query name is encountered
     */
    private static @NonNull Map<String, String> createQueries(String content) {
        Map<String, String> queries = new HashMap<>();
        String[] blocks = content.split("--\\s*name:\\s*");

        for (int i = 1; i < blocks.length; i++) {
            String block = blocks[i];
            int firstLineEnd = block.indexOf('\n');

            if (firstLineEnd == -1) {
                throw new IllegalArgumentException(
                        "Invalid SQL query block: missing query body"
                );
            }

            String name = block.substring(0, firstLineEnd).trim();
            String sql = block.substring(firstLineEnd).trim();

            if (name.isBlank() || sql.isBlank()) {
                throw new IllegalArgumentException(
                        "SQL query name and body must not be blank"
                );
            }

            if (queries.putIfAbsent(name, sql) != null) {
                throw new IllegalArgumentException(
                        "Duplicate SQL query name: " + name
                );
            }
        }

        if (queries.isEmpty()) {
            throw new IllegalArgumentException(
                    "No named SQL queries found"
            );
        }

        return queries;
    }
}
