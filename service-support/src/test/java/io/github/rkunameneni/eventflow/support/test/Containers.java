package io.github.rkunameneni.eventflow.support.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Testcontainers shared by all integration tests of a module (one JVM), so the
 * containers start once instead of once per test class. Ryuk removes them when the JVM exits.
 */
public final class Containers {

    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    public static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"))
            .withEnv("KAFKA_HEAP_OPTS", "-Xms256m -Xmx384m")
            .withStartupTimeout(Duration.ofMinutes(3))
            .withStartupAttempts(2);

    public static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private Containers() {
    }

    public static void registerPostgres(DynamicPropertyRegistry registry) {
        start(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    public static void registerKafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", Containers::kafkaBootstrapServers);
    }

    public static void registerRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", Containers::redisHost);
        registry.add("spring.data.redis.port", Containers::redisPort);
    }

    public static String kafkaBootstrapServers() {
        start(KAFKA);
        return KAFKA.getBootstrapServers();
    }

    public static String redisHost() {
        start(REDIS);
        return REDIS.getHost();
    }

    public static int redisPort() {
        start(REDIS);
        return REDIS.getMappedPort(6379);
    }

    /** Creates (if needed) a dedicated database in the shared container - one per service, as in production. */
    public static String postgresDatabaseUrl(String database) {
        start(POSTGRES);
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            var exists = statement.executeQuery(
                    "select 1 from pg_database where datname = '" + database.replaceAll("\\W", "") + "'");
            if (!exists.next()) {
                statement.execute("create database " + database.replaceAll("\\W", ""));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create database " + database, e);
        }
        return POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
    }

    private static synchronized void start(GenericContainer<?> container) {
        if (!container.isRunning()) {
            container.start();
        }
    }
}
