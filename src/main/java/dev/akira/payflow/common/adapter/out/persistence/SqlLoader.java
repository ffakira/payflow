
package dev.akira.payflow.common.adapter.out.persistence;

import org.jspecify.annotations.NonNull;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class SqlLoader {

    private SqlLoader() {
        // Utility class
    }

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
