package io.github.rkunameneni.eventflow.acceptance;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.rkunameneni.eventflow.inventory.InventoryServiceApplication;
import io.github.rkunameneni.eventflow.notification.NotificationServiceApplication;
import io.github.rkunameneni.eventflow.order.OrderServiceApplication;
import io.github.rkunameneni.eventflow.payment.PaymentServiceApplication;
import io.github.rkunameneni.eventflow.support.EventflowApplication;
import io.github.rkunameneni.eventflow.support.test.Containers;
import io.github.rkunameneni.eventflow.support.test.TestJwt;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Boots the four saga participants in this JVM, each with its own database, all sharing one Kafka
 * broker and one Redis - the same topology as docker-compose, minus the containers per service.
 * Started once per test run and shut down with the JVM.
 */
final class SagaEnvironment {

    private static SagaEnvironment instance;

    final WireMockServer mockPay = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private final List<ConfigurableApplicationContext> services = new ArrayList<>();
    private int orderPort;
    private int inventoryPort;
    private int paymentPort;
    private int notificationPort;

    private SagaEnvironment() {
    }

    static synchronized SagaEnvironment get() {
        if (instance == null) {
            instance = new SagaEnvironment();
            instance.start();
            Runtime.getRuntime().addShutdownHook(new Thread(instance::stop, "saga-environment-shutdown"));
        }
        return instance;
    }

    int orderPort() {
        return orderPort;
    }

    int inventoryPort() {
        return inventoryPort;
    }

    int paymentPort() {
        return paymentPort;
    }

    int notificationPort() {
        return notificationPort;
    }

    private void start() {
        mockPay.start();
        orderPort = boot(OrderServiceApplication.class, OrderServiceApplication.SERVICE_NAME, "orders");
        inventoryPort = boot(InventoryServiceApplication.class, InventoryServiceApplication.SERVICE_NAME, "inventory",
                "spring.data.redis.host=" + Containers.redisHost(),
                "spring.data.redis.port=" + Containers.redisPort());
        paymentPort = boot(PaymentServiceApplication.class, PaymentServiceApplication.SERVICE_NAME, "payments",
                "eventflow.payment.provider=mockpay",
                "eventflow.payment.mockpay.base-url=" + mockPay.baseUrl(),
                "resilience4j.retry.instances.payment-provider.wait-duration=20ms");
        notificationPort = boot(NotificationServiceApplication.class, NotificationServiceApplication.SERVICE_NAME,
                "notifications");
    }

    private int boot(Class<?> application, String serviceName, String database, String... extraProperties) {
        List<String> properties = new ArrayList<>(List.of(
                "server.port=0",
                "spring.main.banner-mode=off",
                "spring.datasource.url=" + Containers.postgresDatabaseUrl(database),
                "spring.datasource.username=" + Containers.POSTGRES.getUsername(),
                "spring.datasource.password=" + Containers.POSTGRES.getPassword(),
                "spring.datasource.hikari.maximum-pool-size=4",
                "spring.kafka.bootstrap-servers=" + Containers.kafkaBootstrapServers(),
                "eventflow.security.jwt-secret=" + TestJwt.SECRET,
                "eventflow.outbox.poll-interval-ms=100",
                "eventflow.topics.partitions=1"));
        properties.addAll(List.of(extraProperties));
        // Passed as command-line arguments so they take precedence over the service's own YAML.
        String[] args = properties.stream().map(property -> "--" + property).toArray(String[]::new);
        ConfigurableApplicationContext context = EventflowApplication.run(application, serviceName, args);
        services.add(context);
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private void stop() {
        services.forEach(ConfigurableApplicationContext::close);
        mockPay.stop();
    }
}
