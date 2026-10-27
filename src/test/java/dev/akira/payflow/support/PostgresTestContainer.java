package dev.akira.payflow.support;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class PostgresTestContainer {

    public static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
                .withDatabaseName("payflow_test")
                .withUsername("payflow_test")
                .withPassword("payflow_test");

    private PostgresTestContainer() {
    }
}
